package dev.betterxray.mixin;

import dev.betterxray.render.UnderwaterParticleMarker;
import dev.betterxray.render.XRayState;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes underwater particles that already existed when X-Ray was switched on. */
@Mixin(Particle.class)
public abstract class ParticleMixin implements UnderwaterParticleMarker {
    @Unique private boolean betterxray$underwater;
    @Shadow public abstract void remove();

    @Override public void betterxray$markUnderwater() { betterxray$underwater = true; }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void betterxray$dropUnderwater(CallbackInfo ci) {
        if (betterxray$underwater && XRayState.enabled()) { remove(); ci.cancel(); }
    }
}
