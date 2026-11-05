package dev.betterxray.compat.iris;

// Iris 1.11 (Fabric 26.3+) only: shader-pack uniforms for Fullbright and underwater X-Ray.
//? if fabric && >=26.3 {
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayState;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CommonUniforms.class, remap = false)
public abstract class IrisUniformsMixin {
    /**
     * Shader packs ignore the lightmap texture that Fullbright raises, but all tested packs brighten
     * their lighting by the {@code nightVision} uniform. While Fullbright is on it reads 1.
     */
    @Inject(method = "getNightVision", at = @At("RETURN"), cancellable = true, remap = false)
    private static void xray$fullbright(CallbackInfoReturnable<Float> cir) {
        if (XRayConfigManager.get().fullbright) cir.setReturnValue(1f);
    }

    /**
     * Packs draw their own underwater fog from {@code isEyeInWater} (1 = water), read straight from
     * the camera. Like the vanilla water fog (UnderwaterFogMixin) it is off while X-Ray is active.
     */
    @Inject(method = "isEyeInWater", at = @At("RETURN"), cancellable = true, remap = false)
    private static void xray$noWaterFog(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() == 1 && XRayState.enabled()) cir.setReturnValue(0);
    }
}
//?}
