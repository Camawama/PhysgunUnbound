package net.camacraft.physgununbound.client;

import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.camafy.compat.vs.ShipEntities;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.physgununbound.hold.HeldEntities;
import net.camacraft.physgununbound.hold.HoldMotion;
import net.camacraft.physgununbound.network.HeldMotionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * This client's side of a hold on its own player (or on the vehicle it drives): only this client
 * moves that entity, so the server sends the target each tick and the pull runs here, from the
 * entity's real position. It is the same spring as the server's for mobs, with the velocity
 * measured from how far the entity really moved last tick (so a wall it ran into, or a key it
 * pressed, counts).
 *
 * <p>While held, the entity is claimed here too, so the movement mods that run on the client
 * (WallJump, Grapple Unbound, Full Stop's client side) stand down; and Valkyrien Skies stops
 * carrying it with a deck it was standing on. Letting go leaves it with the speed it had: a throw.
 */
public final class ClientHeld {
    /** Half a second without a word from the server ends the hold here (lag, or a lost end message). */
    private static final int TIMEOUT = 10;

    private static int entityId = -1;
    private static Vec3 target = Vec3.ZERO;
    private static double spring;
    private static double damping;
    private static double maxSpeed;
    private static int sinceMessage;
    private static @Nullable Vec3 lastPos;
    private static @Nullable Entity claimed;

    private ClientHeld() {}

    public static void onMessage(HeldMotionMessage m) {
        if (!m.active()) {
            if (m.entityId() == entityId) end();
            return;
        }
        if (m.entityId() != entityId) {
            end();
            entityId = m.entityId();
        }
        Vec3 next = new Vec3(m.targetX(), m.targetY(), m.targetZ());
        if (!Double.isFinite(next.x) || !Double.isFinite(next.y) || !Double.isFinite(next.z)) return;
        target = next;
        spring = m.spring();
        damping = m.damping();
        maxSpeed = m.maxSpeed();
        sinceMessage = 0;
    }

    public static void tick() {
        if (entityId == -1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !player.isAlive() || player.isSpectator() || ++sinceMessage > TIMEOUT) {
            end();
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (entity == null || !(entity == player || entity.getControllingPassenger() == player)) {
            end();
            return;
        }
        if (claimed != entity) {
            if (claimed != null) HeldEntities.unclaim(claimed);
            HeldEntities.claim(entity);
            claimed = entity;
        }
        Vec3 pos = entity.position();
        Vec3 velocity = lastPos != null
                ? pos.subtract(lastPos).scale(20.0)
                : EntityScale.realVelocity(entity, entity.getDeltaMovement()).scale(20.0);
        HoldMotion.Step step = HoldMotion.step(pos, velocity, target, spring, damping, maxSpeed);
        entity.setDeltaMovement(EntityScale.velocityFor(entity, step.vel().scale(0.05)));
        entity.resetFallDistance();
        if (ShipEntities.isCarried(entity)) ShipEntities.letGo(entity, ShipRef.NO_ID, false);
        lastPos = pos;
    }

    /** Ends the hold here: the entity keeps the speed it was given last. */
    public static void end() {
        if (claimed != null) HeldEntities.unclaim(claimed);
        claimed = null;
        entityId = -1;
        lastPos = null;
        sinceMessage = 0;
    }
}
