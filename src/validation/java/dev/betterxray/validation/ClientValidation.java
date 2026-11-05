package dev.betterxray.validation;
import dev.betterxray.XRayClient;
import dev.betterxray.config.*;
import dev.betterxray.gui.*;
import dev.betterxray.render.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

/**
 * Scripted client validation for Minecraft versions without Fabric's client gametest API
 * (ported from the NeoForge 1.21.1 fixture). Run with {@code gradlew :<version>:runValidation}.
 */
public final class ClientValidation implements ClientModInitializer {
    //? if >=1.20.5 {
    private static final String ITEM_COMMAND = "summon minecraft:item -10.5 202 14.5 {Item:{id:\"minecraft:diamond\",count:1},NoGravity:1b,Age:-32768,PickupDelay:32767}";
    //?} else
    /*private static final String ITEM_COMMAND = "summon minecraft:item -10.5 202 14.5 {Item:{id:\"minecraft:diamond\",Count:1b},NoGravity:1b,Age:-32768,PickupDelay:32767}";*/
    /** Running Minecraft version, e.g. 1.21.1, and its minor line (1.21). */
    static final String MC = net.minecraft.SharedConstants.getCurrentVersion().getName();
    static final String MC_MINOR = MC.substring(0, MC.lastIndexOf('.'));

    @Override public void onInitializeClient() {
        UpdateChecks.install();
        ClientTickEvents.END_CLIENT_TICK.register(client -> Events.tick());
    }

