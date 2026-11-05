package dev.betterxray.validation;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.betterxray.XRayClient;
import dev.betterxray.compat.Mc;
import dev.betterxray.compat.Platform;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.MobEspScreen;
import dev.betterxray.gui.XRayConfigScreen;
import dev.betterxray.render.HighlightCache;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
//? if >=1.21.2 {
import net.minecraft.world.entity.EntitySpawnReason;
//?}
//? if >=26.1 {
import static net.minecraft.world.entity.EntityTypes.ZOMBIE;
//?} else {
/*import static net.minecraft.world.entity.EntityType.ZOMBIE;
*///?}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
*///?}

/**
 * Development-only client run (never in the release jar): creates a disposable world, builds an
 * enclosed-ore scene, drives X-Ray, highlight, ESP, Fullbright and the underwater tweaks, and checks
 * each state on screenshot pixels. Result: run-validation/validation-result.txt and screenshots in
 * run-validation/screenshots/validation.
 */
//? if neoforge {
@Mod(value = "betterxray_validation", dist = Dist.CLIENT)
//?} else
/*@Mod("betterxray_validation")*/
public final class NeoForgeValidation {
    private static final int CYAN = 0, MAGENTA = 1, GREEN = 2, BRIGHTNESS = 3, YELLOW = 4,
        CENTER_SOLID = 5, RING_SOLID = 6;
    private static final int ESP_COLOR = 0xff00ff00;
    private static final Path SHOTS = Path.of("screenshots", "validation");

    private final List<Runnable> steps = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();
    private final Map<String, int[]> metrics = new ConcurrentHashMap<>();
    private final AtomicInteger pendingShots = new AtomicInteger();
    private boolean worldRequested, finished;
    private int step, wait, titleTicks;

