package dev.betterxray.update;

import dev.betterxray.compat.Mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.betterxray.XRayClient;
import dev.betterxray.compat.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Checks once per game session, when the first singleplayer world is opened, whether
 * update-check.json lists a newer Better X-Ray version for this loader and Minecraft version.
 * The hint is only added to the local chat and only while a singleplayer world is open; nothing
 * is ever sent to a server. Every failure (offline, HTTP error, bad JSON, missing entry, newer
 * local build, …) is logged once and otherwise ignored.
 */
public final class UpdateChecker {
    public static final String DEFAULT_URL = "https://realrbn.github.io/update-check/update-check.json";
    /** CurseForge project ID of Better X-Ray, the key in update-check.json. */
    public static final String PROJECT_ID = "1713176";
    public static final String LOADER = Platform.LOADER;
    public static final String FALLBACK_PAGE = "https://www.curseforge.com/minecraft/mc-mods/better-xray";
    /** Overrides the JSON URL (e.g. for tests); the value {@code off} disables the check. */
    public static final String URL_PROPERTY = "betterxray.updateCheck.url";
    private static final int MAX_BYTES = 1 << 20;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    public enum Status {
        UPDATE_AVAILABLE, UP_TO_DATE, INSTALLED_NEWER, PROJECT_NOT_LISTED, LOADER_NOT_LISTED,
        MINECRAFT_NOT_LISTED, INVALID_DATA, UNREACHABLE, DISABLED
    }

    /** Outcome of one check; {@code latest}/{@code url} are only set when an entry was found. */
    public record Result(Status status, String latest, String url, String detail) {
        static Result of(Status status, String detail) { return new Result(status, null, null, detail); }
    }

    private static boolean started;
    private static volatile Result result;
    private static boolean handled;
    private static Component lastMessage;

    private UpdateChecker() {}

    /** Called every client tick: starts the check in the first singleplayer world and shows the result there. */
    public static void tick(Minecraft client) {
        if (client.player == null || !client.hasSingleplayerServer()) return;
        // Switched off in the X-Ray menu: no request, and a result that is already pending stays hidden.
        if (!dev.betterxray.config.XRayConfigManager.get().updateCheckEnabled) return;
        if (!started) {
            started = true;
            start();
        }
        Result done = result;
        if (done == null || handled) return;
        handled = true;
        if (done.status() == Status.UPDATE_AVAILABLE) {
            lastMessage = message(done);
            // Local chat only: never sent to the (integrated) server or anyone else.
            Mc.localChat(client, lastMessage);
        }
    }

    private static void start() {
        String installed = modVersion("betterxray");
        String minecraft = modVersion("minecraft");
        String url = System.getProperty(URL_PROPERTY, DEFAULT_URL).trim();
        if (url.isEmpty() || url.equalsIgnoreCase("off")) {
            finish(Result.of(Status.DISABLED, "disabled by -D" + URL_PROPERTY));
            return;
        }
        fetch(url, installed, minecraft).whenComplete((value, error) ->
            finish(error != null ? Result.of(Status.UNREACHABLE, error.toString()) : value));
    }

    private static void finish(Result value) {
        result = value;
        switch (value.status()) {
            case UPDATE_AVAILABLE -> XRayClient.LOGGER.info("[XRay] Update available: {} (installed {}) {}",
                value.latest(), modVersion("betterxray"), value.url());
            case UP_TO_DATE -> XRayClient.LOGGER.info("[XRay] Update check: up to date ({})", value.latest());
            default -> XRayClient.LOGGER.info("[XRay] Update check skipped: {} ({})", value.status(), value.detail());
        }
    }