    /** Better X-Ray key mapping by short name, e.g. {@code toggle}. */
    static KeyMapping key(String name) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
            if (mapping.getName().equals("key.betterxray." + name)) return mapping;
        throw new AssertionError("Key mapping missing: " + name);
    }

    public static final class Events {
        private static boolean started, finished;
        private static int ticks, step, meshWait;
        private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
        static void tick() {
            if(finished) return;
            Minecraft mc=Minecraft.getInstance();
            try {
                if(mc.getOverlay()!=null) return;
                if(started && System.getProperty("betterxray.persistence")!=null) {
                    if(++ticks<40) return;
                    shot(mc,"13-picker-final");
                    String mode=System.getProperty("betterxray.persistence");
                    Files.writeString(Path.of(mode.equals("gui")?"gui-result.txt":"persistence-result.txt"),mode.equals("gui")?"PASS: selected-only filter, Controls registration, preset reset checks; final picker capture saved.\n":"PASS: settings, active preset, name and unavailable ID survived process restart; final picker capture saved.\n");
                    finished=true; mc.stop(); return;
                }
                if(!started && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if(!started && mc.screen instanceof TitleScreen) {
                    started=true;
                    if (System.getProperty("betterxray.persistence")!=null) {
                        var saved=XRayConfigManager.get();
                        if(!"gui".equals(System.getProperty("betterxray.persistence"))) check(saved.activePreset==1 && saved.getActivePreset().name.equals("Persistence Proof")
                            && saved.getActiveVisibleBlocks().contains("missing:unavailable_block") && saved.fullbright
                            && Math.abs(saved.terrainOpacity-.27f)<.001f,"Separate-process configuration persistence");
                        XRayConfigManager.reset(); XRayRenderController.apply();
                        mc.setScreen(new BlockSelectionScreen(new TitleScreen()));
                        mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("diamond");
                        mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.getMessage().getString().equals("All Blocks")).findFirst().orElseThrow().onPress();
                        var list=mc.screen.children().stream().filter(BlockListWidget.class::isInstance).map(BlockListWidget.class::cast).findFirst().orElseThrow();
                        check(list.children().size()==2,"Selected-only search excludes diamond block");
                        check(java.util.Arrays.stream(mc.options.keyMappings).filter(mapping -> mapping.getName().startsWith("key.betterxray.")).count()==10,"All ten Controls registrations");
                        var reset=new XRayConfig();
                        for(int index=0;index<3;index++) { reset.presets.get(index).name="Custom"; reset.presets.get(index).visibleBlocks.add("minecraft:stone"); reset.resetPreset(index); check(reset.presets.get(index).name.equals("Preset "+(index+1)) && (index==0?!reset.presets.get(index).visibleBlocks.contains("minecraft:stone"):reset.presets.get(index).visibleBlocks.isEmpty()),"Preset reset "+index); }
                        return;
                    }
                    XRayConfigManager.reset(); XRayRenderController.apply();
                    var c=XRayConfigManager.get();
                    check(c.presets.size()==3 && c.presets.get(1).visibleBlocks.isEmpty() && c.presets.get(2).visibleBlocks.isEmpty(),"Three fresh presets");
                    for(String id:c.getActiveVisibleBlocks()) check(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.tryParse(id)),"Available default "+id);
                    check(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("toggle")).getValue()==88 && net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("fullbright")).getValue()==76 && net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("config")).getValue()==71,"X/L/G defaults");
                    check(key("cycle_preset").isUnbound() && key("quick_add_block").isUnbound() && key("tracers").isUnbound(),"Unbound defaults");
                    check(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("highlight")).getValue()==72 && net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("esp")).getValue()==74,"H/J defaults");
                    XRayClient.LOGGER.info("[XRayValidation] {}; {}; {}; {}", GuiChecks.migrations(), GuiChecks.run(mc), EspChecks.picker(mc), UpdateChecks.evaluation());
                    check(UpdateChecks.requests()==0 && dev.betterxray.update.UpdateChecker.result()==null,"No update request on the title screen");
                    XRayConfigManager.reset(); XRayRenderController.apply(); c=XRayConfigManager.get();
                    var legacy=new XRayConfig(); legacy.migrateLegacyVisibleBlocks(java.util.Set.of("minecraft:stone")); legacy.sanitize();
                    check(legacy.getActiveVisibleBlocks().equals(java.util.Set.of("minecraft:stone")),"Legacy migration");
                    c.activePreset=55; c.terrainOpacity=Float.NaN; c.sanitize();
                    check(c.activePreset==0 && c.terrainOpacity==.15f,"Invalid values repaired");
                    ValidationWorld.create(mc,"betterxray-validation-"+System.currentTimeMillis());
                    return;
                }
                if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null) return;
                if(++ticks<100) return;
                // Software rendering (llvmpipe) compiles sections slowly: wait for the mesh before each capture.
                if(step>0 && !fixtureCompiled(mc) && ++meshWait<600) return;
                ticks=0; meshWait=0;
                var c=XRayConfigManager.get();
                switch(step++) {
                    case 0 -> {
                        mc.options.renderDistance().set(8); mc.options.cloudStatus().set(CloudStatus.OFF); mc.options.hideGui=true; mc.options.pauseOnLostFocus=false;
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> {
                            var source=server.createCommandSourceStack().withPermission(4);
                            for(String command:new String[]{"gamemode creative @a","gamerule doDaylightCycle false","time set noon","weather clear"})
                                server.getCommands().performPrefixedCommand(source,command);
                            var level=server.overworld();
                            for(BlockPos pos:BlockPos.betweenClosed(-12,200,-16,12,200,14)) level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
                            for(int cube=0;cube<3;cube++) {
                                int center=-6+cube*6;
                                var material=cube==0?Blocks.STONE:cube==1?Blocks.DEEPSLATE:Blocks.DIRT;
                                for(BlockPos pos:BlockPos.betweenClosed(center-2,201,3,center+2,205,7)) level.setBlock(pos,material.defaultBlockState(),3);
                                level.setBlock(new BlockPos(center,203,5),(cube==0?Blocks.DIAMOND_ORE:cube==1?Blocks.DEEPSLATE_DIAMOND_ORE:Blocks.ANCIENT_DEBRIS).defaultBlockState(),3);
                            }
                            level.setBlock(new BlockPos(-3,201,1),Blocks.WATER.defaultBlockState(),3);
                            level.setBlock(new BlockPos(3,201,1),Blocks.LAVA.defaultBlockState(),3);
                            level.setBlock(new BlockPos(0,201,1),Blocks.CHEST.defaultBlockState(),3);
                            for(var player:server.getPlayerList().getPlayers()) player.connection.teleport(0,201,-10,0,0);
                        }).join();
                    }
                    case 1 -> { UpdateChecks.firstWorld(mc,0); check(mc.player.getY()>190,"Fixture teleport completed"); check(activeRenderer().equals(FabricLoader.getInstance().isModLoaded("sodium")?"sodium":"vanilla"),"Expected terrain renderer active"); shot(mc,"01-off"); KeyMapping.click(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("toggle"))); }
                    case 2 -> { check(XRayState.enabled(),"Toggle ON (config "+c.enabled+", supported "+XRayState.supported()+")"); shot(mc,"02-on"); c.showWater=false; XRayRenderController.apply(); }
                    case 3 -> { shot(mc,"03-no-water"); c.showWater=true; c.showLava=false; XRayRenderController.apply(); }
                    case 4 -> { shot(mc,"04-no-lava"); c.showWater=false; c.terrainOpacity=.35f; XRayRenderController.apply(); }
                    case 5 -> { shot(mc,"05-opacity35"); c.terrainOpacity=.03f; c.getActiveVisibleBlocks().remove("minecraft:chest"); XRayRenderController.apply(); }
                    case 6 -> { shot(mc,"06-opacity3-faint-chest"); c.terrainOpacity=0f; XRayRenderController.apply(); }
                    case 7 -> { check(XRayState.hideTerrain(),"0% opacity hides unselected terrain"); shot(mc,"06b-opacity0-hidden"); c.terrainOpacity=.03f; XRayRenderController.apply(); press(mc,key("config")); }
                    case 8 -> {
                        check(mc.screen instanceof XRayConfigScreen,"G opens config"); shot(mc,"07-config");
                        mc.setScreen(new BlockSelectionScreen(mc.screen));
                        var search=mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                        search.setValue("minecraft:diamond_ore");
                    }
                    case 9 -> {
                        shot(mc,"08-search");
                        var list=mc.screen.children().stream().filter(BlockListWidget.class::isInstance).map(BlockListWidget.class::cast).findFirst().orElseThrow();
                        // Matching block tags (#minecraft:diamond_ores) are listed after the one block.
                        check(list.children().stream().filter(r->!r.getNarration().getString().contains(", #")).count()==1,"Registry ID search");
                        list.children().get(0).mouseClicked(0,0,0);
                        check(!c.getActiveVisibleBlocks().contains("minecraft:diamond_ore"),"Row deselection");
                        mc.screen.onClose(); mc.screen.onClose();
                        mc.setScreen(new PresetRenameScreen(null,1));
                        mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("Ores");
                        mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.getMessage().getString().equals("Done")).findFirst().orElseThrow().onPress();
                        check(c.presets.get(1).name.equals("Ores"),"GUI rename"); c.setActivePreset(1); XRayRenderController.apply();
                        mc.hitResult=new BlockHitResult(Vec3.atCenterOf(new BlockPos(-6,203,5)),Direction.NORTH,new BlockPos(-6,203,5),false);
                        XRayClient.addTargetedBlock(mc); XRayClient.addTargetedBlock(mc);
                        check(c.getActiveVisibleBlocks().equals(java.util.Set.of("minecraft:diamond_ore")),"Quick Add add-only and no duplicate");
                        XRayConfigManager.load(); check(XRayConfigManager.get().getActivePreset().name.equals("Ores"),"Persistent preset rename");
                        key("cycle_preset").setKey(InputConstants.Type.KEYSYM.getOrCreate(75)); KeyMapping.resetMapping(); KeyMapping.click(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("cycle_preset")));
                    }
                    case 10 -> {
                        check(c.activePreset==2,"Rebound cycle key"); key("cycle_preset").setKey(InputConstants.UNKNOWN); KeyMapping.resetMapping(); c.resetPreset(1); check(c.presets.get(1).name.equals("Preset 2") && c.presets.get(1).visibleBlocks.isEmpty(),"Reset preset 2");
                        c.resetPreset(0); c.setActivePreset(0); c.showWater=true; c.showLava=true; c.terrainOpacity=.15f; c.enabled=false; XRayRenderController.apply();
                        mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().overworld().setDayTime(18000)).join();
                    }
                    case 11 -> { shot(mc,"09-night-off"); press(mc,key("fullbright")); }
                    case 12 -> { check(c.fullbright && !XRayState.enabled() && mc.screen==null,"Independent L without Advancements"); shot(mc,"10-fullbright-only"); press(mc,key("fullbright")); }
                    case 13 -> {
                        check(!c.fullbright && !XRayState.enabled() && mc.screen==null,"Restored OFF"); shot(mc,"11-night-restored");
                        mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().overworld().setDayTime(6000)).join();
                    }
                    case 14 -> {
                        shot(mc,"12-restored");
                        c.highlightBlocks.clear(); c.highlightBlocks.add("minecraft:diamond_ore"); c.highlightBlocks.add("minecraft:ancient_debris");
                        c.highlightEnabled=true; c.tracersEnabled=true;
                        // The world below also contains natural ores; 24 blocks keeps only the fixture ores in range.
                        c.highlightRangeBlocks=24; XRayConfigManager.save();
                    }
                    case 15 -> {
                        check(HighlightCache.isIndexed(new BlockPos(-6,203,5)) && HighlightCache.isIndexed(new BlockPos(6,203,5)) && !HighlightCache.isIndexed(new BlockPos(0,203,5)),"Highlight index finds diamond ore and ancient debris only");
                        check(HighlightCache.lastDrawnBoxes()==2 && HighlightCache.lastDrawnTracers()==2,"Two boxes and two tracers drawn, got "+HighlightCache.lastDrawnBoxes()+"/"+HighlightCache.lastDrawnTracers());
                        int magenta=countPixels(mc,0xff00ff);
                        check(magenta>150,"Highlight color visible on screen ("+magenta+" px)");
                        shot(mc,"18-highlight-tracers");
                        c.highlightRangeBlocks=8; XRayConfigManager.save();
                    }
                    case 16 -> {
                        check(HighlightCache.lastDrawnBoxes()==0 && HighlightCache.lastDrawnTracers()==0,"8-block range hides the ores ~17 blocks away");
                        c.highlightRangeBlocks=24; XRayConfigManager.save();
                        serverSet(mc,new BlockPos(-6,203,5),Blocks.STONE);
                    }
                    case 17 -> {
                        check(!HighlightCache.isIndexed(new BlockPos(-6,203,5)) && HighlightCache.lastDrawnBoxes()==1,"Block change updates the index; 24-block range shows the remaining ore");
                        serverSet(mc,new BlockPos(-6,203,5),Blocks.DIAMOND_ORE);
                        c.tracersEnabled=false; XRayConfigManager.save();
                    }
                    case 18 -> {
                        check(HighlightCache.isIndexed(new BlockPos(-6,203,5)) && HighlightCache.lastDrawnBoxes()==2 && HighlightCache.lastDrawnTracers()==0,"Restored block re-indexed; tracers off");
                        int boxPixels=countPixels(mc,0xff00ff);
                        check(boxPixels>300,"Boxes visible through the enclosing rock without tracers ("+boxPixels+" px)");
                        shot(mc,"19-highlight-no-tracers");
                        c.highlightEnabled=false; c.highlightRangeBlocks=0; c.enabled=false; XRayRenderController.apply();
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> {
                            var level=server.overworld();
                            for(BlockPos pos:BlockPos.betweenClosed(-3,201,-17,3,206,-11)) level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);
                            for(var player:server.getPlayerList().getPlayers()) player.connection.teleport(0,202,-14,0,0);
                        }).join();
                    }
                    case 19 -> {
                        check(mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),"Camera under water");
                        underwaterOff=brightness(mc); shot(mc,"20-underwater-xray-off");
                        c.enabled=true; XRayRenderController.apply();
                    }
                    case 20 -> {
                        int on=brightness(mc);
                        check(XRayState.enabled() && on>underwaterOff+15,"X-Ray removes water fog/overlay (brightness "+underwaterOff+" -> "+on+")");
                        shot(mc,"21-underwater-xray-on");
                        c.enabled=false; XRayRenderController.apply();
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> {
                            var level=server.overworld();
                            for(BlockPos pos:BlockPos.betweenClosed(-3,201,-17,3,206,-11)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                            for(var player:server.getPlayerList().getPlayers()) player.connection.teleport(0,201,-10,0,0);
                        }).join();
                    }
                    case 21 -> {
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> {
                            var source=server.createCommandSourceStack().withPermission(4);
                            for(String command:new String[]{"difficulty easy",
                                "summon minecraft:husk 0 201 10.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                                "summon minecraft:cow 6.5 201 10.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                                "summon minecraft:armor_stand -6.5 201 10.5",
                                ITEM_COMMAND})
                                server.getCommands().performPrefixedCommand(source,command);
                        }).join();
                        EspChecks.configure(c);
                        EspChecks.spawnTester(mc);
                    }
                    case 22 -> {
                        var husk=EspChecks.find(mc,net.minecraft.world.entity.EntityType.HUSK);
                        check(mc.shouldEntityAppearGlowing(husk) && !husk.isCurrentlyGlowing(),"Husk outlined without glowing flag");
                        check(outlinesActive(mc),"Outline pass active");
                        check(MobEspRenderer.lastDrawnBoxes()==4,"Boxes for cow, armor stand, item and player, got "+MobEspRenderer.lastDrawnBoxes());
                        check(MobEspRenderer.lastDrawnLabels()==1,"One player label");
                        int red=countPixels(mc,0xff5555), green=countPixels(mc,0x55ff55), orange=countPixels(mc,0xff8800), blue=countPixels(mc,0x5555ff), yellow=countPixels(mc,0xffff55);
                        XRayClient.LOGGER.info("[XRayValidation] ESP pixels red {} green {} orange {} blue {} yellow {}",red,green,orange,blue,yellow);
                        check(red>40,"Husk outline visible through the cube ("+red+" px)");
                        check(green>40 && orange>40 && blue>40,"Boxes visible through walls (green "+green+", orange "+orange+", blue "+blue+")");
                        check(yellow>10,"Half-health label numbers/bar visible ("+yellow+" px)");
                        check(MobEspRenderer.replacesNameTag(EspChecks.tester()) && !MobEspRenderer.replacesNameTag(mc.player),"Vanilla name tag replaced for the tester only");
                        shot(mc,"22-esp-through-walls");
                        c.mobEspRangeBlocks=8; XRayConfigManager.save();
                    }
                    case 23 -> {
                        check(MobEspRenderer.lastDrawnBoxes()==0 && MobEspRenderer.lastDrawnLabels()==0
                            && !mc.shouldEntityAppearGlowing(EspChecks.find(mc,net.minecraft.world.entity.EntityType.HUSK)),"8-block ESP range hides targets ~20 blocks away");
                        shot(mc,"23-esp-range8");
                        c.mobEspRangeBlocks=0; XRayConfigManager.save();
                        KeyMapping.click(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("esp")));
                    }
                    case 24 -> {
                        check(!c.mobEspEnabled && MobEspRenderer.lastDrawnBoxes()==0
                            && !mc.shouldEntityAppearGlowing(EspChecks.find(mc,net.minecraft.world.entity.EntityType.HUSK)),"J switches all ESP off");
                        check(c.espGroup(dev.betterxray.config.MobEspGroup.ANIMALS).enabled,"Group settings kept while off");
                        int green=countPixels(mc,0x55ff55);
                        check(green<10,"No boxes while off ("+green+" px)");
                        shot(mc,"24-esp-master-off");
                        KeyMapping.click(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key("esp")));
                    }
                    case 25 -> {
                        check(c.mobEspEnabled && MobEspRenderer.lastDrawnBoxes()==4,"J switches ESP back on");
                        EspChecks.removeTester(mc);
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),"kill @e[type=!minecraft:player]")).join();
                        mc.setScreen(new MobEspScreen(null));
                    }
                    case 26 -> { shot(mc,"25-esp-picker"); mc.setScreen(new MobEspSettingsScreen(null,dev.betterxray.config.MobEspGroup.PLAYERS)); }
                    case 27 -> { shot(mc,"26-esp-players-settings"); mc.setScreen(null); c.mobEspGroups=dev.betterxray.config.XRayConfig.createDefaultEspGroups(); XRayConfigManager.save(); }
                    case 28 -> {
                        check(UpdateChecks.REAL || UpdateChecks.requests()==1,"No second update request in the same session");
                        mc.options.hideGui=false; mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
                    }
                    case 29 -> {
                        shot(mc,"27-update-hint"); mc.setScreen(null); mc.options.hideGui=true;
                        chatBefore=UpdateChecks.chat(mc).size();
                        if(UpdateChecks.REAL) step=34;
                        else UpdateChecks.serve(UpdateChecks.json("fabric",MC,"0.1","https://example.com"),200);
                    }
                    case 30 -> { UpdateChecks.expectSilent(mc,chatBefore,dev.betterxray.update.UpdateChecker.Status.INSTALLED_NEWER);
                        UpdateChecks.serve(UpdateChecks.json("neoforge",MC,"9.9.9","https://example.com"),200); }
                    case 31 -> { UpdateChecks.expectSilent(mc,chatBefore,dev.betterxray.update.UpdateChecker.Status.LOADER_NOT_LISTED);
                        UpdateChecks.serve("{broken",200); }
                    case 32 -> { UpdateChecks.expectSilent(mc,chatBefore,dev.betterxray.update.UpdateChecker.Status.INVALID_DATA);
                        UpdateChecks.serve("",404); }
                    case 33 -> { UpdateChecks.expectSilent(mc,chatBefore,dev.betterxray.update.UpdateChecker.Status.UNREACHABLE);
                        UpdateChecks.stopServer(); dev.betterxray.update.UpdateChecker.resetForTests(); }
                    case 34 -> {
                        if(!UpdateChecks.REAL) UpdateChecks.expectSilent(mc,chatBefore,dev.betterxray.update.UpdateChecker.Status.UNREACHABLE);
                        c.updateCheckEnabled=false; XRayConfigManager.save(); dev.betterxray.update.UpdateChecker.resetForTests();
                    }
                    case 35 -> {
                        check(dev.betterxray.update.UpdateChecker.result()==null,"Update Check OFF: no check started");
                        c.updateCheckEnabled=true; XRayConfigManager.save();
                    }
                    case 36 -> window(mc,1280,720,1);
                    case 37 -> { scale(mc,3); mc.setScreen(new XRayConfigScreen(null)); }
                    case 38 -> { check(mc.screen.width==427 && mc.screen.height==240,"1280x720 at scale 3, got "+mc.screen.width+"x"+mc.screen.height); shot(mc,"14-config-1280x720-scale3"); window(mc,400,120,1); }
                    case 39 -> { check(mc.screen.children().stream().anyMatch(ScrollArea.class::isInstance),"Tiny window config scrolls"); shot(mc,"15-config-400x120-scroll"); mc.setScreen(new HighlightScreen(null)); }
                    case 40 -> { shot(mc,"16-highlight-400x120"); window(mc,854,480,1); }
                    case 41 -> { scale(mc,2); mc.setScreen(new BlockSelectionScreen(null)); }
                    case 42 -> { check(mc.screen.width==427,"854x480 at scale 2"); shot(mc,"17-blocks-854x480-scale2"); scale(mc,0); mc.setScreen(null); }
                    case 43 -> {
                        // Deep mob behind solid rock, X-Ray off: its section is never meshed (occlusion
                        // culling), the ESP outline must still appear (regression in 1.20.1/1.21.1).
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> {
                            var source=server.createCommandSourceStack().withPermission(4);
                            for(int y=144;y<192;y+=12)
                                server.getCommands().performPrefixedCommand(source,"fill -16 "+y+" 0 31 "+(y+11)+" 47 minecraft:stone");
                            for(String command:new String[]{"kill @e[type=minecraft:item]","fill 8 168 24 8 169 24 minecraft:air","setblock 8 200 10 minecraft:stone",
                                "summon minecraft:husk 8.5 168 24.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}"})
                                server.getCommands().performPrefixedCommand(source,command);
                            for(var player:server.getPlayerList().getPlayers()) player.connection.teleport(8.5,201,10.5,0,68);
                        }).join();
                        EspChecks.configure(c);
                        c.enabled=false;
                    }
                    case 44 -> {
                        check(!XRayState.enabled(),"X-Ray off for the deep ESP check");
                        EspChecks.find(mc,net.minecraft.world.entity.EntityType.HUSK);
                        XRayRenderController.apply(); mc.levelRenderer.allChanged(); // full reload: only reachable sections get meshed again
                    }
                    case 45 -> {
                        var pocket=new BlockPos(8,168,24);
                        //? if >=1.20.2 {
                        boolean meshed=mc.levelRenderer.isSectionCompiled(pocket);
                        //?} else
                        /*boolean meshed=mc.levelRenderer.isChunkCompiled(pocket);*/
                        check(!meshed,"Deep fixture section is occluded and unmeshed (test precondition)");
                        int red=countPixels(mc,0xff5555);
                        XRayClient.LOGGER.info("[XRayValidation] deep ESP pixels red {}",red);
                        shot(mc,"28-esp-deep-xray-off");
                        check(red>5,"Deep husk outlined with X-Ray off ("+red+" px)");
                        var server=mc.getSingleplayerServer();
                        server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),"kill @e[type=minecraft:husk]")).join();
                        c.mobEspGroups=dev.betterxray.config.XRayConfig.createDefaultEspGroups(); XRayConfigManager.save();
                    }
                    // 1.3.0 features (FeatureChecks), one step per pass.
                    case 46 -> { if(!FeatureChecks.step(mc)) step--; }
                    case 47 -> {
                        c=XRayConfigManager.get();
                        c.activePreset=1; c.presets.get(1).name="Persistence Proof";
                        c.getActiveVisibleBlocks().add("missing:unavailable_block"); c.getActiveVisibleBlocks().add("bad id!");
                        c.terrainOpacity=.27f; c.fullbright=true;
                        XRayRenderController.apply();
                        Files.writeString(Path.of("validation-result.txt"),"PASS ["+activeRenderer()+" renderer]: world entered, key queues/defaults/rebinding, presets, config reload/migration, Quick Add, GUI/search, rendering captures. Visual inspection required.\n");
                        finished=true; XRayClient.LOGGER.info("[XRayValidation] PASS; screenshots ready");
                        mc.stop();
                    }
                }
            } catch(Throwable error) {
                finished=true; XRayClient.LOGGER.error("[XRayValidation] FAILED at step "+step,error);
                try { Files.writeString(Path.of("validation-result.txt"),"FAIL: "+error); }catch(Exception ignored){}
                mc.stop();
            }
        }
        private static boolean outlinesActive(Minecraft mc) {
            return ((dev.betterxray.validation.mixin.LevelRendererAccess)mc.levelRenderer).betterxrayValidation$outlines();
        }
        /** "sodium" when Sodium's world renderer drives the terrain, otherwise "vanilla". */
        private static String activeRenderer() {
            try {
                //? if >=1.21 {
                Object sodium=Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer").getMethod("instanceNullable").invoke(null);
                //?} else
                /*Object sodium=Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer").getMethod("instanceNullable").invoke(null);*/
                return sodium!=null?"sodium":"vanilla";
            } catch(ReflectiveOperationException | LinkageError absent) { return "vanilla"; }
        }
        private static int underwaterOff, chatBefore;
        private static void serverSet(Minecraft mc,BlockPos pos,net.minecraft.world.level.block.Block block) {
            var server=mc.getSingleplayerServer();
            server.submit(() -> server.overworld().setBlock(pos,block.defaultBlockState(),3)).join();
        }
        /** Pixels of the last frame within a small tolerance of the given RGB color. */
        private static int countPixels(Minecraft mc,int rgb) {
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int count=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int abgr=image.getPixelRGBA(x,y);
                    int r=abgr&255, g=abgr>>8&255, b=abgr>>16&255;
                    if(Math.abs(r-(rgb>>16&255))<40 && Math.abs(g-(rgb>>8&255))<40 && Math.abs(b-(rgb&255))<40) count++;
                }
                return count;
            }
        }
        /** Mean luminance (0-255) of the last frame. */
        private static int brightness(Minecraft mc) {
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                long sum=0;
                for(int y=0;y<image.getHeight();y+=4) for(int x=0;x<image.getWidth();x+=4) {
                    int abgr=image.getPixelRGBA(x,y);
                    sum+=((abgr&255)*299+(abgr>>8&255)*587+(abgr>>16&255)*114)/1000;
                }
                return (int)(sum/((image.getHeight()/4)*(image.getWidth()/4)));
            }
        }
        private static boolean fixtureCompiled(Minecraft mc) {
            //? if >=1.20.2 {
            if(!mc.levelRenderer.hasRenderedAllSections()) return false;
            for(int x=-16;x<=16;x+=8) for(int z=-16;z<=16;z+=8)
                if(!mc.levelRenderer.isSectionCompiled(new BlockPos(x,203,z)) || !mc.levelRenderer.isSectionCompiled(new BlockPos(x,191,z))) return false;
            //?} else {
            /*if(!mc.levelRenderer.hasRenderedAllChunks()) return false;
            for(int x=-16;x<=16;x+=8) for(int z=-16;z<=16;z+=8)
                if(!mc.levelRenderer.isChunkCompiled(new BlockPos(x,203,z)) || !mc.levelRenderer.isChunkCompiled(new BlockPos(x,191,z))) return false;
            *///?}
            return true;
        }
        /** The GUI-scale option is clamped to what the current window allows, so resize first. */
        private static void window(Minecraft mc,int width,int height,int scale) {
            mc.options.guiScale().set(scale); mc.getWindow().setWindowed(width,height); mc.resizeDisplay();
        }
        private static void scale(Minecraft mc,int scale) { mc.options.guiScale().set(scale); mc.resizeDisplay(); }
        private static void shot(Minecraft mc,String name) { Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message -> {}); }
        private static void press(Minecraft mc,KeyMapping mapping) {
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(mapping).getValue(),0,1,0);
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(mapping).getValue(),0,0,0);
        }
    }
}
