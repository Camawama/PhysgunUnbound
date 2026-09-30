package net.camacraft.physgununbound.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.physgununbound.client.ClientHeld;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * To the one client that moves a held entity (a held player's own client, or the client of a
 * player driving a held vehicle): where the entity's position is pulled to this tick, and the
 * hold's spring, damper and top speed. Sent every server tick while the hold lasts, then once with
 * {@code active = false}. The client runs the same spring as the server runs for mobs
 * ({@code hold.HoldMotion}), from its own position, so there is no round trip in the loop.
 *
 * @param entityId entity id of the held entity (the player, or the vehicle they drive)
 * @param spring   1/s²
 * @param damping  1/s
 * @param maxSpeed blocks per second
 */
public record HeldMotionMessage(int entityId, double targetX, double targetY, double targetZ,
                                float spring, float damping, float maxSpeed, boolean active) {

    public static void encode(HeldMotionMessage m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeBoolean(m.active);
        if (m.active) {
            buf.writeDouble(m.targetX);
            buf.writeDouble(m.targetY);
            buf.writeDouble(m.targetZ);
            buf.writeFloat(m.spring);
            buf.writeFloat(m.damping);
            buf.writeFloat(m.maxSpeed);
        }
    }

    public static HeldMotionMessage decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean active = buf.readBoolean();
        if (!active) return new HeldMotionMessage(entityId, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, false);
        return new HeldMotionMessage(entityId, buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), true);
    }

    public static void handle(HeldMotionMessage m, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> ClientHeld.onMessage(m));
    }
}
