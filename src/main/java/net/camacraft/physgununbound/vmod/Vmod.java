package net.camacraft.physgununbound.vmod;

import net.camacraft.camafy.compat.ModPresence;
import net.camacraft.camafy.compat.SoftDep;
import net.camacraft.camafy.util.log.LogOnce;
import net.camacraft.physgununbound.PhysgunUnbound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * The one door to VMod. Everything else in the mod asks here, and nothing here names a VMod class:
 * those are in {@code VmodBridgeImpl}, {@code VmodPackets} and {@code mixin.vmod}, which only load
 * when VMod is installed.
 *
 * <p>If anything about VMod's physgun doesn't match what this mod was written against (a class,
 * method or field gone after a VMod update, or the packet hook not applying), the mod logs it once
 * and switches itself off: every hold ends and nothing is grabbed until the game restarts. VMod's
 * own physgun is never affected.
 */
public final class Vmod {
    public static final String MOD_ID = "the_vmod";
    public static final ResourceLocation PHYSGUN = ResourceLocation.fromNamespaceAndPath(MOD_ID, "physgun");
    private static final String BRIDGE = "net.camacraft.physgununbound.vmod.VmodBridgeImpl";
    private static final String STATE_CLASS = "net.spaceeye.vmod.physgun.PlayerPhysgunState";

    private static volatile VmodAccess access;
    private static volatile boolean failed;
    private static volatile Item physgun;

    private Vmod() {}

    public static boolean present() {
        return ModPresence.isLoaded(MOD_ID);
    }

    /** VMod's physgun, as far as this mod can use it ({@link VmodAccess#NONE} without VMod or after a failure). */
    public static VmodAccess access() {
        if (failed) return VmodAccess.NONE;
        VmodAccess current = access;
        if (current == null) {
            current = present() ? SoftDep.load(MOD_ID, BRIDGE, VmodAccess.class, VmodAccess.NONE) : VmodAccess.NONE;
            access = current;
        }
        return current;
    }

    /** Whether the VMod integration broke this session (holds end, nothing is grabbed). */
    public static boolean failed() {
        return failed;
    }

    /** Switches the integration off for the session, logging why once. Any thread. */
    public static void fail(String what, @Nullable Throwable cause) {
        if (failed) return;
        failed = true;
        if (cause != null) {
            LogOnce.error(PhysgunUnbound.LOGGER, "physgununbound-vmod-failed",
                    "VMod's physgun doesn't work the way Physgun Unbound expects ({}), so its physgun won't pick up "
                            + "entities this session. VMod itself is unaffected. VMod version: {}", what, vmodVersion(), cause);
        } else {
            LogOnce.error(PhysgunUnbound.LOGGER, "physgununbound-vmod-failed",
                    "VMod's physgun doesn't work the way Physgun Unbound expects ({}), so its physgun won't pick up "
                            + "entities this session. VMod itself is unaffected. VMod version: {}", what, vmodVersion());
        }
    }

    /** Whether {@code stack} is VMod's physgun. */
    public static boolean isPhysgun(ItemStack stack) {
        Item item = physgun;
        if (item == null) {
            item = ForgeRegistries.ITEMS.getValue(PHYSGUN);
            if (item == null) item = Items.AIR;
            physgun = item;
        }
        return item != Items.AIR && stack.is(item);
    }

    /**
     * Checks the integration when the server has started: VMod's physgun state class loads (which
     * applies the packet hook), the hook is in, and the bridge can reach everything it uses.
     */
    public static void verify() {
        if (!present()) {
            LogOnce.warn(PhysgunUnbound.LOGGER, "physgununbound-no-vmod",
                    "VMod isn't installed, so Physgun Unbound has nothing to do (it adds entity grabbing to VMod's physgun)");
            return;
        }
        if (failed) return;
        try {
            Class.forName(STATE_CLASS, false, Vmod.class.getClassLoader());
        } catch (Throwable t) {
            fail("its class " + STATE_CLASS + " can't be loaded", t);
            return;
        }
        if (!VmodHookStatus.applied()) {
            fail("the hook into PlayerPhysgunState.fromPkt, which reads the physgun's buttons, didn't apply", null);
            return;
        }
        VmodAccess bridge = access();
        if (!bridge.working()) {
            fail("the bridge to its physgun state didn't load", null);
            return;
        }
        try {
            bridge.probe();
        } catch (Throwable t) {
            fail("its physgun state isn't what the bridge expects", t);
            return;
        }
        if (!isPhysgun(new ItemStack(ForgeRegistries.ITEMS.getValue(PHYSGUN)))) {
            fail("there is no item " + PHYSGUN, null);
            return;
        }
        PhysgunUnbound.LOGGER.info("VMod {} found: its physgun picks up entities too", vmodVersion());
        if (VmodHookStatus.messagesHooked()) {
            PhysgunUnbound.LOGGER.info("VMod's physgun messages are handled on the server thread ([vmod] messagesOnServerThread: {})",
                    VmodMessages.enabled() ? "on" : "off");
        } else {
            LogOnce.warn(PhysgunUnbound.LOGGER, "physgununbound-vmod-messages-unhooked",
                    "The hook into VMod {}'s physgun message handler didn't apply, so VMod handles its messages on the network thread: "
                            + "R by a held ship near unloaded ship chunks can freeze the server for good, as without Physgun Unbound", vmodVersion());
        }
    }

    private static String vmodVersion() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        } catch (Throwable t) {
            return "?";
        }
    }
}
