package net.camacraft.physgununbound.vmod;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What the mod needs from VMod's physgun, in terms that don't name VMod's classes. The real one
 * ({@code VmodBridgeImpl}) is only loaded when VMod is installed; {@link #NONE} answers "nothing
 * is happening" to everything.
 *
 * <p>Server thread, apart from what the packet hook does on the network thread.
 */
public interface VmodAccess {
    /** Whether VMod is there and this bridge still works (it switches itself off on the first failure). */
    boolean working();

    /**
     * The players VMod is seeking a target for right now: left button held with the physgun,
     * nothing grabbed yet. A copy.
     */
    Collection<UUID> seekingPlayers();

    /** Whether VMod holds a ship for this player. */
    boolean holdingShip(UUID player);

    /** The player object VMod's state for this player holds (the one its last message came with), or null. */
    @Nullable ServerPlayer player(UUID player);

    /**
     * Runs {@code action} holding VMod's lock for this player's physgun state, the lock VMod's own
     * packet handler takes, so no physgun message of theirs is handled halfway through it.
     */
    <T> T withPlayerLock(UUID player, Supplier<T> action);

    /**
     * Stops VMod seeking a ship for this player, and removes its seeking beam (the straight one to
     * the crosshair). Call inside {@link #withPlayerLock}, right after the hold started.
     */
    void stopSeeking(UUID player);

    /** VMod's spring constant for this player's physgun (its server setting PCONST), or {@code fallback}. */
    double spring(UUID player, double fallback);

    /** VMod's damper constant (DCONST), or {@code fallback}. */
    double damping(UUID player, double fallback);

    /** Touches everything the bridge uses once, so a mismatch shows at server start. Throws on a mismatch. */
    void probe() throws Throwable;

    VmodAccess NONE = new VmodAccess() {
        @Override
        public boolean working() {
            return false;
        }

        @Override
        public Collection<UUID> seekingPlayers() {
            return List.of();
        }

        @Override
        public boolean holdingShip(UUID player) {
            return false;
        }

        @Override
        public @Nullable ServerPlayer player(UUID player) {
            return null;
        }

        @Override
        public <T> T withPlayerLock(UUID player, Supplier<T> action) {
            return action.get();
        }

        @Override
        public void stopSeeking(UUID player) {
        }

        @Override
        public double spring(UUID player, double fallback) {
            return fallback;
        }

        @Override
        public double damping(UUID player, double fallback) {
            return fallback;
        }

        @Override
        public void probe() {
        }
    };
}
