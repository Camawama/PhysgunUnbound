package net.camacraft.physgununbound.gametest;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.HeldEntities;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.HoldMotion;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.hold.PhysgunInput;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * The hold itself, driven directly (no VMod needed): the grabbed point follows the point in front
 * of the holder, turns with the holder, answers the wheel and the rotate drag, stops at walls, and
 * is thrown with its speed on release, with every claim cleared.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class HoldTests {

    /**
     * A pig grabbed at its middle is lifted to the holder's eye line and held there, then swings
     * round a quarter turn when the holder turns, and turns with them. Claimed the whole time.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void heldMobFollowsTheTarget(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(4.5, 2.0, 12.5), -90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(8.5, 2.0, 12.5));
        Hold hold = TestUtil.grab(helper, holder, pig);
        float pigYaw = pig.getYRot();

        helper.runAfterDelay(20, () -> {
            if (TestUtil.error(hold) > 0.05) helper.fail("after 1 s the pig is " + fmt(TestUtil.error(hold)) + " from the target");
            double feet = helper.relativeVec(pig.position()).y;
            if (feet < TestUtil.FLOOR + 0.8) helper.fail("the pig wasn't lifted to the eye line: feet at " + fmt(feet));
            if (!PhysicsOwnership.claimed(pig, PhysicsAspect.MOTION) || !PhysicsOwnership.claimed(pig, PhysicsAspect.IMPACT)) {
                helper.fail("a held pig isn't claimed");
            }
            TestUtil.look(holder, 0.0F, 0.0F); // a quarter turn right: now facing +Z
        });
        helper.runAfterDelay(50, () -> {
            double error = TestUtil.error(hold);
            if (error > 0.1) helper.fail("after the turn the pig is " + fmt(error) + " from the target");
            Vec3 rel = helper.relativeVec(pig.position());
            if (Math.abs(rel.x - 4.5) > 0.6 || rel.z < 15.5) helper.fail("the pig didn't swing round in front of the holder: " + fmt(rel));
            float turned = Mth.wrapDegrees(pig.getYRot() - pigYaw);
            if (Math.abs(turned - 90.0F) > 1.0F) helper.fail("the pig turned " + fmt(turned) + " degrees with a 90 degree turn");
            Holds.end(hold, Holds.End.RELEASED);
            helper.succeed();
        });
    }

    /**
     * Swinging the physgun and letting go throws: the pig leaves with the speed it had in the hold,
     * keeps going that way, and nothing claims it any more.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void releaseThrowsWithTheHoldSpeed(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(4.5, 2.0, 4.5), -90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(8.5, 2.0, 4.5));
        Hold hold = TestUtil.grab(helper, holder, pig);

        // a quick swing: 12 degrees a tick, let go mid-swing
        for (int i = 0; i < 6; i++) {
            float yaw = -90.0F + 12.0F * (i + 1);
            helper.runAfterDelay(15 + i, () -> TestUtil.look(holder, yaw, 0.0F));
        }
        Vec3[] thrown = new Vec3[2]; // speed at release (blocks/s), position at release
        helper.runAfterDelay(21, () -> {
            Vec3 speed = hold.velocity();
            if (speed.horizontalDistance() < 3.0) helper.fail("the swing only got the pig to " + fmt(speed) + " blocks/s");
            Holds.end(hold, Holds.End.RELEASED);
            Vec3 perTick = pig.getDeltaMovement();
            if (perTick.distanceTo(speed.scale(0.05)) > 1.0E-6) {
                helper.fail("released with " + fmt(perTick.scale(20.0)) + " blocks/s instead of the hold's " + fmt(speed));
            }
            if (HeldEntities.isHeld(pig) || PhysicsOwnership.claimed(pig, PhysicsAspect.MOTION) || Holds.holdOn(pig) != null) {
                helper.fail("the pig is still held or claimed after the release");
            }
            thrown[0] = speed;
            thrown[1] = pig.position();
        });
        helper.runAfterDelay(23, () -> {
            Vec3 flat = new Vec3(thrown[0].x, 0.0, thrown[0].z);
            double along = pig.position().subtract(thrown[1]).dot(flat.normalize());
            // two ticks of flight at the release speed, less the air's drag: ~0.095 of a second's worth
            if (along < flat.length() * 0.07) {
                helper.fail("two ticks after the release the pig only went " + fmt(along) + " blocks along its "
                        + fmt(flat.length()) + " blocks/s throw");
            }
            helper.succeed();
        });
    }

    /**
     * The wheel moves the target out and in (a block per notch, as VMod), and the rotate drag
     * turns the entity about the vertical as it would turn a ship.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void wheelAndRotateDragMoveAndTurn(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        Hold hold = TestUtil.grab(helper, holder, pig);
        double before = hold.distance();

        helper.runAfterDelay(10, () -> {
            Holds.applyInput(holder, PhysgunInput.scroll(3.0));
            Holds.applyInput(holder, PhysgunInput.rotate(HoldMotion.yawTurn(-45.0)));
        });
        helper.runAfterDelay(40, () -> {
            if (Math.abs(hold.distance() - (before + 3.0)) > 1.0E-9) {
                helper.fail("three notches made the distance " + fmt(hold.distance()) + " from " + fmt(before));
            }
            if (TestUtil.error(hold) > 0.05) helper.fail("the pig is " + fmt(TestUtil.error(hold)) + " from the pushed-out target");
            float turned = Mth.wrapDegrees(pig.getYRot() - hold.yaw());
            if (Math.abs(turned) > 0.5F) helper.fail("the pig faces " + fmt(pig.getYRot()) + ", not the hold's " + fmt(hold.yaw()));
            Holds.applyInput(holder, PhysgunInput.scroll(-100.0)); // clamped to the minimum, not through the holder
        });
        helper.runAfterDelay(41, () -> {
            if (hold.distance() < 0.99) helper.fail("the wheel pulled the pig in to " + fmt(hold.distance()));
            Holds.end(hold, Holds.End.RELEASED);
            helper.succeed();
        });
    }

    /** A held mob pushed into a wall stops at the wall (it moves through its own collision code). */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void wallsStopAHeldMob(GameTestHelper helper) {
        for (int y = 2; y <= 6; y++) {
            for (int z = 9; z <= 16; z++) helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
        }
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        Hold hold = TestUtil.grab(helper, holder, pig);
        helper.runAfterDelay(5, () -> Holds.applyInput(holder, PhysgunInput.scroll(10.0)));
        helper.runAfterDelay(40, () -> {
            double wall = helper.absoluteVec(new Vec3(12.0, 0.0, 0.0)).x;
            double front = pig.getBoundingBox().maxX;
            if (front > wall + 1.0E-3) helper.fail("the pig went " + fmt(front - wall) + " blocks into the wall");
            if (wall - front > 0.05) helper.fail("the pig stopped " + fmt(wall - front) + " short of the wall");
            if (Holds.holdOn(pig) != hold) helper.fail("the wall ended the hold");
            if (Math.abs(hold.velocity().x) > 1.0E-6) helper.fail("the pig still pushes into the wall at " + fmt(hold.velocity().x));
            Holds.end(hold, Holds.End.RELEASED);
            helper.succeed();
        });
    }
}
