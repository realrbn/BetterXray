package dev.betterxray.mixin;

//? if <1.21.11 {
/*import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * ESP outlines reuse vanilla's glowing-outline pass (drawn through terrain, also for invisible
 * mobs). Only this client-side query changes; the entity's glowing flag is untouched.
 ^/
@Mixin(Minecraft.class)
public abstract class MobEspOutlineMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void betterxray$espOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (MobEspRenderer.outlineColor(entity) != -1) cir.setReturnValue(true);
    }
}
*///?}
