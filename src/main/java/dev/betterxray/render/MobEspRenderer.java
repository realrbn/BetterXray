package dev.betterxray.render;

import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >=1.21.11 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import org.joml.Quaternionf;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
*///?}
//? if >=1.21.11 {
import net.minecraft.client.DeltaTracker;
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MobEspRenderer {
    private MobEspRenderer() {}

    private record TypeInfo(String id, MobEspGroup group) {}
    /** Registry ID and group per type, so extraction does not build ID strings every frame. */
    private static final Map<EntityType<?>, TypeInfo> TYPES = new ConcurrentHashMap<>();

    private static TypeInfo info(EntityType<?> type) {
        return TYPES.computeIfAbsent(type, t -> {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
            MobEspGroup group = isMobType(t) ? MobEspGroup.of(id, t.getCategory().name()) : MobEspGroup.OBJECTS;
            return new TypeInfo(id, group);
        });
    }

    /**
     * Types offered in the ESP picker: every entity type except markers, which only exist on the
     * server. Mobs and players go into their mob groups, everything else into Objects.
     */
    public static boolean isEspType(EntityType<?> type) {
        return !BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals("minecraft:marker");
    }

    /**
     * Living mob types: players, MISC mobs such as villagers and golems, and registered modded
     * mobs. Armor stands and mannequins have attributes but are objects, not mobs.
     */
    public static boolean isMobType(EntityType<?> type) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (id.equals("minecraft:player")) return true;
        return !id.equals("minecraft:armor_stand") && !id.equals("minecraft:mannequin")
            && (type.getCategory() != MobCategory.MISC || DefaultAttributes.hasSupplier(type));
    }

    public static MobEspGroup group(EntityType<?> type) { return info(type).group(); }

    public static String id(EntityType<?> type) { return info(type).id(); }

    /**
     * Effective settings of this entity (its own overrides on top of its group), or null when off.
     * Mobs, other players and objects qualify; the local player, spectators, the camera entity,
     * the vehicle the camera rides and anything outside the configured ESP range never do.
     */
    /** Render thread only, before 1.21.11: the entity just tested by the vanilla render loop has ESP. */
    public static boolean legacyEspCandidate;

    public static XRayConfig.ResolvedEsp settings(Entity entity) {
        var config = XRayConfigManager.get();
        if (!config.mobEspEnabled || entity.isRemoved()) return null;
        if (entity instanceof Player player && (player instanceof LocalPlayer || player.isSpectator())) return null;
        TypeInfo info = info(entity.getType());
        if (info.id().equals("minecraft:marker")) return null;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null) {
            if (camera == entity || entity.hasIndirectPassenger(camera)) return null;
            if (config.mobEspRangeBlocks > 0
                && !config.inEspRange(camera.getEyePosition().distanceToSqr(entity.getBoundingBox().getCenter())))
                return null;
        }
        if (entity instanceof ItemEntity item) return config.resolveItemEsp(itemId(item.getItem().getItem()));
        return config.resolveEsp(info.id(), info.group());
    }

    /** Registry ID per item, so the per-frame ESP lookup does not build ID strings. */
    private static final Map<Item, String> ITEM_IDS = new ConcurrentHashMap<>();

    public static String itemId(Item item) {
        return ITEM_IDS.computeIfAbsent(item, i -> BuiltInRegistries.ITEM.getKey(i).toString());
    }


    /** Opaque ARGB outline color when this entity gets an ESP outline, otherwise -1. */
    public static int outlineColor(Entity entity) {
        var setting = settings(entity);
        return setting != null && setting.style() == XRayConfig.EspStyle.OUTLINE ? setting.color() | 0xff000000 : -1;
    }

    /** True when the vanilla name tag is replaced by the ESP label (player with names on). */
    public static boolean replacesNameTag(Entity entity) {
        return entity instanceof Player && XRayConfigManager.get().playerEspName && settings(entity) != null;
    }

    /** One tracer of the current frame: interpolated center of the target and its ESP color. */
    public record Tracer(Vec3 target, int color) {}
    private static int lastTracers;
    /** Tracers drawn in the last frame (used by tests). */
    public static int lastDrawnTracers() { return lastTracers; }

    //? if >=1.21.11 {
    /** Called after the complete renderer extraction, including specialized mob renderers. */
    public static void extract(Entity entity, EntityRenderState state) {
        var data = (MobEspRenderState)state;
        data.betterxray$setEspBox(null, 0);
        data.betterxray$setPlayerLabel(null);
        var setting = settings(entity);
        if (setting == null) return;
        int color = setting.color() | 0xff000000;
        if (setting.style() == XRayConfig.EspStyle.BOX) {
            var box = entity.getBoundingBox().move(state.x - entity.getX(), state.y - entity.getY(), state.z - entity.getZ());
            if (state.passengerOffset != null) box = box.move(state.passengerOffset);
            data.betterxray$setEspBox(box.inflate(0.025), color);
        } else {
            // Vanilla's outline target is rendered without terrain depth testing.
            // Do not change the entity's glowing flag or its scoreboard team.
            state.outlineColor = color;
        }
        if (entity instanceof Player player) extractLabel(player, state, data);
    }

    private static void extractLabel(Player player, EntityRenderState state, MobEspRenderState data) {
        var config = XRayConfigManager.get();
        var health = config.playerEspHealth;
        if (!config.playerEspName && !health.numbers() && !health.bar()) return;
        Vec3 anchor = new Vec3(state.x, state.y + player.getBbHeight() + 0.5, state.z);
        if (state.passengerOffset != null) anchor = anchor.add(state.passengerOffset);
        String name = config.playerEspName ? player.getName().getString() : null;
        // Our label shows the name through walls; the vanilla tag would only duplicate it.
        if (name != null) state.nameTag = null;
        data.betterxray$setPlayerLabel(new PlayerLabel(anchor, name, player.getHealth(), player.getMaxHealth(),
            player.getAbsorptionAmount(), health.numbers(), health.bar()));
    }

    //? if >=1.21.11 {
    /**
     * Collects a tracer for every ESP target with tracers on that the client knows about, also
     * outside the view frustum: a target behind the camera gets a line toward the screen edge.
     * The ESP range and the ESP master switch apply through {@link #settings(Entity)}.
     */
    public static java.util.List<Tracer> collectTracers(DeltaTracker deltaTracker) {
        var config = XRayConfigManager.get();
        var level = Minecraft.getInstance().level;
        if (level == null || !config.mobEspEnabled || !config.mobEspTracersEnabled) return java.util.List.of();
        var tickRate = level.tickRateManager();
        var tracers = new java.util.ArrayList<Tracer>();
        for (Entity entity : level.entitiesForRendering()) {
            var setting = settings(entity);
            if (setting == null || !setting.tracer()) continue;
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(!tickRate.isEntityFrozen(entity));
            tracers.add(new Tracer(entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2.0, 0), setting.color() | 0xff000000));
        }
        return tracers;
    }

    /** Always-on-top lines from the screen center, like the block-highlight tracers. */
    public static void renderTracers(java.util.List<Tracer> tracers, Vec3 start) {
        lastTracers = tracers.size();
        for (Tracer tracer : tracers) Gizmos.line(start, tracer.target(), tracer.color(), 1.5f).setAlwaysOnTop();
    }
    //?}

    //? if >=26.1 {
    /** Render thread only: written while entities are extracted, read when the frame's gizmos are collected. */
    private static java.util.List<Tracer> frameTracers = java.util.List.of();

    public static void extractTracers(DeltaTracker deltaTracker) { frameTracers = collectTracers(deltaTracker); }

    public static void renderBoxes(LevelRenderState frame) {
        var camera = frame.cameraRenderState;
        renderBoxes(frame.entityRenderStates, camera.pos, camera.orientation, camera.cullFrustum);
        renderTracers(camera);
    }

    private static void renderTracers(CameraRenderState camera) {
        var tracers = frameTracers;
        lastTracers = tracers.size();
        if (!tracers.isEmpty()) renderTracers(tracers, HighlightCache.tracerStart(camera, Minecraft.getInstance()));
    }
    //?}

    /** Collects always-on-top gizmos; the caller opens the per-frame gizmo collection. */
    /** Type and position of each ESP target in the last rendered frame (used by tests). */
    public record RenderedTarget(EntityType<?> type, double x, double y, double z) {}
    private static java.util.List<RenderedTarget> lastRendered = java.util.List.of();
    public static java.util.List<RenderedTarget> lastRenderedTargets() { return lastRendered; }

    public static void renderBoxes(Iterable<? extends EntityRenderState> states, Vec3 cameraPos,
                                   Quaternionf orientation, Frustum frustum) {
        var rendered = new java.util.ArrayList<RenderedTarget>();
        for (var state : states) {
            var data = (MobEspRenderState)state;
            if (data.betterxray$espBox() != null || state.outlineColor != EntityRenderState.NO_OUTLINE
                || data.betterxray$playerLabel() != null)
                rendered.add(new RenderedTarget(state.entityType, state.x, state.y, state.z));
            var box = data.betterxray$espBox();
            if (box != null && frustum.isVisible(box))
                Gizmos.cuboid(box, GizmoStyle.stroke(data.betterxray$espColor(), 2.5f)).setAlwaysOnTop();
            var label = data.betterxray$playerLabel();
            if (label != null) renderLabel(label, cameraPos, orientation, frustum);
        }
        lastRendered = rendered;
    }

    /**
     * Billboarded label above a player, drawn through walls: health bar at the bottom, numbers
     * above it, name on top. Its size in world units grows with distance beyond 6 blocks so it
     * stays readable at the same on-screen size.
     */
    private static void renderLabel(PlayerLabel label, Vec3 cameraPos, Quaternionf orientation, Frustum frustum) {
        if (!frustum.isVisible(new AABB(label.anchor(), label.anchor()).inflate(1.0))) return;
        double distance = cameraPos.distanceTo(label.anchor());
        float px = 0.025f * (float)Math.max(1.0, distance / 6.0);
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0)).mul(px);
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0)).mul(px);
        Vector3f toward = orientation.transform(new Vector3f(0, 0, 1)).mul(px * 0.5f);
        var font = Minecraft.getInstance().font;
        float y = 0;
        if (label.showBar()) {
            float half = 16, fraction = label.maxHealth() <= 0 ? 0 : Mth.clamp(label.health() / label.maxHealth(), 0f, 1f);
            // Translucent quads are depth-sorted, so the frame, filled and empty parts never overlap.
            int frame = 0xc0000000;
            float split = -half + 2 * half * fraction;
            quad(label.anchor(), right, up, null, -half - 1, y, half + 1, y + 1, frame);
            quad(label.anchor(), right, up, null, -half - 1, y + 3, half + 1, y + 4, frame);
            quad(label.anchor(), right, up, null, -half - 1, y + 1, -half, y + 3, frame);
            quad(label.anchor(), right, up, null, half, y + 1, half + 1, y + 3, frame);
            if (fraction > 0) quad(label.anchor(), right, up, null, -half, y + 1, split, y + 3,
                healthColor(label.health(), label.maxHealth()));
            if (fraction < 1) quad(label.anchor(), right, up, null, split, y + 1, half, y + 3, 0x90000000);
            y += 5;
        }
        if (label.showNumbers()) {
            text(label.anchor(), right, up, toward, font, healthText(label.health(), label.maxHealth(), label.absorption()),
                y, healthColor(label.health(), label.maxHealth()), px);
            y += 10;
        }
        if (label.name() != null) text(label.anchor(), right, up, toward, font, label.name(), y, 0xffffffff, px);
    }

    /** One text line whose bottom edge is {@code bottom} pixels above the anchor, on a dark background. */
    private static void text(Vec3 anchor, Vector3f right, Vector3f up, Vector3f toward, net.minecraft.client.gui.Font font,
                             String text, float bottom, int color, float px) {
        float half = font.width(text) / 2.0f + 1;
        quad(anchor, right, up, null, -half, bottom, half, bottom + 9, 0x90000000);
        Vec3 top = at(anchor, right, up, toward, 0, bottom + 9);
        Gizmos.billboardText(text, top, TextGizmo.Style.forColorAndCentered(color).withScale(px * 16)).setAlwaysOnTop();
    }

    private static void quad(Vec3 anchor, Vector3f right, Vector3f up, Vector3f toward,
                             float x0, float y0, float x1, float y1, int color) {
        Gizmos.rect(at(anchor, right, up, toward, x0, y0), at(anchor, right, up, toward, x1, y0),
            at(anchor, right, up, toward, x1, y1), at(anchor, right, up, toward, x0, y1), GizmoStyle.fill(color)).setAlwaysOnTop();
    }

    /** Anchor plus {@code x}/{@code y} label pixels along the camera's right/up axes (optionally nudged toward the camera). */
    private static Vec3 at(Vec3 anchor, Vector3f right, Vector3f up, Vector3f toward, float x, float y) {
        double dx = right.x * x + up.x * y, dy = right.y * x + up.y * y, dz = right.z * x + up.z * y;
        if (toward != null) { dx += toward.x; dy += toward.y; dz += toward.z; }
        return anchor.add(dx, dy, dz);
    }
    //?} else {
    /*private static int lastBoxes, lastLabels;
    /^* Boxes / player labels drawn in the last frame (used by tests). ^/
    public static int lastDrawnBoxes() { return lastBoxes; }
    public static int lastDrawnLabels() { return lastLabels; }

    /^*
     * Draws ESP boxes and player labels after the level, without depth test. Outlines use the
     * vanilla glowing pass (see MobEspOutlineMixin). Vertices are camera-relative; {@code base}
     * is the pose that maps them to view space.
     ^/
    public static void render(Camera camera, Frustum frustum, float partialTick, PoseStack.Pose base, Vec3 tracerStart) {
        Minecraft client = Minecraft.getInstance();
        var level = client.level;
        lastBoxes = 0;
        lastLabels = 0;
        lastTracers = 0;
        var config = XRayConfigManager.get();
        if (level == null || !config.mobEspEnabled) return;
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        VertexConsumer lines = null;
        java.util.List<PlayerLabel> labels = new java.util.ArrayList<>();
        java.util.List<Tracer> tracers = new java.util.ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            var setting = settings(entity);
            if (setting == null) continue;
            Vec3 lerped = entity.getPosition(partialTick);
            Vec3 shift = lerped.subtract(entity.position());
            // Tracers also reach targets outside the frustum (a line toward the screen edge).
            if (config.mobEspTracersEnabled && setting.tracer())
                tracers.add(new Tracer(lerped.add(0, entity.getBbHeight() / 2.0, 0), setting.color() | 0xff000000));
            if (setting.style() == XRayConfig.EspStyle.BOX) {
                AABB box = entity.getBoundingBox().move(shift).inflate(0.025);
                if (frustum.isVisible(box)) {
                    if (lines == null) lines = buffers.getBuffer(XRayRenderTypes.ESP_BOXES);
                    int color = setting.color() | 0xff000000;
                    LineBuffers.box(lines, base, box.move(-eye.x, -eye.y, -eye.z),
                        (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
                    lastBoxes++;
                }
            }
            if (entity instanceof Player player) {
                PlayerLabel label = label(player, lerped);
                if (label != null && frustum.isVisible(new AABB(label.anchor(), label.anchor()).inflate(1.0))) {
                    labels.add(label);
                    lastLabels++;
                }
            }
        }
        if (lines != null) buffers.endBatch(XRayRenderTypes.ESP_BOXES);
        if (!tracers.isEmpty()) {
            VertexConsumer tracerLines = buffers.getBuffer(XRayRenderTypes.TRACERS);
            Vec3 from = tracerStart.subtract(eye);
            for (Tracer tracer : tracers) {
                int color = tracer.color();
                LineBuffers.line(tracerLines, base, from, tracer.target().subtract(eye),
                    (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
            }
            buffers.endBatch(XRayRenderTypes.TRACERS);
            lastTracers = tracers.size();
        }
        for (PlayerLabel label : labels) renderLabel(label, camera, buffers, base);
    }

    private static PlayerLabel label(Player player, Vec3 lerped) {
        var config = XRayConfigManager.get();
        var health = config.playerEspHealth;
        if (!config.playerEspName && !health.numbers() && !health.bar()) return null;
        Vec3 anchor = lerped.add(0, player.getBbHeight() + 0.5, 0);
        String name = config.playerEspName ? player.getName().getString() : null;
        return new PlayerLabel(anchor, name, player.getHealth(), player.getMaxHealth(),
            player.getAbsorptionAmount(), health.numbers(), health.bar());
    }

    /^*
     * Billboarded label above a player, drawn through walls: health bar at the bottom, numbers
     * above it, name on top. One label pixel is 0.025 blocks up to 6 blocks away and grows
     * linearly beyond, so the label keeps its on-screen size.
     ^/
    private static void renderLabel(PlayerLabel label, Camera camera, MultiBufferSource.BufferSource buffers, PoseStack.Pose base) {
        Vec3 eye = camera.getPosition();
        double distance = eye.distanceTo(label.anchor());
        float px = 0.025f * (float)Math.max(1.0, distance / 6.0);
        PoseStack pose = new PoseStack();
        pose.last().pose().set(base.pose());
        pose.last().normal().set(base.normal());
        Vec3 rel = label.anchor().subtract(eye);
        pose.translate(rel.x, rel.y, rel.z);
        pose.mulPose(camera.rotation());
        pose.scale(px, px, px);
        Matrix4f matrix = pose.last().pose();
        Font font = Minecraft.getInstance().font;

        VertexConsumer quads = buffers.getBuffer(XRayRenderTypes.LABEL_QUADS);
        float y = 0;
        float numbersY = -1, nameY = -1;
        String numbers = label.showNumbers() ? healthText(label.health(), label.maxHealth(), label.absorption()) : null;
        if (label.showBar()) {
            float half = 16, fraction = label.maxHealth() <= 0 ? 0 : Mth.clamp(label.health() / label.maxHealth(), 0f, 1f);
            int frame = 0xc0000000;
            float split = -half + 2 * half * fraction;
            LineBuffers.quad(quads, matrix, -half - 1, y, half + 1, y + 1, frame);
            LineBuffers.quad(quads, matrix, -half - 1, y + 3, half + 1, y + 4, frame);
            LineBuffers.quad(quads, matrix, -half - 1, y + 1, -half, y + 3, frame);
            LineBuffers.quad(quads, matrix, half, y + 1, half + 1, y + 3, frame);
            if (fraction > 0) LineBuffers.quad(quads, matrix, -half, y + 1, split, y + 3, healthColor(label.health(), label.maxHealth()));
            if (fraction < 1) LineBuffers.quad(quads, matrix, split, y + 1, half, y + 3, 0x90000000);
            y += 5;
        }
        if (numbers != null) {
            float half = font.width(numbers) / 2.0f + 1;
            LineBuffers.quad(quads, matrix, -half, y, half, y + 9, 0x90000000);
            numbersY = y;
            y += 10;
        }
        if (label.name() != null) {
            float half = font.width(label.name()) / 2.0f + 1;
            LineBuffers.quad(quads, matrix, -half, y, half, y + 9, 0x90000000);
            nameY = y;
        }
        buffers.endBatch(XRayRenderTypes.LABEL_QUADS);

        // Text runs downward on screen, so draw it in a pose with y flipped back.
        pose.scale(1, -1, 1);
        Matrix4f textMatrix = pose.last().pose();
        if (numbers != null) text(font, numbers, numbersY, healthColor(label.health(), label.maxHealth()), textMatrix, buffers);
        if (label.name() != null) text(font, label.name(), nameY, 0xffffffff, textMatrix, buffers);
        buffers.endBatch();
    }

    /^* One text line whose bottom edge is {@code bottom} label pixels above the anchor. ^/
    private static void text(Font font, String text, float bottom, int color, Matrix4f matrix, MultiBufferSource buffers) {
        float x = -font.width(text) / 2.0f;
        float top = -(bottom + 9) + 1;
        font.drawInBatch(text, x, top, color, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
    }
    *///?}

    /** Health color: green above half, yellow above a quarter, red below. */
    public static int healthColor(float health, float maxHealth) {
        float fraction = maxHealth <= 0 ? 0 : health / maxHealth;
        return fraction > 0.5f ? 0xff55ff55 : fraction > 0.25f ? 0xffffff55 : 0xffff5555;
    }

    /** "17.5/20 ❤", with absorption as "+4". */
    public static String healthText(float health, float maxHealth, float absorption) {
        String text = number(health) + "/" + number(maxHealth) + " \u2764";
        return absorption > 0 ? text + " +" + number(absorption) : text;
    }

    private static String number(float value) {
        float rounded = Math.round(value * 2) / 2.0f;
        return rounded == (int)rounded ? Integer.toString((int)rounded) : String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }
}
