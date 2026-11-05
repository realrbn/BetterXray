package dev.betterxray.mixin;

import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the blue underwater screen texture while X-Ray is active. */
@Mixin(ScreenEffectRenderer.class)
public abstract class UnderwaterOverlayMixin {
    //? if >=26.1 {
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    *///?}
    private static void betterxray$noWaterOverlay(CallbackInfo ci) {
        if (XRayState.enabled()) ci.cancel();
    }
}
