package dev.betterxray.render;

import dev.betterxray.config.BlockSelection;
import dev.betterxray.XRayClient;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
//? if >=26.1 {
import net.minecraft.world.level.BlockAndLightGetter;
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
*///?}
import java.util.Set;

public final class XRayState {
    /** {@code enabled}: X-Ray switch; {@code caveFind}: Cave-Find switch. Either one activates the X-Ray view. */
    public record Snapshot(boolean enabled, boolean caveFind, float opacity, boolean water, boolean lava, Set<Block> visible,
                           boolean wireframe, float wireframeOpacity) {
        public boolean selected(BlockState state) { return visible.contains(state.getBlock()); }
        public boolean active() { return enabled || caveFind; }
    }
    /**
     * The Wireframe mode exists on this target (every loader and version since 1.6.0): a line
     * polygon mode on the translucent terrain pipeline copy (1.21.11+) or on the GL state of the
     * translucent terrain layer (before).
     */
    public static final boolean WIREFRAME_TARGET = true;
    private static volatile Snapshot current = new Snapshot(false, false, .15f, true, true, Set.of(), false, 1f);
    private static volatile boolean wireframeUnavailable;
    private static volatile boolean supported = true;
    private static volatile boolean faintUnsupported;
    private static volatile boolean faintEncoded;
    public static Snapshot get() { return current; }

    /**
     * The terrain meshes of both snapshots are the same: both have the X-Ray view off, or every
     * value the meshing reads is equal. The preset only counts while the X-Ray switch is on, the
     * opacity only outside the Wireframe mode (in it, the line opacity is meshed instead).
     */
    public static boolean meshEquals(Snapshot a, Snapshot b) {
        if (!a.active() && !b.active()) return true;
        if (a.enabled() != b.enabled() || a.caveFind() != b.caveFind() || a.water() != b.water() || a.lava() != b.lava()
            || a.wireframe() != b.wireframe()) return false;
        if (a.enabled() && !a.visible().equals(b.visible())) return false;
        boolean lines = a.wireframe() && !wireframeUnavailable && !faintUnsupported;
        // Faint block-entity models read the opacity while drawing, not from the meshes.
        if (lines) return Math.min(a.wireframeOpacity(), MAX_LINE_OPACITY) == Math.min(b.wireframeOpacity(), MAX_LINE_OPACITY);
        return a.opacity() == b.opacity();
    }

