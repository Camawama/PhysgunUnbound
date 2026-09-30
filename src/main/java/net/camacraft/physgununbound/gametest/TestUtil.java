package net.camacraft.physgununbound.gametest;

import com.mojang.authlib.GameProfile;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.Locale;
import java.util.UUID;

/** Shared setup for the gametests. Positions passed in are relative to the test's structure. */
final class TestUtil {
    /**
     * A 24x16x24 plot with a stone floor. A test's coordinates start at its structure block, which
     * sits one block below the structure, so the floor is the layer at y = 1 and its top is at
     * {@link #FLOOR}.
     */
    static final String PLATFORM = "platform";
    /** Where something standing on the platform's floor has its feet (relative y). */
    static final double FLOOR = 2.0;
    /** VMod's default spring and damper (its server settings PCONST and DCONST). */
    static final double SPRING = 160.0;
    static final double DAMPING = 20.0;

    private TestUtil() {}

    /**
     * A fake player standing at {@code rel}, facing {@code yaw} (0 = +Z, -90 = +X) and
     * {@code pitch}, unique to the calling test. It is not in the level's player list, so nothing
     * ticks or pushes it: it only holds.
     */
    static FakePlayer holder(GameTestHelper helper, Vec3 rel, float yaw, float pitch) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, "pu_" + id.toString().substring(0, 8)));
        Vec3 abs = helper.absoluteVec(rel);
        player.setPos(abs.x, abs.y, abs.z);
        look(player, yaw, pitch);
        return player;
    }

    static void look(FakePlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    /** A mob with its goals removed, so nothing but the hold (and its own physics) moves it. */
    static <T extends Mob> T idle(GameTestHelper helper, EntityType<T> type, Vec3 rel) {
        T mob = helper.spawn(type, rel);
        mob.removeFreeWill();
        return mob;
    }

    /** Grabs {@code entity} at the centre of its box, as the physgun would, with VMod's default spring. */
    static Hold grab(GameTestHelper helper, FakePlayer holder, Entity entity) {
        Hold hold = Holds.grab(holder, entity, entity.getBoundingBox().getCenter(), false, SPRING, DAMPING);
        if (hold == null) throw new AssertionError("could not grab " + entity);
        return hold;
    }

    /** How far the grabbed point is from where the holder is pulling it. */
    static double error(Hold hold) {
        return hold.entity().position().add(hold.offset()).distanceTo(hold.target());
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }
}
