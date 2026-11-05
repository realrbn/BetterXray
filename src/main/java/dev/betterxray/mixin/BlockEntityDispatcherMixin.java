package dev.betterxray.mixin;

//? if >=1.21.11 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.betterxray.render.BlockEntityContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityDispatcherMixin {
    @WrapMethod(method = "submit")
    private void xray$scope(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        var previous = BlockEntityContext.CURRENT.get();
        BlockEntityContext.CURRENT.set(((BlockEntityStateAccessor)state).xray$state());
        try { original.call(state, pose, collector, camera); }
        finally {
            if (previous == null) BlockEntityContext.CURRENT.remove();
            else BlockEntityContext.CURRENT.set(previous);
        }
    }
}
//?}
