package dev.betterxray.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.gui.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.20.2 {
import net.minecraft.resources.Identifier;
//?}
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else if >=1.21.11 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}

// Text fields: recessed glass with an accent rim while focused.
@Mixin(EditBox.class)
public abstract class GlassEditBoxMixin {
    private boolean betterxray$focused() { return ((EditBox)(Object)this).isFocused(); }

    //? if >=26.3 {
    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    //?} else if >=26.1 {
    /*@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    *///?} else if >=1.21.11 {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    *///?}
    //? if >=1.21.11 {
    private void betterxray$glassField(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
                                       int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.field(g, x, y, x + w, y + h, betterxray$focused());
        else original.call(g, pipeline, sprite, x, y, w, h);
    }
    //?} else if >=1.20.2 {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lnet/minecraft/resources/Identifier;IIII)V"))
    private void betterxray$glassField(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.field(g, x, y, x + w, y + h, betterxray$focused());
        else original.call(g, sprite, x, y, w, h);
    }
    *///?} else {
    /*// 1.20.1 draws a 1-pixel frame (first fill) and a black inner area (second fill).
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void betterxray$glassField(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.field(g, x0, y0, x1, y1, betterxray$focused());
        else original.call(g, x0, y0, x1, y1, color);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void betterxray$glassFieldInner(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!GlassTheme.active()) original.call(g, x0, y0, x1, y1, color);
    }
    *///?}
}
