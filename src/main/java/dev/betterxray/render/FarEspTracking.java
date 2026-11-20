package dev.betterxray.render;

import dev.betterxray.config.XRayConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * "Full view distance" for ESP targets (singleplayer, every target).
 *
 * <p>The client already draws ESP targets past the size-based entity render distance
 * ({@code MobEspExtractorMixin}, before 1.21.11 {@code MobEspVisibilityMixin}), but the server only sends an entity within its tracking range
 * ({@code EntityType.clientTrackingRange}: monsters 128 blocks, animals 160, items 96, scaled by
 * {@code entity-broadcast-range-percentage}), far less than a 20-chunk view distance. On the
 * integrated server this mod runs in the same JVM, so {@code FarEspTrackingMixin} raises that
 * range to the player's view distance for the singleplayer owner and entities with ESP. Other
 * players (LAN guests) keep the vanilla range; dedicated servers cannot be changed from the client.
 *
 * <p>Called on the server thread: reads only the config and {@link MobEspRenderer}'s thread-safe
 * caches, with the server entity and the owner's server player as viewer.
 */
public final class FarEspTracking {
    private FarEspTracking() {}

    /** Implemented by {@code ChunkMap.TrackedEntity} through {@code FarEspTrackingMixin}. */
    public interface Tracked {
        void betterxray$updatePlayer(ServerPlayer player);
    }

    public static boolean active() {
        var config = XRayConfigManager.get();
        return config.mobEspFullViewDistance && config.mobEspEnabled;
    }

    public static boolean isOwner(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        // 1.21.9 replaced the GameProfile parameter with NameAndId.
        //? if >=1.21.11 {
        return server.isSingleplayerOwner(player.nameAndId());
        //?} else
        /*return server.isSingleplayerOwner(player.getGameProfile());*/
    }

    /** True when {@code entity} should be tracked up to the view distance of {@code player}. */
    public static boolean extend(Entity entity, ServerPlayer player) {
        return active() && isOwner(player) && MobEspRenderer.settings(entity, player) != null;
    }
}
