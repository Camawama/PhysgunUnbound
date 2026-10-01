package net.camacraft.physgununbound.gametest;

import net.camacraft.camafy.compat.gravity.FrameKind;
import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravityFrame;
import net.camacraft.camafy.compat.gravity.GravityProvider;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * The hold works in world axes whatever the gravity mod does to an entity's frame. The game test
 * server has no Gravity Unbound, so a stand-in provider gives one tagged pig a sideways movement
 * convention (its "up" is world +X), the way Gravity Unbound's {@code Entity.move} hook and
 * Camafy's {@code Gravity.movementToWorld} turn a frame's vectors into the world.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class GravityFrameTests {
    static final String SIDEWAYS = "pu_test_sideways";

    /** Plain gravity for everything, except a sideways movement convention for entities tagged {@link #SIDEWAYS}. */
    static final class StandIn implements GravityProvider {
        static boolean installed;

        static boolean install() {
            if (!installed) installed = Gravity.registerProvider(new StandIn());
            return installed;
        }

        static boolean sideways(Entity entity) {
            return entity.getTags().contains(SIDEWAYS);
        }

        @Override public String name() { return "Physgun Unbound test frame"; }
        @Override public boolean isDefault(Entity entity, FrameKind kind) { return true; }
        @Override public GravityFrame frame(Entity entity, FrameKind kind, float partialTick) { return GravityFrame.DEFAULT; }
        @Override public Vec3 pullDirection(Entity entity) { return new Vec3(0.0, -1.0, 0.0); }
        @Override public double strength(Entity entity) { return 1.0; }
        @Override public Direction cardinalDown(Entity entity) { return Direction.DOWN; }
        @Override public boolean canChange(Entity entity) { return false; }
        @Override public void sustainHeldSurface(Entity entity) {}
        @Override public void setBaseDirection(Entity entity, Direction down) {}
        @Override public void reset(Entity entity) {}

        /** Movement +Y is world +X: a quarter turn about Z. */
        @Override
        public Vec3 movementToWorld(Entity entity, Vec3 movement) {
            return sideways(entity) ? new Vec3(movement.y, -movement.x, movement.z) : movement;
        }

        @Override
        public Vec3 worldToMovement(Entity entity, Vec3 world) {
            return sideways(entity) ? new Vec3(-world.y, world.x, world.z) : world;
        }
    }

    /**
     * A pig in a sideways frame, let go while the hold pulls it along +Z: its velocity in world axes
     * is the throw's, and what sits in {@code deltaMovement} is that velocity in the frame's own
     * convention (what the gravity mod would turn back into the world).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aThrowInASidewaysFrameLeavesInWorldAxes(GameTestHelper helper) {
        if (!StandIn.install()) {
            PhysgunUnbound.LOGGER.info("[physgununbound] another gravity provider is installed: the frame test can't stand in");
            helper.succeed();
            return;
        }
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(8.5, 2.0, 4.5));
        pig.addTag(SIDEWAYS);
        Vec3 probe = Gravity.worldToMovement(pig, new Vec3(1.0, 0.0, 0.0));
        if (probe.distanceTo(new Vec3(0.0, 1.0, 0.0)) > 1.0E-9) {
            helper.fail("the stand-in frame isn't in force: world +X became " + fmt(probe));
            return;
        }
        // the holder stands behind the pig looking along +Z, the target 6 blocks ahead: the hold pulls it along +Z
        FakePlayer holder = TestUtil.holder(helper, new Vec3(8.5, 2.0, 1.5), 0.0F, 0.0F);
        Hold hold = TestUtil.grab(helper, holder, pig);
        if (hold == null) {
            helper.fail("the pig was refused");
            return;
        }
        helper.runAfterDelay(6, () -> {
            Vec3 pull = hold.velocity();
            if (pull.length() < 1.0) {
                helper.fail("the hold isn't pulling the pig (velocity " + fmt(pull) + " blocks/s)");
                return;
            }
            Vec3 expectedWorld = pull.scale(0.05);
            Holds.end(hold, Holds.End.RELEASED);
            Vec3 world = Gravity.getWorldVelocity(pig);
            Vec3 stored = pig.getDeltaMovement();
            if (world.distanceTo(expectedWorld) > 1.0E-6) {
                helper.fail("let go, the pig's world velocity is " + fmt(world) + ", not the throw's " + fmt(expectedWorld));
            }
            Vec3 convention = new Vec3(-expectedWorld.y, expectedWorld.x, expectedWorld.z);
            if (stored.distanceTo(convention) > 1.0E-6) {
                helper.fail("the pig's deltaMovement " + fmt(stored) + " isn't the throw in its frame's convention " + fmt(convention));
            }
            pig.removeTag(SIDEWAYS);
            helper.succeed();
        });
    }
}
