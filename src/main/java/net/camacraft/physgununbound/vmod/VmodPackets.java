package net.camacraft.physgununbound.vmod;

import net.camacraft.physgununbound.hold.HolderScale;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.hold.PhysgunInput;
import net.minecraft.server.level.ServerPlayer;
import net.spaceeye.vmod.physgun.PlayerPhysgunState;
import net.spaceeye.vmod.physgun.ServerPhysgunState;
import org.joml.Quaterniond;

import java.util.UUID;

/**
 * Called by {@code mixin.vmod.PlayerPhysgunStateMixin} at the end of
 * {@code PlayerPhysgunState.fromPkt}, on the network thread, inside VMod's lock for the player,
 * before VMod's handler decides what the message means for ships.
 *
 * <p>Two things happen:
 * <ul>
 *   <li>A copy of the message is queued for the server thread ({@link PhysgunInput#queue}): the
 *   holds apply it at the start of the next tick.</li>
 *   <li>While the player holds an entity, VMod's handler is told to stand down, so it doesn't make
 *   the player seek a ship again behind the hold's back. VMod's handler only starts seeking when
 *   neither the rotate key nor right click is down ({@code mainShipId != -1 || !freezeSelected && !rotate}),
 *   and nothing else of VMod's reads the state's {@code rotate}; so setting it on the state (not
 *   the message) makes the handler do nothing with this message. The next message sets it afresh.</li>
 *   <li>The wheel VMod is about to add to a held ship's distance is scaled to the holder's size
 *   ({@link HolderScale}), so a shrunk player's wheel moves a shrunk ship in their own blocks.</li>
 * </ul>
 */
public final class VmodPackets {
    private VmodPackets() {}

    public static void onPacket(PlayerPhysgunState state, ServerPlayer player,
                                ServerPhysgunState.C2SPhysgunStateChanged message) {
        if (Vmod.failed() || player == null || message == null) return;
        try {
            UUID id = player.getUUID();
            Quaterniond turn = message.getQuatDiff();
            PhysgunInput input = new PhysgunInput(message.getPrimaryActivated(), message.getRotate(),
                    message.getFreezeSelected(), message.getFreezeAll(), message.getUnfreezeAllOrOne(),
                    turn == null ? null : new Quaterniond(turn), message.getIncreaseDistanceBy());
            PhysgunInput.queue(player, input);
            if (input.primary() && Holds.isHolding(id)) state.setRotate(true);
            // VMod's handler, right after this, adds the wheel to a held ship's distance in full-size blocks: one
            // notch is one of the holder's own blocks instead (1/16 of a block in the Great Unknown). The queued
            // input keeps the raw notches; the entity holds scale them themselves.
            double scale = HolderScale.of(player);
            if (scale != 1.0 && state.getIncreaseDistanceBy() != 0.0) state.setIncreaseDistanceBy(state.getIncreaseDistanceBy() * scale);
        } catch (Throwable t) {
            Vmod.fail("reading its physgun message", t);
        }
    }
}
