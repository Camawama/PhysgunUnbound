package net.camacraft.physgununbound.gametest;

import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.vmod.Vmod;
import net.camacraft.physgununbound.vmod.VmodBridgeImpl;
import net.camacraft.physgununbound.vmod.VmodHookStatus;
import net.camacraft.physgununbound.vmod.VmodMessages;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.spaceeye.vmod.physgun.PlayerPhysgunState;
import net.spaceeye.vmod.physgun.ServerPhysgunState;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static net.camacraft.physgununbound.gametest.TestUtil.PLATFORM;

/**
 * The 2026-10-30 freeze (only with -Pvmod): VMod held its per-player lock on the network thread while its R ray-cast
 * waited for the server thread to load a chunk, and the server thread waited for that lock at the start of the tick.
 * <ul>
 *   <li>A physgun message that arrives on another thread is handed to the server thread: VMod's handler doesn't run
 *   on the sending thread, and runs on the server thread before the next tick.</li>
 *   <li>Physgun Unbound's let-go of a held ship that is gone never waits for VMod's lock, whoever holds it: done at
 *   once on the server thread while the messages are handled there, and on its own thread (after the lock comes
 *   free) while they aren't.</li>
 * </ul>
 * Each in a batch of its own: one switches the hand-over off for a moment through the system property.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class VmodThreadTests {
    /** One batch each: one switches the hand-over off through the system property, which the others read. */
    private static final String BATCH = "vmodThreads";
    /** A ship id no ship has (VS counts from 0). */
    private static final long NO_SHIP = 987_654_321L;

    @GameTest(template = PLATFORM, timeoutTicks = 100, batch = BATCH + "1")
    public static void aPhysgunMessageFromAnotherThreadIsHandledOnTheServerThread(GameTestHelper helper) {
        if (!VmodHookStatus.messagesHooked()) {
            helper.fail("the hook into VMod's physgun message handler didn't apply");
            return;
        }
        if (!VmodMessages.enabled()) {
            helper.fail("[vmod] messagesOnServerThread is off");
            return;
        }
        FakePlayer holder = holder(helper);
        UUID id = holder.getUUID();
        long deferredBefore = VmodMessages.deferred();
        long handledBefore = VmodMessages.handledOnServerThread();
        AtomicReference<Throwable> failed = new AtomicReference<>();
        // the left button, sent from a thread of its own as the network thread would
        Thread sender = new Thread(() -> {
            try {
                VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true));
            } catch (Throwable t) {
                failed.set(t);
            }
        }, "PU test sender");
        sender.start();
        try {
            sender.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (sender.isAlive()) {
            helper.fail("VMod's handler didn't return on the sending thread within 2 s");
            return;
        }
        if (failed.get() != null) {
            helper.fail("sending failed: " + failed.get());
            return;
        }
        helper.assertTrue(VmodMessages.deferred() == deferredBefore + 1, "the message wasn't handed to the server thread ("
                + (VmodMessages.deferred() - deferredBefore) + " handed over)");
        helper.assertFalse(seeking(id), "VMod's handler ran on the sending thread (the player seeks already)");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(seeking(id), "the message handed to the server thread was never handled (no seeking)");
            helper.assertTrue(VmodMessages.handledOnServerThread() > handledBefore, "VMod's handler didn't run on the server thread");
            VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(false));
            helper.assertFalse(seeking(id), "letting go on the server thread didn't stop the seeking");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 4000, batch = BATCH + "2")
    public static void aMissingHeldShipIsLetGoWithoutWaitingForVmodsLock(GameTestHelper helper) {
        letGoWhileTheLockIsHeld(helper, true);
    }

    @GameTest(template = PLATFORM, timeoutTicks = 4000, batch = BATCH + "3")
    public static void withTheHandOverOffTheLetGoWaitsOnItsOwnThread(GameTestHelper helper) {
        letGoWhileTheLockIsHeld(helper, false);
    }

    /**
     * VMod holds a ship that doesn't exist for a player in {@code active}, while another thread holds VMod's lock for
     * that player for a second (as VMod's handler did, waiting for a chunk): the let-go returns at once either way.
     */
    private static void letGoWhileTheLockIsHeld(GameTestHelper helper, boolean handOver) {
        if (Vmod.failed() || !Vmod.access().working()) {
            helper.fail("the VMod bridge isn't working");
            return;
        }
        String before = System.getProperty(VmodMessages.PROPERTY);
        System.setProperty(VmodMessages.PROPERTY, Boolean.toString(handOver));
        Runnable restore = () -> {
            if (before == null) System.clearProperty(VmodMessages.PROPERTY);
            else System.setProperty(VmodMessages.PROPERTY, before);
        };
        FakePlayer holder = holder(helper);
        UUID id = holder.getUUID();
        VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true)); // on this thread: the state exists now
        PlayerPhysgunState state = ServerPhysgunState.INSTANCE.getPlayerStates().get(id);
        if (state == null) {
            restore.run();
            helper.fail("VMod made no physgun state for the player");
            return;
        }
        state.setMainShipId(NO_SHIP);
        ServerPhysgunState.INSTANCE.getActive().add(id);

        CountDownLatch held = new CountDownLatch(1);
        Thread holderThread = new Thread(() -> {
            synchronized (state.getLock()) {
                held.countDown();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }, "PU test lock holder");
        holderThread.start();
        try {
            if (!held.await(2, TimeUnit.SECONDS)) {
                restore.run();
                helper.fail("the lock holder never took VMod's lock");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long hereBefore = VmodBridgeImpl.letGoesHere();
        long elsewhereBefore = VmodBridgeImpl.letGoesOnLockThread();
        long start = System.nanoTime();
        Vmod.access().letGoOfMissingShips();
        double ms = (System.nanoTime() - start) / 1.0E6;
        restore.run(); // the call has chosen its way
        // VMod's own tick takes the lock of everyone in `active` (that wait is VMod's): keep the test player out of it
        ServerPhysgunState.INSTANCE.getActive().remove(id);
        if (ms > 100.0) {
            helper.fail("the let-go waited " + TestUtil.fmt(ms) + " ms for VMod's lock");
            return;
        }
        if (handOver) {
            helper.assertTrue(VmodBridgeImpl.letGoesHere() == hereBefore + 1 && state.getMainShipId() == -1L,
                    "the missing ship wasn't let go right away on the server thread (holds " + state.getMainShipId() + ")");
        } else {
            helper.assertTrue(state.getMainShipId() == NO_SHIP, "the let-go didn't wait for the lock to come free");
        }
        // the lock comes free after a second of real time (the gametest server's ticks are no clock)
        helper.succeedWhen(() -> {
            helper.assertTrue(state.getMainShipId() == -1L, "the missing ship is still held (" + state.getMainShipId() + ")");
            if (!handOver) {
                helper.assertTrue(VmodBridgeImpl.letGoesOnLockThread() == elsewhereBefore + 1,
                        "the let-go wasn't done on the lock thread once the lock came free");
            }
            helper.assertTrue(seeking(id), "the player, button still down, doesn't seek again");
            VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(false));
        });
    }

    private static FakePlayer holder(GameTestHelper helper) {
        FakePlayer holder = TestUtil.holder(helper, new Vec3(3.5, 2.0, 12.5), 90.0F, 0.0F);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(Vmod.PHYSGUN)));
        return holder;
    }

    private static boolean seeking(UUID id) {
        return ServerPhysgunState.INSTANCE.getActivelySeeking().contains(id);
    }
}
