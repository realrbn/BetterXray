package dev.betterxray.test;

import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.XRayRenderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * ESP "Full View Distance": a zombie 160 blocks away is beyond its tracking range
 * (128 blocks), so the integrated server never sends it with the option off; with the option on
 * and ESP on for the zombie it reaches the client and gets its ESP box. Switching ESP or the option
 * off removes it again without anyone moving (periodic re-check).
 */
public final class FarEspTest implements FabricClientGameTest {
    private static final int DISTANCE = 160;

    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.skip(this) || !MobEspRenderer.FAR_TRACKING_TARGET) return;
        if (System.getProperty("betterxray.test.persistence") != null) return;
        int renderDistance = context.computeOnClient(client -> client.options.renderDistance().get());
        context.runOnClient(client -> client.options.renderDistance().set(12));
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode spectator @a"); // no falling: the zombie stays at eye level
            world.getServer().runCommand("gamerule spawn_mobs false");
            world.getServer().runCommand("forceload add " + DISTANCE + " 0");
            world.getServer().runCommand("tp @a 0 118.4 0 -90 0");
            world.getServer().runCommand("summon minecraft:zombie " + DISTANCE + " 120 0 "
                + "{NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            context.runOnClient(client -> {
                XRayConfigManager.reset();
                XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = true;
                XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).style = dev.betterxray.config.XRayConfig.EspStyle.BOX;
                XRayRenderController.apply();
            });
            TestCompat.packets(context, world);
            TestCompat.lookAt(context, -90, 0);
            context.waitTicks(100);
            context.runOnClient(client -> {
                if (zombies(client) != 0) throw new AssertionError("Zombie beyond its tracking range reached the client with the option off");
                if (XRayConfigManager.get().mobEspFullViewDistance) throw new AssertionError("Full View Distance must default to off");
                XRayConfigManager.get().mobEspFullViewDistance = true;
            });
            context.waitTicks(40);
            TestCompat.packets(context, world);
            context.waitTicks(4);
            TestCompat.screenshot(context, "far-esp-01-on");
            context.runOnClient(client -> {
                if (zombies(client) != 1) throw new AssertionError("Full View Distance did not send the far zombie, client has " + zombies(client));
                boolean boxed = MobEspRenderer.lastRenderedTargets().stream().anyMatch(t -> MobEspRenderer.id(t.type()).equals("minecraft:zombie") && t.x() > DISTANCE - 2);
                if (!boxed) throw new AssertionError("Far zombie has no ESP box: " + MobEspRenderer.lastRenderedTargets());
                // ESP off for zombies: the extended range no longer applies.
                XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = false;
            });
            context.waitTicks(40);
            TestCompat.packets(context, world);
            context.runOnClient(client -> {
                if (zombies(client) != 0) throw new AssertionError("Far zombie kept after its ESP was switched off");
                XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = true;
            });
            context.waitTicks(40);
            TestCompat.packets(context, world);
            context.runOnClient(client -> {
                if (zombies(client) != 1) throw new AssertionError("Far zombie not sent again after ESP was switched on");
                XRayConfigManager.get().mobEspFullViewDistance = false;
            });
            context.waitTicks(10);
            TestCompat.packets(context, world);
            context.runOnClient(client -> {
                if (zombies(client) != 0) throw new AssertionError("Far zombie kept after Full View Distance was switched off");
            });
        } finally {
            context.runOnClient(client -> {
                XRayConfigManager.reset();
                client.options.renderDistance().set(renderDistance);
            });
        }
        System.out.println("[BetterXray FarEsp] far ESP tracking checks passed");
    }

    private static int zombies(Minecraft client) {
        int count = 0;
        for (var entity : client.level.entitiesForRendering()) if (MobEspRenderer.id(entity.getType()).equals("minecraft:zombie")) count++;
        return count;
    }
}
