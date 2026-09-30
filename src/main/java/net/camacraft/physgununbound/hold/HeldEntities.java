package net.camacraft.physgununbound.hold;

import com.google.common.collect.MapMaker;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Which entities the physgun owns right now, as the rest of the pack sees it.
 *
 * <ul>
 *   <li><b>Claims</b> (Camafy's {@link PhysicsOwnership}): a held entity is claimed for every
 *   aspect, so Full Stop, Gravity Unbound, WallJump and the other movement mods leave it alone
 *   while it is held (no crash damage from being swung into a wall, no g-forces, no bounces). A
 *   frozen entity is claimed for {@code MOTION}. The client claims its own player while that
 *   player is held.</li>
 *   <li><b>Own movement:</b> an entity the server simulates moves only where the hold puts it.
 *   Its own tick still runs (a held creeper still fuses, a held zombie still swings), but its
 *   {@code Entity.move(SELF, ...)} is skipped ({@code mixin.EntityMoveMixin}). Frozen entities
 *   stay put the same way.</li>
 * </ul>
 * Entities are kept by identity in weak sets, so a client copy never matches a server copy (in
 * singleplayer both live in this JVM) and an entity nobody references anymore drops out.
 */
public final class HeldEntities {
    public static final ResourceLocation OWNER = PhysgunUnbound.id("physgun");

    private static final Set<Entity> CLAIMED = weakIdentitySet();
    private static final Set<Entity> FROZEN = weakIdentitySet();
    private static final Set<Entity> SERVER_MOVED = weakIdentitySet();
    /** Entries in {@link #SERVER_MOVED} and {@link #FROZEN}: the move hook's fast path. */
    private static final AtomicInteger BLOCKING = new AtomicInteger();
    /** Set while the hold itself moves an entity (server thread). */
    private static boolean ownMove;

    private HeldEntities() {}

    private static Set<Entity> weakIdentitySet() {
        // MapMaker's weak keys compare by identity.
        return Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());
    }

    /** Registers the standing claim with Camafy. Mod construction. */
    public static void registerClaims() {
        PhysicsOwnership.register(OWNER, entity -> {
            if (CLAIMED.contains(entity)) return PhysicsAspect.ALL;
            if (FROZEN.contains(entity)) return PhysicsAspect.MOTION.bit;
            return 0;
        });
    }

    // ------------------------------------------------------------------ claims

    public static void claim(Entity entity) {
        CLAIMED.add(entity);
    }

    public static void unclaim(Entity entity) {
        CLAIMED.remove(entity);
    }

    /** Whether the physgun holds this entity (server or client copy, whichever this is). */
    public static boolean isHeld(Entity entity) {
        return entity != null && CLAIMED.contains(entity);
    }

    // ------------------------------------------------------------------ own movement

    static void moveOnlyByHold(Entity entity) {
        if (SERVER_MOVED.add(entity)) BLOCKING.incrementAndGet();
    }

    static void releaseMovement(Entity entity) {
        if (SERVER_MOVED.remove(entity)) BLOCKING.decrementAndGet();
    }

    static void markFrozen(Entity entity) {
        if (FROZEN.add(entity)) BLOCKING.incrementAndGet();
    }

    static void unmarkFrozen(Entity entity) {
        if (FROZEN.remove(entity)) BLOCKING.decrementAndGet();
    }

    public static boolean isMarkedFrozen(Entity entity) {
        return FROZEN.contains(entity);
    }

    /**
     * Whether {@code entity}'s own {@code move(SELF, ...)} is skipped this call: it is held (and
     * simulated here) or frozen, and the hold isn't the one moving it. Called for every entity move
     * in the game, so the common case is one volatile read.
     */
    public static boolean blocksSelfMove(Entity entity) {
        if (BLOCKING.get() <= 0 || ownMove) return false;
        return SERVER_MOVED.contains(entity) || FROZEN.contains(entity);
    }

    /** Moves the entity through its own collision code, as its own movement would. Server thread. */
    static void move(Entity entity, Vec3 delta) {
        boolean outer = ownMove;
        ownMove = true;
        try {
            entity.move(MoverType.SELF, delta);
        } finally {
            ownMove = outer;
        }
    }

    /** Forgets everything (server stop). The client's own claim is dropped by its hold ending. */
    static void clearServer() {
        SERVER_MOVED.clear();
        FROZEN.clear();
        BLOCKING.set(0);
    }
}
