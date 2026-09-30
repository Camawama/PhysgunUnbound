package net.camacraft.physgununbound.mixin;

import net.camacraft.physgununbound.hold.HeldEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A held (or frozen) entity doesn't move itself: the hold moves it, through this same method, at
 * the start of the tick. Its own {@code move(SELF, ...)} (walking, falling, drifting, sliding
 * along a rail) is skipped; everything else its tick does still happens, and pushes from outside
 * (pistons, shulkers, the player's own movement packets) still move it.
 *
 * <p>The check is one volatile read unless something is held or frozen.
 */
@Mixin(Entity.class)
public abstract class EntityMoveMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void physgununbound$heldDoesNotMoveItself(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (type == MoverType.SELF && HeldEntities.blocksSelfMove((Entity) (Object) this)) ci.cancel();
    }
}
