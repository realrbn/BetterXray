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
import net.minecraft.world.level.LightLayer;
//? if >=26.1 {
import net.minecraft.world.level.BlockAndLightGetter;
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
*///?}
import java.util.Set;

public final class XRayState {
    /** {@code enabled}: X-Ray switch; {@code caveFind}: Cave-Find switch. Either one activates the X-Ray view. */
    public record Snapshot(boolean enabled, boolean caveFind, float opacity, boolean water, boolean lava, Set<Block> visible) {
        public boolean selected(BlockState state) { return visible.contains(state.getBlock()); }
        public boolean active() { return enabled || caveFind; }
    }
    private static volatile Snapshot current = new Snapshot(false, false, .15f, true, true, Set.of());
    private static volatile boolean supported = true;
    private static volatile boolean faintUnsupported;
    private static volatile boolean faintEncoded;
    public static Snapshot get() { return current; }
    /** True while the X-Ray view is meshed: X-Ray, Cave-Find or both are on. */
    public static boolean enabled() { return supported && current.active(); }
    public static boolean caveFind() { return supported && current.caveFind(); }
    /**
     * Block of the active preset while the X-Ray switch is on. Cave-Find alone only shows caves:
     * preset blocks are then ordinary terrain (a cave wall, faint or hidden like the rest).
     */
    public static boolean selected(BlockState state) { return supported && current.enabled() && current.selected(state); }
    /** Opacity 0%: unselected terrain is not emitted at all instead of drawn fully transparent. */
    public static float opacity() { return current.opacity(); }
    public static boolean hideTerrain() { return enabled() && (current.opacity() <= 0f || faintUnsupported); }
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
     * Cave-Find: {@code face} of the block at {@code pos} borders underground air, i.e. an air
     * block without sky light. Such faces stay opaque and fully lit while the rest of the
     * unselected terrain follows the X-Ray opacity. Meshing threads only; reads the section copy.
     * Dimensions without sky light (Nether, End) count all air as underground.
     */
    //? if >=26.1 {
    public static boolean caveFace(BlockAndLightGetter level, BlockPos pos, Direction face) {
    //?} else {
    /*public static boolean caveFace(BlockAndTintGetter level, BlockPos pos, Direction face) {
    *///?}
        if (face == null || !caveFind()) return false;
        BlockPos neighbor = pos.relative(face);
        return level.getBlockState(neighbor).isAir() && level.getBrightness(LightLayer.SKY, neighbor) == 0;
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
            config.showWater, config.showLava, Set.copyOf(blocks));
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
