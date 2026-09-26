package com.qionsi.simplification;

import java.io.File;
import java.util.List;

import com.qionsi.simplification.recipe.RecipeManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Picks up config file edits without an explicit command.
 * <p>
 * Minecraft 1.7.10 cannot reload changed Java code while it is running, so this is deliberately about data only: the
 * recipe file and the structure file are checked whenever a player joins a world, and re-applied if either changed
 * since the last read. When iterating, the loop becomes "edit the file, leave and re-enter the world" - or use
 * {@code /simplification reload}, which does not even need that.
 */
public final class RecipeAutoReloader {

    private static File recipeFile;
    private static File structureFile;
    private static long recipeModified;
    private static long structureModified;

    private RecipeAutoReloader() {}

    public static void init(File recipe, File structure) {
        recipeFile = recipe;
        structureFile = structure;
        recipeModified = lastModifiedOf(recipe);
        structureModified = lastModifiedOf(structure);
        FMLCommonHandler.instance()
            .bus()
            .register(new RecipeAutoReloader());
    }

    private static long lastModifiedOf(File file) {
        return file != null && file.isFile() ? file.lastModified() : 0L;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        reloadIfChanged(recipeFile, true);
        reloadIfChanged(structureFile, false);
    }

    private void reloadIfChanged(File file, boolean recipes) {
        if (file == null || !file.isFile()) return;
        long modified = file.lastModified();
        long previous = recipes ? recipeModified : structureModified;
        if (modified == previous) return;
        if (recipes) {
            recipeModified = modified;
        } else {
            structureModified = modified;
        }

        List<String> problems = recipes ? RecipeManager.reload() : RecipeManager.reloadStructure();
        if (problems.isEmpty()) {
            MyMod.LOG.info("{} changed; reloaded automatically.", file.getName());
        } else {
            for (String problem : problems) {
                MyMod.LOG.warn("[{}] {}", file.getName(), problem);
            }
        }
    }
}
