package dev.betterxray.render;

import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.debug.DebugMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?}
//? if <1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
//? if >=1.20.5 {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?}

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Structure Finder (singleplayer). The client never receives structure data, the
 * integrated server has it: every second a scan on the server thread reads the structure
 * references of the loaded chunks around the player and looks up each referenced start. Nothing
 * is loaded or generated for it. The render pass only reads the published result.
 */
public final class StructureFinder {
    /**
     * One picker row: the structures of one structure set (all village variants, all abandoned
     * camps, …). A set that mixes kinds (nether_complexes: fortress and bastion) gives one row per kind.
     */
    public record Group(String key, Component name, List<String> structures, int color) {}

    /** A structure start near the player; {@code center} is the tracer target. */
    public record Found(Group group, AABB bounds, Vec3 center) {}

    /**
     * Result of one scan of the chunks within {@code radius} chunks of {@code centerX}/{@code centerZ}.
     *
     * @param chunks loaded chunks that were read
     * @param nanos  time the scan took on the server thread
     */
    private record Scan(ResourceKey<Level> dimension, List<Found> found, int centerX, int centerZ, int radius,
                        int chunks, long nanos) {
        static final Scan EMPTY = new Scan(null, List.of(), 0, 0, 0, 0, 0);
    }

    /** State for {@code /betterxray debug stats} and tests. */
    public record Stats(boolean available, int found, int centerX, int centerZ, int radius, int chunks, long scanNanos,
                        int drawnBoxes, int epoch, boolean scanning) {}

    /**
     * Structures may extend beyond the chunks that reference them by this many blocks: the start's
     * box, which places the references, is padded for terrain adaptation, the piece box is not.
     */
    private static final int REFERENCE_SLACK = 16;

    private static final int SCAN_TICKS = 20;
    private static volatile Scan last = Scan.EMPTY;
    /** Last scan the debug checks ran on, and whether the finder was already off on the previous tick. */
    private static Scan checked = Scan.EMPTY;
    private static boolean wasOff = true;
    private static final AtomicBoolean scanning = new AtomicBoolean();
    /** Bumped whenever results are cleared; a scan still running for an older epoch is dropped. */
    private static final AtomicInteger epoch = new AtomicInteger();
    private static int ticks = SCAN_TICKS;
    private static WeakReference<MinecraftServer> groupsServer = new WeakReference<>(null);
    private static List<Group> groups = List.of();
    private static Map<String, Group> groupOf = Map.of();
    private static int lastDrawnBoxes;

    private StructureFinder() {}

    /** Structure data exists only on the integrated server. */
    public static boolean available() { return Minecraft.getInstance().getSingleplayerServer() != null; }

