package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.betterxray.render.ChunkMeshing;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Chunk section meshing (worker threads). Deselected blocks go into the translucent layer, Cave-Find
 * blocks (bordering underground air) keep theirs; the vertex changes happen in
 * {@link ModelRendererMixin} while {@link ChunkMeshing} is active.
 * Fabric's Indigo redirects the block render call itself, so this only wraps around it.
 ^/
//? if >=1.20.5 {
@Mixin(net.minecraft.client.renderer.chunk.SectionCompiler.class)
//?} else {
/^@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
^///?}
public abstract class TerrainMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void xray$begin(CallbackInfoReturnable<?> cir) { ChunkMeshing.begin(); }

    @Inject(method = "compile", at = @At("RETURN"))
    private void xray$end(CallbackInfoReturnable<?> cir) { ChunkMeshing.end(); }

    @ModifyExpressionValue(method = "compile", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getChunkRenderType(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType xray$layer(RenderType original, @com.llamalad7.mixinextras.sugar.Local BlockState state,
                                  @com.llamalad7.mixinextras.sugar.Local(ordinal = 2) net.minecraft.core.BlockPos pos,
                                  @com.llamalad7.mixinextras.sugar.Local net.minecraft.client.renderer.chunk.RenderChunkRegion region) {
        if (!XRayState.enabled() || XRayState.selected(state) || XRayState.caveBlock(region, pos)) return original;
        return RenderType.translucent();
    }
}
*///?}
