package net.camacraft.physgununbound.mixin;

import net.camacraft.physgununbound.hold.HeldEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player the physgun holds up in the air (or whose vehicle it holds) isn't flying: without this,
 * a server with {@code allow-flight=false} kicks them after four seconds ("Flying is not enabled
 * on this server"). The counters it would kick on restart while they are held.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;
    @Shadow
    private int aboveGroundTickCount;
    @Shadow
    private int aboveGroundVehicleTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void physgununbound$heldPlayersAreNotFlying(CallbackInfo ci) {
        if (player != null && (HeldEntities.isHeld(player) || HeldEntities.isHeld(player.getRootVehicle()))) {
            aboveGroundTickCount = 0;
            aboveGroundVehicleTickCount = 0;
        }
    }
}
