package net.camacraft.physgununbound.gametest;

import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.PuConfig;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.HolderScale;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.hold.PhysgunInput;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * The physgun in a shrunk world, as in VS Genesis's Great Unknown where players, mobs and ships are 1/16 size: a
 * shrunk holder holds in its own blocks (how close it pulls, how far a wheel notch moves, how fast it throws), so the
 * same hand movement does at 1/16 what it does at full size, at 1/16 of the size. The holder and the pig are shrunk
 * with Forge's size event ({@link TestScale}).
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScaleTests {
    private static final float TINY = 1.0F / 16.0F;

    /**
     * A 1/16 holder grabs a 1/16 pig five of its blocks away (0.3 blocks): the pig is held there, not pushed out to the
     * full-size minDistance (1 block, sixteen of the holder's blocks); two wheel notches move it two of the holder's
     * blocks (1/8 of a block); and pulling it all the way in stops at minDistance in the holder's blocks.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aShrunkHolderHoldsInItsOwnBlocks(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(4.5, 2.0, 12.5), -90.0F, 0.0F);
        TestScale.scale(holder, TINY);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.8, 2.0, 12.5));
        TestScale.scale(pig, TINY);
        double scale = HolderScale.of(holder);
        if (Math.abs(scale - TINY) > 1.0E-3) {
            helper.fail("the holder isn't 1/16 size: " + fmt(scale));
            return;
        }
        Hold hold = TestUtil.grab(helper, holder, pig);
        double grabbed = hold.distance();
        if (grabbed > 0.5) helper.fail("the shrunk pig was pushed out to " + fmt(grabbed) + " blocks (full-size minDistance)");

        helper.runAfterDelay(20, () -> {
            double error = TestUtil.error(hold);
            double fromEye = pig.position().add(hold.offset()).distanceTo(holder.getEyePosition());
            report("1/16 holder, 1/16 pig", "grabbed at " + fmt(grabbed) + " blocks, held at " + fmt(fromEye) + " from the eye, "
                    + fmt(error) + " from the target");
            if (error > 0.02) helper.fail("after 1 s the shrunk pig is " + fmt(error) + " from the target");
            if (Math.abs(fromEye - grabbed) > 0.03) helper.fail("the shrunk pig is held " + fmt(fromEye) + " from the eye, grabbed at " + fmt(grabbed));
            double before = hold.distance();
            Holds.applyInput(holder, PhysgunInput.scroll(2.0));
            double step = hold.distance() - before;
            report("1/16 holder, two wheel notches", "the hold distance moved " + fmt(step) + " blocks");
            if (Math.abs(step - 2.0 * TINY) > 1.0E-6) helper.fail("two notches moved a 1/16 holder's hold " + fmt(step) + " blocks, not 0.125");
            Holds.applyInput(holder, PhysgunInput.scroll(-100.0));
            double min = PuConfig.SERVER.minDistance() * TINY;
            if (Math.abs(hold.distance() - min) > 1.0E-6) helper.fail("pulled all the way in, the hold stops at " + fmt(hold.distance())
                    + ", not minDistance at 1/16 (" + fmt(min) + ")");
            Holds.end(hold, Holds.End.RELEASED);
            helper.succeed();
        });
    }

    /**
     * The same throw at both sizes: a full-size holder with a pig 4.8 blocks away and a 1/16 holder with a 1/16 pig 0.3
     * blocks away both turn 4 degrees a tick for six ticks and let go. The shrunk pig leaves at 1/16 of the full-size
     * pig's speed: the same movement in its own world (at full-size lengths it left at nearly a fifth).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aShrunkHolderThrowsAtItsOwnScale(GameTestHelper helper) {
        FakePlayer big = TestUtil.holder(helper, new Vec3(4.5, 2.0, 6.5), -90.0F, 0.0F);
        Pig bigPig = TestUtil.idle(helper, EntityType.PIG, new Vec3(9.3, 2.0, 6.5));
        FakePlayer tiny = TestUtil.holder(helper, new Vec3(4.5, 2.0, 18.5), -90.0F, 0.0F);
        TestScale.scale(tiny, TINY);
        Pig tinyPig = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.8, 2.0, 18.5));
        TestScale.scale(tinyPig, TINY);
        Hold bigHold = TestUtil.grab(helper, big, bigPig);
        Hold tinyHold = TestUtil.grab(helper, tiny, tinyPig);
        float[] yaw = {-90.0F};

        helper.startSequence()
                .thenIdle(20)
                .thenExecuteFor(6, () -> {
                    yaw[0] += 4.0F;
                    TestUtil.look(big, yaw[0], 0.0F);
                    TestUtil.look(tiny, yaw[0], 0.0F);
                })
                .thenExecute(() -> {
                    Holds.end(bigHold, Holds.End.RELEASED);
                    Holds.end(tinyHold, Holds.End.RELEASED);
                    double bigSpeed = bigPig.getDeltaMovement().length() * 20.0;
                    double tinySpeed = tinyPig.getDeltaMovement().length() * 20.0;
                    double ratio = tinySpeed / bigSpeed;
                    report("the same throw at full size and at 1/16", "full size " + fmt(bigSpeed) + " m/s, 1/16 " + fmt(tinySpeed)
                            + " m/s: " + fmt(ratio * 16.0) + "/16 of it");
                    helper.assertTrue(bigSpeed > 1.0, "the full-size pig wasn't thrown (" + fmt(bigSpeed) + " m/s)");
                    helper.assertTrue(ratio > 0.8 / 16.0 && ratio < 1.25 / 16.0, "the shrunk pig left at " + fmt(ratio * 16.0)
                            + "/16 of the full-size pig's speed, not 1/16");
                })
                .thenSucceed();
    }

    private static void report(String what, String result) {
        PhysgunUnbound.LOGGER.info("[PU gametest] {}: {}", what, result);
    }
}
