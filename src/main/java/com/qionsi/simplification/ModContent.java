package com.qionsi.simplification;

import com.qionsi.simplification.machine.MTEPetrochemicalComplex;
import com.qionsi.simplification.machine.MTERareEarthComplex;
import com.qionsi.simplification.machine.MTETranscendentCatalystMaker;
import com.qionsi.simplification.recipe.ModRecipeMaps;
import com.qionsi.simplification.recipe.ModRecipes;
import com.qionsi.simplification.recipe.RareEarthRecipes;
import com.qionsi.simplification.recipe.TranscendentCatalystAssemblyLine;
import com.qionsi.simplification.recipe.TranscendentCatalystRecipes;

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
     * Registers the machines, the recipe pools and the recipes.
     *
     * @return {@code false} when the machine could not be registered and the mod is unusable
     */
    public static boolean register() {
        if (registered) return true;
        registered = true;

        if (!registerMachine(
            MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER,
            "the Petrochemical Complex",
            "MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER",
            MTEPetrochemicalComplex::prepareStructure)) return false;

        // The Rare Earth structure is deliberately not built here. Its glass and coil blocks come from other mods that
        // register them during their own init, so building the definition now - before those exist - would bind a
        // chainAllGlasses that knows no glasses at all. It is built on the first structure check instead.
        registerMachine(
            MetaTileIDs.RARE_EARTH_COMPLEX_CONTROLLER,
            "the Rare Earth Processing Complex",
            "MetaTileIDs.RARE_EARTH_COMPLEX_CONTROLLER",
            () -> {});

        // Everything the Transcendent Catalyst Maker is built from comes from GregTech itself, so its blueprint can be
        // checked right here.
        registerMachine(
            MetaTileIDs.TRANSCENDENT_CATALYST_MAKER_CONTROLLER,
            "the Transcendent Catalyst Maker",
            "MetaTileIDs.TRANSCENDENT_CATALYST_MAKER_CONTROLLER",
            MTETranscendentCatalystMaker::prepareStructure);

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

        // RareEarthRecipes registers itself, and reports its own counts: Bartworks only creates its items during its
        // own init phase, so that class defers the work until they exist and logs the result then.
        try {
            RareEarthRecipes.init();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Rare Earth recipes; the machine will have none.", t);
        }

        try {
            TranscendentCatalystRecipes.init();
        } catch (Throwable t) {
            MyMod.LOG
                .error("Could not register the Transcendent Catalyst Maker recipes; the machine will have none.", t);
        }

        // The assembly line recipe of the 初步研究的超维度催化剂制造机 needs the same Bartworks catalyst fluids as the
        // catalyst maker, so it defers its work in exactly the same way; a failure here leaves the item and its
        // assembler recipe untouched.
        try {
            TranscendentCatalystAssemblyLine.init();
        } catch (Throwable t) {
            MyMod.LOG
                .error("Could not register the Preliminary Study assembly line recipe; the recipe will be missing.", t);
        }
        return true;
    }

    /**
     * Creates one controller and reports what happened.
     * <p>
     * The structure definition is then built straight away, so a problem with a shape shows up here, in a short log,
     * rather than the first time a player tries to build the machine. Failures are caught inside and fall back to a
     * working one block structure, so this cannot stop the game from starting.
     */
    private static boolean registerMachine(int id, String name, String idConstant, Runnable prepareStructure) {
        try {
            if (id == MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER) {
                new MTEPetrochemicalComplex(id, "multimachine.petrochemicalcomplex", "Petrochemical Complex");
            } else if (id == MetaTileIDs.RARE_EARTH_COMPLEX_CONTROLLER) {
                new MTERareEarthComplex(id, "multimachine.rareearthcomplex", "Rare Earth Processing Complex");
            } else {
                new MTETranscendentCatalystMaker(
                    id,
                    "multimachine.transcendentcatalystmaker",
                    "Transcendent Catalyst Maker");
            }
        } catch (Throwable t) {
            // A class initialiser failure here is almost always an id clash with another addon. Report it and carry on
            // so the rest of the pack still loads.
            MyMod.LOG.error(
                "Could not register the controller of {} at MetaTileEntity id {}. Another mod may already use that "
                    + "id; change {}.",
                name,
                id,
                idConstant,
                t);
            return false;
        }
        if (GregTechAPI.METATILEENTITIES[id] == null) {
            MyMod.LOG.error("The controller of {} was not registered at MetaTileEntity id {}.", name, id);
            return false;
        }
        MyMod.LOG.info("Registered the controller of {}", name);
        prepareStructure.run();
        return true;
    }
}
