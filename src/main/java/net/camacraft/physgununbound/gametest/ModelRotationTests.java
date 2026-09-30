package net.camacraft.physgununbound.gametest;

import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Freezing;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.HoldMotion;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.hold.ModelPose;
import net.camacraft.physgununbound.hold.PhysgunInput;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * The whole-model turn: the rotate drag tips a held entity every way (its renderer shows the
 * heading, the rest is the model rotation the clients draw), the grabbed point turns with it, a
 * frozen entity keeps the pose and a hold taken out of the freeze or from another holder carries
 * on from it, and an arrow's own heading and pitch follow the hold.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModelRotationTests {

    /**
     * A pig tipped a quarter turn about the world's X axis: its orientation is that turn, its
     * heading is unchanged, its head isn't pitched (the whole model is), and the grabbed point
     * has moved round with it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void rotateDragTipsTheWholeModel(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        pig.setYRot(30.0F);
        pig.setYBodyRot(30.0F);
        pig.setXRot(20.0F); // looking down a little: the hold starts from the body, not the head
        // grab its snout end, off its centre, so the offset has something to turn
        Vec3 grabPoint = pig.getBoundingBox().getCenter().add(0.0, 0.0, 0.4);
        Vec3 grabOffset = grabPoint.subtract(pig.position());
        Hold hold = Holds.grab(holder, pig, grabPoint, false, TestUtil.SPRING, TestUtil.DAMPING);
        if (hold == null) throw new AssertionError("could not grab the pig");
        Quaterniond atGrab = new Quaterniond(hold.orientation());
        if (angleBetween(atGrab, HoldMotion.yawTurn(30.0)) > 1.0E-6) {
            helper.fail("the hold starts from " + atGrab + ", not the pig's heading");
        }
        if (angleBetween(ModelPose.modelRotation(hold.orientation(), pig, 1.0F), new Quaterniond()) > 1.0E-6) {
            helper.fail("a fresh hold already turns the model");
        }

        Quaterniond tip = new Quaterniond().rotateX(Math.toRadians(90.0));
        helper.runAfterDelay(5, () -> Holds.applyInput(holder, PhysgunInput.rotate(tip)));
        helper.runAfterDelay(30, () -> {
            Quaterniond expected = tip.mul(atGrab, new Quaterniond());
            if (angleBetween(hold.orientation(), expected) > 1.0E-6) {
                helper.fail("after the drag the orientation is " + hold.orientation() + ", not the tip times the grab's");
            }
            if (Math.abs(Mth.wrapDegrees(pig.getYRot() - 30.0F)) > 0.5F) {
                helper.fail("the tip changed the pig's heading to " + fmt(pig.getYRot()) + " (body " + fmt(pig.yBodyRot) + ")");
            }
            if (pig.getXRot() != 0.0F) helper.fail("the pig's head is pitched " + fmt(pig.getXRot()) + " while its whole body is tipped");
            Quaterniond model = ModelPose.modelRotation(hold.orientation(), pig, 1.0F);
            if (angleBetween(model, tip) > 1.0E-4) helper.fail("the model rotation is " + model + ", not the tip");
            Vec3 offset = hold.offset();
            Vector3d turned = tip.transform(new Vector3d(grabOffset.x, grabOffset.y, grabOffset.z));
            if (offset.distanceTo(new Vec3(turned.x, turned.y, turned.z)) > 1.0E-6) {
                helper.fail("the grabbed point is at " + fmt(offset) + " from the pig, not turned with it");
            }
            if (TestUtil.error(hold) > 0.05) helper.fail("the tipped pig is " + fmt(TestUtil.error(hold)) + " from the target");
            Holds.end(hold, Holds.End.RELEASED);
            helper.succeed();
        });
    }

    /**
     * Frozen, a tipped pig keeps the pose (saved with it), and grabbing it again carries on from
     * it; a second holder taking it over does too. Unfreezing drops the pose.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aFrozenOrTakenOverEntityKeepsItsTurn(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        FakePlayer other = TestUtil.holder(helper, new Vec3(11.5, 2.0, 12.5), 90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        Hold hold = TestUtil.grab(helper, holder, pig);
        Quaterniond tip = new Quaterniond().rotateZ(Math.toRadians(60.0));
        Holds.applyInput(holder, PhysgunInput.rotate(tip));
        Quaterniond tipped = new Quaterniond(hold.orientation());

        helper.runAfterDelay(10, () -> {
            Holds.freeze(hold);
            if (!Freezing.isFrozen(pig)) helper.fail("the pig wasn't frozen");
            Quaterniondc kept = Freezing.keptPose(pig);
            if (kept == null || angleBetween(kept, tipped) > 1.0E-6) helper.fail("the frozen pig doesn't keep its turn: " + kept);

            Hold again = TestUtil.grab(helper, holder, pig);
            if (Freezing.isFrozen(pig)) helper.fail("grabbing didn't unfreeze the pig");
            if (Freezing.keptPose(pig) != null) helper.fail("the pose is still on the pig after the grab");
            if (angleBetween(again.orientation(), tipped) > 1.0E-6) helper.fail("the new hold started from " + again.orientation() + ", not the kept pose");

            Hold taken = TestUtil.grab(helper, other, pig);
            if (Holds.holdOf(holder) != null) helper.fail("the first holder still holds the pig");
            if (angleBetween(taken.orientation(), tipped) > 1.0E-6) helper.fail("the second holder started from " + taken.orientation() + ", not the pose it had");

            Holds.freeze(taken);
            Freezing.unfreeze(pig);
            if (Freezing.keptPose(pig) != null) helper.fail("unfreezing left the pose on the pig");
            helper.succeed();
        });
    }

    /**
     * An arrow's own renderer shows a heading and a pitch, so both follow the hold: turned a
     * quarter to the right, a level arrow's yaw turns with it; tipped up, its pitch does.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void anArrowFacesTheWayItIsTurned(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 3.5, 12.5));
        Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z);
        arrow.setNoGravity(true);
        arrow.setYRot(0.0F); // pointing +Z, level (the arrow's own yaw sense: atan2(dx, dz))
        arrow.setXRot(0.0F);
        arrow.yRotO = 0.0F;
        arrow.xRotO = 0.0F;
        helper.getLevel().addFreshEntity(arrow);
        Hold hold = TestUtil.grab(helper, holder, arrow);
        if (ModelPose.kindOf(arrow) != ModelPose.Kind.ARROW) helper.fail("an arrow isn't drawn by heading and pitch");

        helper.runAfterDelay(5, () -> Holds.applyInput(holder, PhysgunInput.rotate(HoldMotion.yawTurn(90.0))));
        helper.runAfterDelay(20, () -> {
            // Minecraft's yaw turn of +90 (to the right) turns +Z into -X; the arrow's yaw for -X is atan2(-1, 0) = -90
            if (Math.abs(Mth.wrapDegrees(arrow.getYRot() + 90.0F)) > 0.5F) helper.fail("turned a quarter right the arrow's yaw is " + fmt(arrow.getYRot()));
            if (Math.abs(arrow.getXRot()) > 0.5F) helper.fail("a level turn pitched the arrow " + fmt(arrow.getXRot()));
            // tip it up 45 degrees about the axis it now lies across (world Z)
            Holds.applyInput(holder, PhysgunInput.rotate(new Quaterniond().rotateZ(Math.toRadians(-45.0))));
        });
        helper.runAfterDelay(30, () -> {
            if (Math.abs(arrow.getXRot() - 45.0F) > 0.5F) helper.fail("tipped up 45 degrees the arrow's pitch is " + fmt(arrow.getXRot()));
            Holds.end(hold, Holds.End.RELEASED);
            helper.succeed();
        });
    }

    /** The angle between two rotations, radians. */
    private static double angleBetween(Quaterniondc a, Quaterniondc b) {
        Quaterniond diff = a.mul(b.conjugate(new Quaterniond()), new Quaterniond()).normalize();
        return Math.abs(diff.angle());
    }
}
