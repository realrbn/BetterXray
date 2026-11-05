package dev.betterxray.compat.sodium;

//? if <1.21 {
/*import dev.betterxray.compat.SodiumTranslucency;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sodium 0.5.13 (1.20.1): every translucent-pass quad (blocks, fluids, other mods' emitters)
// passes through here and gets real vertex alpha, matching the patched shader.
@Mixin(value = ChunkMeshBufferBuilder.class, remap = false)
public abstract class SodiumVertexMixin {
    @Inject(method = "push", at = @At("HEAD"), remap = false)
    private void xray$alpha(ChunkVertexEncoder.Vertex[] vertices, Material material, CallbackInfo ci) {
        if (material.pass != DefaultTerrainRenderPasses.TRANSLUCENT) return;
        float opacity = SodiumTranslucency.opacity();
        //? if forge {
        /^// Embeddium (Forge) keeps real alpha in the vertex colour: only the opacity is applied.
        for (ChunkVertexEncoder.Vertex vertex : vertices)
            vertex.color = (vertex.color & 0x00ffffff) | Math.round((vertex.color >>> 24) * opacity) << 24;
        ^///?} else
        for (ChunkVertexEncoder.Vertex vertex : vertices) vertex.color = SodiumTranslucency.convert(vertex.color, opacity);
    }
}
*///?}
