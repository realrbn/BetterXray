package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.betterxray.render.ChunkMeshing;
import dev.betterxray.render.XRayState;
import dev.betterxray.render.XRayVertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/^*
 * Block models of a chunk section that is being meshed: deselected blocks are written with the
 * configured alpha (or skipped at 0 %), selected blocks with full light and all faces. A Cave-Find
 * block stays in its own layer (TerrainMixin): only its faces toward underground air are written.
 ^/
@Mixin(ModelBlockRenderer.class)
public abstract class ModelRendererMixin {
    @WrapMethod(method = "tesselateBlock")
    private void xray$tesselate(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack pose,
                                VertexConsumer buffer, boolean cull, RandomSource random, long seed, int overlay,
                                Operation<Void> original) {
        if (!ChunkMeshing.active() || !XRayState.enabled()) {
            original.call(level, model, state, pos, pose, buffer, cull, random, seed, overlay);
            return;
        }
        boolean selected = XRayState.selected(state);
        if (!selected && XRayState.caveBlock(level, pos)) {
            original.call(level, model, state, pos, pose, XRayVertexConsumer.cave(buffer, null, level, pos), cull,
                random, seed, overlay);
            return;
        }
        if (!selected && XRayState.hideTerrain()) return;
        original.call(level, model, state, pos, pose, new XRayVertexConsumer(buffer, selected), cull && !selected,
            random, seed, overlay);
    }
}
*///?}
