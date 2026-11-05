package dev.betterxray.gui;

//? if <1.21.11 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/^*
 * Vertical scroll container for a {@link GuiLayout.Panel}; replaces vanilla's
 * {@code ScrollableLayout}, which Minecraft 1.21.1 does not have yet.
 *
 * <p>The widgets keep their real size and are moved, not drawn through a scaled or translated
 * pose, so hitboxes and tooltips match. Drawing is clipped to the viewport, input outside the
 * viewport is ignored, and keyboard focus scrolls the focused widget into view.</p>
 ^/
public final class ScrollArea extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private static final int SCROLL_STEP = 12;
    private static final int SCROLLBAR_WIDTH = 6;

    private final GuiLayout.Panel content;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final int width;
    private final int height;
    private int x;
    private int y;
    private double scroll;
    private boolean draggingScrollbar;

    ScrollArea(GuiLayout.Panel content, int viewportHeight) {
        this.content = content;
        this.width = content.getWidth() + GuiLayout.SCROLLBAR_RESERVE;
        this.height = Math.min(viewportHeight, content.getHeight());
        content.visitWidgets(widgets::add);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public double scrollAmount() { return scroll; }
    public int maxScroll() { return Math.max(0, content.getHeight() - height); }

    void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        content.setPosition(x, y - (int)scroll);
    }

    void setScrollAmount(double amount) {
        scroll = Mth.clamp(amount, 0, maxScroll());
        content.setPosition(x, y - (int)scroll);
    }

    private boolean inside(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    private int scrollbarX() { return x + width - SCROLLBAR_WIDTH; }

    @Override public List<? extends GuiEventListener> children() { return widgets; }
    @Override public boolean isMouseOver(double mx, double my) { return inside(mx, my); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) return false;
        if (button == 0 && maxScroll() > 0 && mx >= scrollbarX()) {
            draggingScrollbar = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            int thumb = thumbHeight();
            if (height > thumb) setScrollAmount(scroll + dy * maxScroll() / (height - thumb));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    //? if >=1.20.2 {
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!inside(mx, my)) return false;
        if (super.mouseScrolled(mx, my, sx, sy)) return true; // e.g. a hovered slider
    //?} else {
    /^@Override public boolean mouseScrolled(double mx, double my, double sy) {
        if (!inside(mx, my)) return false;
        if (super.mouseScrolled(mx, my, sy)) return true; // e.g. a hovered slider
    ^///?}
        if (maxScroll() == 0) return false;
        setScrollAmount(scroll - sy * SCROLL_STEP);
        return true;
    }

    @Override public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        if (focused instanceof AbstractWidget widget) {
            if (widget.getY() < y) setScrollAmount(scroll - (y - widget.getY()));
            else if (widget.getY() + widget.getHeight() > y + height) setScrollAmount(scroll + widget.getY() + widget.getHeight() - (y + height));
        }
    }

    private int thumbHeight() {
        return Mth.clamp(height * height / Math.max(1, content.getHeight()), 8, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean hover = inside(mx, my);
        g.enableScissor(x, y, x + width, y + height);
        for (AbstractWidget widget : widgets) widget.render(g, hover ? mx : -1, hover ? my : -1, delta);
        g.disableScissor();
        if (maxScroll() > 0) {
            int bar = scrollbarX();
            int thumb = thumbHeight();
            int top = y + (int)Math.round(scroll * (height - thumb) / maxScroll());
            if (GlassTheme.ENABLED) {
                GlassTheme.scrollTrack(g, bar, y, bar + SCROLLBAR_WIDTH, y + height);
                GlassTheme.scrollThumb(g, bar, top, bar + SCROLLBAR_WIDTH, top + thumb, true);
            } else {
                g.fill(bar, y, bar + SCROLLBAR_WIDTH, y + height, 0x80000000);
                g.fill(bar, top, bar + SCROLLBAR_WIDTH, top + thumb, 0xff808080);
                g.fill(bar, top, bar + SCROLLBAR_WIDTH - 1, top + thumb - 1, 0xffc0c0c0);
            }
        }
    }

    @Override public NarrationPriority narrationPriority() {
        return getFocused() instanceof NarratableEntry entry ? entry.narrationPriority() : NarrationPriority.NONE;
    }

    @Override public void updateNarration(NarrationElementOutput output) {
        if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output);
    }
}
*///?}
