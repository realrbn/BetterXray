package dev.betterxray.validation;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.PresetTransfer;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.BlockSelectionScreen;
import dev.betterxray.gui.HighlightScreen;
import dev.betterxray.gui.XRayConfigScreen;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static dev.betterxray.validation.GuiChecks.check;

/**
 * 1.7.0 features: clipboard export/import of a preset (parser, buttons, confirmation), "Add From
 * Preset" in the highlight screen, and the far-ESP helpers for the ESP "Full View Distance" steps
 * in {@link FeatureChecks}.
 */
final class TransferChecks {
    private TransferChecks() {}

    /** Distance of the far zombie: beyond its 128-block tracking range, inside render distance 12. */
    static final int FAR_DISTANCE = 160;

    /** Parser, clipboard round trip through the GUI buttons and the highlight import; no world needed. */
    static String presets(Minecraft mc) {
        Screen previous = Mc.screen(mc);
        String clipboard = mc.keyboardHandler.getClipboard();
        try {
            parser();
            XRayConfigManager.reset();
            var c = XRayConfigManager.get();
            Set<String> source = new LinkedHashSet<>(c.presets.get(0).visibleBlocks);
            check(!source.isEmpty(), "Default preset has entries");

            var blocks = new BlockSelectionScreen(null);
            Mc.setScreen(mc, blocks);
            button(blocks, "Export").onPress();
            check(mc.keyboardHandler.getClipboard().equals(PresetTransfer.export(c.presets.get(0))), "Export copies the active preset");

            // Import into preset 3 through the confirmation screen.
            c.setActivePreset(2);
            Mc.setScreen(mc, blocks = new BlockSelectionScreen(null));
            button(blocks, "Import").onPress();
            check(Mc.screen(mc) instanceof ConfirmScreen, "Import asks for confirmation, got " + Mc.screen(mc));
            check(c.presets.get(2).visibleBlocks.isEmpty(), "Nothing imported before confirming");
            button(Mc.screen(mc), "Yes").onPress();
            check(Mc.screen(mc) == blocks, "Back to the block list after confirming");
            check(c.presets.get(2).visibleBlocks.equals(source) && c.presets.get(2).name.equals(c.presets.get(0).name),
                "Imported preset equals the exported one");

            // Cancelled and invalid imports leave the preset unchanged.
            c.presets.get(2).visibleBlocks.clear();
            button(blocks, "Import").onPress();
            button(Mc.screen(mc), "No").onPress();
            check(c.presets.get(2).visibleBlocks.isEmpty() && Mc.screen(mc) == blocks, "Cancelled import changes nothing");
            mc.keyboardHandler.setClipboard("not a preset");
            button(blocks, "Import").onPress();
            check(Mc.screen(mc) == blocks && c.presets.get(2).visibleBlocks.isEmpty(), "Invalid clipboard: no confirmation, nothing imported");

            var config = new XRayConfigScreen(null);
            Mc.setScreen(mc, config);
            button(config, "Export");
            button(config, "Import");

            // Highlight: "Add From Preset" adds the active preset's entries once.
            c.setActivePreset(0);
            c.highlightBlocks.clear();
            c.highlightBlocks.add("minecraft:dirt");
            var highlight = new HighlightScreen(null);
            Mc.setScreen(mc, highlight);
            var add = button(highlight, "Add From Preset");
            check(add.active && add.getMessage().getString().contains("(" + source.size() + ")"), "Add From Preset counts " + add.getMessage().getString());
            add.onPress();
            check(c.highlightBlocks.containsAll(source) && c.highlightBlocks.contains("minecraft:dirt")
                && c.highlightBlocks.size() == source.size() + 1, "Add From Preset adds every entry and keeps the others");
            check(!button(Mc.screen(mc), "Add From Preset").active, "Add From Preset inactive when nothing is missing");
        } finally {
            mc.keyboardHandler.setClipboard(clipboard);
            XRayConfigManager.reset();
            XRayConfigManager.save();
            Mc.setScreen(mc, previous);
        }
        return "preset export/import and Add From Preset";
    }

    private static void parser() {
        check(failure("").equals("empty"), "Empty clipboard");
        check(failure("hello").equals("not_preset"), "Random text");
        check(failure("{\"betterxray_preset\":2,\"blocks\":[]}").equals("newer_version"), "Newer format");
        check(failure("{\"betterxray_preset\":1}").equals("invalid"), "Missing blocks");
        check(failure("{\"betterxray_preset\":1,\"blocks\":" + "[".repeat(100_000)).equals("not_preset"), "Deep nesting");
        var result = PresetTransfer.parse("{\"betterxray_preset\":1,\"name\":\" Ores\\u0007 \","
            + "\"blocks\":[\"minecraft:diamond_ore\",\"#c:ores\",\"bad id!\",\"missing:block\",\"minecraft:diamond_ore\"]}");
        check(result instanceof PresetTransfer.Imported, "Valid preset parsed: " + result);
        var imported = (PresetTransfer.Imported)result;
        check(imported.name().equals("Ores") && imported.blocks().equals(Set.of("minecraft:diamond_ore", "#c:ores"))
            && imported.skippedInvalid() == 1 && imported.skippedUnknown() == 1, "Normalized import: " + imported);
    }

    private static String failure(String text) {
        var result = PresetTransfer.parse(text);
        return result instanceof PresetTransfer.Failure failure ? failure.reason() : "imported";
    }

    /** Client-side zombies beyond {@code FAR_DISTANCE - 8} blocks on the x axis. */
    static List<Entity> farZombies(Minecraft mc) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering())
            if (entity.getType() == EntityType.ZOMBIE && entity.getX() > FAR_DISTANCE - 8) result.add(entity);
        return result;
    }

    static boolean hasEsp(Entity entity) { return MobEspRenderer.settings(entity) != null; }

    /** Every widget of the screen, also inside scroll areas and containers. */
    private static void collect(List<? extends GuiEventListener> children, List<AbstractWidget> out) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget) out.add(widget);
            if (child instanceof ContainerEventHandler container) collect(container.children(), out);
        }
    }

    static Button button(Screen screen, String prefix) {
        List<AbstractWidget> widgets = new ArrayList<>();
        collect(screen.children(), widgets);
        return widgets.stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst()
            .orElseThrow(() -> new AssertionError("Button " + prefix + " on " + screen.getClass().getSimpleName()));
    }
}
