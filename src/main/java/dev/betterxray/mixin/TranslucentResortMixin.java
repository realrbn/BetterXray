package dev.betterxray.mixin;

import dev.betterxray.render.XRayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.2 {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else if >=1.20.5 {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * No translucent resorts while the X-Ray view is on: sections built then have no sort state
 * (TranslucentSortMixin), and with every section visible vanilla would otherwise schedule a resort
 * task for an eighth of them per frame of camera movement (1.21.11+; up to 15 nearby sections
 * before), each one cancelled right away. Sections built before X-Ray was switched on keep their
 * last order until the rebuild replaces them.
 */
//? if >=1.21.2 {
@Mixin(LevelRenderer.class)
public abstract class TranslucentResortMixin {
    @Inject(method = "scheduleTranslucentSectionResort", at = @At("HEAD"), cancellable = true)
    private void xray$noResort(Vec3 cameraPos, CallbackInfo ci) {
        if (XRayState.enabled()) ci.cancel();
    }
}
//?} else if >=1.20.5 {
/*@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class TranslucentResortMixin {
    @Inject(method = "resortTransparency", at = @At("HEAD"), cancellable = true)
    private void xray$noResort(RenderType type, SectionRenderDispatcher dispatcher, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled()) cir.setReturnValue(false);
    }
}
*///?} else {
/*@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public abstract class TranslucentResortMixin {
    @Inject(method = "resortTransparency", at = @At("HEAD"), cancellable = true)
    private void xray$noResort(RenderType type, ChunkRenderDispatcher dispatcher, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled()) cir.setReturnValue(false);
    }
}
*///?}
