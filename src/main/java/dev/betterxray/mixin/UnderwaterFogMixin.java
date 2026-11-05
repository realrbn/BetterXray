package dev.betterxray.mixin;

//? if >=1.21.6 {
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While X-Ray is active, a camera submerged in water uses the regular atmospheric
 * fog instead of the short-range blue water fog (color, distances and water-vision tint).
 */
@Mixin(FogRenderer.class)
public abstract class UnderwaterFogMixin {
    @Inject(method = "getFogType", at = @At("RETURN"), cancellable = true)
    private void betterxray$noWaterFog(CallbackInfoReturnable<FogType> cir) {
        if (cir.getReturnValue() == FogType.WATER && XRayState.enabled()) cir.setReturnValue(FogType.ATMOSPHERIC);
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// While X-Ray is active, a camera submerged in water uses the regular atmospheric fog instead
// of the short-range blue water fog. Both fog color and fog distance read getFluidInCamera().
@Mixin(FogRenderer.class)
public abstract class UnderwaterFogMixin {
    @ModifyExpressionValue(method = {"setupColor", "setupFog"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private static FogType betterxray$noWaterFog(FogType original) {
        return original == FogType.WATER && XRayState.enabled() ? FogType.NONE : original;
    }
}
*///?}
