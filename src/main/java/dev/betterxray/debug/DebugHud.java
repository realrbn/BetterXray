package dev.betterxray.debug;

import dev.betterxray.compat.RendererCompatibility;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.HighlightCache;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.StructureFinder;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Debug HUD in the top left corner, in the style of the F3 screen: live counters of X-Ray,
 * Highlight, ESP and the Structure Finder. The lines are built twice a second on the client
 * tick, so a frame only draws finished strings. Hidden while the F3 screen is open or the HUD is
 * hidden (F1). Like the F3 screen it is not translated.
 */
public final class DebugHud {
    private static final int UPDATE_TICKS = 10;
    private static final int TEXT = 0xffe0e0e0, TITLE = 0xffffffff, GOOD = 0xff60ff60, BAD = 0xffff5050;
    /** Background of every line, as on the F3 screen. */
    private static final int BACKGROUND = 0x90505050;

    private record Line(String text, int color) {}

    /** Written on the client tick, read when the HUD is drawn (also the client thread). */
    private static List<Line> lines = List.of();
    private static int ticks = UPDATE_TICKS;

    private DebugHud() {}

    public static void tick(Minecraft client) {
        if (!DebugMode.hud() || client.level == null) {
            lines = List.of();
            ticks = UPDATE_TICKS;
            return;
        }
        if (++ticks < UPDATE_TICKS) return;
        ticks = 0;
        lines = build(client);
    }

    /** Lines shown by the HUD right now (used by tests); empty while it is off. */
    public static List<String> lines() { return lines.stream().map(Line::text).toList(); }

    public static void draw(GuiGraphicsExtractor g) {
        List<Line> current = lines;
        if (current.isEmpty() || !DebugMode.hud()) return;
        Minecraft client = Minecraft.getInstance();
        if (hidden(client)) return;
        Font font = client.font;
        int y = 2;
        for (Line line : current) {
            g.fill(1, y - 1, 3 + font.width(line.text()), y + font.lineHeight - 1, BACKGROUND);
            g.text(font, line.text(), 2, y, line.color(), false);
            y += font.lineHeight;
        }
    }

    /** The HUD is hidden (F1) or the F3 screen covers the corner. */
    private static boolean hidden(Minecraft client) {
        //? if >=26.1 {
        if (client.gui.hud.isHidden()) return true;
        //?} else {
        /*if (client.options.hideGui) return true;
        *///?}
        //? if >=1.21.9 {
        return client.debugEntries.isOverlayVisible();
        //?} else if >=1.21 {
        /*return client.getDebugOverlay().showDebugScreen();
        *///?} else {
        /*return client.options.renderDebug;
        *///?}
    }

