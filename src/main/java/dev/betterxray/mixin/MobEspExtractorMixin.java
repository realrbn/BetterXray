package dev.betterxray.mixin;

//? if >=26.1 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.betterxray.compat.EntityCullingCompat;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.XRayState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class MobEspExtractorMixin {
    // Entity Culling replaces a culled entity's state at the head of extractEntity; a method wrapper
    // runs before every injection into it, so ESP targets (and, with the X-Ray view, all entities) are released first.
    @WrapMethod(method = "extractEntity")
    private EntityRenderState betterxray$keepVisible(Entity entity, float partialTick, Operation<EntityRenderState> original) {
        if (EntityCullingCompat.active()) EntityCullingCompat.keepVisible(entity, MobEspRenderer.settings(entity) != null);
        return original.call(entity, partialTick);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void betterxray$extractEsp(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        MobEspRenderer.extract(entity, cir.getReturnValue());
    }

    // Tracers also point at targets outside the frustum, which get no entity render state.
    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void betterxray$extractTracers(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                          LevelRenderState output, CallbackInfo ci) {
        MobEspRenderer.extractTracers(deltaTracker);
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    //? if >=26.3 {
    private void betterxray$includeHiddenMob(Entity entity, Frustum frustum, double x, double y, double z,
                                           float partialTick, long fadeDuration, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void betterxray$includeHiddenMob(Entity entity, Frustum frustum, double x, double y, double z,
                                           CallbackInfoReturnable<Boolean> cir) {
    *///?}
        // Selected entities bypass section occlusion and the size-based entity render distance
        // (a dropped item would otherwise vanish after ~16 blocks); the view frustum still applies.
        // How far they reach is limited by the ESP range and the server's entity tracking range.
        // Without ESP, "Hide Mobs" drops mobs while the X-Ray view is on.
        if (MobEspRenderer.settings(entity) == null) {
            if (XRayState.hideMob(entity)) cir.setReturnValue(false);
        } else if (frustum.isVisible(entity.getBoundingBox().inflate(0.5))) cir.setReturnValue(true);
    }
}
//?} else if >=1.21.11 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.betterxray.compat.EntityCullingCompat;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.XRayState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class MobEspExtractorMixin {
    // Entity Culling replaces a culled entity's state at the head of extractEntity; a method wrapper
    // runs before every injection into it, so ESP targets (and, with the X-Ray view, all entities) are released first.
    @WrapMethod(method = "extractEntity")
    private EntityRenderState betterxray$keepVisible(Entity entity, float partialTick, Operation<EntityRenderState> original) {
        if (EntityCullingCompat.active()) EntityCullingCompat.keepVisible(entity, MobEspRenderer.settings(entity) != null);
        return original.call(entity, partialTick);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void betterxray$extractEsp(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        MobEspRenderer.extract(entity, cir.getReturnValue());
    }

    // Selected entities bypass the size-based entity render distance (a dropped item would
    // otherwise vanish after ~16 blocks) and section occlusion; the view frustum still applies.
    // Without ESP, "Hide Mobs" drops mobs while the X-Ray view is on.
    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean betterxray$includeFarMob(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum,
                                             double x, double y, double z, Operation<Boolean> original) {
        if (MobEspRenderer.settings(entity) == null) {
            if (XRayState.hideMob(entity)) return false;
        } else if (frustum.isVisible(entity.getBoundingBox().inflate(0.5))) return true;
        return original.call(dispatcher, entity, frustum, x, y, z);
    }

    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean betterxray$includeHiddenMob(LevelRenderer renderer, BlockPos pos, Operation<Boolean> original,
                                                @Local Entity entity) {
        return original.call(renderer, pos) || MobEspRenderer.settings(entity) != null;
    }
}
*///?}
