package dev.betterxray.gui;

import net.minecraft.util.Mth;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
//? if >=1.21.11 {
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}
import java.util.function.IntConsumer;

/**
 * Range slider in blocks. The positions are {@link XRayConfig#RANGE_STEP_BLOCKS} apart; the last
 * position stands for zero, the special "no own limit" value (render distance or unlimited).
 */
final class RangeSlider extends AbstractSliderButton {
    private final int finiteSteps;
    private final String key;
    private final Component special;
    private final IntConsumer apply;

    /**
     * @param finiteSteps number of finite positions: step, 2·step, … finiteSteps·step blocks
     * @param key         translation key with one argument, the block count
     * @param special     label argument shown at the last position (stored as zero)
     */
    private RangeSlider(int finiteSteps, int current, String key, Component special, IntConsumer apply) {
        super(0, 0, 0, GuiLayout.BUTTON_HEIGHT, Component.empty(), position(current, finiteSteps));
        this.finiteSteps = finiteSteps;
        this.key = key;
        this.special = special;
        this.apply = apply;
        updateMessage();
    }

    private static double position(int blocks, int finiteSteps) {
        if (blocks <= 0) return 1.0;
        int step = Mth.clamp(Math.round(blocks / (float)XRayConfig.RANGE_STEP_BLOCKS), 1, finiteSteps);
        return (step - 1.0) / finiteSteps;
    }

    /** Selected range in blocks, or zero for the special last position. */
    int selectedBlocks() {
        int step = (int)Math.round(value * finiteSteps);
        return step >= finiteSteps ? 0 : (step + 1) * XRayConfig.RANGE_STEP_BLOCKS;
    }

    @Override protected void updateMessage() {
        int blocks = selectedBlocks();
        setMessage(Component.translatable(key, blocks == 0 ? special
            : Component.translatable("option.betterxray.range_blocks", blocks)));
    }

    //? if >=1.21.11 {
    //?} else {
    /*/^*
     * Arrow keys move one range step (8 blocks) at a time. Minecraft 1.21.1 moves sliders by one
     * pixel per key press, which would need dozens of presses to change a discrete range.
     ^/
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        double before = value;
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        boolean arrow = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT;
        if (handled && arrow && value != before) {
            int step = (int)Math.round(before * finiteSteps) + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            value = net.minecraft.util.Mth.clamp(step, 0, finiteSteps) / (double)finiteSteps;
            applyValue();
            updateMessage();
        }
        return handled;
    }

    *///?}
    @Override protected void applyValue() {
        apply.accept(selectedBlocks());
        XRayConfigManager.save();
    }

    /** Highlight range from 8 blocks up to just below the render distance, then "Render Distance". */
    static RangeSlider highlight(int renderDistance) {
        var config = XRayConfigManager.get();
        int maximum = Math.max(1, renderDistance) * 16;
        int steps = Math.max(1, maximum / XRayConfig.RANGE_STEP_BLOCKS - 1);
        int current = config.highlightRangeLimited(renderDistance) ? config.highlightRangeBlocks : 0;
        return new RangeSlider(steps, current, "option.betterxray.range",
            Component.translatable("option.betterxray.range_render_distance", maximum),
            blocks -> config.highlightRangeBlocks = blocks);
    }

    /** ESP range from 8 to 512 blocks, then "Unlimited". */
    static RangeSlider esp() {
        var config = XRayConfigManager.get();
        return new RangeSlider(XRayConfig.MAX_RANGE_BLOCKS / XRayConfig.RANGE_STEP_BLOCKS, config.mobEspRangeBlocks,
            "option.betterxray.esp_range", Component.translatable("option.betterxray.range_unlimited"),
            blocks -> config.mobEspRangeBlocks = blocks);
    }
}
