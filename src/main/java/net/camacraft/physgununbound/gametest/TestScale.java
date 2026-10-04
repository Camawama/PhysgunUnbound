package net.camacraft.physgununbound.gametest;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shrinks (or grows) chosen entities for the tests the way a size mod does, through Forge's size event: their box and
 * eye height scale, so Camafy's {@code EntityScale} reads them at that size without Pehkui on the test server. Their
 * motion isn't scaled (that part is Pehkui's alone), which the tests don't need: a hold moves in real blocks.
 */
final class TestScale {
    private static final Map<UUID, Float> SCALED = new ConcurrentHashMap<>();
    private static boolean registered;

    private TestScale() {}

    /** Sizes {@code entity} at {@code factor} from now on (1/16 as in VS Genesis's Great Unknown). */
    static void scale(Entity entity, float factor) {
        register();
        SCALED.put(entity.getUUID(), factor);
        entity.refreshDimensions();
    }

    private static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityEvent.Size.class, TestScale::onSize);
    }

    private static void onSize(EntityEvent.Size event) {
        Float factor = SCALED.get(event.getEntity().getUUID());
        if (factor == null) return;
        // the eye height too: a player's standing eye height doesn't follow its box by itself
        float eye = event.getNewEyeHeight() * factor;
        event.setNewSize(event.getNewSize().scale(factor));
        event.setNewEyeHeight(eye);
    }
}
