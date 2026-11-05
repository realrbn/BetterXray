package dev.betterxray.mixin;

//? if <1.21.11 {
/*import dev.betterxray.compat.EntityCullingCompat;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Selected entities bypass the size-based entity render distance (a dropped item would otherwise
 * vanish after ~16 blocks); the view frustum still applies. How far they reach is limited by the
 * ESP range and the server's entity tracking range. Without ESP, "Hide Mobs" drops mobs while the
 * X-Ray view is on.
 ^/
@Mixin(EntityRenderDispatcher.class)
public abstract class MobEspVisibilityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void betterxray$includeEspEntity(Entity entity, Frustum frustum, double x, double y, double z,
                                             CallbackInfoReturnable<Boolean> cir) {
        boolean esp = MobEspRenderer.settings(entity) != null;
        MobEspRenderer.legacyEspCandidate = esp;
        // Entity Culling skips culled entities in renderEntity, after this test.
        EntityCullingCompat.keepVisible(entity, esp);
        if (!esp) {
            if (XRayState.hideMob(entity)) cir.setReturnValue(false);
        } else if (frustum.isVisible(entity.getBoundingBox().inflate(0.5))) cir.setReturnValue(true);
    }
}
*///?}
