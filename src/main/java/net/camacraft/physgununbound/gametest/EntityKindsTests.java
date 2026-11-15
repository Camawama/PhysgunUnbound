package net.camacraft.physgununbound.gametest;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Freezing;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * Every kind of thing the physgun should lift, held the same way: an item, a minecart standing on
 * a rail (which would otherwise snap itself back onto it), a boat, a falling sand block (which
 * would otherwise land or expire), an armor stand, and a player (moved by their own client, or with
 * vanilla motion packets when it has no mod, as this fake one hasn't).
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class EntityKindsTests {

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void itemsCartsBoatsBlocksAndStandsAreHeld(GameTestHelper helper) {
        ItemEntity item = helper.spawnItem(Items.DIAMOND, 7.5F, 2.2F, 3.5F);
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.RAIL);
        AbstractMinecart cart = helper.spawn(EntityType.MINECART, new Vec3(7.5, 2.0, 7.5));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(7.5, 2.0, 11.5));
        helper.setBlock(new BlockPos(7, 4, 15), Blocks.SAND);
        FallingBlockEntity sand = FallingBlockEntity.fall(helper.getLevel(), helper.absolutePos(new BlockPos(7, 4, 15)),
                Blocks.SAND.defaultBlockState());
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(7.5, 2.0, 19.5));

        List<Entity> entities = List.of(item, cart, boat, sand, stand);
        List<Hold> holds = entities.stream().map(entity -> {
            // each looks a little up at its own entity, so the target is well off the floor
            FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, helper.relativeVec(entity.position()).z), -90.0F, -20.0F);
            return TestUtil.grab(helper, holder, entity);
        }).toList();

        helper.runAfterDelay(40, () -> {
            for (Hold hold : holds) {
                Entity entity = hold.entity();
                if (!entity.isAlive()) helper.fail(entity.getType().toShortString() + " didn't survive being held");
                if (TestUtil.error(hold) > 0.1) {
                    helper.fail(entity.getType().toShortString() + " is " + fmt(TestUtil.error(hold)) + " from its target");
                }
                if (!PhysicsOwnership.claimed(entity, PhysicsAspect.MOTION)) helper.fail(entity.getType().toShortString() + " isn't claimed");
            }
            if (cart.canUseRail()) helper.fail("a held cart may still use rails");
            if (!item.isNoGravity() || !sand.isNoGravity()) helper.fail("held items and blocks still have gravity (their clients would draw them falling)");
            // held back to 1 each tick before its own tick counts it up
            if (sand.time > 2) helper.fail("the held sand's air time is " + sand.time + " (it would drop as an item at 600)");
            if (!item.getTags().contains(Freezing.HELD)) helper.fail("the held item isn't marked for crash recovery");
            for (Hold hold : holds) Holds.end(hold, Holds.End.RELEASED);
            if (!cart.canUseRail()) helper.fail("the released cart can't use rails");
            if (item.isNoGravity() || sand.isNoGravity() || boat.isNoGravity()) helper.fail("released entities didn't get their gravity back");
            if (item.getTags().contains(Freezing.HELD)) helper.fail("the released item is still marked as held");
            for (Entity entity : entities) {
                if (PhysicsOwnership.claimed(entity, PhysicsAspect.MOTION)) helper.fail(entity.getType().toShortString() + " is still claimed");
            }
            helper.succeed();
        });
    }

    /**
     * A held player is claimed and, having no client with the mod (this one is fake), is steered
     * with velocity toward the target from the server. Letting go clears the claim. The holder is
     * an operator: by default only staff (the permission physgununbound.grab.players) pick players up.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void playersAreHeldThroughTheirOwnMotion(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, -30.0F);
        FakePlayer held = TestUtil.holder(helper, new Vec3(7.5, 2.0, 12.5), 90.0F, 0.0F);
        var players = helper.getLevel().getServer().getPlayerList();
        // (not PlayerList.op: the gametest server hands out operator level 0)
        players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(holder.getGameProfile(), 2, false));
        Hold hold;
        try {
            hold = TestUtil.grab(helper, holder, held);
        } catch (RuntimeException | Error e) {
            players.deop(holder.getGameProfile());
            throw e;
        }
        if (hold.mode() != Hold.Mode.CLIENT) helper.fail("a player is moved by the server, not their own client");
        helper.runAfterDelay(2, () -> {
            Vec3 toTarget = hold.entityTarget().subtract(held.position());
            Vec3 speed = held.getDeltaMovement();
            if (speed.dot(toTarget) <= 0.0) helper.fail("the held player is sent " + fmt(speed) + ", not toward " + fmt(toTarget));
            if (!PhysicsOwnership.claimed(held, PhysicsAspect.MOTION)) helper.fail("the held player isn't claimed");
            if (held.fallDistance != 0.0F) helper.fail("a held player builds up fall distance");
            Holds.end(hold, Holds.End.RELEASED);
            players.deop(holder.getGameProfile());
            if (PhysicsOwnership.claimed(held, PhysicsAspect.MOTION)) helper.fail("the released player is still claimed");
            helper.succeed();
        });
    }
}