    private static CompletableFuture<Result> fetch(String url, String installed, String minecraft) {
        HttpRequest request;
        try {
            URI uri = URI.create(url);
            if (!isWebUri(uri)) return CompletableFuture.completedFuture(Result.of(Status.INVALID_DATA, "unsupported URL " + url));
            request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "BetterXray/" + installed + " (Minecraft " + minecraft + "; " + LOADER + ")")
                .GET().build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Result.of(Status.INVALID_DATA, "bad URL " + url));
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) return Result.of(Status.UNREACHABLE, "HTTP " + response.statusCode());
                byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) return Result.of(Status.INVALID_DATA, "response larger than 1 MiB");
                return evaluate(new String(bytes, StandardCharsets.UTF_8), PROJECT_ID, LOADER, minecraft, installed);
            } catch (IOException e) {
                return Result.of(Status.UNREACHABLE, e.toString());
            }
        });
    }

    /**
     * Pure evaluation of update-check.json:
     * {@code mods.<projectId>.<loader>.<minecraftVersion> = {latest, url}}. A patch release such as
     * 26.3.1 falls back to the 26.3 entry when it has no entry of its own.
     */
    public static Result evaluate(String json, String projectId, String loader, String minecraft, String installed) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return Result.of(Status.INVALID_DATA, "root is not an object");
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return Result.of(Status.INVALID_DATA, "not JSON: " + e.getMessage());
        }
        JsonObject mods = object(root, "mods");
        if (mods == null) return Result.of(Status.INVALID_DATA, "no mods object");
        JsonObject project = object(mods, projectId);
        if (project == null) return Result.of(Status.PROJECT_NOT_LISTED, projectId);
        JsonObject loaders = object(project, loader);
        if (loaders == null) return Result.of(Status.LOADER_NOT_LISTED, loader);
        JsonObject entry = null;
        for (String key : minecraftKeys(minecraft)) if ((entry = object(loaders, key)) != null) break;
        if (entry == null) return Result.of(Status.MINECRAFT_NOT_LISTED, minecraft);
        String latest = string(entry, "latest");
        if (latest == null || latest.isBlank()) return Result.of(Status.INVALID_DATA, "no latest version for " + minecraft);
        String page = safePage(string(entry, "url"));
        int comparison;
        try {
            comparison = compareVersions(latest.trim(), installed);
        } catch (IllegalArgumentException e) {
            return Result.of(Status.INVALID_DATA, e.getMessage());
        }
        Status status = comparison > 0 ? Status.UPDATE_AVAILABLE : comparison == 0 ? Status.UP_TO_DATE : Status.INSTALLED_NEWER;
        return new Result(status, latest.trim(), page, "installed " + installed);
    }

    private static List<String> minecraftKeys(String minecraft) {
        List<String> keys = new ArrayList<>();
        if (minecraft == null) return keys;
        keys.add(minecraft);
        String[] parts = minecraft.split("\\.");
        if (parts.length > 2) keys.add(parts[0] + "." + parts[1]);
        return keys;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    /** The JSON's download page if it is a plain http(s) link, otherwise the CurseForge project page. */
    static String safePage(String url) {
        if (url == null) return FALLBACK_PAGE;
        try {
            URI uri = new URI(url.trim());
            return isWebUri(uri) ? uri.toString() : FALLBACK_PAGE;
        } catch (Exception e) {
            return FALLBACK_PAGE;
        }
    }

    private static boolean isWebUri(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) && uri.getHost() != null;
    }

    /**
     * Compares versions like {@code 1.0.10}, {@code v2.0}, {@code 2.0.0-beta.2} or {@code 1.0.9+build.5}:
     * numeric parts are compared numerically (missing parts count as 0), a pre-release is older
     * than its release, build metadata is ignored. Throws for anything that is not a version.
     */
    public static int compareVersions(String a, String b) {
        Version left = Version.parse(a), right = Version.parse(b);
        int length = Math.max(left.numbers.length, right.numbers.length);
        for (int i = 0; i < length; i++) {
            long x = i < left.numbers.length ? left.numbers[i] : 0, y = i < right.numbers.length ? right.numbers[i] : 0;
            if (x != y) return Long.compare(x, y);
        }
        if (left.pre == null || right.pre == null) return left.pre == null ? (right.pre == null ? 0 : 1) : -1;
        String[] p = left.pre.split("\\."), q = right.pre.split("\\.");
        for (int i = 0; i < Math.min(p.length, q.length); i++) {
            boolean pn = p[i].matches("\\d+"), qn = q[i].matches("\\d+");
            int c = pn && qn ? Long.compare(Long.parseLong(p[i]), Long.parseLong(q[i]))
                : pn ? -1 : qn ? 1 : p[i].compareTo(q[i]);
            if (c != 0) return c;
        }
        return Integer.compare(p.length, q.length);
    }

    private record Version(long[] numbers, String pre) {
        static Version parse(String text) {
            if (text == null) throw new IllegalArgumentException("missing version");
            String value = text.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("v")) value = value.substring(1);
            int plus = value.indexOf('+');
            if (plus >= 0) value = value.substring(0, plus);
            String pre = null;
            int dash = value.indexOf('-');
            if (dash >= 0) {
                pre = value.substring(dash + 1);
                value = value.substring(0, dash);
                if (pre.isEmpty()) pre = null;
            }
            if (!value.matches("\\d{1,9}(\\.\\d{1,9})*")) throw new IllegalArgumentException("not a version: " + text);
            String[] parts = value.split("\\.");
            long[] numbers = new long[parts.length];
            for (int i = 0; i < parts.length; i++) numbers[i] = Long.parseLong(parts[i]);
            return new Version(numbers, pre);
        }
    }

    /** "[Better X-Ray: New Version is available. Update on Curseforge]" with a clickable link. */
    public static Component message(Result update) {
        Component link = Component.literal("Update on Curseforge").withStyle(Style.EMPTY
            .withColor(ChatFormatting.AQUA).withUnderlined(true)
            //? if >=1.21.5 {
            .withClickEvent(new ClickEvent.OpenUrl(URI.create(update.url())))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(
            //?} else {
            /*.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, update.url()))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
            *///?}
                "Better X-Ray " + update.latest() + " (installed: " + modVersion("betterxray") + ")\n" + update.url()))));
        return Component.literal("[Better X-Ray: New Version is available. ").withStyle(ChatFormatting.YELLOW)
            .append(link).append(Component.literal("]").withStyle(ChatFormatting.YELLOW));
    }

    private static String modVersion(String id) {
        return Platform.modVersion(id);
    }

    /** Last update hint shown in chat this session, or {@code null}. */
    public static Component lastMessage() { return lastMessage; }
    /** Latest check result, or {@code null} while running or before the first singleplayer world. */
    public static Result result() { return result; }

    /** Test hook: forget this session's check so the next singleplayer world checks again. */
    public static void resetForTests() {
        started = false;
        handled = false;
        result = null;
        lastMessage = null;
    }
}
