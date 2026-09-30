package net.camacraft.physgununbound.hold;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PuConfig;
import net.camacraft.physgununbound.mixin.AbstractArrowAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * What the physgun is pointing at, and whether it may pick it up.
 *
 * <p>VMod's physgun casts a ray from the eyes along the look, up to 100 blocks, through world
 * blocks and ship blocks alike ({@code ClipContext.Block.COLLIDER}, no fluids), and grabs the ship
 * whose block it hits. Entities don't stop that ray, so the entity version looks along the same
 * ray, only as far as the first block (world or ship): the nearest entity the ray enters before
 * that is the one grabbed. If there is none, VMod goes on to grab the ship as usual.
 */
public final class Grabbing {
    /** Entities are looked for in stretches of the ray this long (a few small box queries, not one huge one). */
    private static final double STRETCH = 16.0;

    private Grabbing() {}

    /** The entity to hold ({@link #resolve}d), where the ray entered it, and how far that is from the eyes. */
    public record Pick(Entity entity, Vec3 point, double distance) {}

    /**
     * The nearest entity along {@code player}'s look, before the first block and within
     * {@code range}, that passes {@code accept} once {@link #resolve}d; null if there is none.
     * Entities that fail {@code accept} don't block the ray.
     */
    public static @Nullable Pick pick(ServerPlayer player, double range, Predicate<Entity> accept) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = eye.add(look.scale(range));
        BlockHitResult block = Ships.clip(level, new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));

        Pick best = null;
        for (double from = 0.0; from < reach; from += STRETCH) {
            double to = Math.min(reach, from + STRETCH);
            AABB box = new AABB(eye.add(look.scale(from)), eye.add(look.scale(to))).inflate(1.0);
            for (Entity candidate : level.getEntities(player, box, Grabbing::couldBePicked)) {
                AABB bounds = candidate.getBoundingBox().inflate(candidate.getPickRadius());
                if (bounds.contains(eye)) continue; // standing inside it: not something one aims at
                Optional<Vec3> hit = bounds.clip(eye, end);
                if (hit.isEmpty()) continue;
                double distance = hit.get().distanceTo(eye);
                if (best != null && distance >= best.distance()) continue;
                Entity target = resolve(candidate);
                if (!accept.test(target)) continue;
                best = new Pick(target, hit.get(), distance);
            }
            if (best != null && best.distance() <= to) break;
        }
        return best;
    }

    private static boolean couldBePicked(Entity entity) {
        return entity.isAlive() && !entity.isSpectator();
    }

    /** Anything with a box to grab. Entities that are only data (markers, interaction boxes, displays) have none, or are tagged. */
    private static boolean hasBody(Entity entity) {
        AABB box = entity.getBoundingBox();
        return box.getXsize() > 1.0E-4 && box.getYsize() > 1.0E-4 && box.getZsize() > 1.0E-4;
    }

    /** What grabbing {@code hit} takes hold of: a multipart body's owner, and whatever it rides (the whole stack). */
    public static Entity resolve(Entity hit) {
        Entity entity = hit instanceof PartEntity<?> part ? part.getParent() : hit;
        return entity.getRootVehicle();
    }

    /**
     * Why {@code holder} may not pick {@code target} up, or null if it may. Follows the pack's
     * rules: never oneself or a spectator; never what another mod scripts the motion of (MKW's
     * karts, a player on a grapple); never what the gravity mod anchors to its surface; players
     * only if the config and every minigame host allow it, and the same host rules for anything else.
     */
    public static @Nullable String refusal(ServerPlayer holder, Entity target) {
        if (target == holder) return "that is the holder";
        if (target.isRemoved() || !target.isAlive()) return "it is gone";
        if (target.isSpectator()) return "it is a spectator";
        if (target.level() != holder.level()) return "it is in another dimension";
        if (target == holder.getRootVehicle() || target.hasIndirectPassenger(holder)) return "it carries the holder";
        if (target instanceof HangingEntity) return "it hangs on a block"; // frames, paintings and knots break when moved
        if (target instanceof AbstractArrow arrow && ((AbstractArrowAccessor) arrow).physgununbound$inGround()) return "it is stuck in a block";
        if (!hasBody(target)) return "it has no body";
        if (target instanceof LivingEntity living && living.isSleeping()) return "it is asleep in a bed";
        if (PuConfig.SERVER.isUngrabbable(target.getType())) return "its type is ungrabbable";
        if (Ships.inShipyard(target.level(), target.getX(), target.getY(), target.getZ())) return "it is part of a ship";
        if (Gravity.isAnchored(target)) return "the gravity mod anchors it";
        // Another mod's claim on its motion alone doesn't stop the physgun: the hold claims every
        // aspect, and the mods that arbitrate MOTION (WallJump, Grapple Unbound) stand down for it.
        // A mod that owns the whole of its physics (a kart in an MKW race, MKW's hazards) keeps it.
        if (PhysicsOwnership.claimsExcept(target, HeldEntities.OWNER) == PhysicsAspect.ALL) return "another mod scripts all of it";
        if (!MatchPolicies.canHarm(holder, target)) return "a minigame protects it";
        for (Entity rider : target.getIndirectPassengers()) {
            if (rider == holder) return "it carries the holder";
            if (rider.isSpectator()) return "a spectator rides it";
            if (rider instanceof Player && !PuConfig.SERVER.grabPlayers()) return "a player rides it";
            if (!MatchPolicies.canHarm(holder, rider)) return "a minigame protects its rider";
        }
        if (target instanceof Player && !PuConfig.SERVER.grabPlayers()) return "players can't be picked up";
        Hold theirs = target instanceof ServerPlayer player ? Holds.holdOf(player) : null;
        if (theirs != null && (theirs.entity == holder || theirs.entity == holder.getRootVehicle())) {
            return "it is holding the holder";
        }
        return null;
    }
}
