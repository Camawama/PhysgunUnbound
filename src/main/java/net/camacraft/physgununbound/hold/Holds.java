package net.camacraft.physgununbound.hold;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.camafy.compat.vs.ShipEntities;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.camafy.network.NetValidate;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.PuConfig;
import net.camacraft.physgununbound.network.PuNetwork;
import net.camacraft.physgununbound.vmod.Vmod;
import net.camacraft.physgununbound.vmod.VmodAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every physgun hold on an entity, and what happens to it each tick. Server thread only, apart
 * from {@link #isHolding}, which VMod's packet hook asks from whichever thread VMod's handler runs on
 * (the server thread unless {@code [vmod] messagesOnServerThread} is off).
 *
 * <p>Each server tick, before any level ticks (and before VMod's own physgun tick, which runs at
 * a lower priority on the same event):
 * <ol>
 *   <li>the physgun inputs VMod received since last tick are applied in order (let go, freeze,
 *   the wheel, the rotate drag);</li>
 *   <li>holds that can't go on end (the holder died, left, changed dimension, put the physgun
 *   away; the entity died or was removed);</li>
 *   <li>players VMod is "seeking" for (left button held, nothing grabbed yet) are checked for an
 *   entity in front of the first block: if there is one it is grabbed, and VMod is told to stop
 *   seeking, so it can't also grab a ship behind it;</li>
 *   <li>every hold moves its entity one tick along ({@link HoldMotion}), and the beam is sent.</li>
 * </ol>
 * The levels tick afterwards, so what a hold did goes out to the clients in the same tick.
 */
public final class Holds {
    /** How a hold ended. */
    public enum End {
        /** Let go (left button up): the entity keeps its speed, like a throw. */
        RELEASED,
        /** Frozen where it is. */
        FROZEN,
        /** The holder can't hold any more (dead, gone, physgun put away): as {@link #RELEASED}. */
        LOST,
        /** The entity is gone (dead, removed, unloaded, changed dimension): nothing to put back. */
        GONE,
        /** Another player took it: the new hold carries on from here. */
        STOLEN
    }

    /** By holder. Insertion order, so holds tick in the order they started. */
    private static final Map<UUID, Hold> BY_HOLDER = new LinkedHashMap<>();
    /** The holders, readable from any thread. */
    private static final Set<UUID> HOLDERS = ConcurrentHashMap.newKeySet();

    private Holds() {}

    // ------------------------------------------------------------------ queries

    /** Whether this player holds an entity. Any thread. */
    public static boolean isHolding(UUID player) {
        return HOLDERS.contains(player);
    }

    public static @Nullable Hold holdOf(Player holder) {
        return BY_HOLDER.get(holder.getUUID());
    }

    /** The hold on this entity, if any. */
    public static @Nullable Hold holdOn(Entity entity) {
        for (Hold hold : BY_HOLDER.values()) {
            if (hold.entity == entity) return hold;
        }
        return null;
    }

    public static Collection<Hold> all() {
        return Collections.unmodifiableCollection(BY_HOLDER.values());
    }

    // ------------------------------------------------------------------ grabbing

