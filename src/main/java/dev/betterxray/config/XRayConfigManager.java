package dev.betterxray.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.betterxray.XRayClient;
import dev.betterxray.compat.Platform;
import java.io.IOException;
import java.nio.file.*;

public final class XRayConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.configDir().resolve("betterxray.json");
    private static XRayConfig config = new XRayConfig();
    public static XRayConfig get() { return config; }

    public static void load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                // 1.3.2 stored Hide Mobs as a switch; on hid every mob.
                if (root.get("hideMobs") instanceof com.google.gson.JsonPrimitive old && old.isBoolean())
                    root.addProperty("hideMobs", (old.getAsBoolean() ? XRayConfig.HideMobs.ALL : XRayConfig.HideMobs.OFF).name());
                XRayConfig loaded = GSON.fromJson(root, XRayConfig.class);
                if (loaded != null) {
                    if (root.has("visibleBlocks") && !root.has("presets")) loaded.migrateLegacyVisibleBlocks(loaded.visibleBlocks);
                    if (!root.has("mobEspGroups")) loaded.migrateLegacyMobEsp();
                    config = loaded;
                }
            } catch (Exception e) {
                XRayClient.LOGGER.warn("[XRay] Invalid configuration; preserving a backup and using defaults", e);
                try { Files.copy(FILE, FILE.resolveSibling("betterxray.invalid-" + System.currentTimeMillis() + ".json")); }
                catch (IOException backupError) { XRayClient.LOGGER.warn("[XRay] Could not back up config", backupError); }
            }
        }
        config.sanitize();
    }

    public static void reset() { config = new XRayConfig(); }

    public static void save() {
        config.sanitize();
        Path tmp = FILE.resolveSibling("betterxray.json.tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(tmp, GSON.toJson(config));
            try { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { XRayClient.LOGGER.error("[XRay] Could not save configuration", e); }
    }
}
