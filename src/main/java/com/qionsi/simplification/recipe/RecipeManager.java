package com.qionsi.simplification.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.qionsi.simplification.MetaTileIDs;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.machine.MTEPetrochemicalComplex;
import com.qionsi.simplification.machine.PetrochemicalComplexStructure;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;

/**
 * Owns the Petrochemical Complex recipe pool and keeps it in sync with the editable recipe file.
 * <p>
 * The file lives under {@code config/simplification/}. {@link #reload()} re-reads it and replaces the recipes in the
 * running game, which is what {@code /simplification reload} calls. A reload is all-or-nothing: the new file is
 * parsed and every recipe built before anything is removed, so a typo leaves the previous recipes in place and only
 * reports an error.
 */
public final class RecipeManager {

    private static File recipeFile;
    private static File structureFile;
    private static int lastReloadedCount;

    private RecipeManager() {}

    /**
     * First-time setup: registers the controller's assembler recipe and applies the recipe file.
     *
     * @param configDirectory the mod's config directory
     */
    public static void init(File configDirectory) {
        File directory = new File(configDirectory, MyMod.MODID);
        recipeFile = new File(directory, RecipeConfig.FILE_NAME);
        structureFile = new File(directory, PetrochemicalComplexStructure.FILE_NAME);
        registerAssemblerRecipe();

        List<String> structureProblems = reloadStructure();
        if (!structureProblems.isEmpty()) {
            MyMod.LOG.error("Petrochemical Complex structure could not be read from {}", structureFile);
            for (String problem : structureProblems) {
                MyMod.LOG.error("  {}", problem);
            }
        }

        List<String> problems = reload();
        if (problems.isEmpty()) {
            MyMod.LOG.info("Loaded {} Petrochemical Complex recipes from {}", lastReloadedCount, recipeFile);
        } else {
            MyMod.LOG.error(
                "Petrochemical Complex recipes could not be loaded from {}; the machine has no recipes.",
                recipeFile);
            for (String problem : problems) {
                MyMod.LOG.error("  {}", problem);
            }
        }
    }

    /** Path of the editable structure file, for chat messages. */
    public static File structureFile() {
        return structureFile;
    }

    /**
     * Re-reads the structure file and swaps the shape in the running game. Machines that are already built re-check
     * themselves on their next tick, so a changed shape takes effect immediately.
     *
     * @return human readable problems; empty when the file applied cleanly
     */
    public static List<String> reloadStructure() {
        PetrochemicalComplexStructure.Result result = PetrochemicalComplexStructure.load(structureFile);
        List<String> problems = new ArrayList<>(result.errors());
        if (!problems.isEmpty()) {
            problems.add("Kept the previous structure.");
            return problems;
        }
        for (String warning : result.warnings()) {
            MyMod.LOG.warn("[petrochemical complex structure] {}", warning);
        }
        MTEPetrochemicalComplex.setStructureBlueprint(result.blueprint());
        MyMod.LOG.info(
            "Applied Petrochemical Complex structure: {} wide x {} tall x {} deep (controller offset {})",
            result.blueprint()
                .width(),
            result.blueprint()
                .height(),
            result.blueprint()
                .depth(),
            result.blueprint()
                .offsetSummary());
        return problems;
    }

    /**
     * Re-reads the recipe file and swaps the recipes in the live recipe pool.
     * <p>
     * Never leaves the machine without recipes: if the file does not parse, or produces nothing, the built-in
     * defaults are applied instead and the problems are reported. The file on disk is left untouched so a half
     * finished edit is not lost.
     *
     * @return human readable problems; empty when the file itself applied cleanly
     */
    public static List<String> reload() {
        RecipeConfig config = new RecipeConfig().load(recipeFile);
        List<String> problems = new ArrayList<>(config.errors());
        List<GTRecipeBuilder> builders = config.buildAll();

        if (problems.isEmpty() && builders.isEmpty()) {
            problems.add("The recipe file defines no recipes.");
        }
        if (!problems.isEmpty()) {
            // Fall back to the built-in recipes rather than leaving the machine unable to do anything.
            RecipeConfig defaults = new RecipeConfig().loadDefaults();
            List<GTRecipeBuilder> defaultBuilders = defaults.buildAll();
            if (defaultBuilders.isEmpty()) {
                problems.addAll(defaults.errors());
                return problems;
            }
            problems.add("Applied the built-in default recipes instead; fix the file and run /simplification reload.");
            apply(defaultBuilders);
            return problems;
        }

        apply(builders);
        return problems;
    }

    private static void apply(List<GTRecipeBuilder> builders) {
        ModRecipeMaps.petrochemicalComplexRecipes.getBackend()
            .clearRecipes();
        for (GTRecipeBuilder builder : builders) {
            builder.addTo(ModRecipeMaps.petrochemicalComplexRecipes);
        }
        lastReloadedCount = builders.size();
        MyMod.LOG.info("Applied {} Petrochemical Complex recipes", lastReloadedCount);
    }

    /** Number of recipes applied by the last successful load or reload. */
    public static int recipeCount() {
        return lastReloadedCount;
    }

    /** Full path of the editable recipe file, for chat messages. */
    public static File recipeFile() {
        return recipeFile;
    }

    /**
     * The controller's own recipe: one LV Machine Hull, two LV circuits, one LV Distillery and one LV Chemical
     * Reactor, with the programming circuit set to 15.
     */
    private static void registerAssemblerRecipe() {
        var controller = GregTechAPI.METATILEENTITIES[MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER];
        if (controller == null) return;
        GTRecipeBuilder.builder()
            .circuit(15)
            .itemInputs(
                ItemList.Hull_LV.get(1),
                // The oredict entry for any LV circuit.
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.LV, 2),
                ItemList.Machine_LV_Distillery.get(1),
                ItemList.Machine_LV_ChemicalReactor.get(1))
            .itemOutputs(controller.getStackForm(1))
            .duration(30 * GTRecipeBuilder.SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /** Formats a reload result for chat. */
    public static String describe(List<String> problems) {
        if (problems.isEmpty()) {
            return EnumChatFormatting.GREEN + "Petrochemical Complex recipes reloaded: "
                + EnumChatFormatting.GOLD
                + lastReloadedCount
                + EnumChatFormatting.GREEN
                + " recipes. Press R on a recipe in NEI to refresh its display.";
        }
        StringBuilder message = new StringBuilder(
            EnumChatFormatting.RED + "The recipe file was not applied. "
                + EnumChatFormatting.GRAY
                + problems.size()
                + " problem(s):");
        for (String problem : problems) {
            message.append("\n ")
                .append(EnumChatFormatting.YELLOW)
                .append(problem);
        }
        return message.toString();
    }
}
