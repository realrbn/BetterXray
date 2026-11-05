package dev.betterxray.validation;

import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Responsive-GUI and configuration-migration checks that need no world. */
final class GuiChecks {
    private GuiChecks() {}

    /**
     * Scaled GUI sizes: 854x480 at scale 1 and 2, 1080p/1440p at "Auto" (480x270, 427x240),
     * 1280x720 at scale 2/3 (640x360, 427x240), the smallest auto-scaled size (320x240) and tiny windows at scale 1.
     */
    private static final int[][] SIZES = {{854, 480}, {640, 360}, {480, 270}, {427, 240}, {320, 240}, {400, 200}, {400, 120}, {300, 160}};

    static String run(Minecraft mc) {
        int screens = 0;
        for (int[] size : SIZES) {
            screens += layout(mc, () -> new XRayConfigScreen(null), size);
            screens += layout(mc, () -> new BlockSelectionScreen(null), size);
            screens += layout(mc, () -> new HighlightScreen(null), size);
            screens += layout(mc, () -> new PresetRenameScreen(null, 0), size);
            screens += layout(mc, () -> new MobEspScreen(null), size);
            screens += layout(mc, () -> new MobEspSettingsScreen(null, dev.betterxray.config.MobEspGroup.PLAYERS), size);
            screens += layout(mc, () -> new MobEspSettingsScreen(null, net.minecraft.world.entity.EntityType.ZOMBIE), size);
        }
        // Main screen: no scrolling down to 427x240 (GUI scale Auto at 1440p), scrolling on 213x120.
        check(scroll(open(mc, new XRayConfigScreen(null), 427, 240)) == null, "Config fits without scrolling at 427x240");
        check(scroll(open(mc, new XRayConfigScreen(null), 854, 480)) == null, "Config fits without scrolling at 854x480");
        var config = open(mc, new XRayConfigScreen(null), 400, 120);
        ScrollArea area = scroll(config);
        check(area != null && area.maxScroll() > 0, "Config scrolls at 400x120");
        //? if >=1.20.2 {
        area.mouseScrolled(area.getX() + 4, area.getY() + 4, 0, -3);
        //?} else
        /*area.mouseScrolled(area.getX() + 4, area.getY() + 4, -3);*/
        check(area.scrollAmount() > 0, "Mouse wheel scrolls the config controls");
        AbstractWidget last = null;
        for (GuiEventListener child : area.children()) if (child instanceof AbstractWidget w) last = w;
        area.setFocused(last);
        check(last.getY() >= area.getY() && last.getY() + last.getHeight() <= area.getY() + area.getHeight(), "Focused widget scrolled into view");
        check(scroll(open(mc, new HighlightScreen(null), 400, 120)) != null, "Highlight controls scroll on 400x120");

        // Highlight screen controls change the configuration.
        var c = XRayConfigManager.get();
        var highlight = open(mc, new HighlightScreen(null), 640, 360);
        boolean before = c.highlightEnabled;
        button(highlight, "Enable Highlight").onPress();
        check(c.highlightEnabled != before, "Highlight toggle");
        button(highlight, "Enable Highlight").onPress();
        var range = widgets(highlight).stream().filter(AbstractSliderButton.class::isInstance)
            .filter(w -> w.getMessage().getString().startsWith("Highlight Range")).findFirst().orElseThrow();
        activate(range);
        range.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        check(c.highlightRangeBlocks > 0 && c.highlightRangeBlocks % XRayConfig.RANGE_STEP_BLOCKS == 0, "Highlight range slider steps in blocks");
        c.highlightRangeBlocks = 0;

        // Opacity slider reaches 0 % (unselected terrain hidden) and is capped at 80 %.
        var main = open(mc, new XRayConfigScreen(null), 640, 360);
        var opacity = widgets(main).stream().filter(AbstractSliderButton.class::isInstance)
            .filter(w -> w.getMessage().getString().startsWith("Terrain Opacity")).findFirst().orElseThrow();
        activate(opacity);
        for (int i = 0; i < 400; i++) opacity.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        check(c.terrainOpacity == 0f && opacity.getMessage().getString().contains("0%"), "Opacity slider reaches 0%");
        for (int i = 0; i < 400; i++) opacity.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        check(Math.abs(c.terrainOpacity - .80f) < .001f, "Opacity slider capped at 80%");
        button(main, "Mob ESP").onPress();
        check(mc.screen instanceof MobEspScreen, "Mob ESP button opens the picker");
        mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        c.terrainOpacity = .15f;
        return screens + " screen layouts";
    }

