package net.camacraft.physgununbound.gametest;

import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.mixin.AbstractArrowAccessor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * Projectiles in flight are picked up like anything else: held, they stop flying (no gravity,
 * no acceleration, nothing hit) and sit where the hold puts them; let go, they fly on with the
 * throw. A stuck arrow and a fishing bobber are refused.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProjectileTests {

    /** An arrow, a snowball and a blaze's fireball, all in flight, held still and then thrown on. */
    @GameTest(template = PLATFORM, timeoutTicks = 120)
    public static void projectilesInFlightAreHeldAndThrownOn(GameTestHelper helper) {
        Vec3 flying = new Vec3(0.0, 0.0, 1.2); // blocks a tick, along +Z
        Arrow arrow = new Arrow(helper.getLevel(), 0.0, 0.0, 0.0);
        place(helper, arrow, new Vec3(7.5, 4.0, 4.5), flying);
        Snowball snowball = new Snowball(helper.getLevel(), 0.0, 0.0, 0.0);
        place(helper, snowball, new Vec3(7.5, 4.0, 10.5), flying);
        SmallFireball fireball = new SmallFireball(helper.getLevel(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.08);
        place(helper, fireball, new Vec3(7.5, 4.0, 16.5), flying);

        List<Entity> entities = List.of(arrow, snowball, fireball);
        List<Hold> holds = entities.stream().map(entity -> {
            FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, helper.relativeVec(entity.position()).z), -90.0F, -20.0F);
            return TestUtil.grab(helper, holder, entity);
        }).toList();

        Vec3[] settled = new Vec3[entities.size()];
        helper.runAfterDelay(30, () -> {
            for (int i = 0; i < holds.size(); i++) {
                Hold hold = holds.get(i);
                Entity entity = hold.entity();
                String name = entity.getType().toShortString();
                if (!entity.isAlive()) helper.fail(name + " didn't survive being held");
                if (TestUtil.error(hold) > 0.1) helper.fail(name + " is " + fmt(TestUtil.error(hold)) + " from its target: it flies on while held");
                if (!entity.isNoGravity()) helper.fail(name + " still has gravity while held");
                settled[i] = entity.position();
            }
        });
        helper.runAfterDelay(40, () -> {
            for (int i = 0; i < holds.size(); i++) {
                Entity entity = holds.get(i).entity();
                double drift = entity.position().distanceTo(settled[i]);
                if (drift > 0.02) helper.fail(entity.getType().toShortString() + " drifted " + fmt(drift) + " in half a second while held still");
            }
            for (Hold hold : holds) Holds.end(hold, Holds.End.RELEASED);
            if (arrow.isNoGravity()) helper.fail("the released arrow has no gravity");
        });
        helper.runAfterDelay(50, () -> {
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                // let go from rest they fall (the arrow and the snowball) or fly on (the fireball
                // keeps its own acceleration); either way they move by themselves again
                if (entity.isAlive() && entity.position().distanceTo(settled[i]) < 0.05) {
                    helper.fail(entity.getType().toShortString() + " doesn't move by itself once let go");
                }
            }
            helper.succeed();
        });
    }

    /** An arrow stuck in a block, and a fishing bobber, are not picked up; a flying arrow is. */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void stuckArrowsAndBobbersAreRefused(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        Arrow stuck = new Arrow(helper.getLevel(), 0.0, 0.0, 0.0);
        place(helper, stuck, new Vec3(7.5, 2.3, 12.5), new Vec3(0.0, -1.0, 0.0)); // into the floor
        FishingHook bobber = EntityType.FISHING_BOBBER.create(helper.getLevel());
        if (bobber == null) throw new AssertionError("no bobber");
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 3.0, 8.5));
        bobber.setPos(at.x, at.y, at.z);

        helper.runAfterDelay(10, () -> {
            if (!((AbstractArrowAccessor) stuck).physgununbound$inGround()) helper.fail("the arrow didn't stick in the floor");
            Hold hold = Holds.grab(holder, stuck, stuck.getBoundingBox().getCenter(), false, TestUtil.SPRING, TestUtil.DAMPING);
            if (hold != null) {
                Holds.end(hold, Holds.End.RELEASED);
                helper.fail("picked up an arrow stuck in a block");
            }
            Hold hooked = Holds.grab(holder, bobber, bobber.getBoundingBox().getCenter(), false, TestUtil.SPRING, TestUtil.DAMPING);
            if (hooked != null) {
                Holds.end(hooked, Holds.End.RELEASED);
                helper.fail("picked up a fishing bobber (#physgununbound:ungrabbable)");
            }
            helper.succeed();
        });
    }

    private static void place(GameTestHelper helper, Entity entity, Vec3 rel, Vec3 velocity) {
        Vec3 at = helper.absoluteVec(rel);
        entity.setPos(at.x, at.y, at.z);
        entity.setDeltaMovement(velocity);
        helper.getLevel().addFreshEntity(entity);
    }
}
