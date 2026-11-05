package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import java.util.HashSet;
import java.util.Set;

/**
 * ESP settings of either one {@link MobEspGroup} (defaults for all its mobs), one mob type or one
 * dropped item. A mob's enable switch, style and color can each follow its group
 * ("Group (…)") or override it; an item follows the dropped-item entity ("Item (…)") the same way.
 */
public final class MobEspSettingsScreen extends Screen {
    static final int[] COLORS = {0xff00ffff, 0xffff5555, 0xff55ff55, 0xff5555ff,
        0xffffff55, 0xffff55ff, 0xffffaa00, 0xffffffff, 0xffaaaaaa, 0xff000000};
    private static final String[] COLOR_NAMES = {"cyan", "red", "green", "blue", "yellow", "magenta", "orange", "white", "gray", "black"};
    private final Screen parent;
    private final MobEspGroup group;
    /** Entity (or item) registry ID, or {@code null} when this screen edits the group itself. */
    private final String id;
    /** True when {@link #id} is an item ID; its overrides live in {@code itemEsp}. */
    private final boolean item;
    private int titleY;
    private Button colorButton;
    private Button groupColorButton;
    private final AbstractSliderButton[] channels = new AbstractSliderButton[3];

    public MobEspSettingsScreen(Screen parent, EntityType<?> type) {
        super(Component.translatable("screen.betterxray.esp_mob", type.getDescription()));
        this.parent = parent;
        id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        group = MobEspRenderer.group(type);
        item = false;
    }

    public MobEspSettingsScreen(Screen parent, Item item) {
        super(Component.translatable("screen.betterxray.esp_mob", Component.translatable(item.getDescriptionId())));
        this.parent = parent;
        id = MobEspRenderer.itemId(item);
        group = MobEspGroup.OBJECTS;
        this.item = true;
    }

    public MobEspSettingsScreen(Screen parent, MobEspGroup group) {
        super(Component.translatable("screen.betterxray.esp_group", Component.translatable(group.translationKey())));
        this.parent = parent;
        this.group = group;
        id = null;
        item = false;
    }

    public boolean editsGroup() { return id == null; }
    public boolean editsItem() { return item; }

    private static XRayConfig config() { return XRayConfigManager.get(); }
    private XRayConfig.MobEspGroupSettings groupSettings() { return config().espGroup(group); }
    private java.util.Map<String, XRayConfig.MobEspSettings> overrides() { return item ? config().itemEsp : config().mobEsp; }
    /** The mob's or item's override entry, created on first change and removed again when empty. */
    private XRayConfig.MobEspSettings own() {
        return overrides().computeIfAbsent(id, ignored -> new XRayConfig.MobEspSettings());
    }
    private XRayConfig.MobEspSettings ownOrNull() { return overrides().get(id); }
    private void changed() {
        if (id != null) {
            var own = ownOrNull();
            if (own != null && own.isEmpty()) overrides().remove(id);
        }
        XRayConfigManager.save();
    }

    private boolean enabled() {
        return id == null ? groupSettings().enabled : item ? config().itemEspEnabled(id) : config().espEnabled(id, group);
    }
    private XRayConfig.EspStyle style() {
        return id == null ? groupSettings().style : item ? config().itemEspStyle(id) : config().espStyle(id, group);
    }
    private int color() {
        return id == null ? groupSettings().color | 0xff000000 : item ? config().itemEspColor(id) : config().espColor(id, group);
    }
    private boolean tracer() {
        return id == null ? groupSettings().tracer : item ? config().itemEspTracer(id) : config().espTracer(id, group);
    }
    /** What an override falls back to: the group for a mob, the dropped-item entity for an item. */
    private boolean parentEnabled() {
        return item ? config().espEnabled(XRayConfig.ITEM_ENTITY, MobEspGroup.OBJECTS) : groupSettings().enabled;
    }
    private boolean parentTracer() {
        return item ? config().espTracer(XRayConfig.ITEM_ENTITY, MobEspGroup.OBJECTS) : groupSettings().tracer;
    }
    private XRayConfig.EspStyle parentStyle() {
        return item ? config().espStyle(XRayConfig.ITEM_ENTITY, MobEspGroup.OBJECTS) : groupSettings().style;
    }
    private boolean inheritsEnabled() { var own = id == null ? null : ownOrNull(); return id != null && (own == null || own.enabled == null); }
    private boolean inheritsStyle() { var own = id == null ? null : ownOrNull(); return id != null && (own == null || own.style == null); }
    private boolean inheritsTracer() { var own = id == null ? null : ownOrNull(); return id != null && (own == null || own.tracer == null); }
    private boolean inheritsColor() { var own = id == null ? null : ownOrNull(); return id != null && (own == null || own.color == null); }

