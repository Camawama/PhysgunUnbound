package net.camacraft.physgununbound.mixin.vmod;

import net.camacraft.physgununbound.vmod.VmodPackets;
import net.minecraft.server.level.ServerPlayer;
import net.spaceeye.vmod.physgun.PlayerPhysgunState;
import net.spaceeye.vmod.physgun.ServerPhysgunState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VMod's physgun messages, read as VMod reads them: at the end of {@code fromPkt}, which VMod's
 * handler calls first thing for every physgun message, on the network thread and under the
 * player's lock (see {@link VmodPackets}).
 *
 * <p>Applied only when VMod is installed ({@link VmodMixinPlugin}), from a config that isn't
 * required. The target is named with its full descriptor and nothing is required of it, so a VMod
 * update that renames or re-types {@code fromPkt} simply leaves it unhooked: the plugin notices,
 * and the addon switches itself off at server start with one log line.
 */
@Pseudo
@Mixin(value = PlayerPhysgunState.class, remap = false)
public abstract class PlayerPhysgunStateMixin {
    @Inject(method = "fromPkt(Lnet/minecraft/server/level/ServerPlayer;Lnet/spaceeye/vmod/physgun/ServerPhysgunState$C2SPhysgunStateChanged;)V",
            at = @At("TAIL"), require = 0, remap = false)
    private void physgununbound$onPhysgunMessage(ServerPlayer player, ServerPhysgunState.C2SPhysgunStateChanged message,
                                                 CallbackInfo ci) {
        VmodPackets.onPacket((PlayerPhysgunState) (Object) this, player, message);
    }
}
