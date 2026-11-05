package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/**
 * Registry-driven mob picker, grouped by {@link MobEspGroup}; works from Mod Menu without a world.
 * Group rows switch a whole group on/off (toggle on the right), open the group's default
 * style/color (click on the name) and collapse (arrow or Left/Right keys). Mob rows open the
 * per-mob overrides; their toggle on the right flips the effective state of that one mob.
 * The dropped-item row (Objects → Item) opens a second view of this screen with
 * one row per item, its own search and enabled filter; each item has its own toggle and overrides
 * on top of the item entity's settings. Searches in the mob view also list matching items.
 */
public final class MobEspScreen extends Screen {
    /** Collapsed groups are remembered for the running session. */
    private static final Set<MobEspGroup> COLLAPSED = EnumSet.noneOf(MobEspGroup.class);
    private static final int ARROW_WIDTH = 14;
    private static final int INDENT = 10;

    private final Screen parent;
    /** Item view: only the item rows of Objects → Item, with their own search and filter. */
    private final boolean itemsOnly;
    private MobList list;
    /** The mob list (used by the validation fixture). */
    public MobList list() { return list; }
    private String query = "";
    private boolean enabledOnly;
    private int titleY;
    private double scroll;
    private Button collapseButton;

    public MobEspScreen(Screen parent) { this(parent, false); }

    private MobEspScreen(Screen parent, boolean itemsOnly) {
        super(Component.translatable(itemsOnly ? "screen.betterxray.esp_items" : "screen.betterxray.esp"));
        this.parent = parent;
        this.itemsOnly = itemsOnly;
    }

    /** The item view (Objects → Item). */
    public static MobEspScreen items(Screen parent) { return new MobEspScreen(parent, true); }
    public boolean itemsOnly() { return itemsOnly; }

    @Override protected void init() {
        int contentWidth = GuiLayout.contentWidth(width, 480);
        int left = (width - contentWidth) / 2;
        var metrics = ListScreens.metrics(height, font, GuiLayout.BUTTON_HEIGHT + GuiLayout.NORMAL.rowGap);
        titleY = ListScreens.titleY(metrics);
        int y = ListScreens.controlsTop(metrics, font);
        var search = new EditBox(font, 0, 0, 0, GuiLayout.BUTTON_HEIGHT, Component.translatable("screen.betterxray.search"));
        search.setHint(Component.translatable(itemsOnly ? "screen.betterxray.search_item"
            : "screen.betterxray.search_mob_item"));
        search.setMaxLength(200);
        search.setValue(query);
        var filter = Button.builder(filterLabel(), b -> {
            enabledOnly = !enabledOnly;
            b.setMessage(filterLabel());
            refresh();
        }).build();
        y = ListScreens.searchRow(left, y, contentWidth, search, filter) + ListScreens.listGap(metrics);
        addRenderableWidget(search);
        addRenderableWidget(filter);
        int footerY = ListScreens.footerY(metrics, height);
        if (itemsOnly) {
            initItemFooter(contentWidth, left, y, footerY, metrics);
            search.setResponder(value -> { query = value; refresh(); });
            setInitialFocus(search);
            return;
        }
        int footerWidth = Math.min(440, contentWidth);
        // Global ESP range sits directly above the footer buttons and shortens the list.
        var range = RangeSlider.esp();
        range.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_range")));
        int rangeY = footerY - GuiLayout.BUTTON_HEIGHT - metrics.rowGap;
        GuiLayout.row((width - footerWidth) / 2, rangeY, footerWidth, range);
        if (list != null) scroll = GuiLayout.scrollAmount(list);
        list = addRenderableWidget(new MobList(minecraft, contentWidth,
            Math.max(0, rangeY - ListScreens.listGap(metrics) - y), y, ListScreens.rowHeight(metrics)));
        list.placeAt(left);
        search.setResponder(value -> { query = value; refresh(); });
        refresh();
        list.setScrollAmount(scroll);
        addRenderableWidget(range);
        collapseButton = Button.builder(collapseLabel(), b -> {
            if (COLLAPSED.isEmpty()) {
                COLLAPSED.addAll(EnumSet.allOf(MobEspGroup.class));
            } else COLLAPSED.clear();
            refresh();
        }).build();
        var master = Button.builder(masterLabel(), b -> {
            var config = XRayConfigManager.get();
            config.mobEspEnabled = !config.mobEspEnabled;
            XRayConfigManager.save();
            b.setMessage(masterLabel());
        }).build();
        master.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_master")));
        var done = Button.builder(Component.translatable("gui.done"), b -> onClose()).build();
        var tracers = Button.builder(tracersLabel(), b -> {
            var config = XRayConfigManager.get();
            config.mobEspTracersEnabled = !config.mobEspTracersEnabled;
            XRayConfigManager.save();
            b.setMessage(tracersLabel());
        }).build();
        tracers.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_tracers_master")));
        GuiLayout.row((width - footerWidth) / 2, footerY, footerWidth, master, tracers, collapseButton, done);
        addRenderableWidget(tracers);
        addRenderableWidget(master);
        addRenderableWidget(collapseButton);
        addRenderableWidget(done);
        setInitialFocus(search);
    }

