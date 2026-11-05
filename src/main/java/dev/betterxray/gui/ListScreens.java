package dev.betterxray.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;

/** Shared geometry for the two block-list screens (Visible Blocks and Highlight Blocks). */
final class ListScreens {
    /** Normal spacing is kept while the list can still show this many comfortable rows. */
    private static final int MIN_COMFORTABLE_ROWS = 5;
    private static final int FILTER_MAX_WIDTH = 120;

    private ListScreens() {}

    /**
     * Picks normal spacing when the list stays roomy with it, compact spacing otherwise.
     * {@code controlsHeight} is the height of everything between the title and the search row,
     * including the gap above the search row, measured with normal spacing.
     */
    static GuiLayout metrics(int screenHeight, Font font, int controlsHeight) {
        GuiLayout m = GuiLayout.NORMAL;
        int listTop = controlsTop(m, font) + controlsHeight + GuiLayout.BUTTON_HEIGHT + listGap(m);
        int listHeight = footerY(m, screenHeight) - listGap(m) - listTop;
        return listHeight >= MIN_COMFORTABLE_ROWS * BlockListWidget.ROW_HEIGHT ? GuiLayout.NORMAL : GuiLayout.COMPACT;
    }

    static int titleY(GuiLayout m) { return m.compact ? 6 : 12; }
    static int controlsTop(GuiLayout m, Font font) { return titleY(m) + font.lineHeight + (m.compact ? 4 : 7); }
    static int listGap(GuiLayout m) { return m.compact ? 4 : 6; }
    static int footerY(GuiLayout m, int screenHeight) { return screenHeight - (m.compact ? 24 : 28); }
    static int rowHeight(GuiLayout m) { return m.compact ? BlockListWidget.COMPACT_ROW_HEIGHT : BlockListWidget.ROW_HEIGHT; }

    /** Lays out the search field and the "All Blocks / Selected Only" toggle; returns the row bottom. */
    static int searchRow(int left, int y, int width, AbstractWidget search, AbstractWidget filter) {
        int filterWidth = Math.min(FILTER_MAX_WIDTH, width / 3);
        GuiLayout.place(search, width - filterWidth - GuiLayout.CELL_GAP, GuiLayout.BUTTON_HEIGHT, left, y);
        GuiLayout.place(filter, filterWidth, GuiLayout.BUTTON_HEIGHT, left + width - filterWidth, y);
        return y + GuiLayout.BUTTON_HEIGHT;
    }
}