    /**
     * {@code holder} grabs {@code hit} (or what it rides: see {@link Grabbing#resolve}) at
     * {@code hitPoint}. Returns the new hold, or null if {@link Grabbing#refusal} says no.
     *
     * @param requirePhysgun whether the hold ends when VMod's physgun leaves the holder's main hand
     * @param spring         VMod's spring constant for this player (1/s²)
     * @param damping        VMod's damper constant (1/s)
     */
    public static @Nullable Hold grab(ServerPlayer holder, Entity hit, Vec3 hitPoint, boolean requirePhysgun,
                                      double spring, double damping) {
        Entity entity = Grabbing.resolve(hit);
        String refusal = Grabbing.refusal(holder, entity);
        if (refusal != null) {
            PhysgunUnbound.LOGGER.debug("{} can't pick up {}: {}", holder.getGameProfile().getName(), entity, refusal);
            return null;
        }
        Hold mine = BY_HOLDER.get(holder.getUUID());
        if (mine != null) end(mine, End.RELEASED);
        Hold theirs = holdOn(entity);
        // a tilted entity taken over, or grabbed out of a freeze, carries on from the pose it had
        Quaterniond startOrientation = theirs != null ? new Quaterniond(theirs.orientation) : ModelPose.storedFrozen(entity);
        if (theirs != null) end(theirs, End.STOLEN);
        Freezing.unfreeze(entity, false); // grabbing a frozen entity unfreezes it, as grabbing a static ship does

        PuConfig.Server cfg = PuConfig.SERVER;
        Vec3 eye = holder.getEyePosition();
        double scale = HolderScale.of(holder); // in the holder's own blocks: 1/16 in the Great Unknown
        double distance = Mth.clamp(hitPoint.distanceTo(eye), cfg.minDistance() * scale, cfg.maxDistance() * scale);
        Hold.Mode mode = isClientSimulated(entity) ? Hold.Mode.CLIENT : Hold.Mode.SERVER;
        Vec3 velocity = EntityScale.realVelocity(entity, Gravity.getWorldVelocity(entity)).scale(20.0);
        Hold hold = new Hold(holder, entity, requirePhysgun, mode,
                spring > 0.0 && Double.isFinite(spring) ? spring : cfg.fallbackSpring(),
                damping >= 0.0 && Double.isFinite(damping) ? damping : cfg.fallbackDamping(),
                distance, hitPoint.subtract(entity.position()), velocity, startOrientation);

        if (mode == Hold.Mode.SERVER) {
            if (!(entity instanceof LivingEntity) && !entity.isNoGravity()) {
                // items, carts, boats, falling blocks: their clients would draw them falling between updates
                hold.savedNoGravity = false;
                Freezing.markHeld(entity);
                entity.setNoGravity(true);
            }
            if (entity instanceof AbstractMinecart cart) {
                // a cart near a rail would snap itself back onto it every tick
                hold.savedCanUseRail = cart.canUseRail();
                cart.setCanUseRail(false);
            }
            HeldEntities.moveOnlyByHold(entity);
            ShipEntities.letGo(entity, ShipRef.NO_ID, false);
        } else if (entity instanceof ServerPlayer player && player.isFallFlying()) {
            player.stopFallFlying();
        }
        HeldEntities.claim(entity);
        entity.resetFallDistance();

        BY_HOLDER.put(hold.holderId, hold);
        HOLDERS.add(hold.holderId);
        return hold;
    }

    /** Ends a hold. See {@link End} for what each reason leaves the entity with. */
    public static void end(Hold hold, End reason) {
        if (BY_HOLDER.get(hold.holderId) != hold) return;
        BY_HOLDER.remove(hold.holderId);
        HOLDERS.remove(hold.holderId);

        Entity entity = hold.entity;
        HeldEntities.unclaim(entity);
        HeldEntities.releaseMovement(entity);
        if (hold.mode == Hold.Mode.SERVER) {
            if (hold.savedNoGravity != null) entity.setNoGravity(hold.savedNoGravity);
            Freezing.clearHeldMark(entity);
            if (hold.savedCanUseRail != null && entity instanceof AbstractMinecart cart) cart.setCanUseRail(hold.savedCanUseRail);
            if (reason != End.GONE) {
                Vec3 perTick = reason == End.FROZEN ? Vec3.ZERO : hold.velocity.scale(0.05);
                // world axes: let go inside a gravity field it leaves with the throw's speed, not the throw turned by the field
                Gravity.setWorldVelocity(entity, EntityScale.velocityFor(entity, perTick));
                entity.hasImpulse = true;
            }
        } else {
            // the client that moves it keeps the speed it had; it only has to stop pulling (also
            // when the player changed dimension, where the old target means nothing)
            ServerPlayer simulator = simulator(entity);
            if (simulator != null && !simulator.hasDisconnected()) PuNetwork.sendHeldEnd(simulator, entity);
        }
        PuNetwork.sendBeamEnd(hold);
    }

    /** Freezes the held entity where it is and lets go (right or middle click while holding). */
    public static void freeze(Hold hold) {
        Entity entity = hold.entity;
        if (hold.mode != Hold.Mode.SERVER || !Freezing.canFreeze(entity)) {
            end(hold, End.RELEASED); // a player, or a vehicle a player drives, can't be frozen
            return;
        }
        boolean originalNoGravity = hold.savedNoGravity != null ? hold.savedNoGravity : entity.isNoGravity();
        hold.savedNoGravity = null; // freezing keeps gravity off
        end(hold, End.FROZEN);
        Freezing.freeze(entity, originalNoGravity);
        Freezing.keepPose(entity, hold.orientation); // it hangs there turned the way it was held
    }

