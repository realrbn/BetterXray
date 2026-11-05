package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^* The ESP label shows the player's name through walls; the vanilla tag would only duplicate it. ^/
@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean betterxray$hideReplacedTag(EntityRenderer<?> renderer, Entity entity, Operation<Boolean> original) {
        return !MobEspRenderer.replacesNameTag(entity) && original.call(renderer, entity);
    }
}
*///?}
