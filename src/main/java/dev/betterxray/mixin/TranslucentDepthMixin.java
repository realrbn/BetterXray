package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* Faint translucent terrain must not hide farther geometry: no depth writes while X-Ray is on. ^/
@Mixin(RenderStateShard.class)
public abstract class TranslucentDepthMixin {
    @Inject(method = "setupRenderState", at = @At("RETURN"))
    private void xray$noDepthWrite(CallbackInfo ci) {
        if (XRayState.enabled() && (Object)this == RenderType.translucent()) RenderSystem.depthMask(false);
    }

    @Inject(method = "clearRenderState", at = @At("RETURN"))
    private void xray$restore(CallbackInfo ci) {
        if ((Object)this == RenderType.translucent()) RenderSystem.depthMask(true);
    }
}
*///?}
