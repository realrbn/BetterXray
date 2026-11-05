package dev.betterxray.test;

import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Cave-Find: a stone cube floating in the sky with an enclosed 5x5x5 air pocket and a
 * diamond ore (default preset) embedded in its solid roof. With X-Ray at 0 % only the ore is left;
 * Cave-Find alone shows exactly the pocket's walls (faces next to air without sky light) and no
 * preset blocks; Cave-Find together with X-Ray shows both.
 */
public final class CaveFindTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null) return;
        if (System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            KeyMapping key = KeyMapping.get("key.betterxray.cave_find");
            if (key == null || !key.getDefaultKey().getName().equals("key.keyboard.u")) throw new AssertionError("Cave-Find hotkey default is not U");
            for (KeyMapping other : client.options.keyMappings)
                if (other != key && other.same(key)) throw new AssertionError("Cave-Find hotkey U conflicts with " + other.getName());
            if (XRayConfigManager.get().caveFind) throw new AssertionError("Cave-Find must default to off");
            if (!XRayConfigManager.get().getActiveVisibleBlocks().contains("minecraft:diamond_ore"))
                throw new AssertionError("Default preset lacks diamond ore");
        });
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("fill -4 116 0 4 124 8 minecraft:stone");
            world.getServer().runCommand("fill -2 118 2 2 122 6 minecraft:air");
            // In the roof above the pocket's entrance side, touching neither the pocket nor the open air.
            world.getServer().runCommand("setblock 0 123 1 minecraft:diamond_ore");
            // Standing on a small platform, the eye (1.62 above the feet) is level with the pocket center.
            world.getServer().runCommand("fill -1 117 -11 1 117 -9 minecraft:stone");
            world.getServer().runCommand("tp @a 0 118 -10 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(40);
            TestCompat.lookAt(context, new BlockPos(0, 120, 7));
            TestCompat.chunksRender(world);
            context.runOnClient(client -> {
                int sky = client.level.getBrightness(LightLayer.SKY, new BlockPos(0, 120, 4));
                if (sky != 0) throw new AssertionError("Fixture pocket has sky light " + sky);
                if (client.level.getBrightness(LightLayer.SKY, new BlockPos(0, 120, -1)) == 0)
                    throw new AssertionError("Air in front of the cube has no sky light");
            });

            Path vanilla = TestCompat.screenshot(context, "cave-01-vanilla");
            expect(vanilla, Region.CENTER, .9, 1, "vanilla cube front");
            expect(vanilla, Region.RING, .9, 1, "vanilla cube front");

            // X-Ray at 0 %: the cube is gone, the embedded preset ore remains.
            set(context, world, true, false, 0f);
            Path xray = TestCompat.screenshot(context, "cave-02-xray-0-percent-ore");
            expect(xray, Region.CENTER, 0, .05, "X-Ray 0 % center");
            expect(xray, Region.RING, .05, 1, "preset ore with X-Ray");

            // Cave-Find alone (X-Ray switch off), 0 %: only the pocket walls, no preset ore.
            set(context, world, false, true, 0f);
            context.runOnClient(client -> {
                if (!XRayState.enabled() || !XRayState.caveFind()) throw new AssertionError("Cave-Find did not activate the X-Ray view");
                if (XRayState.selected(client.level.getBlockState(new BlockPos(0, 123, 1))))
                    throw new AssertionError("Preset block selected while only Cave-Find is on");
            });
            Path caves = TestCompat.screenshot(context, "cave-03-cave-find-only");
            expect(caves, Region.CENTER, .5, 1, "cave walls");
            expect(caves, Region.RING, 0, .02, "outer faces and preset ore with Cave-Find only");

            // Cave-Find with X-Ray: caves and the preset ore.
            set(context, world, true, true, 0f);
            Path both = TestCompat.screenshot(context, "cave-04-cave-find-and-xray");
            expect(both, Region.CENTER, .5, 1, "cave walls with X-Ray");
            expect(both, Region.RING, .05, 1, "preset ore with Cave-Find and X-Ray");

            // At 15 % (X-Ray off) the rest of the cube is faint again, the cave walls stay solid.
            set(context, world, false, true, .15f);
            Path faint = TestCompat.screenshot(context, "cave-05-cave-find-15-percent");
            expect(faint, Region.CENTER, .5, 1, "cave walls at 15 %");

            // Real key press on U: Cave-Find off (back to vanilla, X-Ray is off), then on again.
            context.getInput().pressKey(options -> KeyMapping.get("key.betterxray.cave_find"));
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (XRayConfigManager.get().caveFind || XRayState.enabled()) throw new AssertionError("Hotkey U did not switch Cave-Find off");
            });
            rebuild(context, world);
            Path off = TestCompat.screenshot(context, "cave-06-hotkey-off");
            expect(off, Region.RING, .9, 1, "terrain restored after hotkey U");
            context.getInput().pressKey(options -> KeyMapping.get("key.betterxray.cave_find"));
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (!XRayConfigManager.get().caveFind || !XRayState.caveFind()) throw new AssertionError("Hotkey U did not switch Cave-Find on again");
                XRayConfigManager.reset();
                XRayConfigManager.save();
                XRayRenderController.apply();
            });
            rebuild(context, world);
        }
    }

    private static void set(ClientGameTestContext context, TestSingleplayerContext world, boolean xray, boolean caveFind, float opacity) {
        context.runOnClient(client -> {
            var config = XRayConfigManager.get();
            config.enabled = xray;
            config.caveFind = caveFind;
            config.terrainOpacity = opacity;
            XRayRenderController.apply();
        });
        rebuild(context, world);
    }

    /** Waits for the requested mesh rebuild (next client tick) and the rebuilt sections. */
    private static void rebuild(ClientGameTestContext context, TestSingleplayerContext world) {
        context.waitTicks(4);
        TestCompat.chunksRender(world);
        context.waitTicks(4);
    }

    /**
     * CENTER: the pocket's far wall (about ±8° around the view direction). RING: a band about 17°
     * above the center, on the cube's front face above the pocket, where the embedded ore sits.
     */
    private enum Region { CENTER, RING }

    private static void expect(Path screenshot, Region region, double min, double max, String what) {
        double fraction = nonSky(screenshot, region);
        if (fraction < min || fraction > max)
            throw new AssertionError(what + ": " + Math.round(fraction * 100) + " % non-sky pixels in " + region
                + ", expected " + Math.round(min * 100) + "–" + Math.round(max * 100) + " % (" + screenshot + ")");
    }

    /** Fraction of pixels in {@code region} that are not sky (sky: blue clearly above red). */
    private static double nonSky(Path screenshot, Region region) {
        try {
            BufferedImage image = ImageIO.read(screenshot.toFile());
            int w = image.getWidth(), h = image.getHeight();
            int cx = w / 2, cy = h / 2, half = h / 2;
            int x0, x1, y0, y1;
            if (region == Region.CENTER) {
                x0 = cx - half / 8; x1 = cx + half / 8; y0 = cy - half / 8; y1 = cy + half / 8;
            } else {
                x0 = cx - half / 6; x1 = cx + half / 6; y0 = cy - half * 50 / 100; y1 = cy - half * 40 / 100;
            }
            int solid = 0, total = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                if (Math.abs(x - cx) < 6 && Math.abs(y - cy) < 6) continue; // crosshair
                int p = image.getRGB(x, y);
                if ((p & 255) - (p >> 16 & 255) < 40) solid++;
                total++;
            }
            return total == 0 ? 0 : (double)solid / total;
        } catch (IOException e) { throw new AssertionError(e); }
    }
}
