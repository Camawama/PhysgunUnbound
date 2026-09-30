package net.camacraft.physgununbound.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.physgununbound.client.ClientModelRotations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Quaternionf;

import java.util.function.Supplier;

/**
 * How a frozen entity is turned: sent when it is frozen out of a hold (to the players near it),
 * to a player who starts seeing it, and once with {@code active = false} when it is unfrozen, so
 * it turns back upright.
 *
 * @param entityId    entity id of the frozen entity
 * @param orientation the orientation it keeps (world frame, see {@code hold.ModelPose})
 */
public record FrozenPoseMessage(int entityId, Quaternionf orientation, boolean active) {

    public static void encode(FrozenPoseMessage m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeBoolean(m.active);
        if (m.active) buf.writeQuaternion(m.orientation);
    }

    public static FrozenPoseMessage decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean active = buf.readBoolean();
        return new FrozenPoseMessage(entityId, active ? buf.readQuaternion() : new Quaternionf(), active);
    }

    public static void handle(FrozenPoseMessage m, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> {
            if (m.active()) ClientModelRotations.pin(m.entityId(), m.orientation());
            else ClientModelRotations.release(m.entityId());
        });
    }
}
