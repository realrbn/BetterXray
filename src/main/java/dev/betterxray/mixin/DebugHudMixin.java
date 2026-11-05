package dev.betterxray.mixin;

//? if forge && >=1.21 {
/*import dev.betterxray.debug.DebugHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* Forge 1.21.1 has no HUD render event: the debug HUD is drawn after the vanilla HUD layers. ^/
@Mixin(Gui.class)
abstract class DebugHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void betterxray$debugHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DebugHud.draw(graphics);
    }
}
*///?}
