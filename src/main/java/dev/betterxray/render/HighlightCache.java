package dev.betterxray.render;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.BlockSelection;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.debug.DebugMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
//?}
//? if >=1.21.11 {
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
*///?}
//? if >=1.20.5 {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?} else {
/*import net.minecraft.world.level.chunk.ChunkStatus;
*///?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Client-thread index of highlighted blocks, per 16³ section. Only sections around the camera
 * are scanned: within the highlight range plus {@link #SCAN_MARGIN}, or every section of the
 * chunks within the render distance when the range follows it. Scans happen on ticks, never in
 * the render pass.
 */
public final class HighlightCache {
    private static ClientLevel world;
    /** Loaded chunks by packed chunk position. */
    private static final Long2ObjectMap<LevelChunk> loaded = new Long2ObjectOpenHashMap<>();
    /** Packed positions of the sections scanned for the current selection; only these are kept up to date. */
    private static final LongSet scanned = new LongOpenHashSet();
    /** Highlighted positions per scanned section that contains any, with the block found there. */
    private static final Long2ObjectMap<Long2ObjectMap<Block>> positions = new Long2ObjectOpenHashMap<>();
    /** Sections still to scan, nearest first. An entry no longer in {@link #queued} was dropped and is skipped. */
    private static final LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
    private static final LongSet queued = new LongOpenHashSet();

    /**
     * Sections are scanned up to this many blocks beyond the highlight range, so veins at its edge
     * stay whole and the camera can move within its section without leaving the indexed area.
     * Indexed sections are dropped only beyond twice the margin, so walking back and forth across
     * the edge does not rescan them.
     */
    private static final double SCAN_MARGIN = 16.0;
    /** Scan time per tick; at least one section is scanned. */
    private static final long SCAN_NANOS = 2_000_000L;
    /** Camera position and settings the indexed area was last computed for. */
    private static boolean regionValid;
    private static long regionAnchor;
    private static Vec3 regionEye = Vec3.ZERO;
    private static boolean regionLimited;
    private static int regionView;
    private static double regionRange;
    private static Set<String> configured = Set.of();
    private static Set<Block> blocks = Set.of();
    /** Block a highlighted block counts as within a vein: deepslate ores join their stone variant. */
    private static Map<Block, Block> veinKinds = Map.of();
    private static Map<String, Integer> configuredColors = Map.of();
    private static int tagCheckTicks;
    /** Own highlight colors of selected blocks; every other block uses the global color. */
    private static Map<Block, Integer> blockColors = Map.of();
    private static boolean active;
    private static int lastDrawnBoxes;
    private static int lastDrawnTracers;
    /** Sections scanned and time spent scanning in the last tick; longest scan tick in this world. */
    private static int lastTickSections;
    private static long lastTickNanos;
    private static long maxTickNanos;
    private static int veinRebuilds;
    private static long lastVeinNanos;

    /**
     * Adjacent highlighted blocks of the same kind (sharing a face, edge or corner), drawn as one
     * box and one tracer.
     *
     * @param block        kind of the vein; the stone variant when deepslate ores are mixed in
     * @param bounds       box around all of its blocks
     * @param tracerTarget center of the block closest to the vein's centroid, always inside the vein
     */
    public record Vein(Block block, int count, AABB bounds, Vec3 tracerTarget) {}

    /** Veins of the current index, rebuilt on ticks; empty while vein grouping is off. */
    private static List<Vein> veins = List.of();
    private static boolean veinsDirty;
    private static int ticksSinceVeins;
    /** While sections are still being scanned, veins are rebuilt at most this often. */
    private static final int VEIN_REBUILD_TICKS = 10;
    /**
     * Otherwise at most this often, so mining or placing highlighted blocks in quick succession
     * does not rebuild every tick. A single change after a quiet period rebuilds on the next tick.
     */
    private static final int VEIN_MIN_REBUILD_TICKS = 4;
    /** Set by setting changes (selection, colors, grouping, switch): rebuild on the next tick. */
    private static boolean veinsForced;

    private HighlightCache() {}

    // ChunkPos became a record with x()/z()/pack() in 26.1.
    private static int chunkX(ChunkPos pos) {
        //? if >=26.1 {
        return pos.x();
        //?} else {
        /*return pos.x;
        *///?}
    }

    private static int chunkZ(ChunkPos pos) {
        //? if >=26.1 {
        return pos.z();
        //?} else {
        /*return pos.z;
        *///?}
    }

    private static long pack(ChunkPos pos) {
        //? if >=26.1 {
        return pos.pack();
        //?} else {
        /*return pos.toLong();
        *///?}
    }

    private static long chunkKey(int x, int z) {
        return pack(new ChunkPos(x, z));
    }

    private static long sectionKey(BlockPos pos) {
        return SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    private static int minY(ClientLevel level) {
        //? if >=1.21.2 {
        return level.getMinY();
        //?} else {
        /*return level.getMinBuildHeight();
        *///?}
    }

    private static int maxY(ClientLevel level) {
        //? if >=1.21.2 {
        return level.getMaxY();
        //?} else {
        /*return level.getMaxBuildHeight() - 1;
        *///?}
    }

    /** Lowest / highest section Y of the current world. */
    private static int minSection() { return minY(world) >> 4; }
    private static int maxSection() { return maxY(world) >> 4; }

    private static void ensureWorld(ClientLevel level) {
        if (world == level) return;
        world = level;
        loaded.clear();
        clearIndex();
        active = false;
        veins = List.of();
        veinsDirty = true;
        maxTickNanos = 0;
        veinRebuilds = 0;
        lastVeinNanos = 0;
        debugBoxes = List.of();
    }

    /** Forgets every scanned section; the indexed area is computed again on the next tick. */
    private static void clearIndex() {
        scanned.clear();
        positions.clear();
        pending.clear();
        queued.clear();
        regionValid = false;
    }

    public static void loaded(ClientLevel level, ChunkPos pos) {
        ensureWorld(level);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX(pos), chunkZ(pos), ChunkStatus.FULL, false);
        if (chunk == null) return;
        long key = pack(pos);
        loaded.put(key, chunk);
        // New chunk data replaces whatever was indexed for this column.
        forgetSections(chunkX(pos), chunkZ(pos));
        if (!active || !regionValid) return;
        LongArrayList missing = new LongArrayList();
        addMissingSections(chunkX(pos), chunkZ(pos), missing);
        long[] sections = nearestFirst(missing);
        ChunkPos camera = new ChunkPos(Mth.floor(regionEye.x) >> 4, Mth.floor(regionEye.z) >> 4);
        if (distanceSquared(key, camera) <= 16) {
            for (int i = sections.length - 1; i >= 0; i--) enqueueFirst(sections[i]);
        } else {
            for (long section : sections) enqueueLast(section);
        }
    }

    public static void unloaded(ClientLevel level, LevelChunk chunk) {
        if (world != level) return;
        loaded.remove(pack(chunk.getPos()));
        forgetSections(chunkX(chunk.getPos()), chunkZ(chunk.getPos()));
    }

    /** Drops every section of a chunk column from the index and the scan queue. */
    private static void forgetSections(int chunkX, int chunkZ) {
        for (int y = minSection(), top = maxSection(); y <= top; y++) {
            long section = SectionPos.asLong(chunkX, y, chunkZ);
            scanned.remove(section);
            queued.remove(section);
            if (positions.remove(section) != null) veinsDirty = true;
        }
    }

    public static void changed(ClientLevel level, BlockPos pos) {
        if (world != level) return;
        long section = sectionKey(pos);
        // Outside the indexed area, or a pending scan will pick up the change.
        if (!scanned.contains(section)) return;
        long packed = pos.asLong();
        Block block = level.getBlockState(pos).getBlock();
        Block now = blocks.contains(block) ? block : null;
        Long2ObjectMap<Block> found = positions.get(section);
        Block before;
        if (now != null) {
            if (found == null) positions.put(section, found = new Long2ObjectOpenHashMap<>());
            before = found.put(packed, now);
        } else {
            before = found == null ? null : found.remove(packed);
            if (found != null && found.isEmpty()) positions.remove(section);
        }
        if (before != now) veinsDirty = true;
    }

    /** True if the position is currently indexed as highlighted (used by tests). */
    public static boolean isIndexed(BlockPos pos) {
        Long2ObjectMap<Block> found = positions.get(sectionKey(pos));
        return found != null && found.containsKey(pos.asLong());
    }

    /** True if the section containing the position has been scanned (used by tests). */
    public static boolean isScanned(BlockPos pos) {
        return scanned.contains(sectionKey(pos));
    }

    /** Number of highlighted positions currently indexed (used by tests). */
    public static int indexedCount() {
        int count = 0;
        for (var found : positions.values()) count += found.size();
        return count;
    }

    /** Veins of the current index (used by tests); empty while vein grouping is off. */
    public static List<Vein> veins() { return veins; }

    /** Boxes / tracers submitted in the last frame (used by tests). */
    public static int lastDrawnBoxes() { return lastDrawnBoxes; }
    public static int lastDrawnTracers() { return lastDrawnTracers; }

    /** Highlight color of a block (opaque ARGB): its own color or the global one. */
    public static int color(Block block) {
        Integer own = blockColors.get(block);
        return own != null ? own : XRayConfigManager.get().highlightColor | 0xff000000;
    }

    private static boolean groupVeins() {
        return XRayConfigManager.get().groupVeins;
    }

    public static void tick(Minecraft client) {
        update(client);
        if (!DebugMode.enabled() || client.level == null) {
            debugBoxes = List.of();
            return;
        }
        if (++debugTicks < DEBUG_TICKS) return;
        debugTicks = 0;
        checkInvariants(client);
        debugBoxes = DebugMode.bounds() ? buildDebugBoxes(client) : List.of();
    }

    private static void update(Minecraft client) {
        if (client.level == null) {
            ensureWorld(null);
            return;
        }
        ensureWorld(client.level);
        // The live config collections are compared every tick and only copied when they changed.
        Set<String> requested = XRayConfigManager.get().highlightBlocks;
        boolean changed = !configured.equals(requested);
        Map<String, Integer> requestedColors = XRayConfigManager.get().highlightBlockColors;
        boolean recolor = false;
        if (++tagCheckTicks >= BlockSelection.TAG_CHECK_TICKS) {
            tagCheckTicks = 0;
            // Selected tags are bound on world join and change on data pack reloads.
            changed |= BlockSelection.hasTags(requested) && !BlockSelection.resolve(requested, null).equals(blocks);
            recolor = BlockSelection.hasTags(requestedColors.keySet()) && !resolveColors(requestedColors).equals(blockColors);
        }
        if (changed || recolor || !configuredColors.equals(requestedColors)) {
            configuredColors = Map.copyOf(requestedColors);
            blockColors = resolveColors(configuredColors);
            if (!changed) {
                // Same selection: only deepslate/stone pairs may have to split or join.
                veinKinds = veinKinds(blocks);
                veinsDirty = true;
                veinsForced = true;
            }
        }
        if (changed) {
            configured = Set.copyOf(requested);
            blocks = Set.copyOf(BlockSelection.resolve(configured, null));
            veinKinds = veinKinds(blocks);
            clearIndex();
            veinsDirty = true;
            veinsForced = true;
        }
        boolean enabled = XRayConfigManager.get().highlightEnabled && !blocks.isEmpty();
        if (!enabled) {
            active = false;
            clearIndex();
            veins = List.of();
            veinsDirty = true;
            veinsForced = true;
            return;
        }
        updateRegion(client, !active);
        active = true;
        // Nearest sections first. The time cap protects FPS when a selected block is very common.
        long start = System.nanoTime();
        int scannedNow = 0;
        while (!pending.isEmpty()) {
            long section = pending.dequeueLong();
            if (!queued.remove(section)) continue; // Dropped after it was queued.
            LevelChunk chunk = loaded.get(chunkKey(SectionPos.x(section), SectionPos.z(section)));
            if (chunk != null) {
                scan(section, chunk);
                scannedNow++;
            }
            if (System.nanoTime() - start >= SCAN_NANOS) break;
        }
        lastTickSections = scannedNow;
        lastTickNanos = System.nanoTime() - start;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
        if (!groupVeins()) {
            veins = List.of();
            veinsDirty = true;
            veinsForced = true;
            return;
        }
        if (ticksSinceVeins < VEIN_REBUILD_TICKS) ticksSinceVeins++;
        int interval = pending.isEmpty() ? VEIN_MIN_REBUILD_TICKS : VEIN_REBUILD_TICKS;
        if (veinsDirty && (veinsForced || ticksSinceVeins >= interval)) {
            long veinStart = System.nanoTime();
            veins = buildVeins();
            lastVeinNanos = System.nanoTime() - veinStart;
            veinRebuilds++;
            veinsDirty = false;
            veinsForced = false;
            ticksSinceVeins = 0;
        }
    }

    /** Highlight colors per block; a block's own color wins over the color of a tag that contains it. */
    private static Map<Block, Integer> resolveColors(Map<String, Integer> colors) {
        Map<Block, Integer> resolved = new HashMap<>();
        for (var entry : colors.entrySet()) {
            if (BlockSelection.isTag(entry.getKey())) continue;
            Block block = Mc.block(entry.getKey());
            if (block != null) resolved.put(block, entry.getValue() | 0xff000000);
        }
        for (var entry : colors.entrySet()) {
            if (!BlockSelection.isTag(entry.getKey())) continue;
            for (Block block : BlockSelection.members(entry.getKey())) resolved.putIfAbsent(block, entry.getValue() | 0xff000000);
        }
        return Map.copyOf(resolved);
    }

    /**
     * Maps {@code ns:deepslate_x} to {@code ns:x} when that block exists and both share a highlight
     * color, every other block to itself.
     */
    private static Map<Block, Block> veinKinds(Set<Block> selected) {
        Map<Block, Block> kinds = new HashMap<>();
        for (Block block : selected) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            Block kind = id.getPath().startsWith("deepslate_")
                ? Mc.block(id.getNamespace() + ":" + id.getPath().substring("deepslate_".length())) : null;
            boolean sameColor = kind != null && java.util.Objects.equals(blockColors.get(kind), blockColors.get(block));
            kinds.put(block, sameColor ? kind : block);
        }
        return Map.copyOf(kinds);
    }

    private static Block veinKind(Block block) {
        return veinKinds.getOrDefault(block, block);
    }

    /** Flood fill over all indexed positions, across chunk borders; neighbors share a face, edge or corner. */
    private static List<Vein> buildVeins() {
        Long2ObjectOpenHashMap<Block> all = new Long2ObjectOpenHashMap<>(indexedCount());
        for (var found : positions.values()) all.putAll(found);
        LongOpenHashSet visited = new LongOpenHashSet(all.size());
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongArrayList members = new LongArrayList();
        List<Vein> result = new ArrayList<>();
        for (var entry : Long2ObjectMaps.fastIterable(all)) {
            long first = entry.getLongKey();
            if (!visited.add(first)) continue;
            Block kind = veinKind(entry.getValue());
            members.clear();
            queue.enqueue(first);
            while (!queue.isEmpty()) {
                long packed = queue.dequeueLong();
                members.add(packed);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    long next = BlockPos.offset(packed, dx, dy, dz);
                    Block block = all.get(next);
                    if (block != null && veinKind(block) == kind && visited.add(next)) queue.enqueue(next);
                }
            }
            result.add(vein(kind, members));
        }
        return List.copyOf(result);
    }

    private static Vein vein(Block kind, LongArrayList members) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = members.size();
        for (int i = 0; i < count; i++) {
            long packed = members.getLong(i);
            int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            sumX += x; sumY += y; sumZ += z;
        }
        double cx = sumX / count, cy = sumY / count, cz = sumZ / count;
        long closest = members.getLong(0);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            long packed = members.getLong(i);
            double dx = BlockPos.getX(packed) - cx, dy = BlockPos.getY(packed) - cy, dz = BlockPos.getZ(packed) - cz;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) { best = distance; closest = packed; }
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(0.0125);
        return new Vein(kind, count, bounds, Vec3.atCenterOf(BlockPos.of(closest)));
    }

    private static long distanceSquared(long packed, ChunkPos center) {
        long dx = (long)ChunkPos.getX(packed) - chunkX(center);
        long dz = (long)ChunkPos.getZ(packed) - chunkZ(center);
        return dx * dx + dz * dz;
    }

    private static void enqueueFirst(long section) {
        if (queued.add(section)) pending.enqueueFirst(section);
    }

    private static void enqueueLast(long section) {
        if (queued.add(section)) pending.enqueue(section);
    }

    private static Vec3 cameraEye(Minecraft client) {
        Entity camera = client.getCameraEntity();
        if (camera == null) camera = client.player;
        return camera == null ? Vec3.ZERO : camera.getEyePosition();
    }

    /**
     * Recomputes the indexed area when the camera enters another section (another chunk while the
     * range follows the render distance) or the range or render distance changes: drops sections
     * that left it and queues the missing ones, nearest first.
     */
    private static void updateRegion(Minecraft client, boolean force) {
        var config = XRayConfigManager.get();
        int renderDistance = client.options.renderDistance().get();
        boolean limited = config.highlightRangeLimited(renderDistance);
        int view = Math.max(1, renderDistance);
        double range = config.getHighlightRangeBlocks(renderDistance);
        Vec3 eye = cameraEye(client);
        // Without a range limit whole columns are indexed, so only chunk borders matter.
        long anchor = SectionPos.asLong(Mth.floor(eye.x) >> 4, limited ? Mth.floor(eye.y) >> 4 : 0, Mth.floor(eye.z) >> 4);
        if (!force && regionValid && anchor == regionAnchor && limited == regionLimited
            && view == regionView && range == regionRange) return;
        regionValid = true;
        regionAnchor = anchor;
        regionEye = eye;
        regionLimited = limited;
        regionView = view;
        regionRange = range;
        for (LongIterator it = scanned.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            if (inIndexedArea(section, 1, 2 * SCAN_MARGIN)) continue;
            it.remove();
            if (positions.remove(section) != null) veinsDirty = true;
        }
        pending.clear();
        queued.clear();
        LongArrayList missing = new LongArrayList();
        for (LongIterator it = loaded.keySet().iterator(); it.hasNext(); ) {
            long chunk = it.nextLong();
            addMissingSections(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), missing);
        }
        for (long section : nearestFirst(missing)) enqueueLast(section);
    }

    /**
     * True if a section lies within the render distance plus {@code chunkSlack} chunks and, with a
     * range limit, within the range plus {@code margin} blocks of the camera.
     */
    private static boolean inIndexedArea(long section, int chunkSlack, double margin) {
        int cx = Mth.floor(regionEye.x) >> 4, cz = Mth.floor(regionEye.z) >> 4;
        if (Math.abs(SectionPos.x(section) - cx) > regionView + chunkSlack
            || Math.abs(SectionPos.z(section) - cz) > regionView + chunkSlack) return false;
        double reach = regionRange + margin;
        return !regionLimited || sectionDistanceSquared(section) <= reach * reach;
    }

    /** Sections of a chunk column that belong in the index but are neither scanned nor queued. */
    private static void addMissingSections(int chunkX, int chunkZ, LongArrayList out) {
        int bottom = minSection(), top = maxSection();
        if (regionLimited) {
            double reach = regionRange + SCAN_MARGIN;
            bottom = Math.max(bottom, Mth.floor(regionEye.y - reach) >> 4);
            top = Math.min(top, Mth.floor(regionEye.y + reach) >> 4);
        }
        for (int y = bottom; y <= top; y++) {
            long section = SectionPos.asLong(chunkX, y, chunkZ);
            if (!scanned.contains(section) && !queued.contains(section) && inIndexedArea(section, 0, SCAN_MARGIN))
                out.add(section);
        }
    }

    /** Squared distance from the camera the indexed area was computed for to the closest point of a section. */
    private static double sectionDistanceSquared(long section) {
        return sectionDistanceSquared(regionEye, section);
    }

    private static double sectionDistanceSquared(Vec3 eye, long section) {
        double dx = axisDistance(eye.x, SectionPos.x(section));
        double dy = axisDistance(eye.y, SectionPos.y(section));
        double dz = axisDistance(eye.z, SectionPos.z(section));
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double eye, int sectionCoord) {
        double min = sectionCoord * 16.0;
        return Math.max(0.0, Math.max(min - eye, eye - (min + 16.0)));
    }

    private static long[] nearestFirst(LongArrayList sections) {
        long[] sorted = sections.toLongArray();
        LongArrays.quickSort(sorted, (a, b) -> Double.compare(sectionDistanceSquared(a), sectionDistanceSquared(b)));
        return sorted;
    }

    private static void scan(long section, LevelChunk chunk) {
        scanned.add(section);
        Long2ObjectMap<Block> found = null;
        LevelChunkSection[] sections = chunk.getSections();
        int index = SectionPos.y(section) - minSection();
        if (index >= 0 && index < sections.length) {
            LevelChunkSection blockSection = sections[index];
            if (!blockSection.hasOnlyAir() && blockSection.maybeHas(state -> blocks.contains(state.getBlock()))) {
                int baseX = SectionPos.x(section) * 16, baseY = SectionPos.y(section) * 16, baseZ = SectionPos.z(section) * 16;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    Block block = blockSection.getBlockState(x, y, z).getBlock();
                    if (!blocks.contains(block)) continue;
                    if (found == null) found = new Long2ObjectOpenHashMap<>();
                    found.put(BlockPos.asLong(baseX + x, baseY + y, baseZ + z), block);
                }
            }
        }
        var before = found == null ? positions.remove(section) : positions.put(section, found);
        if (found != null || before != null) veinsDirty = true;
    }

    /** Receives what one frame draws; the backends differ per Minecraft generation (also used by {@link StructureFinder}). */
    interface Sink {
        void box(AABB box, int color);
        void tracer(Vec3 target, int color);
        /** A line between two world points (debug drawing). */
        void line(Vec3 from, Vec3 to, int color);
    }

    // ---- debug mode ----

    /** Ticks between invariant checks and rebuilds of the drawn scan area. */
    private static final int DEBUG_TICKS = 10;
    /**
     * How far the camera may be from the eye the indexed area was computed for without the area
     * being recomputed: anywhere within the same section, so up to its diagonal.
     */
    private static final double ANCHOR_SLACK = 16.0 * Math.sqrt(3.0);
    private static final int ZONE_RANGE = 0, ZONE_MARGIN = 1, ZONE_OUTSIDE = 2;
    private static final int[] ZONE_COLORS = {0xff40ff40, 0xffffd040, 0xffff3030};
    private static final int RANGE_COLOR = 0xff40e0ff, REACH_COLOR = 0xff4060ff;
    /** Sections waiting for their scan, and the ESP range. */
    private static final int QUEUED_COLOR = 0xffffffff, ESP_RANGE_COLOR = 0xffff8020;
    /** Most section boxes drawn in {@link DebugMode#sections()} mode (nearest first) and of queued ones. */
    private static final int MAX_DEBUG_SECTIONS = 4096, MAX_DEBUG_QUEUED = 1024;
    private static int debugTicks;

    /** A drawn piece of the index: a chunk column, a single section or a queued section. */
    private record DebugBox(AABB box, int color) {}

    private static volatile List<DebugBox> debugBoxes = List.of();

    /**
     * Index state for {@code /betterxray debug stats} and tests.
     *
     * @param farthestScanned distance from the camera to the closest point of the farthest scanned section
     * @param farthestAllowed farthest distance a scanned section may have with a range limit
     *                        (range, scan margin and keep zone), or -1 when the render distance limits
     * @param outsideSections scanned sections outside every area the index may cover (always 0 unless broken)
     * @param veinRebuilds    veins built since the world was joined
     */
    public record Stats(boolean active, boolean limited, double range, int viewChunks, int scannedSections,
                        int queuedSections, int sectionsWithHits, int indexedPositions, int veins,
                        double farthestScanned, double farthestAllowed, int outsideSections, int lastTickSections,
                        long lastTickNanos, long maxTickNanos, int veinRebuilds, long lastVeinNanos,
                        int drawnBoxes, int drawnTracers) {}

    public static Stats stats(Minecraft client) {
        var config = XRayConfigManager.get();
        int renderDistance = client.options.renderDistance().get();
        int view = Math.max(1, renderDistance);
        boolean limited = config.highlightRangeLimited(renderDistance);
        double range = config.getHighlightRangeBlocks(renderDistance);
        Vec3 eye = cameraEye(client);
        double farthest = 0;
        int outside = 0;
        for (LongIterator it = scanned.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            farthest = Math.max(farthest, sectionDistanceSquared(eye, section));
            if (zone(section, eye, view, limited, range) == ZONE_OUTSIDE) outside++;
        }
        return new Stats(active, limited, range, view, scanned.size(), queued.size(), positions.size(), indexedCount(),
            veins.size(), Math.sqrt(farthest), limited ? range + 2 * SCAN_MARGIN + ANCHOR_SLACK : -1, outside,
            lastTickSections, lastTickNanos, maxTickNanos, veinRebuilds, lastVeinNanos, lastDrawnBoxes, lastDrawnTracers);
    }

    /**
     * Where a section lies for a camera at {@code eye}, measured independently of the cached
     * indexed area: within the highlight range, within the scan margin or the zone where indexed
     * sections are kept, or outside every area the index may cover.
     */
    private static int zone(long section, Vec3 eye, int view, boolean limited, double range) {
        int cx = Mth.floor(eye.x) >> 4, cz = Mth.floor(eye.z) >> 4;
        int chunks = Math.max(Math.abs(SectionPos.x(section) - cx), Math.abs(SectionPos.z(section) - cz));
        // Kept sections may be one chunk beyond the render distance (see updateRegion).
        if (chunks > view + 1) return ZONE_OUTSIDE;
        if (!limited) return chunks > view ? ZONE_MARGIN : ZONE_RANGE;
        double distance = Math.sqrt(sectionDistanceSquared(eye, section));
        if (distance <= range) return chunks > view ? ZONE_MARGIN : ZONE_RANGE;
        return distance <= range + 2 * SCAN_MARGIN + ANCHOR_SLACK ? ZONE_MARGIN : ZONE_OUTSIDE;
    }

    private static String describe(long section, Vec3 eye) {
        return "section " + SectionPos.x(section) + "," + SectionPos.y(section) + "," + SectionPos.z(section)
            + " at " + Math.round(Math.sqrt(sectionDistanceSquared(eye, section))) + " blocks";
    }

    /**
     * Checks the index against the current camera and settings. Every check recomputes what it
     * expects from the configuration instead of trusting the cached region, so a stale or wrong
     * region shows up as a violation.
     */
    private static void checkInvariants(Minecraft client) {
        var config = XRayConfigManager.get();
        int renderDistance = client.options.renderDistance().get();
        int view = Math.max(1, renderDistance);
        boolean limited = config.highlightRangeLimited(renderDistance);
        double range = config.getHighlightRangeBlocks(renderDistance);
        Vec3 eye = cameraEye(client);
        String setting = " (range " + (limited ? Math.round(range) + " blocks" : "render distance") + ", view " + view + " chunks)";
        if (!active) {
            if (!scanned.isEmpty() || !positions.isEmpty() || !queued.isEmpty())
                DebugMode.violation("highlight.index_while_off", scanned.size() + " scanned, " + queued.size()
                    + " queued sections while highlighting is off");
            return;
        }
        for (LongIterator it = scanned.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            if (zone(section, eye, view, limited, range) != ZONE_OUTSIDE) continue;
            DebugMode.violation("highlight.scan_outside_range", "scanned " + describe(section, eye) + setting);
            break;
        }
        for (LongIterator it = queued.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            if (zone(section, eye, view, limited, range) != ZONE_OUTSIDE) continue;
            DebugMode.violation("highlight.queue_outside_range", "queued " + describe(section, eye) + setting);
            break;
        }
        for (var entry : Long2ObjectMaps.fastIterable(positions)) {
            long section = entry.getLongKey();
            if (!scanned.contains(section)) {
                DebugMode.violation("highlight.positions_not_scanned", "positions kept for unscanned " + describe(section, eye));
                break;
            }
            for (var found : Long2ObjectMaps.fastIterable(entry.getValue())) {
                BlockPos pos = BlockPos.of(found.getLongKey());
                if (sectionKey(pos) != section)
                    DebugMode.violation("highlight.position_wrong_section", pos.toShortString() + " filed under " + describe(section, eye));
                else if (!blocks.contains(found.getValue()))
                    DebugMode.violation("highlight.unselected_block", BuiltInRegistries.BLOCK.getKey(found.getValue())
                        + " at " + pos.toShortString() + " is indexed but not selected");
            }
        }
        if (!groupVeins() && !veins.isEmpty())
            DebugMode.violation("highlight.veins_while_ungrouped", veins.size() + " veins while Group Veins is off");
    }

    /**
     * The drawn index: per chunk column a box from its lowest to its highest scanned section,
     * colored by the farthest {@link #zone} among them, plus every section outside all allowed
     * areas on its own. In {@link DebugMode#sections()} mode every scanned section gets its own
     * box instead (the nearest {@value #MAX_DEBUG_SECTIONS}). Queued sections are drawn white.
     */
    private static List<DebugBox> buildDebugBoxes(Minecraft client) {
        var config = XRayConfigManager.get();
        int renderDistance = client.options.renderDistance().get();
        int view = Math.max(1, renderDistance);
        boolean limited = config.highlightRangeLimited(renderDistance);
        double range = config.getHighlightRangeBlocks(renderDistance);
        Vec3 eye = cameraEye(client);
        List<DebugBox> result = new ArrayList<>();
        if (DebugMode.sections()) {
            LongArrayList sorted = new LongArrayList(scanned);
            sorted.sort((long a, long b) -> Double.compare(sectionDistanceSquared(eye, a), sectionDistanceSquared(eye, b)));
            for (int i = 0; i < Math.min(sorted.size(), MAX_DEBUG_SECTIONS); i++) {
                long section = sorted.getLong(i);
                result.add(new DebugBox(sectionBox(section, 0.5), ZONE_COLORS[zone(section, eye, view, limited, range)]));
            }
        } else {
            // Per column: lowest section, highest section, zone.
            Long2ObjectOpenHashMap<int[]> columns = new Long2ObjectOpenHashMap<>();
            for (LongIterator it = scanned.iterator(); it.hasNext(); ) {
                long section = it.nextLong();
                int y = SectionPos.y(section), zone = zone(section, eye, view, limited, range);
                if (zone == ZONE_OUTSIDE) result.add(new DebugBox(sectionBox(section, 0.5), ZONE_COLORS[ZONE_OUTSIDE]));
                int[] column = columns.computeIfAbsent(chunkKey(SectionPos.x(section), SectionPos.z(section)), k -> new int[] {y, y, zone});
                column[0] = Math.min(column[0], y);
                column[1] = Math.max(column[1], y);
                column[2] = Math.max(column[2], zone);
            }
            for (var entry : Long2ObjectMaps.fastIterable(columns)) {
                double x = ChunkPos.getX(entry.getLongKey()) * 16.0, z = ChunkPos.getZ(entry.getLongKey()) * 16.0;
                int[] column = entry.getValue();
                AABB box = new AABB(x, column[0] * 16.0, z, x + 16, column[1] * 16.0 + 16, z + 16).inflate(-0.25);
                result.add(new DebugBox(box, ZONE_COLORS[column[2]]));
            }
        }
        int queuedBoxes = 0;
        for (LongIterator it = queued.iterator(); it.hasNext() && queuedBoxes < MAX_DEBUG_QUEUED; queuedBoxes++)
            result.add(new DebugBox(sectionBox(it.nextLong(), 2.0), QUEUED_COLOR));
        return List.copyOf(result);
    }

    /** Box of a section, shrunk by {@code inset} on every side so neighbors stay apart. */
    private static AABB sectionBox(long section, double inset) {
        double x = SectionPos.x(section) * 16.0, y = SectionPos.y(section) * 16.0, z = SectionPos.z(section) * 16.0;
        return new AABB(x, y, z, x + 16, y + 16, z + 16).inflate(-inset);
    }

    /**
     * Debug drawing: the index (see {@link #buildDebugBoxes}: green within the range, yellow in
     * the scan margin, red outside every allowed area, white queued), the highlight range as a
     * wireframe sphere with the scan reach as a second hull (or, without a range limit, the
     * render distance as squares), the ESP range and the Structure Finder's scan area.
     */
    private static void drawDebug(Vec3 eye, Frustum frustum, Sink sink) {
        if (!DebugMode.bounds() || Minecraft.getInstance().level == null) return;
        for (DebugBox box : debugBoxes) if (frustum.isVisible(box.box())) sink.box(box.box(), box.color());
        Minecraft client = Minecraft.getInstance();
        int renderDistance = client.options.renderDistance().get();
        var config = XRayConfigManager.get();
        if (config.highlightEnabled) {
            if (config.highlightRangeLimited(renderDistance)) {
                double range = config.getHighlightRangeBlocks(renderDistance);
                sphere(sink, eye, range, 5, RANGE_COLOR);
                sphere(sink, eye, range + SCAN_MARGIN, 1, REACH_COLOR);
            } else {
                int view = Math.max(1, renderDistance);
                chunkSquare(sink, eye, view, RANGE_COLOR);
                chunkSquare(sink, eye, view + 1, REACH_COLOR);
            }
        }
        if (config.mobEspEnabled && config.mobEspRangeBlocks > 0) {
            Entity camera = client.getCameraEntity();
            if (camera != null) sphere(sink, camera.getEyePosition(), config.mobEspRangeBlocks, 1, ESP_RANGE_COLOR);
        }
        StructureFinder.drawDebug(frustum, sink);
    }

    /**
     * Wireframe sphere: {@code rings} circles of latitude spread evenly between the poles (an odd
     * count includes the equator) and {@code rings + 1} great circles through the poles.
     */
    private static void sphere(Sink sink, Vec3 center, double radius, int rings, int color) {
        final int segments = 72;
        for (int ring = 0; ring < rings; ring++) {
            double latitude = Math.PI * (ring + 1) / (rings + 1) - Math.PI / 2;
            double y = Math.sin(latitude) * radius, r = Math.cos(latitude) * radius;
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double angle = i * 2 * Math.PI / segments;
                Vec3 point = center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
                if (previous != null) sink.line(previous, point, color);
                previous = point;
            }
        }
        int meridians = rings + 1;
        for (int meridian = 0; meridian < meridians; meridian++) {
            double longitude = meridian * Math.PI / meridians, dx = Math.cos(longitude), dz = Math.sin(longitude);
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double angle = i * 2 * Math.PI / segments, a = Math.cos(angle) * radius;
                Vec3 point = center.add(dx * a, Math.sin(angle) * radius, dz * a);
                if (previous != null) sink.line(previous, point, color);
                previous = point;
            }
        }
    }

    /** Horizontal square at eye height around the chunks up to {@code chunks} away from the camera's chunk. */
    private static void chunkSquare(Sink sink, Vec3 eye, int chunks, int color) {
        int cx = Mth.floor(eye.x) >> 4, cz = Mth.floor(eye.z) >> 4;
        double minX = (cx - chunks) * 16.0, maxX = (cx + chunks + 1) * 16.0;
        double minZ = (cz - chunks) * 16.0, maxZ = (cz + chunks + 1) * 16.0, y = eye.y;
        sink.line(new Vec3(minX, y, minZ), new Vec3(maxX, y, minZ), color);
        sink.line(new Vec3(maxX, y, minZ), new Vec3(maxX, y, maxZ), color);
        sink.line(new Vec3(maxX, y, maxZ), new Vec3(minX, y, maxZ), color);
        sink.line(new Vec3(minX, y, maxZ), new Vec3(minX, y, minZ), color);
    }

    /**
     * Wraps a sink so every drawn box is checked against the highlight range: with a range limit,
     * its closest point must lie within the range.
     */
    private static Sink checkedSink(Sink sink, Vec3 eye) {
        int renderDistance = Minecraft.getInstance().options.renderDistance().get();
        var config = XRayConfigManager.get();
        if (!config.highlightRangeLimited(renderDistance)) return sink;
        double range = config.getHighlightRangeBlocks(renderDistance);
        return new Sink() {
            @Override public void box(AABB box, int color) {
                double dx = Math.max(0.0, Math.max(box.minX - eye.x, eye.x - box.maxX));
                double dy = Math.max(0.0, Math.max(box.minY - eye.y, eye.y - box.maxY));
                double dz = Math.max(0.0, Math.max(box.minZ - eye.z, eye.z - box.maxZ));
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance > range + 0.05)
                    DebugMode.violation("highlight.drawn_outside_range", "box at " + BlockPos.containing(box.getCenter()).toShortString()
                        + " drawn " + Math.round(distance) + " blocks away, range " + Math.round(range));
                sink.box(box, color);
            }
            @Override public void tracer(Vec3 target, int color) { sink.tracer(target, color); }
            @Override public void line(Vec3 from, Vec3 to, int color) { sink.line(from, to, color); }
        };
    }

    /**
     * Walks the index for one frame. A range below the render distance is a sphere around the
     * camera; boxes and tracers share it.
     */
    private static void visit(Vec3 eye, Frustum frustum, boolean tracers, Sink sink) {
        if (DebugMode.enabled()) sink = checkedSink(sink, eye);
        if (groupVeins()) {
            visitVeins(eye, frustum, tracers, sink);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        var config = XRayConfigManager.get();
        int renderDistance = client.options.renderDistance().get();
        int view = Math.max(1, renderDistance);
        boolean limited = config.highlightRangeLimited(renderDistance);
        double range = config.getHighlightRangeBlocks(renderDistance);
        double rangeSquared = range * range;
        int cx = Mth.floor(eye.x) >> 4, cz = Mth.floor(eye.z) >> 4;
        int boxes = 0, lines = 0;
        // Objects are only created for what is drawn: this runs for every indexed block each frame.
        for (var entry : Long2ObjectMaps.fastIterable(positions)) {
            long section = entry.getLongKey();
            int sx = SectionPos.x(section), sz = SectionPos.z(section);
            if (Math.abs(sx - cx) > view || Math.abs(sz - cz) > view || !world.getChunkSource().hasChunk(sx, sz)) continue;
            double minX = sx * 16.0, minY = SectionPos.y(section) * 16.0, minZ = sz * 16.0;
            // Per-block range checks are only needed for sections that cross the range sphere.
            boolean checkBlocks = false;
            if (limited) {
                double nearX = Math.max(0.0, Math.max(minX - eye.x, eye.x - (minX + 16)));
                double nearY = Math.max(0.0, Math.max(minY - eye.y, eye.y - (minY + 16)));
                double nearZ = Math.max(0.0, Math.max(minZ - eye.z, eye.z - (minZ + 16)));
                if (nearX * nearX + nearY * nearY + nearZ * nearZ > rangeSquared) continue;
                double farX = Math.max(Math.abs(minX - eye.x), Math.abs(minX + 16 - eye.x));
                double farY = Math.max(Math.abs(minY - eye.y), Math.abs(minY + 16 - eye.y));
                double farZ = Math.max(Math.abs(minZ - eye.z), Math.abs(minZ + 16 - eye.z));
                checkBlocks = farX * farX + farY * farY + farZ * farZ > rangeSquared;
            }
            boolean visibleSection = frustum.isVisible(new AABB(minX, minY, minZ, minX + 16, minY + 16, minZ + 16).inflate(0.0125));
            if (!visibleSection && !tracers) continue;
            for (var found : Long2ObjectMaps.fastIterable(entry.getValue())) {
                long packed = found.getLongKey();
                int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
                if (checkBlocks) {
                    double dx = x + 0.5 - eye.x, dy = y + 0.5 - eye.y, dz = z + 0.5 - eye.z;
                    if (dx * dx + dy * dy + dz * dz > rangeSquared) continue;
                }
                AABB box = null;
                if (visibleSection) {
                    box = new AABB(x - 0.0125, y - 0.0125, z - 0.0125, x + 1.0125, y + 1.0125, z + 1.0125);
                    if (!frustum.isVisible(box)) box = null;
                }
                if (box == null && !tracers) continue;
                int color = color(found.getValue());
                if (box != null) {
                    sink.box(box, color);
                    boxes++;
                }
                if (tracers) {
                    sink.tracer(new Vec3(x + 0.5, y + 0.5, z + 0.5), color);
                    lines++;
                }
            }
        }
        lastDrawnBoxes = boxes;
        lastDrawnTracers = lines;
    }

    /** Like {@link #visit} with one box and tracer per vein. */
    private static void visitVeins(Vec3 eye, Frustum frustum, boolean tracers, Sink sink) {
        Minecraft client = Minecraft.getInstance();
        var config = XRayConfigManager.get();
        int renderDistance = client.options.renderDistance().get();
        int view = Math.max(1, renderDistance);
        boolean limited = config.highlightRangeLimited(renderDistance);
        double range = config.getHighlightRangeBlocks(renderDistance);
        double rangeSquared = range * range;
        int cx = Mth.floor(eye.x) >> 4, cz = Mth.floor(eye.z) >> 4;
        int boxes = 0, lines = 0;
        for (Vein vein : veins) {
            Vec3 target = vein.tracerTarget();
            int vx = Mth.floor(target.x) >> 4, vz = Mth.floor(target.z) >> 4;
            if (Math.abs(vx - cx) > view || Math.abs(vz - cz) > view || !world.getChunkSource().hasChunk(vx, vz)) continue;
            // In range when the closest point of the vein is.
            AABB bounds = vein.bounds();
            if (limited) {
                double dx = Math.max(0.0, Math.max(bounds.minX - eye.x, eye.x - bounds.maxX));
                double dy = Math.max(0.0, Math.max(bounds.minY - eye.y, eye.y - bounds.maxY));
                double dz = Math.max(0.0, Math.max(bounds.minZ - eye.z, eye.z - bounds.maxZ));
                if (dx * dx + dy * dy + dz * dz > rangeSquared) continue;
            }
            int color = color(vein.block());
            if (frustum.isVisible(bounds)) {
                sink.box(bounds, color);
                boxes++;
            }
            if (tracers) {
                sink.tracer(target, color);
                lines++;
            }
        }
        lastDrawnBoxes = boxes;
        lastDrawnTracers = lines;
    }

    private static boolean shouldRender() {
        var config = XRayConfigManager.get();
        if (!config.highlightEnabled || world == null || positions.isEmpty()
            || Minecraft.getInstance().level != world) {
            lastDrawnBoxes = 0;
            lastDrawnTracers = 0;
            return false;
        }
        return true;
    }

    //? if >=1.21.11 {
    /** Always-on-top gizmo backend; the caller opens the per-frame gizmo collection. */
    static Sink gizmoSink(Vec3 tracerStart) {
        // One stroke style per color in this frame; usually only a handful of colors.
        var styles = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<GizmoStyle>();
        return new Sink() {
            @Override public void box(AABB box, int color) {
                Gizmos.cuboid(box, styles.computeIfAbsent(color, c -> GizmoStyle.stroke(c, 2.5f))).setAlwaysOnTop();
            }
            @Override public void tracer(Vec3 target, int color) { Gizmos.line(tracerStart, target, color, 1.5f).setAlwaysOnTop(); }
            @Override public void line(Vec3 from, Vec3 to, int color) { Gizmos.line(from, to, color, 1.5f).setAlwaysOnTop(); }
        };
    }

    private static void gizmos(Vec3 eye, Frustum frustum, Vec3 tracerStart) {
        visit(eye, frustum, XRayConfigManager.get().tracersEnabled, gizmoSink(tracerStart));
    }

    //?}

    //? if >=26.1 {
    public static void render(CameraRenderState camera) {
        drawDebug(camera.pos, camera.cullFrustum, gizmoSink(camera.pos));
        if (!shouldRender()) return;
        var config = XRayConfigManager.get();
        Vec3 tracerStart = config.tracersEnabled ? tracerStart(camera, Minecraft.getInstance()) : camera.pos;
        gizmos(camera.pos, camera.cullFrustum, tracerStart);
    }

    /**
     * Tracers start this far ahead on the view axis; drawn always-on-top, the point still sits on the
     * crosshair. Shader packs (Iris) project lines with a slightly different camera than vanilla, and
     * close to the eye that difference became a large angle: at 0.25 blocks the start jumped 30 px
     * with the view bobbing. At 16 blocks it stays within 2 px.
     */
    private static final float TRACER_START_DISTANCE = 16f;

    /** Shared with the ESP tracers ({@link MobEspRenderer}). */
    static Vec3 tracerStart(CameraRenderState camera, Minecraft client) {
        // Gizmo lines starting at the eye are clipped. Invert the same view effects
        // that Minecraft applies to its projection so the start stays
        // at the screen center even while the player's view is bobbing.
        Matrix4f viewEffects = new Matrix4f();
        var entity = camera.entityRenderState;
        if (entity.isLiving) {
            if (entity.isDeadOrDying) {
                float duration = Math.min(entity.deathTime, 20.0f);
                viewEffects.rotateZ((float) Math.toRadians(40.0f - 8000.0f / (duration + 200.0f)));
            }
            if (entity.hurtTime >= 0.0f) {
                float hurt = entity.hurtTime / entity.hurtDuration;
                hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
                float yaw = (float) Math.toRadians(entity.hurtDir);
                float tilt = (float) Math.toRadians(-hurt * 14.0f * client.options.damageTiltStrength().get());
                viewEffects.rotateY(-yaw).rotateZ(tilt).rotateY(yaw);
            }
        }
        if (client.options.bobView().get() && entity.isPlayer) {
            float phase = entity.backwardsInterpolatedWalkDistance * (float) Math.PI;
            float bob = entity.bob;
            viewEffects.translate(Mth.sin(phase) * bob * 0.5f, -Math.abs(Mth.cos(phase) * bob), 0.0f);
            viewEffects.rotateZ((float) Math.toRadians(Mth.sin(phase) * bob * 3.0f));
            viewEffects.rotateX((float) Math.toRadians(Math.abs(Mth.cos(phase - 0.2f) * bob) * 5.0f));
        }
        Vector3f offset = viewEffects.mul(new Matrix4f(camera.viewRotationMatrix)).invert()
            .transformPosition(0.0f, 0.0f, -TRACER_START_DISTANCE, new Vector3f());
        return camera.pos.add(offset.x(), offset.y(), offset.z());
    }
    //?} else if >=1.21.11 {
    /*/// @param tracerStart world point at the screen center just in front of the camera
    public static void render(Vec3 eye, Frustum frustum, Vec3 tracerStart) {
        drawDebug(eye, frustum, gizmoSink(tracerStart));
        if (!shouldRender()) return;
        gizmos(eye, frustum, tracerStart);
    }
    *///?} else {
    /*/// Draws with always-on-top line render types; vertices are camera-relative.
    public static void render(Vec3 eye, Frustum frustum, Vec3 tracerStart, PoseStack.Pose pose) {
        var sink = new LineSink(eye, tracerStart, pose);
        drawDebug(eye, frustum, sink);
        if (shouldRender()) visit(eye, frustum, XRayConfigManager.get().tracersEnabled, sink);
        sink.finish();
    }

    /^*
     * Line backend: boxes are written while visiting, tracers are collected and written by
     * {@link #finish} (the immediate buffer source holds one render type at a time).
     ^/
    static final class LineSink implements Sink {
        private final Vec3 eye;
        private final Vec3 tracerStart;
        private final PoseStack.Pose pose;
        private final MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        private final List<Vec3> lineStarts = new ArrayList<>();
        private final List<Vec3> lineEnds = new ArrayList<>();
        private final it.unimi.dsi.fastutil.ints.IntArrayList lineColors = new it.unimi.dsi.fastutil.ints.IntArrayList();
        private VertexConsumer boxes;

        LineSink(Vec3 eye, Vec3 tracerStart, PoseStack.Pose pose) {
            this.eye = eye;
            this.tracerStart = tracerStart;
            this.pose = pose;
        }

        @Override public void box(AABB box, int color) {
            if (boxes == null) boxes = buffers.getBuffer(XRayRenderTypes.HIGHLIGHT_BOXES);
            LineBuffers.box(boxes, pose, box.move(-eye.x, -eye.y, -eye.z), red(color), green(color), blue(color), 1f);
        }

        @Override public void tracer(Vec3 target, int color) { line(tracerStart, target, color); }

        @Override public void line(Vec3 from, Vec3 to, int color) {
            lineStarts.add(from);
            lineEnds.add(to);
            lineColors.add(color);
        }

        void finish() {
            if (boxes != null) buffers.endBatch(XRayRenderTypes.HIGHLIGHT_BOXES);
            if (lineStarts.isEmpty()) return;
            VertexConsumer lines = buffers.getBuffer(XRayRenderTypes.TRACERS);
            for (int i = 0; i < lineStarts.size(); i++) {
                int color = lineColors.getInt(i);
                LineBuffers.line(lines, pose, lineStarts.get(i).subtract(eye), lineEnds.get(i).subtract(eye),
                    red(color), green(color), blue(color), 1f);
            }
            buffers.endBatch(XRayRenderTypes.TRACERS);
        }
    }

    private static float red(int color) { return (color >> 16 & 255) / 255f; }
    private static float green(int color) { return (color >> 8 & 255) / 255f; }
    private static float blue(int color) { return (color & 255) / 255f; }
    *///?}
}
