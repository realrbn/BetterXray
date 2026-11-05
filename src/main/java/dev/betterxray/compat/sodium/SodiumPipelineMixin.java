package dev.betterxray.compat.sodium;

// Sodium 0.9 (26.x) only: 0.8 uses the vanilla VisGraph and has no alpha cutout on its translucent pass.
//? if >=26.1 {
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
import dev.betterxray.render.XRayPipelines;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
//? if >=26.3 {
import net.minecraft.client.renderer.oit.OitStage;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderChunkRenderer.class)
public abstract class SodiumPipelineMixin {
    @Inject(method = "compileProgram", at = @At("RETURN"), cancellable = true)
    private void xray$pipeline(TerrainRenderPass pass,
        //? if >=26.3 {
        OitStage oitStage,
        //?}
        CallbackInfoReturnable<RenderPipeline> cir) {
        if (pass.isTranslucent()) cir.setReturnValue(XRayPipelines.translucent(cir.getReturnValue()));
    }
}
//?}
