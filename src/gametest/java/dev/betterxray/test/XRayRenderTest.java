package dev.betterxray.test;

import dev.betterxray.compat.Mc;
import com.mojang.blaze3d.platform.InputConstants;
import dev.betterxray.config.*;
import dev.betterxray.gui.XRayConfigScreen;
import dev.betterxray.gui.HighlightScreen;
import dev.betterxray.gui.BlockListWidget;
import dev.betterxray.render.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.level.block.Blocks;
import dev.betterxray.debug.DebugHud;
import dev.betterxray.debug.DebugMode;

/** Real rendering integration fixture; this test mod is excluded from the release jar. */
public final class XRayRenderTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null) return;
        String persistence = System.getProperty("betterxray.test.persistence");
        if (persistence != null) {
            context.runOnClient(client -> {
                var config = XRayConfigManager.get();
                if (persistence.equals("seed")) {
                    config.enabled = true;
                    config.fullbright = true;
                    config.terrainOpacity = .27f;
                    config.showWater = false;
                    config.showLava = true;
                    config.getActivePreset().name = "Ores";
                    config.getActivePreset().visibleBlocks = new java.util.LinkedHashSet<>(java.util.Set.of("minecraft:diamond_ore"));
                    var nether = config.espGroup(dev.betterxray.config.MobEspGroup.NETHER_ENEMIES);
                    nether.enabled = true;
                    nether.style = dev.betterxray.config.XRayConfig.EspStyle.BOX;
                    nether.color = 0xff123456;
                    var piglin = new dev.betterxray.config.XRayConfig.MobEspSettings();
                    piglin.enabled = false;
                    config.mobEsp.put("minecraft:piglin", piglin);
                    var zombie = new dev.betterxray.config.XRayConfig.MobEspSettings();
                    zombie.enabled = true;
                    zombie.color = 0xffabcdef;
                    config.mobEsp.put("minecraft:zombie", zombie);
                    XRayRenderController.apply();
                    KeyMapping quickAdd = KeyMapping.get("key.betterxray.quick_add_block");
                    quickAdd.setKey(InputConstants.UNKNOWN);
                    config.mobEspEnabled = false;
                    KeyMapping.get("key.betterxray.esp").setKey(InputConstants.getKey("key.keyboard.k"));
                    XRayConfigManager.save();
                    KeyMapping.resetMapping();
                    client.options.save();
                } else if (persistence.equals("verify")) {
                    // Do not call load here: these values must come from client startup
                    // in a new JVM after the seed process exited.
                    if (!config.enabled || !config.fullbright || Math.abs(config.terrainOpacity - .27f) > .001f || config.showWater || !config.showLava
                        || !config.getActivePreset().name.equals("Ores")
                        || !config.getActiveVisibleBlocks().equals(java.util.Set.of("minecraft:diamond_ore")) || !XRayState.enabled())
                        throw new AssertionError("Configuration did not survive a game restart");
                    var nether = config.espGroup(dev.betterxray.config.MobEspGroup.NETHER_ENEMIES);
                    var overworld = dev.betterxray.config.MobEspGroup.OVERWORLD_ENEMIES;
                    var blaze = config.resolveEsp("minecraft:blaze", dev.betterxray.config.MobEspGroup.NETHER_ENEMIES);
                    var zombie = config.resolveEsp("minecraft:zombie", overworld);
                    if (!nether.enabled || blaze == null || blaze.style() != dev.betterxray.config.XRayConfig.EspStyle.BOX || blaze.color() != 0xff123456
                        || config.resolveEsp("minecraft:piglin", dev.betterxray.config.MobEspGroup.NETHER_ENEMIES) != null
                        || zombie == null || zombie.color() != 0xffabcdef || config.resolveEsp("minecraft:husk", overworld) != null)
                        throw new AssertionError("ESP groups/overrides did not survive a game restart");
                    if (config.mobEspEnabled) throw new AssertionError("ESP master switch did not survive a game restart");
                    if (!KeyMapping.get("key.betterxray.esp").saveString().equals("key.keyboard.k"))
                        throw new AssertionError("Rebound ESP hotkey did not survive a game restart: " + KeyMapping.get("key.betterxray.esp").saveString());
                    if (!KeyMapping.get("key.betterxray.quick_add_block").isUnbound())
                        throw new AssertionError("Unbound key mapping did not survive a game restart");
                    KeyMapping.setAll();
                    XRayConfigManager.reset(); XRayRenderController.apply();
                } else if (persistence.equals("keymigration")) {
                    KeyMapping.setAll();
                    KeyMapping cycle = KeyMapping.get("key.betterxray.cycle_preset");
                    KeyMapping quickAdd = KeyMapping.get("key.betterxray.quick_add_block");
                    if (!cycle.isUnbound() || !quickAdd.isUnbound()
                        || !cycle.saveString().equals(InputConstants.UNKNOWN.getName())
                        || !quickAdd.saveString().equals(InputConstants.UNKNOWN.getName()))
                        throw new AssertionError("Saved raw -1 key mappings were not migrated");
                } else throw new AssertionError("Unknown persistence test phase: " + persistence);
            });
            return;
        }
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            assertKeyDefaults();
            var initial = XRayConfigManager.get();
            if (initial.presets.size() != XRayConfig.PRESET_COUNT || initial.activePreset != 0)
                throw new AssertionError("Fresh config does not contain exactly three presets");
            if (!initial.presets.get(1).visibleBlocks.isEmpty() || !initial.presets.get(2).visibleBlocks.isEmpty())
                throw new AssertionError("Preset 2 and Preset 3 must start empty");
            for (String id : XRayConfig.defaultPreset1Blocks()) {
                if (!BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id))) throw new AssertionError("Invalid default: " + id);
            }
            initial.presets.get(1).visibleBlocks.add("minecraft:stone");
            initial.setActivePreset(1);
            if (!initial.getActiveVisibleBlocks().contains("minecraft:stone") || initial.presets.get(0).visibleBlocks.contains("minecraft:stone"))
                throw new AssertionError("Preset block selections are not independent");
            initial.resetPreset(1);
            if (!initial.presets.get(1).visibleBlocks.isEmpty() || !initial.presets.get(1).name.equals("Preset 2"))
                throw new AssertionError("Individual preset reset failed");
            var migrated = new XRayConfig();
            migrated.enabled = true;
            migrated.terrainOpacity = .31f;
            migrated.migrateLegacyVisibleBlocks(java.util.Set.of("minecraft:emerald_ore"));
            migrated.sanitize();
            if (!migrated.enabled || Math.abs(migrated.terrainOpacity - .31f) > .001f
                || !migrated.presets.get(0).visibleBlocks.equals(java.util.Set.of("minecraft:emerald_ore"))
                || !migrated.presets.get(1).visibleBlocks.isEmpty() || !migrated.presets.get(2).visibleBlocks.isEmpty())
                throw new AssertionError("Legacy visibleBlocks migration failed");
            XRayConfigManager.reset();
            XRayRenderController.apply();
        });
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -12 60 -4 12 72 14 minecraft:air");
            // Twenty separated ores across neighboring chunks reproduce missing ESP boxes.
            for (int x : new int[]{-8, -4, 0, 4, 8}) {
                for (int y : new int[]{62, 64, 66, 68}) {
                    world.getServer().runCommand("setblock " + x + " " + y + " 11 minecraft:emerald_ore");
                }
            }
            world.getServer().runCommand("fill -12 60 -16 12 60 14 minecraft:stone");
            world.getServer().runCommand("fill -8 61 3 -4 65 7 minecraft:stone");
            world.getServer().runCommand("setblock -6 63 5 minecraft:diamond_ore");
            world.getServer().runCommand("fill -2 61 3 2 65 7 minecraft:deepslate");
            world.getServer().runCommand("setblock 0 63 5 minecraft:deepslate_diamond_ore");
            world.getServer().runCommand("fill 4 61 3 8 65 7 minecraft:dirt");
            world.getServer().runCommand("setblock 6 63 5 minecraft:ancient_debris");
            world.getServer().runCommand("setblock -3 61 1 minecraft:water");
            world.getServer().runCommand("setblock 3 61 1 minecraft:lava");
            world.getServer().runCommand("setblock 0 61 1 minecraft:chest");
            world.getServer().runCommand("tp @a 0 62 -10 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(20);
            TestCompat.lookAt(context, 0, 0);
            TestCompat.chunksRender(world);
            context.takeScreenshot("01-off");
            context.runOnClient(client -> {
                var config = XRayConfigManager.get();
                config.highlightBlocks.add("minecraft:emerald_ore");
                config.highlightEnabled = true;
                config.tracersEnabled = false;
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                for (int x : new int[]{-8, -4, 0, 4, 8}) for (int y : new int[]{62, 64, 66, 68})
                    if (!highlightCached(new net.minecraft.core.BlockPos(x, y, 11)))
                        throw new AssertionError("Highlight cache missed emerald ore at " + x + "," + y + ",11");
            });
            context.takeScreenshot("01d-highlight-emerald-grid");
            context.runOnClient(client -> {
                XRayConfigManager.get().tracersEnabled = true;
                client.player.getAbilities().flying = true;
                client.player.onUpdateAbilities();
            });
            context.waitTicks(2);
            context.takeScreenshot("01d-tracers-ground");
            context.getInput().holdKey(InputConstants.KEY_W);
            context.waitTicks(6);
            context.takeScreenshot("01d-tracers-moving");
            context.getInput().releaseKey(InputConstants.KEY_W);
            world.getServer().runCommand("tp @a 0 90 -10 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(4);
            TestCompat.lookAt(context, new net.minecraft.core.BlockPos(0, 64, 11));
            context.takeScreenshot("01d-tracers-flight");
            world.getServer().runCommand("tp @a 0 59 -10 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(2);
            context.takeScreenshot("01d-tracers-inside-ground");
            world.getServer().runCommand("tp @a 0 62 -10 0 0");
            TestCompat.packets(context, world);
            context.runOnClient(client -> XRayConfigManager.get().tracersEnabled = false);
            world.getServer().runCommand("setblock -8 62 11 minecraft:air");
            world.getServer().runCommand("setblock -1 62 11 minecraft:emerald_ore");
            world.getServer().runCommand("setblock 0 62 10 minecraft:emerald_ore");
            TestCompat.packets(context, world);
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (highlightCached(new net.minecraft.core.BlockPos(-8, 62, 11))
                    || !highlightCached(new net.minecraft.core.BlockPos(-1, 62, 11))
                    || !highlightCached(new net.minecraft.core.BlockPos(0, 62, 10)))
                    throw new AssertionError("Highlight did not track adjacent block updates across chunk boundaries");
                client.player.getAbilities().flying = true;
                client.player.onUpdateAbilities();
            });
            world.getServer().runCommand("forceload add 157 -12 163 27");
            world.getServer().runCommand("tp @a 160 62 -10 0 0");
            TestCompat.packets(context, world);
            TestCompat.chunksRender(world);
            world.getServer().runCommand("fill 157 60 -12 163 60 -8 minecraft:stone");
            world.getServer().runCommand("tp @a 160 62 -10 0 0");
            world.getServer().runCommand("setblock 159 63 11 minecraft:emerald_ore");
            world.getServer().runCommand("setblock 160 63 11 minecraft:emerald_ore");
            world.getServer().runCommand("setblock 161 66 27 minecraft:emerald_ore");
            // Range scan fixture: ~74 blocks ahead and ~76 blocks above the player.
            var farOre = new net.minecraft.core.BlockPos(160, 63, 74);
            var highOre = new net.minecraft.core.BlockPos(160, 140, -10);
            world.getServer().runCommand("setblock 160 63 74 minecraft:emerald_ore");
            world.getServer().runCommand("setblock 160 140 -10 minecraft:emerald_ore");
            TestCompat.packets(context, world);
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (!highlightCached(new net.minecraft.core.BlockPos(159, 63, 11))
                    || !highlightCached(new net.minecraft.core.BlockPos(160, 63, 11)))
                    throw new AssertionError("Highlight missed newly loaded adjacent chunks after travel");
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            TestCompat.lookAt(context, new net.minecraft.core.BlockPos(159, 63, 11));
            context.runOnClient(client -> {
                if (client.player.getBlockY() < 60) throw new AssertionError("Travel fixture player fell below ore grid");
                if (!highlightCached(new net.minecraft.core.BlockPos(161, 66, 27)))
                    throw new AssertionError("Highlight missed distant ore used for range check");
                if (!client.level.hasChunk(10, 4)) throw new AssertionError("Range fixture chunk 10,4 not loaded on the client");
            });
            // While the range follows the render distance, every section of the loaded chunks is indexed.
            context.waitFor(client -> highlightCached(farOre) && highlightCached(highOre), 200);
            context.runOnClient(client -> XRayConfigManager.get().highlightRangeBlocks = 16);
            context.waitTicks(2);
            context.takeScreenshot("01e-highlight-range-1");
            // Range 16 scans sections up to 32 blocks from the camera and keeps them up to 48 blocks.
            context.runOnClient(client -> {
                if (HighlightCache.isScanned(farOre) || HighlightCache.isScanned(highOre))
                    throw new AssertionError("Highlight range 16 kept sections far outside the range");
                if (!highlightCached(new net.minecraft.core.BlockPos(161, 66, 27)))
                    throw new AssertionError("Highlight range 16 dropped an ore within the scan margin");
                XRayConfigManager.get().highlightEnabled = false;
            });
            context.waitTicks(1);
            context.runOnClient(client -> XRayConfigManager.get().highlightEnabled = true);
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (!highlightCached(new net.minecraft.core.BlockPos(159, 63, 11))
                    || !highlightCached(new net.minecraft.core.BlockPos(161, 66, 27)))
                    throw new AssertionError("Highlight range 16 missed ores near the camera after a rescan");
                if (HighlightCache.isScanned(farOre) || HighlightCache.isScanned(highOre))
                    throw new AssertionError("Highlight range 16 scanned sections far outside the range");
            });
            // Debug mode (on in test runs): the scan area matches the range, and its drawing works.
            context.waitTicks(10);
            context.runOnClient(client -> {
                var stats = HighlightCache.stats(client);
                if (!stats.limited() || stats.outsideSections() != 0 || stats.farthestScanned() > 16 + 32 + 28)
                    throw new AssertionError("Highlight range 16 debug stats: " + stats);
                DebugMode.assertClean("highlight range 16");
                DebugMode.setBounds(true);
                DebugMode.setHud(true);
            });
            context.waitTicks(11);
            context.runOnClient(client -> {
                var lines = DebugHud.lines();
                for (String start : new String[] {"Better X-Ray debug | checks: ok", "X-Ray: ", "Highlight: range 16 blocks", "  farthest scan ", "ESP: ", "Structures: "})
                    if (lines.stream().noneMatch(line -> line.startsWith(start)))
                        throw new AssertionError("Debug HUD has no line starting with '" + start + "': " + lines);
            });
            context.takeScreenshot("01g-highlight-debug-bounds");
            // Every scanned section on its own instead of one box per chunk column.
            context.runOnClient(client -> DebugMode.setSections(true));
            context.waitTicks(11);
            context.takeScreenshot("01h-highlight-debug-sections");
            context.runOnClient(client -> {
                DebugMode.setHud(false);
                DebugMode.setBounds(false);
                if (DebugMode.sections()) throw new AssertionError("Section boxes stay on without bounds");
                DebugMode.assertClean("highlight debug bounds");
            });
            context.runOnClient(client -> {
                Mc.setScreen(client, new HighlightScreen(null));
                String expected = net.minecraft.network.chat.Component.translatable("option.betterxray.range",
                    net.minecraft.network.chat.Component.translatable("option.betterxray.range_blocks", 16)).getString();
                boolean found = Mc.screen(client).children().stream()
                    .anyMatch(w -> w instanceof AbstractSliderButton slider && slider.getMessage().getString().equals(expected));
                if (!found) throw new AssertionError("Highlight range slider missing or showing wrong value");
            });
            context.takeScreenshot("01f-highlight-range-menu");
            context.runOnClient(client -> {
                Mc.screen(client).onClose();
                XRayConfigManager.get().highlightRangeBlocks = 0;
            });
            // Raising the range scans the sections that are now inside it.
            context.waitFor(client -> highlightCached(farOre) && highlightCached(highOre), 200);
            context.runOnClient(client -> DebugMode.assertClean("highlight range back to the render distance"));
            world.getServer().runCommand("setblock 160 63 74 minecraft:air");
            world.getServer().runCommand("setblock 160 140 -10 minecraft:air");
            TestCompat.packets(context, world);
            context.waitTicks(2);
            context.takeScreenshot("01e-highlight-after-travel");
            world.getServer().runCommand("tp @a 0 62 -10 0 0");
            TestCompat.packets(context, world);
            TestCompat.chunksRender(world);
            world.getServer().runCommand("tp @a 160 62 -10 0 0");
            TestCompat.packets(context, world);
            TestCompat.chunksRender(world);
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (!highlightCached(new net.minecraft.core.BlockPos(159, 63, 11))
                    || !highlightCached(new net.minecraft.core.BlockPos(160, 63, 11)))
                    throw new AssertionError("Highlight missed existing ores when chunks reloaded");
            });
            world.getServer().runCommand("tp @a 0 62 -10 0 0");
            TestCompat.packets(context, world);
            veins(context, world);
            context.runOnClient(client -> {
                XRayConfigManager.get().highlightEnabled = false;
                XRayConfigManager.get().highlightBlocks.clear();
            });
            context.getInput().pressKey(InputConstants.KEY_L);
            context.runOnClient(client -> {
                if (!XRayConfigManager.get().fullbright || XRayState.enabled())
                    throw new AssertionError("Fullbright did not toggle independently of X-Ray");
            });
            context.waitTicks(2);
            context.takeScreenshot("01b-fullbright");
            context.getInput().pressKey(InputConstants.KEY_L);
            context.runOnClient(client -> {
                if (XRayConfigManager.get().fullbright) throw new AssertionError("Fullbright did not toggle off");
            });
            context.waitTicks(2);
            context.takeScreenshot("01c-fullbright-off");
            context.getInput().pressKey(InputConstants.KEY_G);
            context.runOnClient(client -> {
                if (!(Mc.screen(client) instanceof XRayConfigScreen)) throw new AssertionError("G configuration hotkey failed");
                Mc.screen(client).onClose();
                KeyMapping cycle = KeyMapping.get("key.betterxray.cycle_preset");
                var rebound = InputConstants.UNKNOWN.getType().getOrCreate(InputConstants.KEY_F2);
                cycle.setKey(rebound);
                KeyMapping.resetMapping();
                KeyMapping.click(rebound);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (XRayConfigManager.get().activePreset != 1) throw new AssertionError("Rebound preset-cycle key failed");
                XRayConfigManager.get().setActivePreset(0);
                KeyMapping.get("key.betterxray.cycle_preset").setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            });
            world.getServer().runCommand("setblock 0 63 -6 minecraft:emerald_ore");
            TestCompat.lookAt(context, new net.minecraft.core.BlockPos(0, 63, -6));
            context.waitTicks(2);
            context.runOnClient(client -> {
                KeyMapping quickAdd = KeyMapping.get("key.betterxray.quick_add_block");
                var rebound = InputConstants.UNKNOWN.getType().getOrCreate(InputConstants.KEY_F3);
                quickAdd.setKey(rebound);
                KeyMapping.resetMapping();
                KeyMapping.click(rebound);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (!XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:emerald_ore"))
                    throw new AssertionError("Rebound Quick Add key failed");
                XRayConfigManager.get().getActiveVisibleBlocks().remove("minecraft:emerald_ore");
                KeyMapping.get("key.betterxray.quick_add_block").setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            });
            TestCompat.lookAt(context, 0, 0);
            context.getInput().pressKey(InputConstants.KEY_X);
            context.runOnClient(client -> { if (!XRayState.enabled()) throw new AssertionError("X key failed"); });
            context.waitTicks(40);
            TestCompat.chunksRender(world);
            context.takeScreenshot("02-on");
            context.runOnClient(client -> {
                XRayConfigManager.get().showWater = false;
                XRayRenderController.apply();
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("03-no-water");
            context.runOnClient(client -> {
                XRayConfigManager.get().showWater = true;
                XRayConfigManager.get().showLava = false;
                XRayRenderController.apply();
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("03b-no-lava-water-visible");
            context.runOnClient(client -> {
                XRayConfigManager.get().showWater = false;
                XRayConfigManager.get().showLava = false;
                XRayConfigManager.get().terrainOpacity = .35f;
                XRayRenderController.apply();
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("04-no-fluids-opacity35");
            context.runOnClient(client -> {
                if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                    try {
                        Class<?> modMenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
                        Screen screen = (Screen)modMenu.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, "betterxray", null);
                        if (!(screen instanceof XRayConfigScreen)) throw new AssertionError("Mod Menu integration missing");
                        Mc.setScreen(client, screen);
                    } catch (ReflectiveOperationException e) { throw new AssertionError("Mod Menu integration failed", e); }
                } else Mc.setScreen(client, new XRayConfigScreen(null));
            });
            context.takeScreenshot("05-config");
            context.runOnClient(client -> {
                var button = Mc.screen(client).children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals("X-Ray Blocks")).findFirst().orElseThrow();
                ((Button)button).onPress(null);
                var search = Mc.screen(client).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("diamond");
            });
            context.takeScreenshot("06-search");
            context.runOnClient(client -> {
                var list = Mc.screen(client).children().stream().filter(BlockListWidget.class::isInstance).map(BlockListWidget.class::cast).findFirst().orElseThrow();
                var row = list.children().stream().filter(r -> r.getNarration().getString().contains(", minecraft:diamond_ore,")).findFirst().orElseThrow();
                //? if >=26.3 {
                var click = new MouseButtonEvent(row.getX() + 5, row.getY() + 5, new MouseButtonInfo(1, 0));
                Mc.screen(client).mouseClicked(click, false);
                //?} else {
                /*row.mouseClicked(new MouseButtonEvent(row.getX() + 5, row.getY() + 5, new MouseButtonInfo(0, 0)), false);
                *///?}
                if (XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:diamond_ore")) throw new AssertionError("GUI selection click failed");
                //? if >=26.3 {
                Mc.screen(client).mouseClicked(click, false);
                if (!XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:diamond_ore")) throw new AssertionError("GUI selection add failed");
                Mc.screen(client).mouseClicked(click, false);
                if (XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:diamond_ore")) throw new AssertionError("GUI selection remove failed");
                var preset2 = Mc.screen(client).children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().contains("Preset 2"))
                    .map(Button.class::cast).findFirst().orElseThrow();
                Mc.screen(client).mouseClicked(new MouseButtonEvent(preset2.getX() + 5, preset2.getY() + 5, new MouseButtonInfo(1, 0)), false);
                if (XRayConfigManager.get().activePreset != 1) throw new AssertionError("GUI preset switch failed");
                var secondList = Mc.screen(client).children().stream().filter(BlockListWidget.class::isInstance).map(BlockListWidget.class::cast).findFirst().orElseThrow();
                var secondRow = secondList.children().stream().filter(r -> r.getNarration().getString().contains(", minecraft:diamond_ore,"))
                    .findFirst().orElseThrow();
                var secondClick = new MouseButtonEvent(secondRow.getX() + 5, secondRow.getY() + 5, new MouseButtonInfo(1, 0));
                Mc.screen(client).mouseClicked(secondClick, false);
                if (!XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:diamond_ore")) throw new AssertionError("GUI preset 2 add failed");
                Mc.screen(client).mouseClicked(secondClick, false);
                if (XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:diamond_ore")) throw new AssertionError("GUI preset 2 remove failed");
                var preset1 = Mc.screen(client).children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().contains("Preset 1"))
                    .map(Button.class::cast).findFirst().orElseThrow();
                Mc.screen(client).mouseClicked(new MouseButtonEvent(preset1.getX() + 5, preset1.getY() + 5, new MouseButtonInfo(1, 0)), false);
                if (XRayConfigManager.get().activePreset != 0) throw new AssertionError("GUI return to preset 1 failed");
                //?}
                Mc.screen(client).onClose();
                Mc.screen(client).onClose();
                XRayConfigManager.load();
                if (XRayConfigManager.get().showWater || XRayConfigManager.get().showLava || Math.abs(XRayConfigManager.get().terrainOpacity - .35f) > .001)
                    throw new AssertionError("Config persistence failed");
                XRayConfigManager.get().getActiveVisibleBlocks().remove("minecraft:chest");
                XRayConfigManager.get().getActiveVisibleBlocks().add("missing:unknown_block");
                XRayRenderController.apply();
                if (XRayState.selected(Blocks.DIAMOND_ORE.defaultBlockState())) throw new AssertionError("Selection change failed");
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("07-diamond-removed");
            context.runOnClient(client -> {
                XRayConfigManager.get().terrainOpacity = .03f;
                XRayRenderController.apply();
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("07b-opacity3");
            context.runOnClient(client -> {
                XRayConfigManager.get().terrainOpacity = 0f;
                XRayConfigManager.get().sanitize();
                if (XRayConfigManager.get().terrainOpacity != 0f) throw new AssertionError("Opacity 0 was clamped");
                XRayRenderController.apply();
                if (!XRayState.hideTerrain()) throw new AssertionError("Opacity 0 does not hide terrain");
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("07c-opacity0");
            context.getInput().pressKey(InputConstants.KEY_X);
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            context.takeScreenshot("08-restored");
            // Two entirely solid adjacent sections: tests section-occlusion data, not
            // just selected face emission in a partially empty section.
            world.getServer().runCommand("fill 32 64 16 47 79 47 minecraft:stone");
            world.getServer().runCommand("fill 39 69 39 41 71 41 minecraft:gold_ore");
            world.getServer().runCommand("fill 32 63 0 47 63 15 minecraft:stone");
            world.getServer().runCommand("tp @a 40 69 5 0 0");
            TestCompat.packets(context, world);
            context.runOnClient(client -> {
                if (!XRayState.get().visible().contains(Blocks.GOLD_ORE)) throw new AssertionError("Gold is not selected");
                client.player.getAbilities().flying = true;
                client.player.onUpdateAbilities();
                XRayConfigManager.get().terrainOpacity = .15f;
                XRayConfigManager.get().enabled = true;
                XRayRenderController.apply();
            });
            context.waitTicks(40);
            TestCompat.lookAt(context, new net.minecraft.core.BlockPos(40, 70, 40));
            TestCompat.chunksRender(world);
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.takeScreenshot("09-solid-sections");
            // Underwater: X-Ray must remove the blue screen overlay and the water fog.
            world.getServer().runCommand("fill -12 61 -16 12 70 -4 minecraft:water");
            world.getServer().runCommand("tp @a 0 64 -10 0 0");
            TestCompat.packets(context, world);
            context.runOnClient(client -> {
                var config = XRayConfigManager.get();
                config.enabled = false;
                config.showWater = true;
                XRayRenderController.apply();
            });
            context.waitTicks(40);
            TestCompat.lookAt(context, 0, 0);
            TestCompat.chunksRender(world);
            context.runOnClient(client -> {
                if (!client.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER))
                    throw new AssertionError("Underwater fixture did not submerge the camera");
            });
            context.takeScreenshot("10-underwater-off");
            var existing = new net.minecraft.client.particle.Particle[1];
            context.runOnClient(client -> {
                var p = client.player.position();
                existing[0] = client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.UNDERWATER, p.x, p.y + 1, p.z, 0, 0, 0);
                if (existing[0] == null) throw new AssertionError("Underwater particle missing with X-Ray off");
                XRayConfigManager.get().enabled = true;
                XRayRenderController.apply();
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (existing[0].isAlive()) throw new AssertionError("Existing underwater particle survived X-Ray activation");
            });
            TestCompat.chunksRender(world);
            context.runOnClient(client -> {
                var p = client.player.position();
                if (client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.UNDERWATER, p.x, p.y + 1, p.z, 0, 0, 0) != null)
                    throw new AssertionError("Underwater particle spawned while X-Ray is active");
            });
            context.takeScreenshot("10b-underwater-xray");
            context.runOnClient(client -> {
                XRayConfigManager.get().enabled = false;
                XRayRenderController.apply();
                var p = client.player.position();
                if (client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.UNDERWATER, p.x, p.y + 1, p.z, 0, 0, 0) == null)
                    throw new AssertionError("Underwater particle missing with X-Ray off");
            });
            context.runOnClient(client -> {
                XRayConfigManager.reset(); XRayRenderController.apply();
                if (XRayState.enabled()) throw new AssertionError("Reset failed");
                DebugMode.assertClean("end of the render test");
            });
        }
    }

    private static void assertKeyDefaults() {
        // Exercises the exact focus-regain path that crashed 26.3. The mixin must
        // canonicalize any saved 26.2 raw -1 value before Minecraft polls it.
        KeyMapping.setAll();
        assertKeyDefault("key.betterxray.toggle", InputConstants.KEY_X, false);
        assertKeyDefault("key.betterxray.fullbright", InputConstants.KEY_L, false);
        assertKeyDefault("key.betterxray.config", InputConstants.KEY_G, false);
        assertKeyDefault("key.betterxray.cycle_preset", InputConstants.UNKNOWN.getValue(), true);
        assertKeyDefault("key.betterxray.quick_add_block", InputConstants.UNKNOWN.getValue(), true);
        for (String name : new String[] {
            "key.betterxray.toggle", "key.betterxray.fullbright", "key.betterxray.config",
            "key.betterxray.cycle_preset", "key.betterxray.quick_add_block"
        }) {
            KeyMapping mapping = KeyMapping.get(name);
            mapping.setKey(InputConstants.UNKNOWN.getType().getOrCreate(InputConstants.KEY_F1));
            KeyMapping.resetMapping();
            KeyMapping.setAll();
            mapping.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            KeyMapping.setAll();
            mapping.setKey(mapping.getDefaultKey());
        }
        KeyMapping.resetMapping();

        KeyMapping cycle = KeyMapping.get("key.betterxray.cycle_preset");
        KeyMapping quickAdd = KeyMapping.get("key.betterxray.quick_add_block");
        cycle.setKey(InputConstants.UNKNOWN.getType().getOrCreate(-1));
        quickAdd.setKey(InputConstants.UNKNOWN.getType().getOrCreate(-1));
        KeyMapping.resetMapping();
        KeyMapping.setAll();
        if (!cycle.isUnbound() || !quickAdd.isUnbound())
            throw new AssertionError("Legacy raw -1 bindings were not migrated");
    }

    /**
     * Vein grouping: a five-block diamond vein (across the chunk border at x=0, with a deepslate
     * ore and a diagonal-only neighbor) and a single ore next to it. Highlight is on with emerald ore.
     */
    private static void veins(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        String[] ores = {
            "-2 64 13 minecraft:diamond_ore", "-1 64 13 minecraft:diamond_ore", "0 64 13 minecraft:diamond_ore",
            "0 65 13 minecraft:deepslate_diamond_ore", "1 66 14 minecraft:diamond_ore", "4 64 13 minecraft:diamond_ore"};
        // Called right after teleporting back; setblock needs the chunks loaded again.
        TestCompat.chunksRender(world);
        for (String ore : ores) world.getServer().runCommand("setblock " + ore);
        context.runOnClient(client -> {
            var config = XRayConfigManager.get();
            config.groupVeins = true;
            config.tracersEnabled = false;
            config.highlightBlocks.add("minecraft:diamond_ore");
            config.highlightBlocks.add("minecraft:deepslate_diamond_ore");
        });
        TestCompat.packets(context, world);
        // A new selection rescans every chunk; veins follow at most every 10 ticks until that is done.
        context.waitTicks(20);
        TestCompat.lookAt(context, new net.minecraft.core.BlockPos(1, 64, 13));
        context.takeScreenshot("01g-veins");
        context.runOnClient(client -> {
            var counts = fixtureVeinCounts();
            if (!counts.equals(java.util.List.of(1, 5)))
                throw new AssertionError("Expected fixture veins of 1 and 5 diamond ores, got " + counts
                    + " (indexed " + highlightCached(new net.minecraft.core.BlockPos(-2, 64, 13)) + "/"
                    + highlightCached(new net.minecraft.core.BlockPos(4, 64, 13)) + ", block "
                    + client.level.getBlockState(new net.minecraft.core.BlockPos(-2, 64, 13)) + ", "
                    + HighlightCache.indexedCount() + " positions, " + HighlightCache.veins().size() + " veins, config "
                    + XRayConfigManager.get().highlightBlocks + ")");
            for (var vein : HighlightCache.veins())
                if (vein.count() == 5 && inFixture(vein) && vein.block() != Blocks.DIAMOND_ORE)
                    throw new AssertionError("Deepslate ore did not join the stone variant's vein: " + vein.block());
            if (HighlightCache.lastDrawnBoxes() < 2 || HighlightCache.lastDrawnBoxes() > HighlightCache.veins().size())
                throw new AssertionError("Vein boxes not drawn per vein: " + HighlightCache.lastDrawnBoxes()
                    + " boxes, " + HighlightCache.veins().size() + " veins");
            XRayConfigManager.get().tracersEnabled = true;
        });
        context.waitTicks(2);
        context.takeScreenshot("01g-veins-tracers");
        context.runOnClient(client -> {
            int tracers = HighlightCache.lastDrawnTracers();
            if (tracers < 2 || tracers > HighlightCache.veins().size())
                throw new AssertionError("Expected one tracer per vein, got " + tracers + " for " + HighlightCache.veins().size() + " veins");
            // Own color for the deepslate ore: it leaves the stone vein, which splits at it.
            XRayConfigManager.get().highlightBlockColors.put("minecraft:deepslate_diamond_ore", 0xff00ff00);
        });
        context.waitTicks(3);
        context.takeScreenshot("01g-veins-colors");
        context.runOnClient(client -> {
            var counts = fixtureVeinCounts();
            if (!counts.equals(java.util.List.of(1, 1, 1, 3)))
                throw new AssertionError("Deepslate ore with its own color should form its own vein, got " + counts);
            int global = XRayConfigManager.get().highlightColor | 0xff000000;
            if (HighlightCache.color(Blocks.DEEPSLATE_DIAMOND_ORE) != 0xff00ff00 || HighlightCache.color(Blocks.DIAMOND_ORE) != global)
                throw new AssertionError("Per-block highlight colors not applied: deepslate "
                    + Integer.toHexString(HighlightCache.color(Blocks.DEEPSLATE_DIAMOND_ORE)) + ", stone "
                    + Integer.toHexString(HighlightCache.color(Blocks.DIAMOND_ORE)));
            XRayConfigManager.get().highlightBlockColors.remove("minecraft:deepslate_diamond_ore");
        });
        context.waitTicks(3);
        context.runOnClient(client -> {
            var counts = fixtureVeinCounts();
            if (!counts.equals(java.util.List.of(1, 5)))
                throw new AssertionError("Vein did not rejoin after removing the block color, got " + counts);
            if (HighlightCache.color(Blocks.DEEPSLATE_DIAMOND_ORE) != (XRayConfigManager.get().highlightColor | 0xff000000))
                throw new AssertionError("Block color still applied after removing it");
        });
        world.getServer().runCommand("setblock 1 66 14 minecraft:air");
        TestCompat.packets(context, world);
        context.waitTicks(2);
        context.runOnClient(client -> {
            var counts = fixtureVeinCounts();
            if (!counts.equals(java.util.List.of(1, 4)))
                throw new AssertionError("Vein count did not follow a mined block, got " + counts);
            XRayConfigManager.get().groupVeins = false;
        });
        context.waitTicks(2);
        context.takeScreenshot("01g-veins-off");
        context.runOnClient(client -> {
            if (!HighlightCache.veins().isEmpty() || HighlightCache.lastDrawnBoxes() <= 2)
                throw new AssertionError("Vein grouping still active after turning it off");
            var config = XRayConfigManager.get();
            config.groupVeins = true;
            config.tracersEnabled = false;
            config.highlightBlocks.remove("minecraft:diamond_ore");
            config.highlightBlocks.remove("minecraft:deepslate_diamond_ore");
        });
        for (String ore : ores) world.getServer().runCommand("setblock " + ore.substring(0, ore.lastIndexOf(' ')) + " minecraft:air");
        TestCompat.packets(context, world);
    }

    private static boolean inFixture(HighlightCache.Vein vein) {
        var target = vein.tracerTarget();
        return target.x > -3 && target.x < 5 && target.y > 63 && target.y < 67 && target.z > 12 && target.z < 15;
    }

    /** Sorted block counts of the veins in the vein fixture area. */
    private static java.util.List<Integer> fixtureVeinCounts() {
        return HighlightCache.veins().stream().filter(XRayRenderTest::inFixture)
            .map(HighlightCache.Vein::count).sorted().toList();
    }

    private static boolean highlightCached(net.minecraft.core.BlockPos pos) {
        return HighlightCache.isIndexed(pos);
    }

    private static void assertKeyDefault(String name, int value, boolean unbound) {
        KeyMapping mapping = KeyMapping.get(name);
        if (mapping == null || mapping.getDefaultKey().getType() != InputConstants.UNKNOWN.getType()
            || mapping.getDefaultKey().getValue() != value || mapping.isUnbound() != unbound)
            throw new AssertionError("Invalid key mapping default: " + name);
    }
}
