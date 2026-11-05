package dev.betterxray.mixin;

//? if >=1.21.11 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.gui.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//?}
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else if >=1.21.11 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
//? if >=26.1 {
import org.spongepowered.asm.mixin.Shadow;
//?}

//? if >=1.21.11 {
// Scrollbars (lists and the scrolling settings panel): thin rounded track and thumb.
// Before 1.21.11 lists draw their own scrollbar (GlassListMixin).
@Mixin(AbstractScrollArea.class)
public abstract class GlassScrollbarMixin {
    //? if >=26.3 {
    private static final String SCROLLBAR = "extractScrollbar";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
    //?} else if >=26.1 {
    /*private static final String SCROLLBAR = "extractScrollbar";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
    *///?} else {
    /*private static final String SCROLLBAR = "renderScrollbar";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
    *///?}

    //? if >=26.1 {
    @Shadow protected abstract boolean scrollable();
    //?} else
    /*private boolean scrollable() { return true; }*/

    @WrapOperation(method = SCROLLBAR, at = @At(value = "INVOKE", target = BLIT))
    private void betterxray$glassScrollbar(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
                                           int x, int y, int w, int h, Operation<Void> original) {
        if (!GlassTheme.active()) { original.call(g, pipeline, sprite, x, y, w, h); return; }
        // The track spans the whole area; everything shorter is the thumb.
        if (h >= ((AbstractScrollArea)(Object)this).getHeight()) GlassTheme.scrollTrack(g, x, y, x + w, y + h);
        else GlassTheme.scrollThumb(g, x, y, x + w, y + h, scrollable());
    }
}
//?}
