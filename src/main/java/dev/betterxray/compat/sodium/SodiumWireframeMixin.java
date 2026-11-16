package dev.betterxray.compat.sodium;

//? if <26.1 {
/*import dev.betterxray.render.XRayState;
//? if >=1.21 {
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
//?} else {
/^import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
^///?}
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Wireframe mode for Sodium 0.5 (also Embeddium on Forge 1.20.1) and 0.8, which draw terrain with their own GL state: the
// translucent pass (all unselected terrain while X-Ray is on) is rasterized as lines. Sodium 0.9
// (26.x) draws through render pipelines instead (SodiumPipelineMixin). The methods are selected by
// name; begin has two more parameters in 0.8.14 (1.21.11).
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class SodiumWireframeMixin {
    @Inject(method = "begin", at = @At("TAIL"), remap = false)
    private void xray$lines(TerrainRenderPass pass,
        //? if >=1.21.11 {
        net.caffeinemc.mods.sodium.client.util.FogParameters fog, com.mojang.blaze3d.textures.GpuSampler sampler,
        //?}
        CallbackInfo ci) {
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT && XRayState.wireframe())
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
    }

    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void xray$fill(TerrainRenderPass pass, CallbackInfo ci) {
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    }
}
*///?}
