package net.camacraft.physgununbound.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Hold;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's one channel. Both messages go to clients only, and the channel is optional on both
 * sides: a client without the mod can join (it just sees no beam to held entities, and is steered
 * with vanilla motion packets when held), and one with it can join servers without it.
 */
public final class PuNetwork {
    public static final CamafyChannel CHANNEL = CamafyChannel.createOptional(PhysgunUnbound.id("main"), "1");
    /** How far from the holder the beam is sent: past entity tracking range, the holder isn't drawn anyway. */
    private static final double BEAM_RANGE = 160.0;

    private PuNetwork() {}

    public static void init() {
        CHANNEL.clientbound(BeamMessage.class, BeamMessage::encode, BeamMessage::decode, BeamMessage::handle);
        CHANNEL.clientbound(HeldMotionMessage.class, HeldMotionMessage::encode, HeldMotionMessage::decode, HeldMotionMessage::handle);
    }

    public static boolean hasChannel(ServerPlayer player) {
        return CHANNEL.hasChannel(player);
    }

    /** The beam from the holder to the grabbed point, to every player near the holder that has the mod. */
    public static void sendBeam(Hold hold, Vec3 offset) {
        toNear(hold.holder(), new BeamMessage(hold.holder().getId(), hold.entity().getId(),
                (float) offset.x, (float) offset.y, (float) offset.z, true));
    }

    public static void sendBeamEnd(Hold hold) {
        toNear(hold.holder(), new BeamMessage(hold.holder().getId(), hold.entity().getId(), 0.0F, 0.0F, 0.0F, false));
    }

    /** Where the held entity's position is pulled to this tick, to the client that moves it. */
    public static void sendHeldMotion(ServerPlayer simulator, Entity entity, Vec3 target, double spring, double damping,
                                      double maxSpeed) {
        CHANNEL.toPlayer(simulator, new HeldMotionMessage(entity.getId(), target.x, target.y, target.z,
                (float) spring, (float) damping, (float) maxSpeed, true));
    }

    public static void sendHeldEnd(ServerPlayer simulator, Entity entity) {
        if (hasChannel(simulator)) {
            CHANNEL.toPlayer(simulator, new HeldMotionMessage(entity.getId(), 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, false));
        }
    }

    private static void toNear(ServerPlayer holder, Object message) {
        if (!(holder.level() instanceof ServerLevel level)) return;
        double rangeSq = BEAM_RANGE * BEAM_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(holder) <= rangeSq && hasChannel(player)) CHANNEL.toPlayer(player, message);
        }
    }
}
