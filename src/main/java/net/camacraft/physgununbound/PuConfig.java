package net.camacraft.physgununbound;

import net.camacraft.camafy.config.ConfigUtil;
import net.camacraft.camafy.config.IdMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Settings. The server's ({@code serverconfig/physgununbound-server.toml}): the hold itself
 * follows VMod's own physgun settings (its spring and damper constants), so a held entity moves
 * exactly as a held ship does on the same server; the values here are only the fallbacks for when
 * VMod's can't be read, and the rules that ships don't need. The client's
 * ({@code config/physgununbound-client.toml}): the feel of the rotate drag and how a let-go entity
 * turns back upright.
 */
public final class PuConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private PuConfig() {}

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue grabPlayers;
        public final ForgeConfigSpec.DoubleValue range;
        public final ForgeConfigSpec.DoubleValue minDistance;
        public final ForgeConfigSpec.DoubleValue maxDistance;
        public final ForgeConfigSpec.DoubleValue maxSpeed;
        public final ForgeConfigSpec.DoubleValue fallbackSpring;
        public final ForgeConfigSpec.DoubleValue fallbackDamping;
        public final ForgeConfigSpec.BooleanValue followViewPitch;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ungrabbable;

        private final IdMatcher.Cached<EntityType<?>> ungrabbableMatcher;

        @SuppressWarnings("deprecation") // BuiltInRegistries: Camafy's IdMatcher reads vanilla registries (ids and tags alike)
        Server(ForgeConfigSpec.Builder b) {
            b.push("physgun");
            enabled = b.comment("Whether VMod's physgun picks up entities at all.")
                    .define("enabled", true);
            grabPlayers = b.comment("Whether the physgun picks up other players (and anything a player rides).",
                            "A minigame host (Game Start) can still forbid it per player through Camafy's match rules.")
                    .define("grabPlayers", true);
            range = b.comment("How far away an entity can be picked up, in blocks. VMod's physgun reaches ships 100 blocks away.")
                    .defineInRange("range", 100.0, 1.0, 512.0);
            minDistance = b.comment("The closest an entity can be pulled in, in blocks from the eyes.",
                            "VMod lets a ship come right up to 0; an entity that close would sit inside the holder.")
                    .defineInRange("minDistance", 1.0, 0.0, 16.0);
            maxDistance = b.comment("The farthest an entity can be pushed out, in blocks from the eyes.")
                    .defineInRange("maxDistance", 128.0, 1.0, 512.0);
            maxSpeed = b.comment("The fastest a held entity moves, and so the fastest it can be thrown, in blocks per second.",
                            "70 is 3.5 blocks a tick. A held player is moved by their own client, and vanilla servers",
                            "reset a player who moves faster than 10 blocks in one tick.")
                    .defineInRange("maxSpeed", 70.0, 1.0, 190.0);
            fallbackSpring = b.comment("The hold's spring (1/s^2), used only when VMod's own physgun setting (PCONST, 160) can't be read.")
                    .defineInRange("fallbackSpring", 160.0, 1.0, 10000.0);
            fallbackDamping = b.comment("The hold's damper (1/s), used only when VMod's own physgun setting (DCONST, 20) can't be read.")
                    .defineInRange("fallbackDamping", 20.0, 0.0, 1000.0);
            followViewPitch = b.comment("Whether a held entity tips with the holder's view when they look up and down, as a held",
                            "ship does (and as a prop does in Garry's Mod). Off, it only turns with the view's heading and",
                            "stays level; the rotate drag (E) tips it either way.")
                    .define("followViewPitch", true);
            ungrabbable = b.comment("Entities the physgun never picks up: ids (minecraft:wither), #tags, wildcards (create:*) or /regex/.",
                            "Everything else with a body is picked up, modded entities and projectiles included. The tag",
                            "#physgununbound:ungrabbable lists the ender dragon; the markers, displays and interaction boxes",
                            "that only exist to hold data; frames, paintings and lead knots (they break when moved); fishing",
                            "bobbers and eyes of ender (they fly themselves); and Create's contraptions, trains and seats",
                            "(their blocks move them). A datapack can add to the tag.")
                    .defineListAllowEmpty("ungrabbable", List.of("#physgununbound:ungrabbable"), o -> o instanceof String);
            b.pop();
            ungrabbableMatcher = IdMatcher.cached(BuiltInRegistries.ENTITY_TYPE, "physgununbound ungrabbable", ungrabbable);
        }

        public boolean enabled() {
            return ConfigUtil.getOr(enabled);
        }

        public boolean grabPlayers() {
            return ConfigUtil.getOr(grabPlayers);
        }

        public double range() {
            return ConfigUtil.getOr(range);
        }

        public double minDistance() {
            return ConfigUtil.getOr(minDistance);
        }

        public double maxDistance() {
            return Math.max(minDistance(), ConfigUtil.getOr(maxDistance));
        }

        /** Blocks per second. */
        public double maxSpeed() {
            return ConfigUtil.getOr(maxSpeed);
        }

        public double fallbackSpring() {
            return ConfigUtil.getOr(fallbackSpring);
        }

        public double fallbackDamping() {
            return ConfigUtil.getOr(fallbackDamping);
        }

        public boolean followViewPitch() {
            return ConfigUtil.getOr(followViewPitch);
        }

        public boolean isUngrabbable(EntityType<?> type) {
            return ungrabbableMatcher.matches(type);
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue invertMouse;
        public final ForgeConfigSpec.IntValue returnTicks;

        Client(ForgeConfigSpec.Builder b) {
            b.push("rotation");
            invertMouse = b.comment("The rotate drag (E and the mouse) turns what is held the way the mouse goes: mouse left",
                            "turns its near side to the left, mouse up tips its top away. That is the corrected feel, for",
                            "ships and entities alike. Set true for VMod's own sense (mouse left turned the near side right).")
                    .define("invertMouse", false);
            returnTicks = b.comment("How long an entity that was held turned takes to turn back upright once let go or",
                            "unfrozen, in ticks (20 a second). 0 snaps.")
                    .defineInRange("returnTicks", 10, 0, 200);
            b.pop();
        }

        public boolean invertMouse() {
            return ConfigUtil.getOr(invertMouse);
        }

        public int returnTicks() {
            return ConfigUtil.getOr(returnTicks);
        }
    }
}
