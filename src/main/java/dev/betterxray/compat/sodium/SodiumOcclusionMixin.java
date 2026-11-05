package dev.betterxray.compat.sodium;

// Sodium 0.9 (26.x) only: 0.8 uses the vanilla VisGraph and has no alpha cutout on its translucent pass.
//? if >=26.1 {
import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectionalVisGraph.class)
public abstract class SodiumOcclusionMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void xray$openSections(int x, int y, int z, CallbackInfo ci) { if (XRayState.enabled()) ci.cancel(); }
}
//?}
