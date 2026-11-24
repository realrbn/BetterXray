package dev.betterxray.render;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cave-Find: the cave the player is standing in. Underground, the player's own tunnel or cave is
 * cave space as well; its walls would be meshed opaque around the camera and hide every other cave
 * (and with X-Ray on, the ores behind them). Cave space connected to the player's eye is therefore
 * not a cave: its walls follow the X-Ray opacity like the rest of the terrain.
 *
 * <p>Flood fill from the eye through dark space the player could move through (air, fluids, plants,
 * torches, rails; not glass, doors or fences, so a cave behind a window stays a cave), bounded by
 * {@link #RADIUS} and {@link #MAX_NODES}. On the surface (eye not dark) the pocket is empty, so caves
 * opening next to the player stay visible. Dark open cells next to the fill are recorded as well.
 * When the pocket changes (moving, mining), only sections holding faces toward changed cells are
 * rebuilt. The published set is never modified afterwards (read by meshing threads).
 */
public final class CavePocket {
    /** Chebyshev distance from the eye that the flood fill reaches. */
    private static final int RADIUS = 32;
    /** Visited blocks per flood fill (the surface or huge caves are cut off here). */
    private static final int MAX_NODES = 32768;
    /** Recomputed this often while standing still (mining, block updates). */
    private static final int REFRESH_TICKS = 5;
    /** At most one flood fill per this many ticks while moving (flying fast through caves). */
    private static final int MIN_INTERVAL_TICKS = 3;
    /** More dirty sections than this rebuild everything instead. */
    private static final int MAX_DIRTY_SECTIONS = 512;

    private static volatile LongOpenHashSet pocket = new LongOpenHashSet();
    private static long lastEye = Long.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static int ticks;

    private CavePocket() {}

    /** {@code pos} is dark open space connected to the player: not a cave for Cave-Find. */
    public static boolean contains(BlockPos pos) {
        return pocket.contains(pos.asLong());
    }

    /**
     * Client tick, before a pending full rebuild. {@code fullRebuild}: the meshes are rebuilt
     * anyway, no sections need to be marked.
     */
    public static void tick(Minecraft client, boolean fullRebuild) {
        ClientLevel level = client.level;
        if (level == null || client.player == null || !XRayState.caveFind()) {
            if (!pocket.isEmpty()) pocket = new LongOpenHashSet();
            lastEye = Long.MIN_VALUE;
            lastLevel = null;
            return;
        }
        BlockPos eye = BlockPos.containing(client.player.getEyePosition());
        boolean moved = eye.asLong() != lastEye || level != lastLevel;
        ticks++;
        if (!fullRebuild && level == lastLevel && ticks < (moved ? MIN_INTERVAL_TICKS : REFRESH_TICKS)) return;
        ticks = 0;
        boolean sameLevel = level == lastLevel;
        lastEye = eye.asLong();
        lastLevel = level;

        LongOpenHashSet next = flood(level, eye);
        LongOpenHashSet previous = pocket;
        if (next.equals(previous)) return;
        pocket = next;
        if (fullRebuild || !sameLevel) {
            if (!sameLevel) XRayRenderController.requestRebuild();
            return;
        }
        markChanged(client, level, previous, next);
    }

    private static LongOpenHashSet flood(ClientLevel level, BlockPos eye) {
        LongOpenHashSet result = new LongOpenHashSet();
        BlockPos start = eye;
        if (!dark(level, start) || !traversable(level, start)) {
            start = eye.below();
            if (!dark(level, start) || !traversable(level, start)) return result;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        visited.add(start.asLong());
        result.add(start.asLong());
        queue.enqueue(start.asLong());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            long current = queue.dequeueLong();
            for (Direction direction : Direction.values()) {
                long neighbor = BlockPos.offset(current, direction);
                if (!visited.add(neighbor)) continue;
                pos.set(neighbor);
                if (Math.abs(pos.getX() - eye.getX()) > RADIUS || Math.abs(pos.getY() - eye.getY()) > RADIUS
                    || Math.abs(pos.getZ() - eye.getZ()) > RADIUS || level.isOutsideBuildHeight(pos)) continue;
                if (!dark(level, pos)) continue;
                result.add(neighbor);
                if (traversable(level, pos)) queue.enqueue(neighbor);
            }
        }
        return result;
    }

    /** Open (does not fill its cell) and dark enough to be cave space. */
    private static boolean dark(ClientLevel level, BlockPos pos) {
        return XRayState.openForCave(level, pos, level.getBlockState(pos))
            && level.getBrightness(LightLayer.SKY, pos) <= XRayState.CAVE_SKY_LIGHT;
    }

    /** The player could move through it: no collision (air, plants, torches, rails) or a fluid. */
    private static boolean traversable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty() || state.getCollisionShape(level, pos).isEmpty();
    }

    /** Rebuilds the sections holding faces toward cells that joined or left the pocket. */
    private static void markChanged(Minecraft client, ClientLevel level, LongOpenHashSet previous, LongOpenHashSet next) {
        LongOpenHashSet sections = new LongOpenHashSet();
        if (!collect(level, previous, next, sections) || !collect(level, next, previous, sections)) {
            XRayRenderController.requestRebuild();
            return;
        }
        for (LongIterator it = sections.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            int x = SectionPos.x(section), y = SectionPos.y(section), z = SectionPos.z(section);
            //? if >=26.1 {
            client.levelExtractor.setSectionDirty(x, y, z);
            //?} else {
            /*client.levelRenderer.setSectionDirty(x, y, z);
            *///?}
        }
    }

    /**
     * Sections of the non-air neighbors (the blocks with faces toward the cell) of every cell in
     * {@code a} but not in {@code b}; false when too many. Changes in open air touch no section.
     */
    private static boolean collect(ClientLevel level, LongOpenHashSet a, LongOpenHashSet b, LongOpenHashSet sections) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (LongIterator it = a.iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (b.contains(pos)) continue;
            for (Direction direction : Direction.values()) {
                neighbor.set(BlockPos.offset(pos, direction));
                if (level.getBlockState(neighbor).isAir()) continue;
                sections.add(SectionPos.asLong(SectionPos.blockToSectionCoord(neighbor.getX()),
                    SectionPos.blockToSectionCoord(neighbor.getY()), SectionPos.blockToSectionCoord(neighbor.getZ())));
            }
            if (sections.size() > MAX_DIRTY_SECTIONS) return false;
        }
        return true;
    }
}
