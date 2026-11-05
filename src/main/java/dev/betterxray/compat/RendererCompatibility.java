package dev.betterxray.compat;

import dev.betterxray.XRayClient;
import dev.betterxray.render.*;
//? if fabric && >=26.1 {
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
//?} else if fabric && >=1.21.4 {
/*import net.fabricmc.fabric.api.renderer.v1.Renderer;
*///?} else if fabric {
/*import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
*///?}
import java.lang.reflect.Method;

public final class RendererCompatibility {
    private static boolean rendererSupported;
    private static boolean irisUnavailable;
    private static Object iris;
    private static Method shaderPackInUse;
    private static int ticks;

    public static void initialize() {
        //? if !fabric {
        /*// (Neo)Forge has no FRAPI: terrain is meshed by vanilla's block renderer or by Sodium/Embeddium, both patched.
        rendererSupported = true;
        *///?} else {
        //? if >=1.21.4 {
        String renderer = Renderer.get().getClass().getName();
        //?} else {
        /*var active = RendererAccess.INSTANCE.getRenderer();
        String renderer = active == null ? "none" : active.getClass().getName();
        *///?}
        rendererSupported = renderer.equals("net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer")
            || renderer.equals("net.caffeinemc.mods.sodium.client.render.frapi.SodiumRenderer");
        //? if <1.21 {
        /*// Sodium 0.5 has no FRAPI renderer of its own (none, or Indium's); its terrain is patched directly.
        if (Platform.sodiumLoaded()) rendererSupported = true;
        *///?}
        if (!rendererSupported) XRayClient.LOGGER.warn("[XRay] Unsupported renderer {}; effect disabled", renderer);
        //?}
        if (Platform.isModLoaded("iris")) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                irisUnavailable = true;
                XRayClient.LOGGER.warn("[XRay] Cannot query Iris API; effect disabled", e);
            }
        }
        EntityCullingCompat.initialize();
        refresh();
    }
    public static void tick() { if (++ticks >= 20) { ticks = 0; refresh(); } }
    public static void refresh() {
        boolean supported = rendererSupported && !irisUnavailable;
        // An active shader pack disables the effect unless this target supports the installed Iris
        // (Fabric 26.3+, tested version range and API revision: IrisCompat.shadersSupported).
        if (supported && iris != null && !IrisCompat.shadersSupported()) {
            try { supported = !((Boolean)shaderPackInUse.invoke(iris)); }
            catch (ReflectiveOperationException e) { supported = false; irisUnavailable = true; }
        }
        if (supported != XRayState.supported()) {
            XRayState.setSupported(supported);
            XRayRenderController.requestRebuild();
            XRayClient.LOGGER.warn("[XRay] Rendering support {} (unsupported renderer or shader pack)", supported ? "restored" : "disabled");
        }
        // separateAo packs ignore the faint opacity: hide faint terrain, or (experimental) encode it
        // for the patched shaders.
        boolean separateAo = IrisCompat.faintUnsupported();
        boolean encode = separateAo && IrisCompat.opacityPatch();
        boolean hide = separateAo && !encode;
        if (hide != XRayState.faintUnsupported() || encode != XRayState.faintEncoded()) {
            XRayState.setFaintUnsupported(hide);
            XRayState.setFaintEncoded(encode);
            XRayRenderController.requestRebuild();
            XRayClient.LOGGER.info("[XRay] Faint terrain under the shader pack: {}", hide ? "hidden (separateAo)"
                : encode ? "shader opacity patch (experimental)" : "normal");
        }
    }
}
