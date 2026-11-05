package dev.betterxray.compat.iris;

// Iris 1.11 (Fabric 26.3+) only: experimental faint terrain under separateAo packs.
//? if fabric && >=26.3 {
import dev.betterxray.compat.IrisCompat;
import net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension;
import net.irisshaders.iris.vertices.sodium.terrain.XHFPTerrainVertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the faint flag and opacity of the current quad to Iris' raw {@code mc_Entity} value. */
@Mixin(value = XHFPTerrainVertex.class, remap = false)
public abstract class IrisVertexMixin {
    @Inject(method = "packBlockId", at = @At("RETURN"), cancellable = true, remap = false)
    private void xray$faintBits(ChunkVertexExtension vertex, CallbackInfoReturnable<Integer> cir) {
        int bits = IrisCompat.faintBits();
        if (bits != 0) cir.setReturnValue(cir.getReturnValueI() | bits);
    }
}
//?}
