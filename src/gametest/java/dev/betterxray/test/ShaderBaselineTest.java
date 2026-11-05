package dev.betterxray.test;

import dev.betterxray.XRayClient;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Shader baseline: records what the X-Ray features look like under one Iris
 * shader pack. Runs only with {@code -Piris -PshaderPack=<file>} (property
 * {@code betterxray.test.shaderPack}); the other gametests skip in that mode. No pixel assertions:
 * the screenshots {@code shader-<pack>-<nn>-<step>} are the result.
 *
 * <p>Scene: a stone cube floating in the sky with an enclosed air pocket (Cave-Find), ores in its
 * walls (default preset), a bell (deselected block entity), water in the pocket and a zombie behind
 * the cube (ESP through terrain).
 */
public final class ShaderBaselineTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String pack = TestCompat.shaderPack();
        if (pack == null) return;
        // -PopacityPatch: experimental shader opacity patch, on before the world (and the pack) loads.
        boolean patch = Boolean.getBoolean("betterxray.test.opacityPatch");
        String tag = "shader-" + pack.replaceAll("\\.zip$", "").replaceAll("[^A-Za-z0-9.]+", "_") + (patch ? "-patch-" : "-");
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            XRayConfigManager.get().shaderOpacityPatch = patch;
            XRayRenderController.apply();
            // Iris may already have compiled the pack; the switch only reaches the shaders through a reload.
            if (patch) dev.betterxray.compat.IrisCompat.reloadShaders();
        });
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("fill -6 112 0 6 124 12 minecraft:stone");
            world.getServer().runCommand("fill -2 116 4 2 120 8 minecraft:air");
            world.getServer().runCommand("setblock 0 116 6 minecraft:water");
            world.getServer().runCommand("setblock 0 122 2 minecraft:diamond_ore");
            world.getServer().runCommand("setblock -4 118 2 minecraft:emerald_ore");
            world.getServer().runCommand("setblock 4 118 2 minecraft:gold_ore");
            world.getServer().runCommand("setblock 0 114 2 minecraft:deepslate_diamond_ore");
            world.getServer().runCommand("setblock 3 122 1 minecraft:bell[attachment=floor]");
            world.getServer().runCommand("fill -1 117 -11 1 117 -9 minecraft:stone");
            world.getServer().runCommand("fill -2 111 13 2 111 17 minecraft:stone");
            world.getServer().runCommand("summon minecraft:zombie 0 112 15 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            world.getServer().runCommand("tp @a 0 118 -10 0 0");
            TestCompat.packets(context, world);
            // Iris compiles the pack when the world loads; give it time before the first capture.
            context.waitTicks(100);
            TestCompat.lookAt(context, new BlockPos(0, 118, 6));
            TestCompat.chunksRender(world);
            context.waitTicks(20);
            context.runOnClient(client -> XRayClient.LOGGER.info("[BetterXray shader baseline] pack={} irisPackInUse={} xraySupported={}",
                pack, irisPackInUse(), XRayState.supported()));

            Path s01 = capture(context, tag + "01-off");
            set(context, world, c -> { c.enabled = true; c.terrainOpacity = .15f; });
            context.runOnClient(client -> {
                XRayClient.LOGGER.info("[BetterXray shader baseline] X-Ray on: enabled={} supported={} gate={} faintUnsupported={} faintEncoded={}",
                    XRayState.enabled(), XRayState.supported(), dev.betterxray.compat.IrisCompat.shadersSupported(),
                    XRayState.faintUnsupported(), XRayState.faintEncoded());
                // Phase 1: under a pack the effect follows the Iris version gate; without a pack it is always on.
                boolean expected = pack.equals("none") || dev.betterxray.compat.IrisCompat.shadersSupported();
                if (XRayState.supported() != expected)
                    throw new AssertionError("X-Ray support " + XRayState.supported() + " under " + pack + ", expected " + expected);
            });
            Path s02 = capture(context, tag + "02-xray-15");
            set(context, world, c -> c.terrainOpacity = 0f);
            Path s03 = capture(context, tag + "03-xray-0");
            set(context, world, c -> c.terrainOpacity = .5f);
            Path s04 = capture(context, tag + "04-xray-50");
            boolean faintHidden = context.computeOnClient(client -> XRayState.faintUnsupported());
            boolean xraySupported = context.computeOnClient(client -> XRayState.supported());
            set(context, world, c -> { c.enabled = false; c.caveFind = true; c.terrainOpacity = 0f; });
            capture(context, tag + "05-cavefind-0");
            set(context, world, c -> { c.enabled = true; c.terrainOpacity = .15f; });
            capture(context, tag + "06-cavefind-xray-15");

            set(context, world, c -> {
                c.caveFind = false;
                c.highlightBlocks.add("minecraft:emerald_ore");
                c.highlightEnabled = true;
                c.tracersEnabled = true;
            });
            Path s07 = capture(context, tag + "07-highlight-tracers");
            // Tracer start while walking (view bobbing): it must stay on the crosshair.
            world.getServer().runCommand("fill -1 117 -40 1 117 -9 minecraft:stone");
            world.getServer().runCommand("tp @a 0 118 -38 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(10);
            TestCompat.lookAt(context, 0, 0);
            List<Path> walk = new ArrayList<>();
            context.getInput().holdKey(InputConstants.KEY_W);
            context.waitTicks(10);
            for (int i = 0; i < 6; i++) {
                context.waitTicks(2);
                walk.add(context.takeScreenshot(tag + "07w-walk-" + i));
            }
            context.getInput().releaseKey(InputConstants.KEY_W);
            world.getServer().runCommand("tp @a 0 118 -10 0 0");
            TestCompat.packets(context, world);
            TestCompat.lookAt(context, new BlockPos(0, 118, 6));
            context.waitTicks(10);
            set(context, world, c -> {
                c.highlightEnabled = false;
                c.tracersEnabled = false;
                c.enabled = false;
                c.mobEspEnabled = true;
                var group = c.espGroup(MobEspGroup.OVERWORLD_ENEMIES);
                group.enabled = true;
                group.style = XRayConfig.EspStyle.OUTLINE;
                group.color = 0xffff3030;
            });
            Path s08 = capture(context, tag + "08-esp-outline");
            set(context, world, c -> c.espGroup(MobEspGroup.OVERWORLD_ENEMIES).style = XRayConfig.EspStyle.BOX);
            Path s09 = capture(context, tag + "09-esp-box");
            set(context, world, c -> { c.mobEspEnabled = false; c.enabled = true; });

            world.getServer().runCommand("time set midnight");
            TestCompat.packets(context, world);
            context.waitTicks(10);
            capture(context, tag + "10-night-xray-15");
            set(context, world, c -> { c.enabled = false; c.fullbright = true; });
            capture(context, tag + "11-night-fullbright");
            set(context, world, c -> { c.fullbright = false; c.enabled = true; });

            world.getServer().runCommand("time set noon");
            // Camera inside the solid cube, below the pocket: the usual mining situation.
            world.getServer().runCommand("fill 0 113 1 0 114 1 minecraft:air");
            world.getServer().runCommand("tp @a 0 113 1 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(10);
            TestCompat.lookAt(context, new BlockPos(0, 118, 6));
            TestCompat.chunksRender(world);
            context.waitTicks(10);
            capture(context, tag + "12-inside-rock-xray-15");
            set(context, world, c -> c.enabled = false);
            capture(context, tag + "13-inside-rock-off");
            context.runOnClient(client -> dev.betterxray.compat.Mc.setScreen(client, new dev.betterxray.gui.XRayConfigScreen(null)));
            capture(context, tag + "14-config-screen");
            context.runOnClient(client -> dev.betterxray.compat.Mc.screen(client).onClose());

            // Phase 4 – shadow pass: a floor of a selected block (iron ore) below the cube, seen from
            // the side through the 7-block gap. With X-Ray the faint cube should not shadow the floor.
            world.getServer().runCommand("fill -12 104 -8 12 104 20 minecraft:iron_ore");
            world.getServer().runCommand("fill 15 104 4 17 104 8 minecraft:iron_ore");
            world.getServer().runCommand("tp @a 16 105 6 90 0");
            TestCompat.packets(context, world);
            context.waitTicks(10);
            TestCompat.lookAt(context, new BlockPos(0, 104, 6));
            TestCompat.chunksRender(world);
            context.waitTicks(10);
            capture(context, tag + "15-shadow-off");
            set(context, world, c -> { c.enabled = true; c.terrainOpacity = .15f; });
            capture(context, tag + "16-shadow-xray-15");
            set(context, world, c -> c.enabled = false);

            // Phase 5 – player label: a client-side player (half health) behind the cube, ESP box with
            // name and health bar drawn through the wall.
            world.getServer().runCommand("tp @a 0 118 -10 0 0");
            TestCompat.packets(context, world);
            context.runOnClient(client -> {
                var other = new net.minecraft.client.player.RemotePlayer(client.level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5b7c3a1e-9d2f-4c8b-a6e1-0f3d2c1b4a59"), "EspTester"));
                other.setId(990_001);
                other.snapTo(2.0, 112.0, 15.0, 180f, 0f);
                other.setOldPosAndRot();
                other.setHealth(10f);
                client.level.addEntity(other);
            });
            TestCompat.lookAt(context, new BlockPos(1, 114, 15));
            Path s18a = capture(context, tag + "18a-player-no-esp");
            set(context, world, c -> {
                c.mobEspEnabled = true;
                var players = c.espGroup(MobEspGroup.PLAYERS);
                players.enabled = true;
                players.style = XRayConfig.EspStyle.BOX;
                players.color = 0xffffaa00;
                c.playerEspName = true;
                c.playerEspHealth = XRayConfig.HealthDisplay.BOTH;
            });
            Path s18 = capture(context, tag + "18-player-label");
            set(context, world, c -> c.mobEspEnabled = false);

            // Phase 5 – underwater: a water basin inside stone, a diamond ore in the far wall.
            world.getServer().runCommand("fill 28 98 -2 42 108 16 minecraft:stone");
            world.getServer().runCommand("fill 30 100 0 40 106 13 minecraft:water");
            world.getServer().runCommand("setblock 35 103 15 minecraft:diamond_ore");
            world.getServer().runCommand("tp @a 35 102 2 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(20);
            TestCompat.lookAt(context, new BlockPos(35, 103, 15));
            TestCompat.chunksRender(world);
            context.runOnClient(client -> {
                if (!client.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER))
                    throw new AssertionError("Underwater fixture did not submerge the camera");
            });
            capture(context, tag + "19-underwater-off");
            set(context, world, c -> { c.enabled = true; c.terrainOpacity = .15f; });
            Path s20 = capture(context, tag + "20-underwater-xray-15");
            set(context, world, c -> c.enabled = false);

            // Phase 7 – regression checks on the screenshots, pack-independent: differences between two
            // shots of the same view and contrast between an ore and its surroundings.
            // With the version gate closed X-Ray is off under the pack on purpose; nothing to compare.
            if (xraySupported) {
                Check check = new Check(pack);
                check.above("ore contrast at 0 %", contrast(s03, 312, 248, 262, 248), 40);
                if (faintHidden) check.below("faint terrain hidden (separateAo)", diff(s04, s03, 230, 120, 14), 10);
                else check.above("faint terrain 50 % vs 0 %", diff(s04, s03, 230, 120, 14), 40);
                check.above("highlight + tracer", diff(s07, s02, 541, 248, 26), 12);
                for (int i = 0; i < walk.size(); i++)
                    check.below("tracer start off the crosshair while walking, frame " + i, tracerStartOffset(walk.get(i)), 5);
                check.above("ESP outline", diff(s08, s01, 427, 305, 20), 15);
                check.above("ESP box", diff(s09, s01, 427, 305, 20), 10);
                check.above("player label + box", changed(s18, s18a, 300, 150, 560, 330), 40);
                check.above("ore behind water", contrast(s20, 427, 200, 487, 200), 40);
                check.done();
            } else XRayClient.LOGGER.info("[BetterXray shader check] {} skipped: X-Ray disabled by the Iris version gate", pack);

            context.runOnClient(client -> { XRayConfigManager.reset(); XRayConfigManager.save(); XRayRenderController.apply(); });
            context.waitTicks(4);
        }
    }

    private static Path capture(ClientGameTestContext context, String name) {
        // Temporal effects (TAA, auto exposure) of the packs settle over a few frames.
        context.waitTicks(20);
        return context.takeScreenshot(name);
    }

    /**
     * Collects all screenshot checks of one run, logs every value and fails once at the end, so a
     * failing check never hides the others. Thresholds come from the five packs and the reference.
     */
    private static final class Check {
        private final String pack;
        private final StringBuilder failures = new StringBuilder();
        Check(String pack) { this.pack = pack; }
        void above(String what, int value, int min) { record(what, value, value > min, "> " + min); }
        void below(String what, int value, int max) { record(what, value, value < max, "< " + max); }
        private void record(String what, int value, boolean ok, String expected) {
            XRayClient.LOGGER.info("[BetterXray shader check] {} {}: {} (expected {}) {}", pack, what, value, expected, ok ? "ok" : "FAILED");
            if (!ok) failures.append("\n  ").append(what).append(": ").append(value).append(", expected ").append(expected);
        }
        void done() { if (!failures.isEmpty()) throw new AssertionError("Shader checks failed under " + pack + ":" + failures); }
    }

    // Coordinates are given for the 854x480 gametest window and scaled to the real screenshot size.
    private static BufferedImage image(Path path) {
        try { return ImageIO.read(path.toFile()); } catch (IOException e) { throw new AssertionError(e); }
    }
    private static int sx(BufferedImage image, int x) { return x * image.getWidth() / 854; }
    private static int sy(BufferedImage image, int y) { return y * image.getHeight() / 480; }

    /** Mean per-pixel RGB difference (sum of channels) of two shots in a square around (x, y). */
    private static int diff(Path a, Path b, int x, int y, int radius) {
        BufferedImage ia = image(a), ib = image(b);
        long total = 0; int n = 0;
        for (int dy = -radius; dy <= radius; dy += 2) for (int dx = -radius; dx <= radius; dx += 2) {
            total += rgbDistance(ia.getRGB(sx(ia, x + dx), sy(ia, y + dy)), ib.getRGB(sx(ib, x + dx), sy(ib, y + dy)));
            n++;
        }
        return (int)(total / n);
    }

    /** RGB distance between the mean colours of two small squares of one shot (an ore and beside it). */
    private static int contrast(Path shot, int x, int y, int otherX, int otherY) {
        BufferedImage image = image(shot);
        return rgbDistance(mean(image, x, y), mean(image, otherX, otherY));
    }

    /** Sampled pixels (every second one) in a rectangle whose colour changed by more than 60 between two shots. */
    private static int changed(Path a, Path b, int x0, int y0, int x1, int y1) {
        BufferedImage ia = image(a), ib = image(b);
        int count = 0;
        for (int y = y0; y < y1; y += 2) for (int x = x0; x < x1; x += 2)
            if (rgbDistance(ia.getRGB(sx(ia, x), sy(ia, y)), ib.getRGB(sx(ib, x), sy(ib, y))) > 60) count++;
        return count;
    }

    /**
     * Distance in pixels from the screen centre to the nearest tracer pixel within 60 px. Tracer
     * pixels: green clearly below red and blue (the magenta highlight colour, also when a pack draws
     * lines translucent and dark, as MakeUp does); sky, stone and the ores around do not match.
     */
    private static int tracerStartOffset(Path shot) {
        BufferedImage image = image(shot);
        int cx = image.getWidth() / 2, cy = image.getHeight() / 2, best = 999;
        for (int y = cy - 60; y <= cy + 60; y++) for (int x = cx - 60; x <= cx + 60; x++) {
            int p = image.getRGB(x, y), r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
            if (g < r - 10 && g < b - 30) best = Math.min(best, (int) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)));
        }
        return best;
    }

    private static int mean(BufferedImage image, int x, int y) {
        int r = 0, g = 0, b = 0, n = 0;
        for (int dy = -6; dy <= 6; dy += 2) for (int dx = -6; dx <= 6; dx += 2) {
            int p = image.getRGB(sx(image, x + dx), sy(image, y + dy));
            r += p >> 16 & 255; g += p >> 8 & 255; b += p & 255; n++;
        }
        return (r / n) << 16 | (g / n) << 8 | (b / n);
    }

    private static int rgbDistance(int p, int q) {
        return Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
    }

    private static void set(ClientGameTestContext context, TestSingleplayerContext world, java.util.function.Consumer<XRayConfig> change) {
        context.runOnClient(client -> {
            change.accept(XRayConfigManager.get());
            XRayRenderController.apply();
        });
        context.waitTicks(4);
        TestCompat.chunksRender(world);
        context.waitTicks(4);
    }

    private static Object irisPackInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            return "unavailable (" + e + ")";
        }
    }
}