    /** Ends every hold by or on this entity (it logged out, died, changed dimension or left its level). */
    public static void endInvolving(Entity entity, End asHolder, End asHeld) {
        if (BY_HOLDER.isEmpty()) return; // (called for every entity that leaves a level)
        for (Hold hold : new ArrayList<>(BY_HOLDER.values())) {
            if (hold.holder == entity) end(hold, asHolder);
            else if (hold.entity == entity) end(hold, asHeld);
        }
    }

    /** Ends every hold (server stopping, or the mod switched off). Entities keep their speed. */
    public static void endAll(End reason) {
        for (Hold hold : new ArrayList<>(BY_HOLDER.values())) end(hold, reason);
    }

    /** Server stopped: forget everything. */
    public static void clear() {
        endAll(End.RELEASED);
        PhysgunInput.Queues.clear();
        HeldEntities.clearServer();
    }

    // ------------------------------------------------------------------ input

    /**
     * Applies one physgun input from {@code holder}, as VMod applies it to a held ship: let go,
     * else freeze (right or middle click), else unfreeze (R), else turn by the rotate drag and
     * move by the wheel.
     */
    public static void applyInput(ServerPlayer holder, PhysgunInput input) {
        Hold hold = BY_HOLDER.get(holder.getUUID());
        if (hold == null) {
            if (!input.primary() && input.unfreeze()) unfreezeLookedAt(holder);
            return;
        }
        if (!input.primary()) {
            end(hold, End.RELEASED);
        } else if (input.freezeSelected() || input.freezeAll()) {
            freeze(hold);
        } else if (input.unfreeze()) {
            // VMod unfreezes the held ship's connected ships; an entity's are its riders and mount
            for (Entity rider : hold.entity.getIndirectPassengers()) Freezing.unfreeze(rider);
        } else {
            Quaterniond turn = input.rotation();
            if (turn != null && isTurnable(hold.entity) && isUnitQuaternion(turn)) hold.turnBy(new Quaterniond(turn).normalize());
            double wheel = NetValidate.finiteClamp(input.distanceDelta(), -64.0, 64.0, 0.0);
            if (wheel != 0.0) {
                // a notch is one of the holder's own blocks (1/16 of a block in the Great Unknown)
                PuConfig.Server cfg = PuConfig.SERVER;
                double scale = HolderScale.of(holder);
                hold.distance = Mth.clamp(hold.distance + wheel * scale, cfg.minDistance() * scale, cfg.maxDistance() * scale);
            }
        }
    }

    /**
     * R with nothing held: unfreeze the frozen entity the physgun points at, if there is one (and no
     * minigame protects it).
     */
    public static boolean unfreezeLookedAt(ServerPlayer player) {
        Grabbing.Pick pick = Grabbing.pick(player, PuConfig.SERVER.range(), Freezing::isFrozen);
        return pick != null && MatchPolicies.canHarm(player, pick.entity()) && Freezing.unfreeze(pick.entity());
    }

    // ------------------------------------------------------------------ tick

    /** One server tick, at the start of the tick (see the class comment). */
    public static void tick(MinecraftServer server) {
        // before VMod's own physgun tick, and whether or not entity holds are on: a ship VMod holds that is gone
        // would kick its holder at their next physgun message
        if (!Vmod.failed()) Vmod.access().letGoOfMissingShips();
        if (!PuConfig.SERVER.enabled() || Vmod.failed()) {
            if (!BY_HOLDER.isEmpty()) endAll(End.RELEASED);
            PhysgunInput.Queues.clear();
            return;
        }
        PhysgunInput.Queues.drain((player, input) -> {
            if (player.isRemoved() || player.hasDisconnected()) return;
            // VMod's client only sends while the physgun is in hand; a message from anyone else (a
            // modified client) may still end their own hold, but starts nothing
            if (BY_HOLDER.containsKey(player.getUUID()) || Vmod.isPhysgun(player.getMainHandItem())) applyInput(player, input);
        });
        for (Hold hold : new ArrayList<>(BY_HOLDER.values())) {
            End reason = whyItEnds(hold);
            if (reason != null) end(hold, reason);
        }
        seek(server);
        for (Hold hold : new ArrayList<>(BY_HOLDER.values())) tickHold(hold);
    }