    /** Picker rows of the current singleplayer world, sorted by name; empty without one. */
    public static List<Group> groups() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return List.of();
        if (groupsServer.get() != server) {
            groups = buildGroups(server.registryAccess());
            Map<String, Group> byStructure = new HashMap<>();
            for (Group group : groups) for (String id : group.structures()) byStructure.put(id, group);
            groupOf = Map.copyOf(byStructure);
            groupsServer = new WeakReference<>(server);
        }
        return groups;
    }

    /** Structures found by the last scan (used by tests). */
    public static List<Found> found() { return last.found(); }

    /** Boxes submitted in the last frame (used by tests). */
    public static int lastDrawnBoxes() { return lastDrawnBoxes; }

    /** Every structure lands in exactly one group; structures without a set get their own. */
    private static List<Group> buildGroups(RegistryAccess access) {
        Map<String, List<String>> members = new LinkedHashMap<>();
        Set<String> assigned = new HashSet<>();
        for (var set : registry(access, Registries.STRUCTURE_SET).entrySet()) {
            Map<StructureType<?>, List<String>> kinds = new LinkedHashMap<>();
            for (var entry : set.getValue().structures()) {
                var key = entry.structure().unwrapKey();
                if (key.isPresent() && assigned.add(id(key.get())))
                    kinds.computeIfAbsent(entry.structure().value().type(), k -> new ArrayList<>()).add(id(key.get()));
            }
            String setId = id(set.getKey());
            for (var kind : kinds.entrySet()) {
                List<String> ids = kind.getValue();
                String key = kinds.size() == 1 ? setId : ids.size() == 1 ? ids.get(0)
                    : setId + "_" + BuiltInRegistries.STRUCTURE_TYPE.getKey(kind.getKey()).getPath();
                members.computeIfAbsent(key, k -> new ArrayList<>()).addAll(ids);
            }
        }
        for (var structure : registry(access, Registries.STRUCTURE).entrySet()) {
            String id = id(structure.getKey());
            if (assigned.add(id)) members.computeIfAbsent(id, k -> new ArrayList<>()).add(id);
        }
        List<Group> result = new ArrayList<>();
        members.forEach((key, ids) -> result.add(new Group(key, name(key), List.copyOf(ids), color(key))));
        result.sort(Comparator.comparing(group -> group.name().getString().toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }

    private static <T> Registry<T> registry(RegistryAccess access, ResourceKey<? extends Registry<? extends T>> key) {
        //? if >=1.21.2 {
        return access.lookupOrThrow(key);
        //?} else {
        /*return access.registryOrThrow(key);
        *///?}
    }

    private static String id(ResourceKey<?> key) {
        //? if >=1.21.11 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();
        *///?}
    }

    /** {@code structure.<namespace>.<path>} from the language files, else the path in title case. */
    private static Component name(String key) {
        Identifier id = Identifier.tryParse(key);
        StringBuilder pretty = new StringBuilder();
        for (String word : id.getPath().split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!pretty.isEmpty()) pretty.append(' ');
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.translatableWithFallback("structure." + id.getNamespace() + "." + id.getPath(), pretty.toString());
    }

    /** Stable, saturated color per group. */
    private static int color(String key) {
        float hue = (key.hashCode() * 0x9E3779B9 >>> 8) / (float)(1 << 24);
        //? if >=1.21 {
        return Mth.hsvToArgb(hue, 0.7f, 1.0f, 255);
        //?} else {
        /*return Mth.hsvToRgb(hue, 0.7f, 1.0f) | 0xff000000;
        *///?}
    }

    private static void clear() {
        epoch.incrementAndGet();
        last = Scan.EMPTY;
        ticks = SCAN_TICKS;
    }

    public static Stats stats() {
        Scan scan = last;
        return new Stats(available(), scan.found().size(), scan.centerX(), scan.centerZ(), scan.radius(), scan.chunks(),
            scan.nanos(), lastDrawnBoxes, epoch.get(), scanning.get());
    }

    public static void tick(Minecraft client) {
        var config = XRayConfigManager.get();
        MinecraftServer server = client.getSingleplayerServer();
        if (!config.structureFinderEnabled || config.structures.isEmpty() || server == null
            || client.level == null || client.player == null) {
            // Results were cleared on an earlier tick; a scan published since then belongs to an older epoch.
            if (DebugMode.enabled() && wasOff && !last.found().isEmpty())
                DebugMode.violation("structure.found_while_off", last.found().size() + " structures published after the finder was switched off");
            wasOff = true;
            if (!last.found().isEmpty() || last.dimension() != null) clear();
            return;
        }
        wasOff = false;
        if (DebugMode.enabled() && last != checked) {
            checked = last;
            checkInvariants(checked);
        }
        if (++ticks < SCAN_TICKS || !scanning.compareAndSet(false, true)) return;
        ticks = 0;
        groups();
        Map<String, Group> wanted = new HashMap<>();
        for (String id : config.structures) {
            Group group = groupOf.get(id);
            if (group != null) wanted.put(id, group);
        }
        ResourceKey<Level> dimension = client.level.dimension();
        ChunkPos center = client.player.chunkPosition();
        int radius = Math.max(1, client.options.renderDistance().get());
        int scanEpoch = epoch.get();
        server.execute(() -> {
            try {
                long start = System.nanoTime();
                int[] chunks = new int[1];
                List<Found> found = scan(server.getLevel(dimension), center, radius, wanted, chunks);
                //? if >=26.1 {
                int centerX = center.x(), centerZ = center.z();
                //?} else {
                /*int centerX = center.x, centerZ = center.z;
                *///?}
                if (epoch.get() == scanEpoch)
                    last = new Scan(dimension, found, centerX, centerZ, radius, chunks[0], System.nanoTime() - start);
            } finally {
                scanning.set(false);
            }
        });
    }

    /**
     * Checks a published scan: every structure must touch the chunks the scan read (plus
     * {@link #REFERENCE_SLACK}), and no structure start may be listed twice.
     */
    private static void checkInvariants(Scan scan) {
        double minX = (scan.centerX() - scan.radius()) * 16.0 - REFERENCE_SLACK;
        double minZ = (scan.centerZ() - scan.radius()) * 16.0 - REFERENCE_SLACK;
        double maxX = (scan.centerX() + scan.radius() + 1) * 16.0 + REFERENCE_SLACK;
        double maxZ = (scan.centerZ() + scan.radius() + 1) * 16.0 + REFERENCE_SLACK;
        Set<AABB> seen = new HashSet<>();
        for (Found found : scan.found()) {
            AABB box = found.bounds();
            if (box.maxX < minX || box.minX > maxX || box.maxZ < minZ || box.minZ > maxZ) {
                DebugMode.violation("structure.found_outside_scan", found.group().key() + " at "
                    + BlockPos.containing(found.center()).toShortString() + " lies outside the scanned chunks around chunk "
                    + scan.centerX() + "," + scan.centerZ() + " (radius " + scan.radius() + ")");
            }
            if (!seen.add(box)) DebugMode.violation("structure.duplicate", found.group().key() + " at "
                + BlockPos.containing(found.center()).toShortString() + " listed twice");
        }
    }

    /** Debug drawing: the chunk square of the last scan, over the full world height. */
    static void drawDebug(Frustum frustum, HighlightCache.Sink sink) {
        Scan scan = last;
        Minecraft client = Minecraft.getInstance();
        if (scan.dimension() == null || client.level == null || !client.level.dimension().equals(scan.dimension())) return;
        //? if >=1.21.2 {
        int bottom = client.level.getMinY(), top = client.level.getMaxY() + 1;
        //?} else {
        /*int bottom = client.level.getMinBuildHeight(), top = client.level.getMaxBuildHeight();
        *///?}
        AABB area = new AABB((scan.centerX() - scan.radius()) * 16.0, bottom, (scan.centerZ() - scan.radius()) * 16.0,
            (scan.centerX() + scan.radius() + 1) * 16.0, top, (scan.centerZ() + scan.radius() + 1) * 16.0);
        if (frustum.isVisible(area)) sink.box(area, 0xffff40ff);
    }

    /** Runs on the server thread; {@code chunks[0]} receives the number of loaded chunks read. */
    private static List<Found> scan(ServerLevel level, ChunkPos center, int radius, Map<String, Group> wanted, int[] chunksRead) {
        if (level == null || wanted.isEmpty()) return List.of();
        Registry<Structure> registry = registry(level.registryAccess(), Registries.STRUCTURE);
        Map<Structure, Group> selected = new HashMap<>();
        wanted.forEach((id, group) -> {
            Identifier key = Identifier.tryParse(id);
            if (key != null) registry.getOptional(key).ifPresent(s -> selected.put(s, group));
        });
        Map<Structure, LongOpenHashSet> seen = new HashMap<>();
        List<Found> result = new ArrayList<>();
        var chunks = level.getChunkSource();
        //? if >=26.1 {
        int centerX = center.x(), centerZ = center.z();
        //?} else {
        /*int centerX = center.x, centerZ = center.z;
        *///?}
        for (int x = centerX - radius; x <= centerX + radius; x++) for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            LevelChunk chunk = chunks.getChunkNow(x, z);
            if (chunk == null) continue;
            chunksRead[0]++;
            // Every chunk a structure overlaps references its start chunk, also across chunk borders.
            for (var references : chunk.getAllReferences().entrySet()) {
                Group group = selected.get(references.getKey());
                if (group == null) continue;
                for (long origin : references.getValue()) {
                    if (!seen.computeIfAbsent(references.getKey(), k -> new LongOpenHashSet()).add(origin)) continue;
                    ChunkAccess start = startChunk(chunks.chunkMap, origin);
                    StructureStart structure = start == null ? null : start.getStartForStructure(references.getKey());
                    if (structure == null || !structure.isValid()) continue;
                    BoundingBox box = bounds(level, structure);
                    AABB bounds = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
                    result.add(new Found(group, bounds, bounds.getCenter()));
                }
            }
        }
        return List.copyOf(result);
    }

    /** The start chunk once its structure starts exist; never loads or generates it. */
    private static ChunkAccess startChunk(net.minecraft.server.level.ChunkMap chunkMap, long origin) {
        //? if >=1.21.11 {
        ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(origin);
        //?} else {
        /*ChunkHolder holder = ((dev.betterxray.mixin.ChunkMapAccessor)chunkMap).xray$updatingChunk(origin);
        *///?}
        if (holder == null) return null;
        //? if >=1.20.5 {
        return holder.getChunkIfPresent(ChunkStatus.STRUCTURE_STARTS);
        //?} else {
        /*// Earlier statuses have no structure starts, so the latest chunk is enough.
        return holder.getLastAvailable();
        *///?}
    }

    /**
     * Box around the pieces where they actually stand. The start's own box is cached before
     * temples settle on the terrain, and igloo pieces are stored at y 90 and only move to the
     * ground while they are placed ({@code IglooPieces.postProcess}); here they move to the same
     * ground height.
     */
    private static BoundingBox bounds(ServerLevel level, StructureStart start) {
        BoundingBox result = null;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (piece instanceof IglooPieces.IglooPiece igloo) {
                // The template position stays at y 90; the box is at y 90 too until the piece is
                // placed in this session, at the ground afterwards. Measured from the template, the
                // shift fits both.
                BlockPos center = box.getCenter();
                var chunks = level.getChunkSource();
                int ground = chunks.getGenerator().getBaseHeight(center.getX(), center.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, level, chunks.randomState());
                box = box.moved(0, igloo.templatePosition().getY() + ground - IglooPieces.GENERATION_HEIGHT - 1 - box.minY(), 0);
            }
            result = result == null ? box : new BoundingBox(Math.min(result.minX(), box.minX()), Math.min(result.minY(), box.minY()),
                Math.min(result.minZ(), box.minZ()), Math.max(result.maxX(), box.maxX()), Math.max(result.maxY(), box.maxY()),
                Math.max(result.maxZ(), box.maxZ()));
        }
        return result != null ? result : start.getBoundingBox();
    }

    private static boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        Scan scan = last;
        lastDrawnBoxes = 0;
        return XRayConfigManager.get().structureFinderEnabled && !scan.found().isEmpty() && client.level != null
            && client.level.dimension().equals(scan.dimension());
    }

    /** One box per visible structure, one tracer per structure while structure tracers are on. */
    private static void visit(Frustum frustum, boolean tracers, HighlightCache.Sink sink) {
        for (Found found : last.found()) {
            int color = found.group().color();
            if (frustum.isVisible(found.bounds())) {
                sink.box(found.bounds(), color);
                lastDrawnBoxes++;
            }
            if (tracers) sink.tracer(found.center(), color);
        }
    }

    //? if >=26.1 {
    public static void render(CameraRenderState camera) {
        if (!shouldRender()) return;
        boolean tracers = XRayConfigManager.get().structureTracersEnabled;
        Vec3 tracerStart = tracers ? HighlightCache.tracerStart(camera, Minecraft.getInstance()) : camera.pos;
        visit(camera.cullFrustum, tracers, HighlightCache.gizmoSink(tracerStart));
    }
    //?} else if >=1.21.11 {
    /*/// @param tracerStart world point at the screen center just in front of the camera
    public static void render(Frustum frustum, Vec3 tracerStart) {
        if (!shouldRender()) return;
        visit(frustum, XRayConfigManager.get().structureTracersEnabled, HighlightCache.gizmoSink(tracerStart));
    }
    *///?} else {
    /*/// Always-on-top line render types; vertices are camera-relative.
    public static void render(Vec3 eye, Frustum frustum, Vec3 tracerStart, PoseStack.Pose pose) {
        if (!shouldRender()) return;
        var sink = new HighlightCache.LineSink(eye, tracerStart, pose);
        visit(frustum, XRayConfigManager.get().structureTracersEnabled, sink);
        sink.finish();
    }
    *///?}
}
