package dev.betterxray.compat.sodium;

//? if >=26.1 {
import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultFluidRenderer.class)
public abstract class SodiumFluidFacesMixin {
    @Inject(method = "isFullBlockFluidSideVisible", at = @At("HEAD"), cancellable = true)
    private void xray$visible(BlockGetter view, BlockPos pos, Direction face, FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled()) return;
        BlockState neighbor = view.getBlockState(pos.relative(face));
        if (!XRayState.selected(neighbor)) cir.setReturnValue(!neighbor.getFluidState().getType().isSame(fluid.getType()));
    }
    // Sodium 0.9.3 folded the (own, neighbor, face, height) overload into this one; both 0.9.2 and 0.9.3 have it.
    @Inject(method = "isFluidSideExposed(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Z", at = @At("HEAD"), cancellable = true)
    private void xray$exposed(BlockAndTintGetter view, BlockState own, BlockPos neighborPos, Direction face, float height,
            CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled() && !XRayState.selected(view.getBlockState(neighborPos))) cir.setReturnValue(height > 0);
    }
}
//?} else if >=1.21.11 {
/*import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultFluidRenderer.class)
public abstract class SodiumFluidFacesMixin {
    @Inject(method = "isFullBlockFluidSideVisible", at = @At("HEAD"), cancellable = true)
    private void xray$visible(BlockGetter view, BlockPos pos, Direction face, FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled()) return;
        BlockState neighbor = view.getBlockState(pos.relative(face));
        if (!XRayState.selected(neighbor)) cir.setReturnValue(!neighbor.getFluidState().getType().isSame(fluid.getType()));
    }
    @Inject(method = "isFluidSideExposed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;F)Z", at = @At("HEAD"), cancellable = true)
    private void xray$exposed(BlockState own, BlockState neighbor, Direction face, float height, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled() && !XRayState.selected(neighbor)) cir.setReturnValue(height > 0);
    }
}
*///?} else if >=1.21 {
/*import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Sodium 0.8.13 (1.21.1): fluid faces next to deselected blocks stay visible.
@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class SodiumFluidFacesMixin {
    @Inject(method = "isFullBlockFluidOccluded", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$occluded(BlockAndTintGetter world, BlockPos pos, Direction dir, BlockState own, FluidState fluid,
                               CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled()) return;
        BlockState neighbor = world.getBlockState(pos.relative(dir));
        if (!XRayState.selected(neighbor)) cir.setReturnValue(neighbor.getFluidState().getType().isSame(fluid.getType()));
    }

    @Inject(method = "isSideExposed", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$exposed(BlockAndTintGetter world, int x, int y, int z, Direction dir, float height,
                              CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled()) return;
        BlockState neighbor = world.getBlockState(new BlockPos(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ()));
        if (!XRayState.selected(neighbor)) cir.setReturnValue(height > 0);
    }
}
*///?} else {
/*import dev.betterxray.render.XRayState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Sodium 0.5.13 (1.20.1): fluid faces next to deselected blocks stay visible.
@Mixin(value = FluidRenderer.class, remap = false)
public abstract class SodiumFluidFacesMixin {
    @Inject(method = "isFluidOccluded", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$occluded(BlockAndTintGetter world, int x, int y, int z, Direction dir, Fluid fluid,
                               CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled()) return;
        BlockState neighbor = world.getBlockState(new BlockPos(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ()));
        if (!XRayState.selected(neighbor)) cir.setReturnValue(neighbor.getFluidState().getType().isSame(fluid));
    }

    @Inject(method = "isSideExposed", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$exposed(BlockAndTintGetter world, int x, int y, int z, Direction dir, float height,
                              CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled()) return;
        BlockState neighbor = world.getBlockState(new BlockPos(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ()));
        if (!XRayState.selected(neighbor)) cir.setReturnValue(height > 0);
    }
}
*///?}
