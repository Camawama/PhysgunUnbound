package net.camacraft.physgununbound.hold;

import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.physgununbound.PuConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * How big the physgun's holder is next to normal size: 1 normally, 1/16 in VS Genesis's Great Unknown, where Genesis
 * shrinks every player and mob (through Pehkui) and every ship to 1/16. The physgun's lengths are the holder's
 * ({@code scaleWithHolder}): how close it pulls things in and how far it pushes them out, how far one wheel notch moves
 * the held thing (a held ship too: VMod's own wheel step is scaled where VMod reads it, see
 * {@code vmod.VmodPackets}), and the fastest a hold moves and so throws. A shrunk world then handles like a full-size
 * one: at full-size lengths a 1/16 player's grab was pushed out to 16 of their own blocks, every small turn of the
 * view swept the held thing 16 times too far and too fast, and it was thrown with that speed.
 *
 * <p>Nothing else in a hold needs scaling: the spring and the damper (VMod's PCONST and DCONST) have no length in them,
 * and a hold follows the holder's view at the distance it was grabbed at, which is already the holder's own.
 *
 * <p>Read from the holder's size ({@link EntityScale#heightFactor}: Pehkui's scale, or any other resize), not its
 * motion scale, because these are lengths. Any thread that may read the entity: VMod's wheel is scaled on the network
 * thread.
 */
public final class HolderScale {
    /** Smaller or larger than this is taken as this (a size mod at its extremes). */
    private static final double MIN = 1.0 / 64.0;
    private static final double MAX = 64.0;

    private HolderScale() {}

    /** The holder's size next to normal for the hold's lengths (1 when {@code scaleWithHolder} is off). */
    public static double of(@Nullable Entity holder) {
        return PuConfig.SERVER.scaleWithHolder() ? size(holder) : 1.0;
    }

    /**
     * The holder's size next to normal, whatever the config: what the beams are drawn at (VMod's to a held ship,
     * {@code mixin.vmod.client.PhysgunRayRendererMixin}, and this mod's to a held entity, {@code client.ClientBeams}),
     * so a 1/16 player's beam is 1/16 as thick and leaves the gun 1/16 as far from the camera. 1 if it can't be read.
     */
    public static double size(@Nullable Entity holder) {
        if (holder == null) return 1.0;
        double size = EntityScale.heightFactor(holder);
        if (!(size > 0.0) || !Double.isFinite(size) || Math.abs(size - 1.0) < 1.0E-3) return 1.0;
        return Mth.clamp(size, MIN, MAX);
    }
}
