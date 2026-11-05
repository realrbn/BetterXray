package dev.betterxray.test;

import com.mojang.brigadier.CommandDispatcher;
import dev.betterxray.command.XRayCommands;
import dev.betterxray.compat.Mc;
import dev.betterxray.config.BlockSelection;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.BlockListWidget;
import dev.betterxray.gui.BlockSelectionScreen;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.Blocks;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Block tags in a selection and the {@code /betterxray} client commands. A preset that only holds
 * {@code #minecraft:diamond_ores} selects nothing on the title screen (tags unbound) and both
 * diamond ores once a world bound the tags. The picker lists tag rows and marks blocks covered by a
 * selected tag; the commands edit presets, highlight list, switches and ranges.
 */
public final class TagsCommandsTest implements FabricClientGameTest {
    private static final String DIAMONDS = "#minecraft:diamond_ores";

    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null || System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            XRayConfigManager.get().getActivePreset().visibleBlocks = new LinkedHashSet<>(Set.of(DIAMONDS));
            XRayRenderController.apply();
            if (!BlockSelection.normalize("#Ores").equals("#minecraft:ores") || !BlockSelection.normalize("diamond_ore").equals("minecraft:diamond_ore")
                || BlockSelection.normalize("#") != null || BlockSelection.normalize("bad id!") != null)
                throw new AssertionError("Entry normalization failed");
        });
        // Title screen: the tag is kept and the picker lists it although it has no blocks yet.
        context.setScreen(() -> new BlockSelectionScreen(null));
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (!rows(Mc.screen(client), "").stream().anyMatch(n -> n.contains(", " + DIAMONDS + ", ")))
                throw new AssertionError("Selected tag not listed on the title screen");
        });
        context.setScreen(() -> null);

        try (var world = TestCompat.worldBuilder(context).create()) {
            TestCompat.chunksRender(world);
            // The periodic tag check (one second) picks up the tags bound on world join.
            context.waitTicks(BlockSelection.TAG_CHECK_TICKS + 5);
            context.runOnClient(client -> {
                Set<?> visible = XRayState.get().visible();
                if (!visible.contains(Blocks.DIAMOND_ORE) || !visible.contains(Blocks.DEEPSLATE_DIAMOND_ORE) || visible.contains(Blocks.GOLD_ORE))
                    throw new AssertionError("Tag not resolved after joining the world: " + visible);
                var tags = BlockSelection.allTags();
                if (tags.get(DIAMONDS) == null || tags.get(DIAMONDS).size() != 2) throw new AssertionError("Tag index wrong: " + tags.get(DIAMONDS));
            });

            runPicker(context);
            runCommands(context);

            // Registered as a real client command: executed through the chat command path, never sent to the server.
            context.runOnClient(client -> client.player.connection.sendCommand("betterxray opacity 42"));
            context.waitTick();
            context.runOnClient(client -> {
                if (Math.abs(XRayConfigManager.get().terrainOpacity - .42f) > 1e-4) throw new AssertionError("Registered /betterxray did not run");
            });
            context.runOnClient(client -> client.player.connection.sendCommand("bxray opacity 15"));
            context.waitTick();
            context.runOnClient(client -> {
                if (Math.abs(XRayConfigManager.get().terrainOpacity - .15f) > 1e-4) throw new AssertionError("Alias /bxray did not run");
            });
        }
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            XRayRenderController.apply();
        });
    }

    /** Tag rows on top, "#" searches tags only, covered blocks say "via", Enter on a tag row toggles the tag. */
    private void runPicker(ClientGameTestContext context) {
        context.setScreen(() -> new BlockSelectionScreen(null));
        context.waitTicks(2);
        context.runOnClient(client -> {
            Screen screen = Mc.screen(client);
            var tagsOnly = rows(screen, "#diamond_ores");
            if (tagsOnly.isEmpty() || !tagsOnly.stream().allMatch(n -> n.contains(", #")))
                throw new AssertionError("'#' search must list tags only: " + tagsOnly);
            // Selected tags first, other matching tags after the blocks.
            var mixed = rows(screen, "diamond_ore");
            int tagRow = indexOf(mixed, ", " + DIAMONDS + ", "), blockRow = indexOf(mixed, ", minecraft:diamond_ore, ");
            if (tagRow < 0 || blockRow < 0 || tagRow > blockRow) throw new AssertionError("Selected tag not listed before the blocks: " + mixed);
            var gold = rows(screen, "gold_ore");
            int goldTag = indexOf(gold, ", #minecraft:gold_ores, "), goldBlock = indexOf(gold, ", minecraft:gold_ore, ");
            if (goldTag < 0 || goldBlock < 0 || goldTag < goldBlock) throw new AssertionError("Unselected tag not listed after the blocks: " + gold);
            if (mixed.stream().noneMatch(n -> n.contains(", minecraft:deepslate_diamond_ore, selected via " + DIAMONDS)))
                throw new AssertionError("Covered block not marked: " + mixed);
            rows(screen, "#diamond_ores");
            var list = list(screen);
            var row = list.children().stream().filter(r -> r.getNarration().getString().contains(", " + DIAMONDS + ", ")).findFirst().orElseThrow();
            screen.setFocused(list);
            list.setFocused(row);
        });
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        context.waitTick();
        context.runOnClient(client -> {
            if (XRayConfigManager.get().getActiveVisibleBlocks().contains(DIAMONDS)) throw new AssertionError("Enter did not deselect the tag row");
        });
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        context.waitTick();
        context.runOnClient(client -> {
            if (!XRayConfigManager.get().getActiveVisibleBlocks().contains(DIAMONDS)) throw new AssertionError("Enter did not select the tag row");
        });
        context.setScreen(() -> null);
    }

    private void runCommands(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var dispatcher = new CommandDispatcher<Object>();
            XRayCommands.register(dispatcher);
            var config = XRayConfigManager.get();

            run(dispatcher, "betterxray add #minecraft:gold_ores emerald_ore bogus:block");
            if (!config.getActiveVisibleBlocks().containsAll(Set.of("#minecraft:gold_ores", "minecraft:emerald_ore"))
                || config.getActiveVisibleBlocks().contains("bogus:block"))
                throw new AssertionError("add failed: " + config.getActiveVisibleBlocks());
            if (!XRayState.get().visible().contains(Blocks.DEEPSLATE_GOLD_ORE)) throw new AssertionError("Added tag not published");
            run(dispatcher, "betterxray remove #minecraft:gold_ores minecraft:emerald_ore");
            if (config.getActiveVisibleBlocks().contains("#minecraft:gold_ores") || XRayState.get().visible().contains(Blocks.GOLD_ORE))
                throw new AssertionError("remove failed");

            run(dispatcher, "betterxray on");
            if (!config.enabled || !XRayState.get().enabled()) throw new AssertionError("on failed");
            run(dispatcher, "betterxray toggle");
            if (config.enabled) throw new AssertionError("toggle failed");

            run(dispatcher, "betterxray preset 2");
            if (config.activePreset != 1) throw new AssertionError("preset switch failed");
            run(dispatcher, "betterxray preset rename Tag Test");
            if (!config.getActivePreset().name.equals("Tag Test")) throw new AssertionError("preset rename failed");
            run(dispatcher, "betterxray preset reset");
            if (!config.getActivePreset().name.equals("Preset 2")) throw new AssertionError("preset reset failed");
            run(dispatcher, "bxray preset 1");
            if (config.activePreset != 0 || !config.getActiveVisibleBlocks().contains(DIAMONDS)) throw new AssertionError("preset 1 failed");

            run(dispatcher, "betterxray opacity 30");
            if (Math.abs(config.terrainOpacity - .3f) > 1e-4) throw new AssertionError("opacity failed");
            run(dispatcher, "betterxray water off");
            run(dispatcher, "betterxray lava");
            if (config.showWater || config.showLava) throw new AssertionError("fluid switches failed");
            run(dispatcher, "betterxray fullbright on");
            if (!config.fullbright) throw new AssertionError("fullbright failed");

            run(dispatcher, "betterxray highlight add #minecraft:diamond_ores ancient_debris");
            if (!config.highlightBlocks.containsAll(Set.of(DIAMONDS, "minecraft:ancient_debris"))) throw new AssertionError("highlight add failed");
            run(dispatcher, "betterxray highlight on");
            run(dispatcher, "betterxray highlight tracers on");
            if (!config.highlightEnabled || !config.tracersEnabled) throw new AssertionError("highlight switches failed");
            run(dispatcher, "betterxray highlight range 100");
            if (config.highlightRangeBlocks != 104) throw new AssertionError("highlight range not rounded to a slider step: " + config.highlightRangeBlocks);
            run(dispatcher, "betterxray highlight range render");
            if (config.highlightRangeBlocks != 0) throw new AssertionError("highlight range render failed");
            run(dispatcher, "betterxray highlight clear");
            if (!config.highlightBlocks.isEmpty()) throw new AssertionError("highlight clear failed");

            run(dispatcher, "betterxray esp off");
            if (config.mobEspEnabled) throw new AssertionError("esp off failed");
            run(dispatcher, "betterxray esp range 64");
            if (config.mobEspRangeBlocks != 64) throw new AssertionError("esp range failed");
            run(dispatcher, "betterxray esp range unlimited");
            if (config.mobEspRangeBlocks != 0) throw new AssertionError("esp range unlimited failed");

            for (String info : new String[]{"betterxray", "betterxray help", "betterxray status", "betterxray list", "betterxray preset",
                "betterxray opacity", "betterxray tags", "betterxray tags diamond", "betterxray tag #minecraft:diamond_ores",
                "betterxray tag minecraft:nonexistent_tag", "betterxray highlight list", "betterxray esp range"})
                run(dispatcher, info);

            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("betterxray add stone #minecraft:diamond", new Object())).join();
            if (suggestions.getList().stream().noneMatch(s -> s.getText().equals(DIAMONDS))) throw new AssertionError("No tag suggestion: " + suggestions.getList());
            suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("betterxray add deepslate_diamond", new Object())).join();
            if (suggestions.getList().stream().noneMatch(s -> s.getText().equals("minecraft:deepslate_diamond_ore")))
                throw new AssertionError("No block suggestion: " + suggestions.getList());
            suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("betterxray remove ", new Object())).join();
            if (suggestions.getList().stream().noneMatch(s -> s.getText().equals(DIAMONDS))) throw new AssertionError("No remove suggestion");
        });
    }

    private static void run(CommandDispatcher<Object> dispatcher, String command) {
        try {
            dispatcher.execute(command, new Object());
        } catch (Exception e) {
            throw new AssertionError("/" + command + " failed", e);
        }
    }

    private static int indexOf(java.util.List<String> rows, String part) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).contains(part)) return i;
        return -1;
    }

    private static BlockListWidget list(Screen screen) {
        return screen.children().stream().filter(BlockListWidget.class::isInstance).map(BlockListWidget.class::cast).findFirst().orElseThrow();
    }

    /** Narrations of the rows listed for a search. */
    private static java.util.List<String> rows(Screen screen, String query) {
        screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(query);
        return list(screen).children().stream().map(row -> row.getNarration().getString()).toList();
    }
}
