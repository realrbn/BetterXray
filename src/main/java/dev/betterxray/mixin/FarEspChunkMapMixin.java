package dev.betterxray.mixin;

import dev.betterxray.render.FarEspTracking;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla re-checks the tracking range only when the entity or the player changes its chunk
 * section. While "full view distance" is on (and once after it was switched off) the owner's
 * tracking is re-evaluated every second, so switching the option, the ESP selection or the ESP
 * range takes effect without anyone moving.
 */
@Mixin(ChunkMap.class)
public abstract class FarEspChunkMapMixin {
    @Shadow @Final private Int2ObjectMap<?> entityMap;
    @Shadow @Final private ServerLevel level;

    @Unique private int betterxray$farEspTimer;
    @Unique private boolean betterxray$farEspWasActive;

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void betterxray$refreshFarEsp(CallbackInfo ci) {
        boolean active = FarEspTracking.active();
        if (!active && !betterxray$farEspWasActive) return;
        if (active == betterxray$farEspWasActive && ++betterxray$farEspTimer < 20) return;
        betterxray$farEspTimer = 0;
        betterxray$farEspWasActive = active;
        for (ServerPlayer player : level.players()) {
            if (!FarEspTracking.isOwner(player)) continue;
            for (Object tracked : entityMap.values()) ((FarEspTracking.Tracked)tracked).betterxray$updatePlayer(player);
        }
    }
}
