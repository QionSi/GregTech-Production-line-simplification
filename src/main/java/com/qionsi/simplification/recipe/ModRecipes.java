package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;

import com.qionsi.simplification.MetaTileIDs;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.objects.SubstituteFluidStack;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;

/**
 * The recipes of the 石油化工综合体 / Petrochemical Complex.
 * <p>
 * Every recipe consumes 10000 mB of some kind of oil plus a set of reagents and a programming circuit, and produces
 * both fluids and a few solid by-products. The programming circuit, 1 through 6, is what picks the product.
 * <p>
 * Energy per operation follows the design document: 19200 EU for circuit 1 (600 ticks at 32 EU/t), 72000 EU for
 * circuits 2, 3 and 4 (600 ticks at 120 EU/t), 153600 EU for circuit 5 (320 ticks at 480 EU/t) and 86400 EU for
 * circuit 6 (720 ticks at 120 EU/t).
 * <p>
 * The recipes live in code on purpose: while debugging, editing one of these numbers and letting the IDE swap the
 * method body in takes effect without restarting the game.
 */
public final class ModRecipes {

    /** All six recipes are built around this much oil. */
    private static final int OIL_AMOUNT = 10000;

    /**
     * Anything the player might reasonably call "oil" is accepted. GregTech has four grades and the recipe does not
     * care which one is used.
     */
    private static final SubstituteFluidStack ANY_OIL = new SubstituteFluidStack(
        Materials.Oil.getFluid(OIL_AMOUNT),
        Materials.OilLight.getFluid(OIL_AMOUNT),
        Materials.OilHeavy.getFluid(OIL_AMOUNT),
        Materials.OilExtraHeavy.getFluid(OIL_AMOUNT));

    private ModRecipes() {}

    public static void init() {
        registerPolyethylene();
        registerPolyvinylChloride();
        registerPolytetrafluoroethylene();
        registerPolystyrene();
        registerCetaneBoostedDiesel();
        registerDiesel();
        registerAssemblerRecipe();
    }

    // spotless:off

    /** 聚乙烯 / Polyethylene. */
    private static void registerPolyethylene() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .fluidInputs(ANY_OIL, Materials.Oxygen.getGas(10000), Materials.Steam.getGas(12000))
            .fluidOutputs(Materials.Polyethylene.getMolten(1656))
            .itemOutputs(dust(Materials.Sulfur, 1), dust(Materials.Carbon, 1))
            .outputChances(10000, 3333)
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 聚氯乙烯 / Polyvinyl Chloride. */
    private static void registerPolyvinylChloride() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .fluidInputs(
                ANY_OIL,
                Materials.Chlorine.getGas(1104),
                Materials.Oxygen.getGas(10000),
                Materials.Steam.getGas(12000))
            .fluidOutputs(Materials.PolyvinylChloride.getMolten(1656))
            .itemOutputs(dust(Materials.Sulfur, 1), dust(Materials.Carbon, 1))
            .outputChances(10000, 3333)
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 聚四氟乙烯 / Polytetrafluoroethylene. */
    private static void registerPolytetrafluoroethylene() {
        GTRecipeBuilder.builder()
            .circuit(3)
            .fluidInputs(
                ANY_OIL,
                Materials.Hydrogen.getGas(2208),
                Materials.Fluorine.getGas(2208),
                Materials.Oxygen.getGas(10000),
                Materials.Steam.getGas(12000))
            .fluidOutputs(Materials.Polytetrafluoroethylene.getMolten(828))
            .itemOutputs(dust(Materials.Sulfur, 1), dust(Materials.Carbon, 1))
            .outputChances(10000, 3333)
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 聚苯乙烯 / Polystyrene. */
    private static void registerPolystyrene() {
        GTRecipeBuilder.builder()
            .circuit(4)
            .fluidInputs(
                ANY_OIL,
                Materials.Hydrogen.getGas(552),
                Materials.Oxygen.getGas(10000),
                Materials.Steam.getGas(12000))
            .fluidOutputs(Materials.Styrene.getFluid(1656))
            .itemOutputs(dust(Materials.Carbon, 1))
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 高十六烷值柴油 / Cetane-Boosted Diesel, GregTech's {@link Materials#NitroFuel}. */
    private static void registerCetaneBoostedDiesel() {
        GTRecipeBuilder.builder()
            .circuit(5)
            .fluidInputs(
                ANY_OIL,
                Materials.Steam.getGas(12000),
                Materials.Oxygen.getGas(4080),
                Materials.Nitrogen.getGas(960))
            .fluidOutputs(
                Materials.NitroFuel.getFluid(12000),
                Materials.Hydrogen.getGas(5520),
                Materials.Water.getFluid(2040),
                Materials.Helium.getGas(1440),
                Materials.Methane.getGas(10080),
                Materials.Ethane.getGas(144),
                Materials.Propene.getGas(144))
            .itemOutputs(dust(Materials.Sulfur, 1), dust(Materials.Carbon, 12))
            .duration(16 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 柴油 / Diesel. */
    private static void registerDiesel() {
        GTRecipeBuilder.builder()
            .circuit(6)
            .fluidInputs(ANY_OIL, Materials.Steam.getGas(12000))
            .fluidOutputs(
                Materials.Diesel.getFluid(12000),
                Materials.Methane.getGas(10080),
                Materials.Propene.getGas(144),
                Materials.Helium.getGas(1000))
            .itemOutputs(dust(Materials.Sulfur, 2), dust(Materials.Carbon, 3))
            .duration(36 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** The controller's own assembler recipe: circuit 15, one LV Machine Hull, two LV circuits and two LV machines. */
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
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    // spotless:on

    /** Convenience wrapper so a recipe can be written as "1 Sulfur dust". */
    private static ItemStack dust(Materials material, int amount) {
        return GTOreDictUnificator.get(OrePrefixes.dust, material, amount);
    }
}
