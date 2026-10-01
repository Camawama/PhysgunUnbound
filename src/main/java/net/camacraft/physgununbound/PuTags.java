package net.camacraft.physgununbound;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** The mod's tags. */
public final class PuTags {
    /**
     * Entities whose renderer faces the camera (a thrown egg's sprite, a dropped item, an orb): the
     * physgun never draws them turned, since a billboard turned about its entity stops facing the
     * camera. The vanilla ones are also recognised by their renderer class; this is for other mods'.
     */
    public static final TagKey<EntityType<?>> BILLBOARDS = TagKey.create(Registries.ENTITY_TYPE, PhysgunUnbound.id("billboards"));

    private PuTags() {}
}
