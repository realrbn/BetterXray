package dev.betterxray.mixin;

//? if <1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^* The outline buffer takes the ESP color instead of the scoreboard team color (team unchanged). ^/
@Mixin(LevelRenderer.class)
public abstract class MobEspLevelMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
    private int betterxray$espColor(Entity entity, Operation<Integer> original) {
        int color = MobEspRenderer.outlineColor(entity);
        return color != -1 ? color & 0xffffff : original.call(entity);
    }
}
*///?}
