package net.camacraft.physgununbound.gametest;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Freezing;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.HoldMotion;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.vmod.Vmod;
import net.camacraft.physgununbound.vmod.VmodHookStatus;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.spaceeye.vmod.networking.C2SConnection;
import net.spaceeye.vmod.physgun.PlayerPhysgunState;
import net.spaceeye.vmod.physgun.ServerPhysgunState;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;
import static net.camacraft.physgununbound.gametest.TestUtil.fmt;

/**
 * The real VMod, driven through its own physgun message handler (only compiled with -Pvmod, which
 * puts VMod, Valkyrien Skies, Kotlin for Forge and Architectury on the dev runtime). Each message
 * is built as VMod's client builds it and handed to VMod's registered server handler, so the path
 * is exactly the game's: VMod reads it, the hook sees it, VMod decides about seeking, and this
 * mod's tick does the rest.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class VmodIntegrationTests {

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void vmodsPhysgunPicksUpAndDropsAPig(GameTestHelper helper) {
        if (!VmodHookStatus.applied()) helper.fail("the hook into VMod's PlayerPhysgunState.fromPkt didn't apply");
        if (Vmod.failed() || !Vmod.access().working()) helper.fail("the VMod bridge isn't working");

        // looking down at the pig on the floor (its middle is 1.2 below the eyes, 4 ahead)
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), -90.0F, 16.0F);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(Vmod.PHYSGUN)));
        if (!Vmod.isPhysgun(holder.getMainHandItem())) helper.fail("the fake player holds no physgun");
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, 2.0, 12.5));
        UUID id = holder.getUUID();
        float[] startYaw = new float[1];
        double[] startDistance = new double[1];

        // left button down: VMod starts seeking (for a ship)
        send(holder, m -> m.setPrimaryActivated(true));
        if (!seeking(id)) helper.fail("VMod didn't start seeking on the left button");

        helper.runAfterDelay(2, () -> {
            Hold hold = Holds.holdOf(holder);
            if (hold == null || hold.entity() != pig) helper.fail("the physgun didn't pick the pig up");
            if (seeking(id)) helper.fail("VMod is still seeking behind the hold");
            if (state(id).getRID() != -1) helper.fail("VMod's seeking beam is still there");
            if (Math.abs(hold.spring() - state(id).getPConst()) > 1.0E-9 || Math.abs(hold.damping() - state(id).getDConst()) > 1.0E-9) {
                helper.fail("the hold's spring and damper aren't VMod's: " + fmt(hold.spring()) + ", " + fmt(hold.damping()));
            }
            if (!PhysicsOwnership.claimed(pig, PhysicsAspect.MOTION)) helper.fail("the held pig isn't claimed");
            startYaw[0] = hold.yaw();
            startDistance[0] = hold.distance();
            TestUtil.look(holder, -90.0F, -10.0F); // lift it

            // the wheel, while holding: VMod must stand down (no seeking again)
            send(holder, m -> {
                m.setPrimaryActivated(true);
                m.setIncreaseDistanceBy(2.0);
            });
            if (seeking(id)) helper.fail("a wheel message put VMod back to seeking");
            if (!state(id).getRotate()) helper.fail("VMod's handler wasn't told to stand down");
        });
        helper.runAfterDelay(4, () -> {
            Hold hold = Holds.holdOf(holder);
            if (hold == null) {
                helper.fail("the wheel ended the hold");
                return;
            }
            if (Math.abs(hold.distance() - (startDistance[0] + 2.0)) > 1.0E-9) {
                helper.fail("two notches moved the pig to " + fmt(hold.distance()) + " from " + fmt(startDistance[0]));
            }
            // the rotate key's drag: a 30 degree turn to the left
            send(holder, m -> {
                m.setPrimaryActivated(true);
                m.setRotate(true);
                m.setQuatDiff(HoldMotion.yawTurn(-30.0));
            });
        });
        helper.runAfterDelay(7, () -> {
            float turned = Mth.wrapDegrees(pig.getYRot() - startYaw[0]);
            if (Math.abs(turned + 30.0F) > 1.0F) helper.fail("the rotate drag turned the pig " + fmt(turned) + " degrees, not -30");
            // right click while holding: freeze it where it is
            send(holder, m -> {
                m.setPrimaryActivated(true);
                m.setFreezeSelected(true);
            });
        });
        helper.runAfterDelay(9, () -> {
            if (Holds.holdOf(holder) != null) helper.fail("right click didn't let go");
            if (!Freezing.isFrozen(pig) || !pig.isNoAi() || !pig.isNoGravity()) helper.fail("right click didn't freeze the pig");
            if (seeking(id)) helper.fail("VMod seeks while the right button is down");
            // let go of both buttons, then R with the left button up: unfreeze what it points at
            send(holder, m -> m.setFreezeSelected(false));
            send(holder, m -> m.setUnfreezeAllOrOne(true));
        });
        helper.runAfterDelay(11, () -> {
            if (Freezing.isFrozen(pig) || pig.isNoAi() || pig.isNoGravity()) helper.fail("R didn't unfreeze the pig");
            send(holder, m -> m.setUnfreezeAllOrOne(false));
            // grab it again, swing, and let go: thrown
            send(holder, m -> m.setPrimaryActivated(true));
        });
        helper.runAfterDelay(14, () -> {
            if (Holds.holdOf(holder) == null) helper.fail("the physgun didn't pick the pig up again");
            TestUtil.look(holder, -60.0F, -10.0F);
        });
        helper.runAfterDelay(16, () -> TestUtil.look(holder, -30.0F, -10.0F));
        helper.runAfterDelay(17, () -> {
            Hold hold = Holds.holdOf(holder);
            Vec3 speed = hold == null ? Vec3.ZERO : hold.velocity();
            send(holder, m -> m.setPrimaryActivated(false));
            helper.runAfterDelay(1, () -> {
                if (Holds.holdOf(holder) != null) helper.fail("letting go of the left button didn't let go");
                if (PhysicsOwnership.claimed(pig, PhysicsAspect.MOTION)) helper.fail("the released pig is still claimed");
                if (speed.horizontalDistance() < 2.0) helper.fail("the swing only reached " + fmt(speed) + " blocks/s");
                if (pig.getDeltaMovement().horizontalDistance() < speed.horizontalDistance() * 0.05 * 0.8) {
                    helper.fail("the pig was let go with " + fmt(pig.getDeltaMovement().scale(20.0)) + " blocks/s, not about " + fmt(speed));
                }
                if (seeking(id)) helper.fail("VMod still seeks after the release");
                helper.succeed();
            });
        });
    }

    private static boolean seeking(UUID id) {
        return ServerPhysgunState.INSTANCE.getActivelySeeking().contains(id);
    }

    private static PlayerPhysgunState state(UUID id) {
        return ServerPhysgunState.INSTANCE.getPlayerStates().get(id);
    }

    /** One physgun message, as VMod's client sends it, handled by VMod's own server handler. */
    static void send(ServerPlayer player, Consumer<ServerPhysgunState.C2SPhysgunStateChanged> fill) {
        ServerPhysgunState.C2SPhysgunStateChanged message = new ServerPhysgunState.C2SPhysgunStateChanged();
        fill.accept(message);
        FriendlyByteBuf buf = message.serialize();
        try {
            Class<?> contextType = Class.forName("dev.architectury.networking.NetworkManager$PacketContext");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object server = Enum.valueOf((Class) Class.forName("dev.architectury.utils.Env"), "SERVER");
            Object context = Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[]{contextType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getPlayer" -> player;
                        case "queue" -> {
                            ((Runnable) args[0]).run();
                            yield null;
                        }
                        case "getEnvironment" -> server;
                        case "getEnv" -> Dist.DEDICATED_SERVER;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "test packet context";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            C2SConnection<?> connection = ServerPhysgunState.INSTANCE.getC2sPrimaryStateChanged();
            Method handler = C2SConnection.class.getMethod("serverHandler", FriendlyByteBuf.class, contextType);
            handler.invoke(connection, buf, context);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("couldn't hand the message to VMod's handler", e);
        }
    }
}
