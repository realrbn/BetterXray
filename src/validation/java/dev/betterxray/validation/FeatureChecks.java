package dev.betterxray.validation;

import dev.betterxray.XRayClient;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.StructureScreen;
import dev.betterxray.render.HighlightCache;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.StructureFinder;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import static dev.betterxray.validation.GuiChecks.check;

/**
 * Checks for the 1.3.0 features in the fixture world: Cave-Find, vein grouping, block and tag
 * highlight colors, block tags and the client command, ESP tracers, single-item ESP and the
 * Structure Finder. One step per call, with the fixture's tick and mesh wait in between.
 */
final class FeatureChecks {
    private FeatureChecks() {}

    private static int step;
    private static double ringBefore;

    /** Runs the next step; true once every step has run. */
    static boolean step(Minecraft mc) {
        var c = XRayConfigManager.get();
        switch (step++) {
            case 0 -> {
                // A stone cube with an enclosed air pocket and a diamond ore in its roof (as CaveFindTest).
                XRayConfigManager.reset();
                commands(mc, "fill -4 216 20 4 224 28 minecraft:stone", "fill -2 218 22 2 222 26 minecraft:air",
                    "setblock 0 223 21 minecraft:diamond_ore", "fill -1 217 9 1 217 11 minecraft:stone", "tp @a 0 218 10 0 -3.6");
                set(true, false, 0f);
            }
            case 1 -> {
                check(mc.level.getBrightness(LightLayer.SKY, new BlockPos(0, 220, 24)) == 0, "Cave pocket has no sky light");
                shot(mc, "30-cave-xray-0");
                check(nonSky(mc, true) < .05, "X-Ray 0 %: cube gone (" + nonSky(mc, true) + ")");
                check(nonSky(mc, false) > .05, "X-Ray 0 %: preset ore in the roof (" + nonSky(mc, false) + ")");
                set(false, true, 0f);
            }
            case 2 -> {
                check(XRayState.enabled() && XRayState.caveFind(), "Cave-Find activates the X-Ray view");
                check(!XRayState.selected(mc.level.getBlockState(new BlockPos(0, 223, 21))), "Preset ore not selected with Cave-Find alone");
                shot(mc, "31-cave-find-only");
                check(nonSky(mc, true) > .5, "Cave-Find: pocket walls visible (" + nonSky(mc, true) + ")");
                check(nonSky(mc, false) < .02, "Cave-Find alone: outer faces and preset ore hidden (" + nonSky(mc, false) + ")");
                set(true, true, 0f);
            }
            case 3 -> {
                shot(mc, "32-cave-find-and-xray");
                check(nonSky(mc, true) > .5 && nonSky(mc, false) > .05, "Cave-Find with X-Ray: walls and preset ore");
                set(false, true, .15f);
            }
            case 4 -> {
                shot(mc, "33-cave-find-15");
                check(nonSky(mc, true) > .5, "Cave-Find at 15 %: walls stay solid");
                XRayClient.toggleCaveFind(mc);
                check(!c.caveFind && !XRayState.enabled(), "Cave-Find toggle switches it off");
            }
            case 5 -> {
                ringBefore = nonSky(mc, false);
                check(ringBefore > .9, "Terrain restored after Cave-Find (" + ringBefore + ")");
                // Ore fixture: a second diamond ore next to the first forms a two-block vein.
                commands(mc, "fill -4 216 20 4 224 28 minecraft:air", "setblock -6 203 4 minecraft:diamond_ore", "tp @a 0 201 -10 0 0");
                c.highlightBlocks.clear();
                c.highlightBlocks.add("minecraft:diamond_ore");
                c.highlightBlocks.add("minecraft:ancient_debris");
                c.highlightEnabled = true;
                c.tracersEnabled = true;
                c.highlightRangeBlocks = 24;
                c.groupVeins = true;
                XRayConfigManager.save();
            }
            case 6 -> {
                check(HighlightCache.veins().stream().anyMatch(vein -> vein.count() == 2 && vein.block() == Blocks.DIAMOND_ORE),
                    "Two adjacent diamond ores form one vein");
                check(HighlightCache.lastDrawnBoxes() == 2 && HighlightCache.lastDrawnTracers() == 2,
                    "Vein and debris: two boxes, two tracers, got " + HighlightCache.lastDrawnBoxes() + "/" + HighlightCache.lastDrawnTracers());
                shot(mc, "34-veins");
                c.groupVeins = false;
                XRayConfigManager.save();
            }
            case 7 -> {
                check(HighlightCache.lastDrawnBoxes() == 3, "Group Veins off: one box per block, got " + HighlightCache.lastDrawnBoxes());
                c.highlightBlockColors.put("minecraft:ancient_debris", 0xffffff00);
                XRayConfigManager.save();
            }
            case 8 -> {
                check(HighlightCache.color(Blocks.ANCIENT_DEBRIS) == 0xffffff00 && HighlightCache.color(Blocks.DIAMOND_ORE) == (c.highlightColor | 0xff000000),
                    "Own block color and global color");
                int yellow = pixels(mc, 0xffff00), magenta = pixels(mc, 0xff00ff);
                shot(mc, "35-block-color");
                check(yellow > 100 && magenta > 100, "Debris box yellow, diamond boxes magenta (" + yellow + "/" + magenta + ")");
                c.highlightBlockColors.clear();
                c.highlightBlocks.clear();
                c.highlightBlocks.add("#minecraft:diamond_ores");
                XRayConfigManager.save();
            }
            case 9 -> {
                check(HighlightCache.isIndexed(new BlockPos(-6, 203, 5)) && HighlightCache.isIndexed(new BlockPos(0, 203, 5))
                    && !HighlightCache.isIndexed(new BlockPos(6, 203, 5)), "Tag #minecraft:diamond_ores covers both diamond ores only");
                mc.player.connection.sendCommand("betterxray highlight add minecraft:ancient_debris");
                check(c.highlightBlocks.contains("minecraft:ancient_debris"), "/betterxray client command (" + c.highlightBlocks + ")");
                c.highlightEnabled = false;
                // ESP: a cow with a tracer, a diamond with its own item ESP, an emerald without.
                commands(mc, "kill @e[type=!minecraft:player]",
                    "summon minecraft:cow 3.5 201 -2.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                    item(-2.5, "minecraft:diamond"), item(-4.5, "minecraft:emerald"));
                c.mobEspEnabled = true;
                c.mobEspTracersEnabled = true;
                var animals = c.espGroup(MobEspGroup.ANIMALS);
                animals.enabled = true;
                animals.style = XRayConfig.EspStyle.BOX;
                animals.color = 0xff55ff55;
                animals.tracer = true;
                c.espGroup(MobEspGroup.OBJECTS).enabled = true;
                var itemRow = new XRayConfig.MobEspSettings();
                itemRow.enabled = false;
                c.mobEsp.put(XRayConfig.ITEM_ENTITY, itemRow);
                var diamond = new XRayConfig.MobEspSettings();
                diamond.enabled = true;
                c.itemEsp.put("minecraft:diamond", diamond);
                XRayConfigManager.save();
            }
            case 10 -> {
                check(MobEspRenderer.lastDrawnTracers() == 1, "One ESP tracer (cow), got " + MobEspRenderer.lastDrawnTracers());
                check(MobEspRenderer.settings(item(mc, "minecraft:diamond")) != null, "Diamond item has ESP");
                check(MobEspRenderer.settings(item(mc, "minecraft:emerald")) == null, "Emerald item has no ESP");
                shot(mc, "36-esp-tracer-items");
                c.mobEspTracersEnabled = false;
                XRayConfigManager.save();
            }
            case 11 -> {
                check(MobEspRenderer.lastDrawnTracers() == 0, "ESP tracer master switch off");
                c.mobEspEnabled = false;
                commands(mc, "kill @e[type=!minecraft:player]");
                int[] box = plantIgloo(mc, new ChunkPos(-4, -4));
                XRayClient.LOGGER.info("[XRayValidation] planted igloo start {}..{}", box[0], box[3]);
                c.structureFinderEnabled = true;
                c.structureTracersEnabled = true;
                c.structures.add("minecraft:igloo");
                XRayConfigManager.save();
            }
            case 12 -> {
                var found = StructureFinder.found();
                check(found.size() == 1 && found.get(0).group().key().equals("minecraft:igloos"), "Structure Finder finds the igloo, got " + found.size());
                check(StructureFinder.groups().size() > 10, "Structure picker lists the structure sets");
                mc.setScreen(new StructureScreen(null));
            }
            case 13 -> {
                check(mc.screen instanceof StructureScreen, "Structure Finder screen opens");
                shot(mc, "37-structures");
                mc.setScreen(null);
                XRayConfigManager.reset();
                XRayConfigManager.save();
                XRayRenderController.apply();
                return true;
            }
            default -> { return true; }
        }
        return false;
    }

    private static void set(boolean xray, boolean caveFind, float opacity) {
        var c = XRayConfigManager.get();
        c.enabled = xray;
        c.caveFind = caveFind;
        c.terrainOpacity = opacity;
        XRayRenderController.apply();
    }

    private static String item(double x, String id) {
        //? if >=1.20.5 {
        return "summon minecraft:item " + x + " 201 -3.5 {Item:{id:\"" + id + "\",count:1},NoGravity:1b,Age:-32768,PickupDelay:32767}";
        //?} else
        /*return "summon minecraft:item " + x + " 201 -3.5 {Item:{id:\"" + id + "\",Count:1b},NoGravity:1b,Age:-32768,PickupDelay:32767}";*/
    }

    private static Entity item(Minecraft mc, String id) {
        for (Entity entity : mc.level.entitiesForRendering())
            if (entity instanceof ItemEntity item && MobEspRenderer.itemId(item.getItem().getItem()).equals(id)) return entity;
        throw new AssertionError("Fixture item not loaded: " + id);
    }

    private static void commands(Minecraft mc, String... commands) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.submit(() -> {
            var source = server.createCommandSourceStack().withPermission(4);
            for (String command : commands) server.getCommands().performPrefixedCommand(source, command);
        }).join();
    }

