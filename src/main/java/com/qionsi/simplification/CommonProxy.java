package com.qionsi.simplification;

import java.io.File;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        MyMod.LOG.info(Config.greeting);
        MyMod.LOG.info("I am MyMod at version " + Tags.VERSION);

        // GregTech loads in this same phase; its preload flag is already set by the time any mod's pre-init runs, so
        // machine ids reserved below are registered safely here.
        File configDirectory = event.getModConfigurationDirectory();
        ModContent.register(configDirectory);
        File directory = new File(configDirectory, MyMod.MODID);
        RecipeAutoReloader.init(
            new File(directory, com.qionsi.simplification.recipe.RecipeConfig.FILE_NAME),
            new File(directory, com.qionsi.simplification.machine.PetrochemicalComplexStructure.FILE_NAME));
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.qionsi.simplification.command.SimplificationCommand());
    }
}
