package dev.betterxray.compat;

import dev.betterxray.XRayClient;
//? if fabric && >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
//?}

/**
 * Shader support: the only class that calls Iris directly. Fabric 26.3+ compiles
 * against Iris' API; every other target (and a game without Iris) gets the no-op branch. Iris maps
 * render pipelines by identity to shader-pack programs and keeps the pipeline's own state (depth,
 * blending) for the override, so Better X-Ray's pipeline copies only need to be made known.
 */
public final class IrisCompat {
    //? if fabric && >=26.3 {
    private static final boolean PRESENT = Platform.isModLoaded("iris");
    /** API revision 4: assignPipeline (0.3), shadow programs and callbacks (0.4). */
    private static final int MIN_API_REVISION = 4;
    private static boolean broken;
    private static Boolean shadersSupported;

    /**
     * X-Ray stays on under an active shader pack: Iris is within the tested range of this target
     * ({@code iris_supported} in versions/&lt;mc&gt;/gradle.properties, stored in fabric.mod.json) and
     * offers API revision 4. Otherwise the old guard applies. Decided once per game start.
     */
    public static boolean shadersSupported() {
        if (shadersSupported == null) shadersSupported = evaluateGate();
        return shadersSupported && !broken;
    }

    private static boolean evaluateGate() {
        if (!PRESENT) return false;
        String version = Platform.modVersion("iris");
        String range = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("betterxray")
            .map(mod -> mod.getMetadata().getCustomValue("betterxray:iris_supported"))
            .map(value -> value.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING ? value.getAsString() : "")
            .orElse("");
        try {
            int api = IrisApi.getInstance().getMinorApiRevision();
            boolean inRange = !range.isBlank() && net.fabricmc.loader.api.metadata.version.VersionPredicate.parse(range)
                .test(net.fabricmc.loader.api.Version.parse(version));
            boolean supported = inRange && api >= MIN_API_REVISION;
            if (supported) XRayClient.LOGGER.info("[XRay] Iris {} (API 0.{}): shader packs supported", version, api);
            else XRayClient.LOGGER.warn("[XRay] Iris {} (API 0.{}) is outside the tested range '{}' (API >= 0.{}); "
                + "the effect is disabled while a shader pack is active", version, api, range, MIN_API_REVISION);
            return supported;
        } catch (Exception | LinkageError e) {
            XRayClient.LOGGER.warn("[XRay] Cannot check the Iris version; the effect is disabled while a shader pack is active", e);
            return false;
        }
    }

    /** A faint block-entity copy: drawn with the pack's translucent block-entity program. */
    public static void assignFaintModel(Object pipeline) {
        if (!PRESENT || broken) return;
        try {
            IrisApi.getInstance().assignPipeline((RenderPipeline) pipeline, IrisProgram.BLOCK_TRANSLUCENT);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            XRayClient.LOGGER.warn("[XRay] Cannot register a pipeline with Iris; faint block entities may be missing under shaders", e);
        }
    }

    /**
     * The active pack cannot show faint terrain: with {@code separateAo=true} Iris stores ambient
     * occlusion in the vertex alpha and packs such as Complementary and BSL ignore it as opacity, so
     * faint quads would be opaque. Reads Iris internals; unknown means false.
     */
    public static boolean faintUnsupported() {
        if (!PRESENT || broken) return false;
        try {
            return IrisApi.getInstance().isShaderPackInUse()
                && net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            XRayClient.LOGGER.warn("[XRay] Cannot read the shader pack's separateAo setting", e);
            return false;
        }
    }

    /** True while Iris renders its shadow map; faint geometry must not cast shadows. */
    public static boolean shadowPass() {
        if (!PRESENT || broken) return false;
        try { return IrisApi.getInstance().isRenderingShadowPass(); }
        catch (LinkageError e) { broken = true; return false; }
    }

    /**
     * Experimental: Sodium terrain programs are patched (iris/IrisPatcherMixin)
     * so faint quads can carry their opacity past {@code separateAo}. Only the config switch; the
     * shaders follow it after {@link #reloadShaders}.
     */
    /** Iris is installed on a target with shader support (Fabric 26.3+). */
    public static boolean available() { return PRESENT; }

    public static boolean opacityPatch() {
        return PRESENT && !broken && dev.betterxray.config.XRayConfigManager.get().shaderOpacityPatch;
    }

    // Raw mc_Entity bits of the quad Sodium is currently encoding on this meshing thread
    // (set around BlockRenderer.bufferQuad, read by iris/IrisVertexMixin).
    private static final ThreadLocal<int[]> FAINT_BITS = ThreadLocal.withInitial(() -> new int[1]);
    public static void beginFaintQuad(float opacity) { if (PRESENT) FAINT_BITS.get()[0] = IrisShaderPatch.faintBits(opacity); }
    public static void endQuad() { if (PRESENT) FAINT_BITS.get()[0] = 0; }
    public static int faintBits() { return !PRESENT ? 0 : FAINT_BITS.get()[0]; }

    /** Recompiles the shader pack (after the experimental switch changed). */
    public static void reloadShaders() {
        if (!PRESENT || broken) return;
        try {
            if (IrisApi.getInstance().isShaderPackInUse()) net.irisshaders.iris.Iris.reload();
        } catch (Exception | LinkageError e) {
            XRayClient.LOGGER.warn("[XRay] Cannot reload the shader pack; reload it manually (Iris: R)", e);
        }
    }
    //?} else {
    /*public static void assignFaintModel(Object pipeline) {}
    public static boolean shadersSupported() { return false; }
    public static boolean available() { return false; }
    public static boolean faintUnsupported() { return false; }
    public static boolean shadowPass() { return false; }
    public static boolean opacityPatch() { return false; }
    public static void beginFaintQuad(float opacity) {}
    public static void endQuad() {}
    public static int faintBits() { return 0; }
    public static void reloadShaders() {}
    *///?}

    private IrisCompat() {}
}
