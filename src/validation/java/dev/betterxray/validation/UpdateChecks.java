package dev.betterxray.validation;

import com.sun.net.httpserver.HttpServer;
import dev.betterxray.update.UpdateChecker;
import dev.betterxray.update.UpdateChecker.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.betterxray.validation.GuiChecks.check;

/**
 * Update-check fixture: a local HTTP server stands in for update-check.json, so the run needs
 * no internet and can serve every case. With {@code -Dbetterxray.validation.realUpdateCheck=true}
 * the real file on GitHub Pages is used instead (only the first-world check runs then).
 */
final class UpdateChecks {
    private UpdateChecks() {}

    static final boolean REAL = Boolean.getBoolean("betterxray.validation.realUpdateCheck");
    private static HttpServer server;
    private static final AtomicInteger requests = new AtomicInteger();
    private static volatile String body = json("fabric", ClientValidation.MC, "9.9.9", "https://example.com/better-xray");
    private static volatile int code = 200;

    static String json(String loader, String minecraft, String latest, String url) {
        return "{\"schema\":1,\"mods\":{\"1713176\":{\"" + loader + "\":{\"" + minecraft + "\":{\"latest\":\"" + latest + "\",\"url\":\"" + url + "\"}}}}}";
    }

    /** Starts the local server and points the checker at it (before any world is opened). */
    static void install() {
        if (REAL || server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) { throw new IllegalStateException(e); }
        server.createContext("/update-check.json", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, code == 200 ? bytes.length : -1);
            if (code == 200) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        System.setProperty(UpdateChecker.URL_PROPERTY, "http://127.0.0.1:" + server.getAddress().getPort() + "/update-check.json");
    }

    static int requests() { return requests.get(); }

    /** Pure evaluation and version comparison, every edge case of the Fabric test. */
    static String evaluation() {
        String json = json("fabric", ClientValidation.MC, "1.1.0", "https://www.curseforge.com/minecraft/mc-mods/better-xray");
        check(UpdateChecker.evaluate(json, "1713176", "fabric", ClientValidation.MC, "1.0.1").status() == Status.UPDATE_AVAILABLE, "Newer version");
        check(UpdateChecker.evaluate(json, "1713176", "fabric", ClientValidation.MC, "1.1.0").status() == Status.UP_TO_DATE, "Same version");
        check(UpdateChecker.evaluate(json, "1713176", "fabric", ClientValidation.MC, "1.2.0").status() == Status.INSTALLED_NEWER, "Installed newer");
        check(UpdateChecker.evaluate(json, "9999", "fabric", ClientValidation.MC, "1.0.0").status() == Status.PROJECT_NOT_LISTED, "Project missing");
        check(UpdateChecker.evaluate(json, "1713176", "neoforge", ClientValidation.MC, "1.0.0").status() == Status.LOADER_NOT_LISTED, "Loader missing");
        check(UpdateChecker.evaluate(json, "1713176", "fabric", "0.0.1", "1.0.0").status() == Status.MINECRAFT_NOT_LISTED, "MC missing");
        check(UpdateChecker.evaluate(json("fabric", ClientValidation.MC_MINOR, "2.0", "x"), "1713176", "fabric", ClientValidation.MC, "1.0").status() == Status.UPDATE_AVAILABLE,
            ClientValidation.MC + " falls back to a " + ClientValidation.MC_MINOR + " entry");
        check(UpdateChecker.evaluate("not json", "1713176", "fabric", ClientValidation.MC, "1.0").status() == Status.INVALID_DATA, "Broken JSON");
        check(UpdateChecker.evaluate("[]", "1713176", "fabric", ClientValidation.MC, "1.0").status() == Status.INVALID_DATA, "Wrong shape");
        check(UpdateChecker.evaluate(json("fabric", ClientValidation.MC, "soon", "x"), "1713176", "fabric", ClientValidation.MC, "1.0").status() == Status.INVALID_DATA, "Bad latest");
        var unsafe = UpdateChecker.evaluate(json("fabric", ClientValidation.MC, "2.0", "javascript:alert(1)"), "1713176", "fabric", ClientValidation.MC, "1.0");
        check(unsafe.url().equals(UpdateChecker.FALLBACK_PAGE), "Non-http link falls back to CurseForge");
        check(UpdateChecker.compareVersions("1.0.10", "1.0.9") > 0 && UpdateChecker.compareVersions("2.0", "2.0.0") == 0
            && UpdateChecker.compareVersions("v1.2", "1.2") == 0 && UpdateChecker.compareVersions("1.2+build.5", "1.2") == 0
            && UpdateChecker.compareVersions("2.0.0-beta.1", "2.0.0") < 0 && UpdateChecker.compareVersions("2.0.0-beta.2", "2.0.0-beta.10") < 0,
            "Version comparison");
        return "update evaluation ok";
    }

    /** Checks the first-world hint: one request, exact text, only the link clickable, in the chat history. */
    static void firstWorld(Minecraft mc, int requestsBefore) {
        var result = UpdateChecker.result();
        check(result != null, "Update check finished in the first world");
        if (REAL) {
            check(result.status() == Status.UPDATE_AVAILABLE,
                "Real update-check.json: expected an available update for fabric " + ClientValidation.MC + ", got " + result);
        } else {
            check(requests() == requestsBefore + 1, "Exactly one request in the first world, got " + (requests() - requestsBefore));
            check(result.status() == Status.UPDATE_AVAILABLE && result.latest().equals("9.9.9"), "Update available from the local file");
        }
        Component message = UpdateChecker.lastMessage();
        check(message != null && message.getString().equals("[Better X-Ray: New Version is available. Update on Curseforge]"), "Hint text");
        check(message.getStyle().getClickEvent() == null, "Text outside the link is not clickable");
        var link = message.getSiblings().get(0);
        var click = link.getStyle().getClickEvent();
        check(link.getString().equals("Update on Curseforge") && click != null && click.getAction() == ClickEvent.Action.OPEN_URL
            && click.getValue().equals(result.url()), "Only the link opens the download page");
        check(link.getStyle().getHoverEvent() != null, "Link has a hover text");
        check(chat(mc).stream().anyMatch(line -> line.content() == message), "Hint is in the local chat history");
    }

    /** Next server-side scenario: resets the checker so the open world checks again. */
    static void serve(String json, int status) {
        body = json;
        code = status;
        UpdateChecker.resetForTests();
    }

    /** The open world re-checked and showed nothing new; returns the status for the message. */
    static Status expectSilent(Minecraft mc, int chatBefore, Status expected) {
        var result = UpdateChecker.result();
        check(result != null && result.status() == expected, "Expected " + expected + ", got " + result);
        check(UpdateChecker.lastMessage() == null && chat(mc).size() == chatBefore, expected + " adds no chat message");
        return result.status();
    }

    static void stopServer() {
        if (server != null) server.stop(0);
    }

    static List<GuiMessage> chat(Minecraft mc) {
        return ((dev.betterxray.validation.mixin.ChatAccess)mc.gui.getChat()).betterxrayValidation$messages();
    }
}
