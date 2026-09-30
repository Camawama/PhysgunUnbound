package net.camacraft.physgununbound.hold;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

/**
 * One physgun input message, as VMod's client sends it ({@code the_vmod:c2s_server_physgun_state_changed},
 * at most once per client tick and only when something changed):
 *
 * @param primary        left mouse button held
 * @param rotate         rotate key (E) held while the left button is held
 * @param freezeSelected right mouse button held: freeze what is held and let go
 * @param freezeAll      middle mouse button clicked: freeze what is held (and, for ships, everything connected) and let go
 * @param unfreeze       R held: with nothing held, unfreeze what the physgun points at
 * @param rotation       the rotation the mouse dragged while rotating, in world space (null: none)
 * @param distanceDelta  mouse wheel notches since the last message: the hold distance changes by this many blocks
 */
public record PhysgunInput(boolean primary, boolean rotate, boolean freezeSelected, boolean freezeAll,
                           boolean unfreeze, @Nullable Quaterniond rotation, double distanceDelta) {

    /** A release: the left button let go. */
    public static PhysgunInput release() {
        return new PhysgunInput(false, false, false, false, false, null, 0.0);
    }

    /** The left button still held, with this much wheel movement. */
    public static PhysgunInput scroll(double notches) {
        return new PhysgunInput(true, false, false, false, false, null, notches);
    }

    /** The left button still held, with the rotate key's drag. */
    public static PhysgunInput rotate(Quaterniond rotation) {
        return new PhysgunInput(true, true, false, false, false, rotation, 0.0);
    }

    /** Right click while holding. */
    public static PhysgunInput freeze() {
        return new PhysgunInput(true, false, true, false, false, null, 0.0);
    }

    /** R with the left button up. */
    public static PhysgunInput unfreezeLookedAt() {
        return new PhysgunInput(false, false, false, false, true, null, 0.0);
    }

    /**
     * Messages waiting for the server thread. VMod reads its message on the network thread, where
     * the world can't be touched, so the hook queues a copy and {@link Holds} applies them in order
     * at the start of the next server tick.
     */
    static final class Queues {
        /** More than a second of messages from one player is a flood, not input. */
        private static final int LIMIT = 64;
        private static final Map<UUID, Queue<Pending>> QUEUES = new ConcurrentHashMap<>();

        /** The player object the message came in on (the one its connection has now). */
        private record Pending(ServerPlayer player, PhysgunInput input) {}

        private Queues() {}

        static void offer(ServerPlayer player, PhysgunInput input) {
            Queue<Pending> queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ConcurrentLinkedQueue<>());
            if (queue.size() < LIMIT) queue.offer(new Pending(player, input));
        }

        static void drain(BiConsumer<ServerPlayer, PhysgunInput> sink) {
            for (Queue<Pending> queue : QUEUES.values()) {
                Pending pending;
                while ((pending = queue.poll()) != null) sink.accept(pending.player(), pending.input());
            }
        }

        static void forget(UUID player) {
            QUEUES.remove(player);
        }

        static void clear() {
            QUEUES.clear();
        }
    }

    /** Queues a message from VMod's network thread (see {@link Queues}). */
    public static void queue(ServerPlayer player, PhysgunInput input) {
        Queues.offer(player, input);
    }
}
