package dev.betterxray.render;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
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
 * and other blocks without a visual shape do not block. A result is kept for {@link #KEEP_TICKS}
 * game ticks, so every mob costs at most two ray casts per that many ticks (mob farms).
 */
public final class MobOcclusion {
    /** Ticks a result stays valid; a mob stepping out from behind a wall shows up this much later at most. */
    private static final int KEEP_TICKS = 4;
    /** Results older than this are dropped (mobs that left or are no longer drawn). */
    private static final int FORGET_TICKS = 100;
    /** Per entity id: game tick of the ray casts shifted left by one, plus 1 when occluded. */
    private static final Int2LongOpenHashMap CACHE = new Int2LongOpenHashMap();
    private static ClientLevel cachedLevel;
    private static long lastPrune;

    private MobOcclusion() {}

    /** Forgets every result (world left). */
    public static void reset() {
        CACHE.clear();
        cachedLevel = null;
    }

    public static boolean occluded(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Entity viewer = client.getCameraEntity();
        if (level == null || viewer == null || entity == viewer) return false;
        long tick = level.getGameTime();
        if (level != cachedLevel) {
            CACHE.clear();
            cachedLevel = level;
            lastPrune = tick;
        } else if (tick - lastPrune >= FORGET_TICKS) {
            for (var it = CACHE.int2LongEntrySet().iterator(); it.hasNext(); )
                if (tick - (it.next().getLongValue() >> 1) >= FORGET_TICKS) it.remove();
            lastPrune = tick;
        }
        int id = entity.getId();
        if (CACHE.containsKey(id)) {
            long value = CACHE.get(id);
            long age = tick - (value >> 1);
            if (age >= 0 && age < KEEP_TICKS) return (value & 1) != 0;
        }
        Vec3 eye = viewer.getEyePosition();
        boolean occluded = blocked(level, viewer, eye, entity.getEyePosition())
            && blocked(level, viewer, eye, entity.getBoundingBox().getCenter());
        CACHE.put(id, tick << 1 | (occluded ? 1 : 0));
        return occluded;
    }
    private static boolean blocked(ClientLevel level, Entity viewer, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer))
            .getType() != HitResult.Type.MISS;
    }
}
