package dev.betterxray.validation.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Remapped access for the production-jar run (reflection by name would break there). */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccess {
    @Invoker("shouldShowEntityOutlines") boolean betterxrayValidation$outlines();
}
