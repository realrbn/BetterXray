package dev.betterxray.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.betterxray.compat.Mc;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Clipboard export/import of one X-Ray preset (name and block/tag entries).
 *
 * <p>Format, one line of JSON: {@code {"betterxray_preset":1,"name":"Ores","blocks":["minecraft:diamond_ore","#c:ores"]}}.
 *
 * <p>{@link #parse} never throws: any input (empty, huge, random text, wrong JSON, newer format)
 * becomes an {@link Failure} with a translation key. Entries are normalized like typed input; invalid
 * entries and block IDs that are not registered (e.g. of a mod that is not installed) are skipped
 * and counted. Tags are kept, they are only bound once a world is joined.
 */
public final class PresetTransfer {
    private PresetTransfer() {}

    /** Import/export buttons exist on this target (Fabric 26.3). */
    //? if fabric && >=26.3 {
    public static final boolean TARGET = true;
    //?} else
    /*public static final boolean TARGET = false;*/

    public static final String MARKER = "betterxray_preset";
    public static final int FORMAT_VERSION = 1;
    /** Larger clipboard contents are rejected before parsing. */
    public static final int MAX_INPUT_LENGTH = 1 << 20;
    /** More entries than any real preset needs; more are rejected. */
    public static final int MAX_ENTRIES = 20_000;

    public sealed interface Result permits Imported, Failure {}

    /** A valid preset; {@code name} is null when the text had none (the current name is kept). */
    public record Imported(String name, Set<String> blocks, int skippedInvalid, int skippedUnknown) implements Result {}

    /** Rejected input: translation key {@code message.betterxray.preset_import.<reason>} with optional arguments. */
    public record Failure(String reason, Object... args) implements Result {
        public String key() { return "message.betterxray.preset_import." + reason; }
    }

    public static String export(XRayConfig.XRayPreset preset) {
        JsonObject root = new JsonObject();
        root.addProperty(MARKER, FORMAT_VERSION);
        root.addProperty("name", preset.name);
        JsonArray blocks = new JsonArray();
        if (preset.visibleBlocks != null) for (String entry : preset.visibleBlocks) if (entry != null) blocks.add(entry);
        root.add("blocks", blocks);
        return root.toString();
    }

    public static Result parse(String text) {
        try {
            return parseUnchecked(text);
        } catch (RuntimeException | StackOverflowError e) {
            // Malformed or hostile JSON (Gson exceptions, absurd nesting): never crash the game.
            return new Failure("not_preset");
        }
    }

    private static Result parseUnchecked(String text) {
        if (text == null || text.isBlank()) return new Failure("empty");
        if (text.length() > MAX_INPUT_LENGTH) return new Failure("too_large");
        text = text.strip();
        if (!text.startsWith("{")) return new Failure("not_preset");
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) return new Failure("not_preset");
        JsonObject root = parsed.getAsJsonObject();
        JsonElement marker = root.get(MARKER);
        if (!(marker instanceof JsonPrimitive version) || !version.isNumber()) return new Failure("not_preset");
        double number = version.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 1) return new Failure("invalid");
        if (number > FORMAT_VERSION) return new Failure("newer_version");

        String name = null;
        JsonElement nameElement = root.get("name");
        if (nameElement != null && !nameElement.isJsonNull()) {
            if (!(nameElement instanceof JsonPrimitive primitive) || !primitive.isString()) return new Failure("invalid");
            name = sanitizeName(primitive.getAsString());
        }

        JsonElement blocksElement = root.get("blocks");
        if (blocksElement == null || !blocksElement.isJsonArray()) return new Failure("invalid");
        JsonArray array = blocksElement.getAsJsonArray();
        if (array.size() > MAX_ENTRIES) return new Failure("too_large");
        Set<String> blocks = new LinkedHashSet<>();
        int invalid = 0, unknown = 0;
        for (JsonElement element : array) {
            String entry = element instanceof JsonPrimitive primitive && primitive.isString()
                ? BlockSelection.normalize(primitive.getAsString()) : null;
            if (entry == null) invalid++;
            else if (!BlockSelection.isTag(entry) && Mc.block(entry) == null) unknown++;
            else blocks.add(entry);
        }
        return new Imported(name, blocks, invalid, unknown);
    }

    /** Trimmed, control characters removed, at most {@link XRayConfig.MAX_PRESET_NAME_LENGTH}; null when empty. */
    static String sanitizeName(String raw) {
        StringBuilder clean = new StringBuilder();
        raw.codePoints().filter(c -> !Character.isISOControl(c)).forEach(clean::appendCodePoint);
        String name = clean.toString().trim();
        if (name.isEmpty()) return null;
        if (name.length() > XRayConfig.MAX_PRESET_NAME_LENGTH) {
            int end = XRayConfig.MAX_PRESET_NAME_LENGTH;
            if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
            name = name.substring(0, end).trim();
        }
        return name;
    }

    /** Replaces preset {@code index} with the imported one. */
    public static void apply(XRayConfig config, int index, Imported imported) {
        XRayConfig.XRayPreset preset = config.presets.get(index);
        if (imported.name() != null) preset.name = imported.name();
        preset.visibleBlocks = new LinkedHashSet<>(imported.blocks());
    }
}
