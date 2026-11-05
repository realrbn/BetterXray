package dev.betterxray.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ESP categories shown in the picker. Players and every vanilla mob of 26.2/26.3 have an explicit entry;
 * mobs from other mods fall back to their spawn category (see {@link #fallback(String)}). Every
 * entity that is not a mob (armor stands, minecarts, boats, items, item frames, projectiles, …)
 * belongs to {@link #OBJECTS}.
 */
public enum MobEspGroup {
    PLAYERS(0xffffffff, "player"),
    ANIMALS(0xff55ff55,
        "allay", "armadillo", "bat", "camel", "cat", "chicken", "cow", "donkey", "fox", "frog", "happy_ghast",
        "horse", "mooshroom", "mule", "ocelot", "parrot", "pig", "rabbit", "sheep", "skeleton_horse", "sniffer",
        "strider", "sulfur_cube"),
    AQUATIC(0xff5555ff,
        "axolotl", "cod", "dolphin", "glow_squid", "nautilus", "pufferfish", "salmon", "squid", "tadpole",
        "tropical_fish", "turtle"),
    NEUTRAL(0xffffff55,
        "bee", "goat", "llama", "panda", "polar_bear", "trader_llama", "wolf"),
    VILLAGERS(0xffaaaaaa,
        "villager", "wandering_trader", "iron_golem", "snow_golem", "copper_golem"),
    OVERWORLD_ENEMIES(0xffff5555,
        "bogged", "breeze", "camel_husk", "cave_spider", "creaking", "creeper", "drowned", "elder_guardian",
        "giant", "guardian", "husk", "parched", "phantom", "silverfish", "skeleton", "slime", "spider", "stray",
        "warden", "zombie", "zombie_horse", "zombie_nautilus", "zombie_villager"),
    ILLAGERS(0xff00ffff,
        "evoker", "illusioner", "pillager", "ravager", "vex", "vindicator", "witch"),
    NETHER_ENEMIES(0xffffaa00,
        "blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute", "wither", "wither_skeleton", "zoglin",
        "zombified_piglin"),
    END(0xffff55ff,
        "ender_dragon", "enderman", "endermite", "shulker"),
    OTHER(0xffaaaaaa),
    /** Non-mob entities; assigned by type, so it has no explicit list. Boxes by default. */
    OBJECTS(0xffff8800, XRayConfig.EspStyle.BOX);

    private static final Map<String, MobEspGroup> VANILLA = new HashMap<>();
    static {
        for (MobEspGroup group : values()) for (String path : group.vanillaMobs) {
            if (VANILLA.put("minecraft:" + path, group) != null) throw new IllegalStateException("Duplicate ESP group entry: " + path);
        }
    }

    /** Stable key used in the configuration file and in translation keys. */
    public final String id;
    public final int defaultColor;
    public final XRayConfig.EspStyle defaultStyle;
    private final String[] vanillaMobs;

    MobEspGroup(int defaultColor, String... vanillaMobs) {
        this(defaultColor, XRayConfig.EspStyle.OUTLINE, vanillaMobs);
    }

    MobEspGroup(int defaultColor, XRayConfig.EspStyle defaultStyle, String... vanillaMobs) {
        this.id = name().toLowerCase(Locale.ROOT);
        this.defaultColor = defaultColor;
        this.defaultStyle = defaultStyle;
        this.vanillaMobs = vanillaMobs;
    }

    public String translationKey() { return "esp_group.betterxray." + id; }

    /** Explicit group of a vanilla mob, or {@code null} if the ID is not classified. */
    public static MobEspGroup explicit(String entityId) { return VANILLA.get(entityId); }

    /**
     * Group of any mob. {@code spawnCategory} is the lower-case name of its {@code MobCategory}
     * and only decides for mobs without an explicit entry (e.g. from other mods).
     */
    public static MobEspGroup of(String entityId, String spawnCategory) {
        MobEspGroup group = VANILLA.get(entityId);
        return group != null ? group : fallback(spawnCategory);
    }

    public static MobEspGroup fallback(String spawnCategory) {
        return switch (spawnCategory == null ? "" : spawnCategory.toLowerCase(Locale.ROOT)) {
            case "monster" -> OVERWORLD_ENEMIES;
            case "creature", "ambient" -> ANIMALS;
            case "water_creature", "water_ambient", "underground_water_creature", "axolotls" -> AQUATIC;
            default -> OTHER;
        };
    }

    public static MobEspGroup byId(String id) {
        for (MobEspGroup group : values()) if (group.id.equals(id)) return group;
        return null;
    }
}
