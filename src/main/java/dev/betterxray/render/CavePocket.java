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
 * Cave-Find: the air space the player is standing in. Underground, the player's own tunnel or
 * cave is "underground air" as well; its walls would be meshed opaque around the camera and hide
 * every other cave (and with X-Ray on, the ores behind them). Air reachable from the player's eye
 * is therefore not a cave: its walls follow the X-Ray opacity like the rest of the terrain.
 *
 * <p>Flood fill over passable blocks from the eye, bounded by {@link #RADIUS} and {@link #MAX_NODES}.
 * Only dark air (no sky light) is recorded, the only air {@link XRayState#caveFace} looks at.
 * When the pocket changes (moving, mining), the sections whose faces border the changed blocks
 * are rebuilt. The published set is never modified afterwards (read by meshing threads).
 */
public final class CavePocket {
    /** Chebyshev distance from the eye that the flood fill reaches. */
    private static final int RADIUS = 32;
    /** Visited blocks per flood fill (the surface or huge caves are cut off here). */
    private static final int MAX_NODES = 32768;
    /** Recomputed this often while standing still (mining, block updates). */
    private static final int REFRESH_TICKS = 5;
    /** More dirty sections than this rebuild everything instead. */
    private static final int MAX_DIRTY_SECTIONS = 512;

    private static volatile LongOpenHashSet pocket = new LongOpenHashSet();
    private static long lastEye = Long.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static int ticks;

    private CavePocket() {}

    /** {@code pos} is dark air connected to the player: not a cave for Cave-Find. */
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
        if (!moved && !fullRebuild && ++ticks < REFRESH_TICKS) return;
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
        markChanged(client, previous, next);
    }

    private static LongOpenHashSet flood(ClientLevel level, BlockPos eye) {
        LongOpenHashSet result = new LongOpenHashSet();
        BlockPos start = eye;
        if (!passable(level.getBlockState(start))) {
            start = eye.below();
            if (!passable(level.getBlockState(start))) return result;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        visited.add(start.asLong());
        queue.enqueue(start.asLong());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            pos.set(current);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() && level.getBrightness(LightLayer.SKY, pos) == 0) result.add(current);
            for (Direction direction : Direction.values()) {
                long neighbor = BlockPos.offset(current, direction);
                if (visited.contains(neighbor) || visited.size() >= MAX_NODES) continue;
                pos.set(neighbor);
                if (Math.abs(pos.getX() - eye.getX()) > RADIUS || Math.abs(pos.getY() - eye.getY()) > RADIUS
                    || Math.abs(pos.getZ() - eye.getZ()) > RADIUS || level.isOutsideBuildHeight(pos)) continue;
                if (!passable(level.getBlockState(pos))) continue;
                visited.add(neighbor);
                queue.enqueue(neighbor);
            }
        }
        return result;
    }

    /** The player could see or walk through it: air, fluids, torches, glass, plants. */
    private static boolean passable(BlockState state) {
        return state.isAir() || !state.canOcclude() || !state.getFluidState().isEmpty();
    }

    /** Rebuilds the sections holding the faces next to blocks that joined or left the pocket. */
    private static void markChanged(Minecraft client, LongOpenHashSet previous, LongOpenHashSet next) {
        LongOpenHashSet sections = new LongOpenHashSet();
        if (!collect(previous, next, sections) || !collect(next, previous, sections)) {
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

    /** Sections of the neighbors of every block in {@code a} but not in {@code b}; false when too many. */
    private static boolean collect(LongOpenHashSet a, LongOpenHashSet b, LongOpenHashSet sections) {
        for (LongIterator it = a.iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (b.contains(pos)) continue;
            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
            for (Direction direction : Direction.values()) {
                sections.add(SectionPos.asLong(SectionPos.blockToSectionCoord(x + direction.getStepX()),
                    SectionPos.blockToSectionCoord(y + direction.getStepY()),
                    SectionPos.blockToSectionCoord(z + direction.getStepZ())));
            }
            if (sections.size() > MAX_DIRTY_SECTIONS) return false;
        }
        return true;
    }
}
