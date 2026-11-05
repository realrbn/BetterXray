package dev.betterxray.mixin;

//? if >=26.1 {
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = FluidRenderer.class, priority = 1100)
public abstract class FluidMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void xray$fluid(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState block, FluidState fluid, CallbackInfo ci) {
        if (XRayState.hideFluid(fluid)) ci.cancel();
    }
    @Inject(method = "isFaceOccludedByNeighbor", at = @At("HEAD"), cancellable = true)
    private static void xray$face(Direction direction, float height, BlockState neighbor, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled() && !XRayState.selected(neighbor)) cir.setReturnValue(false);
    }
}
//?} else if >=1.21.2 {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LiquidBlockRenderer.class, priority = 1100)
public abstract class FluidMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void xray$fluid(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer, BlockState block, FluidState fluid, CallbackInfo ci) {
        if (XRayState.hideFluid(fluid)) ci.cancel();
    }
    @Inject(method = "isFaceOccludedByNeighbor", at = @At("HEAD"), cancellable = true)
    private static void xray$face(Direction direction, float height, BlockState neighbor, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled() && !XRayState.selected(neighbor)) cir.setReturnValue(false);
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LiquidBlockRenderer.class, priority = 1100)
public abstract class FluidMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void xray$fluid(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer, BlockState block, FluidState fluid, CallbackInfo ci) {
        if (XRayState.hideFluid(fluid)) ci.cancel();
    }
    @Inject(method = "isFaceOccludedByNeighbor", at = @At("HEAD"), cancellable = true)
    private static void xray$face(BlockGetter level, BlockPos pos, Direction direction, float height, BlockState neighbor, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.enabled() && !XRayState.selected(neighbor)) cir.setReturnValue(false);
    }
}
*///?}
