package dev.betterxray.render;

import dev.betterxray.config.BlockSelection;
import dev.betterxray.config.XRayConfigManager;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class XRayRenderController {
    private static boolean rebuildPending;
    /** Blocks that joined or left the preset: only sections that may contain them are rebuilt. */
    private static final Set<Block> changedBlocks = new HashSet<>();
    /** Above this share of the loaded sections a targeted rebuild is not worth it. */
    private static final double TARGETED_MAX_SHARE = .5;
    private static int targetedRebuilds;
    private static int tagCheckTicks;
    private static int rebuilds;
    private static long lastRebuildMillis;
    public static void requestRebuild() { rebuildPending = true; }
    /** Complete terrain rebuilds this session and when the last one started (debug HUD). */
    public static int rebuilds() { return rebuilds; }
    public static long lastRebuildMillis() { return lastRebuildMillis; }
    /** Preset changes that rebuilt only the affected sections (debug HUD). */
    public static int targetedRebuilds() { return targetedRebuilds; }
    public static void apply() {
        XRayState.Snapshot before = XRayState.get();
        XRayState.publish(XRayConfigManager.get());
        XRayConfigManager.save();
        XRayState.Snapshot after = XRayState.get();
        schedule(before, after);
    }

    /**
     * Rebuilds after a snapshot change: nothing when no meshed value changed (e.g. the preset while
     * only Cave-Find is on, the line opacity while the Wireframe mode is off), only the sections that
     * may contain the changed blocks when only the preset changed, else everything.
     */
    private static void schedule(XRayState.Snapshot before, XRayState.Snapshot after) {
        if (XRayState.meshEquals(before, after)) return;
        if (XRayState.onlyPresetChanged(before, after)) {
            for (Block block : before.visible()) if (!after.visible().contains(block)) changedBlocks.add(block);
            for (Block block : after.visible()) if (!before.visible().contains(block)) changedBlocks.add(block);
            return;
        }
        rebuildPending = true;
    }
    public static void tick(Minecraft client) {
        if (client.level != null && ++tagCheckTicks >= BlockSelection.TAG_CHECK_TICKS) {
            tagCheckTicks = 0;
            // Tags of the active preset are bound on world join and change on data pack reloads.
            if (XRayState.tagsChanged(XRayConfigManager.get())) {
                XRayState.Snapshot before = XRayState.get();
                XRayState.publish(XRayConfigManager.get());
                schedule(before, XRayState.get());
            }
        }
        if (client.level == null) {
            MobOcclusion.reset();
            changedBlocks.clear();
        }
        if (!changedBlocks.isEmpty()) {
            if (!rebuildPending && client.level != null && !rebuildSections(client, client.level)) rebuildPending = true;
            changedBlocks.clear();
        }
        // Cave-Find: the player's own air space is no cave (before the rebuild that meshes with it).
        CavePocket.tick(client, rebuildPending);
        if (rebuildPending) {
            rebuildPending = false;
            if (client.level != null) {
                rebuilds++;
                lastRebuildMillis = System.currentTimeMillis();
            }
            // LevelExtractor requests invalidation on the rendering side. The vanilla
            // and Sodium backends then replace their asynchronous section builders.
            //? if >=26.1 {
            if (client.level != null) client.levelExtractor.allChanged();
            //?} else {
            /*if (client.level != null) client.levelRenderer.allChanged();
            *///?}
        }
    }

    /**
     * Marks the loaded sections whose palette may hold a changed block, and their neighbors (faces
     * between two selected blocks are culled). False when that would be most of the world anyway.
     */
    private static boolean rebuildSections(Minecraft client, ClientLevel level) {
        if (client.player == null) return false;
        int radius = client.options.getEffectiveRenderDistance() + 1;
        int centerX = SectionPos.blockToSectionCoord(client.player.getBlockX());
        int centerZ = SectionPos.blockToSectionCoord(client.player.getBlockZ());
        LongOpenHashSet sections = new LongOpenHashSet();
        int loaded = 0;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                LevelChunkSection[] chunkSections = chunk.getSections();
                for (int i = 0; i < chunkSections.length; i++) {
                    LevelChunkSection section = chunkSections[i];
                    if (section.hasOnlyAir()) continue;
                    loaded++;
                    if (!section.maybeHas(state -> changedBlocks.contains(state.getBlock()))) continue;
                    int y = chunk.getSectionYFromSectionIndex(i);
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1) sections.add(SectionPos.asLong(x + dx, y + dy, z + dz));
                }
            }
        }
        if (sections.size() > loaded * TARGETED_MAX_SHARE) return false;
        targetedRebuilds++;
        for (LongIterator it = sections.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            //? if >=26.1 {
            client.levelExtractor.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
            //?} else {
            /*client.levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
            *///?}
        }
        return true;
    }
}
