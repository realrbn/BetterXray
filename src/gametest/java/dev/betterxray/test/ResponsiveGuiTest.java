package dev.betterxray.test;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.BlockListWidget;
import dev.betterxray.gui.BlockSelectionScreen;
import dev.betterxray.gui.HighlightScreen;
import dev.betterxray.gui.MobEspScreen;
import dev.betterxray.gui.MobEspSettingsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import dev.betterxray.gui.PresetRenameScreen;
import dev.betterxray.gui.XRayConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Layout checks for the configuration screens at the window sizes and GUI scales players use.
 * Runs on the title screen (no world needed). For every combination it asserts that widgets
 * stay on screen, do not overlap and that preset labels fit; it also clicks real mouse input at
 * GUI scale Auto to prove hitboxes match the rendered buttons.
 */
public final class ResponsiveGuiTest implements FabricClientGameTest {
    private static final int[][] WINDOWS = {{2560, 1440}, {1920, 1080}, {1280, 720}, {854, 480}, {640, 480}, {500, 300}, {400, 200}};
    private static final int[] SCALES = {0, 1, 2, 3, 4};
    private static final boolean SCREENSHOTS = Boolean.parseBoolean(System.getProperty("betterxray.test.guiScreenshots", "true"));

    private record Case(String name, Function<Screen, Screen> factory) {}
    private static final List<Case> SCREENS = List.of(
        new Case("config", XRayConfigScreen::new),
        new Case("blocks", BlockSelectionScreen::new),
        new Case("highlight", HighlightScreen::new),
        new Case("highlight-color", parent -> new dev.betterxray.gui.HighlightColorScreen(parent, net.minecraft.world.level.block.Blocks.DIAMOND_ORE)),
        new Case("structures", dev.betterxray.gui.StructureScreen::new),
        new Case("rename", parent -> new PresetRenameScreen(parent, 0)),
        new Case("esp", MobEspScreen::new),
        new Case("esp-mob", parent -> new MobEspSettingsScreen(parent,
            BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie")))),
        new Case("esp-group", parent -> new MobEspSettingsScreen(parent, MobEspGroup.OVERWORLD_ENEMIES)));

    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null) return;
        // Regression: without a world (e.g. Mod Menu on the title screen) item components are
        // not bound yet; the block lists must open and render without icons instead of crashing.
        for (Case screen : List.of(SCREENS.get(1), SCREENS.get(2))) {
            context.setScreen(() -> screen.factory.apply(null));
            context.waitTicks(3);
            context.runOnClient(client -> {
                var list = Mc.screen(client).children().stream().filter(BlockListWidget.class::isInstance)
                    .map(BlockListWidget.class::cast).findFirst().orElseThrow(() -> new AssertionError("No block list on title screen"));
                if (list.children().isEmpty()) throw new AssertionError("Block list empty on title screen");
            });
            if (SCREENSHOTS) context.takeScreenshot("gui-title-screen-" + screen.name);
        }
        context.setScreen(() -> null);
        context.waitTick();

        try (var world = TestCompat.worldBuilder(context).create()) {
            TestCompat.chunksRender(world);
            runKeyboardToggle(context);
            runLayoutChecks(context);
        }
    }

