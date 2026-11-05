package dev.betterxray.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.gui.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else if >=1.21.11 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}

// Sliders: glass track with an accent fill up to the handle, and a bright pill as handle.
@Mixin(AbstractSliderButton.class)
public abstract class GlassSliderMixin {
    @Shadow protected double value;

    //? if >=26.3 {
    private static final String RENDER = "extractWidgetRenderState";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
        + "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";
    //?} else if >=26.1 {
    /*private static final String RENDER = "extractWidgetRenderState";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
        + "Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";
    *///?} else if >=1.21.11 {
    /*private static final String RENDER = "renderWidget";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
        + "Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";
    *///?} else if >=1.20.2 {
    /*private static final String RENDER = "renderWidget";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lnet/minecraft/resources/Identifier;IIII)V";
    *///?} else {
    /*private static final String RENDER = "renderWidget";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitNineSliced(Lnet/minecraft/resources/Identifier;IIIIIIIIII)V";
    *///?}

    private void betterxray$track(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var self = (AbstractSliderButton)(Object)this;
        GlassTheme.sliderTrack(g, x, y, x + w, y + h, value, self.isActive(), GlassTheme.keyboardFocus(self.isFocused()));
    }

    private void betterxray$handle(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var self = (AbstractSliderButton)(Object)this;
        GlassTheme.sliderHandle(g, x, y, x + w, y + h, self.isActive(), self.isHovered());
    }

    //? if >=1.21.11 {
    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void betterxray$glassTrack(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
                                       int x, int y, int w, int h, int color, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$track(g, x, y, w, h);
        else original.call(g, pipeline, sprite, x, y, w, h, color);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void betterxray$glassHandle(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
                                        int x, int y, int w, int h, int color, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$handle(g, x, y, w, h);
        else original.call(g, pipeline, sprite, x, y, w, h, color);
    }
    //?} else if >=1.20.2 {
    /*@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void betterxray$glassTrack(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$track(g, x, y, w, h);
        else original.call(g, sprite, x, y, w, h);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void betterxray$glassHandle(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$handle(g, x, y, w, h);
        else original.call(g, sprite, x, y, w, h);
    }
    *///?} else {
    /*@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void betterxray$glassTrack(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h,
                                       int sliceW, int sliceH, int uw, int vh, int u, int v, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$track(g, x, y, w, h);
        else original.call(g, texture, x, y, w, h, sliceW, sliceH, uw, vh, u, v);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void betterxray$glassHandle(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h,
                                        int sliceW, int sliceH, int uw, int vh, int u, int v, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$handle(g, x, y, w, h);
        else original.call(g, texture, x, y, w, h, sliceW, sliceH, uw, vh, u, v);
    }
    *///?}
}
