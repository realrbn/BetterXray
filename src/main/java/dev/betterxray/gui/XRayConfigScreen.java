package dev.betterxray.gui;

import dev.betterxray.compat.IrisCompat;
import dev.betterxray.compat.Mc;
import dev.betterxray.compat.RendererCompatibility;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main configuration screen.
 *
 * <p>The layout is chosen from the scaled screen size on every {@link #init()} (which also runs
 * after every resize or GUI-scale change):
 * <ol>
 *   <li>single column with normal spacing, when it fits with room to spare;</li>
 *   <li>two columns (display options | presets) on wide but short screens such as GUI scale
 *       "Auto" at 1080p/1440p, normal spacing first, then compact spacing;</li>
 *   <li>single column with compact spacing;</li>
 *   <li>the same without the footer note;</li>
 *   <li>otherwise the controls scroll between the fixed title and the fixed Done button.</li>
 * </ol>
 * When the panel fits it is centered as one compact block instead of spanning the full height.</p>
 */
public final class XRayConfigScreen extends Screen {
    private static final int SINGLE_COLUMN_MAX_WIDTH = 310;
    /** Each column of the two-column layout; narrower columns would truncate default preset names. */
    private static final int TWO_COLUMN_WIDTH = 200;
    private static final int COLUMN_GAP = 10;
    private static final int DONE_MAX_WIDTH = 200;
    /** Free space kept above and below the panel before a denser layout is preferred. */
    private static final int COMFORT = 24;

    private final Screen parent;
    private final Button[] presetButtons = new Button[XRayConfig.PRESET_COUNT];
    private int titleY;
    /** Bounds of the controls, drawn as one glass card ({@link GlassTheme}); null without the theme. */
    private int[] card;

    public XRayConfigScreen(Screen parent) { super(Component.translatable("screen.betterxray.title")); this.parent = parent; }
    private Component state(String label, boolean value) { return Component.translatable(label, Component.translatable(value ? "options.on" : "options.off")); }
    private static Component hideMobsLabel(XRayConfig.HideMobs mode) {
        return Component.translatable("option.betterxray.hide_mobs", Component.translatable(mode.key()));
    }

    @Override protected void init() {
        card = null;
        int available = width - 2 * GuiLayout.MARGIN;
        boolean twoColumns = 2 * TWO_COLUMN_WIDTH + COLUMN_GAP <= available;
        int singleWidth = Math.min(SINGLE_COLUMN_MAX_WIDTH, available);

        // Candidate layouts, from most spacious to most compact.
        if (tryPlace(false, singleWidth, GuiLayout.NORMAL, true, COMFORT)) return;
        if (twoColumns && tryPlace(true, TWO_COLUMN_WIDTH, GuiLayout.NORMAL, true, COMFORT)) return;
        if (twoColumns && tryPlace(true, TWO_COLUMN_WIDTH, GuiLayout.COMPACT, true, 0)) return;
        if (tryPlace(false, singleWidth, GuiLayout.COMPACT, true, 0)) return;
        // The note only repeats obvious behavior; drop it before resorting to scrolling.
        if (twoColumns && tryPlace(true, TWO_COLUMN_WIDTH, GuiLayout.COMPACT, false, 0)) return;
        if (tryPlace(false, singleWidth, GuiLayout.COMPACT, false, 0)) return;

        int scrollAvailable = available - 2 * GuiLayout.SCROLLBAR_RESERVE;
        boolean scrollTwoColumns = 2 * TWO_COLUMN_WIDTH + COLUMN_GAP <= scrollAvailable;
        placeScrolling(scrollTwoColumns, scrollTwoColumns ? TWO_COLUMN_WIDTH : Math.min(SINGLE_COLUMN_MAX_WIDTH, scrollAvailable));
    }

