package dev.betterxray.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * "Liquid glass" look of the Better X-Ray screens: a blurred, tinted backdrop and translucent
 * rounded surfaces with a light rim instead of the vanilla stone sprites.
 *
 * <p>Only plain fills are used (no textures or shaders), so every shape is built from GUI-pixel
 * rows. Vanilla widgets keep their behavior; the {@code Glass*Mixin}s swap their sprites for
 * these painters while a Better X-Ray screen is open ({@link #active()}).</p>
 */
public final class GlassTheme {
    /** Switches the whole theme (painters and Glass*Mixins); false restores the vanilla look. */
    public static final boolean ENABLED = true;

    /** Accent (iOS-like system blue/cyan). */
    public static final int ACCENT = 0xff5ac8fa;
    public static final int TEXT = 0xffffffff;
    public static final int TEXT_MUTED = 0xffa9b4c2;
    public static final int RADIUS = 4;

    private GlassTheme() {}

    /** True while one of the Better X-Ray screens is the current screen. */
    public static boolean active() {
        return ENABLED && owns(dev.betterxray.compat.Mc.screen());
    }

    /** True for the Better X-Ray screens. */
    public static boolean owns(Object screen) {
        return ENABLED && screen != null && screen.getClass().getName().startsWith("dev.betterxray.gui.");
    }

    /**
     * Focus is only shown while navigating with the keyboard; a clicked widget keeps the focus
     * but should not keep a highlight ring.
     */
    public static boolean keyboardFocus(boolean focused) {
        return focused && Minecraft.getInstance().getLastInputType().isKeyboard();
    }

    // ---- surfaces ---------------------------------------------------------------------------

    /** Screen title; with the theme it gets a short accent bar underneath. */
    public static void title(GuiGraphicsExtractor g, Font font, Component title, int centerX, int y) {
        g.centeredText(font, title, centerX, y, TEXT);
        if (ENABLED) surface(g, centerX - 12, y + font.lineHeight + 1, centerX + 12, y + font.lineHeight + 3, 1, ACCENT, ACCENT, 0, 0);
    }

    /** Full-screen tint drawn over the blurred world or panorama. */
    public static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0x9010141c, 0xc0080a10);
        // Faint accent glow at the top edge.
        g.fillGradient(0, 0, width, Math.min(height, 80), 0x1a5ac8fa, 0x005ac8fa);
    }

    /** Large frosted card behind a group of controls. */
    public static void card(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        surface(g, x0, y0, x1, y1, RADIUS + 2, 0x70202630, 0x80101318, 0x30ffffff, 0x50ffffff);
    }

    /** Button surface in its hovered / focused / disabled state. */
    public static void button(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean active, boolean hovered, boolean focused) {
        if (!active) {
            surface(g, x0, y0, x1, y1, RADIUS, 0x0affffff, 0x05ffffff, 0x14ffffff, 0x18ffffff);
        } else if (hovered) {
            surface(g, x0, y0, x1, y1, RADIUS, 0x34ffffff, 0x1cffffff, 0x60ffffff, 0xa0ffffff);
        } else {
            surface(g, x0, y0, x1, y1, RADIUS, 0x1cffffff, 0x0cffffff, 0x2cffffff, 0x60ffffff);
        }
        if (focused && active) ring(g, x0, y0, x1, y1, RADIUS, ACCENT);
    }

    /** Recessed text field. */
    public static void field(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean focused) {
        surface(g, x0, y0, x1, y1, RADIUS, 0x50000000, 0x38000000, focused ? 0xc05ac8fa : 0x38ffffff, 0x18ffffff);
    }

    /** Slider track with the part left of the handle filled with the accent. */
    public static void sliderTrack(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double value, boolean active, boolean focused) {
        button(g, x0, y0, x1, y1, active, false, focused);
        int fillEnd = x0 + 2 + (int)Math.round((x1 - x0 - 4) * Math.max(0, Math.min(1, value)));
        if (active && fillEnd > x0 + 2)
            surface(g, x0 + 2, y0 + 2, fillEnd, y1 - 2, RADIUS - 1, 0x705ac8fa, 0x4034a0e0, 0x00000000, 0x305ac8fa);
    }

    /** Slider handle: a bright pill. */
    public static void sliderHandle(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean active, boolean hovered) {
        int top = active ? (hovered ? 0xffffffff : 0xf0ffffff) : 0x60ffffff;
        int bottom = active ? (hovered ? 0xffe4f4ff : 0xd8dce6f0) : 0x40ffffff;
        surface(g, x0 + 1, y0 + 2, x1 - 1, y1 - 2, 3, top, bottom, 0x40000000, 0x00000000);
    }

    /** Panel behind a scrolling list. */
    public static void listPanel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        surface(g, x0, y0, x1, y1, RADIUS + 2, 0x60202630, 0x70101318, 0x28ffffff, 0x48ffffff);
    }

    /** Highlight of the selected / focused list row. */
    public static void rowSelection(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean focused) {
        surface(g, x0, y0, x1, y1, RADIUS, 0x24ffffff, 0x14ffffff, focused ? 0xa0ffffff : 0x40ffffff, 0x00000000);
    }

    /** Soft highlight of the list row under the mouse. */
    public static void rowHover(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        surface(g, x0, y0, x1, y1, RADIUS, 0x18ffffff, 0x0cffffff, 0x20ffffff, 0x00000000);
    }

    /** Tinted row background, e.g. for selected blocks. */
    public static void rowTint(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int rgb) {
        int c = rgb & 0xffffff;
        surface(g, x0, y0, x1, y1, RADIUS, 0x38000000 | c, 0x20000000 | c, 0x50000000 | c, 0x00000000);
    }

    /** Thin scrollbar track. */
    public static void scrollTrack(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int cx = (x0 + x1) / 2;
        surface(g, cx - 2, y0, cx + 2, y1, 2, 0x18ffffff, 0x18ffffff, 0x00000000, 0x00000000);
    }

    /** Scrollbar thumb. */
    public static void scrollThumb(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean enabled) {
        int cx = (x0 + x1) / 2;
        int color = enabled ? 0xb0ffffff : 0x40ffffff;
        surface(g, cx - 2, y0, cx + 2, y1, 2, color, color, 0x00000000, 0x00000000);
    }

    /** Checkbox: rounded square, accent filled with a check mark when on. */
    public static void checkbox(GuiGraphicsExtractor g, Font font, int x, int y, int size, boolean on, boolean hovered) {
        if (on) {
            surface(g, x, y, x + size, y + size, 3, 0xff6fd3ff, 0xff3a9fe0, 0x00000000, 0x60ffffff);
            g.text(font, "✔", x + (size - font.width("✔")) / 2 + 1, y + (size - 8) / 2, 0xffffffff, false);
        } else {
            surface(g, x, y, x + size, y + size, 3, 0x30000000, 0x20000000, hovered ? 0xa0ffffff : 0x60ffffff, 0x00000000);
        }
    }

    /** iOS-like switch; {@code color} tints the "on" state. */
    public static void toggle(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean on, int color, boolean hovered) {
        int c = color & 0xffffff;
        int r = (y1 - y0) / 2;
        if (on) surface(g, x0, y0, x1, y1, r, 0xc0000000 | c, 0x90000000 | c, hovered ? 0xffffffff : 0x60ffffff, 0x50ffffff);
        else surface(g, x0, y0, x1, y1, r, 0x40000000, 0x30000000, hovered ? 0xc0ffffff : 0x50ffffff, 0x00000000);
    }

    // ---- primitives -------------------------------------------------------------------------

    /**
     * Rounded rectangle with a vertical gradient fill, a 1-pixel rim and a brighter top
     * highlight (the "specular" edge of the glass). Any color may be 0 to skip that part.
     */
    public static void surface(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius,
                               int fillTop, int fillBottom, int rim, int highlight) {
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (fillTop != 0 || fillBottom != 0) {
            for (int i = 0; i < r; i++) {
                int in = inset(r, i);
                g.fill(x0 + in, y0 + i, x1 - in, y0 + i + 1, lerp(fillTop, fillBottom, (i + .5f) / h));
                g.fill(x0 + in, y1 - 1 - i, x1 - in, y1 - i, lerp(fillTop, fillBottom, (h - i - .5f) / h));
            }
            if (h - 2 * r > 0)
                g.fillGradient(x0, y0 + r, x1, y1 - r, lerp(fillTop, fillBottom, (float)r / h), lerp(fillTop, fillBottom, (float)(h - r) / h));
        }
        if (rim != 0) ring(g, x0, y0, x1, y1, r, rim);
        if (highlight != 0 && w > 2 * r + 2) g.fill(x0 + r + 1, y0, x1 - r - 1, y0 + 1, highlight);
    }

    /** 1-pixel rounded outline. */
    public static void ring(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius, int color) {
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { g.outline(x0, y0, w, h, color); return; }
        // Straight edges.
        g.fill(x0 + r, y0, x1 - r, y0 + 1, color);
        g.fill(x0 + r, y1 - 1, x1 - r, y1, color);
        g.fill(x0, y0 + r, x0 + 1, y1 - r, color);
        g.fill(x1 - 1, y0 + r, x1, y1 - r, color);
        // Corners: per row, the pixels between this row's inset and the next row's.
        for (int i = 0; i < r; i++) {
            int from = inset(r, i);
            int to = i == 0 ? r : Math.max(from + 1, inset(r, i - 1));
            g.fill(x0 + from, y0 + i, x0 + to, y0 + i + 1, color);
            g.fill(x1 - to, y0 + i, x1 - from, y0 + i + 1, color);
            g.fill(x0 + from, y1 - 1 - i, x0 + to, y1 - i, color);
            g.fill(x1 - to, y1 - 1 - i, x1 - from, y1 - i, color);
        }
    }

    /** Horizontal inset of row {@code i} (from the top) of a corner with radius {@code r}. */
    private static int inset(int r, int i) {
        double dy = r - i - .5;
        return (int)Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 255, cb = (b >>> shift) & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
