package dev.betterxray.mixin;

//? if >=1.21.11 {
import dev.betterxray.render.MobEspRenderState;
import dev.betterxray.render.PlayerLabel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class MobEspStateMixin implements MobEspRenderState {
    @Unique private AABB betterxray$box;
    @Unique private int betterxray$color;
    @Unique private PlayerLabel betterxray$label;

    @Override public void betterxray$setEspBox(AABB box, int color) { betterxray$box = box; betterxray$color = color; }
    @Override public AABB betterxray$espBox() { return betterxray$box; }
    @Override public int betterxray$espColor() { return betterxray$color; }
    @Override public void betterxray$setPlayerLabel(PlayerLabel label) { betterxray$label = label; }
    @Override public PlayerLabel betterxray$playerLabel() { return betterxray$label; }
}
//?}
