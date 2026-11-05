package dev.betterxray.compat.sodium;

// While the X-Ray view is on, nearly all terrain sits in the translucent pass. Sorting it costs a lot
// (Sodium 0.8/0.9: a BSP tree per section at meshing time, kept in memory and re-sorted when the
// camera moves) and changes nothing visible: the patched translucent pass writes no depth and the
// faint terrain is a few percent opaque. Sections built while X-Ray is on are therefore unsorted;
// the rebuild when X-Ray is switched off restores the normal sort.
// Sodium 0.5.13 (Fabric 1.20.1) has no translucency sorting and gets no mixin.
//? if >=1.21 {
import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Same private method in 0.8.13, 0.8.14, 0.9.2 and 0.9.3; NONE stores the quads in any order (AnyOrderData).
@Mixin(value = TranslucentGeometryCollector.class, remap = false)
public abstract class SodiumSortMixin {
    @Inject(method = "sortTypeHeuristic", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$unsorted(CallbackInfoReturnable<SortType> cir) {
        if (XRayState.enabled()) cir.setReturnValue(SortType.NONE);
    }
}
//?} else if forge {
/*import dev.betterxray.render.XRayState;
import org.embeddedt.embeddium.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Embeddium 0.3.31 (Forge 1.20.1) sorts translucent quads by their centers. A NONE state skips the
// sort at build time and never needs a resort; clear() resets the per-thread analyzer as the
// original method does before it returns.
@Mixin(value = TranslucentQuadAnalyzer.class, remap = false)
public abstract class SodiumSortMixin {
    @Shadow(remap = false) public abstract void clear();

    @Inject(method = "getSortState", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$unsorted(CallbackInfoReturnable<TranslucentQuadAnalyzer.SortState> cir) {
        if (!XRayState.enabled()) return;
        clear();
        cir.setReturnValue(TranslucentQuadAnalyzer.SortState.NONE);
    }
}
*///?}
