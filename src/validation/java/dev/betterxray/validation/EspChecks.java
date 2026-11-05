package dev.betterxray.validation;

import com.mojang.authlib.GameProfile;
import dev.betterxray.XRayClient;
import dev.betterxray.config.MobEspGroup;
import dev.betterxray.config.XRayConfig;
import dev.betterxray.config.XRayConfigManager;
import dev.betterxray.gui.MobEspScreen;
import dev.betterxray.gui.MobEspSettingsScreen;
import dev.betterxray.render.MobEspRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.util.UUID;

import static dev.betterxray.validation.GuiChecks.check;

/** Mob ESP checks: classification and picker without a world, rendering in the fixture world. */
final class EspChecks {
    private EspChecks() {}

    static final int ENEMY_COLOR = 0xffff5555, ANIMAL_COLOR = 0xff55ff55, OBJECT_COLOR = 0xffff8800, PLAYER_COLOR = 0xff5555ff;
    private static RemotePlayer tester;

    /** Classification, picker contents and the row/settings logic; no world needed. */
    static String picker(Minecraft mc) {
        int types = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            if (!MobEspRenderer.isEspType(type)) continue;
            types++;
            check(MobEspRenderer.group(type) != null, "Every type has a group: " + MobEspRenderer.id(type));
        }
        check(!MobEspRenderer.isEspType(EntityType.MARKER), "Markers are not offered");
        check(MobEspRenderer.group(EntityType.PLAYER) == MobEspGroup.PLAYERS, "Player group");
        check(MobEspRenderer.group(EntityType.ZOMBIE) == MobEspGroup.OVERWORLD_ENEMIES
            && MobEspRenderer.group(EntityType.HUSK) == MobEspGroup.OVERWORLD_ENEMIES, "Zombie/husk are Overworld Enemies");
        check(MobEspRenderer.group(EntityType.COW) == MobEspGroup.ANIMALS, "Cow is an animal");
        check(MobEspRenderer.group(EntityType.VILLAGER) == MobEspGroup.VILLAGERS
            && MobEspRenderer.group(EntityType.IRON_GOLEM) == MobEspGroup.VILLAGERS, "Villagers & golems");
        check(MobEspRenderer.group(EntityType.WITHER) == MobEspGroup.NETHER_ENEMIES
            && MobEspRenderer.group(EntityType.ENDERMAN) == MobEspGroup.END
            && MobEspRenderer.group(EntityType.PILLAGER) == MobEspGroup.ILLAGERS
            && MobEspRenderer.group(EntityType.AXOLOTL) == MobEspGroup.AQUATIC, "Nether/End/Illager/Aquatic groups");
        for (var type : new EntityType<?>[]{EntityType.ARMOR_STAND, EntityType.ITEM, EntityType.MINECART, EntityType.ITEM_FRAME,
                EntityType.ARROW, EntityType.TNT, EntityType.FALLING_BLOCK, EntityType.EXPERIENCE_ORB})
            check(MobEspRenderer.group(type) == MobEspGroup.OBJECTS, "Object: " + MobEspRenderer.id(type));
        // Every vanilla mob of 1.21.1 has an explicit group (no fallback to "Other").
        int mobs = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = MobEspRenderer.id(type);
            if (!id.startsWith("minecraft:") || !MobEspRenderer.isMobType(type)) continue;
            mobs++;
            check(MobEspGroup.explicit(id) != null, "Vanilla mob without explicit group: " + id);
        }
        check(XRayConfig.createDefaultEspGroups().get("objects").style == XRayConfig.EspStyle.BOX
            && (XRayConfig.createDefaultEspGroups().get("objects").color | 0xff000000) == 0xffff8800, "Objects default to orange boxes");

        // Picker: every type listed once, group toggle and minimal per-mob override.
        var c = XRayConfigManager.get();
        var screen = (MobEspScreen)GuiChecks.openScreen(mc, new MobEspScreen(null), 640, 360);
        var list = screen.list();
        int listed = 0;
        for (MobEspGroup group : MobEspGroup.values()) listed += list.mobsOf(group).size();
        check(listed == types, "Picker lists every ESP type (" + listed + "/" + types + ")");
        var zombie = list.mobsOf(MobEspGroup.OVERWORLD_ENEMIES).stream().filter(r -> r.id().equals("minecraft:zombie")).findFirst().orElseThrow();
        boolean groupBefore = c.espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled;
        GuiChecks.buttonOf(screen, "ESP").getMessage(); // footer master switch exists
        list.groupRow(MobEspGroup.OVERWORLD_ENEMIES).mouseClicked(10_000, 0, 0); // click on the right = group toggle
        check(c.espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled != groupBefore && zombie.enabled() != groupBefore, "Group toggle");
        zombie.mouseClicked(10_000, 0, 0); // mob toggle against the group
        check(zombie.enabled() == groupBefore && c.mobEsp.get("minecraft:zombie").enabled == groupBefore, "Mob override created");
        zombie.mouseClicked(10_000, 0, 0); // back to the group's value
        check(!c.mobEsp.containsKey("minecraft:zombie") && zombie.enabled() != groupBefore,
            "Override removed when it equals the group");
        list.groupRow(MobEspGroup.OVERWORLD_ENEMIES).mouseClicked(10_000, 0, 0); // group back
        check(c.espGroup(MobEspGroup.OVERWORLD_ENEMIES).enabled == groupBefore, "Group back");
        var groupRow = list.groupRow(MobEspGroup.ANIMALS);
        groupRow.setCollapsed(true);
        check(list.children().stream().noneMatch(r -> r instanceof MobEspScreen.MobList.MobRow m && m.group() == MobEspGroup.ANIMALS), "Collapse group");
        groupRow.setCollapsed(false);

        // Settings screens: Players get name/health buttons, mobs get the group link.
        var players = GuiChecks.openScreen(mc, new MobEspSettingsScreen(null, MobEspGroup.PLAYERS), 640, 360);
        boolean name = c.playerEspName;
        GuiChecks.buttonOf(players, "Name").onPress();
        check(c.playerEspName != name, "Player name toggle");
        GuiChecks.buttonOf(players, "Name").onPress();
        var health = c.playerEspHealth;
        GuiChecks.buttonOf(players, "Health").onPress();
        check(c.playerEspHealth == health.next(), "Player health cycle");
        c.playerEspHealth = health;
        var zombieScreen = GuiChecks.openScreen(mc, new MobEspSettingsScreen(null, EntityType.ZOMBIE), 640, 360);
        GuiChecks.buttonOf(zombieScreen, "Group:");
        GuiChecks.buttonOf(zombieScreen, "Style").onPress();
        check(c.espStyle("minecraft:zombie", MobEspGroup.OVERWORLD_ENEMIES) != c.espGroup(MobEspGroup.OVERWORLD_ENEMIES).style, "Mob style override");
        GuiChecks.buttonOf(zombieScreen, "Style").onPress();
        GuiChecks.buttonOf(zombieScreen, "Style").onPress();
        check(!c.mobEsp.containsKey("minecraft:zombie"), "Style cycle returns to the group");
        check(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(ClientValidation.key("esp")).getValue() == 74, "J toggles ESP");
        XRayConfigManager.reset();
        return types + " ESP types, " + mobs + " vanilla mobs classified";
    }

    /** Enables the fixture's ESP setup: enemies outlined, animals/objects/players boxed in distinct colors. */
    static void configure(XRayConfig c) {
        c.mobEspGroups = XRayConfig.createDefaultEspGroups();
        c.mobEsp.clear();
        var enemies = c.espGroup(MobEspGroup.OVERWORLD_ENEMIES);
        enemies.enabled = true; enemies.style = XRayConfig.EspStyle.OUTLINE; enemies.color = ENEMY_COLOR;
        var animals = c.espGroup(MobEspGroup.ANIMALS);
        animals.enabled = true; animals.style = XRayConfig.EspStyle.BOX; animals.color = ANIMAL_COLOR;
        var objects = c.espGroup(MobEspGroup.OBJECTS);
        objects.enabled = true; objects.color = OBJECT_COLOR;
        var players = c.espGroup(MobEspGroup.PLAYERS);
        players.enabled = true; players.style = XRayConfig.EspStyle.BOX; players.color = PLAYER_COLOR;
        c.playerEspName = true;
        c.playerEspHealth = XRayConfig.HealthDisplay.BOTH;
        c.mobEspEnabled = true;
        c.mobEspRangeBlocks = 0;
        XRayConfigManager.save();
    }

    /** A client-side remote player ("EspTester") at half health behind the right cube. */
    static RemotePlayer spawnTester(Minecraft mc) {
        tester = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes("EspTester".getBytes()), "EspTester"));
        tester.setPos(3.5, 201, 11.5);
        tester.setOldPosAndRot();
        tester.setHealth(10f);
        //? if >=1.20.2 {
        mc.level.addEntity(tester);
        //?} else
        /*mc.level.addPlayer(tester.getId(), tester);*/
        return tester;
    }

    static RemotePlayer tester() { return tester; }

    static void removeTester(Minecraft mc) {
        if (tester != null) mc.level.removeEntity(tester.getId(), Entity.RemovalReason.DISCARDED);
        tester = null;
    }

    static Entity find(Minecraft mc, EntityType<?> type) {
        for (Entity entity : mc.level.entitiesForRendering()) if (entity.getType() == type) return entity;
        throw new AssertionError("Fixture entity missing: " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }
}
