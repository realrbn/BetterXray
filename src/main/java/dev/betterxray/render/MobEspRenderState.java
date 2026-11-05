package dev.betterxray.render;

//? if >=1.21.11 {
import net.minecraft.world.phys.AABB;

/** Per-frame data: the draw thread never reads live entities or mutable configuration. */
public interface MobEspRenderState {
    void betterxray$setEspBox(AABB box, int color);
    AABB betterxray$espBox();
    int betterxray$espColor();
    void betterxray$setPlayerLabel(PlayerLabel label);
    PlayerLabel betterxray$playerLabel();
}
//?}
