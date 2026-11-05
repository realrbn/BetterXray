package dev.betterxray.render;

/** Implemented on Particle via mixin; tags ambient underwater particles so X-Ray can clear existing ones. */
public interface UnderwaterParticleMarker {
    void betterxray$markUnderwater();
}
