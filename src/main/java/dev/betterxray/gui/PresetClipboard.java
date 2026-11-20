package dev.betterxray.gui;

//? if fabric && >=26.3 {
import dev.betterxray.XRayClient;
import dev.betterxray.compat.Mc;
import dev.betterxray.config.PresetTransfer;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.render.XRayRenderController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Export/Import buttons of the active preset (Fabric 26.3): the preset goes to and comes from the
 * system clipboard as one line of JSON ({@link PresetTransfer}). Results are shown as toasts, which
 * stay visible over the open screen. An import is applied only after a confirmation that names the
 * incoming preset, its entry count and the skipped entries.
 */
public final class PresetClipboard {
    private PresetClipboard() {}

    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    public static void exportPreset(Minecraft client, int index) {
        var preset = XRayConfigManager.get().presets.get(index);
        try {
            client.keyboardHandler.setClipboard(PresetTransfer.export(preset));
        } catch (RuntimeException e) {
            XRayClient.LOGGER.warn("Could not write the preset to the clipboard", e);
            toast(client, "message.betterxray.preset_export.failed_title",
                Component.translatable("message.betterxray.preset_export.failed"));
            return;
        }
        toast(client, "message.betterxray.preset_export.title",
            Component.translatable("message.betterxray.preset_export.done", preset.name, preset.visibleBlocks.size()));
    }

    /** Reads the clipboard; on success asks for confirmation and returns to {@code parent} either way. */
    public static void importPreset(Minecraft client, Screen parent, int index) {
        String text;
        try {
            text = client.keyboardHandler.getClipboard();
        } catch (RuntimeException e) {
            XRayClient.LOGGER.warn("Could not read the clipboard", e);
            text = null;
        }
        PresetTransfer.Result result = PresetTransfer.parse(text);
        if (result instanceof PresetTransfer.Failure failure) {
            fail(client, Component.translatable(failure.key(), failure.args()));
            return;
        }
        var imported = (PresetTransfer.Imported)result;
        int skipped = imported.skippedInvalid() + imported.skippedUnknown();
        if (imported.blocks().isEmpty() && skipped > 0) {
            fail(client, Component.translatable("message.betterxray.preset_import.no_valid_entries", skipped));
            return;
        }
        var config = XRayConfigManager.get();
        String current = config.presets.get(index).name;
        String incoming = imported.name() != null ? imported.name() : current;
        Component details = Component.translatable("message.betterxray.preset_import.confirm", incoming, imported.blocks().size());
        if (skipped > 0) details = details.copy().append("\n").append(
            Component.translatable("message.betterxray.preset_import.skipped", imported.skippedInvalid(), imported.skippedUnknown()));
        Mc.setScreen(client, new ConfirmScreen(accepted -> {
            if (accepted) {
                PresetTransfer.apply(config, index, imported);
                XRayConfigManager.save();
                XRayRenderController.apply();
                toast(client, "message.betterxray.preset_import.done_title",
                    Component.translatable("message.betterxray.preset_import.done", incoming, imported.blocks().size()));
            }
            Mc.setScreen(client, parent);
        }, Component.translatable("message.betterxray.preset_import.confirm_title", current), details));
    }

    private static void fail(Minecraft client, Component message) {
        toast(client, "message.betterxray.preset_import.failed_title", message);
    }

    private static void toast(Minecraft client, String titleKey, Component message) {
        SystemToast.addOrUpdate(client.gui.toastManager(), TOAST, Component.translatable(titleKey), message);
    }
}
//?}