    private static @Nullable End whyItEnds(Hold hold) {
        Entity entity = hold.entity;
        ServerPlayer holder = hold.holder;
        if (entity.isRemoved() || !entity.isAlive()) return End.GONE;
        if (holder.isRemoved() || !holder.isAlive() || holder.hasDisconnected() || holder.isSpectator()) return End.LOST;
        if (holder.level() != entity.level()) return End.LOST;
        if (hold.requirePhysgun && !Vmod.isPhysgun(holder.getMainHandItem())) return End.LOST;
        if (entity.isPassenger()) return End.LOST; // it mounted something: the hold only ever moves whole stacks
        Hold.Mode now = isClientSimulated(entity) ? Hold.Mode.CLIENT : Hold.Mode.SERVER;
        if (now != hold.mode) return End.LOST; // someone took the reins of a held boat, or left them
        return null;
    }

    /** VMod is seeking for these players: grab an entity in front of whatever it would find. */
    private static void seek(MinecraftServer server) {
        VmodAccess vmod = Vmod.access();
        if (!vmod.working()) return;
        PuConfig.Server cfg = PuConfig.SERVER;
        for (UUID id : vmod.seekingPlayers()) {
            ServerPlayer listed = server.getPlayerList().getPlayer(id);
            ServerPlayer player = listed != null ? listed : vmod.player(id); // a fake player isn't listed
            if (player == null || player.isRemoved() || player.isSpectator() || !Vmod.isPhysgun(player.getMainHandItem())) continue;
            if (BY_HOLDER.containsKey(id)) {
                // already holding: VMod's seeking must not run behind the hold's back
                vmod.stopSeeking(id);
                continue;
            }
            if (vmod.holdingShip(id)) continue;
            Grabbing.Pick pick = Grabbing.pick(player, cfg.range(), e -> Grabbing.refusal(player, e) == null);
            if (pick == null) continue;
            double spring = vmod.spring(id, cfg.fallbackSpring());
            double damping = vmod.damping(id, cfg.fallbackDamping());
            // Never under VMod's lock (its handler may hold it while it waits for this thread: the 2026-10-30 freeze).
            // With VMod's messages handled on the server thread none can come between the hold starting and its
            // seeking stopping; without, one that slips a "seek again" in is undone just above next tick.
            Hold hold = grab(player, pick.entity(), pick.point(), true, spring, damping);
            if (hold != null) vmod.stopSeeking(id);
        }
    }

    private static void tickHold(Hold hold) {
        Entity entity = hold.entity;
        ServerPlayer holder = hold.holder;
        PuConfig.Server cfg = PuConfig.SERVER;

        // turn with the holder, as VMod turns a held ship with the player's view: by the change in
        // the view since last tick (its yaw, and its pitch too unless the config keeps entities level)
        if (isTurnable(entity)) {
            Quaterniond view = view(holder);
            Quaterniond turn = view.mul(hold.lastView.conjugate(new Quaterniond()), new Quaterniond());
            hold.lastView.set(view);
            if (Math.abs(turn.w) < 1.0 - 1.0E-12) hold.turnBy(turn.normalize());
        }

        Vec3 target = hold.target();
        Vec3 offset = hold.offset();
        double maxSpeed = cfg.maxSpeed() * HolderScale.of(holder); // the holder's blocks per second
        if (hold.mode == Hold.Mode.SERVER) {
            moveOnServer(hold, entity, target, offset, maxSpeed);
            ModelPose.applyFacing(entity, hold.orientation, hold.yaw);
        } else {
            moveOnClient(hold, entity, target.subtract(offset), maxSpeed);
        }
        entity.resetFallDistance();
        for (Entity rider : entity.getIndirectPassengers()) rider.resetFallDistance();
        hold.ticks++;
        PuNetwork.sendBeam(hold, offset);
    }

