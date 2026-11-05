package dev.betterxray.config;

import dev.betterxray.compat.Mc;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.function.Consumer;

/**
 * Entries of a block selection (X-Ray preset or highlight list): block registry IDs such as
 * {@code minecraft:diamond_ore} and block tags written {@code #c:ores}. A tag covers every block
 * it lists, including blocks of other mods. Tags come from the data packs of the joined world, so
 * they are empty on the title screen and can change on {@code /reload}; callers re-resolve them
 * periodically ({@link #TAG_CHECK_TICKS}).
 */
public final class BlockSelection {
    public static final String TAG_PREFIX = "#";
    /** Ticks between checks whether the blocks of the selected tags changed (world join, reload). */
    public static final int TAG_CHECK_TICKS = 20;

    private BlockSelection() {}

    public static boolean isTag(String entry) { return entry != null && entry.startsWith(TAG_PREFIX); }

    public static boolean hasTags(Collection<String> entries) {
        for (String entry : entries) if (isTag(entry)) return true;
        return false;
    }

    /**
     * Canonical entry for user input: lower case, namespace added ({@code diamond_ore} becomes
     * {@code minecraft:diamond_ore}, {@code #ores} becomes {@code #minecraft:ores}); null when invalid.
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String text = input.trim().toLowerCase(Locale.ROOT);
        boolean tag = isTag(text);
        if (tag) text = text.substring(TAG_PREFIX.length());
        if (text.isEmpty() || text.endsWith(":")) return null;
        Identifier id = Identifier.tryParse(text);
        return id == null ? null : (tag ? TAG_PREFIX : "") + id;
    }

    /** Tag of a {@code #namespace:path} entry, or null for block IDs and invalid entries. */
    public static TagKey<Block> tagKey(String entry) {
        if (!isTag(entry)) return null;
        Identifier id = Identifier.tryParse(entry.substring(TAG_PREFIX.length()));
        return id == null ? null : TagKey.create(Registries.BLOCK, id);
    }

    /** Blocks of a tag; empty for unknown tags and while no world has bound the tags. */
    public static List<Block> members(TagKey<Block> tag) {
        List<Block> blocks = new ArrayList<>();
        try {
            for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) blocks.add(holder.value());
        } catch (IllegalStateException unbound) {
            return List.of();
        }
        return blocks;
    }

    /** Blocks of a selection entry: the block itself, the blocks of a tag, or none. */
    public static List<Block> members(String entry) {
        if (isTag(entry)) {
            TagKey<Block> tag = tagKey(entry);
            return tag == null ? List.of() : members(tag);
        }
        Block block = Mc.block(entry);
        return block == null ? List.of() : List.of(block);
    }

    /**
     * Every non-air block selected by the entries. Unknown block IDs are passed to
     * {@code unknownBlock} (may be null); tags are never reported, they may only be unbound.
     */
    public static Set<Block> resolve(Collection<String> entries, Consumer<String> unknownBlock) {
        Set<Block> blocks = new HashSet<>();
        for (String entry : entries) {
            if (isTag(entry)) {
                blocks.addAll(members(entry));
                continue;
            }
            Block block = Mc.block(entry);
            if (block != null) blocks.add(block);
            else if (unknownBlock != null) unknownBlock.accept(entry);
        }
        blocks.removeIf(block -> block.defaultBlockState().isAir());
        return blocks;
    }

    /** First tag entry in {@code entries} that contains the block, or null. */
    public static String coveringTag(Collection<String> entries, Block block) {
        Holder<Block> holder = block.builtInRegistryHolder();
        try {
            for (String entry : entries) {
                TagKey<Block> tag = tagKey(entry);
                if (tag != null && holder.is(tag)) return entry;
            }
        } catch (IllegalStateException unbound) {
            return null;
        }
        return null;
    }

    /**
     * Every block tag that lists at least one non-air block, keyed by its entry ({@code #c:ores}),
     * sorted by ID. Empty before a world is joined.
     */
    public static SortedMap<String, List<Block>> allTags() {
        SortedMap<String, List<Block>> tags = new TreeMap<>();
        try {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.defaultBlockState().isAir()) continue;
                block.builtInRegistryHolder().tags().forEach(tag ->
                    tags.computeIfAbsent(TAG_PREFIX + tag.location(), ignored -> new ArrayList<>()).add(block));
            }
        } catch (IllegalStateException unbound) {
            return new TreeMap<>();
        }
        return tags;
    }
}
