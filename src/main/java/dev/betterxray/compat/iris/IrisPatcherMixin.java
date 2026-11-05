package dev.betterxray.compat.iris;

// Iris 1.11 (Fabric 26.3+) only: experimental faint terrain under separateAo packs.
//? if fabric && >=26.3 {
import dev.betterxray.XRayClient;
import dev.betterxray.compat.IrisCompat;
import dev.betterxray.compat.IrisShaderPatch;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Patches the GLSL Iris generates for every Sodium terrain program while the experimental switch is
 * on. Iris caches the returned map, so the patch works on a copy and is idempotent (marker).
 * {@code -Dbetterxray.debug.dumpShaders=true} writes the patched sources to
 * {@code <game>/betterxray_shaders}.
 */
@Mixin(value = TransformPatcher.class, remap = false)
public abstract class IrisPatcherMixin {
    private static final boolean DUMP = Boolean.getBoolean("betterxray.debug.dumpShaders");

    @Inject(method = "patchSodium", at = @At("RETURN"), cancellable = true, remap = false)
    private static void xray$faintOpacity(String name, String vertex, String geometry, String tessControl, String tessEval,
                                          String fragment, net.irisshaders.iris.gl.blending.AlphaTest alpha,
                                          it.unimi.dsi.fastutil.objects.Object2ObjectMap<?, ?> textureMap, java.util.Set<String> textureOverrides,
                                          boolean shadow, CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        if (!IrisCompat.opacityPatch()) return;
        Map<PatchShaderType, String> result = cir.getReturnValue();
        if (result == null || result.get(PatchShaderType.GEOMETRY) != null || result.get(PatchShaderType.TESS_EVAL) != null) return;
        String[] patched = IrisShaderPatch.patch(result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.FRAGMENT));
        if (patched == null) {
            XRayClient.LOGGER.info("[XRay] Shader program {} left unpatched (faint opacity)", name);
            return;
        }
        Map<PatchShaderType, String> copy = new EnumMap<>(PatchShaderType.class);
        copy.putAll(result);
        copy.put(PatchShaderType.VERTEX, patched[0]);
        copy.put(PatchShaderType.FRAGMENT, patched[1]);
        cir.setReturnValue(copy);
        if (DUMP) {
            try {
                Path dir = Path.of("betterxray_shaders");
                Files.createDirectories(dir);
                String file = name.replaceAll("[^A-Za-z0-9_.-]", "_") + (shadow ? "_shadow" : "");
                Files.writeString(dir.resolve(file + ".vsh"), patched[0]);
                Files.writeString(dir.resolve(file + ".fsh"), patched[1]);
            } catch (IOException e) {
                XRayClient.LOGGER.warn("[XRay] Cannot dump shader {}", name, e);
            }
        }
    }
}
//?}
