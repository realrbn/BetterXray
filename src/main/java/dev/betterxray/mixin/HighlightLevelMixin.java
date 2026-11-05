package dev.betterxray.mixin;

import dev.betterxray.render.HighlightCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class HighlightLevelMixin {
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void betterxray$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        HighlightCache.loaded((ClientLevel)(Object)this, pos);
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void betterxray$chunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        HighlightCache.unloaded((ClientLevel)(Object)this, chunk);
    }

    //? if >=1.21.11 {
    @Inject(method = "setBlock", at = @At("RETURN"))
    //?} else
    /*@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))*/
    private void betterxray$blockChanged(BlockPos pos, BlockState state, int flags, int limit,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) HighlightCache.changed((ClientLevel)(Object)this, pos);
    }

    // Server block packets bypass ClientLevel.setBlock and call Level.setBlock directly.
    @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
    private void betterxray$serverBlockChanged(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        HighlightCache.changed((ClientLevel)(Object)this, pos);
    }
}
