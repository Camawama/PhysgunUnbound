package net.camacraft.physgununbound.gametest;

import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.camafy.minigame.MatchPolicy;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Freezing;
import net.camacraft.physgununbound.hold.HeldEntities;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.hold.PhysgunInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * The rules around a hold: what may not be picked up, what ends a hold (and that every claim goes
 * with it), freezing and unfreezing, taking an entity from another holder, and the crash-recovery
 * mark.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class RuleTests {

    /** Never oneself, a spectator, a hanging frame, a data-only entity, what another mod moves, or what a minigame protects. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void refusesWhatItMayNotHold(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        refuse(helper, holder, holder, "the holder itself");

        FakePlayer spectator = TestUtil.holder(helper, new Vec3(7.5, 2.0, 12.5), 0.0F, 0.0F);
        spectator.setGameMode(GameType.SPECTATOR);
        refuse(helper, holder, spectator, "a spectator");

        helper.setBlock(new BlockPos(9, 3, 4), Blocks.STONE);
        ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(8, 3, 4)), Direction.WEST);
        helper.getLevel().addFreshEntity(frame);
        refuse(helper, holder, frame, "an item frame");

        Interaction box = helper.spawn(EntityType.INTERACTION, new Vec3(7.5, 2.0, 6.5));
        refuse(helper, holder, box, "an interaction box (#physgununbound:ungrabbable)");

        Pig scripted = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 16.5));
        PhysicsOwnership.claim(scripted, ResourceLocation.fromNamespaceAndPath("othermod", "kart"), PhysicsAspect.ALL, 100);
        refuse(helper, holder, scripted, "a pig another mod scripts the whole physics of");

        // a claim on its motion alone doesn't stop the physgun (the hold claims everything, and
        // mods that arbitrate MOTION stand down for it): a kart in free roam, a player on a rope
        Pig driven = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 18.5));
        PhysicsOwnership.claim(driven, ResourceLocation.fromNamespaceAndPath("othermod", "kart"), PhysicsAspect.MOTION.bit, 100);
        Hold overKart = Holds.grab(holder, driven, driven.getBoundingBox().getCenter(), false, TestUtil.SPRING, TestUtil.DAMPING);
        if (overKart == null) helper.fail("a pig another mod only moves couldn't be picked up");
        else Holds.end(overKart, Holds.End.RELEASED);

        Pig protectedPig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 20.5));
        ResourceLocation host = PhysgunUnbound.id("test_host");
        MatchPolicies.register(host, new MatchPolicy() {
            @Override
            public boolean canHarm(Entity attacker, Entity victim) {
                return victim != protectedPig;
            }
        });
        try {
            refuse(helper, holder, protectedPig, "a pig a minigame protects");
        } finally {
            MatchPolicies.unregister(host);
        }
        Hold allowed = Holds.grab(holder, protectedPig, protectedPig.getBoundingBox().getCenter(), false,
                TestUtil.SPRING, TestUtil.DAMPING);
        if (allowed == null) helper.fail("the pig couldn't be picked up once the minigame let it go");
        else Holds.end(allowed, Holds.End.RELEASED);
        helper.succeed();
    }

    private static void refuse(GameTestHelper helper, FakePlayer holder, Entity entity, String what) {
        Hold hold = Holds.grab(holder, entity, entity.getBoundingBox().getCenter(), false, TestUtil.SPRING, TestUtil.DAMPING);
        if (hold != null) {
            Holds.end(hold, Holds.End.RELEASED);
            helper.fail("picked up " + what);
        }
    }

    /** A held entity that is removed ends its hold, and nothing claims it any more. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void removalEndsTheHoldAndItsClaims(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        TestUtil.grab(helper, holder, pig);
        helper.runAfterDelay(3, () -> {
            if (!HeldEntities.isHeld(pig)) helper.fail("the pig isn't held");
            pig.discard();
        });
        helper.runAfterDelay(5, () -> {
            if (Holds.holdOf(holder) != null || Holds.holdOn(pig) != null) helper.fail("the hold outlived its pig");
            if (HeldEntities.isHeld(pig) || PhysicsOwnership.claims(pig) != 0) helper.fail("the removed pig is still claimed");
            helper.succeed();
        });
    }

    /** A holder that dies lets go, and the pig falls with nothing claiming it. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void aDeadHolderLetsGo(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, -20.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        TestUtil.grab(helper, holder, pig);
        helper.runAfterDelay(10, () -> holder.setHealth(0.0F));
        helper.runAfterDelay(12, () -> {
            if (Holds.holdOn(pig) != null || PhysicsOwnership.claimed(pig, PhysicsAspect.MOTION)) {
                helper.fail("a dead holder still holds the pig");
            }
        });
        helper.runAfterDelay(35, () -> {
            double feet = helper.relativeVec(pig.position()).y;
            if (feet > TestUtil.FLOOR + 0.05) helper.fail("the released pig didn't fall: feet at " + fmt(feet));
            helper.succeed();
        });
    }

    /**
     * Right click while holding freezes the entity in the air (no AI, no gravity, kept in its data);
     * R while looking at it unfreezes it, and it falls.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void freezeHoldsItAndUnfreezeLetsItFall(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, -20.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        TestUtil.grab(helper, holder, pig);
        Vec3[] frozenAt = new Vec3[1];
        helper.runAfterDelay(20, () -> {
            Holds.applyInput(holder, PhysgunInput.freeze());
            if (Holds.holdOn(pig) != null) helper.fail("freezing didn't let go");
            if (!Freezing.isFrozen(pig) || !pig.isNoAi() || !pig.isNoGravity()) helper.fail("the pig isn't frozen");
            frozenAt[0] = pig.position();
        });
        helper.runAfterDelay(45, () -> {
            double drift = pig.position().distanceTo(frozenAt[0]);
            if (drift > 1.0E-3) helper.fail("the frozen pig moved " + fmt(drift));
            Holds.applyInput(holder, PhysgunInput.unfreezeLookedAt());
            if (Freezing.isFrozen(pig) || pig.isNoAi() || pig.isNoGravity()) helper.fail("R didn't unfreeze the pig it points at");
        });
        helper.runAfterDelay(75, () -> {
            double feet = helper.relativeVec(pig.position()).y;
            if (feet > TestUtil.FLOOR + 0.05) helper.fail("the unfrozen pig didn't fall: feet at " + fmt(feet));
            helper.succeed();
        });
    }

    /** Grabbing what another player holds takes it over; the first hold ends. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void aSecondHolderTakesItOver(GameTestHelper helper) {
        FakePlayer first = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 0.0F);
        FakePlayer second = TestUtil.holder(helper, new Vec3(11.5, 2.0, 12.5), 90.0F, 0.0F);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        TestUtil.grab(helper, first, pig);
        helper.runAfterDelay(5, () -> {
            Hold taken = TestUtil.grab(helper, second, pig);
            if (Holds.holdOf(first) != null) helper.fail("the first holder still holds the pig");
            if (Holds.holdOn(pig) != taken) helper.fail("the pig's hold isn't the second holder's");
        });
        helper.runAfterDelay(30, () -> {
            Hold taken = Holds.holdOf(second);
            if (taken == null) {
                helper.fail("the second hold ended");
                return;
            }
            if (TestUtil.error(taken) > 0.1) helper.fail("the taken pig is " + fmt(TestUtil.error(taken)) + " from its new target");
            Holds.end(taken, Holds.End.RELEASED);
            helper.succeed();
        });
    }

    /** An item saved mid-hold (a crash) gets its gravity back when it is loaded again; frozen ones stay frozen. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void aStaleHoldMarkIsUndoneOnLoad(GameTestHelper helper) {
        ItemEntity item = helper.spawnItem(Items.STICK, 5.5F, 3.0F, 5.5F);
        item.setNoGravity(true);
        item.addTag(Freezing.HELD);
        Freezing.onJoin(item);
        if (item.isNoGravity() || item.getTags().contains(Freezing.HELD)) {
            helper.fail("the stale hold mark wasn't undone");
        }

        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(9.5, 3.0, 9.5));
        Freezing.freeze(pig, false);
        Freezing.onLeave(pig); // as unloading does; loading re-fills the frozen set from the mark
        Freezing.onJoin(pig);
        if (!HeldEntities.isMarkedFrozen(pig)) helper.fail("a frozen pig wasn't registered again on load");
        Freezing.unfreeze(pig);
        helper.succeed();
    }
}