    public NeoForgeValidation() {
        //? if neoforge {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick(Minecraft.getInstance()));
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) tick(Minecraft.getInstance());
        });
        *///?}
    }

    private void tick(Minecraft mc) {
        if (finished || loading(mc)) return;
        try {
            if (!worldRequested) {
                var screen = Mc.screen(mc);
                if (screen != null && screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) Mc.setScreen(mc, new TitleScreen());
                // Loading errors, and any warning about Better X-Ray itself (e.g. a deprecated mods.toml
                // key), stop the run; warnings about other mods (Sodium) are only logged.
                if (screen != null && screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
                    //? if neoforge {
                    for (var issue : net.neoforged.fml.ModLoader.getLoadingIssues()) {
                        String mod = issue.affectedMod() == null ? "?" : issue.affectedMod().getModId();
                        XRayClient.LOGGER.warn("[XRayValidation] loading {} for {}: {}", issue.severity(), mod, issue.translationKey());
                        if (issue.severity() == net.neoforged.fml.ModLoadingIssue.Severity.ERROR || mod.startsWith("betterxray"))
                            throw new AssertionError("mod loading " + issue.severity() + " for " + mod + ": " + issue.translationKey());
                    }
                    Mc.setScreen(mc, new TitleScreen());
                    return;
                    //?} else {
                    /*// Forge also shows this screen for warnings; errors would have stopped mod loading.
                    for (var warning : net.minecraftforge.fml.ModLoader.get().getWarnings()) {
                        String text = warning.formatToString();
                        XRayClient.LOGGER.warn("[XRayValidation] loading warning: {}", text);
                        if (text.contains("betterxray") && !text.contains("betterxray_validation"))
                            throw new AssertionError("mod loading warning for Better X-Ray: " + text);
                    }
                    Mc.setScreen(mc, new TitleScreen());
                    return;
                    *///?}
                }
                if (!(screen instanceof TitleScreen)) {
                    if (++titleTicks % 200 == 0) XRayClient.LOGGER.info("[XRayValidation] waiting for title screen, have {}", screen);
                    return;
                }
                worldRequested = true;
                defineSteps(mc);
                titleChecks();
                createWorld(mc, "betterxray-validation-" + System.currentTimeMillis());
                wait = 200;
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (pendingShots.get() > 0 || --wait > 0) return;
            if (step == steps.size()) {
                finish(mc, "PASS [" + renderer() + "]: " + String.join("; ", passed));
                return;
            }
            wait = 60;
            steps.get(step++).run();
        } catch (Throwable error) {
            XRayClient.LOGGER.error("[XRayValidation] FAILED at step " + step, error);
            finish(mc, "FAIL at step " + step + " [" + renderer() + "]: " + error);
        }
    }

    private void defineSteps(Minecraft mc) {
        steps.add(() -> {
            mc.options.renderDistance().set(8);
            mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.options.pauseOnLostFocus = false;
            server(mc, server -> {
                var level = server.overworld();
                // Floor, three 5x5x5 cubes with an enclosed ore each, fluids, a chest.
                for (BlockPos pos : BlockPos.betweenClosed(-12, 200, -18, 12, 200, 16)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                for (BlockPos pos : BlockPos.betweenClosed(-12, 201, -18, 12, 215, 16)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                Block[] materials = {Blocks.STONE, Blocks.DEEPSLATE, Blocks.DIRT};
                Block[] ores = {Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS};
                for (int cube = 0; cube < 3; cube++) {
                    int center = -6 + cube * 6;
                    for (BlockPos pos : BlockPos.betweenClosed(center - 2, 201, 3, center + 2, 205, 7)) level.setBlock(pos, materials[cube].defaultBlockState(), 3);
                    level.setBlock(new BlockPos(center, 203, 5), ores[cube].defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(-3, 201, 1), Blocks.WATER.defaultBlockState(), 3);
                level.setBlock(new BlockPos(3, 201, 1), Blocks.LAVA.defaultBlockState(), 3);
                level.setBlock(new BlockPos(0, 201, 1), Blocks.CHEST.defaultBlockState(), 3);
                // A zombie sealed in stone behind the cubes for the ESP checks (easy difficulty keeps it).
                for (BlockPos pos : BlockPos.betweenClosed(-1, 201, 11, 1, 204, 13)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(0, 201, 12), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(0, 202, 12), Blocks.AIR.defaultBlockState(), 3);
                //? if >=1.21.2 {
                var zombie = ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                //?} else
                /*var zombie = ZOMBIE.create(level);*/
                zombie.setPos(0.5, 201, 12.5);
                zombie.setNoAi(true);
                zombie.setPersistenceRequired();
                level.addFreshEntity(zombie);
                for (String command : new String[] {"time set noon", "weather clear", "tp @a 0 201 -10 0 0"})
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            });
            wait = 200;
        });
        steps.add(() -> {
            check(mc.player.getY() > 199, "fixture teleport");
            check(renderer().equals(Platform.sodiumLoaded() ? "sodium" : "vanilla"), "expected terrain renderer " + renderer());
            shot(mc, "01-off");
        });
        steps.add(() -> {
            KeyMapping.click(InputConstants.getKey(key("key.betterxray.toggle").saveString()));
            wait = 100;
        });
        steps.add(() -> { check(XRayState.enabled(), "X key toggles X-Ray on"); shot(mc, "02-on"); });
        steps.add(() -> {
            int off = metric("01-off", CYAN), on = metric("02-on", CYAN);
            check(on > 60 && on > 3 * off + 30, "enclosed diamond ores visible with X-Ray (ore px " + off + " -> " + on + ")");
            config().terrainOpacity = 0f;
            XRayRenderController.apply();
            wait = 100;
        });
        steps.add(() -> { check(XRayState.hideTerrain(), "0 % opacity hides terrain"); shot(mc, "03-opacity0"); });
        steps.add(() -> {
            check(metric("03-opacity0", CYAN) > 60, "selected ores stay visible at 0 % opacity (" + metric("03-opacity0", CYAN) + " px)");
            var c = config();
            c.terrainOpacity = .15f;
            c.highlightBlocks.clear();
            // Through the registered client command, with a block tag that covers both diamond ores.
            mc.player.connection.sendCommand("betterxray highlight add #minecraft:diamond_ores");
            check(c.highlightBlocks.equals(java.util.Set.of("#minecraft:diamond_ores")), "/betterxray client command registered (" + c.highlightBlocks + ")");
            c.highlightEnabled = true;
            c.tracersEnabled = true;
            c.highlightRangeBlocks = 32;
            XRayConfigManager.save();
            XRayRenderController.apply();
            wait = 100;
        });
        steps.add(() -> shot(mc, "04-highlight-tracers"));
        steps.add(() -> {
            check(HighlightCache.lastDrawnBoxes() >= 2 && HighlightCache.lastDrawnTracers() >= 2,
                "highlight boxes/tracers drawn (" + HighlightCache.lastDrawnBoxes() + "/" + HighlightCache.lastDrawnTracers() + ")");
            check(metric("04-highlight-tracers", MAGENTA) > 150, "highlight color on screen (" + metric("04-highlight-tracers", MAGENTA) + " px)");
            var c = config();
            c.highlightEnabled = false;
            c.enabled = false;
            c.mobEspEnabled = true;
            var group = c.espGroup(MobEspGroup.OVERWORLD_ENEMIES);
            group.enabled = true;
            group.style = XRayConfig.EspStyle.BOX;
            group.color = ESP_COLOR;
            XRayConfigManager.save();
            XRayRenderController.apply();
            wait = 100;
        });
        steps.add(() -> shot(mc, "05-esp-box"));
        steps.add(() -> {
            //? if >=1.21.11 {
            check(!MobEspRenderer.lastRenderedTargets().isEmpty(), "ESP target rendered for the sealed zombie");
            //?} else
            /*check(MobEspRenderer.lastDrawnBoxes() > 0, "ESP box drawn for the sealed zombie");*/
            check(metric("05-esp-box", GREEN) > 100, "ESP box color through stone (" + metric("05-esp-box", GREEN) + " px)");
            config().espGroup(MobEspGroup.OVERWORLD_ENEMIES).style = XRayConfig.EspStyle.OUTLINE;
            XRayConfigManager.save();
        });
        steps.add(() -> shot(mc, "06-esp-outline"));
        steps.add(() -> {
            check(metric("06-esp-outline", GREEN) > 30, "ESP outline color through stone (" + metric("06-esp-outline", GREEN) + " px)");
            config().mobEspEnabled = false;
            XRayConfigManager.save();
            command(mc, "time set midnight");
            wait = 80;
        });
        steps.add(() -> shot(mc, "07-night"));
        steps.add(() -> { config().fullbright = true; XRayConfigManager.save(); wait = 40; });
        steps.add(() -> shot(mc, "08-fullbright"));
        steps.add(() -> {
            int night = metric("07-night", BRIGHTNESS), bright = metric("08-fullbright", BRIGHTNESS);
            check(bright > night + 15, "Fullbright brightens the night (" + night + " -> " + bright + ")");
            config().fullbright = false;
            XRayConfigManager.save();
            server(mc, server -> {
                var level = server.overworld();
                for (BlockPos pos : BlockPos.betweenClosed(-3, 201, -17, 3, 206, -11)) level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0 202 -14 0 0");
            });
            wait = 100;
        });
        steps.add(() -> { check(mc.player.isEyeInFluid(FluidTags.WATER), "camera under water"); shot(mc, "09-underwater-off"); });
        steps.add(() -> { config().enabled = true; XRayRenderController.apply(); wait = 100; });
        steps.add(() -> shot(mc, "10-underwater-xray"));
        steps.add(() -> {
            int off = metric("09-underwater-off", BRIGHTNESS), on = metric("10-underwater-xray", BRIGHTNESS);
            check(on > off + 10, "X-Ray removes water fog/overlay (" + off + " -> " + on + ")");
            config().enabled = false;
            XRayRenderController.apply();
            server(mc, server -> {
                var level = server.overworld();
                for (BlockPos pos : BlockPos.betweenClosed(-3, 201, -17, 3, 206, -11)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0 201 -10 0 0");
            });
            Mc.setScreen(mc, new XRayConfigScreen(null));
        });
        steps.add(() -> { check(Mc.screen(mc) instanceof XRayConfigScreen, "config screen opens"); shot(mc, "11-config"); });
        steps.add(() -> Mc.setScreen(mc, new MobEspScreen(null)));
        steps.add(() -> { check(Mc.screen(mc) instanceof MobEspScreen, "ESP screen opens"); shot(mc, "12-esp-screen"); });
        steps.add(() -> { Mc.setScreen(mc, null); XRayConfigManager.reset(); XRayConfigManager.save(); XRayRenderController.apply(); });
        featureSteps(mc);
    }

    /**
     * 1.3.0 features: Cave-Find, vein grouping, block and tag highlight colors, block tags, ESP
     * tracers, single-item ESP and the Structure Finder.
     */
    private void featureSteps(Minecraft mc) {
        steps.add(() -> {
            // A stone cube with an enclosed air pocket and a diamond ore in its roof (as CaveFindTest).
            for (String c : new String[] {"fill -4 216 20 4 224 28 minecraft:stone", "fill -2 218 22 2 222 26 minecraft:air",
                    "setblock 0 223 21 minecraft:diamond_ore", "fill -1 217 9 1 217 11 minecraft:stone", "tp @a 0 218 10 0 -3.6"})
                command(mc, c);
            caveView(true, false, 0f);
            wait = 100;
        });
        steps.add(() -> {
            check(mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(0, 220, 24)) == 0, "Cave pocket has no sky light");
            shot(mc, "13-cave-xray-0");
        });
        steps.add(() -> {
            check(metric("13-cave-xray-0", CENTER_SOLID) < 50 && metric("13-cave-xray-0", RING_SOLID) > 50, "X-Ray 0 %: cube gone, roof ore visible");
            caveView(false, true, 0f);
            wait = 100;
        });
        steps.add(() -> {
            check(XRayState.enabled() && XRayState.caveFind(), "Cave-Find activates the X-Ray view");
            shot(mc, "14-cave-find-only");
        });
        steps.add(() -> {
            check(metric("14-cave-find-only", CENTER_SOLID) > 500, "Cave-Find: pocket walls visible");
            check(metric("14-cave-find-only", RING_SOLID) < 20, "Cave-Find alone: outer faces and preset ore hidden");
            caveView(true, true, 0f);
            wait = 100;
        });
        steps.add(() -> shot(mc, "15-cave-find-and-xray"));
        steps.add(() -> {
            check(metric("15-cave-find-and-xray", CENTER_SOLID) > 500 && metric("15-cave-find-and-xray", RING_SOLID) > 50,
                "Cave-Find with X-Ray: walls and preset ore");
            XRayClient.toggleCaveFind(mc);
            check(!config().caveFind, "Cave-Find toggle switches it off");
            config().enabled = false;
            config().terrainOpacity = .15f;
            XRayRenderController.apply();
            for (String c : new String[] {"fill -4 216 20 4 224 28 minecraft:air", "setblock -6 203 4 minecraft:diamond_ore", "tp @a 0 201 -10 0 0"})
                command(mc, c);
            var c = config();
            c.highlightBlocks.clear();
            c.highlightBlocks.add("minecraft:diamond_ore");
            c.highlightBlocks.add("minecraft:ancient_debris");
            c.highlightEnabled = true;
            c.tracersEnabled = true;
            c.highlightRangeBlocks = 24;
            c.groupVeins = true;
            XRayConfigManager.save();
            wait = 100;
        });
        steps.add(() -> {
            check(HighlightCache.veins().stream().anyMatch(vein -> vein.count() == 2 && vein.block() == Blocks.DIAMOND_ORE),
                "Two adjacent diamond ores form one vein");
            check(HighlightCache.lastDrawnBoxes() == 2 && HighlightCache.lastDrawnTracers() == 2,
                "Vein and debris: two boxes and tracers (" + HighlightCache.lastDrawnBoxes() + "/" + HighlightCache.lastDrawnTracers() + ")");
            config().groupVeins = false;
            config().highlightBlockColors.put("minecraft:ancient_debris", 0xffffff00);
            XRayConfigManager.save();
            wait = 40;
        });
        steps.add(() -> {
            check(HighlightCache.lastDrawnBoxes() == 3, "Group Veins off: one box per block (" + HighlightCache.lastDrawnBoxes() + ")");
            check(HighlightCache.color(Blocks.ANCIENT_DEBRIS) == 0xffffff00, "Own block color");
            shot(mc, "16-block-color");
        });
        steps.add(() -> {
            check(metric("16-block-color", YELLOW) > 100 && metric("16-block-color", MAGENTA) > 100, "Debris box yellow, diamond boxes magenta");
            var c = config();
            c.highlightBlockColors.clear();
            c.highlightBlocks.clear();
            c.highlightBlocks.add("#minecraft:diamond_ores");
            XRayConfigManager.save();
            wait = 60;
        });
        steps.add(() -> {
            check(HighlightCache.isIndexed(new BlockPos(-6, 203, 5)) && HighlightCache.isIndexed(new BlockPos(0, 203, 5))
                && !HighlightCache.isIndexed(new BlockPos(6, 203, 5)), "Tag #minecraft:diamond_ores covers both diamond ores only");
            var c = config();
            c.highlightEnabled = false;
            for (String command : new String[] {"kill @e[type=!minecraft:player]",
                    "summon minecraft:cow 3.5 201 -2.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                    item(-2.5, "minecraft:diamond"), item(-4.5, "minecraft:emerald")})
                command(mc, command);
            c.mobEspEnabled = true;
            c.mobEspTracersEnabled = true;
            var animals = c.espGroup(MobEspGroup.ANIMALS);
            animals.enabled = true;
            animals.style = XRayConfig.EspStyle.BOX;
            animals.color = ESP_COLOR;
            animals.tracer = true;
            c.espGroup(MobEspGroup.OBJECTS).enabled = true;
            var itemRow = new XRayConfig.MobEspSettings();
            itemRow.enabled = false;
            c.mobEsp.put(XRayConfig.ITEM_ENTITY, itemRow);
            var diamond = new XRayConfig.MobEspSettings();
            diamond.enabled = true;
            c.itemEsp.put("minecraft:diamond", diamond);
            XRayConfigManager.save();
            wait = 60;
        });
        steps.add(() -> {
            check(MobEspRenderer.lastDrawnTracers() == 1, "One ESP tracer for the cow (" + MobEspRenderer.lastDrawnTracers() + ")");
            check(MobEspRenderer.settings(itemEntity(mc, "minecraft:diamond")) != null, "Diamond item has ESP");
            check(MobEspRenderer.settings(itemEntity(mc, "minecraft:emerald")) == null, "Emerald item has no ESP");
            shot(mc, "17-esp-tracer-items");
            config().mobEspTracersEnabled = false;
            XRayConfigManager.save();
        });
        steps.add(() -> {
            check(MobEspRenderer.lastDrawnTracers() == 0, "ESP tracer master switch off");
            var c = config();
            c.mobEspEnabled = false;
            command(mc, "kill @e[type=!minecraft:player]");
            server(mc, server -> plantIgloo(server, new net.minecraft.world.level.ChunkPos(-4, -4)));
            c.structureFinderEnabled = true;
            c.structureTracersEnabled = true;
            c.structures.add("minecraft:igloo");
            XRayConfigManager.save();
            wait = 60;
        });
        steps.add(() -> {
            var found = dev.betterxray.render.StructureFinder.found();
            check(found.size() == 1 && found.get(0).group().key().equals("minecraft:igloos"), "Structure Finder finds the igloo (" + found.size() + ")");
            Mc.setScreen(mc, new dev.betterxray.gui.StructureScreen(null));
        });
        steps.add(() -> { check(Mc.screen(mc) instanceof dev.betterxray.gui.StructureScreen, "Structure Finder screen opens"); shot(mc, "18-structures"); });
        steps.add(() -> { Mc.setScreen(mc, null); XRayConfigManager.reset(); XRayConfigManager.save(); XRayRenderController.apply(); });
    }

    private static void caveView(boolean xray, boolean caveFind, float opacity) {
        var c = config();
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

    private static net.minecraft.world.entity.Entity itemEntity(Minecraft mc, String id) {
        for (var entity : mc.level.entitiesForRendering())
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && MobEspRenderer.itemId(item.getItem().getItem()).equals(id))
                return entity;
        throw new AssertionError("Fixture item not loaded: " + id);
    }

    /** Stores an igloo start like world generation: start in its chunk, references in every chunk it overlaps. */
    private static void plantIgloo(MinecraftServer server, net.minecraft.world.level.ChunkPos origin) {
        var level = server.overworld();
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        //? if >=1.21.2 {
        var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .get(Mc.id("minecraft", "igloo")).orElseThrow();
        //?} else {
        /*var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Mc.id("minecraft", "igloo")));
        *///?}
        //? if >=26.3 {
        var start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
            random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), random,
            level.getStructureTemplateManager(), level.getSeed(), origin, 0, level, biome -> true);
        //?} else if >=1.21.11 {
        /*var start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
            random, level.getStructureManager(), level.getSeed(), origin, 0, level, biome -> true);
        *///?} else {
        /*var start = holder.value().generate(level.registryAccess(), generator, generator.getBiomeSource(), random,
            level.getStructureManager(), level.getSeed(), origin, 0, level, biome -> true);
        *///?}
        if (!start.isValid()) throw new AssertionError("Igloo did not generate");
        //? if >=26.1 {
        int ox = origin.x(), oz = origin.z();
        long packed = origin.pack();
        //?} else {
        /*int ox = origin.x, oz = origin.z;
        long packed = origin.toLong();
        *///?}
        level.getChunk(ox, oz).setStartForStructure(holder.value(), start);
        var b = start.getBoundingBox();
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
            level.getChunk(x, z).addReferenceForStructure(holder.value(), packed);
    }

    private void titleChecks() {
        long keys = XRayClient.keyMappings().size();
        // Eight original mappings plus ESP tracers and Cave-Find.
        int expected = 10;
        check(keys == expected, expected + " key mappings registered (" + keys + ")");
        check(key("key.betterxray.toggle").saveString().equals("key.keyboard.x"), "X default");
        check(key("key.betterxray.config").saveString().equals("key.keyboard.g"), "G default");
        check(key("key.betterxray.esp").saveString().equals("key.keyboard.j"), "J default");
        check(key("key.betterxray.cycle_preset").isUnbound(), "cycle preset unbound");
        XRayConfigManager.reset();
        XRayConfigManager.save();
        XRayRenderController.apply();
        check(config().presets.size() == XRayConfig.PRESET_COUNT, "fresh config with three presets");
    }

    private static void createWorld(Minecraft mc, String name) {
        //? if >=26.1 {
        var settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true, WorldDataConfiguration.DEFAULT);
        //?} else {
        /*//? if >=1.21.11 {
        var settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true,
            new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS),
            WorldDataConfiguration.DEFAULT);
        //?} else {
        /^var settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true,
            new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
        ^///?}
        *///?}
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
            WorldPresets::createNormalWorldDimensions
            //? if >=1.20.2
            , new TitleScreen()
            );
    }

    private void shot(Minecraft mc, String name) {
        pendingShots.incrementAndGet();
        //? if >=1.21.11 {
        Screenshot.takeScreenshot(mainTarget(mc), image -> {
        //?} else {
        /*java.util.function.Consumer<NativeImage> handler = image -> {
        *///?}
            try (image) {
                metrics.put(name, measure(image));
                Files.createDirectories(SHOTS);
                image.writeToFile(SHOTS.resolve(name + ".png"));
            } catch (Exception e) {
                XRayClient.LOGGER.error("[XRayValidation] screenshot " + name, e);
            } finally {
                pendingShots.decrementAndGet();
            }
        //? if >=1.21.11 {
        });
        //?} else {
        /*};
        handler.accept(Screenshot.takeScreenshot(mainTarget(mc)));
        *///?}
        passed.add("shot " + name);
    }

    /** Pixel counts of the check colors and the mean luminance (0-255) of a frame. */
    private static int[] measure(NativeImage image) {
        int[] result = new int[7];
        long luminance = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = pixel(image, x, y);
            int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
            // Teal diamond-ore specks; water (blue-heavy) and sky (gray-blue) do not match.
            if (g - r > 50 && b - r > 50 && Math.abs(g - b) < 35) result[CYAN]++;
            if (r > 220 && b > 220 && g < 60) result[MAGENTA]++;
            if (g > 220 && r < 60 && b < 60) result[GREEN]++;
            if (r > 220 && g > 220 && b < 60) result[YELLOW]++;
            luminance += (r * 299 + g * 587 + b * 114) / 1000;
        }
        result[BRIGHTNESS] = (int)(luminance / ((long)image.getWidth() * image.getHeight()));
        // Per mille of non-sky pixels (sky: blue clearly above red) in the Cave-Find regions:
        // the view center, and a band above it on the cube's front face (as CaveFindTest).
        int w = image.getWidth(), h = image.getHeight(), cx = w / 2, cy = h / 2, half = h / 2;
        result[CENTER_SOLID] = solid(image, cx - half / 8, cx + half / 8, cy - half / 8, cy + half / 8);
        result[RING_SOLID] = solid(image, cx - half / 6, cx + half / 6, cy - half * 50 / 100, cy - half * 40 / 100);
        return result;
    }

    private static int solid(NativeImage image, int x0, int x1, int y0, int y1) {
        int solid = 0, total = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int argb = pixel(image, x, y);
            if ((argb & 255) - (argb >> 16 & 255) < 40) solid++;
            total++;
        }
        return total == 0 ? 0 : solid * 1000 / total;
    }

    private static int pixel(NativeImage image, int x, int y) {
        //? if >=1.21.11 {
        return image.getPixel(x, y);
        //?} else {
        /*int abgr = image.getPixelRGBA(x, y);
        return (abgr & 0xff00ff00) | (abgr >> 16 & 255) | (abgr & 255) << 16;
        *///?}
    }

    private int metric(String shot, int index) {
        int[] values = metrics.get(shot);
        if (values == null) throw new AssertionError("screenshot " + shot + " missing");
        XRayClient.LOGGER.info("[XRayValidation] {} cyan={} magenta={} green={} brightness={} yellow={} center={} ring={}", shot,
            values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
        return values[index];
    }

    private void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        passed.add(message);
        XRayClient.LOGGER.info("[XRayValidation] ok: {}", message);
    }

    private void finish(Minecraft mc, String result) {
        finished = true;
        XRayClient.LOGGER.info("[XRayValidation] {}", result);
        try { Files.writeString(Path.of("validation-result.txt"), result + "\n"); } catch (Exception ignored) {}
        mc.stop();
    }

    private static XRayConfig config() { return XRayConfigManager.get(); }

    private static boolean loading(Minecraft mc) {
        //? if >=26.1 {
        return mc.gui.overlay() != null;
        //?} else {
        /*return mc.getOverlay() != null;
        *///?}
    }

    private static com.mojang.blaze3d.pipeline.RenderTarget mainTarget(Minecraft mc) {
        //? if >=26.1 {
        return mc.gameRenderer.mainRenderTarget();
        //?} else {
        /*return mc.getMainRenderTarget();
        *///?}
    }

    private static KeyMapping key(String name) {
        return XRayClient.keyMappings().stream().filter(mapping -> mapping.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void server(Minecraft mc, java.util.function.Consumer<MinecraftServer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.submit(() -> action.accept(server)).join();
    }

    private static void command(Minecraft mc, String command) {
        server(mc, server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    /** "sodium" when Sodium's world renderer drives the terrain, otherwise "vanilla". */
    private static String renderer() {
        try {
            //? if forge {
            /*Object sodium = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer").getMethod("instanceNullable").invoke(null);
            *///?} else
            Object sodium = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer").getMethod("instanceNullable").invoke(null);
            return sodium != null ? "sodium" : "vanilla";
        } catch (ReflectiveOperationException | LinkageError absent) {
            return "vanilla";
        }
    }
}
