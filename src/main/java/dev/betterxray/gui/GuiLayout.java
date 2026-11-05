package dev.betterxray.gui;

import dev.betterxray.config.XRayConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
//? if >=1.21.11 {
//?} else {
/*import net.minecraft.client.gui.components.StringWidget;
*///?}
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Spacing metrics shared by the Better X-Ray screens.
 *
 * <p>All values are in scaled GUI pixels. Screens derive every position from their current
 * {@code width}/{@code height} inside {@code init()}, which Minecraft calls again on every
 * resize, fullscreen toggle and GUI-scale change, so nothing here caches screen dimensions.
 * Widgets are laid out at their real size; nothing is drawn through a scaled pose matrix, so
 * hitboxes, tooltips and narration always match what is rendered.</p>
 */
final class GuiLayout {
    /** Minimum distance between any widget and the screen edge. */
    static final int MARGIN = 8;
    /** Vanilla button height; kept in every mode so buttons stay easy to hit. */
    static final int BUTTON_HEIGHT = 20;
    /** Horizontal gap between the cells of one row. */
    static final int CELL_GAP = 4;
    /** Space a vertical scrollbar needs next to scrolled content. */
    static final int SCROLLBAR_RESERVE = 10;

    static final GuiLayout NORMAL = new GuiLayout(false, 5, 10, 4, 16, 8);
    static final GuiLayout COMPACT = new GuiLayout(true, 3, 5, 2, 8, 4);

    final boolean compact;
    /** Gap between two rows of controls. */
    final int rowGap;
    /** Gap between two groups of controls. */
    final int sectionGap;
    /** Gap between a section label and the row it describes. */
    final int labelGap;
    /** Gap between the screen title and the first control. */
    final int titleGap;
    /** Distance of the title / bottom buttons from the top / bottom screen edge. */
    final int edge;

    private GuiLayout(boolean compact, int rowGap, int sectionGap, int labelGap, int titleGap, int edge) {
        this.compact = compact;
        this.rowGap = rowGap;
        this.sectionGap = sectionGap;
        this.labelGap = labelGap;
        this.titleGap = titleGap;
        this.edge = edge;
    }

    /** Width available for content, capped at {@code max} so large screens stay compact. */
    static int contentWidth(int screenWidth, int max) {
        return Math.max(40, Math.min(max, screenWidth - 2 * MARGIN));
    }

    /** Sizes and positions {@code widgets} as equal cells filling {@code width}; returns the row bottom. */
    static int row(int x, int y, int width, AbstractWidget... widgets) {
        int count = widgets.length;
        int cell = (width - CELL_GAP * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            int left = x + i * (cell + CELL_GAP);
            int right = i == count - 1 ? x + width : left + cell;
            place(widgets[i], right - left, BUTTON_HEIGHT, left, y);
        }
        return y + BUTTON_HEIGHT;
    }

    //? if >=1.21.11 {
    //?} else {
    /*/^* Single-line label no wider than {@code maxWidth}; longer text is clipped with an ellipsis. ^/
    static StringWidget label(Component text, Font font, int maxWidth) {
        return new StringWidget(Math.min(font.width(text), Math.max(1, maxWidth)), font.lineHeight, text, font).alignLeft();
    }

    *///?}
    /**
     * Fits a preset button's label to its current width. Long names are shortened with an
     * ellipsis and the full name is shown as tooltip; narration always reads the full name.
     */
    static void updatePresetButton(Button button, Font font, int index) {
        var config = XRayConfigManager.get();
        String key = index == config.activePreset ? "button.betterxray.preset_active" : "button.betterxray.preset";
        String name = config.presets.get(index).name;
        Component full = Component.translatable(key, name);
        int available = button.getWidth() - 8;
        if (font.width(full) <= available) {
            button.setMessage(full);
            button.setTooltip(null);
            return;
        }
        Component shortened = Component.translatable(key, "…");
        for (int end = name.offsetByCodePoints(name.length(), -1); end > 0; end = name.offsetByCodePoints(end, -1)) {
            Component candidate = Component.translatable(key, name.substring(0, end).stripTrailing() + "…");
            if (font.width(candidate) <= available) { shortened = candidate; break; }
        }
        button.setMessage(shortened);
        button.setTooltip(Tooltip.create(Component.literal(name)));
    }

    /** Builder for a preset button whose narration uses the full, untruncated preset name. */
    static Button.Builder presetButton(int index, Button.OnPress onPress) {
        return Button.builder(Component.empty(), onPress).createNarration(ignored -> {
            var config = XRayConfigManager.get();
            String key = index == config.activePreset ? "button.betterxray.preset_active" : "button.betterxray.preset";
            return Button.wrapDefaultNarrationMessage(Component.translatable(key, config.presets.get(index).name));
        });
    }

    /** Sets size and position; 1.20.1 widgets cannot change their height (all rows are 20 px). */
    static void place(AbstractWidget widget, int width, int height, int x, int y) {
        //? if >=1.21 {
        widget.setRectangle(width, height, x, y);
        //?} else {
        /*widget.setWidth(width);
        widget.setPosition(x, y);
        *///?}
    }

    /** Current scroll offset of a list (renamed in 1.21.4). */
    static double scrollAmount(net.minecraft.client.gui.components.AbstractSelectionList<?> list) {
        //? if >=1.21.4 {
        return list.scrollAmount();
        //?} else {
        /*return list.getScrollAmount();
        *///?}
    }

    /**
     * Minimal layout holding children at fixed offsets. It lets a block of widgets be moved as
     * a unit, either placed directly on the screen or wrapped in a scroll container
     * (vanilla ScrollableLayout, or {@code ScrollArea} before 1.21.11) when it does not fit.
     */
    static final class Panel implements Layout {
        private record Placed(LayoutElement element, int dx, int dy) {}
        private final List<Placed> children = new ArrayList<>();
        private final int width;
        private int height;
        private int x;
        private int y;

        Panel(int width) { this.width = width; }

        /** Adds an element whose current position is interpreted relative to the panel origin. */
        <T extends LayoutElement> T add(T element) {
            children.add(new Placed(element, element.getX(), element.getY()));
            height = Math.max(height, element.getY() + element.getHeight());
            element.setPosition(x + element.getX(), y + element.getY());
            return element;
        }

        @Override public void visitChildren(Consumer<LayoutElement> visitor) { children.forEach(p -> visitor.accept(p.element)); }
        //? if >=1.21.11 {
        public void removeChildren() { children.clear(); height = 0; }
        //?}
        @Override public void setX(int x) { this.x = x; children.forEach(p -> p.element.setX(x + p.dx)); }
        @Override public void setY(int y) { this.y = y; children.forEach(p -> p.element.setY(y + p.dy)); }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
    }
}
