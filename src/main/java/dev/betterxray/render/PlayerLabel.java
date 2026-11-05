package dev.betterxray.render;

import net.minecraft.world.phys.Vec3;

/**
 * Player ESP label captured during extraction: anchor above the head (interpolated), the name
 * ({@code null} when names are off) and health ({@code showNumbers}/{@code showBar} from config).
 */
public record PlayerLabel(Vec3 anchor, String name, float health, float maxHealth, float absorption,
                          boolean showNumbers, boolean showBar) {}
