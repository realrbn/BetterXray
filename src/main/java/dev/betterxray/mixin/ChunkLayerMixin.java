package dev.betterxray.mixin;

//? if >=1.21.11 {
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
import dev.betterxray.render.XRayPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSectionLayer.class)
public abstract class ChunkLayerMixin {
    @Inject(method = "pipeline", at = @At("RETURN"), cancellable = true)
    private void xray$pipeline(
        //? if >=26.3 {
        boolean multiDraw,
        //?}
        CallbackInfoReturnable<RenderPipeline> cir) {
        if ((Object)this == ChunkSectionLayer.TRANSLUCENT) cir.setReturnValue(XRayPipelines.translucent(cir.getReturnValue()));
    }
}
//?}
