package com.qionsi.simplification;

import com.qionsi.simplification.item.ModItems;
import com.qionsi.simplification.material.ModMaterials;

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
        // the machine id reserved in MetaTileIDs is registered safely here.
        ModContent.register();

        // The bauble and its event handler are independent of GregTech, but they belong to the same pre-init.
        ModItems.register();

        // The five catalyst fluids have to be handed to Bartworks before it runs its werkstoff adders, which happens
        // during its own init phase.
        ModMaterials.register();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        // The recipe waits until here so that GregTech has already filed its wire cutters in the ore dictionary.
        ModItems.registerRecipes();
        ModItems.logRegistration();

        // By now Bartworks has created the fluids and cells of the catalysts, so what was registered can be checked.
        ModMaterials.logRegistration();
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
