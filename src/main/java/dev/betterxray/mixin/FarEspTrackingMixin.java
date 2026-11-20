package dev.betterxray.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.betterxray.render.FarEspTracking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Integrated server: ESP targets are sent to the singleplayer owner up to the view distance
 * ({@link FarEspTracking}). {@code updatePlayer} uses {@code min(getEffectiveRange(), viewDistance * 16)}
 * and still requires the entity's chunk to be tracked by the player.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class FarEspTrackingMixin implements FarEspTracking.Tracked {
    @Shadow @Final Entity entity;

    @Shadow public abstract void updatePlayer(ServerPlayer player);

    @ModifyExpressionValue(method = "updatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkMap$TrackedEntity;getEffectiveRange()I"))
    private int betterxray$farEspRange(int range, @Local(argsOnly = true) ServerPlayer player) {
        return FarEspTracking.extend(entity, player) ? Integer.MAX_VALUE : range;
    }

    @Override
    public void betterxray$updatePlayer(ServerPlayer player) {
        updatePlayer(player);
    }
}
