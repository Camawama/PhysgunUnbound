package net.camacraft.physgununbound.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.camafy.client.render.BlobShadow;
import net.camacraft.camafy.client.render.ShipShadows;
import net.camacraft.camafy.client.render.GuiEntityRender;
import net.camacraft.physgununbound.client.ClientModelRotations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/**
 * Draws an entity the physgun holds (or froze) turned the way the hold turned it: the model
 * rotation ({@link ClientModelRotations}) is applied about the entity's centre, in the world frame,
 * around its renderer's own call. Only the model and its shadow: the hitbox (F3+B) and the name
 * tag, which the dispatcher draws after the renderer, stay as they are, and so does a GUI portrait
 * ({@link GuiEntityRender}).
 *
 * <p>A renderer that faces the camera (a thrown egg or snowball, an item, an experience orb: see
 * {@link ClientModelRotations#drawsTurned}) is never turned: a billboard turned about the entity
 * stops facing the camera. Its shadow still follows the hold.
 *
 * <p>The pose is pushed before the renderer's call and popped after it. Whether one was pushed is
 * kept on a stack, since a renderer may draw other entities through the dispatcher in between.
 *
 * <p>The shadow follows the turn the way Gravity Unbound's does for a body in another gravity: a
 * turned model's round blob becomes an ellipse along the way it lies, centred under the model's
 * middle, fading from its lowest point (Camafy's {@link BlobShadow}). Gravity Unbound wraps the
 * same call for its own frames and hands every other entity on to the call below, so an entity
 * held in ordinary gravity gets this shadow, and one whose gravity is turned keeps Gravity
 * Unbound's.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Unique
    private final ArrayDeque<Boolean> physgununbound$turned = new ArrayDeque<>();

    @Shadow
    private static void renderShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float weight,
                                     float partialTick, LevelReader level, float radius) {
        throw new AssertionError();
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void physgununbound$turnHeldModel(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                               PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Quaternionf turn = GuiEntityRender.active()
                || !ClientModelRotations.drawsTurned((EntityRenderDispatcher) (Object) this, entity)
                ? null : ClientModelRotations.rotation(entity, partialTick);
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

    /**
     * The shadow of a turned model: an ellipse along the way it lies instead of vanilla's circle at
     * the feet. Everything else (no turn, or a portrait) is vanilla's own call, so Gravity Unbound's
     * wrap of this call sees no difference for the entities it doesn't turn.
     */
    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"))
    private void physgununbound$turnedShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float weight,
                                                    float partialTick, LevelReader level, float radius) {
        Quaternionf turn = GuiEntityRender.active() ? null : ClientModelRotations.rotation(entity, partialTick);
        if (turn == null) {
            renderShadow(pose, buffers, entity, weight, partialTick, level, radius);
            return;
        }
        BlobShadow.Blob blob = BlobShadow.turnedAboutCentre(entity, partialTick,
                ClientModelRotations.modelUp(entity, partialTick, turn), radius);
        // on a ship's deck the oval falls on the deck (Camafy 0.1.18), on the ground otherwise
        BlobShadow.render(pose, buffers, entity, weight, partialTick, level, blob, ShipShadows.under(entity, partialTick));
    }
}
