package net.camacraft.physgununbound.hold;

import net.camacraft.physgununbound.network.PuNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/**
 * Freezing entities in place, the entity version of VMod making a ship static (right click while
 * holding it; R on it unfreezes it, and grabbing it again does too).
 *
 * <p>A frozen entity has no gravity, a frozen mob no AI (vanilla's own flags, so it stays frozen
 * through saves and on every client), and its own movement is skipped. The marks are entity tags
 * (the ones {@code /tag} lists), so they are saved with the entity and reading them has no side
 * effects: {@value #FROZEN} on a frozen entity, plus {@value #HAD_NO_GRAVITY} and
 * {@value #HAD_NO_AI} if it had those flags before. Unfreezing puts back exactly what it had, and
 * only entities the physgun froze are ever unfrozen by it (a map maker's NoAI villager stays as it
 * is).
 *
 * <p>A frozen entity also keeps the way it was turned while held ({@link #keepPose}: its
 * orientation, in its persistent data, so it survives saves and is sent to every client that starts
 * seeing it). Unfreezing lets it turn back upright.
 *
 * <p>The hold itself turns gravity off on items, carts, boats and the like while they are held, so
 * their clients don't draw them falling between updates. {@value #HELD} marks that too: if the
 * server stops mid-hold without saying so (a crash), the entity gets its gravity back the next
 * time it is loaded.
 */
public final class Freezing {
    public static final String FROZEN = "physgununbound.frozen";
    public static final String HAD_NO_GRAVITY = "physgununbound.frozen.had_no_gravity";
    public static final String HAD_NO_AI = "physgununbound.frozen.had_no_ai";
    public static final String HELD = "physgununbound.held";

    private Freezing() {}

    public static boolean isFrozen(Entity entity) {
        return entity.getTags().contains(FROZEN);
    }

    /** Whether the physgun may freeze this entity: never a player, nor anything a player drives. */
    public static boolean canFreeze(Entity entity) {
        return !(entity instanceof Player) && !(entity.getControllingPassenger() instanceof Player);
    }

    /**
     * Freezes the entity where it is. {@code originalNoGravity} is the gravity flag to put back
     * when it is unfrozen (the one it had before any hold changed it).
     */
    public static void freeze(Entity entity, boolean originalNoGravity) {
        if (!canFreeze(entity)) return;
        if (!isFrozen(entity)) {
            entity.addTag(FROZEN);
            if (originalNoGravity) entity.addTag(HAD_NO_GRAVITY);
            if (entity instanceof Mob mob && mob.isNoAi()) entity.addTag(HAD_NO_AI);
        }
        entity.setNoGravity(true);
        if (entity instanceof Mob mob) mob.setNoAi(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        HeldEntities.markFrozen(entity);
    }

    /** A frozen entity keeps {@code orientation} (the hold's) until it is unfrozen; the clients near it are told. */
    public static void keepPose(Entity entity, Quaterniondc orientation) {
        if (!isFrozen(entity)) return;
        ModelPose.storeFrozen(entity, orientation);
        PuNetwork.sendFrozenPose(entity, orientation);
    }

    /** The orientation a frozen entity keeps, if it was frozen turned. */
    public static Quaterniond keptPose(Entity entity) {
        return ModelPose.storedFrozen(entity);
    }

    /** A player has started seeing a frozen entity: tell them how it is turned. */
    public static void sendPoseTo(ServerPlayer player, Entity entity) {
        if (!isFrozen(entity)) return;
        Quaterniond pose = ModelPose.storedFrozen(entity);
        if (pose != null) PuNetwork.sendFrozenPose(player, entity, pose);
    }

    /** Unfreezes an entity the physgun froze, and lets it turn back upright; anything else is left alone. */
    public static boolean unfreeze(Entity entity) {
        return unfreeze(entity, true);
    }

    /**
     * Unfreezes an entity the physgun froze; anything else is left alone.
     *
     * @param letTurnBack whether the clients are told it is no longer held turned (false when a
     *                    hold takes it straight over and carries on from its pose)
     */
    public static boolean unfreeze(Entity entity, boolean letTurnBack) {
        if (!isFrozen(entity)) return false;
        entity.setNoGravity(entity.getTags().contains(HAD_NO_GRAVITY));
        if (entity instanceof Mob mob) mob.setNoAi(entity.getTags().contains(HAD_NO_AI));
        entity.removeTag(FROZEN);
        entity.removeTag(HAD_NO_GRAVITY);
        entity.removeTag(HAD_NO_AI);
        boolean turned = ModelPose.storedFrozen(entity) != null;
        ModelPose.clearFrozen(entity);
        HeldEntities.unmarkFrozen(entity);
        entity.hasImpulse = true;
        if (turned && letTurnBack) PuNetwork.sendFrozenPoseEnd(entity);
        return true;
    }

    /** Marks that a hold turned this entity's gravity off (it had it on), for {@link #onJoin}. */
    static void markHeld(Entity entity) {
        entity.addTag(HELD);
    }

    static void clearHeldMark(Entity entity) {
        entity.removeTag(HELD);
    }

    /**
     * An entity joining a level: frozen ones are registered again (their movement stays skipped),
     * and one saved in the middle of a hold gets its gravity back (no hold survives a reload).
     */
    public static void onJoin(Entity entity) {
        if (entity.getTags().contains(HELD) && !HeldEntities.isHeld(entity)) {
            entity.setNoGravity(false);
            entity.removeTag(HELD);
        }
        if (isFrozen(entity)) HeldEntities.markFrozen(entity);
        else if (ModelPose.storedFrozen(entity) != null) ModelPose.clearFrozen(entity); // a pose without its freeze (edited data)
    }

    /** An entity leaving its level (unloaded, killed, changing dimension). */
    public static void onLeave(Entity entity) {
        HeldEntities.unmarkFrozen(entity);
    }
}