    /** Places title, controls, note and Done as one centered block if it fits; returns false otherwise. */
    private boolean tryPlace(boolean twoColumns, int columnWidth, GuiLayout m, boolean withNote, int comfort) {
        GuiLayout.Panel content = buildContent(twoColumns, columnWidth, m, false);
        int noteHeight = withNote ? font.lineHeight + m.labelGap + 2 : 0;
        int total = font.lineHeight + m.titleGap + content.getHeight() + m.sectionGap + noteHeight + GuiLayout.BUTTON_HEIGHT;
        if (total + 2 * Math.max(m.edge, comfort) > height) return false;

        titleY = Math.max(m.edge, (height - total) / 2);
        int y = titleY + font.lineHeight + m.titleGap;
        content.setPosition((width - content.getWidth()) / 2, y);
        content.visitWidgets(this::addRenderableWidget);
        setCard(content.getX(), y, content.getWidth(), content.getHeight());
        y += content.getHeight() + m.sectionGap;
        if (withNote) {
            addRenderableWidget(note(width / 2, y, width - 2 * GuiLayout.MARGIN));
            y += noteHeight;
        }
        addDone(y);
        return true;
    }

    /** Fallback for very small screens: fixed title and Done, scrollable controls in between. */
    private void placeScrolling(boolean twoColumns, int columnWidth) {
        GuiLayout m = GuiLayout.COMPACT;
        titleY = m.edge;
        int top = titleY + font.lineHeight + m.titleGap;
        int doneY = height - m.edge - GuiLayout.BUTTON_HEIGHT;
        int viewport = Math.max(GuiLayout.BUTTON_HEIGHT, doneY - m.sectionGap - top);
        GuiLayout.Panel content = buildContent(twoColumns, Math.max(40, columnWidth), m, true);
        //? if >=1.21.11 {
        var scroll = new ScrollableLayout(minecraft, content, viewport);
        // 1.21.11's constructor does not keep the max height; arrangeElements() would collapse the viewport to 0.
        //? if <26.1
        /*scroll.setMaxHeight(viewport);*/
        scroll.arrangeElements();
        //?} else {
        /*var scroll = new ScrollArea(content, viewport);
        *///?}
        scroll.setPosition((width - scroll.getWidth()) / 2, top);
        setCard(scroll.getX(), top, scroll.getWidth(), scroll.getHeight());
        //? if >=1.21.11 {
        scroll.visitWidgets(this::addRenderableWidget);
        //?} else {
        /*addRenderableWidget(scroll);
        *///?}
        addDone(doneY);
    }

    private void setCard(int x, int y, int w, int h) {
        if (!GlassTheme.ENABLED) return;
        int pad = 6;
        card = new int[] {Math.max(0, x - pad), Math.max(0, y - pad), Math.min(width, x + w + pad), Math.min(height, y + h + pad)};
    }

