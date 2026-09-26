package com.qionsi.simplification;

import java.io.File;

import com.qionsi.simplification.machine.MTEPetrochemicalComplex;
import com.qionsi.simplification.recipe.RecipeManager;

import gregtech.api.GregTechAPI;

/**
 * Creates and registers everything this mod adds to GregTech.
 * <p>
 * GregTech discovers a machine through the constructor of its MetaTileEntity: the constructor writes the instance into
 * {@code GregTechAPI.METATILEENTITIES} at the id it is given, which is also what assigns the item metadata and makes
 * the machine obtainable. That means this class has to run during GregTech's preload phase - which is true of every
 * mod's pre-initialization - and it has to run only once, because constructing the same id twice is an error.
 */
public final class ModContent {

    private static boolean registered;

    private ModContent() {}

    /**
     * Registers the machines, the recipe pool and loads the recipe file.
     *
     * @param configDirectory the mod's configuration directory
     * @return {@code false} when the mod could not be set up and the game should not continue with it
     */
    public static boolean register(File configDirectory) {
        if (registered) return true;
        registered = true;

        try {
            createMachine();
        } catch (Throwable t) {
            // A class initialiser failure here is almost always an id clash or a broken structure file. Report it and
            // carry on so the rest of the pack still loads.
            MyMod.LOG.error(
                "Could not register the Petrochemical Complex controller at MetaTileEntity id {}. Another mod may "
                    + "already use that id (change MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER), or the structure "
                    + "file is malformed.",
                MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER,
                t);
            return false;
        }
        if (GregTechAPI.METATILEENTITIES[MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER] == null) {
            MyMod.LOG.error(
                "The Petrochemical Complex controller was not registered at MetaTileEntity id {}.",
                MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER);
            return false;
        }
        MyMod.LOG.info("Registered the Petrochemical Complex controller");

        try {
            RecipeManager.init(configDirectory);
        } catch (Throwable t) {
            MyMod.LOG.error("Could not load the Petrochemical Complex recipes; the machine will have none.", t);
        }
        return true;
    }

    private static void createMachine() {
        new MTEPetrochemicalComplex(
            MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER,
            "multimachine.petrochemicalcomplex",
            "Petrochemical Complex");
    }
}
