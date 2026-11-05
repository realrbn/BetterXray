package dev.betterxray.mixin;

import dev.betterxray.gui.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.11 {
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.Identifier;
*///?}

// Buttons (plain and cycle buttons) in Better X-Ray screens draw a glass surface instead of the sprite.
@Mixin(AbstractButton.class)
public abstract class GlassButtonMixin {
    private void betterxray$paint(GuiGraphicsExtractor g) {
        var self = (AbstractButton)(Object)this;
        GlassTheme.button(g, self.getX(), self.getY(), self.getX() + self.getWidth(), self.getY() + self.getHeight(),
            self.isActive(), self.isHovered(), GlassTheme.keyboardFocus(self.isFocused()));
    }

    //? if >=26.1 {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    //?} else if >=1.21.11 {
    /*@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    *///?}
    //? if >=1.21.11 {
    private void betterxray$glassButton(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!GlassTheme.active()) return;
        betterxray$paint(g);
        ci.cancel();
    }
    //?} else if >=1.20.2 {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lnet/minecraft/resources/Identifier;IIII)V"))
    private void betterxray$glassButton(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$paint(g);
        else original.call(g, sprite, x, y, w, h);
    }
    *///?} else {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitNineSliced(Lnet/minecraft/resources/Identifier;IIIIIIIIII)V"))
    private void betterxray$glassButton(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h,
                                        int sliceW, int sliceH, int uw, int vh, int u, int v, Operation<Void> original) {
        if (GlassTheme.active()) betterxray$paint(g);
        else original.call(g, texture, x, y, w, h, sliceW, sliceH, uw, vh, u, v);
    }
    *///?}
}
