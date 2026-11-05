package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PresetRenameScreen extends Screen {
    private final Screen parent;
    private final int presetIndex;
    private EditBox name;

    public PresetRenameScreen(Screen parent, int presetIndex) {
        super(Component.translatable("screen.betterxray.rename_preset"));
        this.parent = parent;
        this.presetIndex = presetIndex;
    }

    @Override protected void init() {
        int fieldWidth = GuiLayout.contentWidth(width, 300);
        int x = (width - fieldWidth) / 2;
        name = new EditBox(font, x, height / 2 - 24, fieldWidth, GuiLayout.BUTTON_HEIGHT, title);
        name.setMaxLength(XRayConfig.MAX_PRESET_NAME_LENGTH);
        name.setValue(XRayConfigManager.get().presets.get(presetIndex).name);
        addRenderableWidget(name);
        var done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).build());
        var cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).build());
        GuiLayout.row(x, height / 2 + 4, fieldWidth, done, cancel);
        setInitialFocus(name);
    }

    private void save() {
        String value = name.getValue().trim();
        if (!value.isEmpty()) {
            XRayConfigManager.get().presets.get(presetIndex).name = value;
            XRayConfigManager.save();
        }
        onClose();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, title, width / 2, height / 2 - 48);
    }

    @Override public void onClose() { Mc.setScreen(minecraft, parent); }
}
