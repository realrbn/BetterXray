package dev.betterxray.render;

//? if <1.21.11 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.betterxray.compat.Mc;
//? if fabric {
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
//?} else if neoforge {
/^import net.neoforged.neoforge.client.event.RegisterShadersEvent;
^///?} else {
/^import net.minecraftforge.client.event.RegisterShadersEvent;
^///?}
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import java.util.IdentityHashMap;
import java.util.Map;

/^*
 * Faint block-entity models (chests, signs, ...) for the pre-1.21.11 renderers: the original
 * render type's textures and state, plus blending, no depth writes and the vanilla translucent
 * entity shader with a lower discard threshold (betterxray:faint_model), so even very faint
 * vertex alpha set by {@link XRayVertexConsumer} stays visible.
 ^/
public final class BlockEntityBuffers {
    private static final Map<RenderType, RenderType> TYPES = new IdentityHashMap<>();
    private static ShaderInstance faintShader;
    private BlockEntityBuffers() {}

    //? if fabric {
    public static void registerShader() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(shaderId(),
            DefaultVertexFormat.NEW_ENTITY, shader -> faintShader = shader));
    }
    //?} else {
    /^/// Mod bus listener (NeoForge).
    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), shaderId(), DefaultVertexFormat.NEW_ENTITY),
                shader -> faintShader = shader);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Could not load betterxray:faint_model", e);
        }
    }
    ^///?}

    /^* The JSON core shader of 1.20.1 has its own format (faint_model_legacy.json). ^/
    private static net.minecraft.resources.Identifier shaderId() {
        //? if >=1.21 {
        return Mc.id("betterxray", "faint_model");
        //?} else
        /^return Mc.id("betterxray", "faint_model_legacy");^/
    }

    private static ShaderInstance shader() {
        return faintShader != null ? faintShader : GameRenderer.getRendertypeEntityTranslucentShader();
    }

    public static MultiBufferSource faint(MultiBufferSource original) {
        return type -> {
            // Text, beams and specialized third-party formats keep their own pipelines.
            if (type.format() != DefaultVertexFormat.NEW_ENTITY) return original.getBuffer(type);
            RenderType faint;
            synchronized (TYPES) {
                faint = TYPES.computeIfAbsent(type, source -> new RenderType("betterxray_faint_model",
                    source.format(), source.mode(), source.bufferSize(), source.affectsCrumbling(), true,
                    () -> {
                        source.setupRenderState();
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                        RenderSystem.depthMask(false);
                        RenderSystem.setShader(BlockEntityBuffers::shader);
                    }, () -> {
                        source.clearRenderState();
                        RenderSystem.depthMask(true);
                        RenderSystem.disableBlend();
                    }) {});
            }
            return new XRayVertexConsumer(original.getBuffer(faint), false);
        };
    }
}
*///?}
