package net.camacraft.physgununbound.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.physgununbound.client.ClientBeams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Quaternionf;

import java.util.function.Supplier;

/**
 * A physgun beam from a holder to a held entity, and the way the entity is turned: sent every
 * tick while the hold lasts (so a client that comes into range picks it up, and one that misses
 * the end drops it after half a second), and once more with {@code active = false} when it ends.
 *
 * @param holderId    entity id of the holding player
 * @param entityId    entity id of the held entity
 * @param offsetX     from the held entity's position to the grabbed point (x)
 * @param orientation the hold's orientation of the entity's model (world frame, see {@code hold.ModelPose})
 */
public record BeamMessage(int holderId, int entityId, float offsetX, float offsetY, float offsetZ,
                          Quaternionf orientation, boolean active) {

    public static void encode(BeamMessage m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.holderId);
        buf.writeVarInt(m.entityId);
        buf.writeBoolean(m.active);
        if (m.active) {
            buf.writeFloat(m.offsetX);
            buf.writeFloat(m.offsetY);
            buf.writeFloat(m.offsetZ);
            buf.writeQuaternion(m.orientation);
        }
    }

    public static BeamMessage decode(FriendlyByteBuf buf) {
        int holderId = buf.readVarInt();
        int entityId = buf.readVarInt();
        boolean active = buf.readBoolean();
        if (!active) return new BeamMessage(holderId, entityId, 0.0F, 0.0F, 0.0F, new Quaternionf(), false);
        return new BeamMessage(holderId, entityId, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readQuaternion(), true);
    }

    public static void handle(BeamMessage m, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> ClientBeams.onMessage(m));
    }
}
