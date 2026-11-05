package dev.betterxray.test;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.StructureScreen;
import dev.betterxray.debug.DebugHud;
import dev.betterxray.debug.DebugMode;
import dev.betterxray.render.StructureFinder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
//? if >=26.3 {
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
//?}
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.util.HashSet;

/**
 * Structure Finder: the picker groups every registered structure exactly once
 * (villages as one row, nether_complexes split into fortress and bastion). An igloo start is
 * planted in the integrated server the way world generation stores it (start in its chunk,
 * references in every chunk it overlaps); the finder must report it while igloos are selected
 * with a box around the placed blocks (the start itself keeps igloos at y 90), draw it, and drop
 * it once igloos are deselected or the finder is off.
 */
public final class StructureFinderTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null || System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            if (StructureFinder.available()) throw new AssertionError("Structure Finder available without a world");
            if (!StructureFinder.groups().isEmpty()) throw new AssertionError("Structure groups without a world");
        });
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            context.runOnClient(client -> checkGroups());

            int[] box = world.getServer().computeOnServer(server -> plantIgloo(server, new ChunkPos(3, 0), true));
            // Recorded but never placed, as after a reload: its pieces still sit at y 90.
            world.getServer().computeOnServer(server -> plantIgloo(server, new ChunkPos(3, 2), false));
            int cx = (box[0] + box[3]) / 2, cz = (box[2] + box[5]) / 2;
            // The start keeps the igloo at y 90; its blocks stand on the flat ground. Watch from the ground.
            world.getServer().runCommand("tp @a " + cx + " -60 " + (cz - 16) + " 0 0");
            TestCompat.packets(context, world);
            TestCompat.chunksRender(world);

            // Selected but finder off: nothing.
            context.runOnClient(client -> XRayConfigManager.get().structures.add("minecraft:igloo"));
            context.waitTicks(45);
            context.runOnClient(client -> {
                if (!StructureFinder.found().isEmpty()) throw new AssertionError("Structures found while the finder is off");
            });

            context.runOnClient(client -> {
                XRayConfigManager.get().structureFinderEnabled = true;
                XRayConfigManager.get().structureTracersEnabled = true;
            });
            context.waitTicks(45);
            context.runOnClient(client -> {
                var found = StructureFinder.found();
                if (found.size() != 2) throw new AssertionError("Expected two igloos, found " + found.size());
                var igloo = found.stream().filter(f -> f.bounds().minZ < 32).findFirst().orElseThrow();
                var unplaced = found.stream().filter(f -> f.bounds().minZ >= 32).findFirst().orElseThrow();
                if (unplaced.bounds().minY != igloo.bounds().minY)
                    throw new AssertionError("Unplaced igloo at " + unplaced.bounds() + ", placed one at " + igloo.bounds());
                if (!igloo.group().key().equals("minecraft:igloos")) throw new AssertionError("Igloo in group " + igloo.group().key());
                var b = igloo.bounds();
                // The box must hold the placed igloo (snow dome on the ground), not the start's y 90 box.
                int snow = 0;
                for (BlockPos pos : BlockPos.betweenClosed((int)b.minX, (int)b.minY, (int)b.minZ, (int)b.maxX - 1, (int)b.maxY - 1, (int)b.maxZ - 1))
                    if (client.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) snow++;
                if (snow < 20 || b.minY > -61 || b.maxY < -56)
                    throw new AssertionError("Igloo box " + b + " misses the igloo (" + snow + " snow blocks inside)");
            });
            TestCompat.lookAt(context, new BlockPos(cx, -58, cz));
            context.waitTicks(3);
            TestCompat.screenshot(context, "structure-01-igloo");
            context.runOnClient(client -> {
                if (StructureFinder.lastDrawnBoxes() < 1) throw new AssertionError("Igloo box not drawn: " + StructureFinder.lastDrawnBoxes());
                var stats = StructureFinder.stats();
                if (stats.found() != 2 || stats.chunks() < 1 || stats.radius() < 1)
                    throw new AssertionError("Structure Finder debug stats: " + stats);
                DebugMode.assertClean("structure finder");
            });

            // Regression: always-on-top lines took the terrain fog, so boxes far into the fog turned sky
            // blue. At render distance 2 the second igloo (behind the first, about 45 blocks away) is
            // deep in the fog; its box, in the middle of the screen, must keep the structure color.
            int renderDistance = context.computeOnClient(client -> client.options.renderDistance().get());
            int color = context.computeOnClient(client -> StructureFinder.found().get(0).group().color());
            context.runOnClient(client -> client.options.renderDistance().set(2));
            context.waitTicks(10);
            var fogged = TestCompat.screenshot(context, "structure-03-igloo-in-fog");
            context.runOnClient(client -> client.options.renderDistance().set(renderDistance));
            int far = pixelsNear(fogged, color, 24);
            System.out.println("[BetterXray Structures] far box pixels in structure color at render distance 2: " + far);
            if (far < 300) throw new AssertionError("Far igloo box lost its color in the fog: " + far + " pixels near "
                + Integer.toHexString(color));

            // Debug mode: the scan area of the last scan is drawn (after the fog check, whose timing
            // depends on the 20-tick scan cycle).
            context.runOnClient(client -> {
                DebugMode.setBounds(true);
                DebugMode.setHud(true);
            });
            context.waitTicks(11);
            context.runOnClient(client -> {
                var lines = DebugHud.lines();
                if (lines.stream().noneMatch(line -> line.startsWith("Structures: ") && line.contains(" found, ") && line.contains(" | epoch ")))
                    throw new AssertionError("Debug HUD shows no Structure Finder scan: " + lines);
            });
            TestCompat.screenshot(context, "structure-04-debug-bounds");
            context.runOnClient(client -> {
                DebugMode.setBounds(false);
                DebugMode.setHud(false);
            });

            // The picker lists the igloo row as selected; toggling it deselects every igloo variant.
            context.setScreen(() -> new StructureScreen(null));
            context.waitTicks(2);
            context.runOnClient(client -> {
                var screen = Mc.screen(client);
                var search = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("igloo");
                var list = screen.children().stream().filter(net.minecraft.client.gui.components.AbstractSelectionList.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractSelectionList.class::cast).findFirst().orElseThrow();
                if (list.children().size() != 1) throw new AssertionError("Search 'igloo' shows " + list.children().size() + " rows");
                var row = (net.minecraft.client.gui.components.ObjectSelectionList.Entry<?>)list.children().get(0);
                if (!row.getNarration().getString().contains(", selected")) throw new AssertionError("Igloo row not selected: " + row.getNarration().getString());
                screen.setFocused(list);
                list.setFocused(row);
            });
            context.takeScreenshot("structure-02-picker");
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
            context.waitTick();
            context.runOnClient(client -> {
                if (XRayConfigManager.get().structures.contains("minecraft:igloo")) throw new AssertionError("Enter did not deselect igloos");
                Mc.screen(client).onClose();
            });
            context.waitTicks(45);
            context.runOnClient(client -> {
                if (!StructureFinder.found().isEmpty()) throw new AssertionError("Deselected igloo still found");
                DebugMode.assertClean("end of the structure finder test");
                XRayConfigManager.reset();
            });
        }
    }

    /** Pixels in the screen middle (where the far igloo's box is) within {@code tolerance} of the color per channel. */
    private static int pixelsNear(java.nio.file.Path screenshot, int color, int tolerance) {
        try {
            var image = javax.imageio.ImageIO.read(screenshot.toFile());
            int count = 0;
            int w = image.getWidth(), h = image.getHeight();
            for (int y = h * 49 / 100; y < h * 58 / 100; y++) for (int x = w * 47 / 100; x < w * 56 / 100; x++) {
                int p = image.getRGB(x, y);
                if (Math.abs((p >> 16 & 255) - (color >> 16 & 255)) <= tolerance && Math.abs((p >> 8 & 255) - (color >> 8 & 255)) <= tolerance
                    && Math.abs((p & 255) - (color & 255)) <= tolerance) count++;
            }
            return count;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Stores an igloo start like world generation: start in its chunk, references in every chunk it overlaps. */
    private static int[] plantIgloo(net.minecraft.server.MinecraftServer server, ChunkPos origin, boolean place) {
        var level = server.overworld();
        var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Identifier.parse("minecraft:igloo")).orElseThrow();
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        //? if >=26.3 {
        StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator,
            generator.getBiomeSource(), random.createClimateSampler(SamplerContext.EMPTY_UNCACHED), random,
            level.getStructureTemplateManager(), level.getSeed(), origin, 0, level, biome -> true);
        //?} else {
        /*StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator,
            generator.getBiomeSource(), random, level.getStructureManager(), level.getSeed(), origin, 0, level,
            biome -> true);
        *///?}
        if (!start.isValid()) throw new AssertionError("Igloo did not generate");
        level.getChunk(x(origin), z(origin)).setStartForStructure(holder.value(), start);
        BoundingBox b = start.getBoundingBox();
        ChunkPos.rangeClosed(new ChunkPos(b.minX() >> 4, b.minZ() >> 4), new ChunkPos(b.maxX() >> 4, b.maxZ() >> 4)).forEach(c -> {
            level.getChunk(x(c), z(c)).addReferenceForStructure(holder.value(), pack(origin));
            if (place) start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY(), c.getMaxBlockZ()), c);
        });
        return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    // ChunkPos became a record with x()/z()/pack() in 26.1.
    private static int x(ChunkPos pos) {
        //? if >=26.1 {
        return pos.x();
        //?} else {
        /*return pos.x;
        *///?}
    }

    private static int z(ChunkPos pos) {
        //? if >=26.1 {
        return pos.z();
        //?} else {
        /*return pos.z;
        *///?}
    }

    private static long pack(ChunkPos pos) {
        //? if >=26.1 {
        return pos.pack();
        //?} else {
        /*return pos.toLong();
        *///?}
    }

    private static void checkGroups() {
        var groups = StructureFinder.groups();
        var seen = new HashSet<String>();
        for (var group : groups) for (String id : group.structures())
            if (!seen.add(id)) throw new AssertionError(id + " is in more than one group");
        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        for (var key : server.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet())
            if (!seen.contains(key.toString())) throw new AssertionError(key + " is in no group");
        var villages = groups.stream().filter(g -> g.key().equals("minecraft:villages")).findFirst()
            .orElseThrow(() -> new AssertionError("No villages group"));
        if (villages.structures().size() < 5 || !villages.name().getString().equals("Villages"))
            throw new AssertionError("Villages group: " + villages);
        for (String split : new String[] {"minecraft:fortress", "minecraft:bastion_remnant"})
            if (groups.stream().noneMatch(g -> g.key().equals(split) && g.structures().equals(java.util.List.of(split))))
                throw new AssertionError("nether_complexes not split: no group " + split);
    }
}
