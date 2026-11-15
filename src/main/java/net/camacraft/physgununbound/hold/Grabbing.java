package net.camacraft.physgununbound.hold;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.camafy.permission.Permissions;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PhysgunUnbound;
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
    /**
     * The staff permission {@code physgununbound.grab.players}: picking players up whatever the config says, at the
     * full range. Operators (level 2) have it; a permissions mod can give it to others. A minigame's protection
     * still holds for them.
     */
    public static final String GRAB_PLAYERS = "grab.players";
    public static final int GRAB_PLAYERS_LEVEL = 2;

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

    /** The physgun as Camafy's match policies know it ({@code MatchPolicies.mayUse}): a host may refuse it to a player. */
    public static final net.minecraft.resources.ResourceLocation MECHANIC = new net.minecraft.resources.ResourceLocation("physgununbound", "physgun");

    /**
     * Why {@code holder} may not pick {@code target} up, or null if it may. Follows the pack's
     * rules: never oneself or a spectator; never what another mod scripts the motion of (MKW's
     * karts, a player on a grapple); never what the gravity mod anchors to its surface; players
     * only if the config and every minigame host allow it, and the same host rules for anything else.
     */
    public static @Nullable String refusal(ServerPlayer holder, Entity target) {
        // A host's rules may switch the physgun off for the holder (a minigame without it): asked first.
        if (!MatchPolicies.mayUse(holder, MECHANIC)) return "a minigame switches the physgun off";
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
            if (rider instanceof Player && playerRefusal(holder, rider, true) != null) return "a player rides it";
            if (!MatchPolicies.canHarm(holder, rider)) return "a minigame protects its rider";
        }
        if (target instanceof Player) {
            String why = playerRefusal(holder, target, true);
            if (why != null) return why;
        }
        Hold theirs = target instanceof ServerPlayer player ? Holds.holdOf(player) : null;
        if (theirs != null && (theirs.entity == holder || theirs.entity == holder.getRootVehicle())) {
            return "it is holding the holder";
        }
        return null;
    }

    /**
     * Why {@code holder} may not pick up (or go on holding, with {@code starting} false) the player {@code player},
     * or what they ride; null if they may. Staff with {@link #GRAB_PLAYERS} always may. Anyone else only with the
     * config's {@code playersCanBePickedUp} on, and to start with only from within {@code playerRange} (a held
     * player may then be pushed out as far as anything else).
     */
    public static @Nullable String playerRefusal(ServerPlayer holder, Entity player, boolean starting) {
        if (Permissions.has(holder, PhysgunUnbound.MOD_ID, GRAB_PLAYERS, GRAB_PLAYERS_LEVEL)) return null;
        if (!PuConfig.SERVER.grabPlayers()) return "players can't be picked up";
        double range = PuConfig.SERVER.playerRange() + 2.0; // to the eyes of a body up to two blocks off its feet
        if (starting && player.distanceToSqr(holder.getEyePosition()) > range * range) return "the player is too far away";
        return null;
    }
}