    private static int layout(Minecraft mc, Supplier<Screen> factory, int[] size) {
        Screen screen = open(mc, factory.get(), size[0], size[1]);
        String where = screen.getClass().getSimpleName() + " at " + size[0] + "x" + size[1];
        List<int[]> boxes = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            int[] box;
            if (child instanceof ScrollArea area) box = new int[]{area.getX(), area.getY(), area.getWidth(), area.getHeight()};
            else if (child instanceof AbstractWidget w) box = new int[]{w.getX(), w.getY(), w.getWidth(), w.getHeight()};
            else if (child instanceof BlockListWidget list) box = new int[]{list.listX(), list.listY(), list.listWidth(), list.listHeight()};
            else continue;
            check(box[0] >= 0 && box[1] >= 0 && box[0] + box[2] <= size[0] && box[1] + box[3] <= size[1],
                where + ": " + child.getClass().getSimpleName() + " " + java.util.Arrays.toString(box) + " inside screen");
            for (int[] other : boxes) check(!overlap(box, other), where + ": no overlapping controls " + java.util.Arrays.toString(box) + " / " + java.util.Arrays.toString(other));
            boxes.add(box);
        }
        for (GuiEventListener child : screen.children())
            // Down to 240 GUI px the list keeps at least one full row; tiny windows still show part of one.
            if (child instanceof BlockListWidget list) check(list.listHeight() >= (size[1] >= 240 ? BlockListWidget.COMPACT_ROW_HEIGHT : 20), where + ": block list keeps a visible row");
        return 1;
    }

    /** Focuses a slider and toggles it into value-changing mode with Enter, like a keyboard user. */
    private static void activate(AbstractWidget slider) {
        slider.setFocused(true);
        if (!slider.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0)) slider.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[2] > 0 && a[3] > 0 && b[2] > 0 && b[3] > 0
            && a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    private static Screen open(Minecraft mc, Screen screen, int width, int height) {
        screen.init(mc, width, height);
        return screen;
    }

    private static ScrollArea scroll(Screen screen) {
        return screen.children().stream().filter(ScrollArea.class::isInstance).map(ScrollArea.class::cast).findFirst().orElse(null);
    }

    /** Top-level widgets plus the widgets inside a scroll area. */
    private static List<AbstractWidget> widgets(Screen screen) {
        List<AbstractWidget> result = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w) result.add(w);
            if (child instanceof ScrollArea area) for (GuiEventListener inner : area.children()) if (inner instanceof AbstractWidget w) result.add(w);
        }
        return result;
    }

    private static Button button(Screen screen, String prefix) {
        return widgets(screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError("Button " + prefix));
    }

    /**
     * Loads hand-written configuration files through the real manager: a Forge/NeoForge 1.0.1
     * file, a Fabric file with pre-1.0.7 chunk range and pre-group (first 1.0.6) mob ESP, and out
     * of range values. The previous configuration is restored afterwards.
     */
    static String migrations() throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("betterxray.json");
        String saved = Files.exists(file) ? Files.readString(file) : null;
        try {
            Files.writeString(file, """
                {"enabled":false,"fullbright":true,"terrainOpacity":0.27,"showWater":true,"showLava":false,"activePreset":1,
                 "presets":[{"name":"A","visibleBlocks":["minecraft:diamond_ore"]},{"name":"Ores","visibleBlocks":["minecraft:gold_ore","missing:block"]},{"name":"P3","visibleBlocks":[]}]}""");
            XRayConfigManager.load();
            var c = XRayConfigManager.get();
            check(c.activePreset == 1 && c.getActivePreset().name.equals("Ores") && c.getActiveVisibleBlocks().contains("missing:block")
                && c.fullbright && !c.showLava && Math.abs(c.terrainOpacity - .27f) < .001f, "1.0.1 file keeps presets and settings");
            check(c.mobEspGroups.size() == dev.betterxray.config.MobEspGroup.values().length
                && c.mobEspGroups.values().stream().noneMatch(g -> g.enabled) && c.mobEsp.isEmpty()
                && c.mobEspEnabled && c.updateCheckEnabled && !c.highlightEnabled && c.highlightRangeBlocks == 0, "1.0.1 file gets 1.1.0 defaults");

            Files.writeString(file, """
                {"terrainOpacity":0.95,"highlightRangeChunks":4,"highlightBlocks":["minecraft:diamond_ore"],
                 "mobEsp":{"minecraft:zombie":{"enabled":true},"minecraft:cow":{"enabled":false,"style":"OUTLINE","color":-16711681},
                           "minecraft:pig":{"enabled":false,"style":"BOX","color":-65536}},
                 "mobEspRangeBlocks":3,"presets":[]}""");
            XRayConfigManager.load();
            c = XRayConfigManager.get();
            check(c.highlightRangeBlocks == 64 && c.highlightRangeChunks == null, "Chunk highlight range migrated to blocks");
            check(Math.abs(c.terrainOpacity - .80f) < .001f && c.mobEspRangeBlocks == XRayConfig.RANGE_STEP_BLOCKS, "Out-of-range values clamped");
            var zombie = c.mobEsp.get("minecraft:zombie");
            check(zombie != null && Boolean.TRUE.equals(zombie.enabled) && zombie.style == XRayConfig.EspStyle.OUTLINE
                && zombie.color == XRayConfig.LEGACY_ESP_COLOR, "Legacy enabled mob keeps full override");
            check(!c.mobEsp.containsKey("minecraft:cow"), "Legacy disabled default mob follows its group");
            var pig = c.mobEsp.get("minecraft:pig");
            check(pig != null && pig.enabled == null && pig.style == XRayConfig.EspStyle.BOX, "Legacy disabled mob keeps custom look");
            check(c.presets.size() == 3 && c.presets.get(0).visibleBlocks.contains("minecraft:diamond_ore"), "Missing presets repaired");

            c.terrainOpacity = 0f;
            c.sanitize();
            check(c.terrainOpacity == 0f, "0% opacity is a valid setting");
            XRayConfigManager.save();
            XRayConfigManager.load();
            check(XRayConfigManager.get().terrainOpacity == 0f && XRayConfigManager.get().highlightBlocks.contains("minecraft:diamond_ore"), "1.1.0 fields round-trip");
            return "migrations ok";
        } finally {
            if (saved != null) Files.writeString(file, saved); else Files.deleteIfExists(file);
            XRayConfigManager.load();
        }
    }

    static Button buttonOf(Screen screen, String prefix) { return button(screen, prefix); }
    static Screen openScreen(Minecraft mc, Screen screen, int width, int height) { return open(mc, screen, width, height); }

    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
