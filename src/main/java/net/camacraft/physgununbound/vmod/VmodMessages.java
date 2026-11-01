package net.camacraft.physgununbound.vmod;

import io.netty.buffer.Unpooled;
import net.camacraft.camafy.util.log.LogOnce;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.PuConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VMod's physgun messages, handled on the server thread instead of the network thread (2026-10-30, the owner: "while
 * in the overworld about 1300 blocks in the air, I froze my ship with the physgun. When I tried to grab it again ...
 * the server froze the moment I grabbed the ship, and it never responded again").
 *
 * <p>VMod 1.9.1 handles {@code the_vmod:c2s_server_physgun_state_changed} on the network thread, the whole message
 * under {@code synchronized (state.getLock())}, and for R with the left button up it ray-casts there, 100 blocks
 * through the world and through every ship whose box the ray's box meets, in that ship's shipyard. Reading a chunk
 * that isn't loaded off the server thread waits for the server thread to load it (C2ME's
 * {@code getChunkOffThread}, or vanilla's own {@code CompletableFuture.join} without C2ME); VMod's server tick takes
 * the same lock for every player who holds a ship, at the start of every tick, and so did Physgun Unbound's. Once a
 * tick started while the ray waited, neither thread moved again: the owner's thread dump has "Server thread" BLOCKED on
 * the lock owned by "Netty Server IO #1", parked in C2ME's {@code CFUtil.join} under VMod's
 * {@code RaycastFunctions.clip}. VMod alone does the same (its own tick blocks).
 *
 * <p>So a message that arrives on any other thread is copied and handed to the server (as vanilla hands over its own
 * game packets: in order, between ticks, skipped once the player is gone), and VMod's handler runs it there: the
 * ray-cast loads what it reads on the server thread (a short stall at worst), and every taker of the lock is on one
 * thread. A message already on the server thread (the gametests', a stopped server's) goes straight through.
 *
 * <p>Switch: {@code [vmod] messagesOnServerThread} in {@code physgununbound-server.toml} (on), and the system
 * property {@code physgununbound.vmodMessagesOnServerThread} ({@code true}/{@code false}, read live) over it for
 * the pack harness's A/B. Hooked by {@code mixin.vmod.PhysgunMessageThreadMixin}; whether the hook made it into
 * VMod's class is {@link VmodHookStatus#messagesHooked()}.
 */
public final class VmodMessages {
    public static final String PROPERTY = "physgununbound.vmodMessagesOnServerThread";

    private static final AtomicLong DEFERRED = new AtomicLong();
    private static final AtomicLong HANDLED_ON_SERVER_THREAD = new AtomicLong();
    /** Set while a handed-over message runs, so the hook lets it through whatever thread runs it. */
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile @Nullable Method handlerMethod;
    private static volatile @Nullable Method playerMethod;

    private VmodMessages() {}

    /** Whether the switch is on (the property over the config). */
    public static boolean enabled() {
        String forced = System.getProperty(PROPERTY);
        if (forced != null) return !"false".equalsIgnoreCase(forced.trim());
        return PuConfig.SERVER.vmodMessagesOnServerThread();
    }

    /** Whether VMod's physgun messages are handled on the server thread right now: the hook is in and the switch on. */
    public static boolean onServerThread() {
        return VmodHookStatus.messagesHooked() && enabled();
    }

    /**
     * From the hook at the head of VMod's handler ({@code handler} is VMod's {@code C2SConnection}, {@code context}
     * Architectury's packet context): true when the message was handed to the server thread and VMod's handler must
     * not run here.
     */
    public static boolean deferToServerThread(Object handler, FriendlyByteBuf buf, Object context) {
        if (!enabled()) return false;
        try {
            ServerPlayer player = player(context);
            MinecraftServer server = player == null ? null : player.getServer();
            if (server == null) return false;
            if (server.isSameThread()) {
                HANDLED_ON_SERVER_THREAD.incrementAndGet();
                return false;
            }
            // a stopping server runs what it is handed on the caller's thread: VMod's own way then, never a loop
            if (RUNNING.get() || server.isStopped() || !server.isRunning()) return false;
            Method method = handlerMethod(handler, context);
            // the network buffer belongs to the packet and may be released once this returns: a copy of what's unread
            FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.copiedBuffer(buf));
            server.execute(() -> run(method, handler, copy, context, player));
            DEFERRED.incrementAndGet();
            return true;
        } catch (RejectedExecutionException stopping) {
            return false;
        } catch (Throwable t) {
            LogOnce.warn(PhysgunUnbound.LOGGER, "physgununbound-vmod-messages",
                    "Couldn't hand VMod's physgun message to the server thread ({}); VMod handles it on the network thread as it does without "
                            + "Physgun Unbound", t.toString());
            return false;
        }
    }

    private static void run(Method method, Object handler, FriendlyByteBuf copy, Object context, ServerPlayer player) {
        if (player.hasDisconnected()) return; // as vanilla drops a gone player's queued packets
        RUNNING.set(Boolean.TRUE);
        try {
            method.invoke(handler, copy, context); // through the hook again, which lets it run here
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
            // on the network thread this ended the player's connection; on the server thread it is logged (once)
            LogOnce.warn(PhysgunUnbound.LOGGER, "physgununbound-vmod-handler-threw",
                    "VMod's physgun message handler threw on the server thread ({}); the message is dropped", cause.toString());
        } finally {
            RUNNING.set(Boolean.FALSE);
        }
    }

    private static @Nullable ServerPlayer player(Object context) throws ReflectiveOperationException {
        Method method = playerMethod;
        if (method == null) {
            method = findInterfaceMethod(context.getClass(), "getPlayer");
            playerMethod = method;
        }
        return method.invoke(context) instanceof ServerPlayer player ? player : null;
    }

    /** VMod's {@code C2SConnection.serverHandler(FriendlyByteBuf, NetworkManager.PacketContext)}. */
    private static Method handlerMethod(Object handler, Object context) throws ReflectiveOperationException {
        Method method = handlerMethod;
        if (method == null) {
            Class<?> contextType = Class.forName("dev.architectury.networking.NetworkManager$PacketContext", false,
                    handler.getClass().getClassLoader());
            method = handler.getClass().getMethod("serverHandler", FriendlyByteBuf.class, contextType);
            handlerMethod = method;
        }
        return method;
    }

    /** A public method of one of the context's interfaces (Architectury's implementations aren't public classes). */
    private static Method findInterfaceMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                try {
                    return iface.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return type.getMethod(name);
    }

    /** Messages handed from another thread to the server thread, and messages VMod's handler ran on the server thread. */
    public static long deferred() {
        return DEFERRED.get();
    }

    public static long handledOnServerThread() {
        return HANDLED_ON_SERVER_THREAD.get();
    }
}
