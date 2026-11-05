package dev.betterxray.compat.sodium;

//? if >=1.21.11 {
import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlockRenderContext.class)
public abstract class SodiumCullMixin {
    @Shadow protected BlockState state;
    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true)
    private void xray$faces(Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (state != null && XRayState.selected(state)) cir.setReturnValue(false);
    }
}
//?} else if >=1.21 {
/*import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public abstract class SodiumCullMixin {
    @Shadow(remap = false) protected BlockState state;

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$faces(Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (state != null && XRayState.selected(state)) cir.setReturnValue(false);
    }
}
*///?} else {
/*import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import dev.betterxray.render.XRayState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Sodium 0.5.13 (1.20.1): faces of deselected blocks next to deselected neighbours stay culled;
// SodiumBlockMixin already keeps every face of selected blocks.
@Mixin(value = BlockOcclusionCache.class, remap = false)
public abstract class SodiumCullMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$faces(BlockState self, BlockGetter view, BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.selected(self)) cir.setReturnValue(true);
    }
}
*///?}
