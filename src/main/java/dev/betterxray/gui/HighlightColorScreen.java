package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Highlight color of one block. The block follows the global highlight color
 * ("Global (…)") until a palette color or the RGB sliders give it its own; "Use Global Color"
 * removes that override again.
 */
public final class HighlightColorScreen extends Screen {
    private final Screen parent;
    private final String id;
    private int titleY;
    private Button colorButton;
    private Button globalButton;

    public HighlightColorScreen(Screen parent, Block block) {
        this(parent, BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName());
    }

    /** {@code id}: block ID or block tag ({@code #c:ores}); a tag's color applies to its blocks without an own color. */
    public HighlightColorScreen(Screen parent, String id, Component name) {
        super(Component.translatable("screen.betterxray.highlight_color", name));
        this.parent = parent;
        this.id = id;
    }

    private static XRayConfig config() { return XRayConfigManager.get(); }
    private int color() { return config().highlightColor(id); }
    private boolean inherits() { return !config().highlightBlockColors.containsKey(id); }
    private void setColor(int color) {
        config().highlightBlockColors.put(id, color | 0xff000000);
        XRayConfigManager.save();
    }

    private Component colorLabel() {
        Component name = MobEspSettingsScreen.colorName(color());
        return Component.translatable("option.betterxray.esp_color",
            inherits() ? Component.translatable("option.betterxray.highlight_inherit", name) : name);
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
        colorButton = Button.builder(colorLabel(), b -> {
            int current = color(), index = -1;
            for (int i = 0; i < MobEspSettingsScreen.COLORS.length; i++) if (MobEspSettingsScreen.COLORS[i] == current) index = i;
            setColor(MobEspSettingsScreen.COLORS[(index + 1) % MobEspSettingsScreen.COLORS.length]);
            rebuildWidgets();
        }).build();
        colorButton.setTooltip(Tooltip.create(Component.translatable("option.betterxray.esp_color_hint")));
        globalButton = Button.builder(Component.translatable("button.betterxray.highlight_use_global"), b -> {
            config().highlightBlockColors.remove(id);
            XRayConfigManager.save();
            rebuildWidgets();
        }).build();
        globalButton.setTooltip(Tooltip.create(Component.translatable("tooltip.betterxray.highlight_use_global")));
        globalButton.active = !inherits();
        int y = GuiLayout.row(0, 0, width, colorButton, globalButton) + metrics.rowGap;
        panel.add(colorButton); panel.add(globalButton);
        int initial = color();
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - 8 * channel, index = channel;
            var slider = new AbstractSliderButton(0, 0, width, GuiLayout.BUTTON_HEIGHT, Component.empty(),
                ((initial >>> shift) & 255) / 255.0) {
                { updateMessage(); }
                @Override protected void updateMessage() {
                    setMessage(Component.translatable("option.betterxray.color." + index, (int)Math.round(value * 255)));
                }
                @Override protected void applyValue() {
                    setColor((color() & ~(255 << shift)) | ((int)Math.round(value * 255) << shift));
                    colorButton.setMessage(colorLabel());
                    globalButton.active = !inherits();
                }
            };
            y = GuiLayout.row(0, y, width, slider) + metrics.rowGap;
            panel.add(slider);
        }
        var preview = new AbstractWidget(0, y, width, 16, Component.translatable("option.betterxray.highlight_preview")) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color());
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        };
        panel.add(preview);
        return panel;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, Component.literal(font.plainSubstrByWidth(title.getString(), Math.max(0, width - 16))), width / 2, titleY);
    }
    @Override public void onClose() { XRayConfigManager.save(); Mc.setScreen(minecraft, parent); }
}
