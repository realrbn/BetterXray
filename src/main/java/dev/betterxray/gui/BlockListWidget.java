package dev.betterxray.gui;

import dev.betterxray.config.BlockSelection;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import java.util.*;

/**
 * Blocks and block tags of a selection. Selected tag rows ({@code #c:ores}) come first, other
 * tags follow the blocks when the search matches them; a search starting with {@code #} lists tags
 * only. Blocks covered by a selected tag show it ("via #c:ores") without being selected themselves.
 */
public final class BlockListWidget extends SelectionList<BlockListWidget.Row> {
    private final List<Row> blocks = new ArrayList<>();
    /** Tag rows by entry; tags of the joined world plus selected tags that are not bound (yet). */
    private final Map<String, Row> tags = new LinkedHashMap<>();
    private final Runnable changed;
    private final boolean highlight;
    /** Opens the color of a highlighted block or tag (ID, name); {@code null} when rows have no color square. */
    private final java.util.function.BiConsumer<String, Component> openColor;
    /** Comfortable row height: name and registry ID with generous padding. */
    public static final int ROW_HEIGHT = 36;
    /** Denser row height used when little vertical space is available. */
    public static final int COMPACT_ROW_HEIGHT = 28;
    private static final int ROW_INSET = 12;
    /** Width of the clickable color square area at the right end of a highlight row. */
    private static final int COLOR_AREA = 22;

