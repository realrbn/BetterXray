package dev.betterxray.mixin;

import dev.betterxray.gui.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.11 {
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.injection.Coerce;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.Identifier;
*///?}
//? if <1.20.5 {
/*import org.spongepowered.asm.mixin.Shadow;
*///?}

// Lists: one rounded glass panel instead of the dark texture and header/footer separators,
// soft row hover and a glass selection instead of the white frame. Before 1.21.11 the list also
// draws its own scrollbar (later versions: GlassScrollbarMixin).
@Mixin(AbstractSelectionList.class)
public abstract class GlassListMixin {
    //? if >=26.1 {
    private static final String BACKGROUND = "extractListBackground";
    private static final String SEPARATORS = "extractListSeparators";
    private static final String ITEM = "extractItem";
    private static final String SELECTION = "extractSelection";
    //?} else {
    /*private static final String BACKGROUND = "renderListBackground";
    private static final String SEPARATORS = "renderListSeparators";
    private static final String ITEM = "renderItem";
    private static final String SELECTION = "renderSelection";
    *///?}

    private boolean betterxray$keyboardFocus() {
        return GlassTheme.keyboardFocus(((AbstractSelectionList<?>)(Object)this).isFocused());
    }

    //? if >=1.20.5 {
    @Inject(method = BACKGROUND, at = @At("HEAD"), cancellable = true)
    private void betterxray$glassBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!GlassTheme.active()) return;
        var self = (AbstractSelectionList<?>)(Object)this;
        GlassTheme.listPanel(g, self.getX(), self.getY(), self.getX() + self.getWidth(), self.getY() + self.getHeight());
        ci.cancel();
    }

    @Inject(method = SEPARATORS, at = @At("HEAD"), cancellable = true)
    private void betterxray$noSeparators(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (GlassTheme.active()) ci.cancel();
    }
    //?}

    //? if >=1.21.11 {
    @Inject(method = ITEM, at = @At("HEAD"))
    private void betterxray$rowHover(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta,
                                     @Coerce LayoutElement entry, CallbackInfo ci) {
        if (GlassTheme.active() && entry.getRectangle().containsPoint(mouseX, mouseY))
            GlassTheme.rowHover(g, entry.getX(), entry.getY(), entry.getX() + entry.getWidth(), entry.getY() + entry.getHeight());
    }

    @Inject(method = SELECTION, at = @At("HEAD"), cancellable = true)
    private void betterxray$glassSelection(GuiGraphicsExtractor g, @Coerce LayoutElement entry, int color, CallbackInfo ci) {
        if (!GlassTheme.active()) return;
        GlassTheme.rowSelection(g, entry.getX(), entry.getY(), entry.getX() + entry.getWidth(), entry.getY() + entry.getHeight(),
            betterxray$keyboardFocus());
        ci.cancel();
    }
    //?} else {
    /*// Rows are passed as top/width/height (content height = item height - 4), centered in the list.
    @Inject(method = ITEM, at = @At("HEAD"))
    private void betterxray$rowHover(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, int index,
                                     int left, int top, int width, int height, CallbackInfo ci) {
        if (GlassTheme.active() && mouseX >= left && mouseX < left + width && mouseY >= top - 2 && mouseY < top + height + 2)
            GlassTheme.rowHover(g, left, top - 2, left + width, top + height + 2);
    }

    @Inject(method = SELECTION, at = @At("HEAD"), cancellable = true)
    private void betterxray$glassSelection(GuiGraphicsExtractor g, int top, int width, int height, int outer, int inner, CallbackInfo ci) {
        if (!GlassTheme.active()) return;
        int left = betterxray$listX() + (betterxray$listWidth() - width) / 2;
        GlassTheme.rowSelection(g, left, top - 2, left + width, top + height + 2, betterxray$keyboardFocus());
        ci.cancel();
    }
    *///?}

    //? if >=1.21.11 {
    //?} else if >=1.20.5 {
    /*private int betterxray$listX() { return ((AbstractSelectionList<?>)(Object)this).getX(); }
    private int betterxray$listWidth() { return ((AbstractSelectionList<?>)(Object)this).getWidth(); }

    // Scrollbar: background sprite (track) and scroller sprite (thumb).
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0))
    private void betterxray$glassTrack(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.scrollTrack(g, x, y, x + w, y + h);
        else original.call(g, sprite, x, y, w, h);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1))
    private void betterxray$glassThumb(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.scrollThumb(g, x, y, x + w, y + h, true);
        else original.call(g, sprite, x, y, w, h);
    }
    *///?} else {
    /*@Shadow protected int x0;
    @Shadow protected int y0;
    @Shadow protected int x1;
    @Shadow protected int y1;
    @Shadow protected int width;

    private int betterxray$listX() { return x0; }
    private int betterxray$listWidth() { return width; }

    // 1.20.1 draws the list background (dirt, darkened through setColor) and the scrollbar
    // (three fills: track, thumb shadow, thumb) inside render().
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setColor(FFFF)V"))
    private void betterxray$noTint(GuiGraphicsExtractor g, float r, float gr, float b, float a, Operation<Void> original) {
        if (!GlassTheme.active()) original.call(g, r, gr, b, a);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lnet/minecraft/resources/Identifier;IIFFIIII)V", ordinal = 0))
    private void betterxray$glassBackground(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v,
                                            int w, int h, int tw, int th, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.listPanel(g, x0, y0, x1, y1);
        else original.call(g, texture, x, y, u, v, w, h, tw, th);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void betterxray$glassTrack(GuiGraphicsExtractor g, int xa, int ya, int xb, int yb, int color, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.scrollTrack(g, xa, ya, xb, yb);
        else original.call(g, xa, ya, xb, yb, color);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void betterxray$glassThumb(GuiGraphicsExtractor g, int xa, int ya, int xb, int yb, int color, Operation<Void> original) {
        if (GlassTheme.active()) GlassTheme.scrollThumb(g, xa, ya, xb, yb, true);
        else original.call(g, xa, ya, xb, yb, color);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 2))
    private void betterxray$noThumbHighlight(GuiGraphicsExtractor g, int xa, int ya, int xb, int yb, int color, Operation<Void> original) {
        if (!GlassTheme.active()) original.call(g, xa, ya, xb, yb, color);
    }
    *///?}
}
