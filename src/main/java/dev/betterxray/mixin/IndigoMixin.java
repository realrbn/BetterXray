package dev.betterxray.mixin;

//? if fabric && >=26.1 {
import dev.betterxray.compat.Mc;
import dev.betterxray.render.XRayState;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl")
public abstract class IndigoMixin {
    @Shadow private BlockState blockState;
    @Shadow private BlockAndTintGetter level;
    @Shadow private BlockPos pos;
    /** Cave-Find: the quad being transformed faces underground air (one renderer per meshing thread). */
    @Unique private boolean xray$cave;

    @Inject(method = "transform", at = @At("HEAD"))
    private void xray$light(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        xray$cave = false;
        if (XRayState.selected(blockState)) {
            quad.ambientOcclusion(TriState.FALSE);
            quad.emissive(true);
        } else if (XRayState.caveFace(level, pos, quad.cullFace() != null ? quad.cullFace() : quad.nominalFace())) {
            // Full light, but ambient occlusion keeps the shape of the cave walls readable.
            xray$cave = true;
            quad.emissive(true);
        }
    }
    @Inject(method = "shouldCullFace", at = @At("HEAD"), cancellable = true)
    private void xray$faces(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.selected(blockState)) cir.setReturnValue(false);
    }
    @Inject(method = "transform", at = @At("RETURN"), cancellable = true)
    private void xray$quad(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (!XRayState.enabled() || !cir.getReturnValueZ()) return;
        if (XRayState.selected(blockState)) {
            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, Mc.FULL_BRIGHT);
                quad.color(i, quad.color(i) | 0xff000000);
            }
        } else if (xray$cave) {
            // Cave walls keep their own layer and stay opaque.
            for (int i = 0; i < 4; i++) quad.color(i, quad.color(i) | 0xff000000);
        } else if (XRayState.hideTerrain()) {
            cir.setReturnValue(false);
        } else {
            quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
            for (int i = 0; i < 4; i++) quad.color(i, XRayState.alpha(quad.color(i)));
        }
    }
}
//?} else if fabric && >=1.21.11 {
/*import dev.betterxray.render.XRayState;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Indigo meshes every MODEL-shaped block of a chunk section here (vanilla models included).
// Changes at the start of bufferQuad flow through tint, shading and the layer selection.
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext", remap = false)
public abstract class IndigoMixin {
    @Shadow(remap = false) @Final protected BlockRenderInfo blockInfo;

    @Inject(method = "bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$quad(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (!XRayState.enabled() || !((Object)this instanceof TerrainRenderContext)) return;
        BlockState state = blockInfo.blockState;
        if (XRayState.selected(state)) {
            // Full light (emissive) without ambient occlusion keeps enclosed ores visible.
            quad.ambientOcclusion(TriState.FALSE);
            quad.emissive(true);
            for (int i = 0; i < 4; i++) quad.color(i, quad.color(i) | 0xff000000);
        } else if (XRayState.caveFace(blockInfo.blockView, blockInfo.blockPos,
                quad.cullFace() != null ? quad.cullFace() : quad.nominalFace())) {
            // Cave-Find: walls of underground air keep their layer, stay opaque and get full light;
            // ambient occlusion keeps their shape readable.
            quad.emissive(true);
            for (int i = 0; i < 4; i++) quad.color(i, quad.color(i) | 0xff000000);
        } else if (XRayState.hideTerrain()) {
            ci.cancel();
        } else {
            quad.renderLayer(ChunkSectionLayer.TRANSLUCENT);
            for (int i = 0; i < 4; i++) quad.color(i, XRayState.alpha(quad.color(i)));
        }
    }
}
*///?} else if fabric {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.betterxray.render.XRayState;
import dev.betterxray.render.XRayVertexConsumer;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Before 1.21.11 Indigo meshes every MODEL-shaped block by default ("always-tesselate-blocks"),
// vanilla models included; in its compatibility mode vanilla models fall back to
// ModelRendererMixin. Selected blocks lose ambient occlusion and become emissive (full light).
// Cave-Find faces (decided per quad) keep their layer and ambient occlusion and become emissive.
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext", remap = false)
public abstract class IndigoMixin {
    @Shadow(remap = false) @Final protected BlockRenderInfo blockInfo;
    /^* Cave-Find: the quad in renderQuad faces underground air (one context per meshing thread). ^/
    @Unique private boolean xray$cave;

    private boolean xray$terrain() {
        return XRayState.enabled() && (Object)this instanceof TerrainRenderContext && blockInfo.blockState != null;
    }

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$hide(CallbackInfo ci, @Local(argsOnly = true) MutableQuadViewImpl quad) {
        boolean deselected = xray$terrain() && !XRayState.selected(blockInfo.blockState);
        xray$cave = deselected && XRayState.caveFace(blockInfo.blockView, blockInfo.blockPos,
            quad.cullFace() != null ? quad.cullFace() : quad.nominalFace());
        if (deselected && !xray$cave && XRayState.hideTerrain()) ci.cancel();
    }

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$faces(Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (xray$terrain() && XRayState.selected(blockInfo.blockState)) cir.setReturnValue(false);
    }

    private static final String SHADE = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractBlockRenderContext;shadeQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;ZZZ)V";
    // shadeQuad(quad, ao, emissive, vanillaShade) since 1.21; shadeQuad(quad, isVanilla, ao, emissive) on 1.20.1.
    //? if >=1.21 {
    private static final int AO_ARG = 1, EMISSIVE_ARG = 2;
    //?} else
    /^private static final int AO_ARG = 2, EMISSIVE_ARG = 3;^/

    @ModifyArg(method = "renderQuad", remap = false, index = AO_ARG, at = @At(value = "INVOKE", target = SHADE, remap = false))
    private boolean xray$noAo(boolean ao) {
        return ao && !(xray$terrain() && XRayState.selected(blockInfo.blockState));
    }

    @ModifyArg(method = "renderQuad", remap = false, index = EMISSIVE_ARG, at = @At(value = "INVOKE", target = SHADE, remap = false))
    private boolean xray$emissive(boolean emissive) {
        return emissive || (xray$terrain() && (xray$cave || XRayState.selected(blockInfo.blockState)));
    }

    @WrapOperation(method = "renderQuad", remap = false, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractBlockRenderContext;getVertexConsumer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer xray$buffer(net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext self, RenderType layer,
                                       Operation<VertexConsumer> original) {
        if (!xray$terrain()) return original.call(self, layer);
        boolean selected = XRayState.selected(blockInfo.blockState);
        if (xray$cave) return new XRayVertexConsumer(original.call(self, layer), true);
        VertexConsumer buffer = original.call(self, selected ? layer : RenderType.translucent());
        return new XRayVertexConsumer(buffer, selected);
    }
}
*///?}
