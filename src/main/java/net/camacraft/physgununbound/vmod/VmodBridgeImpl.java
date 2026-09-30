package net.camacraft.physgununbound.vmod;

import net.minecraft.server.level.ServerPlayer;
import net.spaceeye.vmod.physgun.PlayerPhysgunState;
import net.spaceeye.vmod.physgun.ServerPhysgunState;
import net.spaceeye.vmod.rendering.RenderingData;
import net.spaceeye.vmod.rendering.ServerSynchronisedRenderingData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The bridge to VMod's physgun state (VMod 1.7.1 to 1.9.1 have the same one). Loaded by
 * {@link Vmod#access()} only when VMod is installed.
 *
 * <p>How VMod's physgun works, as far as this mod is concerned:
 * <ul>
 *   <li>The client sends {@code the_vmod:c2s_server_physgun_state_changed} whenever the physgun's
 *   buttons change (left button, E to rotate, right and middle click to freeze, R to unfreeze, the
 *   wheel, the rotate drag). The server handles it on the network thread, under
 *   {@code synchronized (state.getLock())}: {@code state.fromPkt(player, pkt)} copies the message
 *   in, then with the left button down and no ship held, the player joins
 *   {@code ServerPhysgunState.activelySeeking}.</li>
 *   <li>Each server tick (Architectury's server tick, at HIGH priority) VMod ray-casts for every
 *   seeking player; a ship block hit grabs that ship ({@code mainShipId}), and a
 *   {@code PhysgunController} on the ship pulls it on the physics thread.</li>
 *   <li>While seeking, a {@code PhysgunRayRenderer} ({@code state.rID}) draws a straight beam to
 *   the crosshair.</li>
 * </ul>
 * This mod grabs entities one step earlier in the same tick, then takes the player out of the
 * seeking set (and drops the straight beam) under the same lock; the packet hook keeps VMod's
 * handler from putting the player back while the entity is held.
 *
 * <p>Every call is guarded: a missing method or field (a VMod update) switches the whole
 * integration off through {@link Vmod#fail}, logged once.
 */
public final class VmodBridgeImpl implements VmodAccess {
    private volatile boolean working = true;

    public VmodBridgeImpl() {
    }

    @Override
    public boolean working() {
        return working && !Vmod.failed();
    }

    @Override
    public Collection<UUID> seekingPlayers() {
        if (!working()) return List.of();
        try {
            Set<UUID> seeking = ServerPhysgunState.INSTANCE.getActivelySeeking();
            return seeking.isEmpty() ? List.of() : new ArrayList<>(seeking);
        } catch (Throwable t) {
            broken("reading which players are seeking", t);
            return List.of();
        }
    }

    @Override
    public boolean holdingShip(UUID player) {
        if (!working()) return false;
        try {
            PlayerPhysgunState state = state(player);
            return state != null && state.getMainShipId() != -1L;
        } catch (Throwable t) {
            broken("reading the held ship", t);
            return false;
        }
    }

    @Override
    public @Nullable ServerPlayer player(UUID player) {
        if (!working()) return null;
        try {
            PlayerPhysgunState state = state(player);
            return state == null ? null : state.getServerPlayer();
        } catch (Throwable t) {
            broken("reading the physgun state's player", t);
            return null;
        }
    }

    @Override
    public <T> T withPlayerLock(UUID player, Supplier<T> action) {
        Object lock = null;
        if (working()) {
            try {
                PlayerPhysgunState state = state(player);
                if (state != null) lock = state.getLock();
            } catch (Throwable t) {
                broken("reading the physgun state's lock", t);
            }
        }
        if (lock == null) return action.get();
        // VMod's handler synchronizes on this object (Kotlin's synchronized(lock) {}), not lock()
        synchronized (lock) {
            return action.get();
        }
    }

    @Override
    public void stopSeeking(UUID player) {
        if (!working()) return;
        try {
            ServerPhysgunState.INSTANCE.getActivelySeeking().remove(player);
            PlayerPhysgunState state = state(player);
            if (state == null) return;
            int beam = state.getRID();
            if (beam != -1) {
                RenderingData.INSTANCE.getServer().removeRenderer(beam);
                state.setRID(-1);
            }
        } catch (Throwable t) {
            broken("stopping its seeking", t);
        }
    }

    @Override
    public double spring(UUID player, double fallback) {
        if (!working()) return fallback;
        try {
            PlayerPhysgunState state = state(player);
            double value = state == null ? fallback : state.getPConst();
            return Double.isFinite(value) && value > 0.0 ? value : fallback;
        } catch (Throwable t) {
            broken("reading PCONST", t);
            return fallback;
        }
    }

    @Override
    public double damping(UUID player, double fallback) {
        if (!working()) return fallback;
        try {
            PlayerPhysgunState state = state(player);
            double value = state == null ? fallback : state.getDConst();
            return Double.isFinite(value) && value >= 0.0 ? value : fallback;
        } catch (Throwable t) {
            broken("reading DCONST", t);
            return fallback;
        }
    }

    @Override
    public void probe() throws Throwable {
        ServerPhysgunState server = ServerPhysgunState.INSTANCE;
        if (server == null) throw new IllegalStateException("ServerPhysgunState.INSTANCE is null");
        server.getActivelySeeking().size();
        server.getPlayerStates().size();
        if (RenderingData.INSTANCE.getServer() == null) throw new IllegalStateException("no server rendering data");
        // what the calls above don't touch yet: resolve it now rather than at the first grab
        Class<?> state = PlayerPhysgunState.class;
        state.getMethod("getLock");
        state.getMethod("getServerPlayer");
        state.getMethod("getRID");
        state.getMethod("setRID", int.class);
        state.getMethod("getMainShipId");
        state.getMethod("getPConst");
        state.getMethod("getDConst");
        state.getMethod("setRotate", boolean.class);
        ServerSynchronisedRenderingData.class.getMethod("removeRenderer", int.class);
        Class<?> message = ServerPhysgunState.C2SPhysgunStateChanged.class;
        for (String getter : new String[]{"getPrimaryActivated", "getRotate", "getFreezeSelected", "getFreezeAll",
                "getUnfreezeAllOrOne", "getQuatDiff", "getIncreaseDistanceBy"}) {
            message.getMethod(getter);
        }
    }

    private static @Nullable PlayerPhysgunState state(UUID player) {
        return ServerPhysgunState.INSTANCE.getPlayerStates().get(player);
    }

    private void broken(String what, Throwable t) {
        working = false;
        Vmod.fail(what, t);
    }
}