    private static List<Line> build(Minecraft client) {
        List<Line> out = new ArrayList<>();
        var config = XRayConfigManager.get();
        int broken = DebugMode.violations().size();
        out.add(new Line("Better X-Ray debug | checks: " + (broken == 0 ? "ok"
            : broken + " broken (" + DebugMode.total() + "x), /bxray debug"), broken == 0 ? TITLE : BAD));

        // X-Ray
        var snapshot = XRayState.get();
        Boolean shader = RendererCompatibility.shaderPackActive();
        String faint = XRayState.faintUnsupported() ? "hidden (shader)" : XRayState.faintEncoded() ? "shader patch"
            : Math.round(snapshot.opacity() * 100) + "%";
        out.add(new Line("X-Ray: " + onOff(snapshot.enabled()) + ", Cave-Find " + onOff(snapshot.caveFind())
            + " | " + RendererCompatibility.debugPath() + (shader == null ? "" : shader ? " + shader pack" : " + Iris")
            + " | faint " + faint, XRayState.supported() ? TEXT : BAD));
        long since = XRayRenderController.lastRebuildMillis();
        out.add(new Line("  " + (XRayState.supported() ? "" : "UNSUPPORTED, ") + snapshot.visible().size()
            + " blocks selected | rebuilds " + XRayRenderController.rebuilds()
            + (since == 0 ? "" : ", last " + seconds(System.currentTimeMillis() - since) + " ago"), XRayState.supported() ? TEXT : BAD));

        // Highlight
        var h = HighlightCache.stats(client);
        if (!h.active()) {
            out.add(new Line("Highlight: off", TEXT));
        } else {
            out.add(new Line("Highlight: range " + (h.limited() ? Math.round(h.range()) + " blocks" : "render distance ("
                + h.viewChunks() + " chunks)") + " | " + h.scannedSections() + " sections scanned, " + h.queuedSections()
                + " queued, " + h.sectionsWithHits() + " with hits", TEXT));
            out.add(new Line("  " + h.indexedPositions() + " blocks, " + (config.groupVeins ? h.veins() + " veins (built "
                + h.veinRebuilds() + "x, " + millis(h.lastVeinNanos()) + ")" : "veins off") + " | drawn " + h.drawnBoxes()
                + " boxes, " + h.drawnTracers() + " tracers", TEXT));
            boolean far = h.farthestAllowed() >= 0 && h.farthestScanned() > h.farthestAllowed();
            out.add(new Line("  farthest scan " + Math.round(h.farthestScanned()) + (h.farthestAllowed() >= 0
                ? " / " + Math.round(h.farthestAllowed()) + " blocks" : " blocks") + " | outside " + h.outsideSections(),
                far || h.outsideSections() > 0 ? BAD : GOOD));
            out.add(new Line("  last tick " + h.lastTickSections() + " sections in " + millis(h.lastTickNanos())
                + " (max " + millis(h.maxTickNanos()) + ")", TEXT));
        }

        // ESP and Hide Mobs
        int entities = 0, targets = 0, boxes = 0, tracers = 0, hidden = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            entities++;
            var setting = MobEspRenderer.settings(entity);
            if (setting == null) {
                if (XRayState.hideMob(entity)) hidden++;
                continue;
            }
            targets++;
            if (setting.style() == XRayConfig.EspStyle.BOX) boxes++;
            if (config.mobEspTracersEnabled && setting.tracer()) tracers++;
        }
        out.add(new Line("ESP: " + onOff(config.mobEspEnabled) + ", range " + (config.mobEspRangeBlocks > 0
            ? config.mobEspRangeBlocks + " blocks" : "unlimited") + " | " + entities + " entities, " + targets + " targets", TEXT));
        out.add(new Line("  " + boxes + " boxes, " + (targets - boxes) + " outlines, " + tracers + " tracers ("
            + MobEspRenderer.lastDrawnTracers() + " drawn)" + (XRayState.enabled() && config.hideMobs != XRayConfig.HideMobs.OFF
            ? " | Hide Mobs " + config.hideMobs.name().toLowerCase(Locale.ROOT) + ": " + hidden + " hidden" : ""), TEXT));

        // Structure Finder
        var s = StructureFinder.stats();
        if (!config.structureFinderEnabled) {
            out.add(new Line("Structures: off", TEXT));
        } else if (!s.available()) {
            out.add(new Line("Structures: unavailable (no integrated server)", TEXT));
        } else {
            out.add(new Line("Structures: " + s.found() + " found, " + s.drawnBoxes() + " drawn | epoch " + s.epoch()
                + (s.scanning() ? ", scanning" : ""), TEXT));
            out.add(new Line("  last scan: radius " + s.radius() + " chunks, " + s.chunks() + " chunks read in "
                + millis(s.scanNanos()), TEXT));
        }
        return List.copyOf(out);
    }

    private static String onOff(boolean value) { return value ? "on" : "off"; }

    private static String millis(long nanos) { return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0); }

    private static String seconds(long millis) {
        return millis < 60_000 ? millis / 1000 + " s" : millis / 60_000 + " min";
    }
}