    /** Item view: the list reaches down to the footer, which links the defaults of all items. */
    private void initItemFooter(int contentWidth, int left, int y, int footerY, GuiLayout metrics) {
        if (list != null) scroll = GuiLayout.scrollAmount(list);
        list = addRenderableWidget(new MobList(minecraft, contentWidth,
            Math.max(0, footerY - ListScreens.listGap(metrics) - y), y, ListScreens.rowHeight(metrics)));
        list.placeAt(left);
        refresh();
        list.setScrollAmount(scroll);
        int footerWidth = Math.min(360, contentWidth);
        var defaults = Button.builder(Component.translatable("button.betterxray.esp_item_link"),
            b -> Mc.setScreen(minecraft, new MobEspSettingsScreen(this, Mc.entityType(XRayConfig.ITEM_ENTITY)))).build();
        defaults.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_item_link")));
        var done = Button.builder(Component.translatable("gui.done"), b -> onClose()).build();
        GuiLayout.row((width - footerWidth) / 2, footerY, footerWidth, defaults, done);
        addRenderableWidget(defaults);
        addRenderableWidget(done);
    }

    private Component filterLabel() {
        if (itemsOnly) return Component.translatable(enabledOnly ? "button.betterxray.esp_items_enabled" : "button.betterxray.all_items");
        return Component.translatable(enabledOnly ? "button.betterxray.esp_selected" : "button.betterxray.all_mobs");
    }
    private static Component tracersLabel() {
        return Component.translatable("button.betterxray.esp_tracers_master",
            Component.translatable(XRayConfigManager.get().mobEspTracersEnabled ? "options.on" : "options.off"));
    }
    private static Component masterLabel() {
        return Component.translatable("button.betterxray.esp_master",
            Component.translatable(XRayConfigManager.get().mobEspEnabled ? "options.on" : "options.off"));
    }
    private static Component collapseLabel() {
        return Component.translatable(COLLAPSED.isEmpty() ? "button.betterxray.collapse_all" : "button.betterxray.expand_all");
    }
    private void refresh() {
        if (list != null) list.filter(query);
        if (collapseButton != null) collapseButton.setMessage(collapseLabel());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, title, width / 2, titleY);
    }
    @Override public void onClose() { XRayConfigManager.save(); Mc.setScreen(minecraft, parent); }

    /** Row hints are wrapped so they never run off narrow screens. */
    private void tooltip(GuiGraphicsExtractor g, Component text, int mx, int my) {
        //? if >=1.21.6 {
        g.setTooltipForNextFrame(font.split(text, Math.max(80, Math.min(240, width - 24))), mx, my);
        //?} else {
        /*setTooltipForNextRenderPass(font.split(text, Math.max(80, Math.min(240, width - 24))));
        *///?}
    }

    /** "ESP: Yes" / "ESP: No" as shown on the row toggles. */
    private static Component toggleLabel(boolean enabled) { return MobEspSettingsScreen.enabledLabel(enabled); }

    public final class MobList extends SelectionList<MobList.Row> {
        private final Map<MobEspGroup, GroupRow> groups = new EnumMap<>(MobEspGroup.class);
        private final int toggleWidth;

        MobList(Minecraft client, int width, int height, int top, int rowHeight) {
            super(client, width, height, top, rowHeight);
            toggleWidth = Math.max(client.font.width(toggleLabel(true)), client.font.width(toggleLabel(false))) + 10;
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                if (!MobEspRenderer.isEspType(type)) continue;
                MobEspGroup group = MobEspRenderer.group(type);
                groups.computeIfAbsent(group, GroupRow::new).mobs.add(new MobRow(type, group));
            }
            for (GroupRow group : groups.values())
                group.mobs.sort(Comparator.comparing(row -> row.name.getString().toLowerCase(Locale.ROOT)));
            MobRow itemEntity = itemEntityRow();
            if (itemEntity != null) {
                for (Item item : BuiltInRegistries.ITEM) if (item != Items.AIR) itemEntity.items.add(new ItemRow(item));
                itemEntity.items.sort(Comparator.comparing(row -> row.name.getString().toLowerCase(Locale.ROOT)));
            }
        }

        /** The dropped-item row under Objects, or {@code null} if absent. */
        public MobRow itemEntityRow() {
            for (MobRow row : mobsOf(MobEspGroup.OBJECTS)) if (row.id.equals(XRayConfig.ITEM_ENTITY)) return row;
            return null;
        }
        @Override public int getRowWidth() { return listWidth() - 24; }

        /** Rows of one group, in display order; empty groups (e.g. no modded mobs) have none. */
        public List<MobRow> mobsOf(MobEspGroup group) {
            GroupRow row = groups.get(group);
            return row == null ? List.of() : Collections.unmodifiableList(row.mobs);
        }
        public GroupRow groupRow(MobEspGroup group) { return groups.get(group); }

        void filter(String query) {
            String needle = query.strip().toLowerCase(Locale.ROOT);
            boolean filtering = !needle.isEmpty() || enabledOnly;
            double previous = GuiLayout.scrollAmount(this);
            List<Row> rows = new ArrayList<>();
            if (itemsOnly) {
                MobRow itemEntity = itemEntityRow();
                if (itemEntity != null) for (ItemRow row : itemEntity.items)
                    if ((!enabledOnly || row.enabled()) && row.matches(needle)) rows.add(row);
                replaceEntries(rows);
                setScrollAmount(previous);
                return;
            }
            for (GroupRow group : groups.values()) {
                boolean groupMatches = needle.isEmpty() || group.name.getString().toLowerCase(Locale.ROOT).contains(needle);
                List<Row> matches = new ArrayList<>();
                for (MobRow row : group.mobs) {
                    boolean textMatches = groupMatches || row.matches(needle);
                    List<ItemRow> items = row.visibleItems(needle, textMatches);
                    // A mob row with matching items stays as their parent even if it does not match itself.
                    if (((!enabledOnly || row.enabled()) && textMatches) || !items.isEmpty()) {
                        matches.add(row);
                        matches.addAll(items);
                    }
                }
                if (filtering && matches.isEmpty()) continue;
                rows.add(group);
                // While searching/filtering, matches are always shown so nothing seems to be missing.
                if (filtering || !COLLAPSED.contains(group.group)) rows.addAll(matches);
            }
            replaceEntries(rows);
            setScrollAmount(previous);
        }

        public abstract sealed class Row extends ListEntry<Row> permits GroupRow, MobRow, ItemRow {
            /** Left edge of the ESP toggle drawn at the right end of the row. */
            int toggleLeft() { return contentRight() - toggleWidth - 2; }
            boolean overToggle(double mouseX) { return mouseX >= toggleLeft(); }
            abstract void toggle();
            abstract void open();

            void drawToggle(GuiGraphicsExtractor g, boolean enabled, int color, boolean hovered) {
                int x0 = toggleLeft(), x1 = contentRight() - 2;
                int y0 = contentYMiddle() - 8, y1 = contentYMiddle() + 8;
                if (GlassTheme.ENABLED) {
                    GlassTheme.toggle(g, x0, y0, x1, y1, enabled, color, hovered);
                } else {
                    g.fill(x0, y0, x1, y1, enabled ? 0xa0205a33 : 0xa0000000);
                    g.outline(x0, y0, x1 - x0, y1 - y0, hovered ? 0xffffffff : enabled ? color : 0xff707070);
                }
                Component label = toggleLabel(enabled);
                g.text(minecraft.font, label, (x0 + x1 - minecraft.font.width(label)) / 2, contentYMiddle() - 4,
                    enabled ? 0xffffffff : 0xffaaaaaa);
            }

            @Override protected boolean primaryClick(double mouseX, double mouseY) {
                setSelected(this);
                if (overToggle(mouseX)) toggle();
                else open();
                return true;
            }
        }

        public final class GroupRow extends Row {
            final MobEspGroup group;
            final Component name;
            final List<MobRow> mobs = new ArrayList<>();

            GroupRow(MobEspGroup group) { this.group = group; name = Component.translatable(group.translationKey()); }

            public MobEspGroup group() { return group; }
            public boolean collapsed() { return COLLAPSED.contains(group); }
            private XRayConfig.MobEspGroupSettings settings() { return XRayConfigManager.get().espGroup(group); }
            int enabledCount() { int n = 0; for (MobRow row : mobs) if (row.enabled()) n++; return n; }

            @Override void toggle() {
                var setting = settings();
                setting.enabled = !setting.enabled;
                XRayConfigManager.save();
                if (enabledOnly) filter(query);
            }
            @Override void open() {
                scroll = GuiLayout.scrollAmount(MobList.this);
                Mc.setScreen(minecraft, new MobEspSettingsScreen(MobEspScreen.this, group));
            }
            public void setCollapsed(boolean collapsed) {
                if (collapsed) COLLAPSED.add(group); else COLLAPSED.remove(group);
                refresh();
                MobList.this.setFocused(this);
                setSelected(this);
            }

            @Override protected boolean primaryClick(double mouseX, double mouseY) {
                if (mouseX < contentX() + ARROW_WIDTH) {
                    setCollapsed(!collapsed());
                    return true;
                }
                return super.primaryClick(mouseX, mouseY);
            }
            @Override protected boolean keyInput(Keys keys) {
                if (keys.selection()) { open(); return true; }
                if (keys.left() && !collapsed()) { setCollapsed(true); return true; }
                if (keys.right() && collapsed()) { setCollapsed(false); return true; }
                return false;
            }
            @Override public Component getNarration() {
                return Component.translatable("narration.betterxray.esp_group", name, enabledCount(), mobs.size(),
                    toggleLabel(settings().enabled));
            }
            @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
                var font = minecraft.font;
                var setting = settings();
                int x = contentX(), y = contentY();
                if (GlassTheme.ENABLED) {
                    GlassTheme.rowTint(g, x, y, contentRight(), contentBottom(), 0xffffff);
                    GlassTheme.surface(g, x + 1, y + 3, x + 3, contentBottom() - 3, 1, setting.color, setting.color, 0, 0);
                } else {
                    g.fill(x, y, contentRight(), contentBottom(), 0x40ffffff);
                    g.fill(x, y, x + 2, contentBottom(), setting.color);
                }
                boolean twoLines = contentHeight() >= 24;
                int textY = y + Math.max(0, (contentHeight() - (twoLines ? 22 : 9)) / 2);
                g.text(font, collapsed() ? "▶" : "▼", x + 4, contentYMiddle() - 4, 0xffdddddd);
                int sx = x + ARROW_WIDTH + 2;
                g.fill(sx, contentYMiddle() - 5, sx + 10, contentYMiddle() + 5, setting.color);
                int textX = sx + 16, textWidth = Math.max(0, toggleLeft() - textX - 4);
                String style = Component.translatable(setting.style == XRayConfig.EspStyle.BOX
                    ? "option.betterxray.esp_box" : "option.betterxray.esp_outline").getString();
                if (setting.tracer)
                    style += " + " + Component.translatable("label.betterxray.esp_tracer").getString();
                String info = Component.translatable("label.betterxray.esp_group_info", enabledCount(), mobs.size(), style).getString();
                if (twoLines) {
                    g.text(font, font.plainSubstrByWidth(name.getString(), textWidth), textX, textY, 0xffffff55);
                    g.text(font, font.plainSubstrByWidth(info, textWidth), textX, textY + 13, 0xffaaaaaa);
                } else {
                    String line = name.getString() + "  " + info;
                    g.text(font, font.plainSubstrByWidth(line, textWidth), textX, textY, 0xffffff55);
                }
                boolean overToggle = hovered && overToggle(mx);
                drawToggle(g, setting.enabled, setting.color, overToggle);
                if (hovered) tooltip(g, Component.translatable(overToggle
                    ? "tooltip.betterxray.esp_group_toggle" : "tooltip.betterxray.esp_group_open"), mx, my);
            }
        }

        public final class MobRow extends Row {
            private final EntityType<?> type;
            private final MobEspGroup group;
            final String id;
            final Component name;
            /** Item rows below the dropped-item row; empty for every other mob. */
            final List<ItemRow> items = new ArrayList<>();

            MobRow(EntityType<?> type, MobEspGroup group) {
                this.type = type;
                this.group = group;
                id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                name = type.getDescription();
            }

            public String id() { return id; }
            public MobEspGroup group() { return group; }
            public boolean enabled() { return XRayConfigManager.get().espEnabled(id, group); }
            private boolean customized() { var own = XRayConfigManager.get().mobEsp.get(id); return own != null && !own.isEmpty(); }
            boolean matches(String needle) { return id.contains(needle) || name.getString().toLowerCase(Locale.ROOT).contains(needle); }

            public boolean hasItems() { return !items.isEmpty(); }
            public List<ItemRow> items() { return Collections.unmodifiableList(items); }
            int enabledItems() { int n = 0; for (ItemRow row : items) if (row.enabled()) n++; return n; }

            /**
             * Items listed below this row in the mob view: only those found by their own name or ID,
             * or, with the enabled filter, those switched on by their own setting. All items are in
             * the item view ({@link #openItems()}).
             */
            List<ItemRow> visibleItems(String needle, boolean parentMatches) {
                if (items.isEmpty()) return List.of();
                List<ItemRow> visible = new ArrayList<>();
                for (ItemRow row : items) {
                    boolean shown = needle.isEmpty() ? enabledOnly && row.explicitlyEnabled() : row.matches(needle);
                    if (shown && (!enabledOnly || row.enabled())) visible.add(row);
                }
                return visible;
            }

            public void openItems() {
                scroll = GuiLayout.scrollAmount(MobList.this);
                Mc.setScreen(minecraft, MobEspScreen.items(MobEspScreen.this));
            }

            @Override protected boolean primaryClick(double mouseX, double mouseY) {
                if (hasItems() && mouseX < contentX() + INDENT + 2) {
                    setSelected(this);
                    openItems();
                    return true;
                }
                return super.primaryClick(mouseX, mouseY);
            }
            @Override void toggle() {
                var config = XRayConfigManager.get();
                boolean target = !enabled();
                var own = config.mobEsp.computeIfAbsent(id, ignored -> new XRayConfig.MobEspSettings());
                own.enabled = target == config.espGroup(group).enabled ? null : target;
                if (own.isEmpty()) config.mobEsp.remove(id);
                XRayConfigManager.save();
                if (enabledOnly) filter(query);
            }
            @Override void open() {
                scroll = GuiLayout.scrollAmount(MobList.this);
                Mc.setScreen(minecraft, new MobEspSettingsScreen(MobEspScreen.this, type));
            }
            @Override public Component getNarration() {
                return Component.literal(name.getString() + ", " + id + ", ").append(toggleLabel(enabled()))
                    .append(", ").append(Component.translatable(customized() ? "label.betterxray.esp_custom" : "label.betterxray.esp_from_group"));
            }
            @Override protected boolean keyInput(Keys keys) {
                if (keys.selection()) { open(); return true; }
                if (hasItems() && keys.right()) { openItems(); return true; }
                return false;
            }
            @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
                var font = minecraft.font;
                var config = XRayConfigManager.get();
                int x = contentX() + INDENT, y = contentY();
                boolean enabled = enabled();
                int color = config.espColor(id, group);
                if (hasItems()) g.text(font, "▶", contentX() + 1, contentYMiddle() - 4, 0xffdddddd);
                if (enabled) {
                    if (GlassTheme.ENABLED) GlassTheme.rowTint(g, x, y, contentRight(), contentBottom(), 0x38ae65);
                    else g.fill(x, y, contentRight(), contentBottom(), 0x4038ae65);
                }
                boolean twoLines = contentHeight() >= 24;
                int textY = y + Math.max(0, (contentHeight() - (twoLines ? 22 : 9)) / 2);
                g.fill(x + 4, contentYMiddle() - 5, x + 14, contentYMiddle() + 5, color);
                if (config.espStyle(id, group) == XRayConfig.EspStyle.BOX)
                    g.outline(x + 2, contentYMiddle() - 7, 14, 14, 0xffffffff);
                int textX = x + 22, textWidth = Math.max(0, toggleLeft() - textX - 4);
                String source = Component.translatable(customized() ? "label.betterxray.esp_custom" : "label.betterxray.esp_from_group").getString();
                if (twoLines) {
                    g.text(font, font.plainSubstrByWidth(name.getString(), textWidth), textX, textY, 0xffffffff);
                    String second = id + "  ·  " + source;
                    if (config.espTracer(id, group))
                        second += "  ·  " + Component.translatable("label.betterxray.esp_tracer").getString();
                    if (hasItems())
                        second += "  ·  " + Component.translatable("label.betterxray.esp_item_count", enabledItems(), items.size()).getString();
                    g.text(font, font.plainSubstrByWidth(second, textWidth), textX, textY + 13, 0xffaaaaaa);
                } else {
                    g.text(font, font.plainSubstrByWidth(name.getString() + "  " + id, textWidth), textX, textY, 0xffffffff);
                }
                boolean overToggle = hovered && overToggle(mx);
                drawToggle(g, enabled, color, overToggle);
                if (hovered && overToggle) tooltip(g, Component.translatable(hasItems()
                    ? "tooltip.betterxray.esp_item_entity_toggle" : "tooltip.betterxray.esp_mob_toggle"), mx, my);
            }
        }

        /** One item below Objects → Item; its settings follow the dropped-item entity unless overridden. */
        public final class ItemRow extends Row {
            private final Item item;
            final String id;
            final Component name;
            private ItemStack icon;

            ItemRow(Item item) {
                this.item = item;
                id = MobEspRenderer.itemId(item);
                name = Component.translatable(item.getDescriptionId());
            }

            public String id() { return id; }
            public boolean enabled() { return XRayConfigManager.get().itemEspEnabled(id); }
            private XRayConfig.MobEspSettings own() { return XRayConfigManager.get().itemEsp.get(id); }
            private boolean customized() { var own = own(); return own != null && !own.isEmpty(); }
            boolean explicitlyEnabled() { var own = own(); return own != null && Boolean.TRUE.equals(own.enabled); }
            boolean matches(String needle) { return id.contains(needle) || name.getString().toLowerCase(Locale.ROOT).contains(needle); }

            /** Without a world (Mod Menu on the title screen) item components are unbound; no icon then. */
            private ItemStack icon() {
                if (icon == null) {
                    //? if >=26.1 {
                    var holder = item.builtInRegistryHolder();
                    if (!holder.areComponentsBound()) return ItemStack.EMPTY;
                    icon = new ItemStack(holder);
                    //?} else {
                    /*icon = new ItemStack(item);
                    *///?}
                }
                return icon;
            }

            /** Flips the effective state; an override is only kept when it differs from the item entity. */
            @Override void toggle() {
                var config = XRayConfigManager.get();
                boolean target = !enabled();
                var own = config.itemEsp.computeIfAbsent(id, ignored -> new XRayConfig.MobEspSettings());
                own.enabled = target == config.espEnabled(XRayConfig.ITEM_ENTITY, MobEspGroup.OBJECTS) ? null : target;
                if (own.isEmpty()) config.itemEsp.remove(id);
                XRayConfigManager.save();
                if (enabledOnly) filter(query);
            }
            @Override void open() {
                scroll = GuiLayout.scrollAmount(MobList.this);
                Mc.setScreen(minecraft, new MobEspSettingsScreen(MobEspScreen.this, item));
            }
            @Override public Component getNarration() {
                return Component.literal(name.getString() + ", " + id + ", ").append(toggleLabel(enabled()))
                    .append(", ").append(Component.translatable(customized() ? "label.betterxray.esp_custom" : "label.betterxray.esp_from_item"));
            }
            @Override protected boolean keyInput(Keys keys) {
                if (keys.selection()) { open(); return true; }
                return false;
            }
            @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
                var font = minecraft.font;
                var config = XRayConfigManager.get();
                int x = contentX() + (itemsOnly ? 0 : 2 * INDENT), y = contentY();
                boolean enabled = enabled();
                int color = config.itemEspColor(id);
                if (enabled) {
                    if (GlassTheme.ENABLED) GlassTheme.rowTint(g, x, y, contentRight(), contentBottom(), 0x38ae65);
                    else g.fill(x, y, contentRight(), contentBottom(), 0x4038ae65);
                }
                boolean twoLines = contentHeight() >= 24;
                int textY = y + Math.max(0, (contentHeight() - (twoLines ? 22 : 9)) / 2);
                g.fill(x + 4, contentYMiddle() - 5, x + 14, contentYMiddle() + 5, color);
                if (config.itemEspStyle(id) == XRayConfig.EspStyle.BOX)
                    g.outline(x + 2, contentYMiddle() - 7, 14, 14, 0xffffffff);
                ItemStack stack = icon();
                if (!stack.isEmpty()) g.item(stack, x + 20, contentYMiddle() - 8);
                int textX = x + 40, textWidth = Math.max(0, toggleLeft() - textX - 4);
                String source = Component.translatable(customized() ? "label.betterxray.esp_custom" : "label.betterxray.esp_from_item").getString();
                if (twoLines) {
                    g.text(font, font.plainSubstrByWidth(name.getString(), textWidth), textX, textY, 0xffffffff);
                    String second = id + "  ·  " + source;
                    if (config.itemEspTracer(id))
                        second += "  ·  " + Component.translatable("label.betterxray.esp_tracer").getString();
                    g.text(font, font.plainSubstrByWidth(second, textWidth), textX, textY + 13, 0xffaaaaaa);
                } else {
                    g.text(font, font.plainSubstrByWidth(name.getString() + "  " + id, textWidth), textX, textY, 0xffffffff);
                }
                boolean overToggle = hovered && overToggle(mx);
                drawToggle(g, enabled, color, overToggle);
                if (hovered && overToggle) tooltip(g, Component.translatable("tooltip.betterxray.esp_item_toggle"), mx, my);
            }
        }
    }
}
