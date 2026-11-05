package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.HighlightCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
//? if >=1.21.11 {
import net.minecraft.client.gui.components.ScrollableLayout;
//?}
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Highlight settings and highlighted-block editor. Controls sit at the top, the block list fills
 * all remaining height. On short screens spacing and list rows shrink; on short and wide screens
 * the range slider joins the toggle row. If a window is too small even for that, the controls
 * above the search field scroll so the list, search and Done button stay usable.
 */
public final class HighlightScreen extends Screen {
    private static final int DONE_MAX_WIDTH = 200;
    /** Minimum width of each cell when the toggles and range slider share one row. */
    private static final int MERGED_CELL_MIN_WIDTH = 150;

    private final Screen parent;
    private BlockListWidget list;
    private EditBox search;
    private String query = "";
    private boolean selectedOnly;
    /** List position kept while a block's color screen is open. */
    private double scroll;
    private int titleY;

    public HighlightScreen(Screen parent) {
        super(Component.translatable("screen.betterxray.highlight"));
        this.parent = parent;
    }

    private Component state(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override protected void init() {
        int contentWidth = GuiLayout.contentWidth(width, BlockSelectionScreen.LIST_MAX_WIDTH);
        int left = (width - contentWidth) / 2;

        // Decide spacing from the normal-spacing control height (toggles, color label, RGB, range).
        int normalControls = buildControls(contentWidth, GuiLayout.NORMAL).getHeight() + GuiLayout.NORMAL.rowGap;
        GuiLayout m = ListScreens.metrics(height, font, normalControls);
        titleY = ListScreens.titleY(m);
        int top = ListScreens.controlsTop(m, font);
        int footerY = ListScreens.footerY(m, height);
        int minList = ListScreens.rowHeight(m) + 4;
        int searchHeight = GuiLayout.BUTTON_HEIGHT + ListScreens.listGap(m);
        int controlsSpace = footerY - ListScreens.listGap(m) - minList - searchHeight - m.rowGap - top;

        GuiLayout.Panel controls = buildControls(contentWidth, m);
        int y;
        if (controls.getHeight() <= controlsSpace) {
            controls.setPosition(left, top);
            controls.visitWidgets(this::addRenderableWidget);
            y = top + controls.getHeight() + m.rowGap;
        } else {
            // Tiny window: scroll the settings, keep search, list and Done in place.
            controls = buildControls(Math.max(40, contentWidth - 2 * GuiLayout.SCROLLBAR_RESERVE), m);
            //? if >=1.21.11 {
            var scroll = new ScrollableLayout(minecraft, controls, Math.max(GuiLayout.BUTTON_HEIGHT, controlsSpace));
            // 1.21.11's constructor does not keep the max height; arrangeElements() would collapse the viewport to 0.
            //? if <26.1
            /*scroll.setMaxHeight(Math.max(GuiLayout.BUTTON_HEIGHT, controlsSpace));*/
            scroll.arrangeElements();
            //?} else {
            /*var scroll = new ScrollArea(controls, Math.max(GuiLayout.BUTTON_HEIGHT, controlsSpace));
            *///?}
            scroll.setPosition((width - scroll.getWidth()) / 2, top);
            //? if >=1.21.11 {
            scroll.visitWidgets(this::addRenderableWidget);
            //?} else {
            /*addRenderableWidget(scroll);
            *///?}
            y = top + scroll.getHeight() + m.rowGap;
        }

        search = new EditBox(font, 0, 0, 0, GuiLayout.BUTTON_HEIGHT, Component.translatable("screen.betterxray.search_blocks"));
        search.setHint(Component.translatable("screen.betterxray.search_blocks"));
        search.setMaxLength(200);
        search.setValue(query);
        var filter = Button.builder(filterLabel(), b -> {
            selectedOnly = !selectedOnly;
            b.setMessage(filterLabel());
            refresh();
        }).build();
        y = ListScreens.searchRow(left, y, contentWidth, search, filter) + ListScreens.listGap(m);
        addRenderableWidget(search);
        addRenderableWidget(filter);

        list = addRenderableWidget(new BlockListWidget(minecraft, contentWidth, Math.max(0, footerY - ListScreens.listGap(m) - y),
            y, this::selectionChanged, true, ListScreens.rowHeight(m),
            this::openColor));
        list.placeAt(left);
        search.setResponder(value -> { query = value; refresh(); });
        refresh();
        list.setScrollAmount(scroll);
        int doneWidth = Math.min(DONE_MAX_WIDTH, contentWidth);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds((width - doneWidth) / 2, footerY, doneWidth, GuiLayout.BUTTON_HEIGHT).build());
        setInitialFocus(search);
    }

