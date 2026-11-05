package dev.betterxray.mixin;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public abstract class VisGraphMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void xray$openSections(BlockPos pos, CallbackInfo ci) { if (XRayState.enabled()) ci.cancel(); }
}