    private static void moveOnServer(Hold hold, Entity entity, Vec3 target, Vec3 offset, double maxSpeed) {
        Vec3 grabbed = entity.position().add(offset);
        HoldMotion.Step step = HoldMotion.step(grabbed, hold.velocity, target, hold.spring, hold.damping, maxSpeed);
        Vec3 planned = step.displacement(grabbed);
        Vec3 before = entity.position();
        Vec3 destination = before.add(planned);
        if (!entity.level().getChunkSource().hasChunk(SectionPos.posToSectionCoord(destination.x), SectionPos.posToSectionCoord(destination.z))) {
            // never into a chunk that isn't loaded: it waits at the edge
            hold.velocity = Vec3.ZERO;
            entity.setDeltaMovement(Vec3.ZERO);
            keepAlive(entity);
            return;
        }
        HeldEntities.move(entity, EntityScale.velocityFor(entity, planned));
        Vec3 moved = entity.position().subtract(before);

        // A block (or a ship's hull) stopped part of the move: that part of the speed is gone,
        // as a held ship's is when it hits something.
        Vec3 velocity = step.vel();
        double vx = blocked(moved.x, planned.x) ? moved.x * 20.0 : velocity.x;
        double vy = blocked(moved.y, planned.y) ? moved.y * 20.0 : velocity.y;
        double vz = blocked(moved.z, planned.z) ? moved.z * 20.0 : velocity.z;
        hold.velocity = new Vec3(vx, vy, vz);

        Gravity.setWorldVelocity(entity, EntityScale.velocityFor(entity, hold.velocity.scale(0.05)));
        entity.hasImpulse = true; // send the new position (and speed) this tick
        if (ShipEntities.isCarried(entity)) ShipEntities.letGo(entity, ShipRef.NO_ID, false);
        keepAlive(entity);
    }

    private static boolean blocked(double moved, double planned) {
        return Math.abs(moved - planned) > 1.0E-3;
    }

    /** Things an entity does on its own that a hold must not let happen. */
    private static void keepAlive(Entity entity) {
        // a falling block drops as an item after 30 s in the air (600 ticks); 0 would make it
        // delete the block at its position again, as on its first tick
        if (entity instanceof FallingBlockEntity falling && falling.time > 1) falling.time = 1;
        // the holder would pick a held item up the moment it came near
        if (entity instanceof ItemEntity item) item.setPickUpDelay(10);
    }

    /** A player, or what a player drives, is moved by that player's own client. */
    private static void moveOnClient(Hold hold, Entity entity, Vec3 entityTarget, double maxSpeed) {
        ServerPlayer simulator = simulator(entity);
        if (simulator == null) return;
        if (PuNetwork.hasChannel(simulator)) {
            PuNetwork.sendHeldMotion(simulator, entity, entityTarget, hold.spring, hold.damping, maxSpeed);
            return;
        }
        // A client without this mod: steer it with vanilla motion packets, working from the
        // position it last reported. Laggier than its own client doing it, but it still holds.
        HoldMotion.Step step = HoldMotion.step(entity.position(), hold.velocity, entityTarget, hold.spring, hold.damping, maxSpeed);
        hold.velocity = step.vel();
        Gravity.setWorldVelocity(entity, EntityScale.velocityFor(entity, hold.velocity.scale(0.05)));
        if (entity == simulator) {
            simulator.connection.send(new ClientboundSetEntityMotionPacket(simulator));
        } else {
            entity.hurtMarked = true; // a driven vehicle: its motion goes to everyone tracking it, driver included
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The holder's view as a rotation (VMod's {@code playerRotToQuat}): its yaw, and its pitch
     * unless the config keeps held entities level when the holder looks up and down.
     */
    public static Quaterniond view(ServerPlayer holder) {
        return HoldMotion.rotationOf(holder.getYRot(), PuConfig.SERVER.followViewPitch() ? holder.getXRot() : 0.0F);
    }

    /** Whether a player's client, not the server, moves this entity: a player, or a vehicle a player drives. */
    public static boolean isClientSimulated(Entity entity) {
        return entity instanceof Player || entity.getControllingPassenger() instanceof Player;
    }

    /** The player whose client moves this entity, or null if the server does. */
    static @Nullable ServerPlayer simulator(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        return entity.getControllingPassenger() instanceof ServerPlayer driver ? driver : null;
    }

    /** Players pick their own facing; everything else turns with the hold. */
    public static boolean isTurnable(Entity entity) {
        return !isClientSimulated(entity);
    }

    private static boolean isUnitQuaternion(Quaterniond q) {
        if (!Double.isFinite(q.x) || !Double.isFinite(q.y) || !Double.isFinite(q.z) || !Double.isFinite(q.w)) return false;
        return Math.abs(q.lengthSquared() - 1.0) < 0.05;
    }

}
