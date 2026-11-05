package dev.betterxray.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.betterxray.compat.Mc;
import dev.betterxray.compat.RendererCompatibility;
import dev.betterxray.config.BlockSelection;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.debug.DebugMode;
import dev.betterxray.render.HighlightCache;
import dev.betterxray.render.MobEspRenderer;
import dev.betterxray.render.StructureFinder;
import dev.betterxray.render.XRayRenderController;
import dev.betterxray.render.XRayState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Client commands {@code /betterxray} (alias {@code /bxray}): switch the features, edit the active
 * preset and the highlight list with block IDs and {@code #tags}, change presets, opacity and
 * ranges without opening the configuration screen. Nothing is sent to the server; feedback goes
 * to the local chat only. The tree uses plain Brigadier types, so Fabric
 * ({@code FabricClientCommandSource}) and NeoForge ({@code CommandSourceStack}) share it.
 */
public final class XRayCommands {
    public static final String NAME = "betterxray";
    public static final String ALIAS = "bxray";
    /** Longest list printed to the chat before it is cut off with "… and N more". */
    private static final int MAX_LISTED = 60;

    private XRayCommands() {}

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(new Tree<S>().build(NAME));
        dispatcher.register(new Tree<S>().build(ALIAS));
    }

    /** Builders typed with the loader's command source. */
    private static final class Tree<S> {
        LiteralArgumentBuilder<S> literal(String name) { return LiteralArgumentBuilder.literal(name); }
        <T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<T> type) { return RequiredArgumentBuilder.argument(name, type); }

        LiteralArgumentBuilder<S> build(String name) {
            var root = literal(name).executes(c -> help(name));
            root.then(literal("help").executes(c -> help(name)));
            root.then(literal("status").executes(c -> status()));
            root.then(literal("toggle").executes(c -> setXRay(!config().enabled)));
            root.then(literal("on").executes(c -> setXRay(true)));
            root.then(literal("off").executes(c -> setXRay(false)));
            addEntryCommands(root, false);
            root.then(literal("preset").executes(c -> showPresets())
                .then(argument("number", IntegerArgumentType.integer(1, XRayConfig.PRESET_COUNT))
                    .executes(c -> selectPreset(IntegerArgumentType.getInteger(c, "number") - 1)))
                .then(literal("rename").then(argument("name", StringArgumentType.greedyString())
                    .executes(c -> renamePreset(StringArgumentType.getString(c, "name")))))
                .then(literal("reset").executes(c -> resetPreset())));
            root.then(literal("opacity").executes(c -> showOpacity())
                .then(argument("percent", IntegerArgumentType.integer(0, 80))
                    .executes(c -> setOpacity(IntegerArgumentType.getInteger(c, "percent")))));
            root.then(toggle("water", "option.betterxray.water", () -> config().showWater, value -> {
                config().showWater = value;
                XRayRenderController.apply();
            }));
            root.then(toggle("lava", "option.betterxray.lava", () -> config().showLava, value -> {
                config().showLava = value;
                XRayRenderController.apply();
            }));
            root.then(toggle("fullbright", "message.betterxray.fullbright", () -> config().fullbright, value -> {
                config().fullbright = value;
                XRayConfigManager.save();
            }));
            root.then(toggle("cavefind", "message.betterxray.cave_find", () -> config().caveFind, value -> {
                RendererCompatibility.refresh();
                config().caveFind = value;
                XRayRenderController.apply();
            }));
            // Without an argument it cycles like the button: off -> walls -> all.
            var hideMobs = literal("hidemobs").executes(c -> setHideMobs(config().hideMobs.next()));
            for (var mode : XRayConfig.HideMobs.values())
                hideMobs.then(literal(mode.name().toLowerCase(java.util.Locale.ROOT)).executes(c -> setHideMobs(mode)));
            root.then(hideMobs);

            var highlight = toggle("highlight", "message.betterxray.highlight", () -> config().highlightEnabled, value -> {
                config().highlightEnabled = value;
                XRayConfigManager.save();
            });
            addEntryCommands(highlight, true);
            highlight.then(toggle("tracers", "message.betterxray.tracers", () -> config().tracersEnabled, value -> {
                config().tracersEnabled = value;
                XRayConfigManager.save();
            }));
            highlight.then(literal("range").executes(c -> showHighlightRange())
                .then(literal("render").executes(c -> setHighlightRange(0)))
                .then(argument("blocks", IntegerArgumentType.integer(XRayConfig.RANGE_STEP_BLOCKS, XRayConfig.MAX_RANGE_BLOCKS))
                    .executes(c -> setHighlightRange(IntegerArgumentType.getInteger(c, "blocks")))));
            root.then(highlight);

            var esp = toggle("esp", "message.betterxray.esp", () -> config().mobEspEnabled, value -> {
                config().mobEspEnabled = value;
                XRayConfigManager.save();
            });
            esp.then(toggle("tracers", "message.betterxray.esp_tracers",
                () -> config().mobEspTracersEnabled, value -> {
                    config().mobEspTracersEnabled = value;
                    XRayConfigManager.save();
                }));
            esp.then(literal("range").executes(c -> showEspRange())
                .then(literal("unlimited").executes(c -> setEspRange(0)))
                .then(argument("blocks", IntegerArgumentType.integer(XRayConfig.RANGE_STEP_BLOCKS, XRayConfig.MAX_RANGE_BLOCKS))
                    .executes(c -> setEspRange(IntegerArgumentType.getInteger(c, "blocks")))));
            root.then(esp);

            root.then(literal("tags").executes(c -> listTags(""))
                .then(argument("search", StringArgumentType.greedyString())
                    .executes(c -> listTags(StringArgumentType.getString(c, "search")))));
            root.then(literal("tag").then(argument("tag", StringArgumentType.greedyString())
                .suggests((c, builder) -> suggestTags(builder))
                .executes(c -> showTag(StringArgumentType.getString(c, "tag")))));

            // Developer debug mode; not listed in the help.
            root.then(literal("debug").executes(c -> debugStatus())
                .then(literal("on").executes(c -> setDebug(true)))
                .then(literal("off").executes(c -> setDebug(false)))
                .then(literal("bounds").executes(c -> setDebugBounds(!DebugMode.bounds()))
                    .then(literal("on").executes(c -> setDebugBounds(true)))
                    .then(literal("off").executes(c -> setDebugBounds(false))))
                .then(literal("sections").executes(c -> setDebugSections(!DebugMode.sections()))
                    .then(literal("on").executes(c -> setDebugSections(true)))
                    .then(literal("off").executes(c -> setDebugSections(false))))
                .then(literal("hud").executes(c -> setDebugHud(!DebugMode.hud()))
                    .then(literal("on").executes(c -> setDebugHud(true)))
                    .then(literal("off").executes(c -> setDebugHud(false))))
                .then(literal("stats").executes(c -> debugStats()))
                .then(literal("clear").executes(c -> clearDebug())));
            return root;
        }

        /** {@code add}, {@code remove}, {@code list} and {@code clear} for the active preset or the highlight list. */
        private void addEntryCommands(LiteralArgumentBuilder<S> parent, boolean highlight) {
            parent.then(literal("add").then(argument("blocks", StringArgumentType.greedyString())
                .suggests((c, builder) -> suggestEntries(builder, highlight, true))
                .executes(c -> add(highlight, StringArgumentType.getString(c, "blocks")))));
            parent.then(literal("remove").then(argument("blocks", StringArgumentType.greedyString())
                .suggests((c, builder) -> suggestEntries(builder, highlight, false))
                .executes(c -> remove(highlight, StringArgumentType.getString(c, "blocks")))));
            parent.then(literal("list").executes(c -> list(highlight)));
            parent.then(literal("clear").executes(c -> clear(highlight)));
        }

        /** {@code name} toggles, {@code name on} / {@code name off} set the switch. */
        private LiteralArgumentBuilder<S> toggle(String name, String messageKey, BooleanSupplier get, Consumer<Boolean> set) {
            return literal(name)
                .executes(c -> setSwitch(messageKey, !get.getAsBoolean(), set))
                .then(literal("on").executes(c -> setSwitch(messageKey, true, set)))
                .then(literal("off").executes(c -> setSwitch(messageKey, false, set)));
        }
    }

    private static XRayConfig config() { return XRayConfigManager.get(); }

    private static void send(Component message) { Mc.localChat(Minecraft.getInstance(), message); }

    private static int fail(Component message) {
        send(message.copy().withStyle(ChatFormatting.RED));
        return 0;
    }

    private static Component onOff(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }

    private static int setSwitch(String messageKey, boolean value, Consumer<Boolean> set) {
        set.accept(value);
        send(Component.translatable(messageKey, onOff(value)));
        return 1;
    }

    private static int setHideMobs(XRayConfig.HideMobs mode) {
        config().hideMobs = mode;
        XRayConfigManager.save();
        send(Component.translatable("option.betterxray.hide_mobs", Component.translatable(mode.key())));
        return 1;
    }

    private static int setXRay(boolean value) {
        RendererCompatibility.refresh();
        config().enabled = value;
        XRayRenderController.apply();
        send(XRayState.supported() ? Component.translatable("message.betterxray.xray", onOff(value))
            : Component.translatable("message.betterxray.unavailable"));
        return 1;
    }

    // ---- block IDs and tags ----

    private static Set<String> entries(boolean highlight) {
        return highlight ? config().highlightBlocks : config().getActiveVisibleBlocks();
    }

    /** Name of the list a command edits: the active preset or the highlight list. */
    private static Component target(boolean highlight) {
        return highlight ? Component.translatable("screen.betterxray.highlight") : Component.literal(config().getActivePreset().name);
    }

    private static void entriesChanged(boolean highlight) {
        if (highlight) XRayConfigManager.save();
        else XRayRenderController.apply();
    }

    /** Entry with the number of blocks of a tag, e.g. {@code #c:ores (45)}. */
    private static String describe(String entry) {
        return BlockSelection.isTag(entry) ? entry + " (" + BlockSelection.members(entry).size() + ")" : entry;
    }

    private static boolean inWorld() { return Minecraft.getInstance().level != null; }

    private static int add(boolean highlight, String input) {
        Set<String> entries = entries(highlight);
        List<String> added = new ArrayList<>(), present = new ArrayList<>(), invalid = new ArrayList<>(), empty = new ArrayList<>();
        for (String token : input.trim().split("\\s+")) {
            String entry = BlockSelection.normalize(token);
            Block block = entry == null || BlockSelection.isTag(entry) ? null : Mc.block(entry);
            boolean valid = entry != null && (BlockSelection.isTag(entry) ? BlockSelection.tagKey(entry) != null
                : block != null && !block.defaultBlockState().isAir());
            if (!valid) {
                invalid.add(token);
                continue;
            }
            if (!entries.add(entry)) {
                present.add(entry);
                continue;
            }
            added.add(describe(entry));
            if (BlockSelection.isTag(entry) && inWorld() && BlockSelection.members(entry).isEmpty()) empty.add(entry);
        }
        if (!added.isEmpty()) {
            entriesChanged(highlight);
            send(Component.translatable("command.betterxray.added", target(highlight), String.join(", ", added)));
        }
        if (!present.isEmpty()) send(Component.translatable("command.betterxray.present", target(highlight), String.join(", ", present)));
        if (!empty.isEmpty()) send(Component.translatable("command.betterxray.empty_tag", String.join(", ", empty)).withStyle(ChatFormatting.YELLOW));
        if (!invalid.isEmpty()) return fail(Component.translatable("command.betterxray.invalid", String.join(", ", invalid)));
        return added.size();
    }

    private static int remove(boolean highlight, String input) {
        Set<String> entries = entries(highlight);
        List<String> removed = new ArrayList<>(), missing = new ArrayList<>();
        for (String token : input.trim().split("\\s+")) {
            String entry = BlockSelection.normalize(token);
            if (entries.remove(token) || (entry != null && entries.remove(entry))) removed.add(entry != null ? entry : token);
            else missing.add(token);
        }
        if (!removed.isEmpty()) {
            entriesChanged(highlight);
            send(Component.translatable("command.betterxray.removed", target(highlight), String.join(", ", removed)));
        }
        if (!missing.isEmpty()) return fail(Component.translatable("command.betterxray.not_present", target(highlight), String.join(", ", missing)));
        return removed.size();
    }

    private static int list(boolean highlight) {
        List<String> sorted = new ArrayList<>(entries(highlight));
        // Tags first, then blocks, each alphabetically.
        sorted.sort(Comparator.comparing((String entry) -> !BlockSelection.isTag(entry)).thenComparing(entry -> entry));
        if (sorted.isEmpty()) {
            send(Component.translatable("command.betterxray.list_empty", target(highlight)));
            return 0;
        }
        send(Component.translatable("command.betterxray.list", target(highlight), sorted.size(),
            joinLimited(sorted.stream().map(XRayCommands::describe).toList())));
        return sorted.size();
    }

    private static int clear(boolean highlight) {
        int count = entries(highlight).size();
        entries(highlight).clear();
        entriesChanged(highlight);
        send(Component.translatable("command.betterxray.cleared", target(highlight), count));
        return 1;
    }

    private static int listTags(String search) {
        if (!inWorld()) return fail(Component.translatable("command.betterxray.tags_no_world"));
        String needle = search.trim().toLowerCase(Locale.ROOT);
        if (needle.startsWith(BlockSelection.TAG_PREFIX)) needle = needle.substring(BlockSelection.TAG_PREFIX.length());
        List<String> matches = new ArrayList<>();
        for (var tag : BlockSelection.allTags().entrySet()) {
            if (tag.getKey().contains(needle)) matches.add(tag.getKey() + " (" + tag.getValue().size() + ")");
        }
        if (matches.isEmpty()) return fail(Component.translatable("command.betterxray.tags_none", search.trim()));
        send(Component.translatable("command.betterxray.tags", matches.size(), joinLimited(matches)));
        return matches.size();
    }

    private static int showTag(String input) {
        String entry = BlockSelection.normalize(input.trim().startsWith(BlockSelection.TAG_PREFIX) ? input : BlockSelection.TAG_PREFIX + input.trim());
        if (entry == null || BlockSelection.tagKey(entry) == null) return fail(Component.translatable("command.betterxray.invalid", input.trim()));
        if (!inWorld()) return fail(Component.translatable("command.betterxray.tags_no_world"));
        List<String> blocks = BlockSelection.members(entry).stream()
            .filter(block -> !block.defaultBlockState().isAir())
            .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString()).sorted().toList();
        if (blocks.isEmpty()) return fail(Component.translatable("command.betterxray.empty_tag", entry));
        send(Component.translatable("command.betterxray.tag", entry, blocks.size(), joinLimited(blocks)));
        return blocks.size();
    }

    private static String joinLimited(List<String> items) {
        if (items.size() <= MAX_LISTED) return String.join(", ", items);
        return String.join(", ", items.subList(0, MAX_LISTED)) + ", " + Component.translatable(
            "command.betterxray.more", items.size() - MAX_LISTED).getString();
    }

    /** Completes the last word of a space-separated list of block IDs and tags. */
    private static CompletableFuture<Suggestions> suggestEntries(SuggestionsBuilder builder, boolean highlight, boolean adding) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastSpace + 1);
        String token = remaining.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
        if (!adding) {
            for (String entry : entries(highlight)) if (matches(entry, token)) word.suggest(entry);
            return word.buildFuture();
        }
        if (token.startsWith(BlockSelection.TAG_PREFIX) || token.isEmpty()) {
            for (String tag : BlockSelection.allTags().keySet()) if (matches(tag, token)) word.suggest(tag);
        }
        if (!token.startsWith(BlockSelection.TAG_PREFIX)) {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.defaultBlockState().isAir()) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (matches(id, token)) word.suggest(id);
            }
        }
        return word.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTags(SuggestionsBuilder builder) {
        String token = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String tag : BlockSelection.allTags().keySet()) if (matches(tag, token)) builder.suggest(tag);
        return builder.buildFuture();
    }

    /** Prefix match on the full entry or on its path ({@code diamond} matches {@code minecraft:diamond_ore}). */
    private static boolean matches(String entry, String token) {
        if (token.isEmpty() || entry.startsWith(token)) return true;
        boolean tag = token.startsWith(BlockSelection.TAG_PREFIX);
        String bare = tag ? token.substring(BlockSelection.TAG_PREFIX.length()) : token;
        if (tag != BlockSelection.isTag(entry)) return false;
        int colon = entry.indexOf(':');
        return colon >= 0 && entry.substring(colon + 1).startsWith(bare);
    }

    // ---- presets ----

    private static int showPresets() {
        var config = config();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < XRayConfig.PRESET_COUNT; i++) {
            names.add((i == config.activePreset ? "▶ " : "") + (i + 1) + " " + config.presets.get(i).name
                + " (" + config.presets.get(i).visibleBlocks.size() + ")");
        }
        send(Component.translatable("command.betterxray.presets", String.join(", ", names)));
        return 1;
    }

    private static int selectPreset(int index) {
        config().setActivePreset(index);
        XRayRenderController.apply();
        send(Component.translatable("message.betterxray.preset", config().getActivePreset().name));
        return 1;
    }

    private static int renamePreset(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return fail(Component.translatable("command.betterxray.invalid", name));
        if (trimmed.length() > XRayConfig.MAX_PRESET_NAME_LENGTH) trimmed = trimmed.substring(0, XRayConfig.MAX_PRESET_NAME_LENGTH);
        config().getActivePreset().name = trimmed;
        XRayConfigManager.save();
        send(Component.translatable("message.betterxray.preset", trimmed));
        return 1;
    }

    private static int resetPreset() {
        config().resetPreset(config().activePreset);
        XRayRenderController.apply();
        send(Component.translatable("command.betterxray.preset_reset", config().getActivePreset().name));
        return 1;
    }

    // ---- values ----

    private static int showOpacity() {
        send(Component.translatable("option.betterxray.opacity", Math.round(config().terrainOpacity * 100)));
        return 1;
    }

    private static int setOpacity(int percent) {
        config().terrainOpacity = percent / 100f;
        XRayRenderController.apply();
        return showOpacity();
    }

    /** Ranges move in slider steps of {@link XRayConfig#RANGE_STEP_BLOCKS}. */
    private static int roundRange(int blocks) {
        int step = XRayConfig.RANGE_STEP_BLOCKS;
        return Mth.clamp(Math.round(blocks / (float)step) * step, step, XRayConfig.MAX_RANGE_BLOCKS);
    }

    private static Component blocks(int blocks) { return Component.translatable("command.betterxray.blocks", blocks); }

    private static int showHighlightRange() {
        int range = config().highlightRangeBlocks;
        send(Component.translatable("option.betterxray.range", range == 0
            ? Component.translatable("command.betterxray.render_distance") : blocks(range)));
        return 1;
    }

    private static int setHighlightRange(int blocks) {
        config().highlightRangeBlocks = blocks == 0 ? 0 : roundRange(blocks);
        XRayConfigManager.save();
        return showHighlightRange();
    }

    private static int showEspRange() {
        int range = config().mobEspRangeBlocks;
        send(Component.translatable("command.betterxray.esp_range", range == 0
            ? Component.translatable("command.betterxray.unlimited") : blocks(range)));
        return 1;
    }

    private static int setEspRange(int blocks) {
        config().mobEspRangeBlocks = blocks == 0 ? 0 : roundRange(blocks);
        XRayConfigManager.save();
        return showEspRange();
    }

    // ---- debug mode ----

    private static int debugStatus() {
        send(Component.translatable("debug.betterxray.status", onOff(DebugMode.enabled()), onOff(DebugMode.bounds()),
            onOff(DebugMode.sections()), onOff(DebugMode.hud()), DebugMode.violations().size(), DebugMode.total()));
        for (String violation : DebugMode.violations()) send(Component.literal("• " + violation).withStyle(ChatFormatting.RED));
        return 1;
    }

    private static int setDebug(boolean value) {
        DebugMode.setEnabled(value);
        return debugStatus();
    }

    private static int setDebugBounds(boolean value) {
        DebugMode.setBounds(value);
        return debugStatus();
    }

    private static int setDebugSections(boolean value) {
        DebugMode.setSections(value);
        return debugStatus();
    }

    private static int setDebugHud(boolean value) {
        DebugMode.setHud(value);
        return debugStatus();
    }

    private static int clearDebug() {
        DebugMode.clear();
        return debugStatus();
    }

    private static int debugStats() {
        if (!inWorld()) return fail(Component.translatable("command.betterxray.tags_no_world"));
        var h = HighlightCache.stats(Minecraft.getInstance());
        send(Component.translatable("debug.betterxray.stats.highlight", onOff(h.active()),
            h.limited() ? blocks((int)Math.round(h.range())) : Component.translatable("command.betterxray.render_distance"),
            h.viewChunks(), h.scannedSections(), h.queuedSections(), h.sectionsWithHits(), h.indexedPositions(), h.veins()));
        send(Component.translatable("debug.betterxray.stats.highlight_scan", Math.round(h.farthestScanned()),
            Component.literal(String.valueOf(h.outsideSections())).withStyle(h.outsideSections() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN),
            h.lastTickSections(), millis(h.lastTickNanos()), millis(h.maxTickNanos()), h.drawnBoxes(), h.drawnTracers()));
        var s = StructureFinder.stats();
        send(s.available() ? Component.translatable("debug.betterxray.stats.structures", s.found(), s.centerX(), s.centerZ(),
                s.radius(), s.chunks(), millis(s.scanNanos()), s.drawnBoxes())
            : Component.translatable("debug.betterxray.stats.structures_unavailable"));
        return 1;
    }

    private static String millis(long nanos) { return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0); }

    // ---- overview ----

    private static int status() {
        var config = config();
        send(Component.translatable("command.betterxray.status.xray",
            XRayState.supported() ? onOff(config.enabled) : Component.translatable("message.betterxray.unavailable"),
            config.getActivePreset().name, config.getActiveVisibleBlocks().size(), XRayState.get().visible().size(),
            Math.round(config.terrainOpacity * 100), onOff(config.showWater), onOff(config.showLava)));
        send(Component.translatable("command.betterxray.status.other", onOff(config.fullbright),
            onOff(config.caveFind), Component.translatable(config.hideMobs.key())));
        send(Component.translatable("command.betterxray.status.highlight", onOff(config.highlightEnabled),
            config.highlightBlocks.size(), onOff(config.tracersEnabled),
            config.highlightRangeBlocks == 0 ? Component.translatable("command.betterxray.render_distance") : blocks(config.highlightRangeBlocks)));
        send(Component.translatable("command.betterxray.status.esp", onOff(config.mobEspEnabled),
            onOff(config.mobEspTracersEnabled),
            config.mobEspRangeBlocks == 0 ? Component.translatable("command.betterxray.unlimited") : blocks(config.mobEspRangeBlocks)));
        return 1;
    }

    private static int help(String name) {
        String c = "/" + name;
        send(Component.translatable("command.betterxray.help.header", "/" + NAME, "/" + ALIAS).withStyle(ChatFormatting.GOLD));
        helpLine(c + " on | off | toggle", "command.betterxray.help.xray");
        helpLine(c + " add | remove <block|#tag …>", "command.betterxray.help.entries");
        helpLine(c + " list | clear", "command.betterxray.help.list");
        helpLine(c + " preset [1-3 | rename <name> | reset]", "command.betterxray.help.preset");
        helpLine(c + " opacity <0-80>", "command.betterxray.help.opacity");
        helpLine(c + " water | lava | fullbright" + " | cavefind" + " [on|off]", "command.betterxray.help.switches");
        helpLine(c + " hidemobs [off|walls|all]", "command.betterxray.help.hide_mobs");
        helpLine(c + " highlight [on|off | add | remove | list | clear | tracers | range]", "command.betterxray.help.highlight");
        helpLine(c + " esp [on|off" + " | tracers" + " | range]", "command.betterxray.help.esp");
        helpLine(c + " tags [search] | tag <#tag>", "command.betterxray.help.tags");
        helpLine(c + " status", "command.betterxray.help.status");
        return 1;
    }

    private static void helpLine(String syntax, String descriptionKey) {
        MutableComponent line = Component.literal(syntax).withStyle(ChatFormatting.YELLOW);
        send(line.append(Component.literal(" – ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY)));
    }
}
