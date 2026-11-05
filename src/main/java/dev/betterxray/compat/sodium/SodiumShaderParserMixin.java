package dev.betterxray.compat.sodium;

//? if <1.21 {
/*import dev.betterxray.compat.SodiumTranslucency;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Sodium 0.5.13 (1.20.1): in the translucent chunk program the vertex alpha becomes real alpha
// instead of ambient-occlusion shade; SodiumVertexMixin moves the shade into the colour.
@Mixin(value = ShaderParser.class, remap = false)
public abstract class SodiumShaderParserMixin {
    @ModifyVariable(method = "parseShader(Ljava/lang/String;Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants;)Ljava/lang/String;",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static String xray$patch(String source) {
        return source.contains(SodiumTranslucency.SHADE_LINE)
            ? source.replace(SodiumTranslucency.SHADE_LINE, SodiumTranslucency.PATCHED_SHADE) : source;
    }
}
*///?}
