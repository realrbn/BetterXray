package dev.betterxray.mixin;

//? if >=26.1 {
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MobEspRenderMixin {
    // Entity states are consumed and cleared inside submitFeatures, before gizmo finalization.
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void betterxray$boxes(LevelRenderState state, SubmitNodeCollector collector,
                                 boolean renderOutline, CallbackInfo ci) {
        try (var ignored = ((LevelRenderer)(Object)this).collectPerFrameRenderThreadGizmos()) {
            MobEspRenderer.renderBoxes(state);
        }
    }
}
//?} else if >=1.21.11 {
/*import com.llamalad7.mixinextras.sugar.Local;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.TracerOrigin;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MobEspRenderMixin {
    @Shadow @Final private LevelRenderState levelRenderState;

    // Entity states are extracted at this point; gizmos are finalized inside addMainPass.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V"))
    private void betterxray$boxes(CallbackInfo ci, @Local(argsOnly = true) Camera camera, @Local Frustum frustum,
                                 @Local(argsOnly = true) DeltaTracker deltaTracker,
                                 @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
                                 @Local(argsOnly = true, ordinal = 1) Matrix4f projection) {
        try (var ignored = ((LevelRenderer)(Object)this).collectPerFrameGizmos()) {
            MobEspRenderer.renderBoxes(levelRenderState.entityRenderStates, camera.position(), camera.rotation(), frustum);
            // No LevelExtractor here: tracers are collected on the render thread right before drawing.
            var tracers = MobEspRenderer.collectTracers(deltaTracker);
            MobEspRenderer.renderTracers(tracers, tracers.isEmpty() ? camera.position()
                : TracerOrigin.compute(camera.position(), projection, viewRotation));
        }
    }
}
*///?}
