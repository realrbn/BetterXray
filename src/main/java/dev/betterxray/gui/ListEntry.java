package dev.betterxray.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*import net.minecraft.client.gui.navigation.CommonInputs;
import org.lwjgl.glfw.GLFW;
*///?}

/**
 * List row base that hides the entry API differences between Minecraft versions: content
 * bounds (vanilla getters since 1.21.9, render arguments before), input events (event objects
 * since 1.21.9, GLFW codes before) and the primary mouse button (SDL numbering on 26.3).
 */
public abstract class ListEntry<E extends ListEntry<E>> extends ObjectSelectionList.Entry<E> {
    /** Keyboard input reduced to what the rows react to. */
    public record Keys(boolean selection, boolean left, boolean right) {}

    /** Left button click inside the row; return true when handled. */
    protected boolean primaryClick(double mouseX, double mouseY) { return false; }

    /** Key press while the row is focused; return true when handled. */
    protected boolean keyInput(Keys keys) { return false; }

    //? if >=1.21.9 {
    protected int contentX() { return getContentX(); }
    protected int contentY() { return getContentY(); }
    protected int contentWidth() { return getContentWidth(); }
    protected int contentHeight() { return getContentHeight(); }
    protected int contentRight() { return getContentRight(); }
    protected int contentBottom() { return getContentBottom(); }
    protected int contentYMiddle() { return getContentYMiddle(); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        //? if >=26.3 {
        boolean primary = event.button() == 1;
        //?} else {
        /*boolean primary = event.button() == 0;
        *///?}
        return primary && primaryClick(event.x(), event.y());
    }

    @Override public boolean keyPressed(KeyEvent event) {
        // isSelection() covers Enter, keypad Enter and Space with the key codes of each version.
        return keyInput(new Keys(event.isSelection(), event.isLeft(), event.isRight()));
    }
    //?} else {
    /*private int left, top, width, height;

    protected int contentX() { return left; }
    protected int contentY() { return top; }
    protected int contentWidth() { return width; }
    protected int contentHeight() { return height; }
    protected int contentRight() { return left + width; }
    protected int contentBottom() { return top + height; }
    protected int contentYMiddle() { return top + height / 2; }

    /^* Draws the row content; the bounds are available through the content getters. ^/
    public abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta);

    @Override public void extractRenderState(GuiGraphicsExtractor g, int index, int top, int left, int width, int height,
                                 int mouseX, int mouseY, boolean hovered, float delta) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        renderContent(g, mouseX, mouseY, hovered, delta);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT && primaryClick(mouseX, mouseY);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        return keyInput(new Keys(CommonInputs.selected(key), key == GLFW.GLFW_KEY_LEFT, key == GLFW.GLFW_KEY_RIGHT));
    }
    *///?}
}
