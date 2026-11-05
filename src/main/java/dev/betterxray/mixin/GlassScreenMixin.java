package dev.betterxray.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.betterxray.gui.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Better X-Ray screens keep the blur (1.20.5+) but replace the dark menu texture, the in-world
// gradient and (1.20.1) the dirt background with a tinted glass backdrop.
@Mixin(Screen.class)
public abstract class GlassScreenMixin {
    //? if >=26.1 {
    private static final String BACKGROUND = "extractBackground";
    private static final String MENU = "Lnet/minecraft/client/gui/screens/Screen;extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V";
    //?} else {
    /*private static final String BACKGROUND = "renderBackground";
    private static final String MENU = "Lnet/minecraft/client/gui/screens/Screen;renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V";
    *///?}

    //? if >=1.20.5 {
    @WrapOperation(method = BACKGROUND, at = @At(value = "INVOKE", target = MENU))
    private void betterxray$glassBackdrop(Screen screen, GuiGraphicsExtractor g, Operation<Void> original) {
        if (GlassTheme.owns(screen)) GlassTheme.backdrop(g, screen.width, screen.height);
        else original.call(screen, g);
    }
    //?}

    //? if >=26.1 {
    @WrapOperation(method = BACKGROUND, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;extractTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    //?} else if >=1.21.11 {
    /*@WrapOperation(method = BACKGROUND, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    *///?}
    //? if >=1.21.11 {
    private void betterxray$glassTransparent(Screen screen, GuiGraphicsExtractor g, Operation<Void> original) {
        if (GlassTheme.owns(screen)) GlassTheme.backdrop(g, screen.width, screen.height);
        else original.call(screen, g);
    }
    //?}

    //? if <1.20.5 {
    /*// 1.20.1: in-world gradient or dirt texture, no blur yet.
    @WrapOperation(method = "renderBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"))
    private void betterxray$glassGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int top, int bottom, Operation<Void> original) {
        Screen screen = (Screen)(Object)this;
        if (GlassTheme.owns(screen)) GlassTheme.backdrop(g, screen.width, screen.height);
        else original.call(g, x0, y0, x1, y1, top, bottom);
    }

    @WrapOperation(method = "renderBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void betterxray$glassDirt(Screen screen, GuiGraphicsExtractor g, Operation<Void> original) {
        if (GlassTheme.owns(screen)) GlassTheme.backdrop(g, screen.width, screen.height);
        else original.call(screen, g);
    }
    *///?}
}
