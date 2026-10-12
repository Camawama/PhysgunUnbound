package net.camacraft.physgununbound.gametest;

import com.mojang.authlib.GameProfile;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.camacraft.physgununbound.hold.Hold;
import net.camacraft.physgununbound.hold.Holds;
import net.camacraft.physgununbound.vmod.Vmod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.spaceeye.vmod.physgun.ServerPhysgunState;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.properties.ChunkClaim;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * VMod's physgun next to a real Valkyrien Skies raft (only with -Pvmod). A pig standing on the
 * raft's deck is picked up instead of the raft under it, and aiming at the raft's hull with nothing
 * in front still grabs the raft through VMod's own code: the addon only ever takes what is in
 * front of the first block.
 *
 * <p>The rig follows Unleashed Lead's ship tests: VS loads ships for players in the server's
 * player list, so the fake player that holds the physgun is put in it; the test is built far from
 * its plot, in its own batch, in chunks it force-loads.
 */
@GameTestHolder(PhysgunUnbound.MOD_ID)
@PrefixGameTestTemplate(false)
public class VmodShipTests {
    private static final String BATCH = "vmodShips";
    /** How far from its plot (along z) the test builds. */
    private static final int AWAY = 4096;

    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = 400, batch = BATCH)
    public static void aPigOnADeckIsPickedUpAndTheRaftStillGrabbable(GameTestHelper helper) {
        if (!Ships.available()) {
            helper.fail("Valkyrien Skies isn't running");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos o = origin(helper);
        // a 5x1x5 plank raft three blocks up: its deck top is at o.y + 4
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(0, 3, 0), o.offset(4, 3, 4))) {
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            blocks.add(pos.immutable());
        }
        // the holder floats level with the deck, five blocks west of it, holding the physgun
        FakePlayer holder = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[PU ship test]"));
        holder.setPos(o.getX() - 4.5, o.getY() + 4.0, o.getZ() + 2.5);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(Vmod.PHYSGUN)));
        List<ServerPlayer> players = playerList(level);
        if (!players.contains(holder)) players.add(holder);

        ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0);
        if (ship == null) {
            players.remove(holder);
            helper.fail("VS couldn't assemble a raft");
            return;
        }
        ship.setStatic(true);
        ChunkClaim claim = ship.getChunkClaim();
        forceClaim(level, claim, true);
        long shipId = ship.getId();
        ShipRef ref = ShipRef.of(level, shipId);
        UUID id = holder.getUUID();
        Pig[] pig = new Pig[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ref.isLoaded(), "VS hasn't loaded the raft yet"))
                .thenExecute(() -> {
                    pig[0] = EntityType.PIG.create(level);
                    pig[0].moveTo(o.getX() + 2.5, o.getY() + 4.2, o.getZ() + 2.5, 0.0F, 0.0F);
                    pig[0].removeFreeWill();
                    level.addFreshEntity(pig[0]);
                })
                .thenIdle(20) // it lands on the deck
                .thenExecute(() -> {
                    helper.assertTrue(pig[0].getY() > o.getY() + 3.9, "the pig fell through the raft: y " + pig[0].getY());
                    aim(holder, pig[0].getBoundingBox().getCenter());
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    Hold hold = Holds.holdOf(holder);
                    helper.assertTrue(hold != null && hold.entity() == pig[0], "the physgun didn't pick up the pig on the deck");
                    helper.assertTrue(state(id) == -1L, "VMod grabbed the raft under the pig too");
                    aim(holder, new Vec3(o.getX() + 2.5, o.getY() + 6.5, o.getZ() + 2.5)); // lift it off the deck
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    Hold hold = Holds.holdOf(holder);
                    helper.assertTrue(hold != null && TestUtil.error(hold) < 0.15,
                            "the pig lifted off the deck doesn't follow the physgun: " + (hold == null ? "dropped" : TestUtil.fmt(TestUtil.error(hold))));
                    aim(holder, new Vec3(o.getX() + 2.5, o.getY() + 3.0, o.getZ() + 2.5)); // press it into the deck
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(Holds.holdOf(holder) != null, "pressing the pig into the deck ended the hold");
                    helper.assertTrue(pig[0].getY() > o.getY() + 3.95, "the held pig went into the raft's deck: feet at "
                            + TestUtil.fmt(pig[0].getY() - o.getY()) + " (deck top 4)");
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(false));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(Holds.holdOf(holder) == null, "letting go didn't let go of the pig");
                    // the raft's west face, below the deck, with nothing in front of it
                    aim(holder, new Vec3(o.getX(), o.getY() + 3.5, o.getZ() + 2.5));
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(state(id) == shipId, "VMod didn't grab the raft (holds ship " + state(id) + ")");
                    helper.assertTrue(Holds.holdOf(holder) == null, "the addon picked something up instead of the raft");
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(false));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(state(id) == -1L, "VMod didn't let go of the raft");
                    players.remove(holder);
                    forceClaim(level, claim, false);
                    helper.succeed();
                });
    }

    /**
     * In a shrunk world (VS Genesis's Great Unknown: players and ships at 1/16) the wheel moves a held ship in the
     * holder's own blocks: a 1/16 holder grabs a 1/16 raft through VMod's own code, and two wheel notches move VMod's
     * hold distance two of the holder's blocks (1/8 of a block), not two full blocks.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = 400, batch = BATCH)
    public static void aShrunkHoldersWheelMovesAShrunkShipInItsOwnBlocks(GameTestHelper helper) {
        if (!Ships.available()) {
            helper.fail("Valkyrien Skies isn't running");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos o = origin(helper);
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(0, 3, 0), o.offset(4, 3, 4))) {
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            blocks.add(pos.immutable());
        }
        FakePlayer holder = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[PU tiny ship test]"));
        holder.setPos(o.getX() + 2.5, o.getY() + 3.0, o.getZ() - 1.5);
        TestScale.scale(holder, 1.0F / 16.0F);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(Vmod.PHYSGUN)));
        List<ServerPlayer> players = playerList(level);
        if (!players.contains(holder)) players.add(holder);

        ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0 / 16.0);
        if (ship == null) {
            players.remove(holder);
            helper.fail("VS couldn't assemble a raft");
            return;
        }
        ship.setStatic(true);
        ChunkClaim claim = ship.getChunkClaim();
        forceClaim(level, claim, true);
        long shipId = ship.getId();
        ShipRef ref = ShipRef.of(level, shipId);
        UUID id = holder.getUUID();
        double[] before = new double[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ref.isLoaded(), "VS hasn't loaded the raft yet"))
                .thenExecute(() -> {
                    var p = ship.getTransform().getPositionInWorld();
                    aim(holder, new Vec3(p.x(), p.y(), p.z()));
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(state(id) == shipId, "VMod didn't grab the 1/16 raft (holds ship " + state(id) + ")");
                    before[0] = ServerPhysgunState.INSTANCE.getPlayerStates().get(id).getDistanceFromPlayer();
                    VmodIntegrationTests.send(holder, m -> {
                        m.setPrimaryActivated(true);
                        m.setIncreaseDistanceBy(2.0);
                    });
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    double after = ServerPhysgunState.INSTANCE.getPlayerStates().get(id).getDistanceFromPlayer();
                    double step = after - before[0];
                    PhysgunUnbound.LOGGER.info("[PU gametest] 1/16 holder, 1/16 raft, two wheel notches: grabbed at {} blocks, moved {}",
                            TestUtil.fmt(before[0]), TestUtil.fmt(step));
                    helper.assertTrue(Math.abs(step - 0.125) < 1.0E-6, "two notches moved VMod's hold on the 1/16 raft "
                            + TestUtil.fmt(step) + " blocks, not 0.125");
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(false));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    players.remove(holder);
                    forceClaim(level, claim, false);
                    helper.succeed();
                });
    }

    /**
     * A ship broken up while VMod holds it: the holder's next physgun message (VMod's client sends one every tick the
     * camera turns, and on right click) must not reach VMod's handler with the gone ship still held. VMod looks it up
     * with Kotlin's {@code !!}, and on a real server the NullPointerException on the network thread kicks the player
     * ("Internal Exception: java.lang.NullPointerException"; the owner's report). The addon lets go of it first.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = 400, batch = BATCH)
    public static void aShipDeletedWhileHeldIsLetGoAndNoMessageFails(GameTestHelper helper) {
        if (!Ships.available()) {
            helper.fail("Valkyrien Skies isn't running");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos o = origin(helper);
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(0, 3, 0), o.offset(2, 3, 2))) {
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            blocks.add(pos.immutable());
        }
        FakePlayer holder = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[PU deleted ship test]"));
        holder.setPos(o.getX() - 4.5, o.getY() + 3.0, o.getZ() + 1.5);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(Vmod.PHYSGUN)));
        List<ServerPlayer> players = playerList(level);
        if (!players.contains(holder)) players.add(holder);

        ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0);
        if (ship == null) {
            players.remove(holder);
            helper.fail("VS couldn't assemble a raft");
            return;
        }
        ship.setStatic(true);
        ChunkClaim claim = ship.getChunkClaim();
        forceClaim(level, claim, true);
        long shipId = ship.getId();
        ShipRef ref = ShipRef.of(level, shipId);
        UUID id = holder.getUUID();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ref.isLoaded(), "VS hasn't loaded the raft yet"))
                .thenExecute(() -> {
                    aim(holder, new Vec3(o.getX(), o.getY() + 3.5, o.getZ() + 1.5)); // its west face
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(state(id) == shipId, "VMod didn't grab the raft (holds ship " + state(id) + ")");
                    org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(level).deleteShip(ship);
                })
                .thenWaitUntil(() -> helper.assertTrue(Ships.byId(level, shipId) == null, "VS hasn't removed the raft yet"))
                .thenExecute(() -> {
                    // a camera turn and a right click (freeze), as the holder's client would send them right away
                    try {
                        VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(true));
                        VmodIntegrationTests.send(holder, m -> {
                            m.setPrimaryActivated(true);
                            m.setFreezeSelected(true);
                        });
                        VmodIntegrationTests.send(holder, m -> {
                            m.setPrimaryActivated(true);
                            m.setUnfreezeAllOrOne(true);
                        });
                    } catch (Throwable t) {
                        Throwable cause = t.getCause() != null && t.getCause().getCause() != null ? t.getCause().getCause() : t;
                        players.remove(holder);
                        forceClaim(level, claim, false);
                        helper.fail("a physgun message after the held ship was deleted failed in VMod's handler: " + cause);
                        return;
                    }
                    helper.assertTrue(state(id) == -1L, "VMod still holds the deleted ship " + state(id));
                    helper.assertFalse(ServerPhysgunState.INSTANCE.getActive().contains(id), "the holder is still active in VMod");
                })
                .thenExecute(() -> {
                    VmodIntegrationTests.send(holder, m -> m.setPrimaryActivated(false));
                    players.remove(holder);
                    forceClaim(level, claim, false);
                    helper.succeed();
                });
    }

    private static long state(UUID id) {
        var state = ServerPhysgunState.INSTANCE.getPlayerStates().get(id);
        return state == null ? -1L : state.getMainShipId();
    }

    /** Turns the fake player to look from its eyes at {@code point}. */
    private static void aim(FakePlayer player, Vec3 point) {
        Vec3 d = point.subtract(player.getEyePosition()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(Math.asin(-d.y));
        TestUtil.look(player, yaw, pitch);
    }

    private static void forceClaim(ServerLevel level, ChunkClaim claim, boolean force) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(claim.getXMiddle() + dx, claim.getZMiddle() + dz, force);
            }
        }
    }

    /** The test is built {@link #AWAY} blocks from its plot, in chunks loaded now. */
    private static BlockPos origin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(BlockPos.ZERO);
        int groundTop = level.getMinBuildHeight() + 4; // the flat world: bedrock, dirt, dirt, grass
        BlockPos o = new BlockPos(plot.getX() + 8, groundTop, plot.getZ() + AWAY);
        for (int cx = (o.getX() - 16) >> 4; cx <= (o.getX() + 24) >> 4; cx++) {
            for (int cz = (o.getZ() - 16) >> 4; cz <= (o.getZ() + 24) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
        return o;
    }

    /** The server's own player list (what VS reads), not the read-only view {@code getPlayers()} hands out. */
    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> playerList(ServerLevel level) {
        try {
            return (List<ServerPlayer>) ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_")
                    .get(level.getServer().getPlayerList());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("can't reach the server's player list", e);
        }
    }
}
