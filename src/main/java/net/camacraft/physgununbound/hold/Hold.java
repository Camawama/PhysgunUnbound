package net.camacraft.physgununbound.hold;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * One player holding one entity with the physgun. Server thread only; made and ended by
 * {@link Holds}.
 */
public final class Hold {
    /** Who simulates the held entity, and so who moves it. */
    public enum Mode {
        /** The server: mobs, items, boats and carts nobody drives. The hold moves it directly. */
        SERVER,
        /** A player's client: a player, or a vehicle a player drives. The hold sends that client the target. */
        CLIENT
    }

    final UUID holderId;
    final ServerPlayer holder;
    final Entity entity;
    /** Holds started by VMod's physgun end when the physgun leaves the main hand. */
    final boolean requirePhysgun;
    final Mode mode;
    /** VMod's spring (1/s²) and damper (1/s) for this hold. */
    final double spring;
    final double damping;

    /** From the eyes to the target point, in blocks (the mouse wheel changes it). */
    double distance;
    /** From the entity's position to the grabbed point, at the moment of the grab. */
    final Vec3 grabOffset;
    /**
     * The ideal orientation (world frame): the model as its renderer drew it at the grab, turned
     * with the holder's view and the rotate drags since ({@link ModelPose}).
     */
    final Quaterniond orientation;
    /** The inverse of {@link #orientation} at the grab: with the orientation, the turn since the grab. */
    final Quaterniond grabOrientationInverse;
    /** Its heading now (kept for when the orientation's forward axis points straight up or down). */
    float yaw;
    /** The holder's view last tick, to turn the entity with the holder. */
    final Quaterniond lastView;
    /** The grabbed point's velocity, blocks per second (server-simulated entities). */
    Vec3 velocity;

    /** What the hold changed on the entity, to put back when it ends (null: nothing changed). */
    @Nullable Boolean savedNoGravity;
    @Nullable Boolean savedCanUseRail;

    int ticks;

    /**
     * @param startOrientation the orientation to carry on from (a hold taken over from another
     *                         player, or a frozen entity's kept pose); null to start from how the
     *                         entity is drawn now
     */
    Hold(ServerPlayer holder, Entity entity, boolean requirePhysgun, Mode mode, double spring, double damping,
         double distance, Vec3 grabOffset, Vec3 velocity, @Nullable Quaterniondc startOrientation) {
        this.holderId = holder.getUUID();
        this.holder = holder;
        this.entity = entity;
        this.requirePhysgun = requirePhysgun;
        this.mode = mode;
        this.spring = spring;
        this.damping = damping;
        this.distance = distance;
        this.grabOffset = grabOffset;
        this.orientation = startOrientation != null ? new Quaterniond(startOrientation).normalize() : ModelPose.base(entity, 1.0F);
        this.grabOrientationInverse = orientation.conjugate(new Quaterniond());
        this.yaw = HoldMotion.yaw(orientation, entity.getYRot());
        this.lastView = Holds.view(holder);
        this.velocity = velocity;
    }

    public ServerPlayer holder() {
        return holder;
    }

    public Entity entity() {
        return entity;
    }

    public Mode mode() {
        return mode;
    }

    public double distance() {
        return distance;
    }

    public double spring() {
        return spring;
    }

    public double damping() {
        return damping;
    }

    /** The grabbed point's velocity, blocks per second. */
    public Vec3 velocity() {
        return velocity;
    }

    public int ticks() {
        return ticks;
    }

    /** The heading the hold gives the entity (degrees). */
    public float yaw() {
        return yaw;
    }

    /** The ideal orientation of the entity's model (world frame; see {@link ModelPose}). */
    public Quaterniondc orientation() {
        return orientation;
    }

    /** How the entity has been turned since the grab (world frame). */
    public Quaterniond turnSinceGrab() {
        return orientation.mul(grabOrientationInverse, new Quaterniond()).normalize();
    }

    /** From the entity's position to the grabbed point now: the grab offset, turned with the entity. */
    public Vec3 offset() {
        Vector3d turned = turnSinceGrab().transform(new Vector3d(grabOffset.x, grabOffset.y, grabOffset.z));
        return new Vec3(turned.x, turned.y, turned.z);
    }

    /** Where the holder is pulling the grabbed point to: VMod's eye position plus look times distance. */
    public Vec3 target() {
        return holder.getEyePosition().add(holder.getLookAngle().scale(distance));
    }

    /** Where the pull brings the entity's own position (the target less the offset). */
    public Vec3 entityTarget() {
        return target().subtract(offset());
    }

    void turnBy(Quaterniondc worldRotation) {
        worldRotation.mul(orientation, orientation);
        orientation.normalize();
        yaw = HoldMotion.yaw(orientation, yaw);
    }
}
