package net.camacraft.physgununbound.client;

import net.camacraft.physgununbound.PuConfig;
import net.camacraft.physgununbound.hold.ModelPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * How held and frozen entities are drawn turned on this client. The server sends an entity's
 * orientation with every beam message while it is held, and once when it is frozen; this keeps
 * the last two, blends between them for the frame, and takes the entity's own renderer turn out
 * ({@link ModelPose#modelRotation}), which the render hook applies about the entity's centre.
 *
 * <p>When the hold ends (its beam ends, or nothing arrives for half a second) or the entity is
 * unfrozen, the turn eases back to nothing over {@code rotation.returnTicks} (client config), so a
 * cow let go upside down rolls upright instead of snapping. A frozen entity keeps its turn until
 * it is unfrozen or gone.
 */
public final class ClientModelRotations {
    /** A held entity's turn not refreshed for this many ticks has been let go (its beam end was missed). */
    private static final int TIMEOUT = 10;
    /** Turns smaller than this (radians) aren't worth a pose push. */
    private static final float NOTHING = 0.0005F;

    private static final class State {
        final Quaternionf previous = new Quaternionf();
        final Quaternionf current = new Quaternionf();
        /** The client tick the current orientation arrived. */
        int arrived;
        /** The tick it was last confirmed (held: every tick; frozen: never times out). */
        int seen;
        boolean frozen;
        /** Set once let go: the orientation to ease back from, and when the easing began. */
        @Nullable Quaternionf releasedFrom;
        int releasedAt;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static int ticks;

    private ClientModelRotations() {}

    /** A held entity's orientation this tick. */
    public static void held(int entityId, Quaternionf orientation) {
        State state = STATES.get(entityId);
        if (state == null) {
            state = new State();
            state.previous.set(orientation);
            STATES.put(entityId, state);
        } else {
            state.previous.set(state.releasedFrom != null ? state.releasedFrom : state.current);
        }
        state.current.set(orientation);
        state.arrived = ticks;
        state.seen = ticks;
        state.frozen = false;
        state.releasedFrom = null;
    }

    /** A frozen entity's kept orientation: it stays until {@link #release}. */
    public static void pin(int entityId, Quaternionf orientation) {
        State state = STATES.computeIfAbsent(entityId, id -> new State());
        if (state.releasedFrom != null || state.frozen || state.seen == 0) {
            state.previous.set(orientation); // no blend from a turn already eased, or none
        } else {
            state.previous.set(state.current);
        }
        state.current.set(orientation);
        state.arrived = ticks;
        state.seen = ticks;
        state.frozen = true;
        state.releasedFrom = null;
    }

    /** The entity was let go or unfrozen: its turn eases back to nothing. */
    public static void release(int entityId) {
        State state = STATES.get(entityId);
        if (state == null || state.releasedFrom != null) return;
        if (PuConfig.CLIENT.returnTicks() <= 0) {
            STATES.remove(entityId);
            return;
        }
        state.releasedFrom = new Quaternionf(state.current);
        state.releasedAt = ticks;
        state.frozen = false;
    }

    public static void tick() {
        ticks++;
        if (STATES.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        int returnTicks = PuConfig.CLIENT.returnTicks();
        for (Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, State> entry = it.next();
            State state = entry.getValue();
            if (level == null || level.getEntity(entry.getKey()) == null) {
                it.remove();
            } else if (state.releasedFrom != null) {
                if (ticks - state.releasedAt > returnTicks) it.remove();
            } else if (!state.frozen && ticks - state.seen > TIMEOUT) {
                release(entry.getKey());
            }
        }
    }

    public static void clear() {
        STATES.clear();
    }

    /**
     * The turn to draw {@code entity} with this frame (world frame, about its centre), or null for
     * none.
     */
    public static @Nullable Quaternionf rotation(Entity entity, float partialTick) {
        State state = STATES.get(entity.getId());
        if (state == null) return null;
        Quaternionf orientation;
        float back = 0.0F; // how far along the way back upright
        if (state.releasedFrom != null) {
            int returnTicks = PuConfig.CLIENT.returnTicks();
            float t = returnTicks <= 0 ? 1.0F : (ticks + partialTick - state.releasedAt) / returnTicks;
            if (t >= 1.0F) return null;
            back = Mth.clamp(t, 0.0F, 1.0F);
            back = back * back * (3.0F - 2.0F * back); // smoothstep: no jolt at either end
            orientation = state.releasedFrom;
        } else {
            float t = Mth.clamp(ticks + partialTick - state.arrived, 0.0F, 1.0F);
            orientation = state.previous.slerp(state.current, t, new Quaternionf());
        }
        Quaterniond base = ModelPose.base(entity, partialTick);
        Quaternionf turn = new Quaternionf(orientation).mul(new Quaternionf(base).conjugate()).normalize();
        if (back > 0.0F) turn.slerp(new Quaternionf(), back).normalize();
        return Math.abs(turn.angle()) < NOTHING ? null : turn;
    }
}
