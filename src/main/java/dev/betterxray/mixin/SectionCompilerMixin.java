package dev.betterxray.mixin;

//? if neoforge && >=26.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.betterxray.compat.Mc;
import dev.betterxray.render.XRayState;
import java.util.Map;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * NeoForge meshes chunk sections with the vanilla ModelBlockRenderer (Fabric uses Indigo, see
 * IndigoMixin). Every block's quads pass through a wrapped output after tint and shading:
 * selected quads get full light and opaque colors, deselected quads go into the translucent layer
 * with the configured alpha (or are dropped at 0 %). AO and face culling of selected blocks are
 * handled in ModelBlockRendererMixin. NeoForge's four-argument compile overload only delegates here.
 ^/
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Shadow protected abstract BufferBuilder getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> startedLayers,
        SectionBufferBuilderPack buffers, ChunkSectionLayer layer);

    @WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void xray$tesselate(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
            Operation<Void> original, @Local Map<ChunkSectionLayer, BufferBuilder> startedLayers,
            @Local(argsOnly = true) SectionBufferBuilderPack builders) {
        if (!XRayState.enabled()) {
            original.call(renderer, output, x, y, z, level, pos, state, model, seed);
        } else if (XRayState.selected(state)) {
            original.call(renderer, (BlockQuadOutput)(qx, qy, qz, quad, instance) -> {
                for (int i = 0; i < 4; i++) instance.setColor(i, instance.getColor(i) | 0xff000000);
                instance.setLightCoords(Mc.FULL_BRIGHT);
                output.put(qx, qy, qz, quad, instance);
            }, x, y, z, level, pos, state, model, seed);
        } else if (XRayState.caveFind() || !XRayState.hideTerrain()) {
            boolean hide = XRayState.hideTerrain();
            BlockQuadOutput faint = (qx, qy, qz, quad, instance) -> {
                if (XRayState.caveFace(level, pos, quad.direction())) {
                    // Cave-Find: walls of underground air keep their layer, opaque and fully lit
                    // (the AO shade in the vertex colors stays).
                    for (int i = 0; i < 4; i++) instance.setColor(i, instance.getColor(i) | 0xff000000);
                    instance.setLightCoords(Mc.FULL_BRIGHT);
                    output.put(qx, qy, qz, quad, instance);
                    return;
                }
                if (hide) return;
                for (int i = 0; i < 4; i++) instance.setColor(i, XRayState.alpha(instance.getColor(i)));
                getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.TRANSLUCENT).putBlockBakedQuad(qx, qy, qz, quad, instance);
            };
            original.call(renderer, faint, x, y, z, level, pos, state, model, seed);
        }
    }
}
*///?} else if neoforge && >=1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.betterxray.render.XRayState;
import dev.betterxray.render.XRayVertexConsumer;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * NeoForge meshes chunk sections with the vanilla block renderer (Fabric uses Indigo, see
 * IndigoMixin). Each block's buffer lookup is wrapped: selected blocks keep their layers, get full
 * light and all faces; deselected blocks are written into the translucent layer with the configured
 * alpha, or skipped at 0 %. NeoForge's four-argument compile overload only delegates here.
 ^/
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/function/Function;ZLjava/util/List;)V"))
    private void xray$renderBatched(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
                                    PoseStack pose, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull,
                                    List<BlockModelPart> parts, Operation<Void> original) {
        if (!XRayState.enabled()) {
            original.call(dispatcher, state, pos, level, pose, buffers, cull, parts);
            return;
        }
        boolean selected = XRayState.selected(state);
        if (!selected && XRayState.caveFind()) {
            // Cave-Find: quads toward underground air stay in their layer, the rest goes faint (or is dropped).
            boolean hide = XRayState.hideTerrain();
            Function<ChunkSectionLayer, VertexConsumer> cave = layer -> XRayVertexConsumer.cave(buffers.apply(layer),
                hide ? null : buffers.apply(ChunkSectionLayer.TRANSLUCENT), level, pos);
            original.call(dispatcher, state, pos, level, pose, cave, cull, parts);
            return;
        }
        if (!selected && XRayState.hideTerrain()) return;
        Function<ChunkSectionLayer, VertexConsumer> xray = selected
            ? layer -> new XRayVertexConsumer(buffers.apply(layer), true)
            : layer -> new XRayVertexConsumer(buffers.apply(ChunkSectionLayer.TRANSLUCENT), false);
        original.call(dispatcher, state, pos, level, pose, xray, cull && !selected, parts);
    }
}
*///?} else if !fabric {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.betterxray.render.XRayState;
import dev.betterxray.render.XRayVertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
//?} else {
/^import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
^///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * NeoForge 1.21.1 and Forge mesh every MODEL block once per render type of its model with the vanilla
 * block renderer. Deselected blocks get the translucent layer only and are written with the
 * configured alpha (or skipped at 0 %); selected blocks keep their layers, get full light and all
 * faces. Cave-Find blocks (bordering underground air) keep their layers and write only the faces
 * toward that air. NeoForge's four-argument compile overload only delegates here.
 ^/
