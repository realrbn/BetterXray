package dev.betterxray.mixin;

//? if neoforge && >=26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Vanilla block meshing on NeoForge 26.x: selected blocks emit their culled faces (enclosed ores
 * stay complete) and skip ambient occlusion, which would darken them inside solid rock.
 ^/
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @ModifyExpressionValue(method = "tesselateBlock",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;ambientOcclusion:Z"))
    private boolean xray$ambientOcclusion(boolean original, @Local(argsOnly = true) BlockState state) {
        return original && !XRayState.selected(state);
    }

    @Inject(method = "shouldRenderFace(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"), cancellable = true)
    private void xray$faces(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction direction,
            BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.selected(state)) cir.setReturnValue(true);
    }
}
*///?}
