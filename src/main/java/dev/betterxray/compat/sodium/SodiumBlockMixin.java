package dev.betterxray.compat.sodium;

//? if >=1.21.11 {
import dev.betterxray.compat.IrisCompat;
import dev.betterxray.compat.Mc;
import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.*;
import net.caffeinemc.mods.sodium.client.render.model.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderer.class)
public abstract class SodiumBlockMixin extends AbstractBlockRenderContext {
    /** Cave-Find: the quad in processQuad faces underground air; one renderer per meshing thread. */
    @Unique private boolean xray$cave;

    // The target has no Minecraft types, so it needs no remapping on obfuscated versions.
    @ModifyArg(method = "processQuad", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private Material xray$material(Material original) {
        return XRayState.enabled() && !XRayState.selected(state) && !xray$cave ? DefaultMaterials.TRANSLUCENT : original;
    }
    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void xray$hide(MutableQuadViewImpl quad, CallbackInfo ci) {
        xray$cave = false;
        if (!XRayState.selected(state))
            xray$cave = XRayState.caveFace(level, pos, quad.getCullFace() != null ? quad.getCullFace() : quad.getNominalFace());
        if (XRayState.hideTerrain() && !XRayState.selected(state) && !xray$cave) ci.cancel();
    }
    @Inject(method = "bufferQuad", at = @At("HEAD"))
    private void xray$quad(MutableQuadViewImpl quad, float[] brightness, Material material, CallbackInfo ci) {
        if (!XRayState.enabled()) return;
        // Experimental shader opacity patch: the faint quad's opacity travels in Iris' block ID bits.
        if (XRayState.faintEncoded() && !XRayState.selected(state) && !xray$cave) IrisCompat.beginFaintQuad(XRayState.opacity());
        for (int i = 0; i < 4; i++) {
            if (XRayState.selected(state)) {
                quad.setLight(i, Mc.FULL_BRIGHT);
                quad.setColor(i, quad.baseColor(i) | 0xff000000);
                brightness[i] = Math.max(brightness[i], 0.8f);
            } else if (xray$cave) {
                // Full light; the AO/directional shade stays so the cave walls keep their shape.
                quad.setLight(i, Mc.FULL_BRIGHT);
                quad.setColor(i, quad.baseColor(i) | 0xff000000);
                brightness[i] = Math.max(brightness[i], 0.5f);
            } else quad.setColor(i, XRayState.alpha(quad.baseColor(i)));
        }
    }
    @Inject(method = "bufferQuad", at = @At("RETURN"))
    private void xray$quadEnd(MutableQuadViewImpl quad, float[] brightness, Material material, CallbackInfo ci) {
        // Fluids and other blocks encoded later on this thread must not inherit the faint bits.
        IrisCompat.endQuad();
    }
}
//?} else if >=1.21 {
/*import dev.betterxray.compat.Mc;
import dev.betterxray.render.XRayState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sodium 0.8.13 (1.21.1): deselected quads go to the translucent material with the configured
// vertex alpha; selected quads get full light and at least 80 % directional brightness. Cave-Find
// quads (toward underground air) keep their material, opaque with full light.
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockMixin extends AbstractBlockRenderContext {
    /^* Cave-Find: the quad in processQuad faces underground air; one renderer per meshing thread. ^/
    @Unique private boolean xray$cave;

    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$hide(MutableQuadViewImpl quad, CallbackInfo ci) {
        xray$cave = !XRayState.selected(state)
            && XRayState.caveFace(level, pos, quad.cullFace() != null ? quad.cullFace() : quad.nominalFace());
        if (XRayState.hideTerrain() && !XRayState.selected(state) && !xray$cave) ci.cancel();
    }

    @ModifyArg(method = "processQuad", remap = false, index = 2, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private Material xray$material(Material original) {
        return XRayState.enabled() && !XRayState.selected(state) && !xray$cave ? DefaultMaterials.TRANSLUCENT : original;
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
    private void xray$quad(MutableQuadViewImpl quad, float[] brightness, Material material, CallbackInfo ci) {
        if (!XRayState.enabled()) return;
        boolean selected = XRayState.selected(state);
        for (int i = 0; i < 4; i++) {
            if (selected || xray$cave) {
                quad.lightmap(i, Mc.FULL_BRIGHT);
                quad.color(i, quad.color(i) | 0xff000000);
                // Cave walls keep more of their shade so their shape stays readable.
                brightness[i] = Math.max(brightness[i], selected ? 0.8f : 0.5f);
            } else quad.color(i, XRayState.alpha(quad.color(i)));
        }
    }
}
*///?} else {
/*import dev.betterxray.compat.Mc;
import dev.betterxray.compat.SodiumTranslucency;
import dev.betterxray.render.XRayState;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Sodium 0.5.13 (1.20.1): deselected blocks go to the translucent material with the configured
// opacity (applied by SodiumVertexMixin), selected blocks get full light, all faces and at least
// 80 % directional brightness. Methods are selected by name only, so nothing needs remapping.
// The material is chosen per block: a Cave-Find block (bordering underground air) keeps its own
// material and writes only its faces toward that air, opaque with full light.
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockMixin {
    /^* Cave-Find: the block in renderModel borders underground air; one renderer per meshing thread. ^/
    @Unique private boolean xray$cave;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$begin(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci) {
        boolean xrayed = XRayState.enabled() && !XRayState.selected(ctx.state());
        xray$cave = xrayed && XRayState.caveBlock(ctx.world(), ctx.pos());
        if (xrayed && !xray$cave && XRayState.hideTerrain()) ci.cancel();
        else SodiumTranslucency.begin(xrayed && !xray$cave ? XRayState.opacity() : 1f);
    }

    @Inject(method = "renderModel", at = @At("RETURN"), remap = false)
    private void xray$end(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci) {
        SodiumTranslucency.end();
    }

    @ModifyVariable(method = "renderModel", at = @At("STORE"), ordinal = 0, remap = false)
    private Material xray$material(Material original, BlockRenderContext ctx, ChunkBuildBuffers buffers) {
        return XRayState.enabled() && !XRayState.selected(ctx.state()) && !xray$cave ? DefaultMaterials.TRANSLUCENT : original;
    }

    @Inject(method = "isFaceVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$faces(BlockRenderContext ctx, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (XRayState.selected(ctx.state())) cir.setReturnValue(true);
    }

    @Inject(method = "writeGeometry", at = @At("HEAD"), cancellable = true, remap = false)
    private void xray$light(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3 offset, Material material,
                            BakedQuadView quad, int[] colors, QuadLightData light, CallbackInfo ci) {
        if (!XRayState.enabled()) return;
        float min;
        if (XRayState.selected(ctx.state())) min = 0.8f;
        else if (xray$cave) {
            // Faces of a cave block that do not face underground air are not written at all.
            if (!XRayState.caveFace(ctx.world(), ctx.pos(), quad.getLightFace())) {
                ci.cancel();
                return;
            }
            min = 0.5f;
        } else return;
        for (int i = 0; i < 4; i++) {
            light.br[i] = Math.max(min, light.br[i]);
            light.lm[i] = Mc.FULL_BRIGHT;
        }
    }
}
*///?}
