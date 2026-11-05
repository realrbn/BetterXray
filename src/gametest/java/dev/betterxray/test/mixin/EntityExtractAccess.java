package dev.betterxray.test.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
//? if >=26.1 {
import net.minecraft.client.renderer.extract.LevelExtractor;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}

/** Remapped access for the production-jar gametest run (reflection by name breaks there). */
//? if >=26.1 {
@Mixin(LevelExtractor.class)
//?} else
/*@Mixin(LevelRenderer.class)*/
public interface EntityExtractAccess {
    @Invoker("extractEntity") EntityRenderState betterxrayTest$extractEntity(Entity entity, float partialTick);

    //? if <26.1 {
    /*@Invoker("extractVisibleEntities") void betterxrayTest$extractVisibleEntities(net.minecraft.client.Camera camera,
        net.minecraft.client.renderer.culling.Frustum frustum, net.minecraft.client.DeltaTracker delta,
        net.minecraft.client.renderer.state.level.LevelRenderState state);
    *///?}
}
