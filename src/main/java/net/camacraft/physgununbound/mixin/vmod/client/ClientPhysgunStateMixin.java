package net.camacraft.physgununbound.mixin.vmod.client;

import net.camacraft.physgununbound.PuConfig;
import net.spaceeye.vmod.physgun.ClientPhysgunState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The rotate drag turns things the way the mouse goes. VMod's client builds the drag's rotation
 * in {@code handleMouseMovement}: mouse X as a turn about the camera's up axis, mouse Y as a turn
 * about its right axis, both with the sense that moves the FAR side of the thing with the mouse
 * (mouse left turned the near side to the right, mouse up dropped the near side and tipped the top
 * toward you), which is what felt reversed. Both angles are negated here, for ships and entities
 * alike (the same message carries the drag to VMod's ship code and to this mod's holds), so the
 * near side follows the mouse: left, right, up (the top tips away) and down. The client config
 * keeps VMod's sense per axis ({@code rotation.invertMouseX}, {@code rotation.invertMouseY}).
 *
 * <p>Applied only with VMod installed ({@link net.camacraft.physgununbound.mixin.vmod.VmodMixinPlugin});
 * the method is named as Kotlin compiles it (an {@code internal} function of VMod's module) and
 * has exactly two {@code rotateAxis} calls, mouse X first (VMod 1.9.1). If a VMod update changes
 * it, the drag simply keeps VMod's sense.
 */
@Pseudo
@Mixin(value = ClientPhysgunState.class, remap = false)
public abstract class ClientPhysgunStateMixin {
    @ModifyArg(method = "handleMouseMovement$VMod(DD)Z",
            at = @At(value = "INVOKE", target = "Lorg/joml/Quaterniond;rotateAxis(DLorg/joml/Vector3dc;)Lorg/joml/Quaterniond;",
                    ordinal = 0, remap = false),
            index = 0, require = 0, remap = false)
    private double physgununbound$mouseXTurnsTheNearSide(double angle) {
        return PuConfig.CLIENT.invertMouseX() ? angle : -angle;
    }

    @ModifyArg(method = "handleMouseMovement$VMod(DD)Z",
            at = @At(value = "INVOKE", target = "Lorg/joml/Quaterniond;rotateAxis(DLorg/joml/Vector3dc;)Lorg/joml/Quaterniond;",
                    ordinal = 1, remap = false),
            index = 0, require = 0, remap = false)
    private double physgununbound$mouseYTipsTheNearSide(double angle) {
        return PuConfig.CLIENT.invertMouseY() ? angle : -angle;
    }
}
