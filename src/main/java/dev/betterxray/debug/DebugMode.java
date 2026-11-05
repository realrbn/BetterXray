package dev.betterxray.debug;

import dev.betterxray.XRayClient;
import dev.betterxray.compat.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Developer debug mode, off by default and not part of the configuration. When on, the features
 * check their own invariants (scan areas, drawn ranges, consistency of the indexes) and report
 * every broken one once: to the log, to the local chat and to {@link #violations()}, which the
 * gametests assert to be empty. {@link #bounds()} additionally draws the areas the features scan,
 * {@link #sections()} every scanned section on its own, and {@link #hud()} shows live counters of
 * every feature in an overlay ({@link DebugHud}).
 *
 * <p>Switched with {@code /betterxray debug on|off|bounds|sections|hud}, or at startup with
 * {@code -Dbetterxray.debug=true}.
 */
public final class DebugMode {
    /** Most violations kept for {@link #violations()}; later ones are only logged. */
    private static final int MAX_KEPT = 100;

    private static volatile boolean enabled = Boolean.getBoolean("betterxray.debug");
    private static volatile boolean bounds;
    private static volatile boolean sections;
    private static volatile boolean hud;
    /** Keys already reported; each broken invariant is reported once until {@link #clear()}. */
    private static final Set<String> reported = new LinkedHashSet<>();
    private static final List<String> violations = new ArrayList<>();
    private static int total;

    private DebugMode() {}

    /** Invariant checks are on. */
    public static boolean enabled() { return enabled; }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            bounds = false;
            sections = false;
            hud = false;
        }
    }

    /** Scan areas are drawn; implies {@link #enabled()}. */
    public static boolean bounds() { return enabled && bounds; }

    public static void setBounds(boolean value) {
        if (value) enabled = true;
        else sections = false;
        bounds = value;
    }

    /** Every scanned section is drawn as its own box instead of one box per chunk column; implies {@link #bounds()}. */
    public static boolean sections() { return bounds() && sections; }

    public static void setSections(boolean value) {
        if (value) setBounds(true);
        sections = value;
    }

    /** The debug HUD is shown; implies {@link #enabled()}. */
    public static boolean hud() { return enabled && hud; }

    public static void setHud(boolean value) {
        if (value) enabled = true;
        hud = value;
    }

    /**
     * Reports a broken invariant. {@code key} identifies the invariant (e.g.
     * {@code highlight.scan_outside_range}); only its first violation is logged and shown in the
     * chat, later ones only count.
     */
    public static void violation(String key, String detail) {
        boolean first;
        synchronized (DebugMode.class) {
            total++;
            first = reported.add(key);
            if (first && violations.size() < MAX_KEPT) violations.add(key + ": " + detail);
        }
        if (!first) return;
        XRayClient.LOGGER.warn("[debug] Invariant broken – {}: {}", key, detail);
        Minecraft client = Minecraft.getInstance();
        // Reports from the integrated server thread reach the chat on the client thread.
        client.execute(() -> {
            if (client.level == null) return;
            Mc.localChat(client, Component.translatable("debug.betterxray.violation", key, detail)
                .withStyle(ChatFormatting.RED));
        });
    }

    /** First violation of every broken invariant since the last {@link #clear()}, oldest first. */
    public static synchronized List<String> violations() { return List.copyOf(violations); }

    /** Violations including repeats of already reported invariants. */
    public static synchronized int total() { return total; }

    public static synchronized void clear() {
        reported.clear();
        violations.clear();
        total = 0;
    }

    /** Throws if an invariant broke since the last {@link #clear()} (used by tests). */
    public static void assertClean(String where) {
        List<String> broken = violations();
        if (!broken.isEmpty()) throw new AssertionError("Debug invariants broken (" + where + "): " + String.join("; ", broken));
    }
}