//? if >=1.20.5 {
@Mixin(net.minecraft.client.renderer.chunk.SectionCompiler.class)
//?} else
/^@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")^/
public abstract class SectionCompilerMixin {
    //? if neoforge {
    private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";
    //?} else
    /^private static final String COMPILE = "compile";^/

    // getRenderTypes and this renderBatched overload are (Neo)Forge methods: never remapped.
    //? if neoforge {
    private static final String RENDER_TYPES = "Lnet/minecraft/client/resources/model/BakedModel;getRenderTypes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;)Lnet/neoforged/neoforge/client/ChunkRenderTypeSet;";
    private static final String RENDER_BATCHED = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";
    //?} else {
    /^private static final String RENDER_TYPES = "Lnet/minecraft/client/resources/model/BakedModel;getRenderTypes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;)Lnet/minecraftforge/client/ChunkRenderTypeSet;";
    private static final String RENDER_BATCHED = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";
    ^///?}

    @WrapOperation(method = COMPILE, at = @At(value = "INVOKE", target = RENDER_TYPES, remap = false))
    private ChunkRenderTypeSet xray$layers(BakedModel model, BlockState state, RandomSource random, ModelData data,
                                           Operation<ChunkRenderTypeSet> original,
                                           @Local RenderChunkRegion region, @Local(ordinal = 2) BlockPos pos) {
        if (!XRayState.enabled() || XRayState.selected(state) || XRayState.caveBlock(region, pos))
            return original.call(model, state, random, data);
        return ChunkRenderTypeSet.of(RenderType.translucent());
    }

    @WrapOperation(method = COMPILE, at = @At(value = "INVOKE", target = RENDER_BATCHED, remap = false))
    private void xray$render(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
                             PoseStack pose, VertexConsumer buffer, boolean cull, RandomSource random, ModelData data,
                             RenderType layer, Operation<Void> original) {
        if (!XRayState.enabled()) {
            original.call(dispatcher, state, pos, level, pose, buffer, cull, random, data, layer);
            return;
        }
        boolean selected = XRayState.selected(state);
        if (!selected && XRayState.caveBlock(level, pos)) {
            original.call(dispatcher, state, pos, level, pose, XRayVertexConsumer.cave(buffer, null, level, pos), cull,
                random, data, layer);
            return;
        }
        if (!selected && XRayState.hideTerrain()) return;
        VertexConsumer xray = new XRayVertexConsumer(buffer, selected);
        if (selected) {
            original.call(dispatcher, state, pos, level, pose, xray, false, random, data, layer);
            return;
        }
        // The translucent buffer receives the quads of every layer of the model: models that filter
        // their quads by layer still get their own layers as arguments.
        BakedModel model = dispatcher.getBlockModel(state);
        random.setSeed(state.getSeed(pos));
        for (RenderType own : model.getRenderTypes(state, random, data))
            original.call(dispatcher, state, pos, level, pose, xray, cull, random, data, own);
    }
}
*///?}
