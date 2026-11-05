package dev.betterxray.compat;

//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
*///?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
*///?}
import java.nio.file.Path;

/**
 * The few mod-loader queries the shared code needs. The NeoForge side uses the loading mod list,
 * which is available from mixin configuration on, before the regular {@code ModList} exists.
 */
public final class Platform {
    //? if fabric {
    public static final String LOADER = "fabric";
    //?} else if forge {
    /*public static final String LOADER = "forge";
    *///?} else {
    /*public static final String LOADER = "neoforge";
    *///?}

    public static boolean isModLoaded(String id) {
        //? if fabric {
        return FabricLoader.getInstance().isModLoaded(id);
        //?} else {
        /*LoadingModList mods = loadingMods();
        return mods != null && mods.getModFileById(id) != null;
        *///?}
    }

    /** Sodium's terrain renderer is installed: Sodium, or its fork Embeddium on Forge 1.20.1. */
    public static boolean sodiumLoaded() {
        //? if forge {
        /*return isModLoaded("embeddium");
        *///?} else
        return isModLoaded("sodium");
    }

    /** Installed version of a mod, or "0" when it is unknown. */
    public static String modVersion(String id) {
        //? if fabric {
        return FabricLoader.getInstance().getModContainer(id)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("0");
        //?} else {
        /*LoadingModList mods = loadingMods();
        if (mods == null) return "0";
        return mods.getMods().stream().filter(mod -> mod.getModId().equals(id)).findFirst()
            .map(mod -> mod.getVersion().toString()).orElse("0");
        *///?}
    }

    /** Physical client? Fabric never loads this client-only mod elsewhere. */
    public static boolean isClient() {
        //? if fabric {
        return true;
        //?} else {
        /*//? if >=1.21.11 {
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        return loader == null || loader.getDist() == Dist.CLIENT;
        //?} else {
        /^return FMLLoader.getDist() == Dist.CLIENT;
        ^///?}
        *///?}
    }

    public static Path configDir() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir();
        //?} else {
        /*return FMLPaths.CONFIGDIR.get();
        *///?}
    }

    //? if !fabric {
    /*private static LoadingModList loadingMods() {
        //? if >=1.21.11 {
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        return loader == null ? null : loader.getLoadingModList();
        //?} else {
        /^return LoadingModList.get();
        ^///?}
    }
    *///?}

    private Platform() {}
}
