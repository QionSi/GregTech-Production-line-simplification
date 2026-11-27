package com.qionsi.simplification;

import com.qionsi.simplification.machine.MTEAruReflection;
import com.qionsi.simplification.machine.MTEHatchGridUpgrade;
import com.qionsi.simplification.machine.MTEModernIndustrialPark;
import com.qionsi.simplification.machine.MTEMolecularPolymerFabricator;
import com.qionsi.simplification.machine.MTENaquadahFuelLaboratory;
import com.qionsi.simplification.machine.MTEPetrochemicalComplex;
import com.qionsi.simplification.machine.MTERareEarthComplex;
import com.qionsi.simplification.machine.MTESimpleWaterPurifier;
import com.qionsi.simplification.machine.MTETranscendentCatalystMaker;
import com.qionsi.simplification.recipe.AruReflectionRecipe;
import com.qionsi.simplification.recipe.GridUpgradeHatchRecipes;
import com.qionsi.simplification.recipe.ModRecipeMaps;
import com.qionsi.simplification.recipe.ModRecipes;
import com.qionsi.simplification.recipe.ModernIndustrialParkRecipes;
import com.qionsi.simplification.recipe.MolecularPolymerRecipes;
import com.qionsi.simplification.recipe.NaquadahFuelRecipes;
import com.qionsi.simplification.recipe.RareEarthRecipes;
import com.qionsi.simplification.recipe.SimpleWaterPurifierRecipes;
import com.qionsi.simplification.recipe.TranscendentCatalystAssemblyLine;
import com.qionsi.simplification.recipe.TranscendentCatalystRecipes;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IVoidable;

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

        registerGridUpgradeHatch();
        GridUpgradeHatchRecipes.init();

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

        // The Molecular Polymer Fabricator is built entirely out of GregTech, New Horizons Core Mod and Thaumcraft
        // blocks, all of which exist by now, so its two blueprints can be checked here as well.
        registerMachine(
            MetaTileIDs.MOLECULAR_POLYMER_FABRICATOR_CONTROLLER,
            "the Molecular Polymer Fabricator",
            "MetaTileIDs.MOLECULAR_POLYMER_FABRICATOR_CONTROLLER",
            MTEMolecularPolymerFabricator::prepareStructure);

        // The Modern Industrial Park is the same story: every block it is built from is registered by the time the
        // controllers are created, so its blueprint is checked right here.
        registerMachine(
            MetaTileIDs.MODERN_INDUSTRIAL_PARK_CONTROLLER,
            "the Modern Industrial Park",
            "MetaTileIDs.MODERN_INDUSTRIAL_PARK_CONTROLLER",
            MTEModernIndustrialPark::prepareStructure);

        // The Simple Water Purifier is built out of GregTech, GT++ and Chisel blocks; the Chisel one is resolved lazily
        // at the first structure check, so the blueprint can be verified here as well.
        registerMachine(
            MetaTileIDs.SIMPLE_WATER_PURIFIER_CONTROLLER,
            "the Simple Water Purifier",
            "MetaTileIDs.SIMPLE_WATER_PURIFIER_CONTROLLER",
            MTESimpleWaterPurifier::prepareStructure);

        // 阿如的倒影: its shape is a 279 x 275 x 275 translation of a 1.20.1 schematic, so the blueprint is read and
        // reported at startup rather than verified symbol by symbol.
        registerMachine(
            MetaTileIDs.ARU_REFLECTION_CONTROLLER,
            "Aru's Reflection",
            "MetaTileIDs.ARU_REFLECTION_CONTROLLER",
            MTEAruReflection::prepareStructure);

        registerMachine(
            MetaTileIDs.NAQUADAH_FUEL_LAB_CONTROLLER,
            "Naquadah Fuel Research and Test Machine",
            "MetaTileIDs.NAQUADAH_FUEL_LAB_CONTROLLER",
            MTENaquadahFuelLaboratory::prepareStructure);

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

        // The Molecular Polymer Fabricator's super glue recipe needs a fluid GT++ builds during its own init, so that
        // class defers itself the same way the rare earth recipes do; a failure here leaves the machine empty-handed.
        try {
            MolecularPolymerRecipes.init();
        } catch (Throwable t) {
            MyMod.LOG
                .error("Could not register the Molecular Polymer Fabricator recipes; the machine will have none.", t);
        }

        // The Modern Industrial Park's naquadah oxide recipes need Bartworks dusts and its waste fluid, which is the
        // same kind of deferral.
        try {
            ModernIndustrialParkRecipes.init();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Modern Industrial Park recipes; the machine will have none.", t);
        }

        // The Simple Water Purifier's part recipes need the report and chips, which ModItems creates after this class
        // has run, so that class defers itself the same way.
        try {
            SimpleWaterPurifierRecipes.init();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Simple Water Purifier recipes; the machine will have none.", t);
        }

        // 阿如的倒影 needs the Research Station, which TecTech only fills in late, so this defers itself too.
        try {
            AruReflectionRecipe.init();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register Aru's Reflection's assembly line recipe.", t);
        }

        try {
            NaquadahFuelRecipes.init();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Naquadah fuel recipes; the machine will have none.", t);
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
    /**
     * Registers 电网升级仓.
     * <p>
     * It is a hatch, not a controller, so it does not go through {@link #registerMachine}: that helper exists to give
     * the multiblock controllers their error handling and structure preparation.
     */
    private static void registerGridUpgradeHatch() {
        try {
            new MTEHatchGridUpgrade(MetaTileIDs.HATCH_GRID_UPGRADE, "hatch.gridupgrade", "Grid Upgrade Hatch", 14);
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not register the Grid Upgrade Hatch at MetaTileEntity id {}. Another mod may already use that "
                    + "id; change MetaTileIDs.HATCH_GRID_UPGRADE.",
                MetaTileIDs.HATCH_GRID_UPGRADE,
                t);
        }
    }

    private static boolean registerMachine(int id, String name, String idConstant, Runnable prepareStructure) {
        try {
            if (id == MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER) {
                new MTEPetrochemicalComplex(id, "multimachine.petrochemicalcomplex", "Petrochemical Complex");
            } else if (id == MetaTileIDs.RARE_EARTH_COMPLEX_CONTROLLER) {
                new MTERareEarthComplex(id, "multimachine.rareearthcomplex", "Rare Earth Processing Complex");
            } else if (id == MetaTileIDs.TRANSCENDENT_CATALYST_MAKER_CONTROLLER) {
                new MTETranscendentCatalystMaker(
                    id,
                    "multimachine.transcendentcatalystmaker",
                    "Transcendent Catalyst Maker");
            } else if (id == MetaTileIDs.MOLECULAR_POLYMER_FABRICATOR_CONTROLLER) {
                new MTEMolecularPolymerFabricator(
                    id,
                    "multimachine.molecularpolymerfabricator",
                    "Molecular Polymer Fabricator");
            } else if (id == MetaTileIDs.MODERN_INDUSTRIAL_PARK_CONTROLLER) {
                new MTEModernIndustrialPark(id, "multimachine.modernindustrialpark", "Modern Industrial Park");
            } else if (id == MetaTileIDs.SIMPLE_WATER_PURIFIER_CONTROLLER) {
                new MTESimpleWaterPurifier(id, "multimachine.simplewaterpurifier", "Simple Water Purifier");
            } else if (id == MetaTileIDs.ARU_REFLECTION_CONTROLLER) {
                new MTEAruReflection(id, "multimachine.arureflection", "Reflection of Aru");
            } else {
                new MTENaquadahFuelLaboratory(
                    id,
                    "multimachine.naquadahfuellab",
                    "Naquadah Fuel Research and Test Machine");
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
        logVoidProtection(id, name);
        prepareStructure.run();
        return true;
    }

    /**
     * Reports what the controller that was just registered can do about an output that does not fit.
     * <p>
     * One line per machine, so a startup log shows at a glance that all three machines offer the item and fluid voiding
     * switches in their GUI, which modes those switches can reach, and what a freshly built machine starts at. The
     * button itself is drawn by GregTech for every multiblock; {@code supportsVoidProtection()} is what decides whether
     * it can be clicked, so that is the value worth printing.
     */
    private static void logVoidProtection(int id, String name) {
        if (!(GregTechAPI.METATILEENTITIES[id] instanceof IVoidable voidable)) return;
        MyMod.LOG.info(
            "{} void protection: supportsVoidProtection={} (the GUI voiding-mode button is drawn either way and is "
                + "only clickable when this is true), modes the button cycles through={}, default mode={} "
                + "(protectItems={}, protectFluids={})",
            name,
            voidable.supportsVoidProtection(),
            voidable.getAllowedVoidingModes(),
            voidable.getDefaultVoidingMode(),
            voidable.getDefaultVoidingMode().protectItem,
            voidable.getDefaultVoidingMode().protectFluid);
    }
}
