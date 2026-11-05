package dev.betterxray.render;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Hide Mobs: Behind Walls": whether blocks stand between the viewer's eyes and a mob, i.e. the
 * mob is only seen because of the X-Ray view (in a cave below, behind a wall). Rays go to the
 * mob's eyes and to the middle of its hitbox; the mob counts as visible if either is free. Glass
 * and other blocks without a visual shape do not block. Results are kept for one game tick, so
 * every mob costs at most two ray casts per tick instead of per frame.
 */
public final class MobOcclusion {
    private static final Int2BooleanOpenHashMap CACHE = new Int2BooleanOpenHashMap();
    private static ClientLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;

    private MobOcclusion() {}

    public static boolean occluded(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Entity viewer = client.getCameraEntity();
        if (level == null || viewer == null || entity == viewer) return false;
        long tick = level.getGameTime();
        if (level != cachedLevel || tick != cachedTick) {
            CACHE.clear();
            cachedLevel = level;
            cachedTick = tick;
        }
        int id = entity.getId();
        if (CACHE.containsKey(id)) return CACHE.get(id);
        Vec3 eye = viewer.getEyePosition();
        boolean occluded = blocked(level, viewer, eye, entity.getEyePosition())
            && blocked(level, viewer, eye, entity.getBoundingBox().getCenter());
        CACHE.put(id, occluded);
        return occluded;
    }

    private static boolean blocked(ClientLevel level, Entity viewer, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer))
            .getType() != HitResult.Type.MISS;
    }
}
