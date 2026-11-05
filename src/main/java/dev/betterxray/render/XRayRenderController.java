package dev.betterxray.render;

import dev.betterxray.config.BlockSelection;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.Minecraft;

public final class XRayRenderController {
    private static boolean rebuildPending;
    private static int tagCheckTicks;
    private static int rebuilds;
    private static long lastRebuildMillis;
    public static void requestRebuild() { rebuildPending = true; }
    /** Complete terrain rebuilds this session and when the last one started (debug HUD). */
    public static int rebuilds() { return rebuilds; }
    public static long lastRebuildMillis() { return lastRebuildMillis; }
    public static void apply() {
        XRayState.Snapshot before = XRayState.get();
        XRayState.publish(XRayConfigManager.get());
        XRayConfigManager.save();
        XRayState.Snapshot after = XRayState.get();
        // X-Ray or Cave-Find was or is on: the meshes depend on the snapshot.
        if (!before.equals(after) && (before.active() || after.active())) rebuildPending = true;
    }
    public static void tick(Minecraft client) {
        if (client.level != null && ++tagCheckTicks >= BlockSelection.TAG_CHECK_TICKS) {
            tagCheckTicks = 0;
            // Tags of the active preset are bound on world join and change on data pack reloads.
            if (XRayState.tagsChanged(XRayConfigManager.get())) {
                XRayState.Snapshot before = XRayState.get();
                XRayState.publish(XRayConfigManager.get());
                if (before.active()) rebuildPending = true;
            }
        }
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
}
