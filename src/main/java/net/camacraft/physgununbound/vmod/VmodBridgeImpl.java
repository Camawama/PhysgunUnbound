package net.camacraft.physgununbound.vmod;

import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.physgununbound.PhysgunUnbound;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The bridge to VMod's physgun state (VMod 1.7.1 to 1.9.1 have the same one). Loaded by
 * {@link Vmod#access()} only when VMod is installed.
 *
 * <p>How VMod's physgun works, as far as this mod is concerned:
 * <ul>
 *   <li>The client sends {@code the_vmod:c2s_server_physgun_state_changed} whenever the physgun's
 *   buttons change (left button, E to rotate, right and middle click to freeze, R to unfreeze, the
 *   wheel, the rotate drag). VMod handles it on the network thread, under
 *   {@code synchronized (state.getLock())}; Physgun Unbound moves that handling to the server thread
 *   ({@link VmodMessages}). {@code state.fromPkt(player, pkt)} copies the message in, then with the left
 *   button down and no ship held, the player joins {@code ServerPhysgunState.activelySeeking}.</li>
 *   <li>Each server tick (Architectury's server tick, at HIGH priority) VMod ray-casts for every
 *   seeking player; a ship block hit grabs that ship ({@code mainShipId}), and a
 *   {@code PhysgunController} on the ship pulls it on the physics thread. The same tick takes the
 *   lock of every player in {@code active} (holding a ship).</li>
 *   <li>While seeking, a {@code PhysgunRayRenderer} ({@code state.rID}) draws a straight beam to
 *   the crosshair.</li>
 * </ul>
 * This mod grabs entities one step earlier in the same tick, then takes the player out of the
 * seeking set (and drops the straight beam); the packet hook keeps VMod's handler from putting the
 * player back while the entity is held.
 *
 * <p><b>The bridge never waits for VMod's per-player lock on the server thread</b> (2026-10-30: the owner's server
 * froze for good the moment they pressed R by a ship they held, 1300 blocks up). VMod's handler held that lock on the
 * network thread while its R ray-cast read a shipyard chunk that wasn't loaded, which waits for the server thread
 * (C2ME's or vanilla's off-thread chunk load); the server thread meanwhile waited for the same lock at the start of the
 * tick ({@link #letGoOfMissingShips}, and VMod's own tick right after it). Its lock is a {@code ReentrantLock} used as
 * a monitor, which Java can't try to enter, so nothing here takes it on the server thread any more: a let-go that has
 * to be done under it goes to a thread of its own ({@link #lockThread}) that may wait for it.
 *
 * <p>Every call is guarded: a missing method or field (a VMod update) switches the whole
 * integration off through {@link Vmod#fail}, logged once.
 */
public final class VmodBridgeImpl implements VmodAccess {
    private volatile boolean working = true;

    /** Players a let-go is queued for on {@link #lockThread} (one at a time per player). */
    private final Set<UUID> letGoQueued = ConcurrentHashMap.newKeySet();
    /** Let-goes done on the server thread itself, and on {@link #lockThread} (for the tests). */
    private static final AtomicLong LET_GO_HERE = new AtomicLong();
    private static final AtomicLong LET_GO_ON_LOCK_THREAD = new AtomicLong();
    private static volatile ExecutorService lockThread;

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
    public void letGoOfMissingShips() {
        if (!working()) return;
        try {
            Set<UUID> holding = ServerPhysgunState.INSTANCE.getActive();
            if (holding.isEmpty()) return;
            for (UUID id : new ArrayList<>(holding)) {
                PlayerPhysgunState state = state(id);
                if (state == null) continue;
                // found without the lock: a ship id is never handed out twice, so one that is gone stays gone
                long gone = missingShip(state);
                if (gone == -1L) continue;
                if (VmodMessages.onServerThread()) {
                    // VMod's handler runs on this thread between ticks, so nothing else writes the state now
                    if (letGo(state, id, gone)) LET_GO_HERE.incrementAndGet();
                } else if (letGoQueued.add(id)) {
                    lockThread().execute(() -> {
                        try {
                            // this thread may wait: VMod's handler can hold the lock while it waits for the server thread
                            synchronized (state.getLock()) {
                                if (letGo(state, id, gone)) LET_GO_ON_LOCK_THREAD.incrementAndGet();
                            }
                        } catch (Throwable t) {
                            broken("letting go of a held ship that is gone", t);
                        } finally {
                            letGoQueued.remove(id);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            broken("letting go of a held ship that is gone", t);
        }
    }

    /** The ship VMod holds for this player if it no longer exists in the player's dimension, else -1. */
    private static long missingShip(PlayerPhysgunState state) {
        long held = state.getMainShipId();
        if (held == -1L || !Ships.available()) return -1L;
        ServerPlayer player = state.getServerPlayer();
        if (player == null || Ships.byId(player.level(), held) != null) return -1L;
        return held;
    }

    /**
     * Lets go of {@code gone} if VMod still holds it for this player, and with the button still down the player seeks
     * again. False when the state moved on meanwhile (another ship grabbed, or let go already).
     */
    private static boolean letGo(PlayerPhysgunState state, UUID id, long gone) {
        if (state.getMainShipId() != gone) return false;
        letGoNow(state, id);
        if (state.getPrimaryActivated()) ServerPhysgunState.INSTANCE.getActivelySeeking().add(id);
        return true;
    }

    /**
     * If the ship VMod holds for this player no longer exists in the player's dimension, lets go of it the way VMod's
     * handler does when the left button comes up (no held ship, no caught ships, not active, the beam removed) and
     * answers true; the caller decides whether the player seeks again. Called inside VMod's handler (from the packet
     * hook), so on whichever thread VMod handles the message, under its lock. Does nothing while Camafy can't see
     * ships: an unknown ship is then not a missing one.
     */
    static boolean letGoIfShipGone(PlayerPhysgunState state, UUID id) {
        if (missingShip(state) == -1L) return false;
        letGoNow(state, id);
        return true;
    }

    private static void letGoNow(PlayerPhysgunState state, UUID id) {
        state.setMainShipId(-1L);
        state.getCaughtShipIds().clear();
        ServerPhysgunState.INSTANCE.getActive().remove(id);
        int beam = state.getRID();
        if (beam != -1) {
            RenderingData.INSTANCE.getServer().removeRenderer(beam);
            state.setRID(-1);
        }
    }

    /** The one thread that may wait for VMod's locks (only used while VMod's messages are handled on the network thread). */
    private static ExecutorService lockThread() {
        ExecutorService current = lockThread;
        if (current == null) {
            synchronized (VmodBridgeImpl.class) {
                current = lockThread;
                if (current == null) {
                    current = Executors.newSingleThreadExecutor(r -> {
                        Thread thread = new Thread(r, "Physgun Unbound VMod lock");
                        thread.setDaemon(true);
                        thread.setUncaughtExceptionHandler((t, e) -> PhysgunUnbound.LOGGER.error("Physgun Unbound's VMod lock thread", e));
                        return thread;
                    });
                    lockThread = current;
                }
            }
        }
        return current;
    }

    /** Let-goes of a missing held ship done on the server thread, and on the lock thread (the tests read them). */
    public static long letGoesHere() {
        return LET_GO_HERE.get();
    }

    public static long letGoesOnLockThread() {
        return LET_GO_ON_LOCK_THREAD.get();
    }

    @Override
    public void probe() throws Throwable {
        ServerPhysgunState server = ServerPhysgunState.INSTANCE;
        if (server == null) throw new IllegalStateException("ServerPhysgunState.INSTANCE is null");
        server.getActivelySeeking().size();
        server.getActive().size();
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
        state.getMethod("setMainShipId", long.class);
        state.getMethod("getCaughtShipIds");
        state.getMethod("getPrimaryActivated");
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
