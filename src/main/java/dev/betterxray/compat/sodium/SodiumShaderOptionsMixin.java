package dev.betterxray.compat.sodium;

//? if <1.21 {
/*import dev.betterxray.compat.SodiumTranslucency;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Sodium 0.5.13 (1.20.1): marks the translucent chunk program, see SodiumShaderParserMixin.
@Mixin(value = ChunkShaderOptions.class, remap = false)
public abstract class SodiumShaderOptionsMixin {
    @Redirect(method = "constants", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants;builder()Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants$Builder;"))
    private ShaderConstants.Builder xray$define() {
        ShaderConstants.Builder builder = ShaderConstants.builder();
        if (((ChunkShaderOptions)(Object)this).pass() == DefaultTerrainRenderPasses.TRANSLUCENT)
            builder.add(SodiumTranslucency.DEFINE);
        return builder;
    }
}
*///?}
