package net.camacraft.physgununbound.mixin;

import net.camacraft.physgununbound.hold.HeldEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A held (or frozen) projectile doesn't fly itself. Projectiles don't move through
 * {@code Entity.move} (which {@link EntityMoveMixin} stops for held entities): each kind's
 * {@code tick} adds its velocity to its position itself, after looking for what it hits on the
 * way, and applies its own gravity or acceleration. While the physgun has one, its tick is cut
 * short on the server before any of that: only {@code Entity.baseTick} runs (fire, water, portals,
 * its age), so it neither flies off, nor falls, nor hits the holder. Let go, it carries on from
 * the throw: an arrow aims itself along it, a fireball flies on.
 *
 * <p>The vanilla kinds that move themselves this way, by their tick methods, and the two whose own
 * {@code tick} overrides do more before calling up: a trident's Loyalty return (which moved a frozen
 * trident toward its owner every tick, a stutter between its creeping position and the clients'
 * copies) and an ender pearl's owner check. A modded projectile built on one of them is covered by
 * the same cut; one with movement of its own outside these ticks is not (it can still be tagged
 * ungrabbable).
 *
 * <p>The client's copy is cut the same way while that client knows the entity is held or frozen
 * ({@link HeldEntities#clientHoldsStill}: it sees the beam, or was told the frozen pose), since
 * these ticks also move the client's copy and turn it along its (zero) velocity, which fought the
 * positions and rotations the server sent.
 */
@Mixin({AbstractArrow.class, ThrownTrident.class, ThrowableProjectile.class, ThrownEnderpearl.class,
        AbstractHurtingProjectile.class, ShulkerBullet.class, LlamaSpit.class, FireworkRocketEntity.class})
public abstract class ProjectileTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void physgununbound$heldDoesNotFly(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() ? !HeldEntities.clientHoldsStill(self) : !HeldEntities.blocksSelfMove(self)) return;
        self.baseTick();
        ci.cancel();
    }
}
