package com.qionsi.simplification;

import com.qionsi.simplification.block.AruStoneRecipes;
import com.qionsi.simplification.block.ModBlocks;
import com.qionsi.simplification.item.ModItems;

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

        // 阿如的倒影 needs the 1.20.1 blocks that 1.7.10 never had; they are read when the structure is checked, so
        // they have to exist before any machine is placed.
        ModBlocks.register();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        // The 阿如石 recipes need both the blocks and the vanilla catalysts, which all exist by now.
        AruStoneRecipes.init();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        // The recipe waits until here so that GregTech has already filed its wire cutters in the ore dictionary.
        ModItems.registerRecipes();
        ModItems.logRegistration();
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