    public static Component enabledLabel(boolean enabled) {
        return Component.translatable("option.betterxray.esp_enabled", yesNo(enabled));
    }
    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "option.betterxray.yes" : "option.betterxray.no");
    }
    private Component inherited(Component value) {
        return Component.translatable(item ? "option.betterxray.esp_inherit_item" : "option.betterxray.esp_inherit", value);
    }
    private static Component styleName(XRayConfig.EspStyle style) {
        return Component.translatable(style == XRayConfig.EspStyle.BOX ? "option.betterxray.esp_box" : "option.betterxray.esp_outline");
    }
    static Component colorName(int color) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == (color | 0xff000000))
            return Component.translatable("color.betterxray." + COLOR_NAMES[i]);
        return Component.literal(String.format("#%06X", color & 0xffffff));
    }

    private Component enabledButtonLabel() {
        return Component.translatable("option.betterxray.esp_enabled", inheritsEnabled() ? inherited(yesNo(enabled())) : yesNo(enabled()));
    }
    private Component styleLabel() {
        return Component.translatable("option.betterxray.esp_style", inheritsStyle() ? inherited(styleName(style())) : styleName(style()));
    }
    private Component tracerLabel() {
        return Component.translatable("option.betterxray.esp_tracer", inheritsTracer() ? inherited(yesNo(tracer())) : yesNo(tracer()));
    }
    private static Component nameLabel() {
        return Component.translatable("option.betterxray.esp_player_name", yesNo(config().playerEspName));
    }
    private static Component healthLabel() {
        return Component.translatable("option.betterxray.esp_player_health",
            Component.translatable("option.betterxray.esp_health." + config().playerEspHealth.name().toLowerCase(java.util.Locale.ROOT)));
    }
    private Component colorLabel() {
        return Component.translatable("option.betterxray.esp_color", inheritsColor() ? inherited(colorName(color())) : colorName(color()));
    }

    /** Mob overrides of this group's registered mobs (unknown IDs of absent mods are left alone). */
    private Set<String> groupMobIds() {
        Set<String> ids = new HashSet<>();
        for (var type : BuiltInRegistries.ENTITY_TYPE)
            if (MobEspRenderer.isEspType(type) && MobEspRenderer.group(type) == group) ids.add(MobEspRenderer.id(type));
        return ids;
    }
    private int overrideCount() {
        Set<String> ids = groupMobIds();
        int count = 0;
        for (var entry : config().mobEsp.entrySet()) if (ids.contains(entry.getKey()) && !entry.getValue().isEmpty()) count++;
        return count;
    }

    @Override protected void init() {
        int panelWidth = GuiLayout.contentWidth(width, 310);
        var metrics = GuiLayout.COMPACT;
        titleY = ListScreens.titleY(metrics);
        var panel = buildControls(panelWidth, metrics);
        int top = ListScreens.controlsTop(metrics, font);
        int doneY = ListScreens.footerY(metrics, height);
        int space = Math.max(GuiLayout.BUTTON_HEIGHT, doneY - metrics.sectionGap - top);
        if (panel.getHeight() <= space) {
            panel.setPosition((width - panelWidth) / 2, top);
            panel.visitWidgets(this::addRenderableWidget);
        } else {
            panel = buildControls(Math.max(40, panelWidth - 2 * GuiLayout.SCROLLBAR_RESERVE), metrics);
            //? if >=1.21.11 {
            var scroll = new ScrollableLayout(minecraft, panel, space);
            // 1.21.11's constructor does not keep the max height; arrangeElements() would collapse the viewport to 0.
            //? if <26.1
            /*scroll.setMaxHeight(space);*/
            scroll.arrangeElements();
            //?} else {
            /*var scroll = new ScrollArea(panel, space);
            *///?}
            scroll.setPosition((width - scroll.getWidth()) / 2, top);
            //? if >=1.21.11 {
            scroll.visitWidgets(this::addRenderableWidget);
            //?} else {
            /*addRenderableWidget(scroll);
            *///?}
        }
        int doneWidth = Math.min(200, panelWidth);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds((width - doneWidth) / 2, doneY, doneWidth, GuiLayout.BUTTON_HEIGHT).build());
    }

    private GuiLayout.Panel buildControls(int width, GuiLayout metrics) {
        var panel = new GuiLayout.Panel(width);
        int y = 0;
        if (item) {
            var itemLink = Button.builder(Component.translatable("button.betterxray.esp_item_link"),
                b -> Mc.setScreen(minecraft, new MobEspSettingsScreen(this, Mc.entityType(XRayConfig.ITEM_ENTITY)))).build();
            itemLink.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_item_link")));
            y = GuiLayout.row(0, y, width, itemLink) + metrics.rowGap;
            panel.add(itemLink);
        } else if (id != null) {
            var groupLink = Button.builder(Component.translatable("button.betterxray.esp_group_link", Component.translatable(group.translationKey())),
                b -> Mc.setScreen(minecraft, new MobEspSettingsScreen(this, group))).build();
            groupLink.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_group_link")));
            y = GuiLayout.row(0, y, width, groupLink) + metrics.rowGap;
            panel.add(groupLink);
        }
        String inheritTooltip = item ? "tooltip.betterxray.esp_item_inherit" : "tooltip.betterxray.esp_mob_inherit";
        var enabled = Button.builder(enabledButtonLabel(), b -> {
            cycleEnabled();
            b.setMessage(enabledButtonLabel());
        }).build();
        var style = Button.builder(styleLabel(), b -> {
            cycleStyle();
            b.setMessage(styleLabel());
        }).build();
        if (id == null) enabled.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_group_enabled")));
        else {
            enabled.setTooltip(Tooltip.create(Component.translatable(inheritTooltip)));
            style.setTooltip(Tooltip.create(Component.translatable(inheritTooltip)));
        }
        colorButton = Button.builder(colorLabel(), b -> {
            int current = color(), index = -1;
            for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == current) index = i;
            setColor(COLORS[(index + 1) % COLORS.length]);
            rebuildWidgets();
        }).build();
        colorButton.setTooltip(Tooltip.create(Component.translatable("option.betterxray.esp_color_hint")));
        y = GuiLayout.row(0, y, width, enabled) + metrics.rowGap;
        y = GuiLayout.row(0, y, width, style) + metrics.rowGap;
        panel.add(enabled); panel.add(style);
        var tracer = Button.builder(tracerLabel(), b -> {
            cycleTracer();
            b.setMessage(tracerLabel());
        }).build();
        tracer.setTooltip(Tooltip.create(Component.translatable(id == null
            ? "tooltip.betterxray.esp_group_tracer" : inheritTooltip)));
        y = GuiLayout.row(0, y, width, tracer) + metrics.rowGap;
        panel.add(tracer);
        if (group == MobEspGroup.PLAYERS) {
            // Name and health labels apply to all players (group screen and player screen alike).
            var name = Button.builder(nameLabel(), b -> {
                config().playerEspName = !config().playerEspName;
                XRayConfigManager.save();
                b.setMessage(nameLabel());
            }).build();
            name.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_player_name")));
            var health = Button.builder(healthLabel(), b -> {
                config().playerEspHealth = config().playerEspHealth.next();
                XRayConfigManager.save();
                b.setMessage(healthLabel());
            }).build();
            health.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_player_health")));
            y = GuiLayout.row(0, y, width, name, health) + metrics.rowGap;
            panel.add(name); panel.add(health);
        }
        if (id != null) {
            groupColorButton = Button.builder(Component.translatable(item
                ? "button.betterxray.esp_use_item_color" : "button.betterxray.esp_use_group_color"), b -> {
                var own = ownOrNull();
                if (own != null) own.color = null;
                changed();
                rebuildWidgets();
            }).build();
            groupColorButton.active = !inheritsColor();
            y = GuiLayout.row(0, y, width, colorButton, groupColorButton) + metrics.rowGap;
            panel.add(colorButton); panel.add(groupColorButton);
        } else {
            y = GuiLayout.row(0, y, width, colorButton) + metrics.rowGap;
            panel.add(colorButton);
        }
        int initial = color();
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - 8 * channel, index = channel;
            channels[channel] = new AbstractSliderButton(0, 0, width, GuiLayout.BUTTON_HEIGHT, Component.empty(),
                ((initial >>> shift) & 255) / 255.0) {
                { updateMessage(); }
                @Override protected void updateMessage() {
                    setMessage(Component.translatable("option.betterxray.color." + index, (int)Math.round(value * 255)));
                }
                @Override protected void applyValue() {
                    setColor((color() & ~(255 << shift)) | ((int)Math.round(value * 255) << shift));
                    if (colorButton != null) colorButton.setMessage(colorLabel());
                    if (groupColorButton != null) groupColorButton.active = !inheritsColor();
                }
            };
            y = GuiLayout.row(0, y, width, channels[channel]) + metrics.rowGap;
            panel.add(channels[channel]);
        }
        var preview = new AbstractWidget(0, y, width, 16, Component.translatable("option.betterxray.esp_preview")) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color());
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        };
        panel.add(preview);
        y += 16;
        if (id == null) {
            int overrides = overrideCount();
            var reset = Button.builder(Component.translatable("button.betterxray.esp_reset_overrides", overrides), b -> {
                Set<String> ids = groupMobIds();
                config().clearEspOverrides(ids::contains);
                XRayConfigManager.save();
                rebuildWidgets();
            }).build();
            reset.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_reset_overrides")));
            reset.active = overrides > 0;
            GuiLayout.row(0, y + metrics.sectionGap, width, reset);
            panel.add(reset);
        } else if (!item && id.equals(XRayConfig.ITEM_ENTITY)) {
            var pick = Button.builder(Component.translatable("button.betterxray.esp_pick_items"),
                b -> Mc.setScreen(minecraft, MobEspScreen.items(this))).build();
            pick.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_pick_items")));
            y = GuiLayout.row(0, y + metrics.sectionGap, width, pick);
            panel.add(pick);
            int overrides = config().itemEsp.size();
            var reset = Button.builder(Component.translatable("button.betterxray.esp_reset_item_overrides", overrides), b -> {
                config().itemEsp.clear();
                XRayConfigManager.save();
                rebuildWidgets();
            }).build();
            reset.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.esp_reset_item_overrides")));
            reset.active = overrides > 0;
            GuiLayout.row(0, y + metrics.sectionGap, width, reset);
            panel.add(reset);
        }
        return panel;
    }

    /** Group: on/off. Mob: Group → opposite of the group → same as the group (explicit) → Group. */
    private void cycleEnabled() {
        if (id == null) groupSettings().enabled = !groupSettings().enabled;
        else {
            var own = own();
            boolean groupValue = parentEnabled();
            own.enabled = own.enabled == null ? Boolean.valueOf(!groupValue) : own.enabled != groupValue ? Boolean.valueOf(groupValue) : null;
        }
        changed();
    }

    /** Same cycle as {@link #cycleEnabled()}: Group → opposite → same as the group → Group. */
    private void cycleTracer() {
        if (id == null) groupSettings().tracer = !groupSettings().tracer;
        else {
            var own = own();
            boolean groupValue = parentTracer();
            own.tracer = own.tracer == null ? Boolean.valueOf(!groupValue) : own.tracer != groupValue ? Boolean.valueOf(groupValue) : null;
        }
        changed();
    }

    private void cycleStyle() {
        if (id == null) groupSettings().style = other(groupSettings().style);
        else {
            var own = own();
            var groupValue = parentStyle();
            own.style = own.style == null ? other(groupValue) : own.style != groupValue ? groupValue : null;
        }
        changed();
    }

    private static XRayConfig.EspStyle other(XRayConfig.EspStyle style) {
        return style == XRayConfig.EspStyle.BOX ? XRayConfig.EspStyle.OUTLINE : XRayConfig.EspStyle.BOX;
    }

    private void setColor(int color) {
        if (id == null) groupSettings().color = color | 0xff000000;
        else own().color = color | 0xff000000;
        changed();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, Component.literal(font.plainSubstrByWidth(title.getString(), Math.max(0, width - 16))), width / 2, titleY);
    }
    @Override public void onClose() { XRayConfigManager.save(); Mc.setScreen(minecraft, parent); }
}
