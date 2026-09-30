package net.camacraft.physgununbound.client;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client wiring: the beams, the turned models of held and frozen entities, and the held player's own side of a hold. */
public final class PhysgunUnboundClient {
    private PhysgunUnboundClient() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(PhysgunUnboundClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientBeams::render);
        MinecraftForge.EVENT_BUS.addListener(PhysgunUnboundClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(PhysgunUnboundClient::onRespawn);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ClientBeams.tick();
        ClientModelRotations.tick();
        ClientHeld.tick();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBeams.clear();
        ClientModelRotations.clear();
        ClientHeld.end();
    }

    /** Respawning or changing dimension: entity ids start over, so nothing held carries across. */
    private static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        ClientBeams.clear();
        ClientModelRotations.clear();
        ClientHeld.end();
    }
}
