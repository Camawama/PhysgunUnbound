package net.camacraft.physgununbound.gametest;

import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Freezing;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * A frozen projectile sits perfectly still. A trident is the hard case: its own {@code tick}
 * runs its Loyalty return (creeping toward its owner, a velocity toward them) before it calls the
 * arrow's tick that the hold cuts short, so a frozen Loyalty trident crept and stuttered.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class StillnessTests {

    /** A Loyalty III trident on its way back to its owner, frozen in flight: it stays exactly where it was frozen. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aFrozenLoyaltyTridentStaysExactlyPut(GameTestHelper helper) {
        // the owner stands well above and away, so a return would be a visible climb toward their eyes
        FakePlayer owner = TestUtil.holder(helper, new Vec3(3.5, 8.0, 8.5), -90.0F, 30.0F);
        ItemStack stack = new ItemStack(Items.TRIDENT);
        stack.enchant(Enchantments.LOYALTY, 3);
        ThrownTrident trident = new ThrownTrident(helper.getLevel(), owner, stack);
        Vec3 at = helper.absoluteVec(new Vec3(10.5, 4.0, 8.5));
        trident.setPos(at.x, at.y, at.z);
        trident.setDeltaMovement(0.0, 0.0, 0.8);
        trident.setNoPhysics(true); // what a trident that hit something is on its way back: Loyalty takes over
        helper.getLevel().addFreshEntity(trident);

        Hold hold = TestUtil.grab(helper, owner, trident);
        if (hold == null) {
            helper.fail("the trident in flight was refused");
            return;
        }
        Holds.freeze(hold);
        if (!Freezing.isFrozen(trident)) {
            helper.fail("the trident didn't freeze");
            return;
        }
        Vec3 frozenAt = trident.position();
        float yaw = trident.getYRot();
        float pitch = trident.getXRot();
        helper.runAfterDelay(40, () -> {
            if (!trident.isAlive()) helper.fail("the frozen trident is gone");
            if (!Freezing.isFrozen(trident)) helper.fail("the trident thawed on its own");
            double moved = trident.position().distanceTo(frozenAt);
            if (moved > 1.0E-6) helper.fail("the frozen trident moved " + fmt(moved) + " blocks (its Loyalty return still runs)");
            if (trident.getYRot() != yaw || trident.getXRot() != pitch) helper.fail("the frozen trident turned");
            if (trident.getDeltaMovement().lengthSqr() > 1.0E-12) helper.fail("the frozen trident has a velocity " + fmt(trident.getDeltaMovement()));
            Freezing.unfreeze(trident);
            helper.succeed();
        });
    }
}
