package dev.betterxray.mixin;

//? if >=1.21.11 {
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
import dev.betterxray.render.RenderSetupCopy;
//? if >=26.3 {
import net.minecraft.client.renderer.oit.OitPipelineSet;
//?}
import net.minecraft.client.renderer.rendertype.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
//? if >=26.3 {
import org.jspecify.annotations.Nullable;
//?}
import java.util.Map;

@Mixin(RenderSetup.class)
public abstract class RenderSetupMixin implements RenderSetupCopy {
    @Shadow @Final private Map<String, ?> textures;
    @Shadow @Final private boolean useLightmap;
    @Shadow @Final private boolean useOverlay;
    @Shadow @Final private LayeringTransform layeringTransform;
    //? if >=26.3 {
    @Shadow @Final private @Nullable OitPipelineSet oitPipelineSet;
    @Shadow @Final private @Nullable String outlineTextureName;
    @Shadow @Final private boolean forceSolidModelPhase;
    //?} else {
    /*@Shadow @Final private OutputTarget outputTarget;
    *///?}
    @Shadow @Final private TextureTransform textureTransform;
    @Shadow @Final private RenderSetup.OutlineProperty outlineProperty;
    @Shadow @Final private boolean affectsCrumbling;
    //? if <26.1 {
    /*@Shadow @Final private int bufferSize;
    *///?}
    //? if >=26.3 {
    @Invoker("<init>")
    private static RenderSetup xray$create(RenderPipeline pipeline, OitPipelineSet oitPipelines,
        Map<String, ?> textures, boolean lightmap, boolean overlay, LayeringTransform layering,
        TextureTransform transform, RenderSetup.OutlineProperty outline, String outlineTexture,
        boolean crumbling, boolean sort, boolean forceSolid) { throw new AssertionError(); }
    //?} else if >=26.1 {
    /*@Invoker("<init>")
    private static RenderSetup xray$create(RenderPipeline pipeline, Map<String, ?> textures, boolean lightmap,
        boolean overlay, LayeringTransform layering, OutputTarget target, TextureTransform transform,
        RenderSetup.OutlineProperty outline, boolean crumbling, boolean sort) { throw new AssertionError(); }
    *///?} else {
    /*@Invoker("<init>")
    private static RenderSetup xray$create(RenderPipeline pipeline, Map<String, ?> textures, boolean lightmap,
        boolean overlay, LayeringTransform layering, OutputTarget target, TextureTransform transform,
        RenderSetup.OutlineProperty outline, boolean crumbling, boolean sort, int bufferSize) { throw new AssertionError(); }
    *///?}
    @Override public RenderSetup xray$copy(RenderPipeline pipeline) {
        //? if >=26.3 {
        return xray$create(pipeline, oitPipelineSet, textures, useLightmap, useOverlay, layeringTransform,
            textureTransform, outlineProperty, outlineTextureName, affectsCrumbling, true, forceSolidModelPhase);
        //?} else if >=26.1 {
        /*return xray$create(pipeline, textures, useLightmap, useOverlay, layeringTransform, outputTarget,
            textureTransform, outlineProperty, affectsCrumbling, true);
        *///?} else {
        /*return xray$create(pipeline, textures, useLightmap, useOverlay, layeringTransform, outputTarget,
            textureTransform, outlineProperty, affectsCrumbling, true, bufferSize);
        *///?}
    }
}
//?}
