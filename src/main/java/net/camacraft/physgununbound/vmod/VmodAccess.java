package net.camacraft.physgununbound.vmod;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What the mod needs from VMod's physgun, in terms that don't name VMod's classes. The real one
 * ({@code VmodBridgeImpl}) is only loaded when VMod is installed; {@link #NONE} answers "nothing
 * is happening" to everything.
 *
 * <p>Server thread, apart from what the packet hook does inside VMod's handler.
 *
 * <p><b>Nothing here ever waits for VMod's per-player lock on the server thread</b> (the 2026-10-30 freeze): VMod's
 * packet handler holds that lock ({@code synchronized (state.getLock())}) for the whole message, and in VMod 1.9.1 it
 * ray-casts there for R with the left button up; a ray through a chunk that isn't loaded waits for the server thread
 * to load it, so a server thread that waits for the lock in turn never moves again. VMod's handler runs on the server
 * thread since ({@link VmodMessages}), and the bridge's own work takes no lock on the server thread at all.
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
     * Stops VMod seeking a ship for this player, and removes its seeking beam (the straight one to
     * the crosshair). Call right after a hold started, in the same server tick, before VMod's own
     * physgun tick could grab a ship for them. Takes no lock: with VMod's messages handled on the
     * server thread ({@link VmodMessages}) no message can be handled halfway through the tick; without
     * that (switched off, or a VMod whose handler the hook missed) a message that slips a "seek again"
     * in is undone by the next tick's check of holders still seeking.
     */
    void stopSeeking(UUID player);

    /** VMod's spring constant for this player's physgun (its server setting PCONST), or {@code fallback}. */
    double spring(UUID player, double fallback);

    /** VMod's damper constant (DCONST), or {@code fallback}. */
    double damping(UUID player, double fallback);

    /**
     * Lets go of every ship VMod still holds for a player although it no longer exists (broken up, deleted, or in
     * another dimension than its holder), as VMod does when the button comes up; a player whose button is still down
     * seeks again. Server thread, at the start of the tick. VMod itself never notices: its packet handler looks the
     * held ship up with Kotlin's {@code !!}, and a {@code NullPointerException} on the network thread ends the
     * player's connection ("Internal Exception: java.lang.NullPointerException").
     *
     * <p>Never waits: what is gone is found without VMod's lock, and the let-go itself is done right here when VMod's
     * messages are handled on the server thread (nothing else writes the state then), or else handed to a thread of
     * its own that may wait for the lock (VMod's handler may hold it while it waits for this very thread).
     */
    void letGoOfMissingShips();

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
        public void letGoOfMissingShips() {
        }

        @Override
        public void probe() {
        }
    };
}
