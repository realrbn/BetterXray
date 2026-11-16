package dev.betterxray.mixin;

//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.betterxray.render.XRayPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wireframe mode with vanilla's improved transparency: translucent terrain is then drawn with the
 * OIT stage pipelines (ChunkSectionsToRender.renderOit), which never pass ChunkLayerMixin.
 */
@Mixin(OitPipelineSet.class)
public abstract class OitWireframeMixin {
    @Inject(method = "getPipeline", at = @At("RETURN"), cancellable = true)
    private void xray$wireframe(OitStage stage, CallbackInfoReturnable<RenderPipeline> cir) {
        Object self = this;
        if (self == RenderPipelines.OIT_TERRAIN || self == RenderPipelines.OIT_TERRAIN_MULTIDRAW)
            cir.setReturnValue(XRayPipelines.oitTerrain(cir.getReturnValue()));
    }
}
//?}
