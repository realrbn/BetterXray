package dev.betterxray.mixin;

import dev.betterxray.render.UnderwaterParticleMarker;
import dev.betterxray.render.XRayState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SuspendedParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No ambient blue underwater particles (minecraft:underwater) while X-Ray is active. */
@Mixin(SuspendedParticle.UnderwaterProvider.class)
public abstract class UnderwaterParticleMixin {
    //? if >=1.21.2 {
    private static final String CREATE = "createParticle(Lnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/util/RandomSource;)Lnet/minecraft/client/particle/Particle;";
    //?} else {
    /*private static final String CREATE = "createParticle(Lnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;";
    *///?}

    @Inject(method = CREATE,
        at = @At("HEAD"), cancellable = true)
    private void betterxray$noUnderwaterParticles(CallbackInfoReturnable<Particle> cir) {
        if (XRayState.enabled()) cir.setReturnValue(null);
    }

    @Inject(method = CREATE,
        at = @At("RETURN"))
    private void betterxray$tag(CallbackInfoReturnable<Particle> cir) {
        if (cir.getReturnValue() instanceof UnderwaterParticleMarker marker) marker.betterxray$markUnderwater();
    }
}