    /** Only the blocks of the preset differ, while the X-Ray switch is on in both snapshots. */
    public static boolean onlyPresetChanged(Snapshot a, Snapshot b) {
        return a.enabled() && b.enabled() && !a.visible().equals(b.visible())
            && meshEquals(a, new Snapshot(b.enabled(), b.caveFind(), b.opacity(), b.water(), b.lava(), a.visible(),
                b.wireframe(), b.wireframeOpacity()));
    }
    /** True while the X-Ray view is meshed: X-Ray, Cave-Find or both are on. */
    public static boolean enabled() { return supported && current.active(); }
    public static boolean caveFind() { return supported && current.caveFind(); }
    /**
     * Block of the active preset while the X-Ray switch is on. Cave-Find alone only shows caves:
     * preset blocks are then ordinary terrain (a cave wall, faint or hidden like the rest).
     */
    public static boolean selected(BlockState state) { return supported && current.enabled() && current.selected(state); }
    /**
     * Selected blocks keep the faces the renderer would cull (ores enclosed in stone stay complete),
     * except toward another selected block: its opaque model hides the face, so the renderer's own
     * culling decides as usual. Large veins and common blocks in a preset mesh far fewer faces.
     */
    public static boolean keepCulledFace(BlockGetter level, BlockPos pos, Direction face) {
        return face == null || !selected(level.getBlockState(pos.relative(face)));
    }
    /** Opacity 0%: unselected terrain is not emitted at all instead of drawn fully transparent. */
    public static float opacity() { return current.opacity(); }
    public static boolean hideTerrain() { return enabled() && (faintUnsupported || (current.opacity() <= 0f && !wireframe())); }
    /**
     * Faint block-entity models are not drawn: they follow the terrain opacity in the Wireframe
     * mode too, where the terrain itself is always meshed.
     */
    public static boolean hideFaintModels() { return enabled() && (current.opacity() <= 0f || faintUnsupported); }
    /**
     * Wireframe mode: the unselected terrain of the X-Ray view is drawn as lines (the translucent
     * terrain pipeline with a line polygon mode, XRayPipelines) with their own opacity. Off while
     * the device has no line polygon mode or the shader pack cannot draw faint terrain.
     */
    public static boolean wireframe() { return enabled() && current.wireframe() && !wireframeUnavailable && !faintUnsupported; }
    /** The device cannot draw polygons as lines (RendererCompatibility). */
    public static void setWireframeUnavailable(boolean value) { wireframeUnavailable = value; }
    public static boolean wireframeUnavailable() { return wireframeUnavailable; }
    /** Opacity baked into unselected terrain quads: the line opacity in the Wireframe mode. */
    public static float terrainOpacity() { return wireframe() ? Math.min(current.wireframeOpacity(), MAX_LINE_OPACITY) : current.opacity(); }
    /**
     * Lines are never meshed fully opaque (alpha 254 of 255 at most): Sodium 0.8 moves translucent
     * quads with an opaque sprite and opaque vertices back to the solid pass, where they would be filled.
     */
    private static final float MAX_LINE_OPACITY = 254 / 255f;
    public static int terrainAlpha(int argb) {
        return (argb & 0x00ffffff) | (Math.round((argb >>> 24) * terrainOpacity()) << 24);
    }
    /** The active shader pack cannot draw faint terrain (IrisCompat): it is hidden as at 0 %. */
    public static boolean faintUnsupported() { return faintUnsupported; }
    public static void setFaintUnsupported(boolean value) { faintUnsupported = value; }
    /** Experimental: faint terrain under such a pack is drawn through the shader opacity patch instead. */
    public static boolean faintEncoded() { return faintEncoded; }
    public static void setFaintEncoded(boolean value) { faintEncoded = value; }
    public static void setSupported(boolean value) { supported = value; }
    public static boolean supported() { return supported; }
    /**
     * "Hide Mobs" while the X-Ray view is on. Read from the config (not the snapshot) so that
     * switching it needs no chunk rebuild; callers keep ESP targets visible themselves. In the
     * "behind walls" mode a mob the camera can see directly stays visible (MobOcclusion).
     */
    public static boolean hideMob(Entity entity) {
        if (!enabled() || !(entity instanceof Mob)) return false;
        return switch (XRayConfigManager.get().hideMobs) {
            case OFF -> false;
            case WALLS -> MobOcclusion.occluded(entity);
            case ALL -> true;
        };
    }
    public static boolean hideFluid(FluidState fluid) {
        return enabled() && ((fluid.is(FluidTags.WATER) && !current.water()) || (fluid.is(FluidTags.LAVA) && !current.lava()));
    }
    public static int alpha(int argb) {
        return (argb & 0x00ffffff) | (Math.round((argb >>> 24) * current.opacity()) << 24);
    }

    /**
     * Cave-Find: sky light up to which open space can be a cave. Above it (open sky, shade under
     * trees and overhangs) nothing is a cave; between 1 and this value (cave entrances, rooms with
     * a door) only space with a solid roof above counts.
     */
    public static final int CAVE_SKY_LIGHT = 7;

