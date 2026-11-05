package dev.betterxray.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client gametest helpers for APIs that differ between Minecraft/Fabric API versions. */
final class TestCompat {
    private TestCompat() {}

    /** Shader baseline mode: the pack file name, or null. Only ShaderBaselineTest runs then. */
    static String shaderPack() { return System.getProperty("betterxray.test.shaderPack"); }

    /**
     * World builder for the fixtures. 1.21.11 searches a spawn point candidate by candidate
     * (one chunk load each, up to 441 in lock-step with the client); radius 0 keeps world
     * creation within the gametest timeout on slow software-rendered machines.
     */
    static net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder worldBuilder(ClientGameTestContext context) {
        //? if >=1.21.11 && <26.1 {
        /*// The server only runs chunk tasks while it is not behind; with a software renderer the
        // blurred loading screen keeps the lock-stepped client below 20 frames per tick.
        context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
        return context.worldBuilder().adjustSettings(creator -> creator.getGameRules()
            .set(net.minecraft.world.level.gamerules.GameRules.RESPAWN_RADIUS, 0, null));
        *///?} else {
        return context.worldBuilder();
        //?}
    }

    /** Lets the client process the packets the server sent for the previous action. */
    static void packets(ClientGameTestContext context, TestSingleplayerContext world) {
        //? if >=26.1 {
        world.getConnection().waitForClientboundPackets();
        //?} else {
        /*context.waitTicks(3);
        *///?}
    }

    static void chunksRender(TestSingleplayerContext world) {
        //? if >=26.1 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientWorld().waitForChunksRender();
        *///?}
    }

    static void lookAt(ClientGameTestContext context, float yaw, float pitch) {
        //? if >=26.1 {
        context.getInput().lookAt(yaw, pitch);
        //?} else {
        /*context.runOnClient(client -> {
            client.player.setYRot(yaw);
            client.player.setXRot(pitch);
            client.player.yRotO = yaw;
            client.player.xRotO = pitch;
            client.player.setYHeadRot(yaw);
        });
        *///?}
    }

    static void lookAt(ClientGameTestContext context, BlockPos target) {
        //? if >=26.1 {
        context.getInput().lookAt(target);
        //?} else {
        /*float[] angles = context.computeOnClient(client -> {
            Vec3 eye = client.player.getEyePosition();
            Vec3 to = Vec3.atCenterOf(target).subtract(eye);
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
            float pitch = (float)(-(Mth.atan2(to.y, horizontal) * Mth.RAD_TO_DEG));
            return new float[] {Mth.wrapDegrees(yaw), pitch};
        });
        lookAt(context, angles[0], angles[1]);
        *///?}
    }

    static void resizeGui(Minecraft client) {
        //? if >=26.1 {
        client.resizeGui();
        //?} else {
        /*client.resizeDisplay();
        *///?}
    }

    /** Runs the level renderer's entity extraction for one entity, as a frame would. */
    static EntityRenderState extracted(Minecraft client, Entity entity) {
        try {
            //? if >=26.1 {
            Object owner = client.levelExtractor;
            //?} else {
            /*Object owner = client.levelRenderer;
            *///?}
            return ((dev.betterxray.test.mixin.EntityExtractAccess)owner).betterxrayTest$extractEntity(entity, 0.5f);
        } catch (ClassCastException e) { throw new AssertionError(e); }
    }

    private record Expectation(Entity entity, String message) {}
    private static final java.util.List<Expectation> PENDING = new java.util.ArrayList<>();

    /**
     * Asserts that the entity passes entity culling (distance and section occlusion). 26.x can
     * ask the level extractor directly; 1.21.11 checks the next rendered frame (see screenshot).
     */
    static void expectRendered(Minecraft client, Entity entity, String message) {
        if (!renderedLastFrame(client, entity)) throw new AssertionError(message);
    }

    /** Takes a screenshot (rendering frames) and then verifies pending render expectations. */
    static java.nio.file.Path screenshot(ClientGameTestContext context, String name) {
        java.nio.file.Path path = context.takeScreenshot(name);
        if (!PENDING.isEmpty()) {
            context.runOnClient(client -> {
                try {
                    for (var expectation : PENDING)
                        if (!renderedLastFrame(client, expectation.entity())) throw new AssertionError(expectation.message());
                } finally {
                    PENDING.clear();
                }
            });
        }
        return path;
    }

    /** Whether the last rendered frame included this entity (it passed distance and occlusion culling). */
    static boolean renderedLastFrame(Minecraft client, Entity entity) {
        //? if >=26.1 {
        var camera = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        //? if >=26.3 {
        return client.levelExtractor.isEntityVisible(entity, camera.cullFrustum, camera.pos.x, camera.pos.y, camera.pos.z, 0.5f, 0L);
        //?} else {
        /*return client.levelExtractor.isEntityVisible(entity, camera.cullFrustum, camera.pos.x, camera.pos.y, camera.pos.z);
        *///?}
        //?} else {
        /*// Runs the frame's entity extraction (including the ESP culling bypass) for the
        // current camera into a scratch state; the real frame state is reset after rendering.
        try {
            var renderer = client.levelRenderer;
            var camera = client.gameRenderer.getMainCamera();
            var view = new org.joml.Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf()));
            var projection = client.gameRenderer.getProjectionMatrix(client.options.fov().get().floatValue());
            var frustum = new net.minecraft.client.renderer.culling.Frustum(view, projection);
            var pos = camera.position();
            frustum.prepare(pos.x, pos.y, pos.z);
            var frame = new net.minecraft.client.renderer.state.level.LevelRenderState();
            ((dev.betterxray.test.mixin.EntityExtractAccess)renderer).betterxrayTest$extractVisibleEntities(camera, frustum,
                client.getDeltaTracker(), frame);
            for (var state : frame.entityRenderStates) {
                if (state.entityType == entity.getType() && Math.abs(state.x - entity.getX()) < 1.0
                    && Math.abs(state.y - entity.getY()) < 1.0 && Math.abs(state.z - entity.getZ()) < 1.0) return true;
            }
            return false;
        } catch (ClassCastException e) { throw new AssertionError(e); }
        *///?}
    }

    static void openChat(Minecraft client) {
        //? if >=26.1 {
        client.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE);
        //?} else {
        /*client.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE);
        *///?}
    }

    static boolean chatContains(Minecraft client, String text) {
        //? if >=26.1 {
        for (Object message : ((dev.betterxray.test.mixin.ChatAccess)client.gui.hud.getChat()).betterxrayTest$messages())
            if (((net.minecraft.client.multiplayer.chat.GuiMessage)message).content().getString().equals(text)) return true;
        //?} else {
        /*for (Object message : ((dev.betterxray.test.mixin.ChatAccess)client.gui.getChat()).betterxrayTest$messages())
            if (((net.minecraft.client.GuiMessage)message).content().getString().equals(text)) return true;
        *///?}
        return false;
    }
}
