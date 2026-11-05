package dev.betterxray.test;

import dev.betterxray.compat.Mc;
import com.mojang.blaze3d.platform.InputConstants;
import dev.betterxray.config.*;
import dev.betterxray.gui.*;
import dev.betterxray.render.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.RemotePlayer;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mob ESP groups, per-mob overrides and the grouped picker, plus an actual world/GUI fixture with
 * mobs completely hidden by opaque terrain and X-Ray off.
 */
public final class MobEspTest implements FabricClientGameTest {
    private static final String ZOMBIE = "minecraft:zombie", COW = "minecraft:cow", SKELETON = "minecraft:skeleton";
    private static final String DIAMOND = "minecraft:diamond", EMERALD = "minecraft:emerald";
    private static final String[] OBJECTS = {"minecraft:armor_stand", "minecraft:minecart", "minecraft:item", "minecraft:item_frame"};
    private static final int OTHER_PLAYER_ID = Integer.MAX_VALUE - 7;

    @Override public void runTest(ClientGameTestContext context) {
        if (TestCompat.shaderPack() != null) return;
        if (System.getProperty("betterxray.test.persistence") != null) return;
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            checkClassification();
            checkConfigRepair();
            checkResolution();
            checkLegacyMigration();
            XRayConfigManager.reset();
        });
        runPickerGui(context);
        runSettingsGui(context);
        runWorldFixture(context);
    }

    // ---------------------------------------------------------------- classification & config

    private static void checkClassification() {
        Map<String, MobEspGroup> expected = Map.ofEntries(
            Map.entry(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES), Map.entry("minecraft:creeper", MobEspGroup.OVERWORLD_ENEMIES),
            Map.entry(COW, MobEspGroup.ANIMALS), Map.entry("minecraft:sulfur_cube", MobEspGroup.ANIMALS),
            Map.entry("minecraft:villager", MobEspGroup.VILLAGERS), Map.entry("minecraft:iron_golem", MobEspGroup.VILLAGERS),
            Map.entry("minecraft:snow_golem", MobEspGroup.VILLAGERS), Map.entry("minecraft:copper_golem", MobEspGroup.VILLAGERS),
            Map.entry("minecraft:piglin", MobEspGroup.NETHER_ENEMIES), Map.entry("minecraft:wither", MobEspGroup.NETHER_ENEMIES),
            Map.entry("minecraft:enderman", MobEspGroup.END), Map.entry("minecraft:ender_dragon", MobEspGroup.END),
            Map.entry("minecraft:pillager", MobEspGroup.ILLAGERS), Map.entry("minecraft:cod", MobEspGroup.AQUATIC),
            Map.entry("minecraft:wolf", MobEspGroup.NEUTRAL), Map.entry("minecraft:player", MobEspGroup.PLAYERS));
        expected.forEach((id, group) -> {
            EntityType<?> type = type(id);
            if (!MobEspRenderer.isMobType(type)) throw new AssertionError("Mob missing from ESP picker: " + id);
            if (MobEspRenderer.group(type) != group) throw new AssertionError("Wrong ESP group for " + id + ": " + MobEspRenderer.group(type));
        });
        for (String id : new String[]{"armor_stand", "mannequin", "item", "arrow", "minecart", "oak_boat", "item_frame",
                "glow_item_frame", "painting", "experience_orb", "tnt", "end_crystal", "falling_block"}) {
            EntityType<?> object = type("minecraft:" + id);
            if (MobEspRenderer.isMobType(object)) throw new AssertionError("Object classified as mob: " + id);
            if (!MobEspRenderer.isEspType(object) || MobEspRenderer.group(object) != MobEspGroup.OBJECTS)
                throw new AssertionError("Object missing from the Objects ESP group: " + id);
        }
        if (MobEspRenderer.isEspType(type("minecraft:marker"))) throw new AssertionError("Server-only marker offered in ESP picker");
        if (new XRayConfig().espGroup(MobEspGroup.OBJECTS).style != XRayConfig.EspStyle.BOX)
            throw new AssertionError("Objects group must default to boxes");
        // Every vanilla entity type is offered: mobs in their mob group, everything else in Objects.
        int objects = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (!id.startsWith("minecraft:") || id.equals("minecraft:marker")) continue;
            if (!MobEspRenderer.isEspType(type)) throw new AssertionError("Entity type missing from ESP picker: " + id);
            boolean object = MobEspRenderer.group(type) == MobEspGroup.OBJECTS;
            if (object == MobEspRenderer.isMobType(type)) throw new AssertionError("Wrong mob/object split for " + id);
            if (object) objects++;
        }
        System.out.println("[BetterXray ESP] " + objects + " vanilla object types in the Objects group");
        // Every vanilla mob of this version must have an explicit, deliberate group.
        List<String> unclassified = new ArrayList<>();
        int count = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (!MobEspRenderer.isMobType(type) || !id.startsWith("minecraft:")) continue;
            count++;
            if (MobEspGroup.explicit(id) == null) unclassified.add(id);
        }
        if (!unclassified.isEmpty()) throw new AssertionError("Vanilla mobs without ESP group: " + unclassified);
        System.out.println("[BetterXray ESP] " + count + " vanilla ESP types (mobs + player) classified into " + MobEspGroup.values().length + " groups");
        if (MobEspGroup.fallback("MONSTER") != MobEspGroup.OVERWORLD_ENEMIES || MobEspGroup.fallback("WATER_AMBIENT") != MobEspGroup.AQUATIC
            || MobEspGroup.fallback("CREATURE") != MobEspGroup.ANIMALS || MobEspGroup.fallback("MISC") != MobEspGroup.OTHER)
            throw new AssertionError("Modded mob fallback groups wrong");
    }

    private static void checkConfigRepair() {
        var corrupt = new XRayConfig();
        corrupt.mobEsp = null;
        corrupt.mobEspGroups = null;
        corrupt.sanitize();
        if (corrupt.mobEspGroups.size() != MobEspGroup.values().length) throw new AssertionError("ESP groups not repaired");
        var setting = new XRayConfig.MobEspSettings();
        setting.color = 0x123456;
        corrupt.mobEsp.put("missing:mob", setting);
        corrupt.mobEsp.put("missing:null", null);
        corrupt.mobEsp.put("missing:empty", new XRayConfig.MobEspSettings());
        corrupt.mobEspGroups.get("animals").style = null;
        corrupt.mobEspGroups.get("animals").color = 0x00ff00;
        corrupt.mobEspGroups.remove("end");
        corrupt.mobEspGroups.put("unknown_group", new XRayConfig.MobEspGroupSettings());
        corrupt.sanitize();
        if (setting.style != null || setting.color != 0xff123456 || corrupt.mobEsp.containsKey("missing:null")
            || corrupt.mobEsp.containsKey("missing:empty") || !corrupt.mobEsp.containsKey("missing:mob"))
            throw new AssertionError("ESP mob config repair failed");
        var animals = corrupt.mobEspGroups.get("animals");
        if (animals.style != XRayConfig.EspStyle.OUTLINE || animals.color != 0xff00ff00 || !corrupt.mobEspGroups.containsKey("end")
            || corrupt.mobEspGroups.containsKey("unknown_group") || corrupt.mobEspGroups.get("end").color != MobEspGroup.END.defaultColor)
            throw new AssertionError("ESP group config repair failed");

        // Ranges: 1.0.6 chunk range migrates to blocks, out-of-range values are clamped, zero stays "no limit".
        var ranges = new XRayConfig();
        ranges.highlightRangeChunks = 3;
        ranges.mobEspRangeBlocks = 5000;
        ranges.sanitize();
        if (ranges.highlightRangeBlocks != 48 || ranges.highlightRangeChunks != null || ranges.mobEspRangeBlocks != XRayConfig.MAX_RANGE_BLOCKS)
            throw new AssertionError("Range migration/clamp failed");
        ranges.highlightRangeBlocks = -4;
        ranges.mobEspRangeBlocks = 3;
        ranges.sanitize();
        if (ranges.highlightRangeBlocks != 0 || ranges.mobEspRangeBlocks != XRayConfig.RANGE_STEP_BLOCKS)
            throw new AssertionError("Range repair failed");
        if (ranges.getHighlightRangeBlocks(12) != 192 || ranges.highlightRangeLimited(12)) throw new AssertionError("Highlight range default wrong");
        ranges.highlightRangeBlocks = 400;
        if (ranges.getHighlightRangeBlocks(12) != 192 || ranges.highlightRangeLimited(12) || !ranges.highlightRangeLimited(32))
            throw new AssertionError("Highlight range not capped at render distance");
        ranges.mobEspRangeBlocks = 0;
        if (!ranges.inEspRange(1e12)) throw new AssertionError("Unlimited ESP range rejected a mob");
        ranges.mobEspRangeBlocks = 16;
        if (!ranges.inEspRange(16 * 16) || ranges.inEspRange(16 * 16 + 1)) throw new AssertionError("ESP range check wrong");
    }

    private static void checkResolution() {
        var config = new XRayConfig();
        config.sanitize();
        var overworld = config.espGroup(MobEspGroup.OVERWORLD_ENEMIES);
        if (config.resolveEsp(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES) != null) throw new AssertionError("ESP must default to off");
        overworld.enabled = true;
        overworld.style = XRayConfig.EspStyle.BOX;
        overworld.color = 0xff112233;
        expect(config.resolveEsp(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.BOX, 0xff112233, "group inheritance");
        var zombie = new XRayConfig.MobEspSettings();
        zombie.color = 0xff445566;
        config.mobEsp.put(ZOMBIE, zombie);
        expect(config.resolveEsp(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.BOX, 0xff445566, "color override");
        zombie.style = XRayConfig.EspStyle.OUTLINE;
        expect(config.resolveEsp(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.OUTLINE, 0xff445566, "style override");
        zombie.enabled = false;
        if (config.resolveEsp(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES) != null) throw new AssertionError("Mob off override ignored");
        expect(config.resolveEsp("minecraft:husk", MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.BOX, 0xff112233, "sibling unaffected");
        overworld.enabled = false;
        var cow = new XRayConfig.MobEspSettings();
        cow.enabled = true;
        config.mobEsp.put(COW, cow);
        expect(config.resolveEsp(COW, MobEspGroup.ANIMALS), XRayConfig.EspStyle.OUTLINE, MobEspGroup.ANIMALS.defaultColor, "mob on in disabled group");
        if (config.clearEspOverrides(id -> id.equals(ZOMBIE)) != 1 || config.mobEsp.containsKey(ZOMBIE) || !config.mobEsp.containsKey(COW))
            throw new AssertionError("Clearing group overrides failed");

        // Dropped items follow the item entity, which follows Objects; single items override it.
        var objects = config.espGroup(MobEspGroup.OBJECTS);
        objects.enabled = true;
        objects.color = 0xff111111;
        expect(config.resolveItemEsp(DIAMOND), XRayConfig.EspStyle.BOX, 0xff111111, "item inherits Objects");
        var itemEntity = new XRayConfig.MobEspSettings();
        itemEntity.enabled = false;
        config.mobEsp.put(XRayConfig.ITEM_ENTITY, itemEntity);
        if (config.resolveItemEsp(DIAMOND) != null) throw new AssertionError("Items must follow the item entity's off switch");
        var diamond = new XRayConfig.MobEspSettings();
        diamond.enabled = true;
        diamond.color = 0xff00ffff;
        config.itemEsp.put(DIAMOND, diamond);
        expect(config.resolveItemEsp(DIAMOND), XRayConfig.EspStyle.BOX, 0xff00ffff, "single item on");
        if (config.resolveItemEsp(EMERALD) != null) throw new AssertionError("Other items must stay off");
        config.itemEsp.put(EMERALD, new XRayConfig.MobEspSettings());
        config.sanitize();
        if (config.itemEsp.containsKey(EMERALD) || !config.itemEsp.containsKey(DIAMOND))
            throw new AssertionError("Sanitize must drop empty item overrides only");
    }

    /** The first 1.0.6 format (every entry: enabled/style/color, no groups) must load correctly. */
    private static void checkLegacyMigration() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("betterxray.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                {"enabled": false, "mobEsp": {
                  "minecraft:zombie": {"enabled": true, "style": "BOX", "color": -43691},
                  "minecraft:cow": {"enabled": false, "style": "OUTLINE", "color": -16711681},
                  "minecraft:pig": {"enabled": false, "style": "OUTLINE", "color": -11141291},
                  "minecraft:creeper": {"enabled": true, "style": "OUTLINE", "color": -16711681},
                  "othermod:beast": {"enabled": true, "style": "BOX", "color": -256}}}
                """);
        } catch (IOException e) { throw new AssertionError(e); }
        XRayConfigManager.reset();
        XRayConfigManager.load();
        var config = XRayConfigManager.get();
        expect(config.resolveEsp(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.BOX, 0xffff5555, "migrated zombie");
        expect(config.resolveEsp("minecraft:creeper", MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.OUTLINE, 0xff00ffff, "migrated creeper");
        if (config.mobEsp.containsKey(COW)) throw new AssertionError("Disabled default legacy entry must follow its group");
        var pig = config.mobEsp.get("minecraft:pig");
        if (pig == null || pig.enabled != null || pig.color != 0xff55ff55) throw new AssertionError("Legacy custom color lost or still forced off");
        if (!Boolean.TRUE.equals(config.mobEsp.get("othermod:beast").enabled)) throw new AssertionError("Unavailable modded ID lost");
        if (config.mobEspGroups.values().stream().anyMatch(g -> g.enabled)) throw new AssertionError("Migration must not enable groups");
        // Saving writes the new format, which must load back identically without migrating again.
        config.espGroup(MobEspGroup.ANIMALS).enabled = true;
        XRayConfigManager.save();
        XRayConfigManager.reset();
        XRayConfigManager.load();
        config = XRayConfigManager.get();
        if (!config.espGroup(MobEspGroup.ANIMALS).enabled || config.mobEsp.get("minecraft:pig").enabled != null
            || !config.mobEsp.get(ZOMBIE).enabled) throw new AssertionError("ESP group persistence failed");
        XRayConfigManager.reset();
        XRayConfigManager.save();
    }

    // ---------------------------------------------------------------- picker GUI (title screen)

    private void runPickerGui(ClientGameTestContext context) {
        context.setScreen(() -> new XRayConfigScreen(null));
        context.waitTick();
        context.runOnClient(client -> button(client, "Mob ESP").onPress(null));
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspScreen)) throw new AssertionError("ESP menu button failed");
            var rows = list(client).children();
            // Order: header, its mobs, next header ... in enum order, no group missing or repeated.
            MobEspGroup current = null;
            List<MobEspGroup> headers = new ArrayList<>();
            for (var row : rows) {
                if (row instanceof MobEspScreen.MobList.GroupRow group) { current = group.group(); headers.add(current); }
                else if (!(row instanceof MobEspScreen.MobList.MobRow mob) || mob.group() != current)
                    throw new AssertionError("Mob row outside its group: " + row.getNarration().getString());
            }
            List<MobEspGroup> expected = new ArrayList<>();
            for (MobEspGroup group : MobEspGroup.values()) if (!list(client).mobsOf(group).isEmpty()) expected.add(group);
            if (!headers.equals(expected) || headers.contains(MobEspGroup.OTHER)) throw new AssertionError("Group headers wrong: " + headers);
            espRangeSlider(client, "option.betterxray.range_unlimited", -1);
        });
        TestCompat.screenshot(context, "esp-picker-groups");
        // The range slider is a real widget: drag it fully left (8 blocks), then fully right (unlimited).
        var slider = context.computeOnClient(client -> espRangeSlider(client, "option.betterxray.range_unlimited", -1));
        if (slider.getY() + slider.getHeight() > context.computeOnClient(client -> button(client, "ESP:").getY())
            || context.computeOnClient(client -> list(client).getBottom()) > slider.getY())
            throw new AssertionError("ESP range slider overlaps list or footer");
        clickAt(context, slider.getX() + 1, slider.getY() + slider.getHeight() / 2);
        context.runOnClient(client -> {
            if (XRayConfigManager.get().mobEspRangeBlocks != XRayConfig.RANGE_STEP_BLOCKS)
                throw new AssertionError("ESP range slider click did not select 8 blocks: " + XRayConfigManager.get().mobEspRangeBlocks);
            espRangeSlider(client, "option.betterxray.range_blocks", XRayConfig.RANGE_STEP_BLOCKS);
        });
        TestCompat.screenshot(context, "esp-picker-range-8");
        clickAt(context, slider.getX() + slider.getWidth() - 1, slider.getY() + slider.getHeight() / 2);
        context.runOnClient(client -> {
            if (XRayConfigManager.get().mobEspRangeBlocks != 0) throw new AssertionError("ESP range slider did not return to unlimited");
        });

        // Real mouse clicks on the first header (Animals): toggle on the right, collapse arrow on the left.
        context.runOnClient(client -> {
            var rows = list(client).children();
            if (!(rows.get(0) instanceof MobEspScreen.MobList.GroupRow players) || players.group() != MobEspGroup.PLAYERS
                || !(rows.get(1) instanceof MobEspScreen.MobList.MobRow player) || !player.id().equals("minecraft:player")
                || !(rows.get(2) instanceof MobEspScreen.MobList.GroupRow animals) || animals.group() != MobEspGroup.ANIMALS)
                throw new AssertionError("Players group with the player row must come first, then Animals");
        });
        var header = context.computeOnClient(client -> list(client).groupRow(MobEspGroup.ANIMALS));
        clickAt(context, header.getContentRight() - 8, header.getContentYMiddle());
        context.runOnClient(client -> {
            if (!XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).enabled) throw new AssertionError("Group toggle click failed");
            if (!XRayConfigManager.get().espEnabled(COW, MobEspGroup.ANIMALS) || XRayConfigManager.get().espEnabled(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES))
                throw new AssertionError("Group toggle affected the wrong mobs");
            if (!XRayConfigManager.get().mobEsp.isEmpty()) throw new AssertionError("Group toggle must not create mob overrides");
        });
        TestCompat.screenshot(context, "esp-picker-animals-enabled");
        clickAt(context, header.getContentX() + 4, header.getContentYMiddle());
        context.runOnClient(client -> {
            if (!header.collapsed() || list(client).children().stream().anyMatch(r -> r instanceof MobEspScreen.MobList.MobRow m && m.group() == MobEspGroup.ANIMALS))
                throw new AssertionError("Collapsing Animals failed");
            var rows = list(client).children();
            if (!(rows.get(rows.indexOf(header) + 1) instanceof MobEspScreen.MobList.GroupRow)) throw new AssertionError("Next group must follow a collapsed one");
        });
        TestCompat.screenshot(context, "esp-picker-animals-collapsed");
        context.getInput().pressKey(InputConstants.KEY_RIGHT);
        context.waitTick();
        if (context.computeOnClient(client -> header.collapsed())) throw new AssertionError("Right key did not expand the focused group");
        context.getInput().pressKey(InputConstants.KEY_LEFT);
        context.waitTick();
        if (!context.computeOnClient(client -> header.collapsed())) throw new AssertionError("Left key did not collapse the focused group");
        context.runOnClient(client -> button(client, "Expand All").onPress(null));
        context.runOnClient(client -> {
            button(client, "ESP: ON").onPress(null);
            if (XRayConfigManager.get().mobEspEnabled || !XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).enabled)
                throw new AssertionError("Master button must switch ESP off and keep group settings");
            button(client, "ESP: OFF").onPress(null);
            if (!XRayConfigManager.get().mobEspEnabled) throw new AssertionError("Master button did not switch ESP on");
        });
        if (context.computeOnClient(client -> header.collapsed())) throw new AssertionError("Expand All failed");

        // A mob's own toggle flips only that mob and stores an override only when it differs from the group.
        context.runOnClient(client -> search(client, "minecraft:cow"));
        context.waitTick();
        var cowRow = context.computeOnClient(client -> mobRow(client, COW));
        clickAt(context, cowRow.getContentRight() - 8, cowRow.getContentYMiddle());
        context.runOnClient(client -> {
            var own = XRayConfigManager.get().mobEsp.get(COW);
            if (XRayConfigManager.get().espEnabled(COW, MobEspGroup.ANIMALS) || own == null || !Boolean.FALSE.equals(own.enabled)
                || !XRayConfigManager.get().espEnabled("minecraft:pig", MobEspGroup.ANIMALS)) throw new AssertionError("Mob toggle click failed");
        });
        clickAt(context, cowRow.getContentRight() - 8, cowRow.getContentYMiddle());
        context.runOnClient(client -> {
            if (!XRayConfigManager.get().espEnabled(COW, MobEspGroup.ANIMALS) || XRayConfigManager.get().mobEsp.containsKey(COW))
                throw new AssertionError("Toggling back must return the mob to its group");
            XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).enabled = false;
        });

        // Search by group name lists the whole group; enabled-only keeps only effectively enabled mobs.
        context.runOnClient(client -> {
            search(client, "Nether Enemies");
            var rows = list(client).children();
            if (!(rows.getFirst() instanceof MobEspScreen.MobList.GroupRow g) || g.group() != MobEspGroup.NETHER_ENEMIES
                || rows.size() != 1 + list(client).mobsOf(MobEspGroup.NETHER_ENEMIES).size()) throw new AssertionError("Group-name search failed");
            search(client, "");
            var zombie = new XRayConfig.MobEspSettings();
            zombie.enabled = true;
            XRayConfigManager.get().mobEsp.put(ZOMBIE, zombie);
            button(client, "All Mobs").onPress(null);
            rows = list(client).children();
            if (rows.size() != 2 || !(rows.get(0) instanceof MobEspScreen.MobList.GroupRow g2) || g2.group() != MobEspGroup.OVERWORLD_ENEMIES
                || !rows.get(1).getNarration().getString().contains(", " + ZOMBIE + ","))
                throw new AssertionError("ESP enabled-only filter failed: " + rows.size());
        });
        TestCompat.screenshot(context, "esp-picker-enabled-only");
        runItemPicker(context);
        context.runOnClient(client -> { XRayConfigManager.reset(); Mc.setScreen(client, null); });
        context.waitTick();
    }

    // ---------------------------------------------------------------- settings screens

    private void runSettingsGui(ClientGameTestContext context) {
        context.setScreen(() -> new MobEspScreen(null));
        context.waitTicks(2);
        context.runOnClient(client -> {
            search(client, ZOMBIE);
            var row = mobRow(client, ZOMBIE);
            Mc.screen(client).setFocused(list(client));
            list(client).setFocused(row);
        });
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTick();
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspSettingsScreen screen) || screen.editsGroup())
                throw new AssertionError("Mob row keyboard navigation failed");
            // Group defaults of Overworld Enemies: off, outline, red.
            var config = XRayConfigManager.get();
            button(client, "ESP: Group (No)").onPress(null);
            if (!Boolean.TRUE.equals(config.mobEsp.get(ZOMBIE).enabled)) throw new AssertionError("Enable override failed");
            button(client, "Style: Group (Outline)").onPress(null);
            if (config.mobEsp.get(ZOMBIE).style != XRayConfig.EspStyle.BOX) throw new AssertionError("Style override failed");
            button(client, "Color: Group (Red)").onPress(null);
            if (config.mobEsp.get(ZOMBIE).color != 0xff55ff55) throw new AssertionError("Color override failed");
            button(client, "Use Group Color").onPress(null);
            if (config.mobEsp.get(ZOMBIE).color != null || config.espColor(ZOMBIE, MobEspGroup.OVERWORLD_ENEMIES) != 0xffff5555)
                throw new AssertionError("Use Group Color failed");
            button(client, "Color: Group (Red)").onPress(null);
            button(client, "Style: Box").onPress(null);      // Box -> explicit Outline (same as the group)
            button(client, "Style: Outline").onPress(null);  // explicit Outline -> follow the group again
            if (config.mobEsp.get(ZOMBIE).style != null) throw new AssertionError("Style cycle did not return to group");
            if (!button(client, "Use Group Color").active) throw new AssertionError("Use Group Color should be active");
        });
        TestCompat.screenshot(context, "esp-mob-settings");
        context.runOnClient(client -> button(client, "Group: Overworld Enemies").onPress(null));
        context.waitTick();
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspSettingsScreen screen) || !screen.editsGroup())
                throw new AssertionError("Group link failed");
            var config = XRayConfigManager.get();
            button(client, "ESP: No").onPress(null);
            button(client, "Style: Outline").onPress(null);
            button(client, "Color: Red").onPress(null);
            var group = config.espGroup(MobEspGroup.OVERWORLD_ENEMIES);
            if (!group.enabled || group.style != XRayConfig.EspStyle.BOX || group.color != 0xff55ff55) throw new AssertionError("Group controls failed");
            expect(config.resolveEsp("minecraft:creeper", MobEspGroup.OVERWORLD_ENEMIES), XRayConfig.EspStyle.BOX, 0xff55ff55, "creeper via group");
            // The zombie keeps its own color (green) and enabled override.
            if (!button(client, "Reset Mob Overrides (1)").active) throw new AssertionError("Override counter wrong");
        });
        TestCompat.screenshot(context, "esp-group-settings");
        context.runOnClient(client -> {
            button(client, "Reset Mob Overrides (1)").onPress(null);
            var config = XRayConfigManager.get();
            if (config.mobEsp.containsKey(ZOMBIE) || button(client, "Reset Mob Overrides (0)").active) throw new AssertionError("Reset overrides failed");
            XRayConfigManager.save();
            XRayConfigManager.reset();
            XRayConfigManager.load();
            var group = XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES);
            if (!group.enabled || group.style != XRayConfig.EspStyle.BOX || group.color != 0xff55ff55) throw new AssertionError("Group persistence failed");
            Mc.screen(client).onClose();
        });
        context.waitTick();
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspSettingsScreen screen) || screen.editsGroup()) throw new AssertionError("Group screen must return to the mob");
            button(client, "Done").onPress(null);
        });
        context.waitTick();
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspScreen)) throw new AssertionError("Mob screen must return to the picker");
            XRayConfigManager.reset();
            XRayConfigManager.save();
            Mc.setScreen(client, null);
        });
        context.waitTick();
    }

    // ---------------------------------------------------------------- world fixture

    private void runWorldFixture(ClientGameTestContext context) {
        try (var world = TestCompat.worldBuilder(context).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("gamerule spawn_mobs false");
            world.getServer().runCommand("fill -10 60 -12 10 72 12 minecraft:air");
            world.getServer().runCommand("fill -10 60 -12 10 60 12 minecraft:stone");
            world.getServer().runCommand("fill -8 61 1 8 68 4 minecraft:stone");
            world.getServer().runCommand("summon minecraft:zombie -4 61 7 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            world.getServer().runCommand("summon minecraft:cow 4 61 7 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            world.getServer().runCommand("summon minecraft:skeleton 0 61 8 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            // Objects (group off by default): armor stand, minecart, dropped item, item frame on a stone block.
            world.getServer().runCommand("summon minecraft:armor_stand -7 61 8 {Invulnerable:1b}");
            world.getServer().runCommand("summon minecraft:minecart 7 61 8 {Invulnerable:1b}");
            world.getServer().runCommand("summon minecraft:item 2 61 10 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767,Age:-32768,NoGravity:1b,Invulnerable:1b}");
            world.getServer().runCommand("summon minecraft:item 5 61 10 {Item:{id:\"minecraft:emerald\",count:1},PickupDelay:32767,Age:-32768,NoGravity:1b,Invulnerable:1b}");
            world.getServer().runCommand("setblock -3 62 11 minecraft:stone");
            world.getServer().runCommand("summon minecraft:item_frame -3 62 10 {Facing:2b,Fixed:1b,Invulnerable:1b,Item:{id:\"minecraft:diamond_sword\",count:1}}");
            world.getServer().runCommand("tp @a 0 61 -8 0 0");
            TestCompat.packets(context, world);
            context.waitTicks(20);
            TestCompat.lookAt(context, 0, 0);
            TestCompat.chunksRender(world);
            context.runOnClient(client -> { XRayConfigManager.reset(); XRayRenderController.apply(); });
            context.waitTicks(4);
            TestCompat.screenshot(context, "esp-01-disabled");

            // Hide Mobs: off by default; it drops mobs (not objects) only while the X-Ray view is on.
            // Behind Walls keeps a mob in plain view (a pig in front of the player) and drops those
            // behind the stone wall; All drops both.
            world.getServer().runCommand("summon minecraft:pig 3 61 -4 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            context.waitTicks(4);
            context.runOnClient(client -> {
                var config = XRayConfigManager.get();
                if (config.hideMobs != XRayConfig.HideMobs.OFF) throw new AssertionError("Hide Mobs must default to off");
                config.enabled = true;
                XRayRenderController.apply();
                if (XRayState.hideMob(mob(client, ZOMBIE))) throw new AssertionError("Mobs must render with Hide Mobs off");
                config.hideMobs = XRayConfig.HideMobs.WALLS;
                if (!XRayState.hideMob(mob(client, ZOMBIE)) || !XRayState.hideMob(mob(client, COW)))
                    throw new AssertionError("Hide Mobs: Behind Walls did not hide mobs behind the wall");
                if (XRayState.hideMob(mob(client, "minecraft:pig")))
                    throw new AssertionError("Hide Mobs: Behind Walls must keep mobs in plain view");
                config.hideMobs = XRayConfig.HideMobs.ALL;
                if (!XRayState.hideMob(mob(client, ZOMBIE)) || !XRayState.hideMob(mob(client, "minecraft:pig")))
                    throw new AssertionError("Hide Mobs: All did not hide every mob under X-Ray");
                if (XRayState.hideMob(mob(client, "minecraft:armor_stand")) || XRayState.hideMob(client.player))
                    throw new AssertionError("Hide Mobs must keep objects and players");
            });
            context.waitTicks(8);
            TestCompat.screenshot(context, "esp-01b-xray-hide-mobs");
            context.runOnClient(client -> {
                XRayConfigManager.get().enabled = false;
                XRayRenderController.apply();
                if (XRayState.hideMob(mob(client, ZOMBIE))) throw new AssertionError("Hide Mobs must not apply with X-Ray off");
                XRayConfigManager.get().hideMobs = XRayConfig.HideMobs.OFF;
            });
            world.getServer().runCommand("kill @e[type=minecraft:pig]");
            context.waitTicks(4);

            // Groups only: Overworld Enemies = cyan outline, Animals = red box.
            context.runOnClient(client -> {
                group(MobEspGroup.OVERWORLD_ENEMIES, true, XRayConfig.EspStyle.OUTLINE, 0xff00ffff);
                group(MobEspGroup.ANIMALS, true, XRayConfig.EspStyle.BOX, 0xffff5555);
                assertState(client, ZOMBIE, true, 0xff00ffff);
                assertState(client, SKELETON, true, 0xff00ffff);
                assertState(client, COW, false, 0xffff5555);
                if (XRayConfigManager.get().enabled || XRayState.enabled()) throw new AssertionError("ESP must work with X-Ray off");
            });
            context.waitTicks(8);
            Path first = TestCompat.screenshot(context, "esp-02-groups-outline-cyan-box-red");
            assertColor(first, 0xff00ffff);
            assertColor(first, 0xffff5555);

            // Individual overrides inside the groups: zombie green box, cow yellow outline; skeleton stays on the group.
            context.runOnClient(client -> {
                override(ZOMBIE, null, XRayConfig.EspStyle.BOX, 0xff55ff55);
                override(COW, null, XRayConfig.EspStyle.OUTLINE, 0xffffff55);
                assertState(client, ZOMBIE, false, 0xff55ff55);
                assertState(client, COW, true, 0xffffff55);
                assertState(client, SKELETON, true, 0xff00ffff);
            });
            context.waitTicks(8);
            Path second = TestCompat.screenshot(context, "esp-03-overrides-box-green-outline-yellow");
            assertColor(second, 0xff55ff55);
            assertColor(second, 0xffffff55);
            assertColor(second, 0xff00ffff);

            // Group off, single mob on; group on, single mob off.
            context.runOnClient(client -> {
                XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).enabled = false;
                override(COW, true, XRayConfig.EspStyle.BOX, 0xffff55ff);
                override(ZOMBIE, false, null, null);
                assertState(client, COW, false, 0xffff55ff);
                assertState(client, SKELETON, true, 0xff00ffff);
                assertNoEsp(client, ZOMBIE);
            });
            context.waitTicks(8);
            Path third = TestCompat.screenshot(context, "esp-04-group-off-mob-on-group-on-mob-off");
            assertColor(third, 0xffff55ff);
            assertColor(third, 0xff00ffff);

            // ESP range: cow (~15.5 blocks) and skeleton (~16 blocks) drop out at 8 blocks, return at 24.
            context.runOnClient(client -> {
                XRayConfigManager.get().mobEspRangeBlocks = 8;
                assertNoEsp(client, COW);
                assertNoEsp(client, SKELETON);
            });
            context.waitTicks(8);
            Path near = TestCompat.screenshot(context, "esp-04b-range-8-blocks");
            assertNoColor(near, 0xffff55ff);
            assertNoColor(near, 0xff00ffff);
            context.runOnClient(client -> {
                XRayConfigManager.get().mobEspRangeBlocks = 24;
                assertState(client, COW, false, 0xffff55ff);
                assertState(client, SKELETON, true, 0xff00ffff);
            });
            context.waitTicks(8);
            Path far = TestCompat.screenshot(context, "esp-04c-range-24-blocks");
            assertColor(far, 0xffff55ff);
            assertColor(far, 0xff00ffff);
            context.runOnClient(client -> XRayConfigManager.get().mobEspRangeBlocks = 0);

            // Players group: a second (client-side) player behind the wall; the local player never gets ESP.
            context.runOnClient(client -> {
                var other = new RemotePlayer(client.level, new GameProfile(UUID.fromString("5b7c3a1e-9d2f-4c8b-a6e1-0f3d2c1b4a59"), "EspTester"));
                other.setId(OTHER_PLAYER_ID);
                other.snapTo(2.0, 61.0, 6.0, 180f, 0f);
                other.setOldPosAndRot();
                client.level.addEntity(other);
                group(MobEspGroup.PLAYERS, true, XRayConfig.EspStyle.BOX, 0xffffaa00);
            });
            context.waitTicks(4);
            context.runOnClient(client -> {
                assertPlayerBox(client, 0xffffaa00);
                if (MobEspRenderer.settings(client.player) != null) throw new AssertionError("Local player must never get ESP");
                // Default hotkey J, not shared with any other control.
                KeyMapping esp = KeyMapping.get("key.betterxray.esp");
                if (esp == null || !esp.getDefaultKey().getName().equals("key.keyboard.j")) throw new AssertionError("ESP hotkey default is not J");
                for (KeyMapping other : client.options.keyMappings)
                    if (other != esp && other.same(esp)) throw new AssertionError("ESP hotkey J conflicts with " + other.getName());
            });
            context.waitTicks(8);
            Path players = TestCompat.screenshot(context, "esp-05-player-box-orange");
            assertColor(players, 0xffffaa00);
            assertColor(players, 0xffff55ff);

            // Player name and health: buttons only on the Players screens, label through the wall.
            context.runOnClient(client -> {
                var config = XRayConfigManager.get();
                if (!config.playerEspName || config.playerEspHealth != XRayConfig.HealthDisplay.BAR)
                    throw new AssertionError("Player label defaults must be name on, health bar");
                Mc.setScreen(client, new MobEspSettingsScreen(null, type("minecraft:zombie")));
                List<Button> zombieButtons = new ArrayList<>();
                collectButtons(Mc.screen(client).children(), zombieButtons);
                if (zombieButtons.stream().anyMatch(b -> b.getMessage().getString().startsWith("Name:")))
                    throw new AssertionError("Player label options shown for a non-player");
                Mc.setScreen(client, new MobEspSettingsScreen(null, MobEspGroup.PLAYERS));
                button(client, "Name: Yes").onPress(null);
                if (config.playerEspName) throw new AssertionError("Name toggle failed");
                button(client, "Name: No").onPress(null);
                button(client, "Health: Bar").onPress(null);
                if (config.playerEspHealth != XRayConfig.HealthDisplay.BOTH) throw new AssertionError("Health cycle failed");
                button(client, "Health: Numbers + Bar");
                Mc.setScreen(client, null);
                otherPlayer(client).setHealth(10.0f);
                var label = ((MobEspRenderState)extracted(client, otherPlayer(client))).betterxray$playerLabel();
                if (label == null || !"EspTester".equals(label.name()) || label.health() != 10.0f || label.maxHealth() != 20.0f
                    || !label.showBar() || !label.showNumbers())
                    throw new AssertionError("Player label not extracted: " + label);
                if (extracted(client, otherPlayer(client)).nameTag != null) throw new AssertionError("Vanilla name tag duplicates the ESP name");
                if (!MobEspRenderer.healthText(10, 20, 0).equals("10/20 \u2764") || !MobEspRenderer.healthText(17.5f, 20, 4).equals("17.5/20 \u2764 +4")
                    || MobEspRenderer.healthColor(10, 20) != 0xffffff55 || MobEspRenderer.healthColor(15, 20) != 0xff55ff55)
                    throw new AssertionError("Health text/color wrong");
            });
            context.waitTicks(8);
            Path labels = TestCompat.screenshot(context, "esp-05a-player-name-health");
            assertColor(labels, 0xffffff55); // half health: yellow bar and numbers
            context.runOnClient(client -> {
                var config = XRayConfigManager.get();
                config.playerEspName = false;
                config.playerEspHealth = XRayConfig.HealthDisplay.OFF;
                if (((MobEspRenderState)extracted(client, otherPlayer(client))).betterxray$playerLabel() != null)
                    throw new AssertionError("Player label still extracted with name and health off");
            });
            context.waitTicks(8);
            assertNoColor(TestCompat.screenshot(context, "esp-05b-player-label-off"), 0xffffff55);
            context.runOnClient(client -> {
                XRayConfigManager.get().playerEspName = true;
                XRayConfigManager.get().playerEspHealth = XRayConfig.HealthDisplay.BAR;
            });

            // Real key press on the configurable hotkey: master switch off, then on again.
            context.getInput().pressKey(options -> KeyMapping.get("key.betterxray.esp"));
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (XRayConfigManager.get().mobEspEnabled) throw new AssertionError("Hotkey J did not switch ESP off");
                assertNoEsp(client, COW);
                assertNoEsp(client, SKELETON);
                if (MobEspRenderer.settings(otherPlayer(client)) != null || ((MobEspRenderState)extracted(client, otherPlayer(client))).betterxray$espBox() != null)
                    throw new AssertionError("Player ESP still active with ESP switched off");
                if (!XRayConfigManager.get().espGroup(MobEspGroup.PLAYERS).enabled) throw new AssertionError("Hotkey must keep group settings");
            });
            context.waitTicks(8);
            Path off = TestCompat.screenshot(context, "esp-06-hotkey-off");
            assertNoColor(off, 0xffffaa00);
            assertNoColor(off, 0xffff55ff);
            assertNoColor(off, 0xff00ffff);
            context.getInput().pressKey(options -> KeyMapping.get("key.betterxray.esp"));
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (!XRayConfigManager.get().mobEspEnabled) throw new AssertionError("Hotkey J did not switch ESP on again");
                assertPlayerBox(client, 0xffffaa00);
                assertState(client, COW, false, 0xffff55ff);
                client.level.removeEntity(OTHER_PLAYER_ID, Entity.RemovalReason.DISCARDED);
            });

            // Objects group: boxes by default, then outlines; mob groups off so only objects are colored.
            context.runOnClient(client -> {
                XRayConfigManager.get().mobEspGroups.values().forEach(setting -> setting.enabled = false);
                XRayConfigManager.get().mobEsp.clear();
                XRayConfigManager.get().espGroup(MobEspGroup.OBJECTS).enabled = true;
                XRayConfigManager.get().espGroup(MobEspGroup.OBJECTS).color = 0xffff8800;
                for (String id : OBJECTS) assertState(client, id, false, 0xffff8800);
                assertNoEsp(client, COW);
                if (MobEspRenderer.settings(client.player) != null) throw new AssertionError("Local player got object ESP");
            });
            context.waitTicks(8);
            Path objectBoxes = TestCompat.screenshot(context, "esp-05c-objects-box-orange");
            assertColor(objectBoxes, 0xffff8800);
            context.runOnClient(client -> {
                group(MobEspGroup.OBJECTS, true, XRayConfig.EspStyle.OUTLINE, 0xff55ffff);
                for (String id : OBJECTS) assertState(client, id, true, 0xff55ffff);
            });
            context.waitTicks(8);
            Path objectOutlines = TestCompat.screenshot(context, "esp-05d-objects-outline-cyan");
            assertColor(objectOutlines, 0xff55ffff);
            context.runOnClient(client -> {
                // A single object can be switched off inside the enabled Objects group.
                override("minecraft:minecart", false, null, null);
                assertNoEsp(client, "minecraft:minecart");
                assertState(client, "minecraft:armor_stand", true, 0xff55ffff);
                XRayConfigManager.get().mobEsp.clear();
                XRayConfigManager.get().espGroup(MobEspGroup.OBJECTS).enabled = false;
                for (String id : OBJECTS) assertNoEsp(client, id);
            });
            // Single items: Objects on, the Item row off, Diamond on: only the diamond gets ESP, the emerald none.
            context.runOnClient(client -> {
                group(MobEspGroup.OBJECTS, true, XRayConfig.EspStyle.BOX, 0xffff8800);
                override(XRayConfig.ITEM_ENTITY, false, null, null);
                var diamond = new XRayConfig.MobEspSettings();
                diamond.enabled = true;
                diamond.color = 0xff00ffff;
                XRayConfigManager.get().itemEsp.put(DIAMOND, diamond);
                var diamondState = (MobEspRenderState)extracted(client, itemEntity(client, DIAMOND));
                if (diamondState.betterxray$espBox() == null || diamondState.betterxray$espColor() != 0xff00ffff)
                    throw new AssertionError("Selected item did not get its ESP box");
                var emerald = itemEntity(client, EMERALD);
                var emeraldState = extracted(client, emerald);
                if (emeraldState.appearsGlowing() || ((MobEspRenderState)emeraldState).betterxray$espBox() != null || MobEspRenderer.settings(emerald) != null)
                    throw new AssertionError("Unselected item must not get ESP");
            });
            context.waitTicks(8);
            Path singleItem = TestCompat.screenshot(context, "esp-05e-single-item-diamond-cyan");
            // Cyan only on the diamond; the other objects keep their orange boxes, the emerald has none (checked above).
            assertColor(singleItem, 0xff00ffff);
            context.runOnClient(client -> {
                XRayConfigManager.get().itemEsp.clear();
                XRayConfigManager.get().mobEsp.clear();
                XRayConfigManager.get().espGroup(MobEspGroup.OBJECTS).enabled = false;
            });

            // ESP tracers: group default, mob overrides, settings button, a target behind the camera, hotkey K.
            context.runOnClient(client -> {
                XRayConfigManager.get().mobEspGroups.values().forEach(setting -> setting.enabled = false);
                XRayConfigManager.get().mobEsp.clear();
                group(MobEspGroup.ANIMALS, true, XRayConfig.EspStyle.BOX, 0xffff55ff);
                if (XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).tracer || !XRayConfigManager.get().mobEspTracersEnabled)
                    throw new AssertionError("Tracer defaults must be group off, master on");
                KeyMapping tracers = KeyMapping.get("key.betterxray.esp_tracers");
                if (tracers == null || !tracers.getDefaultKey().getName().equals("key.keyboard.k")) throw new AssertionError("ESP tracer hotkey default is not K");
                for (KeyMapping other : client.options.keyMappings)
                    if (other != tracers && other.same(tracers)) throw new AssertionError("ESP tracer hotkey K conflicts with " + other.getName());
            });
            context.waitTicks(8);
            Path noTracer = TestCompat.screenshot(context, "esp-08a-box-without-tracer");
            int boxOnly = countColor(noTracer, 0xffff55ff, 0, 10);
            context.runOnClient(client -> {
                if (MobEspRenderer.lastDrawnTracers() != 0) throw new AssertionError("Tracer drawn while all tracers are off");
                XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).tracer = true;
                if (!MobEspRenderer.settings(mob(client, COW)).tracer()) throw new AssertionError("Group tracer not resolved");
            });
            context.waitTicks(8);
            Path withTracer = TestCompat.screenshot(context, "esp-08b-cow-tracer-magenta");
            int boxAndTracer = countColor(withTracer, 0xffff55ff, 0, 10);
            context.runOnClient(client -> {
                if (MobEspRenderer.lastDrawnTracers() != 1) throw new AssertionError("Expected 1 tracer, drew " + MobEspRenderer.lastDrawnTracers());
            });
            if (boxAndTracer < boxOnly + 40)
                throw new AssertionError("Tracer not visible on the GPU screenshot (" + boxOnly + " -> " + boxAndTracer + " px)");

            // Mob override through the settings screen: the cow follows the group, then turns its tracer off.
            context.runOnClient(client -> {
                Mc.setScreen(client, new MobEspSettingsScreen(null, type(COW)));
                button(client, "Tracer: Group (Yes)").onPress(null);
                var own = XRayConfigManager.get().mobEsp.get(COW);
                if (own == null || !Boolean.FALSE.equals(own.tracer) || MobEspRenderer.settings(mob(client, COW)).tracer())
                    throw new AssertionError("Mob tracer override failed");
                button(client, "Tracer: No").onPress(null);
                button(client, "Tracer: Yes").onPress(null);
                if (XRayConfigManager.get().mobEsp.containsKey(COW)) throw new AssertionError("Tracer cycle did not return to the group");
                Mc.setScreen(client, null);
                // Skeleton: group without tracer, mob with tracer.
                group(MobEspGroup.OVERWORLD_ENEMIES, true, XRayConfig.EspStyle.OUTLINE, 0xff00ffff);
                var skeleton = new XRayConfig.MobEspSettings();
                skeleton.tracer = true;
                XRayConfigManager.get().mobEsp.put(SKELETON, skeleton);
                if (MobEspRenderer.settings(mob(client, ZOMBIE)).tracer() || !MobEspRenderer.settings(mob(client, SKELETON)).tracer())
                    throw new AssertionError("Tracer override resolution wrong");
            });
            context.waitTicks(8);
            TestCompat.screenshot(context, "esp-08c-cow-and-skeleton-tracer");
            context.runOnClient(client -> {
                // Cow through its group, skeleton through its override; the zombie has none.
                if (MobEspRenderer.lastDrawnTracers() != 2) throw new AssertionError("Expected cow and skeleton tracers, drew " + MobEspRenderer.lastDrawnTracers());
                XRayConfigManager.get().mobEsp.clear();
                XRayConfigManager.get().espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled = false;
                // Only the pig behind the camera keeps ESP; its box is off-screen, its tracer is not.
                override(COW, false, null, null);
            });
            world.getServer().runCommand("summon minecraft:pig 0 61 -11 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b}");
            TestCompat.packets(context, world);
            context.waitTicks(10);
            Path behind = TestCompat.screenshot(context, "esp-08d-tracer-to-pig-behind-camera");
            context.runOnClient(client -> {
                if (MobEspRenderer.lastDrawnTracers() != 1) throw new AssertionError("Expected the pig tracer, drew " + MobEspRenderer.lastDrawnTracers());
            });
            if (countColor(behind, 0xffff55ff, 0, 10) < 12) throw new AssertionError("Tracer to a target behind the camera missing");

            // Real key press on K: all ESP tracers off (group setting kept), then on again.
            context.getInput().pressKey(options -> KeyMapping.get("key.betterxray.esp_tracers"));
            context.waitTicks(8);
            Path tracersOff = TestCompat.screenshot(context, "esp-08e-tracer-hotkey-off");
            context.runOnClient(client -> {
                if (XRayConfigManager.get().mobEspTracersEnabled) throw new AssertionError("Hotkey K did not switch tracers off");
                if (!XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).tracer) throw new AssertionError("Hotkey must keep group tracer settings");
                if (MobEspRenderer.lastDrawnTracers() != 0) throw new AssertionError("Tracers drawn after hotkey K");
            });
            if (countColor(tracersOff, 0xffff55ff, 0, 10) >= 12) throw new AssertionError("Tracer still on screen after hotkey K");
            context.getInput().pressKey(options -> KeyMapping.get("key.betterxray.esp_tracers"));
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (!XRayConfigManager.get().mobEspTracersEnabled || MobEspRenderer.lastDrawnTracers() != 1)
                    throw new AssertionError("Hotkey K did not switch tracers on again");
                XRayConfigManager.get().espGroup(MobEspGroup.ANIMALS).enabled = false;
            });
            world.getServer().runCommand("kill @e[type=minecraft:pig]");
            TestCompat.packets(context, world);
            context.waitTicks(4);

            world.getServer().runCommand("effect give @e[type=minecraft:zombie] minecraft:glowing infinite 0 true");
            TestCompat.packets(context, world);
            context.waitTicks(3);
            context.runOnClient(client -> {
                XRayConfigManager.get().mobEspGroups.values().forEach(setting -> setting.enabled = false);
                XRayConfigManager.get().mobEsp.clear();
                EntityRenderState zombie = extracted(client, mob(client, ZOMBIE));
                if (!zombie.appearsGlowing() || ((MobEspRenderState)zombie).betterxray$espBox() != null)
                    throw new AssertionError("Disabling ESP changed vanilla glowing");
                assertNoEsp(client, COW);
                assertNoEsp(client, SKELETON);
                XRayConfigManager.save();
            });
            context.waitTicks(8);
            TestCompat.screenshot(context, "esp-07-disabled-vanilla-glow");
            context.runOnClient(client -> { XRayConfigManager.reset(); XRayConfigManager.save(); XRayRenderController.apply(); });
        }
    }

    // ---------------------------------------------------------------- helpers

    private static EntityType<?> type(String id) { return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)); }

    private static void expect(XRayConfig.ResolvedEsp resolved, XRayConfig.EspStyle style, int color, String what) {
        if (resolved == null || resolved.style() != style || resolved.color() != (color | 0xff000000))
            throw new AssertionError("ESP resolution wrong (" + what + "): " + resolved);
    }

    private static Button button(Minecraft client, String prefix) {
        List<Button> buttons = new ArrayList<>();
        collectButtons(Mc.screen(client).children(), buttons);
        return buttons.stream().filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst()
            .orElseThrow(() -> new AssertionError("Button '" + prefix + "' missing; have "
                + buttons.stream().map(b -> b.getMessage().getString()).toList()));
    }

    /** Buttons of the screen, including those inside a scrolling container. */
    private static void collectButtons(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children, List<Button> out) {
        for (var child : children) {
            if (child instanceof Button b) out.add(b);
            else if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
                collectButtons(container.children(), out);
        }
    }

    /** The ESP range slider of the open picker; checks its label ({@code blocks} &lt; 0: special label key). */
    private static net.minecraft.client.gui.components.AbstractSliderButton espRangeSlider(Minecraft client, String key, int blocks) {
        var arg = blocks < 0 ? net.minecraft.network.chat.Component.translatable(key) : net.minecraft.network.chat.Component.translatable(key, blocks);
        String expected = net.minecraft.network.chat.Component.translatable("option.betterxray.esp_range", arg).getString();
        for (var child : Mc.screen(client).children())
            if (child instanceof net.minecraft.client.gui.components.AbstractSliderButton slider) {
                if (!slider.getMessage().getString().equals(expected))
                    throw new AssertionError("ESP range slider shows '" + slider.getMessage().getString() + "', expected '" + expected + "'");
                return slider;
            }
        throw new AssertionError("ESP range slider missing");
    }

    private static MobEspScreen.MobList list(Minecraft client) {
        return Mc.screen(client).children().stream().filter(MobEspScreen.MobList.class::isInstance)
            .map(MobEspScreen.MobList.class::cast).findFirst().orElseThrow();
    }

    private static void search(Minecraft client, String text) {
        Mc.screen(client).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(text);
    }

    /** Objects → Item → single items: search in the mob view, real toggle click, item view with its own search and filter. */
    private static void runItemPicker(ClientGameTestContext context) {
        context.runOnClient(client -> {
            XRayConfigManager.reset();
            Mc.setScreen(client, new MobEspScreen(null));
            list(client).groupRow(MobEspGroup.OBJECTS).setCollapsed(false);
            var items = list(client).itemEntityRow();
            if (items == null || items.items().size() < 1000) throw new AssertionError("Item row must list every item");
            if (list(client).children().stream().anyMatch(MobEspScreen.MobList.ItemRow.class::isInstance))
                throw new AssertionError("The mob view must not list items without a search");
            search(client, DIAMOND);
            list(client).setScrollAmount(0);
            var rows = list(client).children();
            if (!(rows.get(0) instanceof MobEspScreen.MobList.GroupRow g) || g.group() != MobEspGroup.OBJECTS
                || !(rows.get(1) instanceof MobEspScreen.MobList.MobRow m) || !m.id().equals(XRayConfig.ITEM_ENTITY)
                || rows.stream().noneMatch(r -> r instanceof MobEspScreen.MobList.ItemRow i && i.id().equals(DIAMOND)))
                throw new AssertionError("Item search must list Objects → Item → Diamond, got " + rows.size() + " rows");
        });
        context.waitTick(); // rows get their positions when rendered
        var diamondRow = context.computeOnClient(client -> itemRow(client, DIAMOND));
        clickAt(context, diamondRow.getContentRight() - 8, diamondRow.getContentYMiddle());
        context.runOnClient(client -> {
            var config = XRayConfigManager.get();
            var own = config.itemEsp.get(DIAMOND);
            if (own == null || !Boolean.TRUE.equals(own.enabled) || !config.itemEspEnabled(DIAMOND) || config.itemEspEnabled(EMERALD)
                || config.espEnabled(XRayConfig.ITEM_ENTITY, MobEspGroup.OBJECTS))
                throw new AssertionError("Item toggle click must switch on the diamond only: own=" + (own == null ? null : own.enabled)
                    + " diamond=" + config.itemEspEnabled(DIAMOND) + " emerald=" + config.itemEspEnabled(EMERALD)
                    + " entity=" + config.espEnabled(XRayConfig.ITEM_ENTITY, MobEspGroup.OBJECTS) + " row=" + diamondRow.getContentRight()
                    + "," + diamondRow.getContentYMiddle() + " screen=" + Mc.screen(client));
        });
        TestCompat.screenshot(context, "esp-picker-items-search");
        context.runOnClient(client -> {
            search(client, "");
            button(client, "All Mobs").onPress(null);
            var rows = list(client).children();
            if (rows.size() != 3 || !(rows.get(2) instanceof MobEspScreen.MobList.ItemRow i) || !i.id().equals(DIAMOND))
                throw new AssertionError("Enabled-only filter must show Objects → Item → Diamond, got " + rows.size() + " rows");
        });
        TestCompat.screenshot(context, "esp-picker-items-enabled-only");
        // A click on the Item row's arrow opens the item view: all items, own search, own enabled filter.
        context.runOnClient(client -> {
            Mc.setScreen(client, new MobEspScreen(null));
            search(client, "minecraft:item");
            list(client).setScrollAmount(0);
        });
        context.waitTick(); // rows get their positions when rendered
        var itemEntity = context.computeOnClient(client -> mobRow(client, XRayConfig.ITEM_ENTITY));
        clickAt(context, itemEntity.getContentX() + 4, itemEntity.getContentYMiddle());
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspScreen view) || !view.itemsOnly())
                throw new AssertionError("Arrow click must open the item view");
            var rows = list(client).children();
            if (rows.size() < 1000 || !rows.stream().allMatch(MobEspScreen.MobList.ItemRow.class::isInstance))
                throw new AssertionError("Item view must list every item and nothing else, got " + rows.size());
            search(client, "diamond");
            rows = list(client).children();
            if (rows.isEmpty() || !rows.stream().allMatch(r -> r instanceof MobEspScreen.MobList.ItemRow i && i.id().contains("diamond")))
                throw new AssertionError("Item view search failed");
            search(client, "");
            button(client, "All Items").onPress(null);
            rows = list(client).children();
            if (rows.size() != 1 || !(rows.get(0) instanceof MobEspScreen.MobList.ItemRow i) || !i.id().equals(DIAMOND))
                throw new AssertionError("Item view enabled filter must show only Diamond, got " + rows.size());
        });
        TestCompat.screenshot(context, "esp-items-view-enabled");
        context.runOnClient(client -> {
            button(client, "ESP Enabled Items").onPress(null);
            search(client, DIAMOND);
            list(client).setScrollAmount(0);
        });
        TestCompat.screenshot(context, "esp-items-view-search");
        context.waitTick(); // rows get their positions when rendered
        var diamondName = context.computeOnClient(client -> itemRow(client, DIAMOND));
        clickAt(context, diamondName.getContentX() + 60, diamondName.getContentYMiddle());
        context.runOnClient(client -> {
            if (!(Mc.screen(client) instanceof MobEspSettingsScreen screen) || !screen.editsItem())
                throw new AssertionError("Item row click must open its settings");
        });
        TestCompat.screenshot(context, "esp-item-settings");
        context.runOnClient(client -> {
            Mc.screen(client).onClose();
            if (!(Mc.screen(client) instanceof MobEspScreen view) || !view.itemsOnly())
                throw new AssertionError("Item settings must return to the item view");
        });
    }

    private static MobEspScreen.MobList.ItemRow itemRow(Minecraft client, String id) {
        return list(client).children().stream().filter(r -> r instanceof MobEspScreen.MobList.ItemRow i && i.id().equals(id))
            .map(MobEspScreen.MobList.ItemRow.class::cast).findFirst().orElseThrow(() -> new AssertionError("Item row missing: " + id));
    }

    private static Entity itemEntity(Minecraft client, String itemId) {
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && MobEspRenderer.itemId(item.getItem().getItem()).equals(itemId))
                return entity;
        throw new AssertionError("Fixture item not loaded: " + itemId);
    }

    private static MobEspScreen.MobList.MobRow mobRow(Minecraft client, String id) {
        return list(client).children().stream().filter(r -> r instanceof MobEspScreen.MobList.MobRow m && m.id().equals(id))
            .map(MobEspScreen.MobList.MobRow.class::cast).findFirst().orElseThrow(() -> new AssertionError("Row missing: " + id));
    }

    private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos((guiX + .5) * scale, (guiY + .5) * scale);
        //? if >=26.3 {
        context.getInput().pressMouse(1);
        //?} else {
        /*context.getInput().pressMouse(0);
        *///?}
        context.waitTick();
    }

    private static void group(MobEspGroup group, boolean enabled, XRayConfig.EspStyle style, int color) {
        var setting = XRayConfigManager.get().espGroup(group);
        setting.enabled = enabled; setting.style = style; setting.color = color;
    }

    private static void override(String id, Boolean enabled, XRayConfig.EspStyle style, Integer color) {
        var setting = new XRayConfig.MobEspSettings();
        setting.enabled = enabled; setting.style = style; setting.color = color;
        XRayConfigManager.get().mobEsp.put(id, setting);
    }

    /** Guards the actual GPU output, including the consumed-render-state regression. */
    private static void assertColor(Path screenshot, int color) {
        try {
            var image = ImageIO.read(screenshot.toFile());
            int matching = 0;
            // The wall occupies the upper 70%; ignore chat, hotbar and the player's hand.
            for (int y = 0; y < image.getHeight() * 7 / 10; y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (Math.abs((pixel >> 16 & 255) - (color >> 16 & 255)) <= 20
                    && Math.abs((pixel >> 8 & 255) - (color >> 8 & 255)) <= 20
                    && Math.abs((pixel & 255) - (color & 255)) <= 20) matching++;
            }
            if (matching < 12) throw new AssertionError("ESP color " + Integer.toHexString(color) + " missing from GPU screenshot " + screenshot);
        } catch (IOException e) { throw new AssertionError(e); }
    }

    private static Player otherPlayer(Minecraft client) {
        if (client.level.getEntity(OTHER_PLAYER_ID) instanceof Player player) return player;
        throw new AssertionError("Fixture player not loaded");
    }

    private static void assertPlayerBox(Minecraft client, int color) {
        var player = otherPlayer(client);
        var state = extracted(client, player);
        var data = (MobEspRenderState)state;
        if (data.betterxray$espBox() == null || data.betterxray$espColor() != color || state.appearsGlowing())
            throw new AssertionError("Wrong extracted player ESP state");
        TestCompat.expectRendered(client, player, "Selected player culled behind terrain");
    }

    private static int countColor(Path screenshot, int color) { return countColor(screenshot, color, 0, 7); }

    /** Pixels of {@code color} between {@code fromTenth} and {@code toTenth} tenths of the image height. */
    private static int countColor(Path screenshot, int color, int fromTenth, int toTenth) {
        try {
            var image = ImageIO.read(screenshot.toFile());
            int matching = 0;
            for (int y = image.getHeight() * fromTenth / 10; y < image.getHeight() * toTenth / 10; y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (Math.abs((pixel >> 16 & 255) - (color >> 16 & 255)) <= 20
                    && Math.abs((pixel >> 8 & 255) - (color >> 8 & 255)) <= 20
                    && Math.abs((pixel & 255) - (color & 255)) <= 20) matching++;
            }
            return matching;
        } catch (IOException e) { throw new AssertionError(e); }
    }

    private static void assertNoColor(Path screenshot, int color) {
        int matching = countColor(screenshot, color);
        if (matching >= 12) throw new AssertionError("ESP color " + Integer.toHexString(color) + " still in screenshot " + screenshot + " (" + matching + " px)");
    }

    /** First loaded entity of this type (mob or object). */
    private static Entity mob(Minecraft client, String id) {
        for (var entity : client.level.entitiesForRendering())
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(id)) return entity;
        throw new AssertionError("Fixture mob not loaded: " + id);
    }

    private static EntityRenderState extracted(Minecraft client, Entity entity) {
        return TestCompat.extracted(client, entity);
    }

    private static void assertNoEsp(Minecraft client, String id) {
        var mob = mob(client, id);
        EntityRenderState state = extracted(client, mob);
        if (state.appearsGlowing() || ((MobEspRenderState)state).betterxray$espBox() != null || MobEspRenderer.settings(mob) != null)
            throw new AssertionError("ESP still active for " + id);
    }

    private static void assertState(Minecraft client, String id, boolean outline, int color) {
        var mob = mob(client, id);
        var state = extracted(client, mob);
        var data = (MobEspRenderState)state;
        if (outline ? state.outlineColor != color || data.betterxray$espBox() != null
            : data.betterxray$espBox() == null || data.betterxray$espColor() != color || state.appearsGlowing())
            throw new AssertionError("Wrong extracted ESP state for " + id);
        if (mob.isCurrentlyGlowing()) throw new AssertionError("ESP changed server entity glow flag");
        TestCompat.expectRendered(client, mob, "Selected mob culled behind terrain: " + id);
    }
}