    /** Stores an igloo start like world generation: start in its chunk, references in every chunk it overlaps. */
    private static int[] plantIgloo(Minecraft mc, ChunkPos origin) {
        MinecraftServer server = mc.getSingleplayerServer();
        return server.submit(() -> {
            var level = server.overworld();
            var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.tryParse("minecraft:igloo")));
            var generator = level.getChunkSource().getGenerator();
            var start = holder.value().generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), origin, 0, level, biome -> true);
            check(start.isValid(), "Igloo generated");
            level.getChunk(origin.x, origin.z).setStartForStructure(holder.value(), start);
            BoundingBox b = start.getBoundingBox();
            ChunkPos.rangeClosed(new ChunkPos(b.minX() >> 4, b.minZ() >> 4), new ChunkPos(b.maxX() >> 4, b.maxZ() >> 4))
                .forEach(chunk -> level.getChunk(chunk.x, chunk.z).addReferenceForStructure(holder.value(), origin.toLong()));
            return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
        }).join();
    }

    private static void shot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {}); }

    /** Pixels of the last frame within a small tolerance of the given RGB color. */
    private static int pixels(Minecraft mc, int rgb) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int r = abgr & 255, g = abgr >> 8 & 255, b = abgr >> 16 & 255;
                if (Math.abs(r - (rgb >> 16 & 255)) < 40 && Math.abs(g - (rgb >> 8 & 255)) < 40 && Math.abs(b - (rgb & 255)) < 40) count++;
            }
            return count;
        }
    }

    /**
     * Fraction of non-sky pixels (sky: blue clearly above red). Center: the pocket's far wall around
     * the view direction; otherwise a band above it on the cube's front face, where the ore sits.
     */
    private static double nonSky(Minecraft mc, boolean center) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = image.getWidth(), h = image.getHeight(), cx = w / 2, cy = h / 2, half = h / 2;
            int x0, x1, y0, y1;
            if (center) { x0 = cx - half / 8; x1 = cx + half / 8; y0 = cy - half / 8; y1 = cy + half / 8; }
            else { x0 = cx - half / 6; x1 = cx + half / 6; y0 = cy - half * 50 / 100; y1 = cy - half * 40 / 100; }
            int solid = 0, total = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int abgr = image.getPixelRGBA(x, y);
                if ((abgr >> 16 & 255) - (abgr & 255) < 40) solid++;
                total++;
            }
            return total == 0 ? 0 : (double)solid / total;
        }
    }
}
