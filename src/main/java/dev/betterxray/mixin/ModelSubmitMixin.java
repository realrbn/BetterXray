package dev.betterxray.mixin;

//? if >=1.21.11 {
import dev.betterxray.compat.IrisCompat;
import dev.betterxray.compat.Mc;import dev.betterxray.render.*;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class ModelSubmitMixin {
    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true)
    private void xray$hide(CallbackInfo ci) {
        // Faint models cast no shadows under a shader pack (Iris shadow pass).
        if (BlockEntityContext.faint() && (XRayState.hideTerrain() || IrisCompat.shadowPass())) ci.cancel();
    }
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true)
    private RenderType xray$type(RenderType type) { return BlockEntityContext.faint() ? XRayPipelines.faintModel(type) : type; }
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int xray$color(int color) { return BlockEntityContext.faint() ? XRayState.alpha(color) : color; }
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int xray$light(int light) { return BlockEntityContext.selected() ? Mc.FULL_BRIGHT : light; }
}
//?}
