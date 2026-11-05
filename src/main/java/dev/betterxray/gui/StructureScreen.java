package dev.betterxray.gui;

import dev.betterxray.compat.Mc;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.StructureFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/**
 * Structure Finder settings and structure picker. One row per structure set; a
 * row selects all of its variants. The list comes from the integrated server, so it is empty
 * outside singleplayer and the screen says why.
 */
public final class StructureScreen extends Screen {
    private static final int DONE_MAX_WIDTH = 200;

    private final Screen parent;
    private StructureList list;
    private String query = "";
    private boolean selectedOnly;
    private int titleY;
    private int infoY;

    public StructureScreen(Screen parent) {
        super(Component.translatable("screen.betterxray.structures"));
        this.parent = parent;
    }

    private static Component state(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override protected void init() {
        var config = XRayConfigManager.get();
        int contentWidth = GuiLayout.contentWidth(width, BlockSelectionScreen.LIST_MAX_WIDTH);
        int left = (width - contentWidth) / 2;
        GuiLayout m = ListScreens.metrics(height, font, GuiLayout.BUTTON_HEIGHT + 2 * GuiLayout.NORMAL.rowGap + font.lineHeight);
        titleY = ListScreens.titleY(m);
        int y = ListScreens.controlsTop(m, font);
        int footerY = ListScreens.footerY(m, height);

        var finder = Button.builder(state("option.betterxray.structure_finder", config.structureFinderEnabled), b -> {
            config.structureFinderEnabled = !config.structureFinderEnabled;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.structure_finder", config.structureFinderEnabled));
        }).build();
        var tracers = Button.builder(state("option.betterxray.tracers", config.structureTracersEnabled), b -> {
            config.structureTracersEnabled = !config.structureTracersEnabled;
            XRayConfigManager.save();
            b.setMessage(state("option.betterxray.tracers", config.structureTracersEnabled));
        }).build();
        finder.active = tracers.active = StructureFinder.available();
        y = GuiLayout.row(left, y, contentWidth, finder, tracers) + m.rowGap;
        addRenderableWidget(finder);
        addRenderableWidget(tracers);
        infoY = y;
        y += font.lineHeight + m.rowGap;

        var search = new EditBox(font, 0, 0, 0, GuiLayout.BUTTON_HEIGHT, Component.translatable("screen.betterxray.search"));
        search.setHint(Component.translatable("screen.betterxray.search"));
        search.setMaxLength(200);
        search.setValue(query);
        var filter = Button.builder(filterLabel(), b -> {
            selectedOnly = !selectedOnly;
            b.setMessage(filterLabel());
            refresh();
        }).build();
        y = ListScreens.searchRow(left, y, contentWidth, search, filter) + ListScreens.listGap(m);
        addRenderableWidget(search);
        addRenderableWidget(filter);

        list = addRenderableWidget(new StructureList(minecraft, contentWidth, Math.max(0, footerY - ListScreens.listGap(m) - y),
            y, ListScreens.rowHeight(m)));
        list.placeAt(left);
        search.setResponder(value -> { query = value; refresh(); });
        refresh();
        int doneWidth = Math.min(DONE_MAX_WIDTH, contentWidth);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds((width - doneWidth) / 2, footerY, doneWidth, GuiLayout.BUTTON_HEIGHT).build());
        setInitialFocus(search);
    }

    private Component filterLabel() {
        return Component.translatable(selectedOnly ? "button.betterxray.selected" : "button.betterxray.all_structures");
    }

    private void refresh() { if (list != null) list.filter(query, selectedOnly); }

    /** "Found nearby: n" while a singleplayer world is open, otherwise why the list is empty. */
    private Component info() {
        if (!StructureFinder.available()) return Component.translatable("label.betterxray.structures_singleplayer");
        if (!XRayConfigManager.get().structureFinderEnabled) return Component.translatable("label.betterxray.structures_off");
        return Component.translatable("label.betterxray.structures_found", StructureFinder.found().size());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //? if <1.20.2
        /*renderBackground(g);*/
        super.extractRenderState(g, mx, my, delta);
        GlassTheme.title(g, font, title, width / 2, titleY);
        g.centeredText(font, info(), width / 2, infoY, GlassTheme.ENABLED ? GlassTheme.TEXT_MUTED : 0xffaaaaaa);
    }

