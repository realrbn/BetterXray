package dev.betterxray.mixin;

import dev.betterxray.compat.Platform;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class XRayMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean sodium = Platform.sodiumLoaded();
        if (mixin.contains(".sodium.")) return sodium;
        if (mixin.contains(".Indigo")) return !sodium;
        return true;
    }
    private String mixinPackage = "";

    /**
     * Glass look of the Better X-Ray screens (gui.GlassTheme), on every target and loader.
     * Before 1.21.11 there is no shared scroll area; the list scrollbar is part of GlassListMixin.
     */
    //? if >=1.21.11 {
    private static final List<String> GLASS_MIXINS = List.of("GlassScreenMixin", "GlassButtonMixin", "GlassSliderMixin",
        "GlassEditBoxMixin", "GlassListMixin", "GlassScrollbarMixin");
    //?} else
    /*private static final List<String> GLASS_MIXINS = List.of("GlassScreenMixin", "GlassButtonMixin", "GlassSliderMixin", "GlassEditBoxMixin", "GlassListMixin");*/

    @Override public void onLoad(String mixinPackage) { this.mixinPackage = mixinPackage; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    /**
     * The mixin classes differ between Minecraft generations, so they are listed here (with
     * Stonecutter conditions) instead of in the JSON configs.
     */
    @Override public List<String> getMixins() {
        // Every target is client code; a dedicated server (NeoForge) must not even look them up.
        if (!Platform.isClient()) return List.of();
        if (mixinPackage.endsWith(".iris")) {
            // Experimental shader opacity patch: Iris internals, optional config.
            //? if fabric && >=26.3 {
            if (Platform.isModLoaded("iris")) return List.of("IrisPatcherMixin", "IrisVertexMixin", "IrisUniformsMixin");
            //?}
            return List.of();
        }
        if (mixinPackage.endsWith(".sodium")) {
            // Without Sodium the targets do not exist; unlisted mixins are never prepared.
            if (!Platform.sodiumLoaded()) return List.of();
            //? if >=26.1 {
            return List.of("SodiumBlockMixin", "SodiumCullMixin", "SodiumOcclusionMixin", "SodiumFluidMixin",
                "SodiumFluidFacesMixin", "SodiumPipelineMixin", "SodiumSortMixin");
            //?} else if >=1.21 {
            /*return List.of("SodiumBlockMixin", "SodiumCullMixin", "SodiumFluidMixin", "SodiumFluidFacesMixin", "SodiumSortMixin");
            *///?} else if forge {
            /*// Embeddium (Forge 1.20.1), a Sodium 0.5 fork with real vertex alpha: no shader patch.
            return List.of("SodiumBlockMixin", "SodiumCullMixin", "SodiumFluidMixin", "SodiumFluidFacesMixin", "SodiumVertexMixin", "SodiumSortMixin");
            *///?} else {
            /*return List.of("SodiumBlockMixin", "SodiumCullMixin", "SodiumFluidMixin", "SodiumFluidFacesMixin",
                "SodiumVertexMixin", "SodiumShaderOptionsMixin", "SodiumShaderParserMixin");
            *///?}
        }
        var mixins = new ArrayList<>(renderMixins());
        mixins.addAll(GLASS_MIXINS);
        // Debug HUD where the loader has no HUD render event.
        //? if forge && >=1.21
        /*mixins.add("DebugHudMixin");*/
        return mixins;
    }

    private static List<String> renderMixins() {
        //? if neoforge && >=26.1 {
        /*// NeoForge meshes with the vanilla block renderer (SectionCompilerMixin) instead of Indigo.
        return List.of("SectionCompilerMixin", "ModelBlockRendererMixin", "VisGraphMixin", "FluidMixin", "TranslucentSortMixin", "TranslucentResortMixin",
            "ChunkLayerMixin", "RenderSetupMixin", "RenderTypeAccessor", "BlockEntityStateAccessor",
            "BlockEntityDispatcherMixin", "ModelSubmitMixin", "FullbrightLightmapMixin", "FullbrightKeyMixin",
            "KeyMappingMixin", "HighlightLevelMixin", "HighlightRenderMixin", "UnderwaterFogMixin",
            "UnderwaterOverlayMixin", "UnderwaterParticleMixin", "ParticleMixin", "MobEspStateMixin",
            "MobEspExtractorMixin", "MobEspRenderMixin", "GameRendererAccessor");
        *///?} else if !fabric && <1.21.11 {
        /*// NeoForge 1.21.1 and Forge mesh with the vanilla block renderer, one call per render type (SectionCompilerMixin).
        return List.of("SectionCompilerMixin", "VisGraphMixin", "FluidMixin", "TranslucentSortMixin", "TranslucentResortMixin", "TranslucentDepthMixin", "BlockEntityMixin",
            "FullbrightLightmapMixin", "FullbrightKeyMixin", "KeyMappingMixin", "HighlightLevelMixin", "UnderwaterFogMixin",
            "UnderwaterOverlayMixin", "UnderwaterParticleMixin", "ParticleMixin", "MobEspOutlineMixin", "MobEspLevelMixin",
            "MobEspVisibilityMixin", "MobEspSectionMixin", "NameTagMixin", "ChunkMapAccessor");
        *///?} else if neoforge {
        /*return List.of("SectionCompilerMixin", "VisGraphMixin", "FluidMixin", "TranslucentSortMixin", "TranslucentResortMixin", "ChunkLayerMixin",
            "RenderSetupMixin", "RenderTypeAccessor", "BlockEntityStateAccessor", "BlockEntityDispatcherMixin",
            "ModelSubmitMixin", "FullbrightLightmapMixin", "FullbrightKeyMixin", "KeyMappingMixin",
            "HighlightLevelMixin", "HighlightRenderMixin", "UnderwaterFogMixin", "UnderwaterOverlayMixin",
            "UnderwaterParticleMixin", "ParticleMixin", "MobEspStateMixin", "MobEspExtractorMixin", "MobEspRenderMixin",
            "GameRendererAccessor");
        *///?} else if >=26.1 {
        return List.of("IndigoMixin", "VisGraphMixin", "FluidMixin", "TranslucentSortMixin", "TranslucentResortMixin", "ChunkLayerMixin", "RenderSetupMixin",
            "RenderTypeAccessor", "BlockEntityStateAccessor", "BlockEntityDispatcherMixin", "ModelSubmitMixin",
            "FullbrightLightmapMixin", "FullbrightKeyMixin", "KeyMappingMixin", "HighlightLevelMixin",
            "HighlightRenderMixin", "UnderwaterFogMixin", "UnderwaterOverlayMixin", "UnderwaterParticleMixin",
            "ParticleMixin", "MobEspStateMixin", "MobEspExtractorMixin", "MobEspRenderMixin",
            "GameRendererAccessor");
        //?} else if >=1.21.11 {
        /*return List.of("IndigoMixin", "IndigoCullMixin", "VisGraphMixin", "FluidMixin", "TranslucentSortMixin", "TranslucentResortMixin", "ChunkLayerMixin",
            "RenderSetupMixin", "RenderTypeAccessor", "BlockEntityStateAccessor", "BlockEntityDispatcherMixin",
            "ModelSubmitMixin", "FullbrightLightmapMixin", "FullbrightKeyMixin", "KeyMappingMixin",
            "HighlightLevelMixin", "HighlightRenderMixin", "UnderwaterFogMixin", "UnderwaterOverlayMixin",
            "UnderwaterParticleMixin", "ParticleMixin", "MobEspStateMixin", "MobEspExtractorMixin", "MobEspRenderMixin",
            "GameRendererAccessor");
        *///?} else {
        /*return List.of("IndigoMixin", "VisGraphMixin", "FluidMixin", "TranslucentSortMixin", "TranslucentResortMixin", "TerrainMixin", "ModelRendererMixin",
            "TranslucentDepthMixin", "BlockEntityMixin", "FullbrightLightmapMixin", "FullbrightKeyMixin",
            "KeyMappingMixin", "HighlightLevelMixin", "UnderwaterFogMixin", "UnderwaterOverlayMixin",
            "UnderwaterParticleMixin", "ParticleMixin", "MobEspOutlineMixin", "MobEspLevelMixin",
            "MobEspVisibilityMixin", "MobEspSectionMixin", "NameTagMixin", "ChunkMapAccessor");
        *///?}
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
