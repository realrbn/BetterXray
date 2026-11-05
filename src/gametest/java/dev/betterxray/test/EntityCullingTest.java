package dev.betterxray.test;

import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayRenderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Method;

/**
 * Entity Culling (only with -Pentityculling): a NoAI zombie behind a stone wall and a chest sealed
 * in stone. First Entity Culling must cull both (otherwise the fixture proves nothing); then an
 * ESP-enabled zombie and, with the X-Ray view, both of them must stay unculled; with both switched
 * off again Entity Culling culls the zombie again.
 */
public final class EntityCullingTest implements FabricClientGameTest {
    private static final BlockPos CHEST = new BlockPos(0, 62, 12);
    private static final int WAIT = 80;

    @Override public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("entityculling")) return;
        if (TestCompat.shaderPack() != null || System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            var config = XRayConfigManager.get();
            config.mobEspEnabled = true;
            config.mobEspTracersEnabled = false;
            config.espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = false;
            XRayRenderController.apply();
        });
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("fill -12 60 -12 12 72 20 minecraft:air");
            world.getServer().runCommand("fill -12 59 -12 12 59 20 minecraft:stone");
            world.getServer().runCommand("fill -4 60 2 4 66 2 minecraft:stone");
            world.getServer().runCommand("summon minecraft:zombie 0 60 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            world.getServer().runCommand("fill -2 60 10 2 64 14 minecraft:stone");
            world.getServer().runCommand("setblock 0 62 12 minecraft:chest");
            world.getServer().runCommand("tp @a 0 60 -6 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(40);
            TestCompat.lookAt(context, 0, 0);
            TestCompat.chunksRender(world);

            // Without ESP and X-Ray, Entity Culling hides both: the fixture really occludes them.
            await(context, client -> culled(zombie(client)) && culled(client.level.getBlockEntity(CHEST)),
                "Entity Culling did not cull the hidden zombie and chest (fixture or Entity Culling inactive)");
            context.takeScreenshot("ec-01-culled");

            // ESP target, X-Ray off: exempt, and its extraction is the real zombie, not the placeholder.
            context.runOnClient(client -> XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = true);
            await(context, client -> !culled(zombie(client)), "ESP zombie stayed culled by Entity Culling");
            context.waitTicks(10);
            context.runOnClient(client -> {
                Entity zombie = zombie(client);
                if (culled(zombie)) throw new AssertionError("ESP zombie was culled again");
                if (TestCompat.extracted(client, zombie).entityType != zombie.getType())
                    throw new AssertionError("ESP zombie extracted as Entity Culling's placeholder");
                if (!culled(client.level.getBlockEntity(CHEST))) throw new AssertionError("Chest unculled without X-Ray");
            });
            context.takeScreenshot("ec-02-esp");

            // X-Ray view, no ESP: nothing is culled behind the see-through terrain.
            context.runOnClient(client -> {
                XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = false;
                XRayConfigManager.get().enabled = true;
                XRayRenderController.apply();
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            await(context, client -> !culled(zombie(client)) && !culled(client.level.getBlockEntity(CHEST)),
                "Zombie or chest stayed culled with X-Ray on");
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (culled(zombie(client)) || culled(client.level.getBlockEntity(CHEST)))
                    throw new AssertionError("Zombie or chest culled again with X-Ray on");
            });
            context.takeScreenshot("ec-03-xray");

            // Both off: Entity Culling works normally again.
            context.runOnClient(client -> {
                XRayConfigManager.get().enabled = false;
                XRayRenderController.apply();
            });
            context.waitTicks(20);
            TestCompat.chunksRender(world);
            await(context, client -> culled(zombie(client)) && culled(client.level.getBlockEntity(CHEST)),
                "Entity Culling did not resume culling after X-Ray and ESP were switched off");
            context.takeScreenshot("ec-04-restored");
        } finally {
            context.runOnClient(client -> {
                XRayConfigManager.reset();
                XRayRenderController.apply();
            });
        }
        System.out.println("[BetterXray EntityCulling] all Entity Culling cases passed");
    }

    private interface Check { boolean test(Minecraft client); }

    private static void await(ClientGameTestContext context, Check check, String message) {
        for (int i = 0; i < WAIT; i++) {
            if (context.computeOnClient(check::test)) return;
            context.waitTick();
        }
        throw new AssertionError(message);
    }

    private static Entity zombie(Minecraft client) {
        for (Entity entity : client.level.entitiesForRendering())
            if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("zombie"))
                return entity;
        throw new AssertionError("Zombie not loaded on the client");
    }

    private static Method isCulled;

    /** Entity Culling's own flag ({@code Cullable.isCulled}), mixed into entities and block entities. */
    private static boolean culled(Object target) {
        if (target == null) throw new AssertionError("Culling target missing");
        try {
            if (isCulled == null)
                isCulled = Class.forName("dev.tr7zw.entityculling.access.Cullable").getMethod("isCulled");
            return (Boolean) isCulled.invoke(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
