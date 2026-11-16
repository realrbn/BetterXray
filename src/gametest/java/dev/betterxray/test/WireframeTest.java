package dev.betterxray.test;

import dev.betterxray.XRayClient;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Wireframe mode: a solid 9x9x9 stone cube floating in the sky, seen from the front. With X-Ray at
 * 80 % the cube's front is a closed surface; in the Wireframe mode the same terrain is only lines,
 * so most of the pixels in front of the cube show the sky. The lines stay when the terrain opacity
 * is 0 % (faint terrain would be hidden), and switching the mode off restores the faces.
 */
public final class WireframeTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.skip(this) || !XRayState.WIREFRAME_TARGET) return;
        if (System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            KeyMapping key = KeyMapping.get("key.betterxray.wireframe");
            if (key == null) throw new AssertionError("Wireframe hotkey is not registered");
            if (!key.isUnbound()) throw new AssertionError("Wireframe hotkey must be unbound by default");
            if (XRayConfigManager.get().wireframe) throw new AssertionError("Wireframe must default to off");
        });
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("fill -4 116 0 4 124 8 minecraft:stone");
            world.getServer().runCommand("fill -1 117 -11 1 117 -9 minecraft:stone");
            world.getServer().runCommand("tp @a 0 118 -10 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(40);
            TestCompat.lookAt(context, new BlockPos(0, 120, 7));
            TestCompat.chunksRender(world);
            if (context.computeOnClient(client -> XRayState.wireframeUnavailable())) {
                // No line polygon mode on this device: the switch must leave the faint terrain alone.
                set(context, world, true, .80f);
                expect(TestCompat.screenshot(context, "wireframe-unavailable"), .9, 1, "terrain without wireframe support");
                reset(context, world);
                return;
            }

            // X-Ray at 80 %, mode off: the front of the cube is a closed surface.
            context.runOnClient(client -> {
                XRayConfigManager.get().enabled = true;
                XRayConfigManager.get().terrainOpacity = .80f;
                XRayRenderController.apply();
            });
            rebuild(context, world);
            expect(TestCompat.screenshot(context, "wireframe-01-filled-80-percent"), .9, 1, "filled terrain at 80 %");

            // Mode on (as the hotkey would): lines only, in front of the sky.
            context.runOnClient(XRayClient::toggleWireframe);
            rebuild(context, world);
            context.runOnClient(client -> {
                if (!XRayConfigManager.get().wireframe || !XRayState.wireframe()) throw new AssertionError("Wireframe did not switch on");
                if (XRayState.terrainOpacity() < .99f) throw new AssertionError("Line opacity is " + XRayState.terrainOpacity());
            });
            expect(TestCompat.screenshot(context, "wireframe-02-lines"), .01, .6, "wireframe lines");

            // Terrain opacity 0 %: faint terrain would not be meshed, the lines still are.
            set(context, world, true, 0f);
            context.runOnClient(client -> {
                if (XRayState.hideTerrain()) throw new AssertionError("Wireframe terrain hidden at 0 % terrain opacity");
            });
            expect(TestCompat.screenshot(context, "wireframe-03-lines-0-percent"), .01, .6, "wireframe lines at 0 % terrain opacity");

            // X-Ray and Cave-Find off: the mode alone changes nothing.
            context.runOnClient(client -> {
                XRayConfigManager.get().enabled = false;
                XRayRenderController.apply();
                if (XRayState.wireframe()) throw new AssertionError("Wireframe active without the X-Ray view");
            });
            rebuild(context, world);
            expect(TestCompat.screenshot(context, "wireframe-04-xray-off"), .9, 1, "vanilla terrain with only the mode on");

            // Mode off again with X-Ray at 80 %: faces are back.
            set(context, world, false, .80f);
            expect(TestCompat.screenshot(context, "wireframe-05-filled-again"), .9, 1, "filled terrain after switching off");
            reset(context, world);
        }
    }

    private static void set(ClientGameTestContext context, TestSingleplayerContext world, boolean wireframe, float opacity) {
        context.runOnClient(client -> {
            var config = XRayConfigManager.get();
            config.enabled = true;
            config.wireframe = wireframe;
            config.terrainOpacity = opacity;
            XRayRenderController.apply();
        });
        rebuild(context, world);
    }

    private static void reset(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            XRayConfigManager.save();
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

    private static void expect(Path screenshot, double min, double max, String what) {
        double fraction = nonSky(screenshot);
        if (fraction < min || fraction > max)
            throw new AssertionError(what + ": " + Math.round(fraction * 1000) / 10.0 + " % non-sky pixels, expected "
                + Math.round(min * 100) + "–" + Math.round(max * 100) + " % (" + screenshot + ")");
    }

    /** Fraction of non-sky pixels (sky: blue clearly above red) in the center of the cube's front. */
    private static double nonSky(Path screenshot) {
        try {
            BufferedImage image = ImageIO.read(screenshot.toFile());
            int w = image.getWidth(), h = image.getHeight();
            int cx = w / 2, cy = h / 2, half = h / 4;
            int solid = 0, total = 0;
            for (int y = cy - half; y < cy + half; y++) for (int x = cx - half; x < cx + half; x++) {
                if (Math.abs(x - cx) < 6 && Math.abs(y - cy) < 6) continue; // crosshair
                int p = image.getRGB(x, y);
                if ((p & 255) - (p >> 16 & 255) < 40) solid++;
                total++;
            }
            return total == 0 ? 0 : (double)solid / total;
        } catch (IOException e) { throw new AssertionError(e); }
    }
}
