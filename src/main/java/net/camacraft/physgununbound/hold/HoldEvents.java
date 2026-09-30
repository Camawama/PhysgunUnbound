package net.camacraft.physgununbound.hold;

import net.camacraft.physgununbound.vmod.Vmod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * The Forge events that drive and clean up the holds. A hold ends when its holder logs out, dies
 * or changes dimension, and when its entity dies, is removed, is unloaded or changes dimension;
 * everything ends before the server saves on stopping.
 */
public final class HoldEvents {
    private HoldEvents() {}

    public static void register() {
        // HIGHEST: before Architectury's server tick (HIGH), which runs VMod's own physgun tick,
        // so an entity in front of a ship is grabbed before VMod's seeking can grab the ship.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HoldEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(HoldEvents::onLogout);
        MinecraftForge.EVENT_BUS.addListener(HoldEvents::onChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HoldEvents::onDeath);
        MinecraftForge.EVENT_BUS.addListener(HoldEvents::onJoin);
        MinecraftForge.EVENT_BUS.addListener(HoldEvents::onLeave);
        MinecraftForge.EVENT_BUS.addListener(HoldEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(HoldEvents::onServerStopping);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) Holds.tick(event.getServer());
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Holds.endInvolving(event.getEntity(), Holds.End.LOST, Holds.End.GONE);
        PhysgunInput.Queues.forget(event.getEntity().getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Holds.endInvolving(event.getEntity(), Holds.End.LOST, Holds.End.GONE);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) return;
        Holds.endInvolving(event.getEntity(), Holds.End.LOST, Holds.End.GONE);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) Freezing.onJoin(event.getEntity());
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Holds.endInvolving(event.getEntity(), Holds.End.LOST, Holds.End.GONE);
        Freezing.onLeave(event.getEntity());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        Vmod.verify();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // before the levels are saved, so held entities are written with their own flags back
        Holds.clear();
    }
}
