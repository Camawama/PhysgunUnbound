package net.camacraft.physgununbound.mixin.vmod;

import net.camacraft.physgununbound.vmod.VmodMessages;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VMod's physgun message handler (the one {@code regC2S} handler of {@code ServerPhysgunState}, a Kotlin-inlined
 * class), turned back at its head when it runs off the server thread: the message goes to the server thread and the
 * handler runs there instead ({@link VmodMessages}, the 2026-10-30 freeze).
 *
 * <p>Plain Mixin, applied only with VMod ({@link VmodMixinPlugin}) from a config that isn't required; the target is
 * named by string with its full descriptor and nothing is required of it, so a VMod that renamed the class or the
 * method leaves it unhooked, and the plugin's record of that ({@code VmodHookStatus.messagesHooked}) makes the server
 * log a warning at start. Architectury's packet context is taken as an {@code Object} (its class isn't on the compile
 * class path).
 */
@Pseudo
@Mixin(targets = "net.spaceeye.vmod.physgun.ServerPhysgunState$special$$inlined$regC2S$1", remap = false)
public abstract class PhysgunMessageThreadMixin {
    @Inject(method = "serverHandler(Lnet/minecraft/network/FriendlyByteBuf;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void physgununbound$onTheServerThread(FriendlyByteBuf buf, @Coerce Object context, CallbackInfo ci) {
        if (VmodMessages.deferToServerThread(this, buf, context)) ci.cancel();
    }
}
