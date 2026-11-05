package dev.betterxray.compat.sodium;

//? if >=1.21 {
import dev.betterxray.render.XRayState;
//? if neoforge {
/*import net.caffeinemc.mods.sodium.neoforge.render.FluidRendererImpl;
*///?} else {
import net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl;
//?}
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sodium's loader-specific fluid entrypoint (also covers waterlogged blocks and custom fluid renderers).
@Mixin(FluidRendererImpl.class)
public abstract class SodiumFluidMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void xray$fluid(LevelSlice level, BlockState block, FluidState fluid, BlockPos pos, BlockPos offset,
            TranslucentGeometryCollector collector, ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (XRayState.hideFluid(fluid)) ci.cancel();
    }
}
//?} else {
/*import dev.betterxray.render.XRayState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FluidRenderer.class, remap = false)
public abstract class SodiumFluidMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$fluid(WorldSlice world, FluidState fluid, BlockPos pos, BlockPos offset, ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (XRayState.hideFluid(fluid)) ci.cancel();
    }
}
*///?}
