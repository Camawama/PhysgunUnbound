package net.camacraft.physgununbound.vmod;

/**
 * Whether the hooks into VMod's physgun made it into VMod's classes. Set by the mixin config plugin
 * when it applies {@code mixin.vmod.PlayerPhysgunStateMixin} (the message reader) and
 * {@code mixin.vmod.PhysgunMessageThreadMixin} (VMod's handler moved to the server thread), which
 * is why it lives here, depends on nothing and is never in the mixin package (game code may not
 * touch classes there).
 */
public final class VmodHookStatus {
    private static volatile boolean applied;
    private static volatile boolean messagesHooked;

    private VmodHookStatus() {}

    public static void markApplied() {
        applied = true;
    }

    /** Whether the hook into {@code PlayerPhysgunState.fromPkt} applied (the addon needs it). */
    public static boolean applied() {
        return applied;
    }

    public static void markMessagesHooked() {
        messagesHooked = true;
    }

    /**
     * Whether the hook at the head of VMod's physgun message handler applied, which hands messages that arrive on the
     * network thread to the server thread ({@link VmodMessages}). Without it VMod handles them on the network thread
     * as it does alone.
     */
    public static boolean messagesHooked() {
        return messagesHooked;
    }
}
