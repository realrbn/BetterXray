package dev.betterxray.mixin;

//? if fabric && >=1.21.11 && <26.1 {
/*import dev.betterxray.render.XRayState;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Selected blocks emit their culled faces too, so ores enclosed in stone are complete.
@Mixin(value = BlockRenderInfo.class, remap = false)
public abstract class IndigoCullMixin {
    @Shadow(remap = false) public BlockState blockState;

    @Inject(method = "shouldCullSide", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$faces(@Nullable Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (blockState != null && XRayState.selected(blockState)) cir.setReturnValue(false);
    }
}
*///?}
