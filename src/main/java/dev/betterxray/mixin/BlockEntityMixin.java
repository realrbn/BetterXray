package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import dev.betterxray.compat.Mc;
import dev.betterxray.render.BlockEntityBuffers;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/^* Block entities: selected ones full bright, deselected ones faint (or hidden at 0 %). ^/
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityMixin {
    @Redirect(method = "setupAndRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static void xray$render(BlockEntityRenderer<BlockEntity> renderer, BlockEntity block, float delta, PoseStack pose,
                                    MultiBufferSource buffers, int light, int overlay) {
        if (XRayState.enabled()) {
            if (XRayState.selected(block.getBlockState())) light = Mc.FULL_BRIGHT;
            else if (XRayState.hideTerrain()) return;
            else buffers = BlockEntityBuffers.faint(buffers);
        }
        renderer.render(block, delta, pose, buffers, light, overlay);
    }
}
*///?}
