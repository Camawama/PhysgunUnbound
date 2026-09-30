package net.camacraft.physgununbound;

import com.mojang.logging.LogUtils;
import net.camacraft.physgununbound.client.PhysgunUnboundClient;
import net.camacraft.physgununbound.hold.HeldEntities;
import net.camacraft.physgununbound.hold.HoldEvents;
import net.camacraft.physgununbound.network.PuNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Physgun Unbound: VMod's physgun picks up entities too, not only Valkyrien Skies ships.
 *
 * <p>Where the pieces live:
 * <ul>
 *   <li>{@code hold}: the holds themselves (what is held, by whom, how it moves), picking a target,
 *   freezing, and the event wiring. Plain Minecraft and Camafy: the gametests drive it directly.</li>
 *   <li>{@code vmod}: the bridge to VMod's physgun (its input packets, its seeking state, its beam),
 *   loaded only when VMod is installed. Every call into VMod is guarded: if VMod's internals don't
 *   match, the bridge logs once and the mod does nothing.</li>
 *   <li>{@code network}: two client-bound messages (the beam to a held entity, and the motion of a
 *   held player, which only that player's client can move).</li>
 *   <li>{@code client}: the beam, and the held player's side of the hold.</li>
 *   <li>{@code mixin}: VMod's packet hook, a stop to held entities' own movement, and the
 *   flying-kick reset for held players.</li>
 * </ul>
 */
@Mod(PhysgunUnbound.MOD_ID)
public final class PhysgunUnbound {
    public static final String MOD_ID = "physgununbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PhysgunUnbound(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.SERVER, PuConfig.SERVER_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, PuConfig.CLIENT_SPEC);

        PuNetwork.init();
        HeldEntities.registerClaims();
        HoldEvents.register();
        if (FMLEnvironment.dist.isClient()) PhysgunUnboundClient.init(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
