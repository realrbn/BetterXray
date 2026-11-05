package dev.betterxray.mixin;

//? if <1.21.11 {
/*import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/^* Structure Finder: {@code getUpdatingChunkIfPresent} is public from 1.21.11 on. ^/
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor { @Invoker("getUpdatingChunkIfPresent") ChunkHolder xray$updatingChunk(long pos); }
*///?}