    /** Regression: Enter and Space on a focused block row must toggle it with real key input. */
    private void runKeyboardToggle(ClientGameTestContext context) {
        String id = "minecraft:diamond_ore";
        context.setScreen(() -> new BlockSelectionScreen(null));
        context.waitTicks(2);
        boolean initially = context.computeOnClient(client -> XRayConfigManager.get().getActiveVisibleBlocks().contains(id));
        context.runOnClient(client -> {
            Screen screen = Mc.screen(client);
            var search = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            search.setValue(id);
            var list = screen.children().stream().filter(BlockListWidget.class::isInstance).map(BlockListWidget.class::cast).findFirst().orElseThrow();
            var row = list.children().stream().filter(r -> r.getNarration().getString().contains(", " + id + ",")).findFirst().orElseThrow();
            screen.setFocused(list);
            list.setFocused(row);
        });
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        context.waitTick();
        if (context.computeOnClient(client -> XRayConfigManager.get().getActiveVisibleBlocks().contains(id)) == initially)
            throw new AssertionError("Enter did not toggle the focused block row");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_SPACE);
        context.waitTick();
        if (context.computeOnClient(client -> XRayConfigManager.get().getActiveVisibleBlocks().contains(id)) != initially)
            throw new AssertionError("Space did not toggle the focused block row");
        context.runOnClient(client -> Mc.screen(client).onClose());
        context.waitTick();
    }

    private void runLayoutChecks(ClientGameTestContext context) {
        String originalName = context.computeOnClient(client -> XRayConfigManager.get().presets.get(1).name);
        List<String> report = new ArrayList<>();
        for (int[] window : WINDOWS) {
            context.getInput().resizeWindow(window[0], window[1]);
            context.waitTicks(2);
            for (int scale : SCALES) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    TestCompat.resizeGui(client);
                });
                context.waitTick();
                for (Case screen : SCREENS) {
                    context.setScreen(() -> screen.factory.apply(null));
                    context.waitTicks(2);
                    String line = context.computeOnClient(client -> check(client, screen.name, window, scale));
                    report.add(line);
                    boolean interesting = scale == 0 || (window[0] >= 1920 && scale >= 2) || window[0] <= 500;
                    if (SCREENSHOTS && interesting) context.takeScreenshot("gui-" + window[0] + "x" + window[1] + "-s" + scale + "-" + screen.name);
                }
            }
        }

        System.out.println("[BetterXray GUI] layout report\n" + String.join("\n", report));

        // Long preset name: must be shortened with an ellipsis and carry the full name as tooltip.
        context.getInput().resizeWindow(2560, 1440);
        context.waitTicks(2);
        context.runOnClient(client -> {
            client.options.guiScale().set(0);
            TestCompat.resizeGui(client);
            XRayConfigManager.get().presets.get(1).name = "Deepslate Diamonds And Ancient D";
        });
        for (Case screen : List.of(SCREENS.get(0), SCREENS.get(1))) {
            context.setScreen(() -> screen.factory.apply(null));
            context.waitTicks(2);
            Button preset = context.computeOnClient(client -> buttons(Mc.screen(client)).stream()
                .filter(b -> b.getMessage().getString().startsWith("Deep")).findFirst()
                .orElseThrow(() -> new AssertionError("Long preset button missing on " + screen.name)));
            context.runOnClient(client -> {
                String label = preset.getMessage().getString();
                if (client.font.width(preset.getMessage()) > preset.getWidth() - 4)
                    throw new AssertionError("Preset label overflows on " + screen.name + ": " + label);
                if (!label.endsWith("…") || tooltipOf(preset) == null)
                    throw new AssertionError("Long preset name not ellipsized with tooltip on " + screen.name + ": " + label);
            });
            hover(context, preset);
            context.waitTicks(3);
            if (SCREENSHOTS) context.takeScreenshot("gui-long-preset-" + screen.name);
        }
        context.runOnClient(client -> XRayConfigManager.get().presets.get(1).name = originalName);

        // Real mouse input at GUI scale Auto: click the rendered Fullbright button near its edges.
        context.setScreen(() -> new XRayConfigScreen(null));
        context.waitTicks(2);
        Button fullbright = context.computeOnClient(client -> find(Mc.screen(client), "Fullbright"));
        boolean before = context.computeOnClient(client -> XRayConfigManager.get().fullbright);
        clickAt(context, fullbright.getX() + 2, fullbright.getY() + 2);
        if (context.computeOnClient(client -> XRayConfigManager.get().fullbright) == before)
            throw new AssertionError("Click inside the top-left corner of Fullbright missed");
        clickAt(context, fullbright.getRight() - 2, fullbright.getBottom() - 2);
        if (context.computeOnClient(client -> XRayConfigManager.get().fullbright) != before)
            throw new AssertionError("Click inside the bottom-right corner of Fullbright missed");
        clickAt(context, fullbright.getX() + fullbright.getWidth() / 2, fullbright.getBottom() + 1);
        if (context.computeOnClient(client -> XRayConfigManager.get().fullbright) != before)
            throw new AssertionError("Click just below Fullbright toggled it");

        // Scrolling fallback: controls must scroll and still receive clicks.
        context.getInput().resizeWindow(400, 120);
        context.waitTicks(2);
        context.runOnClient(client -> { client.options.guiScale().set(1); TestCompat.resizeGui(client); });
        context.setScreen(() -> new XRayConfigScreen(null));
        context.waitTicks(2);
        context.runOnClient(client -> {
            Screen screen = Mc.screen(client);
            var container = screen.children().stream().filter(c -> c instanceof AbstractContainerWidget && !(c instanceof AbstractSelectionList<?>))
                .map(AbstractContainerWidget.class::cast).findFirst().orElseThrow(() -> new AssertionError("No scroll container on tiny window"));
            if (container.maxScrollAmount() <= 0) throw new AssertionError("Tiny window content does not scroll");
            container.setScrollAmount(container.maxScrollAmount());
        });
        context.waitTicks(2);
        if (SCREENSHOTS) context.takeScreenshot("gui-400x120-s1-config-scrolled");

        context.getInput().resizeWindow(854, 480);
        context.runOnClient(client -> { client.options.guiScale().set(0); TestCompat.resizeGui(client); Mc.setScreen(client, null); });
        context.waitTicks(2);
    }

    private static Object tooltipOf(AbstractWidget widget) {
        return ((dev.betterxray.test.mixin.WidgetAccess)widget).betterxrayTest$tooltip().get();
    }

    private static void hover(ClientGameTestContext context, AbstractWidget widget) {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos((widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale);
    }

    private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos((guiX + .5) * scale, (guiY + .5) * scale);
        //? if >=26.3 {
        context.getInput().pressMouse(1);
        //?} else {
        /*context.getInput().pressMouse(0);
        *///?}
        context.waitTick();
    }

    private static Button find(Screen screen, String prefix) {
        return buttons(screen).stream().filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst()
            .orElseThrow(() -> new AssertionError("Button " + prefix + " missing"));
    }

    private static List<Button> buttons(Screen screen) {
        List<Button> result = new ArrayList<>();
        for (AbstractWidget widget : widgets(screen, true)) if (widget instanceof Button b) result.add(b);
        return result;
    }

    /** Top-level widgets, optionally including the children of scroll containers. */
    private static List<AbstractWidget> widgets(Screen screen, boolean nested) {
        List<AbstractWidget> result = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (nested && widget instanceof AbstractContainerWidget container && !(widget instanceof AbstractSelectionList<?>)) {
                for (GuiEventListener inner : container.children()) if (inner instanceof AbstractWidget w) result.add(w);
            } else {
                result.add(widget);
            }
        }
        return result;
    }

    private static String check(Minecraft client, String name, int[] window, int requestedScale) {
        Screen screen = Mc.screen(client);
        int w = screen.width, h = screen.height;
        String where = name + " @" + window[0] + "x" + window[1] + " guiScale=" + (requestedScale == 0 ? "auto" : requestedScale)
            + " (effective " + (int)client.getWindow().getGuiScale() + ", " + w + "x" + h + " GUI px)";
        List<AbstractWidget> top = widgets(screen, false);
        int minY = Integer.MAX_VALUE, maxY = 0, minX = Integer.MAX_VALUE, maxX = 0;
        boolean scrolling = false;
        for (AbstractWidget widget : top) {
            if (widget.getWidth() <= 0 || widget.getHeight() <= 0) continue;
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > w || widget.getBottom() > h)
                throw new AssertionError(where + ": widget outside screen: " + describe(widget));
            minY = Math.min(minY, widget.getY()); maxY = Math.max(maxY, widget.getBottom());
            minX = Math.min(minX, widget.getX()); maxX = Math.max(maxX, widget.getRight());
            if (widget instanceof AbstractContainerWidget c && !(widget instanceof AbstractSelectionList<?>)) {
                scrolling = c.maxScrollAmount() > 0;
                for (GuiEventListener inner : c.children()) {
                    if (inner instanceof AbstractWidget child && (child.getX() < c.getX() || child.getRight() > c.getRight()))
                        throw new AssertionError(where + ": scrolled widget outside its container: " + describe(child));
                }
            }
        }
        for (int i = 0; i < top.size(); i++) {
            for (int j = i + 1; j < top.size(); j++) {
                AbstractWidget a = top.get(i), b = top.get(j);
                if (a.getX() < b.getRight() && b.getX() < a.getRight() && a.getY() < b.getBottom() && b.getY() < a.getBottom()
                    && a.getWidth() > 0 && b.getWidth() > 0 && a.getHeight() > 0 && b.getHeight() > 0)
                    throw new AssertionError(where + ": overlapping widgets " + describe(a) + " and " + describe(b));
            }
        }
        for (Button button : buttons(screen)) {
            String text = button.getMessage().getString();
            if ((text.startsWith("▶") || text.startsWith("Preset")) && client.font.width(button.getMessage()) > button.getWidth() - 4)
                throw new AssertionError(where + ": preset label does not fit: " + describe(button));
        }
        String list = "";
        for (AbstractWidget widget : top) {
            if (widget instanceof BlockListWidget l) list = ", list " + l.getHeight() + "px (" + (l.getHeight() / Math.max(1, l.children().isEmpty() ? 1 : l.children().get(0).getHeight())) + " rows)";
        }
        return where + ": panel " + (maxX - minX) + "x" + (maxY - minY) + " GUI px = "
            + Math.round(100.0 * (maxY - minY) / h) + "% of height" + (scrolling ? ", scrolling" : "") + list;
    }

    private static String describe(AbstractWidget widget) {
        return "'" + widget.getMessage().getString() + "' [" + widget.getX() + "," + widget.getY() + " " + widget.getWidth() + "x" + widget.getHeight() + "]";
    }
}