    public BlockListWidget(Minecraft minecraft, int width, int height, int top, Runnable changed) {
        this(minecraft, width, height, top, changed, false);
    }
    public BlockListWidget(Minecraft minecraft, int width, int height, int top, Runnable changed, boolean highlight) {
        this(minecraft, width, height, top, changed, highlight, ROW_HEIGHT);
    }
    public BlockListWidget(Minecraft minecraft, int width, int height, int top, Runnable changed, boolean highlight, int rowHeight) {
        this(minecraft, width, height, top, changed, highlight, rowHeight, null);
    }
    public BlockListWidget(Minecraft minecraft, int width, int height, int top, Runnable changed, boolean highlight, int rowHeight,
                           java.util.function.BiConsumer<String, Component> openColor) {
        super(minecraft, width, height, top, rowHeight);
        this.changed = changed;
        this.highlight = highlight;
        this.openColor = highlight ? openColor : null;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().isAir()) blocks.add(new Row(block));
        }
        blocks.sort(Comparator.comparing(row -> row.name.getString().toLowerCase(Locale.ROOT)));
        BlockSelection.allTags().forEach((entry, members) -> tags.put(entry, new Row(entry, members)));
    }
    @Override public int getRowWidth() { return listWidth() - 2 * ROW_INSET; }
    public void filter(String query, boolean selectedOnly) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        boolean tagsOnly = needle.startsWith(BlockSelection.TAG_PREFIX);
        String tagNeedle = tagsOnly ? needle.substring(BlockSelection.TAG_PREFIX.length()) : needle;
        // Selected tags without a bound tag (title screen, missing mod) stay listed so they can be removed.
        for (String entry : selectedEntries()) {
            if (BlockSelection.isTag(entry) && !tags.containsKey(entry)) tags.put(entry, new Row(entry, List.of()));
        }
        // Selected tags first; other matching tags follow the blocks so a plain search keeps its blocks on top.
        List<Row> rows = new ArrayList<>();
        List<Row> otherTags = new ArrayList<>();
        for (Row row : tags.values()) {
            if (!row.matches(tagNeedle)) continue;
            if (row.selected()) rows.add(row);
            else if (!selectedOnly && !needle.isEmpty()) otherTags.add(row);
        }
        if (!tagsOnly) {
            for (Row row : blocks) {
                if ((!selectedOnly || row.selected() || row.coveringTag() != null) && row.matches(needle)) rows.add(row);
            }
        }
        rows.addAll(otherTags);
        double scroll = GuiLayout.scrollAmount(this);
        replaceEntries(rows);
        setScrollAmount(scroll);
    }
    private Set<String> selectedEntries() {
        var config = XRayConfigManager.get();
        return highlight ? config.highlightBlocks : config.getActiveVisibleBlocks();
    }
    public final class Row extends ListEntry<Row> {
        private final String id;
        private final Component name;
        /** The block of a block row; {@code null} for a tag row. */
        private final Block block;
        /** Blocks of a tag row when the screen opened; empty for block rows and unbound tags. */
        private final List<Block> members;
        private ItemStack icon;
        Row(Block block) {
            this.block = block;
            id = BuiltInRegistries.BLOCK.getKey(block).toString();
            name = block.getName();
            members = List.of();
        }
        Row(String tag, List<Block> members) {
            block = null;
            id = tag;
            this.members = List.copyOf(members);
            name = tagName(tag);
        }
        boolean isTag() { return block == null; }
        boolean matches(String needle) {
            return id.contains(needle) || name.getString().toLowerCase(Locale.ROOT).contains(needle);
        }
        /**
         * Item components are only bound once a world is loaded. Without a world (for example
         * when the screen is opened through Mod Menu from the title screen) the row is shown
         * without an icon instead of crashing; the icon appears once components are available.
         */
        private ItemStack icon() {
            if (icon == null) {
                // A tag shows its first block that has an item (fire, water etc. have none).
                Block shown = block;
                if (shown == null) {
                    for (Block member : members) if (member.asItem() != Items.AIR) { shown = member; break; }
                    if (shown == null) return icon = ItemStack.EMPTY;
                }
                //? if >=26.1 {
                var item = shown.asItem().builtInRegistryHolder();
                if (!item.areComponentsBound()) return ItemStack.EMPTY;
                icon = new ItemStack(item);
                //?} else {
                /*icon = new ItemStack(shown.asItem());
                *///?}
            }
            return icon;
        }
        boolean selected() { return selectedEntries().contains(id); }
        /** Selected tag that contains this block, or null; always null for tag rows. */
        String coveringTag() { return block == null ? null : BlockSelection.coveringTag(selectedEntries(), block); }
        void toggle() {
            var selected = selectedEntries();
            if (!selected.remove(id)) selected.add(id);
            changed.run();
        }
        private boolean ownColor() { return XRayConfigManager.get().highlightBlockColors.containsKey(id); }
        /** Color square: the row's own color, else the color of a colored tag that contains the block, else global. */
        private int color() {
            var config = XRayConfigManager.get();
            if (block == null || ownColor()) return config.highlightColor(id);
            String tag = BlockSelection.coveringTag(config.highlightBlockColors.keySet(), block);
            return config.highlightColor(tag != null ? tag : id);
        }
        @Override public Component getNarration() {
            String color = openColor == null ? "" : ", " + Component.translatable(ownColor()
                ? "label.betterxray.highlight_own_color" : "label.betterxray.highlight_global_color").getString();
            String kind = isTag() ? ", " + Component.translatable("label.betterxray.tag_blocks", members.size()).getString() : "";
            String via = coveringTag();
            String state = selected() ? ", selected" : via != null ? ", selected via " + via : ", not selected";
            return Component.literal(name.getString() + ", " + id + kind + state + color);
        }
        @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
            int x = contentX(), y = contentY();
            int textTop = y + Math.max(0, (contentHeight() - 22) / 2);
            boolean selected = selected();
            String via = selected ? null : coveringTag();
            if (GlassTheme.ENABLED) {
                if (selected) GlassTheme.rowTint(g, x, y, contentRight(), contentBottom(), GlassTheme.ACCENT);
                int boxY = y + (contentHeight() - 14) / 2;
                GlassTheme.checkbox(g, minecraft.font, x + 4, boxY, 14, selected, hovered);
                if (via != null) g.text(minecraft.font, "✔", x + 4 + (14 - minecraft.font.width("✔")) / 2 + 1, boxY + 3, GlassTheme.TEXT_MUTED, false);
            } else {
                if (selected) g.fill(x, y, contentRight(), contentBottom(), 0x4038ae65);
                String box = selected ? "[x]" : via != null ? "[~]" : "[ ]";
                g.text(minecraft.font, box, x + 3, y + (contentHeight() - 8) / 2, selected ? 0xff8dffac : 0xffaaaaaa);
            }
            ItemStack icon = icon();
            if (!icon.isEmpty()) g.item(icon, x + 27, y + (contentHeight() - 16) / 2);
            int textWidth = contentWidth() - 56 - (openColor != null ? COLOR_AREA : 0);
            String second = isTag() ? id + "  ·  " + Component.translatable("label.betterxray.tag_blocks", members.size()).getString() : id;
            if (via != null) second += "  ·  " + Component.translatable("label.betterxray.via_tag", via).getString();
            if (openColor != null) {
                // Color square: filled with the effective color, outlined white when the row has its own.
                int sx = contentRight() - COLOR_AREA + 5, sy = contentYMiddle() - 6;
                boolean own = ownColor();
                if (own) g.outline(sx - 2, sy - 2, 16, 16, 0xffffffff);
                else if (hovered) g.outline(sx - 2, sy - 2, 16, 16, 0xff888888);
                g.fill(sx, sy, sx + 12, sy + 12, color());
                if (own) second += "  ·  " + Component.translatable("label.betterxray.highlight_own_color").getString();
            }
            g.text(minecraft.font, minecraft.font.plainSubstrByWidth(name.getString(), textWidth), x + 50, textTop, 0xffffffff);
            g.text(minecraft.font, minecraft.font.plainSubstrByWidth(second, textWidth), x + 50, textTop + 13,
                GlassTheme.ENABLED ? GlassTheme.TEXT_MUTED : 0xffaaaaaa);
        }
        @Override protected boolean primaryClick(double mouseX, double mouseY) {
            setSelected(this);
            if (openColor != null && mouseX >= contentRight() - COLOR_AREA) openColor.accept(id, name);
            else toggle();
            return true;
        }
        @Override protected boolean keyInput(Keys keys) {
            if (keys.selection()) { toggle(); return true; }
            if (openColor != null && keys.right()) { openColor.accept(id, name); return true; }
            return false;
        }
    }

    /**
     * Display name of a block tag: its translation ({@code tag.block.c.ores}, provided by Fabric and
     * NeoForge for their convention tags) or the tag ID.
     */
    public static Component tagName(String entry) {
        String path = entry.substring(BlockSelection.TAG_PREFIX.length());
        int colon = path.indexOf(':');
        String key = "tag.block." + (colon < 0 ? "minecraft." + path : path.substring(0, colon) + "." + path.substring(colon + 1)).replace('/', '.');
        return Component.translatableWithFallback(key, entry);
    }
}
