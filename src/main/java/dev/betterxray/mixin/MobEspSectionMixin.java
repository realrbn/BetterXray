package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * Before 1.21.11 an entity is only drawn when its chunk section has been meshed. Sections hidden
 * behind solid rock are never meshed while X-Ray is off (vanilla and Sodium only build sections
 * the occlusion graph reaches), so deep mobs lost their ESP outline. ESP entities skip that check;
 * {@link MobEspVisibilityMixin} marks the entity currently tested in the render loop.
 ^/
@Mixin(LevelRenderer.class)
public abstract class MobEspSectionMixin {
    //? if >=1.20.2 {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    //?} else {
    /^@WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    ^///?}
    private boolean betterxray$espSection(LevelRenderer self, BlockPos pos, Operation<Boolean> original) {
        return MobEspRenderer.legacyEspCandidate || original.call(self, pos);
    }
}
*///?}
