package net.camacraft.physgununbound.hold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * How an entity's model is turned to show a hold's orientation: the part its own renderer can show
 * (a heading, or for an arrow a heading and a pitch) goes into the entity's rotation fields, so a
 * client without this mod and the entity's own code still see it face the right way; the rest, the
 * <b>model rotation</b>, is drawn by this mod's clients on top ({@code client.ClientModelRotations}).
 *
 * <p>A hold's {@code orientation} maps the model as its renderer draws it at rest (rotation fields
 * zero) to the world. The renderer's own turn for the fields is {@link #base}; what is left to draw
 * is {@code orientation * base^-1} ({@link #modelRotation}), a rotation about the entity's centre
 * in the world frame. At the grab the orientation IS the base, so nothing changes visually until
 * the entity is turned; afterwards the model shows exactly the orientation, whatever the client's
 * copy of the entity thinks its heading is (a held arrow's client aims it along its velocity every
 * tick, and the model rotation cancels that out).
 *
 * <p>The base is known only up to a fixed rotation (a renderer's 180-degree offsets, an arrow's
 * 45-degree roll), which cancels out: the same fixed factor is in the orientation from the grab.
 */
public final class ModelPose {
    /** What of an orientation an entity's own renderer shows. */
    public enum Kind {
        /** A heading, from its yaw (mobs, players, armor stands, boats, carts, and modded vehicles). */
        YAW,
        /** A heading and a pitch, the way arrows and tridents are drawn (their own yaw sense). */
        ARROW,
        /** Nothing: it is drawn the same whatever its fields say (items, orbs, falling blocks, TNT, billboarded projectiles). */
        NONE
    }

    /** Persistent entity data (saved with it): the orientation a frozen entity keeps. */
    static final String FROZEN_POSE = "physgununbound:frozen_pose";

    private ModelPose() {}

    public static Kind kindOf(Entity entity) {
        if (entity instanceof AbstractArrow) return Kind.ARROW;
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity
                || entity instanceof PrimedTnt || entity instanceof EndCrystal || entity instanceof ThrowableProjectile
                || entity instanceof AbstractHurtingProjectile || entity instanceof ShulkerBullet
                || entity instanceof LlamaSpit || entity instanceof FishingHook || entity instanceof EyeOfEnder
                || entity instanceof AreaEffectCloud || entity instanceof EvokerFangs) {
            return Kind.NONE;
        }
        return Kind.YAW;
    }

    /**
     * The turn the entity's renderer gives its model for its rotation fields as they are now
     * (interpolated by {@code partialTick} on a client; 1 on the server), in the world frame.
     */
    public static Quaterniond base(Entity entity, float partialTick) {
        switch (kindOf(entity)) {
            case ARROW: {
                float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
                float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
                // ArrowRenderer: rotate about Y by yaw - 90, then about Z by the pitch (the arrow points along +X).
                // (rotateY/rotateZ, not rotationX/Z: Minecraft's JOML 1.10.5 has rotationX write its cosine into z.)
                return new Quaterniond().rotateY(Math.toRadians(yaw - 90.0)).rotateZ(Math.toRadians(pitch));
            }
            case NONE:
                return new Quaterniond();
            default: {
                float yaw = entity instanceof LivingEntity living
                        ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                        : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
                return HoldMotion.yawTurn(yaw); // +Z turned to the heading: HoldMotion.rotationOf without the pitch
            }
        }
    }

    /** What is left for the client to draw: {@code orientation} less the renderer's own turn. */
    public static Quaterniond modelRotation(Quaterniondc orientation, Entity entity, float partialTick) {
        return orientation.mul(base(entity, partialTick).conjugate(), new Quaterniond()).normalize();
    }

    /**
     * Turns the entity's rotation fields to show as much of {@code orientation} as its renderer can
     * (server). {@code fallbackYaw} is the heading to keep when the orientation's forward axis points
     * straight up or down.
     */
    public static void applyFacing(Entity entity, Quaterniondc orientation, float fallbackYaw) {
        switch (kindOf(entity)) {
            case ARROW: {
                Vector3d along = orientation.transform(new Vector3d(1.0, 0.0, 0.0));
                double flat = Math.sqrt(along.x * along.x + along.z * along.z);
                entity.setYRot((float) Math.toDegrees(Math.atan2(along.x, along.z)));
                entity.setXRot((float) Math.toDegrees(Math.atan2(along.y, flat)));
                break;
            }
            case NONE:
                break;
            default: {
                float yaw = HoldMotion.yaw(orientation, fallbackYaw);
                entity.setYRot(yaw);
                if (entity instanceof LivingEntity living) {
                    living.setYHeadRot(yaw);
                    living.setYBodyRot(yaw);
                    living.setXRot(0.0F); // the pitch is the whole model's now, not the head's
                }
            }
        }
    }

    // ------------------------------------------------------------------ frozen poses

    /** Keeps {@code orientation} on the entity (saved with it) while it is frozen. */
    static void storeFrozen(Entity entity, Quaterniondc orientation) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(orientation.x()));
        list.add(DoubleTag.valueOf(orientation.y()));
        list.add(DoubleTag.valueOf(orientation.z()));
        list.add(DoubleTag.valueOf(orientation.w()));
        entity.getPersistentData().put(FROZEN_POSE, list);
    }

    /** The orientation a frozen entity keeps, or null if it has none (not frozen, or frozen facing its own way). */
    public static @Nullable Quaterniond storedFrozen(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(FROZEN_POSE, Tag.TAG_LIST)) return null;
        ListTag list = data.getList(FROZEN_POSE, Tag.TAG_DOUBLE);
        if (list.size() != 4) return null;
        Quaterniond q = new Quaterniond(list.getDouble(0), list.getDouble(1), list.getDouble(2), list.getDouble(3));
        if (!Double.isFinite(q.x) || !Double.isFinite(q.y) || !Double.isFinite(q.z) || !Double.isFinite(q.w)) return null;
        if (q.lengthSquared() < 1.0E-6) return null;
        return q.normalize();
    }

    static void clearFrozen(Entity entity) {
        entity.getPersistentData().remove(FROZEN_POSE);
    }
}
