package dev.betterxray.mixin;

//? if >=26.1 {
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class FullbrightLightmapMixin {
    @Shadow private boolean needsUpdate;
    @Unique private boolean betterxray$wasFullbright;

    @Inject(method = "extract", at = @At("HEAD"))
    private void betterxray$refreshAfterToggle(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        boolean fullbright = XRayConfigManager.get().fullbright;
        if (fullbright != betterxray$wasFullbright) needsUpdate = true;
        betterxray$wasFullbright = fullbright;
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void betterxray$applyFullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (!XRayConfigManager.get().fullbright || state.blockLightTint == null) return;
        state.needsUpdate = true;
        state.ambientColor = LightmapRenderStateExtractor.WHITE;
        state.darknessEffectScale = 0.0f;
        state.bossOverlayWorldDarkening = 0.0f;
    }
}
//?} else if >=1.21.5 {
/*import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

// The lightmap shader returns its ambient color (white) at ambient light factor 1.
@Mixin(LightTexture.class)
public abstract class FullbrightLightmapMixin {
    @Shadow private boolean updateLightTexture;
    @Unique private boolean betterxray$wasFullbright;

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void betterxray$refreshAfterToggle(float partialTick, CallbackInfo ci) {
        boolean fullbright = Minecraft.getInstance().level != null && XRayConfigManager.get().fullbright;
        if (fullbright != betterxray$wasFullbright) updateLightTexture = true;
        betterxray$wasFullbright = fullbright;
    }

    // WrapOperation, not @Redirect: chains with other mods that redirect the same call.
    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"))
    private float betterxray$ambient(DimensionType type, Operation<Float> original) {
        return betterxray$wasFullbright ? 1.0f : original.call(type);
    }
}
*///?} else {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(LightTexture.class)
public abstract class FullbrightLightmapMixin {
    @Shadow private boolean updateLightTexture;
    @Unique private boolean betterxray$wasFullbright;

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void betterxray$refreshAfterToggle(float partialTick, CallbackInfo ci) {
        boolean fullbright = Minecraft.getInstance().level != null && XRayConfigManager.get().fullbright;
        if (fullbright != betterxray$wasFullbright) updateLightTexture = true;
        betterxray$wasFullbright = fullbright;
    }

    // WrapOperation, not @Redirect: Create Deep Seas redirects the same call (require = 1), and two
    // redirects crash at startup. Fullbright writes white directly so the other mod cannot recolor it.
    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void betterxray$light(NativeImage image, int x, int y, int color, Operation<Void> original) {
        if (betterxray$wasFullbright) image.setPixelRGBA(x, y, -1);
        else original.call(image, x, y, color);
    }
}
*///?}
