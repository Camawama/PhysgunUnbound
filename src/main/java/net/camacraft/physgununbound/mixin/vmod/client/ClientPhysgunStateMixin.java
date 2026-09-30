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
 * about its right axis. The Y sense drags the near face with the mouse (mouse up tips the top
 * away), the X sense did the opposite (mouse left turned the near face to the right), which is
 * what felt reversed. The X turn's angle is negated here, for ships and entities alike (the same
 * message carries it to VMod's ship code and to this mod's holds), unless the client config keeps
 * VMod's sense ({@code rotation.invertMouse}).
 *
 * <p>Applied only with VMod installed ({@link net.camacraft.physgununbound.mixin.vmod.VmodMixinPlugin});
 * the method is named as Kotlin compiles it (an {@code internal} function of VMod's module). If a
 * VMod update changes it, the drag simply keeps VMod's sense.
 */
@Pseudo
@Mixin(value = ClientPhysgunState.class, remap = false)
public abstract class ClientPhysgunStateMixin {
    @ModifyArg(method = "handleMouseMovement$VMod(DD)Z",
            at = @At(value = "INVOKE", target = "Lorg/joml/Quaterniond;rotateAxis(DLorg/joml/Vector3dc;)Lorg/joml/Quaterniond;",
                    ordinal = 0, remap = false),
            index = 0, require = 0, remap = false)
    private double physgununbound$turnTheWayTheMouseGoes(double angle) {
        return PuConfig.CLIENT.invertMouse() ? angle : -angle;
    }
}
