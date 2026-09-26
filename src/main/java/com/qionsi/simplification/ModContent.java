package com.qionsi.simplification;

import com.qionsi.simplification.machine.MTEPetrochemicalComplex;
import com.qionsi.simplification.recipe.ModRecipeMaps;
import com.qionsi.simplification.recipe.ModRecipes;

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
     * Registers the machines, the recipe pool and the recipes.
     *
     * @return {@code false} when the machine could not be registered and the mod is unusable
     */
    public static boolean register() {
        if (registered) return true;
        registered = true;

        try {
            createMachine();
        } catch (Throwable t) {
            // A class initialiser failure here is almost always an id clash with another addon. Report it and carry on
            // so the rest of the pack still loads.
            MyMod.LOG.error(
                "Could not register the Petrochemical Complex controller at MetaTileEntity id {}. Another mod may "
                    + "already use that id; change MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER.",
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

        // Build the structure definition now so a problem with the shape shows up here, in a short log, rather than
        // the first time a player tries to build the machine. Failures are caught inside and fall back to a working
        // one block structure, so this cannot stop the game from starting.
        MTEPetrochemicalComplex.prepareStructure();

        try {
            ModRecipes.init();
            MyMod.LOG.info(
                "Registered {} Petrochemical Complex recipes",
                ModRecipeMaps.petrochemicalComplexRecipes.getBackend()
                    .getAllRecipes()
                    .size());
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Petrochemical Complex recipes; the machine will have none.", t);
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
