package dev.betterxray.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.render.XRayState;
//? if >=1.20.5 {
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
//?} else {
/*import com.mojang.blaze3d.vertex.BufferBuilder;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla meshing (Indigo, NeoForge, Forge): while the X-Ray view is on, nearly all terrain is in
 * the translucent layer. Its quads are not sorted then: sorting decodes every quad centroid, keeps
 * them per section and re-sorts nearby sections on camera movement, but changes nothing visible,
 * because the translucent layer writes no depth while X-Ray is on and the faint terrain is a few
 * percent opaque. Without a sort state the layer is drawn with the sequential index buffer and is
 * never resorted (see TranslucentResortMixin); the rebuild when X-Ray is switched off sorts again.
 * NeoForge adds a compile overload with additional section renderers; the old one only delegates
 * to it, and a bare name matching both selects nothing, so NeoForge needs the full descriptor.
 */
//? if >=1.20.5 {
@Mixin(net.minecraft.client.renderer.chunk.SectionCompiler.class)
public abstract class TranslucentSortMixin {
    //? if neoforge && >=1.21.11 {
    /*private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";
    *///?} else if neoforge {
    /*private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";
    *///?} else
    private static final String COMPILE = "compile";

    @WrapOperation(method = COMPILE, at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/MeshData;sortQuads(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)Lcom/mojang/blaze3d/vertex/MeshData$SortState;"))
    private MeshData.SortState xray$unsorted(MeshData mesh, ByteBufferBuilder indices, VertexSorting sorting,
                                             Operation<MeshData.SortState> original) {
        return XRayState.enabled() ? null : original.call(mesh, indices, sorting);
    }
}
//?} else {
/*@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class TranslucentSortMixin {
    // 1.20.1 sorts only when the translucent buffer is not empty; one decision skips both the sort
    // and the stored sort state, so they cannot disagree when X-Ray is switched during a build.
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;isCurrentBatchEmpty()Z"))
    private boolean xray$unsorted(BufferBuilder builder, Operation<Boolean> original) {
        return XRayState.enabled() || original.call(builder);
    }
}
*///?}