    private void addDone(int y) {
        int doneWidth = Math.min(DONE_MAX_WIDTH, width - 2 * GuiLayout.MARGIN);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds((width - doneWidth) / 2, y, doneWidth, GuiLayout.BUTTON_HEIGHT).build());
    }

    private StringWidget note(int centerX, int y, int maxWidth) {
        var text = Component.translatable("screen.betterxray.apply").withStyle(ChatFormatting.GRAY);
        //? if >=1.21.11 {
        var widget = new StringWidget(text, font).setMaxWidth(maxWidth);
        //?} else {
        /*var widget = GuiLayout.label(text, font, maxWidth);
        *///?}
        widget.setPosition(centerX - widget.getWidth() / 2, y);
        return widget;
    }

    /**
     * Creates all controls at positions relative to the returned panel.
     * Single column: display options, presets, highlight. Two columns: display options and
     * Highlight Blocks on the left, the preset section on the right.
     */
    private GuiLayout.Panel buildContent(boolean twoColumns, int columnWidth, GuiLayout m, boolean includeNote) {
        var c = XRayConfigManager.get();
        int panelWidth = twoColumns ? 2 * columnWidth + COLUMN_GAP : columnWidth;
        var panel = new GuiLayout.Panel(panelWidth);

        // Display options.
        var xray = Button.builder(state("option.betterxray.enabled", c.enabled), b -> {
            c.enabled = !c.enabled; b.setMessage(state("option.betterxray.enabled", c.enabled));
        }).build();
        var fullbright = Button.builder(state("option.betterxray.fullbright", c.fullbright), b -> {
            c.fullbright = !c.fullbright;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.fullbright", c.fullbright));
        }).build();
        // Cave-Find; applied with the other display options when the screen closes.
        var caveFind = Button.builder(state("option.betterxray.cave_find", c.caveFind), b -> {
            c.caveFind = !c.caveFind; b.setMessage(state("option.betterxray.cave_find", c.caveFind));
        }).tooltip(Tooltip.create(Component.translatable("tooltip.betterxray.cave_find"))).build();
        // Hide Mobs is read at render time, so it applies at once without a chunk rebuild.
        // Cycles Off -> Behind Walls -> All.
        var hideMobs = Button.builder(hideMobsLabel(c.hideMobs), b -> {
            c.hideMobs = c.hideMobs.next();
            XRayConfigManager.save();
            b.setMessage(hideMobsLabel(c.hideMobs));
        }).tooltip(Tooltip.create(Component.translatable("tooltip.betterxray.hide_mobs"))).build();
        var opacity = new AbstractSliderButton(0, 0, columnWidth, GuiLayout.BUTTON_HEIGHT, Component.empty(), c.terrainOpacity / .80) {
            // Under a shader pack that ignores the opacity (XRayState.faintUnsupported) the value stays
            // editable for later, but the label and tooltip say that it currently has no effect.
            {
                updateMessage();
                if (XRayState.faintUnsupported()) setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.opacity_shader")));
            }
            @Override protected void updateMessage() {
                setMessage(Component.translatable(XRayState.faintUnsupported() ? "option.betterxray.opacity_shader" : "option.betterxray.opacity",
                    Math.round(value * 80)));
            }
            @Override protected void applyValue() { c.terrainOpacity = Math.round(value * 80) / 100f; }
        };
        var water = Button.builder(state("option.betterxray.water", c.showWater), b -> {
            c.showWater = !c.showWater; b.setMessage(state("option.betterxray.water", c.showWater));
        }).build();
        var lava = Button.builder(state("option.betterxray.lava", c.showLava), b -> {
            c.showLava = !c.showLava; b.setMessage(state("option.betterxray.lava", c.showLava));
        }).build();

        // Presets.
        //? if >=1.21.11 {
        var presetLabel = new StringWidget(Component.translatable("screen.betterxray.preset").withStyle(ChatFormatting.GRAY), font)
            .setMaxWidth(columnWidth);
        //?} else {
        /*var presetLabel = GuiLayout.label(Component.translatable("screen.betterxray.preset").withStyle(ChatFormatting.GRAY), font, columnWidth);
        *///?}
        for (int i = 0; i < XRayConfig.PRESET_COUNT; i++) {
            int preset = i;
            presetButtons[i] = GuiLayout.presetButton(i, b -> {
                if (c.setActivePreset(preset)) XRayRenderController.apply();
                updatePresetButtons();
            }).build();
        }
        var blocks = Button.builder(Component.translatable("screen.betterxray.blocks"), b -> Mc.setScreen(minecraft, new BlockSelectionScreen(this))).build();
        var rename = Button.builder(Component.translatable("button.betterxray.rename_preset"), b ->
            Mc.setScreen(minecraft, new PresetRenameScreen(this, c.activePreset))).build();
        var reset = Button.builder(Component.translatable("button.betterxray.reset_preset"), b -> {
            c.resetPreset(c.activePreset);
            XRayRenderController.apply();
            updatePresetButtons();
        }).build();

        var highlight = Button.builder(Component.translatable("screen.betterxray.highlight"), b ->
            Mc.setScreen(minecraft, new HighlightScreen(this))).build();
        // Structure Finder shares the Highlight Blocks row.
        AbstractWidget[] highlightRow = {highlight, Button.builder(Component.translatable("screen.betterxray.structures"), b ->
            Mc.setScreen(minecraft, new StructureScreen(this))).build()};
        var esp = Button.builder(Component.translatable("screen.betterxray.esp"), b ->
            Mc.setScreen(minecraft, new MobEspScreen(this))).build();
        var updateCheck = Button.builder(state("option.betterxray.update_check", c.updateCheckEnabled), b -> {
            c.updateCheckEnabled = !c.updateCheckEnabled;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.update_check", c.updateCheckEnabled));
        }).tooltip(Tooltip.create(Component.translatable("tooltip.betterxray.update_check"))).build();

        // Experimental shader opacity patch (Iris on Fabric 26.3+ only). Takes effect after a shader reload.
        var shaderPatch = !IrisCompat.available() ? null : Button.builder(state("option.betterxray.shader_opacity", c.shaderOpacityPatch), b -> {
            c.shaderOpacityPatch = !c.shaderOpacityPatch;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.shader_opacity", c.shaderOpacityPatch));
            IrisCompat.reloadShaders();
            RendererCompatibility.refresh();
        }).tooltip(Tooltip.create(Component.translatable("tooltip.betterxray.shader_opacity"))).build();

        int left = 0;
        int right = twoColumns ? columnWidth + COLUMN_GAP : 0;
        int y = GuiLayout.row(left, 0, columnWidth, xray, fullbright) + m.rowGap;
        y = GuiLayout.row(left, y, columnWidth, caveFind, hideMobs) + m.rowGap;
        y = GuiLayout.row(left, y, columnWidth, opacity) + m.rowGap;
        y = GuiLayout.row(left, y, columnWidth, water, lava);
        int presetsY;
        if (twoColumns) {
            int bottom = GuiLayout.row(left, y + m.rowGap, columnWidth, highlightRow);
            GuiLayout.row(left, bottom + m.rowGap, columnWidth, esp, updateCheck);
            presetsY = 0;
        } else {
            presetsY = y + m.sectionGap;
        }
        presetLabel.setPosition(right + (columnWidth - presetLabel.getWidth()) / 2, presetsY);
        int py = GuiLayout.row(right, presetsY + font.lineHeight + m.labelGap, columnWidth, presetButtons) + m.rowGap;
        py = GuiLayout.row(right, py, columnWidth, blocks) + m.rowGap;
        py = GuiLayout.row(right, py, columnWidth, rename, reset);
        if (shaderPatch != null) {
            py = GuiLayout.row(right, py + m.rowGap, columnWidth, shaderPatch);
            panel.add(shaderPatch);
        }
        if (!twoColumns) {
            int bottom = GuiLayout.row(left, py + m.sectionGap, columnWidth, highlightRow);
            GuiLayout.row(left, bottom + m.rowGap, columnWidth, esp, updateCheck);
        }

        for (var widget : new AbstractWidget[] {xray, fullbright, caveFind, hideMobs, opacity, water, lava, presetLabel}) panel.add(widget);
        for (var widget : presetButtons) panel.add(widget);
        for (var widget : new AbstractWidget[] {blocks, rename, reset, esp, updateCheck}) panel.add(widget);
        for (var widget : highlightRow) panel.add(widget);
        if (includeNote) panel.add(note(panelWidth / 2, panel.getHeight() + m.sectionGap, panelWidth));
        updatePresetButtons();
        return panel;
    }

    private void updatePresetButtons() {
        for (int i = 0; i < presetButtons.length; i++) {
            if (presetButtons[i] != null) GuiLayout.updatePresetButton(presetButtons[i], font, i);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        if (card != null) GlassTheme.card(g, card[0], card[1], card[2], card[3]);
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, title, width / 2, titleY);
    }
    @Override public void onClose() { XRayRenderController.apply(); Mc.setScreen(minecraft, parent); }
}
