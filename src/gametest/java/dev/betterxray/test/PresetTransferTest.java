package dev.betterxray.test;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.PresetTransfer;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.BlockSelectionScreen;
import dev.betterxray.gui.XRayConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preset clipboard export/import (Fabric 26.3): round trip, hostile and broken input that must
 * end in a clean failure instead of an exception, and the real Export/Import buttons including
 * the confirmation screen.
 */
public final class PresetTransferTest implements FabricClientGameTest {
    private static final String VALID = "{\"betterxray_preset\":1,\"name\":\"Imported\",\"blocks\":"
        + "[\"minecraft:gold_block\",\"#c:ores\",\"nope:not_a_block\",\"Bad Entry!\",5,null,\"IRON_ORE\"]}";

    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.skip(this) || !PresetTransfer.TARGET) return;
        if (System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            checkRoundTrip();
            checkHostileInput();
            checkEntries();
        });
        String clipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
        try {
            runGui(context);
        } finally {
            context.runOnClient(client -> {
                client.keyboardHandler.setClipboard(clipboard == null ? "" : clipboard);
                XRayConfigManager.reset();
                XRayConfigManager.save();
            });
            context.setScreen(() -> null);
        }
        System.out.println("[BetterXray Presets] preset transfer checks passed");
    }

    private static void checkRoundTrip() {
        var preset = new XRayConfig.XRayPreset("Ores \"&\" Chests ✓", new LinkedHashSet<>(List.of(
            "minecraft:diamond_ore", "minecraft:chest", "#c:ores")));
        String text = PresetTransfer.export(preset);
        if (text.contains("\n")) throw new AssertionError("Export must be a single line: " + text);
        var imported = imported(PresetTransfer.parse(text), "round trip");
        if (!preset.name.equals(imported.name()) || !preset.visibleBlocks.equals(imported.blocks())
            || imported.skippedInvalid() != 0 || imported.skippedUnknown() != 0)
            throw new AssertionError("Round trip changed the preset: " + imported);
        // Surrounding whitespace (copied from chat or a forum post) is fine.
        imported(PresetTransfer.parse("\n  " + text + "  \r\n"), "padded");
        // Default preset 1 survives unchanged.
        var defaults = XRayConfig.createDefaultPreset(0);
        if (!imported(PresetTransfer.parse(PresetTransfer.export(defaults)), "defaults").blocks().equals(defaults.visibleBlocks))
            throw new AssertionError("Default preset round trip failed");
    }

    private static void checkHostileInput() {
        expectFailure(null, "empty");
        expectFailure("", "empty");
        expectFailure("   \n\t", "empty");
        expectFailure("hello world", "not_preset");
        expectFailure("{", "not_preset");
        expectFailure("{}", "not_preset");
        expectFailure("[\"minecraft:stone\"]", "not_preset");
        expectFailure("12345", "not_preset");
        expectFailure("null", "not_preset");
        expectFailure("{\"name\":\"x\",\"blocks\":[]}", "not_preset");
        expectFailure("{\"betterxray_preset\":\"1\",\"blocks\":[]}", "not_preset");
        expectFailure("{\"betterxray_preset\":true,\"blocks\":[]}", "not_preset");
        expectFailure("{\"betterxray_preset\":[1],\"blocks\":[]}", "not_preset");
        expectFailure("{\"betterxray_preset\":1}", "invalid");
        expectFailure("{\"betterxray_preset\":1,\"blocks\":{}}", "invalid");
        expectFailure("{\"betterxray_preset\":1,\"blocks\":\"minecraft:stone\"}", "invalid");
        expectFailure("{\"betterxray_preset\":1,\"name\":42,\"blocks\":[]}", "invalid");
        expectFailure("{\"betterxray_preset\":1,\"name\":{},\"blocks\":[]}", "invalid");
        expectFailure("{\"betterxray_preset\":0,\"blocks\":[]}", "invalid");
        expectFailure("{\"betterxray_preset\":-3,\"blocks\":[]}", "invalid");
        expectFailure("{\"betterxray_preset\":1.5,\"blocks\":[]}", "invalid");
        expectFailure("{\"betterxray_preset\":1e400,\"blocks\":[]}", "invalid");
        expectFailure("{\"betterxray_preset\":2,\"blocks\":[]}", "newer_version");
        expectFailure("{\"betterxray_preset\":1,\"blocks\":[\"minecraft:stone\"]} trailing", "not_preset");
        expectFailure("{\"betterxray_preset\":1,\"blocks\":[\"minecraft:stone\"", "not_preset");
        // Absurd nesting must not overflow the stack.
        expectFailure("{\"betterxray_preset\":1,\"blocks\":" + "[".repeat(200_000) + "]".repeat(200_000) + "}", null);
        expectFailure("{\"betterxray_preset\":1,\"blocks\":[\"" + "a".repeat(PresetTransfer.MAX_INPUT_LENGTH) + "\"]}", "too_large");
        StringBuilder many = new StringBuilder("{\"betterxray_preset\":1,\"blocks\":[");
        for (int i = 0; i <= PresetTransfer.MAX_ENTRIES; i++) many.append(i == 0 ? "" : ",").append("\"minecraft:stone\"");
        expectFailure(many.append("]}").toString(), "too_large");
        // Binary garbage.
        StringBuilder garbage = new StringBuilder();
        var random = new java.util.Random(42);
        for (int i = 0; i < 5000; i++) garbage.append((char)random.nextInt(0x10000));
        expectFailure(garbage.toString(), null);
        expectFailure("{" + garbage, null);
    }

    private static void checkEntries() {
        var imported = imported(PresetTransfer.parse(VALID), "mixed entries");
        if (!imported.blocks().equals(Set.of("minecraft:gold_block", "#c:ores", "minecraft:iron_ore")))
            throw new AssertionError("Wrong imported entries: " + imported.blocks());
        if (imported.skippedInvalid() != 3 || imported.skippedUnknown() != 1)
            throw new AssertionError("Wrong skip counts: " + imported);
        // Missing or blank name keeps the current one; long names and control characters are cleaned.
        if (imported(PresetTransfer.parse("{\"betterxray_preset\":1,\"blocks\":[]}"), "no name").name() != null)
            throw new AssertionError("Missing name must keep the current name");
        if (imported(PresetTransfer.parse("{\"betterxray_preset\":1,\"name\":\"  \\n \",\"blocks\":[]}"), "blank").name() != null)
            throw new AssertionError("Blank name must keep the current name");
        String longName = imported(PresetTransfer.parse("{\"betterxray_preset\":1,\"name\":\"" + "x".repeat(500)
            + "\",\"blocks\":[]}"), "long name").name();
        if (longName.length() != XRayConfig.MAX_PRESET_NAME_LENGTH) throw new AssertionError("Name not shortened: " + longName.length());
        String clean = imported(PresetTransfer.parse("{\"betterxray_preset\":1,\"name\":\"A\\u0000B\\u001bC\\tD\",\"blocks\":[]}"), "control").name();
        if (!clean.equals("ABCD")) throw new AssertionError("Control characters kept: " + clean);
        // Duplicates collapse.
        if (imported(PresetTransfer.parse("{\"betterxray_preset\":1,\"blocks\":[\"minecraft:stone\",\"stone\",\"MINECRAFT:STONE\"]}"),
            "duplicates").blocks().size() != 1) throw new AssertionError("Duplicates not collapsed");
    }

    private static void runGui(ClientGameTestContext context) {
        context.setScreen(() -> new XRayConfigScreen(null));
        context.waitTicks(3);
        context.runOnClient(client -> {
            button(client, "Export").onPress(null);
            var exported = imported(PresetTransfer.parse(client.keyboardHandler.getClipboard()), "export button");
            if (!exported.blocks().equals(XRayConfig.createDefaultPreset(0).visibleBlocks))
                throw new AssertionError("Export button copied the wrong preset");
            // Garbage in the clipboard: no confirmation, preset untouched.
            client.keyboardHandler.setClipboard("definitely not a preset");
            button(client, "Import").onPress(null);
        });
        context.waitTicks(2);
        context.takeScreenshot("preset-import-failed-toast");
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof XRayConfigScreen)) throw new AssertionError("Failed import left the config screen: " + Mc.screen(client));
            if (!XRayConfigManager.get().presets.get(0).visibleBlocks.equals(XRayConfig.createDefaultPreset(0).visibleBlocks))
                throw new AssertionError("Failed import changed the preset");
            // Valid preset, then cancel.
            client.keyboardHandler.setClipboard(VALID);
            button(client, "Import").onPress(null);
        });
        context.waitTicks(2);
        context.takeScreenshot("preset-import-confirm");
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof ConfirmScreen)) throw new AssertionError("No confirmation before import: " + Mc.screen(client));
            button(client, "No").onPress(null);
            if (!(Mc.screen(client) instanceof XRayConfigScreen)) throw new AssertionError("Cancel did not return to the config screen");
            if (!"Preset 1".equals(XRayConfigManager.get().presets.get(0).name)) throw new AssertionError("Cancelled import changed the preset");
            // Switch to preset 2 and import into it from the block list screen.
            XRayConfigManager.get().setActivePreset(1);
            Mc.setScreen(client, new BlockSelectionScreen(null));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            button(client, "Import").onPress(null);
            if (!(Mc.screen(client) instanceof ConfirmScreen)) throw new AssertionError("No confirmation in the block list screen");
            button(client, "Yes").onPress(null);
            if (!(Mc.screen(client) instanceof BlockSelectionScreen)) throw new AssertionError("Import did not return to the block list");
            var config = XRayConfigManager.get();
            var target = config.presets.get(1);
            if (!"Imported".equals(target.name) || !target.visibleBlocks.equals(Set.of("minecraft:gold_block", "#c:ores", "minecraft:iron_ore")))
                throw new AssertionError("Import applied wrongly: " + target.name + " " + target.visibleBlocks);
            if (!config.presets.get(0).visibleBlocks.equals(XRayConfig.createDefaultPreset(0).visibleBlocks))
                throw new AssertionError("Import touched another preset");
            // The block list screen exports the active preset too.
            button(client, "Export").onPress(null);
            if (!"Imported".equals(imported(PresetTransfer.parse(client.keyboardHandler.getClipboard()), "block screen export").name()))
                throw new AssertionError("Block list export copied the wrong preset");
        });
        context.waitTicks(2);
        context.takeScreenshot("preset-import-done");
    }

    private static PresetTransfer.Imported imported(PresetTransfer.Result result, String what) {
        if (result instanceof PresetTransfer.Imported imported) return imported;
        throw new AssertionError("Expected a valid preset (" + what + "), got " + result);
    }

    /** {@code reason} null: any failure. */
    private static void expectFailure(String text, String reason) {
        var result = PresetTransfer.parse(text);
        if (!(result instanceof PresetTransfer.Failure failure) || (reason != null && !failure.reason().equals(reason)))
            throw new AssertionError("Expected failure " + reason + " for "
                + (text == null ? "null" : text.substring(0, Math.min(80, text.length()))) + ", got " + result);
        if (net.minecraft.locale.Language.getInstance().getOrDefault(failure.key(), null) == null)
            throw new AssertionError("Missing translation " + failure.key());
    }

    private static Button button(Minecraft client, String label) {
        List<Button> buttons = new ArrayList<>();
        collect(Mc.screen(client).children(), buttons);
        return buttons.stream().filter(b -> b.getMessage().getString().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("Button '" + label + "' missing; have "
                + buttons.stream().map(b -> b.getMessage().getString()).toList()));
    }

    private static void collect(List<? extends GuiEventListener> children, List<Button> out) {
        for (var child : children) {
            if (child instanceof Button b) out.add(b);
            else if (child instanceof ContainerEventHandler container) collect(container.children(), out);
        }
    }
}
