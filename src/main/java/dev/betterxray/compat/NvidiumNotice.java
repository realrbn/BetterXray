package dev.betterxray.compat;

import dev.betterxray.XRayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Nvidium replaces Sodium's terrain renderer with its own mesh shaders: it drops the vertex alpha
 * of faint blocks, skips the patched translucent pipeline and culls sections on the GPU. It is not
 * supported, so the first world of each game start gets a hint in the local chat.
 */
public final class NvidiumNotice {
    private static boolean shown;

    private NvidiumNotice() {}

    /** Called every client tick. */
    public static void tick(Minecraft client) {
        if (shown || client.player == null || !Platform.isModLoaded("nvidium")) return;
        shown = true;
        XRayClient.LOGGER.warn("[XRay] Nvidium {} is installed; it is not supported and can break X-Ray rendering",
            Platform.modVersion("nvidium"));
        // Local chat only: never sent to the (integrated) server or anyone else.
        Mc.localChat(client, Component.translatable("message.betterxray.nvidium").withStyle(ChatFormatting.YELLOW));
    }
}