    @Override public void onClose() { XRayConfigManager.save(); Mc.setScreen(minecraft, parent); }

    private static final class StructureList extends SelectionList<StructureList.Row> {
        private static final int ROW_INSET = 12;
        private final java.util.List<Row> rows;

        StructureList(Minecraft minecraft, int width, int height, int top, int rowHeight) {
            super(minecraft, width, height, top, rowHeight);
            rows = StructureFinder.groups().stream().map(Row::new).toList();
        }

        @Override public int getRowWidth() { return listWidth() - 2 * ROW_INSET; }

        void filter(String query, boolean selectedOnly) {
            String needle = query.toLowerCase(Locale.ROOT).trim();
            double scroll = GuiLayout.scrollAmount(this);
            replaceEntries(rows.stream().filter(row -> (!selectedOnly || row.selected()) && row.matches(needle)).toList());
            setScrollAmount(scroll);
        }

        final class Row extends ListEntry<Row> {
            private final StructureFinder.Group group;
            private final String details;

            Row(StructureFinder.Group group) {
                this.group = group;
                int variants = group.structures().size();
                details = variants == 1 ? group.structures().get(0)
                    : Component.translatable("label.betterxray.structure_variants", group.key(), variants).getString();
            }

            boolean selected() { return XRayConfigManager.get().structures.containsAll(group.structures()); }

            boolean matches(String needle) {
                return group.name().getString().toLowerCase(Locale.ROOT).contains(needle) || group.key().contains(needle)
                    || group.structures().stream().anyMatch(id -> id.contains(needle));
            }

            void toggle() {
                var selected = XRayConfigManager.get().structures;
                if (selected()) group.structures().forEach(selected::remove);
                else selected.addAll(group.structures());
                XRayConfigManager.save();
            }

            @Override public Component getNarration() {
                return Component.literal(group.name().getString() + ", " + details + (selected() ? ", selected" : ", not selected"));
            }

            @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
                int x = contentX(), y = contentY();
                boolean selected = selected();
                if (GlassTheme.ENABLED) {
                    if (selected) GlassTheme.rowTint(g, x, y, contentRight(), contentBottom(), GlassTheme.ACCENT);
                    GlassTheme.checkbox(g, minecraft.font, x + 4, y + (contentHeight() - 14) / 2, 14, selected, hovered);
                } else {
                    if (selected) g.fill(x, y, contentRight(), contentBottom(), 0x4038ae65);
                    g.text(minecraft.font, selected ? "[x]" : "[ ]", x + 3, y + (contentHeight() - 8) / 2, selected ? 0xff8dffac : 0xffaaaaaa);
                }
                // Box color of this structure in the world.
                int sy = contentYMiddle() - 6;
                g.fill(x + 29, sy, x + 41, sy + 12, group.color());
                int textTop = y + Math.max(0, (contentHeight() - 22) / 2);
                int textWidth = contentWidth() - 56;
                g.text(minecraft.font, minecraft.font.plainSubstrByWidth(group.name().getString(), textWidth), x + 50, textTop, 0xffffffff);
                g.text(minecraft.font, minecraft.font.plainSubstrByWidth(details, textWidth), x + 50, textTop + 13,
                    GlassTheme.ENABLED ? GlassTheme.TEXT_MUTED : 0xffaaaaaa);
            }

            @Override protected boolean primaryClick(double mouseX, double mouseY) {
                setSelected(this);
                toggle();
                return true;
            }

            @Override protected boolean keyInput(Keys keys) {
                if (!keys.selection()) return false;
                toggle();
                return true;
            }
        }
    }
}
