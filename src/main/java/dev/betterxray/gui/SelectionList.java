package dev.betterxray.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

/**
 * Common base of the block and mob lists: one constructor, position and scrollbar placement
 * across the list API generations (1.20.1 still uses x0/x1/y0/y1 instead of widget bounds).
 */
public abstract class SelectionList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    protected SelectionList(Minecraft minecraft, int width, int height, int top, int rowHeight) {
        //? if >=1.21 {
        super(minecraft, width, height, top, rowHeight);
        //?} else {
        /*super(minecraft, width, top + height, top, top + height, rowHeight);
        // The 1.20.1 list would paint dirt across the whole screen above and below itself.
        setRenderTopAndBottom(false);
        *///?}
    }

    public int listWidth() {
        //? if >=1.21 {
        return getWidth();
        //?} else
        /*return width;*/
    }

    public int listX() {
        //? if >=1.21 {
        return getX();
        //?} else
        /*return x0;*/
    }

    public int listY() {
        //? if >=1.21 {
        return getY();
        //?} else
        /*return y0;*/
    }

    public int listHeight() {
        //? if >=1.21 {
        return getHeight();
        //?} else
        /*return y1 - y0;*/
    }

    public int listRight() {
        //? if >=1.21 {
        return getRight();
        //?} else
        /*return x1;*/
    }

    public void placeAt(int x) {
        //? if >=1.21 {
        setX(x);
        //?} else
        /*setLeftPos(x);*/
    }

    /** Keeps the scrollbar inside the list bounds, next to the rows. */
    //? if >=26.1 {
    @Override protected int scrollBarX() { return listRight() - scrollbarWidth() - 2; }
    //?} else if >=1.21.4 {
    /*@Override protected int scrollBarX() { return listRight() - SCROLLBAR_WIDTH - 2; }
    *///?} else {
    /*@Override protected int getScrollbarPosition() { return listRight() - 6 - 2; }
    *///?}
}
