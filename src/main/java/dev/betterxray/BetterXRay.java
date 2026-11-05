package dev.betterxray;

//? if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/^*
 * Forge entrypoint. Forge has no client-only {@code @Mod}, so this class references no client code
 * and starts {@link XRayClient} on the physical client only; a dedicated server just loads it.
 ^/
@Mod("betterxray")
public final class BetterXRay {
    public BetterXRay() {
        if (FMLEnvironment.dist == Dist.CLIENT) XRayClient.init(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
*///?}
