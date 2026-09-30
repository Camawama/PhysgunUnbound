package net.camacraft.physgununbound.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.camafy.client.render.GuiEntityRender;
import net.camacraft.physgununbound.client.ClientModelRotations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/**
 * Draws an entity the physgun holds (or froze) turned the way the hold turned it: the model
 * rotation ({@link ClientModelRotations}) is applied about the entity's centre, in the world frame,
 * around its renderer's own call. Only the model: the shadow, the hitbox (F3+B) and the name tag,
 * which the dispatcher draws after the renderer, stay as they are, and so does a GUI portrait
 * ({@link GuiEntityRender}).
 *
 * <p>The pose is pushed before the renderer's call and popped after it. Whether one was pushed is
 * kept on a stack, since a renderer may draw other entities through the dispatcher in between.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Unique
    private final ArrayDeque<Boolean> physgununbound$turned = new ArrayDeque<>();

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void physgununbound$turnHeldModel(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                               PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Quaternionf turn = GuiEntityRender.active() ? null : ClientModelRotations.rotation(entity, partialTick);
        if (turn == null) {
            physgununbound$turned.push(Boolean.FALSE);
            return;
        }
        physgununbound$turned.push(Boolean.TRUE);
        // the stack is at the entity's position: turn about the middle of its box
        float middle = entity.getBbHeight() * 0.5F;
        pose.pushPose();
        pose.translate(0.0F, middle, 0.0F);
        pose.mulPose(turn);
        pose.translate(0.0F, -middle, 0.0F);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.AFTER))
    private void physgununbound$restore(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                        PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Boolean turned = physgununbound$turned.poll();
        if (Boolean.TRUE.equals(turned)) pose.popPose();
    }
}