    /** Creates the highlight settings (toggles, color, range) positioned relative to the returned panel. */
    private GuiLayout.Panel buildControls(int width, GuiLayout m) {
        var config = XRayConfigManager.get();
        var panel = new GuiLayout.Panel(width);
        var highlight = Button.builder(state("option.betterxray.highlight", config.highlightEnabled), b -> {
            config.highlightEnabled = !config.highlightEnabled;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.highlight", config.highlightEnabled));
        }).build();
        var tracers = Button.builder(state("option.betterxray.tracers", config.tracersEnabled), b -> {
            config.tracersEnabled = !config.tracersEnabled;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.tracers", config.tracersEnabled));
        }).build();
        var toggles = new java.util.ArrayList<AbstractWidget>(java.util.List.of(highlight, tracers));
        var toggleKeys = new java.util.ArrayList<>(java.util.List.of("option.betterxray.highlight", "option.betterxray.tracers"));
        toggleKeys.add("option.betterxray.group_veins");
        var veins = Button.builder(state("option.betterxray.group_veins", config.groupVeins), b -> {
            config.groupVeins = !config.groupVeins;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.group_veins", config.groupVeins));
        }).build();
        veins.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.betterxray.group_veins")));
        toggles.add(veins);
        var channels = new AbstractWidget[3];
        for (int channel = 0; channel < 3; channel++) {
            final int index = channel;
            int shift = 16 - 8 * channel;
            int value = (config.highlightColor >> shift) & 255;
            channels[channel] = new AbstractSliderButton(0, 0, 0, GuiLayout.BUTTON_HEIGHT, Component.empty(), value / 255.0) {
                { updateMessage(); }
                @Override protected void updateMessage() {
                    setMessage(Component.translatable("option.betterxray.color." + index, (int)Math.round(this.value * 255)));
                }
                @Override protected void applyValue() {
                    int component = (int)Math.round(this.value * 255);
                    config.highlightColor = (config.highlightColor & ~(255 << shift)) | (component << shift);
                    XRayConfigManager.save();
                }
            };
        }
        var range = RangeSlider.highlight(minecraft.options.renderDistance().get());
        range.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.betterxray.range")));
        var colorLabel = new ColorLabel(width);
        // Per-block colors: the reset button shares the range row.
        var extras = new java.util.ArrayList<AbstractWidget>();
        var reset = Button.builder(resetColorsLabel(), b -> {
            config.highlightBlockColors.clear();
            XRayConfigManager.save();
            b.setMessage(resetColorsLabel());
            b.active = false;
        }).build();
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.betterxray.highlight_reset_colors")));
        reset.active = !config.highlightBlockColors.isEmpty();
        extras.add(reset);

        // More than two toggles share one row only while every label fits; otherwise the extra ones get their own row.
        int toggleCell = (width - (toggles.size() - 1) * GuiLayout.CELL_GAP) / toggles.size();
        boolean splitToggles = toggles.size() > 2
            && !toggleKeys.stream().allMatch(key -> font.width(state(key, false)) + 8 <= toggleCell);
        var firstRow = new java.util.ArrayList<>(splitToggles ? toggles.subList(0, 2) : toggles);
        var rangeRow = new java.util.ArrayList<AbstractWidget>(java.util.List.of(range));
        rangeRow.addAll(extras);
        int cells = firstRow.size() + rangeRow.size();
        boolean mergeRange = !splitToggles && m.compact && (width - (cells - 1) * GuiLayout.CELL_GAP) / cells >= MERGED_CELL_MIN_WIDTH;
        if (mergeRange) firstRow.addAll(rangeRow);
        int y = GuiLayout.row(0, 0, width, firstRow.toArray(AbstractWidget[]::new));
        if (splitToggles) y = GuiLayout.row(0, y + m.rowGap, width, toggles.subList(2, toggles.size()).toArray(AbstractWidget[]::new));
        colorLabel.setPosition(0, y + m.rowGap);
        y = GuiLayout.row(0, y + m.rowGap + font.lineHeight + m.labelGap, width, channels);
        if (!mergeRange) GuiLayout.row(0, y + m.rowGap, width, rangeRow.toArray(AbstractWidget[]::new));

        for (var toggle : toggles) panel.add(toggle);
        panel.add(colorLabel);
        for (var slider : channels) panel.add(slider);
        panel.add(range);
        for (var extra : extras) panel.add(extra);
        return panel;
    }

    private static Component resetColorsLabel() {
        return Component.translatable("button.betterxray.highlight_reset_colors", XRayConfigManager.get().highlightBlockColors.size());
    }
    private void openColor(String id, Component name) {
        scroll = GuiLayout.scrollAmount(list);
        Mc.setScreen(minecraft, new HighlightColorScreen(this, id, name));
    }

    private void selectionChanged() { XRayConfigManager.save(); refresh(); }
    private Component filterLabel() {
        return Component.translatable(selectedOnly ? "button.betterxray.selected" : "button.betterxray.all");
    }
    private void refresh() { if (list != null) list.filter(query, selectedOnly); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, title, width / 2, titleY);
    }
    @Override public void onClose() { XRayConfigManager.save(); Mc.setScreen(minecraft, parent); }

    /** "Highlight Color #RRGGBB", drawn centered in the current highlight color; updates live. */
    private final class ColorLabel extends AbstractWidget {
        ColorLabel(int width) {
            super(0, 0, width, font.lineHeight, Component.translatable("option.betterxray.color"));
            active = false;
        }
        private Component text() {
            return Component.translatable("option.betterxray.color").append(
                Component.literal(String.format(" #%06X", XRayConfigManager.get().highlightColor & 0xffffff)));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.centeredText(font, text(), getX() + getWidth() / 2, getY(), XRayConfigManager.get().highlightColor | 0xff000000);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
}
