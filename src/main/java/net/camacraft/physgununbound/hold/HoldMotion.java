package net.camacraft.physgununbound.hold;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * The physgun's hold, as VMod applies it to a ship, for one entity.
 *
 * <p>VMod's {@code PhysgunController} pulls the grabbed point of a ship toward the point in front
 * of the player with a spring and a damper, on Valkyrien Skies' physics thread (60 steps a second):
 * {@code force = (spring * (target - grabbedPoint) - damping * velocity) * mass}. The mass cancels
 * out, so every ship, light or heavy, accelerates the same way: with VMod's defaults (160 and 20)
 * it settles in about a third of a second, barely overshooting. {@link #step} runs the same law
 * for an entity, three 1/60 s steps per game tick, in blocks and blocks per second.
 *
 * <p>The turn works the same way: VMod keeps an ideal rotation that turns with the player (each
 * tick by the change in the player's view) and with the rotate key's mouse drags. Entities can
 * only show a heading (and a head pitch), so {@link #yaw} and {@link #pitch} read those out of the
 * ideal rotation.
 */
public final class HoldMotion {
    /** VS's physics runs three steps per game tick. */
    public static final int SUBSTEPS = 3;
    public static final double DT = 1.0 / 60.0;

    private HoldMotion() {}

    /** Where the grabbed point is, and how fast it moves (blocks per second), after one game tick. */
    public record Step(Vec3 pos, Vec3 vel) {
        /** The movement over the tick, in blocks. */
        public Vec3 displacement(Vec3 from) {
            return pos.subtract(from);
        }
    }

    /**
     * One game tick of the hold: the grabbed point at {@code pos}, moving at {@code vel} (blocks per
     * second), pulled toward {@code target}. The speed never exceeds {@code maxSpeed}.
     */
    public static Step step(Vec3 pos, Vec3 vel, Vec3 target, double spring, double damping, double maxSpeed) {
        double px = pos.x, py = pos.y, pz = pos.z;
        double vx = vel.x, vy = vel.y, vz = vel.z;
        double maxSq = maxSpeed * maxSpeed;
        for (int i = 0; i < SUBSTEPS; i++) {
            vx += (spring * (target.x - px) - damping * vx) * DT;
            vy += (spring * (target.y - py) - damping * vy) * DT;
            vz += (spring * (target.z - pz) - damping * vz) * DT;
            double sq = vx * vx + vy * vy + vz * vz;
            if (sq > maxSq) {
                double k = maxSpeed / Math.sqrt(sq);
                vx *= k;
                vy *= k;
                vz *= k;
            }
            px += vx * DT;
            py += vy * DT;
            pz += vz * DT;
        }
        return new Step(new Vec3(px, py, pz), new Vec3(vx, vy, vz));
    }

    // ------------------------------------------------------------------ orientation

    /**
     * The rotation that faces an entity's way: VMod's {@code playerRotToQuat}, which is also what
     * the camera's {@code rotation()} is. It turns +Z into the entity's look vector.
     */
    public static Quaterniond rotationOf(float yawDegrees, float pitchDegrees) {
        return new Quaterniond().rotateY(Math.toRadians(-yawDegrees)).rotateX(Math.toRadians(pitchDegrees));
    }

    /** A turn about the vertical by {@code deltaYawDegrees} (Minecraft's sense: positive turns right). */
    public static Quaterniond yawTurn(double deltaYawDegrees) {
        return new Quaterniond().rotationY(Math.toRadians(-deltaYawDegrees));
    }

    /**
     * The heading (Minecraft yaw, degrees) of a rotation: its forward axis laid flat. When that
     * axis points straight up or down, the heading is read from the up axis instead, so it doesn't
     * flip about as the rotation passes the vertical. {@code fallback} if neither says anything.
     */
    public static float yaw(Quaterniondc rotation, float fallback) {
        Vector3d look = rotation.transform(new Vector3d(0.0, 0.0, 1.0));
        Vector3d up = rotation.transform(new Vector3d(0.0, 1.0, 0.0));
        double fx = look.x - look.y * up.x;
        double fz = look.z - look.y * up.z;
        if (fx * fx + fz * fz < 1.0E-10) return fallback;
        return (float) Math.toDegrees(Math.atan2(-fx, fz));
    }

    /** The pitch (degrees, positive looks down) of a rotation's forward axis. */
    public static float pitch(Quaterniondc rotation) {
        Vector3d look = rotation.transform(new Vector3d(0.0, 0.0, 1.0));
        double y = Math.max(-1.0, Math.min(1.0, -look.y));
        return (float) Math.toDegrees(Math.asin(y));
    }

    /** {@code v} turned about the vertical by {@code deltaYawDegrees} (Minecraft's sense). */
    public static Vec3 turn(Vec3 v, double deltaYawDegrees) {
        if (deltaYawDegrees == 0.0) return v;
        double r = Math.toRadians(deltaYawDegrees);
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }
}
