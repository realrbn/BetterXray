package dev.betterxray.mixin;

//? if >=26.1 {
import dev.betterxray.render.HighlightCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Unique;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?}

@Mixin(LevelRenderer.class)
public abstract class HighlightRenderMixin {
    @Inject(method = "submitFeatures", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void betterxray$highlight(LevelRenderState state, SubmitNodeCollector collector,
                                      boolean renderOutline, CallbackInfo ci) {
        try (var ignored = ((LevelRenderer)(Object)this).collectPerFrameRenderThreadGizmos()) {
            HighlightCache.render(state.cameraRenderState);
            dev.betterxray.render.StructureFinder.render(state.cameraRenderState);
        }
    }

    //? if >=26.3 {
    @Unique private GpuBufferSlice betterxray$terrainFog;

    /**
     * Highlight, ESP and structure boxes are always-on-top gizmos. Their pass ran with the terrain
     * fog bound, so lines far from the camera (large structures) faded into the fog color.
     */
    @Inject(method = "executeAlwaysOnTop", at = @At("HEAD"))
    private void betterxray$noFogOnTop(CallbackInfo ci) {
        betterxray$terrainFog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(((GameRendererAccessor)Minecraft.getInstance().gameRenderer).xray$fogRenderer()
            .getBuffer(FogRenderer.FogMode.NONE));
    }

    @Inject(method = "executeAlwaysOnTop", at = @At("RETURN"))
    private void betterxray$restoreFog(CallbackInfo ci) {
        if (betterxray$terrainFog != null) RenderSystem.setShaderFog(betterxray$terrainFog);
        betterxray$terrainFog = null;
    }
    //?} else {
    /*/^*
     * Highlight, ESP and structure boxes are always-on-top gizmos. Their pass binds the fog it
     * receives, so lines far from the camera (large structures) faded into the fog color.
     ^/
    @ModifyVariable(method = "addAlwaysOnTopPass", at = @At("HEAD"), argsOnly = true)
    private GpuBufferSlice betterxray$noFogOnTop(GpuBufferSlice fog) {
        return ((GameRendererAccessor)Minecraft.getInstance().gameRenderer).xray$fogRenderer().getBuffer(FogRenderer.FogMode.NONE);
    }
    *///?}
}
//?} else if >=1.21.11 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.betterxray.render.HighlightCache;
import dev.betterxray.render.TracerOrigin;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class HighlightRenderMixin {
    /^*
     * Highlight, ESP and structure boxes are always-on-top gizmos, drawn in the late debug pass with
     * the terrain fog it receives, so lines far from the camera faded into the fog color.
     ^/
    @ModifyVariable(method = "addLateDebugPass", at = @At("HEAD"), argsOnly = true)
    private GpuBufferSlice betterxray$noFogOnTop(GpuBufferSlice fog) {
        return ((GameRendererAccessor)Minecraft.getInstance().gameRenderer).xray$fogRenderer().getBuffer(FogRenderer.FogMode.NONE);
    }

    // Entity states are extracted at this point; gizmos are finalized inside addMainPass.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V"))
    private void betterxray$highlight(CallbackInfo ci, @Local(argsOnly = true) Camera camera,
                                      @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
                                      @Local(argsOnly = true, ordinal = 1) Matrix4f projection,
                                      @Local Frustum frustum) {
        try (var ignored = ((LevelRenderer)(Object)this).collectPerFrameGizmos()) {
            var tracerStart = TracerOrigin.compute(camera.position(), projection, viewRotation);
            HighlightCache.render(camera.position(), frustum, tracerStart);
            dev.betterxray.render.StructureFinder.render(frustum, tracerStart);
        }
    }
}
*///?}
