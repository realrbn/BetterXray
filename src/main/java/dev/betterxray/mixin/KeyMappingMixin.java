package dev.betterxray.mixin;

import dev.betterxray.XRayClient;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if fabric && <1.21.11 {
/*import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "setAll", at = @At("HEAD"))
    private static void betterxray$repairLegacyBindings(CallbackInfo ci) {
        XRayClient.repairLegacyKeyMappings();
    }

    //? if fabric && <1.21.11 {
    /*// Before 1.21.11 a key belongs to exactly one mapping (NeoForge already delivers shared keys), so the defaults X (vanilla: load
    // toolbar activator) and L (advancements) would never reach Better X-Ray. Like newer
    // versions, a press is also delivered to our mappings that share the key.
    @Shadow private int clickCount;
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;

    @Inject(method = "click", at = @At("TAIL"))
    private static void betterxray$sharedClick(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping owner = MAP.get(key);
        for (KeyMapping mapping : XRayClient.keyMappings()) {
            if (mapping != owner && !mapping.isUnbound() && mapping.saveString().equals(key.getName()))
                ((KeyMappingMixin)(Object)mapping).clickCount++;
        }
    }
    *///?}
}
