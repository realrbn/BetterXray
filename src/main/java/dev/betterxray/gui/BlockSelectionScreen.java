package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.*;
import dev.betterxray.render.XRayRenderController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Visible Blocks editor. The controls take a fixed amount of space at the top and bottom and
 * the block list fills all remaining height. On short screens spacing and row height shrink so
 * more blocks are visible at once.
 */
public final class BlockSelectionScreen extends Screen {
    static final int LIST_MAX_WIDTH = 480;
    private static final int FOOTER_MAX_WIDTH = 308;

    private final Screen parent;
    private final Button[] presetButtons = new Button[XRayConfig.PRESET_COUNT];
    private BlockListWidget list;
    private EditBox search;
    private String query = "";
    private boolean selectedOnly;
    private int titleY;

    public BlockSelectionScreen(Screen parent) { super(Component.translatable("screen.betterxray.blocks")); this.parent = parent; }

    @Override protected void init() {
        var config = XRayConfigManager.get();
        int contentWidth = GuiLayout.contentWidth(width, LIST_MAX_WIDTH);
        int left = (width - contentWidth) / 2;
        int controlsHeight = GuiLayout.BUTTON_HEIGHT + GuiLayout.NORMAL.rowGap; // preset row
        GuiLayout m = ListScreens.metrics(height, font, controlsHeight);
        titleY = ListScreens.titleY(m);
        int y = ListScreens.controlsTop(m, font);

        for (int i = 0; i < XRayConfig.PRESET_COUNT; i++) {
            int preset = i;
            presetButtons[i] = addRenderableWidget(GuiLayout.presetButton(i, b -> {
                if (config.setActivePreset(preset)) XRayRenderController.apply();
                updatePresetButtons();
                refresh();
            }).build());
        }
        y = GuiLayout.row(left, y, contentWidth, presetButtons) + m.rowGap;
        updatePresetButtons();

        search = new EditBox(font, 0, 0, 0, GuiLayout.BUTTON_HEIGHT, Component.translatable("screen.betterxray.search_blocks"));
        search.setHint(Component.translatable("screen.betterxray.search_blocks"));
        search.setMaxLength(200);
        search.setValue(query);
        var filter = Button.builder(filterLabel(), b -> {
            selectedOnly = !selectedOnly; b.setMessage(filterLabel()); refresh();
        }).build();
        y = ListScreens.searchRow(left, y, contentWidth, search, filter) + ListScreens.listGap(m);
        addRenderableWidget(search);
        addRenderableWidget(filter);

        int footerY = ListScreens.footerY(m, height);
        list = addRenderableWidget(new BlockListWidget(minecraft, contentWidth, Math.max(0, footerY - ListScreens.listGap(m) - y), y,
            this::selectionChanged, false, ListScreens.rowHeight(m)));
        list.placeAt(left);
        search.setResponder(text -> { query = text; refresh(); });
        refresh();

        var reset = Button.builder(Component.translatable("button.betterxray.reset_preset"), b -> {
            config.resetPreset(config.activePreset);
            XRayRenderController.apply();
            rebuildWidgets();
        }).build();
        var done = Button.builder(Component.translatable("gui.done"), b -> onClose()).build();
        int footerWidth = Math.min(FOOTER_MAX_WIDTH, contentWidth);
        GuiLayout.row((width - footerWidth) / 2, footerY, footerWidth, reset, done);
        addRenderableWidget(reset);
        addRenderableWidget(done);
        setInitialFocus(search);
    }

    private void updatePresetButtons() {
        for (int i = 0; i < presetButtons.length; i++) GuiLayout.updatePresetButton(presetButtons[i], font, i);
    }
    private void selectionChanged() { XRayRenderController.apply(); refresh(); }
    private Component filterLabel() { return Component.translatable(selectedOnly ? "button.betterxray.selected" : "button.betterxray.all"); }
    private void refresh() { if (list != null) list.filter(query, selectedOnly); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, title, width / 2, titleY);
    }
    @Override public void onClose() { Mc.setScreen(minecraft, parent); }
}
