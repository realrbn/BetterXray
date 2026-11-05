package dev.betterxray.mixin;

import dev.betterxray.XRayClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class FullbrightKeyMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void betterxray$handleFullbright(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (XRayClient.handlePriorityKey(Minecraft.getInstance(), action, event)) ci.cancel();
    }
    //?} else {
    /*private void betterxray$handleFullbright(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().getWindow() && XRayClient.handlePriorityKey(client, action, key, scancode)) ci.cancel();
    }
    *///?}
}
