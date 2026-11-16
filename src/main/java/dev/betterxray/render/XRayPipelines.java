package dev.betterxray.render;

//? if >=1.21.11 {
import dev.betterxray.compat.IrisCompat;
import dev.betterxray.compat.Mc;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.*;
//?} else {
/*import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.PolygonMode;
*///?}
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.RenderType;
import dev.betterxray.mixin.RenderTypeAccessor;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class XRayPipelines {
    private static final Map<RenderPipeline, RenderPipeline> COPIES = new IdentityHashMap<>();
    private static final Map<RenderPipeline, RenderPipeline> WIREFRAME_COPIES = new IdentityHashMap<>();
    private static final Map<RenderPipeline, RenderPipeline> OIT_WIREFRAME_COPIES = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> MODEL_COPIES = new IdentityHashMap<>();
    public static RenderType faintModel(RenderType source) {
        return MODEL_COPIES.computeIfAbsent(source, type -> {
            RenderPipeline pipeline = copy(type.pipeline(), true, false, false);
            if (pipeline != type.pipeline()) IrisCompat.assignFaintModel(pipeline);
            return RenderType.create("betterxray_faint_model",
                ((RenderSetupCopy)(Object)((RenderTypeAccessor)type).xray$setup()).xray$copy(pipeline));
        });
    }
    public static RenderPipeline translucent(RenderPipeline original) {
        if (!XRayState.enabled()) return original;
        // Wireframe mode: the same copy with a line polygon mode. Vertex data and shaders stay, so
        // the lines carry the texture color and the opacity meshed into the quads.
        if (XRayState.wireframe()) return WIREFRAME_COPIES.computeIfAbsent(original, source -> copy(source, false, true, false));
        return COPIES.computeIfAbsent(original, source -> copy(source, false, false, false));
    }
    /**
     * Vanilla's order-independent transparency draws translucent terrain with its own stage
     * pipelines instead of the layer's (ChunkSectionsToRender.renderOit). In the Wireframe mode
     * they get the line polygon mode and keep their depth state and alpha cutout.
     */
    public static RenderPipeline oitTerrain(RenderPipeline original) {
        if (!XRayState.wireframe()) return original;
        return OIT_WIREFRAME_COPIES.computeIfAbsent(original, source -> copy(source, false, true, true));
    }
    //? if >=26.1 {
    /** {@code keepState}: only the polygon mode changes (depth writes and the alpha cutout stay). */
    private static RenderPipeline copy(RenderPipeline source, boolean model, boolean wireframe, boolean keepState) {
        DepthStencilState depth = source.getDepthStencilState();
        if (depth == null) return source;
        var polygonMode = wireframe ? PolygonMode.WIREFRAME : source.getPolygonMode();
        //? if >=26.3 {
        var colors = source.getColorTargetStates().toArray(ColorTargetState[]::new);
        //?} else {
        /*var colors = source.getColorTargetStates().clone();
        *///?}
        var originalDefines = source.getShaderDefines();
        var values = new HashMap<>(originalDefines.values());
        if (!keepState && values.containsKey("ALPHA_CUTOUT")) values.put("ALPHA_CUTOUT", "0.001");
        var defines = new ShaderDefines(Map.copyOf(values), originalDefines.flags());
        if (model) {
            for (int i = 0; i < colors.length; i++) if (colors[i] != null)
                colors[i] = new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), colors[i].format(), colors[i].writeMask());
        }
        //? if >=26.3 {
        var snippet = new RenderPipeline.Snippet(source.getShaders(),
            Optional.of(defines), Optional.of(source.getBindGroupLayouts()), colors, colors.length,
            Optional.of(new DepthStencilState(depth.depthTest(), keepState && depth.writeDepth(), depth.depthBiasScaleFactor(), depth.depthBiasConstant())),
            Optional.of(polygonMode), Optional.of(source.isCull()),
            source.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),
            Optional.of(source.getPrimitiveTopology()), source.pushConstantSize());
        //?} else {
        /*var snippet = new RenderPipeline.Snippet(Optional.of(source.getVertexShader()),
            Optional.of(source.getFragmentShader()), Optional.of(defines),
            Optional.of(source.getBindGroupLayouts()), colors, colors.length,
            Optional.of(new DepthStencilState(depth.depthTest(), keepState && depth.writeDepth(), depth.depthBiasScaleFactor(), depth.depthBiasConstant())),
            Optional.of(polygonMode), Optional.of(source.isCull()),
            source.getVertexFormatBindings().clone(), Optional.of(source.getPrimitiveTopology()));
        *///?}
        // Iris recognises Sodium's terrain pipelines only by a namespace containing "sodium" and
        // then draws them with the pack's terrain program, so copies of them keep that word.
        String namespace = source.getLocation().getNamespace().contains("sodium") ? "betterxray_sodium" : "betterxray";
        return RenderPipeline.builder(snippet).withLocation(Mc.id(namespace,
            source.getLocation().getNamespace() + "/" + source.getLocation().getPath() + (wireframe ? "_wireframe" : ""))).build();
    }
    //?} else {
    /*private static RenderPipeline copy(RenderPipeline source, boolean model, boolean wireframe, boolean keepState) {
        // Same copy for 1.21.11: shaders, uniforms, samplers and formats stay; only depth writes,
        // the alpha cutout and (for faint block-entity models) blending change.
        var originalDefines = source.getShaderDefines();
        var values = new HashMap<>(originalDefines.values());
        if (values.containsKey("ALPHA_CUTOUT")) values.put("ALPHA_CUTOUT", "0.001");
        var defines = new ShaderDefines(Map.copyOf(values), originalDefines.flags());
        var snippet = new RenderPipeline.Snippet(Optional.of(source.getVertexShader()),
            Optional.of(source.getFragmentShader()), Optional.of(defines),
            Optional.of(source.getSamplers()), Optional.of(source.getUniforms()),
            model ? Optional.of(BlendFunction.TRANSLUCENT) : source.getBlendFunction(),
            Optional.of(source.getDepthTestFunction()), Optional.of(wireframe ? PolygonMode.WIREFRAME : source.getPolygonMode()),
            Optional.of(source.isCull()), Optional.of(source.isWriteColor()), Optional.of(source.isWriteAlpha()),
            Optional.of(false), Optional.of(source.getColorLogic()),
            Optional.of(source.getVertexFormat()), Optional.of(source.getVertexFormatMode()));
        // The 1.21.11 builder does not take the polygon mode over from a snippet.
        return RenderPipeline.builder(snippet)
            .withPolygonMode(wireframe ? PolygonMode.WIREFRAME : source.getPolygonMode())
            .withDepthBias(source.getDepthBiasScaleFactor(), source.getDepthBiasConstant())
            .withLocation(Mc.id("betterxray", source.getLocation().getNamespace() + "/" + source.getLocation().getPath()
                + (wireframe ? "_wireframe" : "")))
            .build();
    }
    *///?}
}
//?}