    /**
     * Cave-Find: {@code face} of the block at {@code pos} borders cave space ({@link #caveSpace}) that
     * is not the player's own air space ({@link CavePocket}). Such faces stay opaque and fully lit
     * while the rest of the unselected terrain follows the X-Ray opacity. Meshing threads only;
     * reads the section copy (the meshed section and its neighbors, never further).
     */
    //? if >=26.1 {
    public static boolean caveFace(BlockAndLightGetter level, BlockPos pos, Direction face) {
    //?} else {
    /*public static boolean caveFace(BlockAndTintGetter level, BlockPos pos, Direction face) {
    *///?}
        if (face == null || !caveFind()) return false;
        BlockPos neighbor = pos.relative(face);
        // Top of the section above the meshed one: the section copy ends there.
        int maxY = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(pos.getY()) + 1) + 15;
        return caveSpace(level, neighbor, maxY) && !CavePocket.contains(neighbor);
    }

    /**
     * Cave-Find: {@code pos} is space inside a cave. Its block does not fill its cell (air, plants,
     * glow lichen, vines, dripstone, carpets, snow layers, rails, torches, fluids; walls covered by
     * them still count) and it is dark: no sky light, or a little sky light under a solid roof.
     * Fluids additionally need a roof or dark air above them within reach, so the floor of deep
     * oceans and lakes under the open sky is no cave while underground lakes and lava pools are.
     * Dimensions without sky light (Nether, End) count all open space as underground. Upward scans
     * stop at {@code maxY}.
     */
    //? if >=26.1 {
    public static boolean caveSpace(BlockAndLightGetter level, BlockPos pos, int maxY) {
    //?} else {
    /*public static boolean caveSpace(BlockAndTintGetter level, BlockPos pos, int maxY) {
    *///?}
        BlockState state = level.getBlockState(pos);
        if (!openForCave(level, pos, state)) return false;
        int sky = level.getBrightness(LightLayer.SKY, pos);
        if (sky > CAVE_SKY_LIGHT) return false;
        if (sky == 0 && state.getFluidState().isEmpty()) return true;
        return covered(level, pos, maxY);
    }

    /** The block does not fill its cell: a wall face next to it stays visible. */
    public static boolean openForCave(BlockGetter level, BlockPos pos, BlockState state) {
        return state.isAir() || !state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Above {@code pos}, through fluids and open blocks, comes a solid roof or dark air (no sky light,
     * i.e. sealed from the sky) before the open sky or {@code maxY}.
     */
    //? if >=26.1 {
    private static boolean covered(BlockAndLightGetter level, BlockPos pos, int maxY) {
    //?} else {
    /*private static boolean covered(BlockAndTintGetter level, BlockPos pos, int maxY) {
    *///?}
        BlockPos.MutableBlockPos above = pos.mutable();
        for (int y = pos.getY() + 1; y <= maxY; y++) {
            above.setY(y);
            BlockState state = level.getBlockState(above);
            boolean fluid = !state.getFluidState().isEmpty();
            if (!fluid && state.canOcclude() && state.isCollisionShapeFullBlock(level, above)) return true;
            int sky = level.getBrightness(LightLayer.SKY, above);
            if (sky >= 15) return false;
            if (!fluid && sky == 0) return true;
        }
        return false;
    }

    /**
     * Cave-Find for renderers that choose the chunk layer per block (vanilla block renderer and
     * Sodium 0.5 before 1.21.11): any face of the block borders underground air.
     */
    //? if >=26.1 {
    public static boolean caveBlock(BlockAndLightGetter level, BlockPos pos) {
    //?} else {
    /*public static boolean caveBlock(BlockAndTintGetter level, BlockPos pos) {
    *///?}
        if (!caveFind()) return false;
        for (Direction face : Direction.values()) if (caveFace(level, pos, face)) return true;
        return false;
    }

    public static void publish(XRayConfig config) {
        config.sanitize();
        Set<Block> blocks = BlockSelection.resolve(config.getActiveVisibleBlocks(),
            id -> XRayClient.LOGGER.warn("[XRay] Ignoring unknown block ID: {}", id));
        current = new Snapshot(config.enabled, config.caveFind, config.terrainOpacity,
            config.showWater, config.showLava, Set.copyOf(blocks),
            WIREFRAME_TARGET && config.wireframe, config.wireframeOpacity);
    }

    /**
     * True when the tags of the active preset now cover other blocks than the published snapshot,
     * for example after joining a world (tags bound) or a data pack reload.
     */
    public static boolean tagsChanged(XRayConfig config) {
        var entries = config.getActiveVisibleBlocks();
        return BlockSelection.hasTags(entries) && !BlockSelection.resolve(entries, null).equals(current.visible());
    }
}
