package dev.betterxray.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Version-neutral access to client APIs that moved between Minecraft generations. */
public final class Mc {
    /** Packed lightmap coordinates for full block and sky light. */
    public static final int FULL_BRIGHT = 0xF000F0;

    private Mc() {}

    public static Screen screen(Minecraft client) {
        //? if >=26.1 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
        *///?}
    }

    public static Screen screen() { return screen(Minecraft.getInstance()); }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.1 {
        client.gui.setScreen(screen);
        //?} else {
        /*client.setScreen(screen);
        *///?}
    }

    public static void setScreen(Screen screen) { setScreen(Minecraft.getInstance(), screen); }

    /** Action-bar message above the hotbar. */
    public static void overlay(Minecraft client, Component message) {
        //? if >=26.1 {
        client.gui.hud.setOverlayMessage(message, false);
        //?} else {
        /*client.gui.setOverlayMessage(message, false);
        *///?}
    }

    /** Adds a line to the local chat only; nothing is sent to a server. */
    public static void localChat(Minecraft client, Component message) {
        //? if >=26.1 {
        client.gui.hud.getChat().addClientSystemMessage(message);
        //?} else {
        /*client.gui.getChat().addMessage(message);
        *///?}
    }

    /** Registered block for an ID such as {@code minecraft:diamond_ore}, or null if unknown/invalid. */
    public static net.minecraft.world.level.block.Block block(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(key)) return null;
        //? if >=1.21.2 {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(key);
        //?} else {
        /*return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key);
        *///?}
    }

    /** Registered entity type for an ID, or null if unknown/invalid. */
    public static net.minecraft.world.entity.EntityType<?> entityType(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return null;
        //? if >=1.21.2 {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(key);
        //?} else {
        /*return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(key);
        *///?}
    }

    public static Identifier id(String namespace, String path) {
        //? if >=1.21 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new Identifier(namespace, path);
        *///?}
    }
}
