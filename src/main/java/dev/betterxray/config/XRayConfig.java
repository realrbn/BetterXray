package dev.betterxray.config;

import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class XRayConfig {
    public static final int PRESET_COUNT = 3;
    public static final int MAX_PRESET_NAME_LENGTH = 32;

    public boolean enabled = false;
    /**
     * Cave-Find (hotkey U, 26.x): block faces next to underground air stay opaque, the rest of the
     * terrain follows {@link #terrainOpacity}. Shows the X-Ray view on its own, also with X-Ray off.
     */
    public boolean caveFind = false;
    /**
     * Which mobs are not drawn while X-Ray or Cave-Find is on: none, only those behind blocks, or
     * all of them. Mobs with active ESP stay visible.
     */
    public HideMobs hideMobs = HideMobs.OFF;
    public boolean fullbright = false;
    public float terrainOpacity = 0.15f;
    public boolean showWater = true;
    public boolean showLava = true;
    /** Experimental (Fabric 26.3+, Iris): faint terrain under separateAo shader packs via a shader patch. */
    public boolean shaderOpacityPatch = false;
    /** Checks once per session, in the first singleplayer world, whether a newer version is listed. */
    public boolean updateCheckEnabled = true;
    public boolean highlightEnabled = false;
    public boolean tracersEnabled = false;
    /**
     * Adjacent highlighted blocks of the same kind form a vein with one box and one tracer; off
     * draws every block on its own.
     */
    public boolean groupVeins = true;
    /** Global highlight color; blocks without an entry in {@link #highlightBlockColors} use it. */
    public int highlightColor = 0xffff00ff;
    /** Per-block highlight colors, keyed by block registry ID; overrides {@link #highlightColor}. */
    public Map<String, Integer> highlightBlockColors = new LinkedHashMap<>();
    /** Smallest selectable highlight/ESP range and slider step, in blocks. */
    public static final int RANGE_STEP_BLOCKS = 8;
    /** Largest finite ESP range; 32 chunks, the maximum Minecraft render distance. */
    public static final int MAX_RANGE_BLOCKS = 512;

    /** Highlight and tracer range in blocks around the camera; zero follows the render distance. */
    public int highlightRangeBlocks = 0;
    /** Read only while migrating the 1.0.0–1.0.6 chunk-based highlight range. */
    public Integer highlightRangeChunks;
    public Set<String> highlightBlocks = new LinkedHashSet<>();
    /** Structure Finder (singleplayer): boxes around the selected structures. */
    public boolean structureFinderEnabled = false;
    public boolean structureTracersEnabled = false;
    /** Structures the finder shows, keyed by structure registry ID (worldgen, read from the integrated server). */
    public Set<String> structures = new LinkedHashSet<>();
    /**
     * Per-mob ESP overrides, keyed by entity registry ID. Independent of X-Ray and block
     * highlighting. A {@code null} field inherits the value of the mob's {@link MobEspGroup}.
     */
    public Map<String, MobEspSettings> mobEsp = new LinkedHashMap<>();
    /**
     * Per-item ESP overrides for dropped items, keyed by item registry ID. A
     * {@code null} field follows the dropped-item entity ({@link #ITEM_ENTITY}) and through it
     * the Objects group, just as a mob follows its group.
     */
    public Map<String, MobEspSettings> itemEsp = new LinkedHashMap<>();
    /** Entity type of dropped items; its ESP settings are the defaults of every item. */
    public static final String ITEM_ENTITY = "minecraft:item";
    /** Master switch for all mob/player ESP (hotkey J); group and mob settings stay untouched. */
    public boolean mobEspEnabled = true;
    /**
     * Master switch for the tracers of ESP targets (hotkey K, 26.x); which groups and mobs get one
     * is kept in their settings. Independent of the block-highlight tracers ({@link #tracersEnabled}).
     */
    public boolean mobEspTracersEnabled = true;
    /** Player ESP: show the player's name above the head (replaces the vanilla name tag). */
    public boolean playerEspName = true;
    /** Player ESP: how the player's health is shown above the head. */
    public HealthDisplay playerEspHealth = HealthDisplay.BAR;
    /** ESP range in blocks around the camera for all groups and mobs; zero means unlimited. */
    public int mobEspRangeBlocks = 0;
    /** ESP defaults per {@link MobEspGroup}, keyed by {@link MobEspGroup#id}. */
    public Map<String, MobEspGroupSettings> mobEspGroups = createDefaultEspGroups();
    public int activePreset = 0;
    public List<XRayPreset> presets = createDefaultPresets();

    /** Read only while migrating the pre-preset configuration format. */
    public Set<String> visibleBlocks;

    public static XRayPreset createDefaultPreset(int index) {
        return switch (index) {
            case 0 -> new XRayPreset("Preset 1", defaultPreset1Blocks());
            case 1 -> new XRayPreset("Preset 2", new LinkedHashSet<>());
            case 2 -> new XRayPreset("Preset 3", new LinkedHashSet<>());
            default -> throw new IllegalArgumentException("Invalid preset index: " + index);
        };
    }

    public static List<XRayPreset> createDefaultPresets() {
        List<XRayPreset> result = new ArrayList<>(PRESET_COUNT);
        for (int i = 0; i < PRESET_COUNT; i++) result.add(createDefaultPreset(i));
        return result;
    }

    public static Set<String> defaultPreset1Blocks() {
        Set<String> ids = new LinkedHashSet<>();
        for (String ore : new String[]{"coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald"}) {
            ids.add("minecraft:" + ore + "_ore");
            ids.add("minecraft:deepslate_" + ore + "_ore");
        }
        for (String block : new String[]{
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris", "chest", "trapped_chest",
            "ender_chest", "barrel", "spawner",
            //? if >=1.20.5
            "trial_spawner", "vault"
        }) ids.add("minecraft:" + block);
        return ids;
    }

    public static Map<String, MobEspGroupSettings> createDefaultEspGroups() {
        Map<String, MobEspGroupSettings> groups = new LinkedHashMap<>();
        for (MobEspGroup group : MobEspGroup.values()) groups.put(group.id, new MobEspGroupSettings(group));
        return groups;
    }

    public MobEspGroupSettings espGroup(MobEspGroup group) {
        if (mobEspGroups == null) mobEspGroups = createDefaultEspGroups();
        return mobEspGroups.computeIfAbsent(group.id, ignored -> new MobEspGroupSettings(group));
    }

    /** Effective ESP of one mob: its own overrides on top of its group, or {@code null} when off. */
    public ResolvedEsp resolveEsp(String entityId, MobEspGroup group) {
        MobEspGroupSettings defaults = espGroup(group);
        MobEspSettings own = mobEsp == null ? null : mobEsp.get(entityId);
        boolean enabled = own != null && own.enabled != null ? own.enabled : defaults.enabled;
        if (!enabled) return null;
        EspStyle style = own != null && own.style != null ? own.style : defaults.style;
        int color = own != null && own.color != null ? own.color : defaults.color;
        boolean tracer = own != null && own.tracer != null ? own.tracer : defaults.tracer;
        return new ResolvedEsp(style == null ? EspStyle.OUTLINE : style, color | 0xff000000, tracer);
    }

    public boolean espEnabled(String entityId, MobEspGroup group) {
        MobEspSettings own = mobEsp.get(entityId);
        return own != null && own.enabled != null ? own.enabled : espGroup(group).enabled;
    }

    public EspStyle espStyle(String entityId, MobEspGroup group) {
        MobEspSettings own = mobEsp.get(entityId);
        return own != null && own.style != null ? own.style : espGroup(group).style;
    }

    public boolean espTracer(String entityId, MobEspGroup group) {
        MobEspSettings own = mobEsp.get(entityId);
        return own != null && own.tracer != null ? own.tracer : espGroup(group).tracer;
    }

    public int espColor(String entityId, MobEspGroup group) {
        MobEspSettings own = mobEsp.get(entityId);
        return (own != null && own.color != null ? own.color : espGroup(group).color) | 0xff000000;
    }

    /** Effective ESP of a dropped item: its own overrides on top of the item entity, or {@code null} when off. */
    public ResolvedEsp resolveItemEsp(String itemId) {
        if (!itemEspEnabled(itemId)) return null;
        return new ResolvedEsp(itemEspStyle(itemId), itemEspColor(itemId), itemEspTracer(itemId));
    }

    public boolean itemEspEnabled(String itemId) {
        MobEspSettings own = itemEsp.get(itemId);
        return own != null && own.enabled != null ? own.enabled : espEnabled(ITEM_ENTITY, MobEspGroup.OBJECTS);
    }

    public EspStyle itemEspStyle(String itemId) {
        MobEspSettings own = itemEsp.get(itemId);
        EspStyle style = own != null && own.style != null ? own.style : espStyle(ITEM_ENTITY, MobEspGroup.OBJECTS);
        return style == null ? EspStyle.OUTLINE : style;
    }

    public boolean itemEspTracer(String itemId) {
        MobEspSettings own = itemEsp.get(itemId);
        return own != null && own.tracer != null ? own.tracer : espTracer(ITEM_ENTITY, MobEspGroup.OBJECTS);
    }

    public int itemEspColor(String itemId) {
        MobEspSettings own = itemEsp.get(itemId);
        return own != null && own.color != null ? own.color | 0xff000000 : espColor(ITEM_ENTITY, MobEspGroup.OBJECTS);
    }

    /** Removes every per-mob override of the given group's mobs; returns how many mobs changed. */
    public int clearEspOverrides(java.util.function.Predicate<String> inGroup) {
        int before = mobEsp.size();
        mobEsp.entrySet().removeIf(entry -> inGroup.test(entry.getKey()));
        return before - mobEsp.size();
    }

    /**
     * Converts the first 1.0.6 development format, where every mob entry carried a mandatory
     * enabled flag, style and color and there were no groups. Enabled mobs keep all three values
     * as overrides; disabled mobs now follow their group and only keep a non-default look.
     */
    public void migrateLegacyMobEsp() {
        mobEspGroups = createDefaultEspGroups();
        if (mobEsp == null) return;
        mobEsp.values().removeIf(java.util.Objects::isNull);
        for (MobEspSettings setting : mobEsp.values()) {
            if (Boolean.TRUE.equals(setting.enabled)) {
                if (setting.style == null) setting.style = EspStyle.OUTLINE;
                if (setting.color == null) setting.color = LEGACY_ESP_COLOR;
                continue;
            }
            setting.enabled = null;
            if (setting.style == EspStyle.OUTLINE && (setting.color == null || (setting.color | 0xff000000) == LEGACY_ESP_COLOR)) {
                setting.style = null;
                setting.color = null;
            }
        }
        mobEsp.values().removeIf(MobEspSettings::isEmpty);
    }

    /** Effective highlight color of one block: its own color, otherwise the global one. */
    public int highlightColor(String blockId) {
        Integer own = highlightBlockColors.get(blockId);
        return (own != null ? own : highlightColor) | 0xff000000;
    }

    public XRayPreset getActivePreset() {
        if (activePreset < 0 || activePreset >= PRESET_COUNT) activePreset = 0;
        return presets.get(activePreset);
    }

    public Set<String> getActiveVisibleBlocks() { return getActivePreset().visibleBlocks; }

    /** Effective highlight range in blocks: the configured value, capped at the render distance. */
    public int getHighlightRangeBlocks(int renderDistance) {
        int maximum = Math.max(1, renderDistance) * 16;
        return highlightRangeBlocks == 0 ? maximum : Mth.clamp(highlightRangeBlocks, 1, maximum);
    }

    /** True when the configured highlight range is smaller than the render distance. */
    public boolean highlightRangeLimited(int renderDistance) {
        return highlightRangeBlocks > 0 && highlightRangeBlocks < Math.max(1, renderDistance) * 16;
    }

    /** True when an ESP target at this squared distance from the camera is within the ESP range. */
    public boolean inEspRange(double distanceSquared) {
        return mobEspRangeBlocks <= 0 || distanceSquared <= (double)mobEspRangeBlocks * mobEspRangeBlocks;
    }

    public boolean setActivePreset(int index) {
        int valid = index >= 0 && index < PRESET_COUNT ? index : 0;
        if (activePreset == valid) return false;
        activePreset = valid;
        return true;
    }

    public int cycleActivePreset() {
        activePreset = activePreset < 0 || activePreset >= PRESET_COUNT ? 0 : (activePreset + 1) % PRESET_COUNT;
        return activePreset;
    }

    public void resetPreset(int index) {
        if (index < 0 || index >= PRESET_COUNT) index = 0;
        presets.set(index, createDefaultPreset(index));
    }

    public void migrateLegacyVisibleBlocks(Set<String> legacyBlocks) {
        presets = createDefaultPresets();
        presets.set(0, new XRayPreset("Preset 1", legacyBlocks == null ? defaultPreset1Blocks() : new LinkedHashSet<>(legacyBlocks)));
        activePreset = 0;
        visibleBlocks = null;
    }

    public void sanitize() {
        if (mobEsp == null) mobEsp = new LinkedHashMap<>();
        mobEsp.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        mobEsp.values().forEach(MobEspSettings::sanitize);
        mobEsp.values().removeIf(MobEspSettings::isEmpty);
        if (itemEsp == null) itemEsp = new LinkedHashMap<>();
        itemEsp.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        itemEsp.values().forEach(MobEspSettings::sanitize);
        itemEsp.values().removeIf(MobEspSettings::isEmpty);
        if (mobEspGroups == null) mobEspGroups = new LinkedHashMap<>();
        Map<String, MobEspGroupSettings> groups = new LinkedHashMap<>();
        for (MobEspGroup group : MobEspGroup.values()) {
            MobEspGroupSettings setting = mobEspGroups.get(group.id);
            if (setting == null) setting = new MobEspGroupSettings(group);
            setting.sanitize();
            groups.put(group.id, setting);
        }
        mobEspGroups = groups;
        if (highlightBlocks == null) highlightBlocks = new LinkedHashSet<>();
        highlightBlocks.remove(null);
        if (structures == null) structures = new LinkedHashSet<>();
        structures.remove(null);
        if (hideMobs == null) hideMobs = HideMobs.OFF;
        highlightColor |= 0xff000000;
        if (highlightBlockColors == null) highlightBlockColors = new LinkedHashMap<>();
        highlightBlockColors.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        highlightBlockColors.replaceAll((id, color) -> color | 0xff000000);
        if (highlightRangeChunks != null) {
            // 1.0.6 and older stored whole chunks; keep the same reach in blocks.
            if (highlightRangeChunks > 0 && highlightRangeBlocks == 0)
                highlightRangeBlocks = Math.min(highlightRangeChunks, MAX_RANGE_BLOCKS / 16) * 16;
            highlightRangeChunks = null;
        }
        if (playerEspHealth == null) playerEspHealth = HealthDisplay.BAR;
        highlightRangeBlocks = sanitizeRange(highlightRangeBlocks);
        mobEspRangeBlocks = sanitizeRange(mobEspRangeBlocks);
        if (!Float.isFinite(terrainOpacity)) terrainOpacity = 0.15f;
        terrainOpacity = Mth.clamp(terrainOpacity, 0.0f, 0.80f);
        if (activePreset < 0 || activePreset >= PRESET_COUNT) activePreset = 0;

        List<XRayPreset> repaired = new ArrayList<>(PRESET_COUNT);
        for (int i = 0; i < PRESET_COUNT; i++) {
            XRayPreset preset = presets != null && i < presets.size() ? presets.get(i) : null;
            if (preset == null) preset = createDefaultPreset(i);
            preset.sanitize(i);
            repaired.add(preset);
        }
        presets = repaired;
        visibleBlocks = null;
    }

    /** Zero (render distance / unlimited) or a range between one step and the maximum. */
    private static int sanitizeRange(int blocks) {
        return blocks <= 0 ? 0 : Mth.clamp(blocks, RANGE_STEP_BLOCKS, MAX_RANGE_BLOCKS);
    }

    public enum EspStyle { OUTLINE, BOX }

    /** Hide Mobs: off, only mobs without line of sight from the camera (behind walls), or all mobs. */
    public enum HideMobs {
        OFF, WALLS, ALL;
        public HideMobs next() { return values()[(ordinal() + 1) % values().length]; }
        public String key() { return "option.betterxray.hide_mobs." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** Player ESP health display: off, numbers, bar or both. */
    public enum HealthDisplay {
        OFF, NUMBERS, BAR, BOTH;
        public boolean numbers() { return this == NUMBERS || this == BOTH; }
        public boolean bar() { return this == BAR || this == BOTH; }
        public HealthDisplay next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** Color every mob entry had before ESP groups existed. */
    public static final int LEGACY_ESP_COLOR = 0xff00ffff;

    /** {@code tracer}: a line from the screen center to the target, in the ESP color. */
    public record ResolvedEsp(EspStyle style, int color, boolean tracer) {}

    /** Per-mob overrides; every {@code null} field follows the mob's group. */
    public static final class MobEspSettings {
        public Boolean enabled;
        public EspStyle style;
        public Integer color;
        public Boolean tracer;

        public boolean isEmpty() { return enabled == null && style == null && color == null && tracer == null; }

        public void sanitize() {
            if (color != null) color |= 0xff000000;
        }
    }

    public static final class MobEspGroupSettings {
        public boolean enabled = false;
        public EspStyle style = EspStyle.OUTLINE;
        public int color;
        public boolean tracer = false;

        public MobEspGroupSettings() { color = MobEspGroup.OTHER.defaultColor; }
        public MobEspGroupSettings(MobEspGroup group) { color = group.defaultColor; style = group.defaultStyle; }

        public void sanitize() {
            if (style == null) style = EspStyle.OUTLINE;
            color |= 0xff000000;
        }
    }

    public static final class XRayPreset {
        public String name;
        public Set<String> visibleBlocks;

        public XRayPreset() {}
        public XRayPreset(String name, Set<String> visibleBlocks) {
            this.name = name;
            this.visibleBlocks = visibleBlocks;
        }

        private void sanitize(int index) {
            if (name == null || name.trim().isEmpty()) name = "Preset " + (index + 1);
            else {
                name = name.trim();
                if (name.length() > MAX_PRESET_NAME_LENGTH) name = name.substring(0, MAX_PRESET_NAME_LENGTH);
            }
            if (visibleBlocks == null) visibleBlocks = createDefaultPreset(index).visibleBlocks;
            else visibleBlocks.remove(null);
        }
    }
}
