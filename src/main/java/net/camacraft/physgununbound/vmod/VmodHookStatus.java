package net.camacraft.physgununbound.vmod;

/**
 * Whether the hook into VMod's physgun packet handling made it into VMod's class. Set by the mixin
 * config plugin when it applies {@code mixin.vmod.PlayerPhysgunStateMixin}, which is why it lives
 * here, depends on nothing and is never in the mixin package (game code may not touch classes
 * there).
 */
public final class VmodHookStatus {
    private static volatile boolean applied;

    private VmodHookStatus() {}

    public static void markApplied() {
        applied = true;
    }

    public static boolean applied() {
        return applied;
    }
}
