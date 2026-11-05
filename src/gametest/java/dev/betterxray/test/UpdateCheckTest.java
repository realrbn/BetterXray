package dev.betterxray.test;

import dev.betterxray.compat.Mc;
import com.sun.net.httpserver.HttpServer;
import dev.betterxray.update.UpdateChecker;
import dev.betterxray.update.UpdateChecker.Status;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Update checker: pure JSON/version evaluation for every edge case, then the real flow against a
 * local HTTP server: no request before a world, one request in the first singleplayer world,
 * clickable local chat hint, no second request in the same session, unreachable server, HTTP
 * errors and an installed build newer than the listed one (no hint).
 */
public final class UpdateCheckTest implements FabricClientGameTest {
    private static final String PAGE = "https://www.curseforge.com/minecraft/mc-mods/better-xray";
    private static final String TEXT = "[Better X-Ray: New Version is available. Update on Curseforge]";

    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null) return;
        if (System.getProperty("betterxray.test.persistence") != null) return;
        checkEvaluation();
        checkVersions();
        try {
            runServerFlow(context);
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            System.setProperty(UpdateChecker.URL_PROPERTY, "off");
            context.runOnClient(client -> UpdateChecker.resetForTests());
        }
    }

    private static String json(String latest, String url) {
        String entry = "{\"latest\": \"" + latest + "\"" + (url == null ? "" : ", \"url\": \"" + url + "\"") + "}";
        return "{\"schema\": 1, \"mods\": {"
            + "\"1699797\": {\"fabric\": {\"26.3\": {\"latest\": \"9.9.9\", \"url\": \"https://example.com/other\"}}},"
            + "\"1713176\": {\"fabric\": {\"1.20.1\": " + entry + ", \"1.21.1\": " + entry + ", \"1.21.11\": " + entry + ", \"26.2\": " + entry + ", \"26.3\": " + entry + "},"
            + " \"forge\": {\"1.19.2\": {\"latest\": \"2.0.0\", \"url\": \"" + PAGE + "\"}}}}}";
    }

    private static void expect(UpdateChecker.Result result, Status status, String what) {
        if (result.status() != status) throw new AssertionError(what + ": expected " + status + " but got " + result);
    }

    private static void checkEvaluation() {
        String placeholder = json("2.0.0", PAGE);
        var update = UpdateChecker.evaluate(placeholder, "1713176", "fabric", "26.3", "1.0.10");
        expect(update, Status.UPDATE_AVAILABLE, "newer listed version");
        if (!update.latest().equals("2.0.0") || !update.url().equals(PAGE)) throw new AssertionError("Wrong entry used: " + update);
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "26.2", "1.0.10"), Status.UPDATE_AVAILABLE, "26.2 entry");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "26.3", "2.0.0"), Status.UP_TO_DATE, "same version");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "26.3", "2.0"), Status.UP_TO_DATE, "2.0 == 2.0.0");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "26.3", "2.1.0"), Status.INSTALLED_NEWER, "local build newer");
        expect(UpdateChecker.evaluate(placeholder, "9999999", "fabric", "26.3", "1.0.10"), Status.PROJECT_NOT_LISTED, "unknown project");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "quilt", "26.3", "1.0.10"), Status.LOADER_NOT_LISTED, "unknown loader");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "27.1", "1.0.10"), Status.MINECRAFT_NOT_LISTED, "unknown Minecraft");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "1.19.2", "1.0.10"), Status.MINECRAFT_NOT_LISTED, "Forge-only version");
        expect(UpdateChecker.evaluate(placeholder, "1713176", "fabric", "26.3.1", "1.0.10"), Status.UPDATE_AVAILABLE, "patch falls back to 26.3");
        expect(UpdateChecker.evaluate("{", "1713176", "fabric", "26.3", "1.0.10"), Status.INVALID_DATA, "truncated JSON");
        expect(UpdateChecker.evaluate("<html>404</html>", "1713176", "fabric", "26.3", "1.0.10"), Status.INVALID_DATA, "HTML page");
        expect(UpdateChecker.evaluate("[]", "1713176", "fabric", "26.3", "1.0.10"), Status.INVALID_DATA, "array root");
        expect(UpdateChecker.evaluate("{\"schema\": 1}", "1713176", "fabric", "26.3", "1.0.10"), Status.INVALID_DATA, "no mods");
        expect(UpdateChecker.evaluate("{\"mods\": {\"1713176\": {\"fabric\": {\"26.3\": {\"url\": \"" + PAGE + "\"}}}}}",
            "1713176", "fabric", "26.3", "1.0.10"), Status.INVALID_DATA, "missing latest");
        expect(UpdateChecker.evaluate(json("soon", PAGE), "1713176", "fabric", "26.3", "1.0.10"), Status.INVALID_DATA, "non-version latest");
        expect(UpdateChecker.evaluate(json("2.0.0", PAGE), "1713176", "fabric", "26.3", "dev"), Status.INVALID_DATA, "non-version installed");
        if (!UpdateChecker.evaluate(json("2.0.0", null), "1713176", "fabric", "26.3", "1.0.10").url().equals(PAGE))
            throw new AssertionError("Missing url must fall back to the CurseForge page");
        if (!UpdateChecker.evaluate(json("2.0.0", "javascript:alert(1)"), "1713176", "fabric", "26.3", "1.0.10").url().equals(PAGE))
            throw new AssertionError("Non-web url must fall back to the CurseForge page");
    }

    private static void checkVersions() {
        String[][] ordered = {{"1.0.9", "1.0.10"}, {"1.0.10", "2.0.0"}, {"2.0.0-beta.1", "2.0.0"}, {"2.0.0-beta.1", "2.0.0-beta.2"},
            {"2.0.0-alpha", "2.0.0-beta"}, {"2.0.0-beta.2", "2.0.0-beta.10"}, {"1.9", "1.10"}};
        for (String[] pair : ordered)
            if (UpdateChecker.compareVersions(pair[0], pair[1]) >= 0 || UpdateChecker.compareVersions(pair[1], pair[0]) <= 0)
                throw new AssertionError("Version order wrong: " + pair[0] + " < " + pair[1]);
        for (String[] pair : new String[][]{{"2.0", "2.0.0"}, {"v1.0.9", "1.0.9"}, {"1.0.9+build.7", "1.0.9"}})
            if (UpdateChecker.compareVersions(pair[0], pair[1]) != 0) throw new AssertionError("Versions should be equal: " + pair[0] + " " + pair[1]);
    }

    private void runServerFlow(ClientGameTestContext context) throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>(json("99.0.0", PAGE));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/update-check.json", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            // 1. Title screen: no request yet. First singleplayer world: one request, clickable hint.
            System.setProperty(UpdateChecker.URL_PROPERTY, base + "/update-check.json");
            context.runOnClient(client -> UpdateChecker.resetForTests());
            context.waitTicks(20);
            if (requests.get() != 0 || UpdateChecker.result() != null) throw new AssertionError("Update check ran without a world");
            try (var world = TestCompat.worldBuilder(context).create()) {
                context.waitFor(client -> UpdateChecker.result() != null, 400);
                context.waitTicks(2);
                context.runOnClient(client -> {
                    expect(UpdateChecker.result(), Status.UPDATE_AVAILABLE, "local server");
                    Component message = UpdateChecker.lastMessage();
                    if (message == null || !message.getString().equals(TEXT)) throw new AssertionError("Wrong chat hint: " + message);
                    Component link = message.getSiblings().get(0);
                    if (!link.getString().equals("Update on Curseforge")
                        || !(link.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl open) || !open.uri().toString().equals(PAGE))
                        throw new AssertionError("'Update on Curseforge' is not a link to " + PAGE);
                    if (message.getStyle().getClickEvent() != null) throw new AssertionError("Only the link part may be clickable");
                    if (!client.hasSingleplayerServer()) throw new AssertionError("Hint shown outside singleplayer");
                });
                context.runOnClient(client -> {
                    // The hint must really be in the local chat history (client-side system message).
                    if (!chatContains(client, TEXT)) throw new AssertionError("Hint missing from the local chat");
                    TestCompat.openChat(client);
                });
                context.waitTicks(5);
                context.takeScreenshot("update-01-chat-hint");
                context.runOnClient(client -> Mc.setScreen(client, null));
                if (requests.get() != 1) throw new AssertionError("Expected exactly one request, got " + requests.get());
            }
            // 2. Second world in the same session: no new request, no new hint.
            try (var world = TestCompat.worldBuilder(context).create()) {
                context.waitTicks(40);
                if (requests.get() != 1) throw new AssertionError("Update check repeated in the same session");
            }
            // 3. Installed build newer than the listed one: checked, but no hint.
            body.set(json("0.0.1", PAGE));
            expectNoHint(context, Status.INSTALLED_NEWER);
            // 4. Project ID, Minecraft version missing; broken JSON; HTTP 404; server offline.
            body.set("{\"schema\": 1, \"mods\": {\"1699797\": {\"fabric\": {}}}}");
            expectNoHint(context, Status.PROJECT_NOT_LISTED);
            body.set("{\"schema\": 1, \"mods\": {\"1713176\": {\"fabric\": {\"1.0\": {\"latest\": \"99.0.0\"}}}}}");
            expectNoHint(context, Status.MINECRAFT_NOT_LISTED);
            body.set("{\"mods\": ");
            expectNoHint(context, Status.INVALID_DATA);
            System.setProperty(UpdateChecker.URL_PROPERTY, base + "/missing.json");
            expectNoHint(context, Status.UNREACHABLE);
        } finally {
            server.stop(0);
        }
        System.setProperty(UpdateChecker.URL_PROPERTY, base + "/update-check.json");
        expectNoHint(context, Status.UNREACHABLE);
        System.out.println("[BetterXray Update] all update-check cases passed (installed "
            + FabricLoader.getInstance().getModContainer("betterxray").orElseThrow().getMetadata().getVersion().getFriendlyString() + ")");
    }

    private static boolean chatContains(net.minecraft.client.Minecraft client, String text) {
        return TestCompat.chatContains(client, text);
    }

    private static void expectNoHint(ClientGameTestContext context, Status status) {
        context.runOnClient(client -> UpdateChecker.resetForTests());
        try (var world = TestCompat.worldBuilder(context).create()) {
            context.waitFor(client -> UpdateChecker.result() != null, 600);
            context.waitTicks(2);
            context.runOnClient(client -> {
                expect(UpdateChecker.result(), status, "server flow");
                if (UpdateChecker.lastMessage() != null) throw new AssertionError("Hint shown for " + status);
            });
        }
    }
}
