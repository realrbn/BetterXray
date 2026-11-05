package dev.betterxray;

import dev.betterxray.compat.Mc;
import dev.betterxray.compat.EntityCullingCompat;
import dev.betterxray.compat.NvidiumNotice;
import com.mojang.blaze3d.platform.InputConstants;

import dev.betterxray.command.XRayCommands;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.debug.DebugHud;
import dev.betterxray.gui.XRayConfigScreen;
import dev.betterxray.render.*;
import dev.betterxray.compat.Platform;
import dev.betterxray.compat.RendererCompatibility;
import dev.betterxray.update.UpdateChecker;
//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
//?} else if neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
*///?} else {
/*import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
//? if <1.21
/^import net.minecraftforge.client.event.RenderGuiEvent;^/
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
*///?}
//? if fabric && >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else if fabric {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
//? if <1.21.11 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
//? if fabric {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?} else if neoforge {
/^import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
^///?}
*///?}
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if fabric {
public final class XRayClient implements ClientModInitializer {
//?} else if neoforge {
/*@Mod(value = "betterxray", dist = Dist.CLIENT)
public final class XRayClient {
*///?} else {
/*/// Client part of the Forge mod; {@link BetterXRay} starts it on the physical client only.
public final class XRayClient {
*///?}
    public static final Logger LOGGER = LoggerFactory.getLogger("betterxray");
    private static KeyMapping toggleKey;
    private static KeyMapping fullbrightKey;
    private static KeyMapping configKey;
    private static KeyMapping cyclePresetKey;
    private static KeyMapping quickAddKey;
    private static KeyMapping highlightKey;
    private static KeyMapping tracersKey;
    private static KeyMapping espKey;
    /** Toggles the ESP tracers. */
    private static KeyMapping espTracersKey;
    /** Toggles Cave-Find. */
    private static KeyMapping caveFindKey;
    private static boolean keyMappingsNeedSave;
    private static final java.util.List<KeyMapping> KEY_MAPPINGS = new java.util.ArrayList<>();

    /** All Better X-Ray key mappings. */
    public static java.util.List<KeyMapping> keyMappings() { return KEY_MAPPINGS; }

    //? if fabric {
    @Override public void onInitializeClient() {
        initialize();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> RendererCompatibility.initialize());
        ClientTickEvents.END_CLIENT_TICK.register(XRayClient::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> XRayCommands.register(dispatcher));
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Mc.id("betterxray", "debug_hud"), (g, delta) -> DebugHud.draw(g));
        //?} else {
        /*HudRenderCallback.EVENT.register((g, delta) -> DebugHud.draw(g));
        *///?}
    }
    //?} else if neoforge {
    /*public XRayClient(IEventBus modBus, ModContainer container) {
        var category = initialize();
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            //? if >=1.21.9
            event.registerCategory(category);
            KEY_MAPPINGS.forEach(event::register);
        });
        //? if <1.21.11 {
        /^modBus.addListener(RegisterShadersEvent.class, BlockEntityBuffers::registerShader);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
            // Vertices are camera-relative; the camera rotation goes onto the model-view stack.
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.mul(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            try {
                renderOverlays(event.getCamera(), event.getFrustum(), event.getPartialTick().getGameTimeDeltaPartialTick(false),
                    new PoseStack().last(), event.getProjectionMatrix(), new Matrix4f(event.getModelViewMatrix()));
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        });
        ^///?}
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(RendererCompatibility::initialize));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> XRayCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, event -> DebugHud.draw(event.getGuiGraphics()));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new XRayConfigScreen(parent));
    }
    *///?} else {
    /*/// Forge: called by {@link BetterXRay} on the physical client.
    static void init(IEventBus modBus) {
        initialize();
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, event -> KEY_MAPPINGS.forEach(event::register));
        modBus.addListener(EventPriority.NORMAL, false, RegisterShadersEvent.class, BlockEntityBuffers::registerShader);
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> event.enqueueWork(RendererCompatibility::initialize));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) tick(Minecraft.getInstance());
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterClientCommandsEvent.class,
            event -> XRayCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, XRayClient::renderStage);
        // Forge 1.21.1 has no HUD event; DebugHudMixin draws the debug HUD there.
        //? if <1.21
        /^MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiEvent.Post.class, event -> DebugHud.draw(event.getGuiGraphics()));^/
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> new XRayConfigScreen(parent)));
    }

    /^* World view of the current frame, captured at AFTER_ENTITIES. ^/
    private static final Matrix4f LEVEL_VIEW = new Matrix4f();

    private static void renderStage(RenderLevelStageEvent event) {
        // These Forge releases pass the projection as the AFTER_LEVEL pose: the world view (including
        // Forge's camera roll) is taken from AFTER_ENTITIES earlier in the same frame.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            //? if >=1.21 {
            LEVEL_VIEW.set(event.getPoseStack());
            //?} else
            /^LEVEL_VIEW.set(event.getPoseStack().last().pose());^/
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        // Vertices are camera-relative; the camera rotation goes onto the model-view stack.
        var modelView = RenderSystem.getModelViewStack();
        //? if >=1.21 {
        modelView.pushMatrix();
        modelView.mul(LEVEL_VIEW);
        //?} else {
        /^modelView.pushPose();
        modelView.mulPoseMatrix(LEVEL_VIEW);
        ^///?}
        RenderSystem.applyModelViewMatrix();
        try {
            renderOverlays(event.getCamera(), event.getFrustum(), event.getPartialTick(), new PoseStack().last(),
                event.getProjectionMatrix(), new Matrix4f(LEVEL_VIEW));
        } finally {
            //? if >=1.21 {
            modelView.popMatrix();
            //?} else
            /^modelView.popPose();^/
            RenderSystem.applyModelViewMatrix();
        }
    }
    *///?}

    //? if >=1.21.9 {
    private static KeyMapping.Category initialize() {
    //?} else {
    /*private static String initialize() {
    *///?}
        XRayConfigManager.load();
        XRayState.publish(XRayConfigManager.get());
        boolean sodium = Platform.sodiumLoaded();
        LOGGER.info("[XRay] Renderer backend: {}", sodium ? "Sodium" : "Vanilla");
        //? if neoforge && >=1.21.9 {
        /*// NeoForge registers the category with the key mappings (RegisterKeyMappingsEvent).
        KeyMapping.Category category = new KeyMapping.Category(Mc.id("betterxray", "controls"));
        *///?} else if >=1.21.9 {
        KeyMapping.Category category = KeyMapping.Category.register(Mc.id("betterxray", "controls"));
        //?} else {
        /*String category = "key.category.betterxray.controls";
        *///?}
        toggleKey = registerKey("key.betterxray.toggle", InputConstants.KEY_X, category);
        fullbrightKey = registerKey("key.betterxray.fullbright", InputConstants.KEY_L, category);
        configKey = registerKey("key.betterxray.config", InputConstants.KEY_G, category);
        cyclePresetKey = registerKey("key.betterxray.cycle_preset", InputConstants.UNKNOWN.getValue(), category);
        quickAddKey = registerKey("key.betterxray.quick_add_block", InputConstants.UNKNOWN.getValue(), category);
        highlightKey = registerKey("key.betterxray.highlight", InputConstants.KEY_H, category);
        tracersKey = registerKey("key.betterxray.tracers", InputConstants.UNKNOWN.getValue(), category);
        espKey = registerKey("key.betterxray.esp", InputConstants.KEY_J, category);
        espTracersKey = registerKey("key.betterxray.esp_tracers", InputConstants.KEY_K, category);
        caveFindKey = registerKey("key.betterxray.cave_find", InputConstants.KEY_U, category);
        //? if fabric && <1.21.11 {
        /*WorldRenderEvents.LAST.register(XRayClient::renderOverlays);
        dev.betterxray.render.BlockEntityBuffers.registerShader();
        *///?}
        return category;
    }

    private static void tick(Minecraft client) {
        UpdateChecker.tick(client);
        NvidiumNotice.tick(client);
        EntityCullingCompat.tick();
        if (keyMappingsNeedSave) {
            client.options.save();
            keyMappingsNeedSave = false;
        }
        RendererCompatibility.tick();
        while (toggleKey.consumeClick()) {
            RendererCompatibility.refresh();
            XRayConfigManager.get().enabled = !XRayConfigManager.get().enabled;
            XRayRenderController.apply();
            if (client.player != null) Mc.overlay(client, Component.literal(
                XRayState.supported() ? "X-Ray: " + (XRayState.get().enabled() ? "ON" : "OFF") : "X-Ray unavailable with this renderer / shader pack"));
        }
        while (fullbrightKey.consumeClick()) {
            // Keyboard presses are handled at the input event so L remains reliable
            // when another Minecraft control uses the same default key. Mouse-bound
            // customizations continue to use the normal key mapping click queue.
            if (fullbrightKey.saveString().startsWith("key.mouse.")) toggleFullbright(client);
        }
        while (configKey.consumeClick()) {
            if (configKey.saveString().startsWith("key.mouse.")) Mc.setScreen(client, new XRayConfigScreen(Mc.screen(client)));
        }
        while (cyclePresetKey.consumeClick()) {
            var xrayConfig = XRayConfigManager.get();
            xrayConfig.cycleActivePreset();
            XRayRenderController.apply();
            if (client.player != null) Mc.overlay(client, Component.translatable(
                "message.betterxray.preset", xrayConfig.getActivePreset().name));
        }
        while (quickAddKey.consumeClick()) addTargetedBlock(client);
        while (highlightKey.consumeClick()) toggleHighlight(client);
        while (tracersKey.consumeClick()) toggleTracers(client);
        while (espKey.consumeClick()) toggleEsp(client);
        while (espTracersKey.consumeClick()) toggleEspTracers(client);
        while (caveFindKey.consumeClick()) toggleCaveFind(client);
        HighlightCache.tick(client);
        StructureFinder.tick(client);
        XRayRenderController.tick(client);
        DebugHud.tick(client);
    }

    //? if >=1.21.9 {
    private static KeyMapping registerKey(String translationKey, int keyCode, KeyMapping.Category category) {
    //?} else {
    /*private static KeyMapping registerKey(String translationKey, int keyCode, String category) {
    *///?}
        var mapping = new KeyMapping(translationKey, InputConstants.UNKNOWN.getType(), keyCode, category);
        KEY_MAPPINGS.add(mapping);
        //? if !fabric {
        /*return mapping;
        *///?} else if >=26.1 {
        return KeyMappingHelper.registerKeyMapping(mapping);
        //?} else {
        /*return KeyBindingHelper.registerKeyBinding(mapping);
        *///?}
    }

    /** Called immediately before Minecraft polls every bound key on focus regain. */
    public static void repairLegacyKeyMappings() {
        keyMappingsNeedSave |= repairLegacyUnbound(cyclePresetKey);
        keyMappingsNeedSave |= repairLegacyUnbound(quickAddKey);
        keyMappingsNeedSave |= repairLegacyUnbound(tracersKey);
        if (keyMappingsNeedSave) KeyMapping.resetMapping();
    }

    private static boolean repairLegacyUnbound(KeyMapping mapping) {
        if (mapping == null || !mapping.saveString().equals("key.keyboard.-1")) return false;
        LOGGER.warn("[XRay] Migrating invalid 26.2 key binding {} from -1 to {}",
            mapping.getName(), InputConstants.UNKNOWN.getValue());
        mapping.setKey(InputConstants.UNKNOWN);
        return true;
    }

    //? if >=1.21.9 {
    public static boolean handlePriorityKey(Minecraft client, int action, KeyEvent event) {
        if (Mc.screen(client) != null || action != 1) return false;
        if (fullbrightKey != null && fullbrightKey.matches(event)) {
            toggleFullbright(client);
            return true;
        }
        if (configKey != null && configKey.matches(event)) {
    //?} else {
    /*public static boolean handlePriorityKey(Minecraft client, int action, int key, int scancode) {
        if (Mc.screen(client) != null || action != 1) return false;
        if (fullbrightKey != null && fullbrightKey.matches(key, scancode)) {
            toggleFullbright(client);
            return true;
        }
        if (configKey != null && configKey.matches(key, scancode)) {
    *///?}
            Mc.setScreen(client, new XRayConfigScreen(Mc.screen(client)));
            return true;
        }
        return false;
    }

    private static void toggleFullbright(Minecraft client) {
        var xrayConfig = XRayConfigManager.get();
        xrayConfig.fullbright = !xrayConfig.fullbright;
        XRayConfigManager.save();
        if (client.player != null) Mc.overlay(client, Component.translatable(
            "message.betterxray.fullbright", Component.translatable(xrayConfig.fullbright ? "options.on" : "options.off")));
    }

    private static void toggleHighlight(Minecraft client) {
        var config = XRayConfigManager.get();
        config.highlightEnabled = !config.highlightEnabled;
        XRayConfigManager.save();
        if (client.player != null) Mc.overlay(client, Component.translatable(
            "message.betterxray.highlight", Component.translatable(config.highlightEnabled ? "options.on" : "options.off")));
    }

    private static void toggleTracers(Minecraft client) {
        var config = XRayConfigManager.get();
        config.tracersEnabled = !config.tracersEnabled;
        XRayConfigManager.save();
        if (client.player != null) Mc.overlay(client, Component.translatable(
            "message.betterxray.tracers", Component.translatable(config.tracersEnabled ? "options.on" : "options.off")));
    }

    /** Master switch for mob/player ESP; the per-group and per-mob choices are kept. */
    public static void toggleEsp(Minecraft client) {
        var config = XRayConfigManager.get();
        config.mobEspEnabled = !config.mobEspEnabled;
        XRayConfigManager.save();
        if (client.player != null) Mc.overlay(client, Component.translatable(
            "message.betterxray.esp", Component.translatable(config.mobEspEnabled ? "options.on" : "options.off")));
    }

    /** Cave-Find on/off; rebuilds the meshes like the X-Ray switch. */
    public static void toggleCaveFind(Minecraft client) {
        RendererCompatibility.refresh();
        var config = XRayConfigManager.get();
        config.caveFind = !config.caveFind;
        XRayRenderController.apply();
        if (client.player != null) Mc.overlay(client, XRayState.supported()
            ? Component.translatable("message.betterxray.cave_find", Component.translatable(config.caveFind ? "options.on" : "options.off"))
            : Component.literal("X-Ray unavailable with this renderer / shader pack"));
    }

    /** Master switch for the ESP tracers; which groups and mobs get one is kept. */
    public static void toggleEspTracers(Minecraft client) {
        var config = XRayConfigManager.get();
        config.mobEspTracersEnabled = !config.mobEspTracersEnabled;
        XRayConfigManager.save();
        if (client.player != null) Mc.overlay(client, Component.translatable(
            "message.betterxray.esp_tracers", Component.translatable(config.mobEspTracersEnabled ? "options.on" : "options.off")));
    }

    public static void addTargetedBlock(net.minecraft.client.Minecraft client) {
        if (client.player == null || client.level == null || client.hitResult == null
            || client.hitResult.getType() != HitResult.Type.BLOCK) {
            if (client.player != null) Mc.overlay(client, Component.translatable("message.betterxray.no_block_targeted"));
            return;
        }
        BlockHitResult hit = (BlockHitResult)client.hitResult;
        var block = client.level.getBlockState(hit.getBlockPos()).getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        var config = XRayConfigManager.get();
        Component blockName = block.getName();
        String presetName = config.getActivePreset().name;
        if (!config.getActiveVisibleBlocks().add(id)) {
            Mc.overlay(client, Component.translatable("message.betterxray.already_added", blockName, presetName));
            return;
        }
        XRayRenderController.apply();
        Mc.overlay(client, Component.translatable("message.betterxray.added", blockName, presetName));
    }

    //? if <1.21.11 {
    /*//? if fabric {
    private static void renderOverlays(WorldRenderContext context) {
        var frustum = context.frustum();
        if (frustum == null) return;
        //? if >=1.21 {
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        // The model-view stack already holds the camera rotation; vertices are camera-relative.
        PoseStack.Pose pose = new PoseStack().last();
        Matrix4f viewRotation = new Matrix4f(context.positionMatrix());
        //?} else {
        /^float partialTick = context.tickDelta();
        // The camera rotation (and view bobbing) is in the pose stack.
        PoseStack.Pose pose = context.matrixStack().last();
        Matrix4f viewRotation = new Matrix4f(pose.pose());
        ^///?}
        renderOverlays(context.camera(), frustum, partialTick, pose, context.projectionMatrix(), viewRotation);
    }
    //?}

    /// Highlight boxes, structure boxes, tracers, ESP boxes and player labels, drawn last and through terrain.
    private static void renderOverlays(Camera camera, Frustum frustum, float partialTick, PoseStack.Pose pose,
                                       Matrix4f projection, Matrix4f viewRotation) {
        var eye = camera.getPosition();
        // The NO_DEPTH_TEST shard does not switch off a depth test that is still enabled.
        RenderSystem.disableDepthTest();
        try {
            var tracerStart = TracerOrigin.compute(eye, projection, viewRotation);
            HighlightCache.render(eye, frustum, tracerStart, pose);
            StructureFinder.render(eye, frustum, tracerStart, pose);
            MobEspRenderer.render(camera, frustum, partialTick, pose, tracerStart);
        } finally {
            RenderSystem.enableDepthTest();
        }
    }
    *///?}
}
