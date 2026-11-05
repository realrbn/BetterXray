package dev.betterxray.validation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
final class ValidationWorld {
    static void create(Minecraft client,String name) {
        client.createWorldOpenFlows().createFreshLevel(name,
            new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
            new WorldOptions(1234L,false,false), WorldPresets::createNormalWorldDimensions
            //? if >=1.20.2
            , new net.minecraft.client.gui.screens.TitleScreen()
            );
    }
}
