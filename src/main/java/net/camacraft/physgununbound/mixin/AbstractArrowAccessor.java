package net.camacraft.physgununbound.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether an arrow is stuck in a block (vanilla keeps it private): a stuck arrow isn't picked up. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("inGround")
    boolean physgununbound$inGround();
}
