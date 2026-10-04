package net.camacraft.physgununbound.mixin.vmod.client;

import net.camacraft.physgununbound.hold.HolderScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.spaceeye.vmod.rendering.types.PhysgunRayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.util.UUID;

/**
 * VMod's physgun beam at its holder's size. VMod draws it ({@code PhysgunRayRenderer.renderData}) as a tube 0.1 blocks
 * thick, which in first person leaves the gun 0.2 below, 0.6 right of and 0.45 in front of the camera: full-size
 * blocks, so a player shrunk to 1/16 (VS Genesis's Great Unknown) drew a beam as thick as sixteen of their blocks,
 * starting ten of their blocks off to the side. All four lengths are scaled by the beam's holder's size
 * ({@link HolderScale#size}), so it looks the same against a shrunk world as against a full-size one; another player's
 * beam by that player's size. The rest (the curve, the colour, VMod's 100-block reach) is VMod's.
 *
 * <p>Applied only with VMod installed ({@link net.camacraft.physgununbound.mixin.vmod.VmodMixinPlugin}); the method is
 * named with its full descriptor, and each of the four constants appears exactly once in it (VMod 1.9.1). If a VMod
 * update changes them, the beam keeps VMod's size and nothing else changes.
 */
@Pseudo
@Mixin(value = PhysgunRayRenderer.class, remap = false)
public abstract class PhysgunRayRendererMixin {
    private static final String RENDER = "renderData(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;J)V";

    /** The tube's thickness. */
    @ModifyConstant(method = RENDER, constant = @Constant(doubleValue = 0.1), require = 0, remap = false)
    private double physgununbound$width(double width) {
        return width * physgununbound$holderSize();
    }

    /** First person: how far below the camera the beam leaves the gun. */
    @ModifyConstant(method = RENDER, constant = @Constant(doubleValue = 0.2), require = 0, remap = false)
    private double physgununbound$below(double below) {
        return below * physgununbound$holderSize();
    }

    /** First person: how far right of the camera. */
    @ModifyConstant(method = RENDER, constant = @Constant(doubleValue = 0.6), require = 0, remap = false)
    private double physgununbound$right(double right) {
        return right * physgununbound$holderSize();
    }

    /** First person: how far in front of the camera. */
    @ModifyConstant(method = RENDER, constant = @Constant(doubleValue = 0.45), require = 0, remap = false)
    private double physgununbound$ahead(double ahead) {
        return ahead * physgununbound$holderSize();
    }

    private double physgununbound$holderSize() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 1.0;
        UUID holder = ((PhysgunRayRenderer) (Object) this).getData().getPlayer();
        return holder == null ? 1.0 : HolderScale.size(level.getPlayerByUUID(holder));
    }
}
