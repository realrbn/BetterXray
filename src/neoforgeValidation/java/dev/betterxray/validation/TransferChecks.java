package dev.betterxray.validation;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.PresetTransfer;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.BlockSelectionScreen;
import dev.betterxray.gui.HighlightScreen;
import dev.betterxray.gui.XRayConfigScreen;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * 1.7.0 features without a world (NeoForge and Forge fixture): the preset parser, the clipboard
 * round trip through the Export/Import buttons and the confirmation, and "Add From Preset" in the
 * highlight screen. The far-ESP steps live in {@link NeoForgeValidation}.
 */
final class TransferChecks {
    private TransferChecks() {}

    /** Runs every check; returns the passed check names. */
    static List<String> presets(Minecraft mc) {
        List<String> passed = new ArrayList<>();
        Screen previous = Mc.screen(mc);
        String clipboard = mc.keyboardHandler.getClipboard();
        try {
            check(passed, failure("").equals("empty"), "Preset import: empty clipboard rejected");
            check(passed, failure("hello").equals("not_preset"), "Preset import: random text rejected");
            check(passed, failure("{\"betterxray_preset\":2,\"blocks\":[]}").equals("newer_version"), "Preset import: newer format rejected");
            check(passed, failure("{\"betterxray_preset\":1}").equals("invalid"), "Preset import: missing blocks rejected");
            check(passed, failure("{\"betterxray_preset\":1,\"blocks\":" + "[".repeat(100_000)).equals("not_preset"), "Preset import: deep nesting rejected");
            var result = PresetTransfer.parse("{\"betterxray_preset\":1,\"name\":\" Ores\\u0007 \","
                + "\"blocks\":[\"minecraft:diamond_ore\",\"#c:ores\",\"bad id!\",\"missing:block\",\"minecraft:diamond_ore\"]}");
            check(passed, result instanceof PresetTransfer.Imported imported && imported.name().equals("Ores")
                && imported.blocks().equals(Set.of("minecraft:diamond_ore", "#c:ores"))
                && imported.skippedInvalid() == 1 && imported.skippedUnknown() == 1, "Preset import normalizes entries (" + result + ")");

            XRayConfigManager.reset();
            var c = XRayConfigManager.get();
            Set<String> source = new LinkedHashSet<>(c.presets.get(0).visibleBlocks);
            var blocks = new BlockSelectionScreen(null);
            Mc.setScreen(mc, blocks);
            press(button(blocks, "Export"));
            check(passed, mc.keyboardHandler.getClipboard().equals(PresetTransfer.export(c.presets.get(0))), "Export copies the active preset");

            c.setActivePreset(2);
            Mc.setScreen(mc, blocks = new BlockSelectionScreen(null));
            press(button(blocks, "Import"));
            check(passed, Mc.screen(mc) instanceof ConfirmScreen && c.presets.get(2).visibleBlocks.isEmpty(), "Import asks for confirmation first");
            press(button(Mc.screen(mc), "Yes"));
            check(passed, Mc.screen(mc) == blocks && c.presets.get(2).visibleBlocks.equals(source)
                && c.presets.get(2).name.equals(c.presets.get(0).name), "Confirmed import equals the exported preset");

            c.presets.get(2).visibleBlocks.clear();
            press(button(blocks, "Import"));
            press(button(Mc.screen(mc), "No"));
            check(passed, Mc.screen(mc) == blocks && c.presets.get(2).visibleBlocks.isEmpty(), "Cancelled import changes nothing");
            mc.keyboardHandler.setClipboard("not a preset");
            press(button(blocks, "Import"));
            check(passed, Mc.screen(mc) == blocks && c.presets.get(2).visibleBlocks.isEmpty(), "Invalid clipboard imports nothing");

            var config = new XRayConfigScreen(null);
            Mc.setScreen(mc, config);
            button(config, "Export");
            button(config, "Import");
            passed.add("Config screen has Export/Import");

            c.setActivePreset(0);
            c.highlightBlocks.clear();
            c.highlightBlocks.add("minecraft:dirt");
            var highlight = new HighlightScreen(null);
            Mc.setScreen(mc, highlight);
            var add = button(highlight, "Add From Preset");
            check(passed, add.active && add.getMessage().getString().contains("(" + source.size() + ")"), "Add From Preset counts the missing entries");
            press(add);
            check(passed, c.highlightBlocks.containsAll(source) && c.highlightBlocks.contains("minecraft:dirt")
                && c.highlightBlocks.size() == source.size() + 1, "Add From Preset adds the preset and keeps the list");
            check(passed, !button(Mc.screen(mc), "Add From Preset").active, "Add From Preset inactive when nothing is missing");
        } finally {
            mc.keyboardHandler.setClipboard(clipboard);
            XRayConfigManager.reset();
            XRayConfigManager.save();
            Mc.setScreen(mc, previous);
        }
        return passed;
    }

    private static String failure(String text) {
        var result = PresetTransfer.parse(text);
        return result instanceof PresetTransfer.Failure failure ? failure.reason() : "imported";
    }

    private static void check(List<String> passed, boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        passed.add(message);
    }

    /** Button presses take an input event since 1.21.9. */
    private static void press(Button button) {
        //? if >=1.21.11 {
        button.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
        //?} else
        /*button.onPress();*/
    }

    private static void collect(List<? extends GuiEventListener> children, List<AbstractWidget> out) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget) out.add(widget);
            if (child instanceof ContainerEventHandler container) collect(container.children(), out);
        }
    }

    private static Button button(Screen screen, String prefix) {
        List<AbstractWidget> widgets = new ArrayList<>();
        collect(screen.children(), widgets);
        return widgets.stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst()
            .orElseThrow(() -> new AssertionError("Button " + prefix + " on " + screen.getClass().getSimpleName()));
    }
}
