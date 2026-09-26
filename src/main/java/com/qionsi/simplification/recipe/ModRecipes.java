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
 * The programming circuit, 1 through 12, is what picks the product. Circuits 1 to 6 are the oil based polymer and fuel
 * recipes from the first design: they consume 10000 mB of some kind of oil plus reagents. Circuits 7 to 12 are the
 * second batch, from {@code more/1.docx}: high octane gasoline starts from oil as well, while the rubbers, the two
 * engineering plastics and the epoxy are built from their monomers, so they take solid reagents and gases instead.
 * <p>
 * Energy per operation follows the design document: 19200 EU for circuit 1 (600 ticks at 32 EU/t), 72000 EU for
 * circuits 2, 3 and 4 (600 ticks at 120 EU/t), 153600 EU for circuits 5, 9 and 11 (320 ticks at 480 EU/t), 86400 EU for
 * circuit 6 (720 ticks at 120 EU/t), 3686400 EU for circuit 7 (600 ticks at 6144 EU/t), 144000 EU for circuit 8 (1200
 * ticks at 120 EU/t), 470400 EU for circuit 10 (240 ticks at 1960 EU/t) and 3276800 EU for circuit 12 (320 ticks at
 * 10240 EU/t).
 * <p>
 * The recipes live in code on purpose: while debugging, editing one of these numbers and letting the IDE swap the
 * method body in takes effect without restarting the game.
 */
public final class ModRecipes {

    /** The oil based recipes 1 to 7 are built around this much oil. */
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
        registerHighOctaneGasoline();
        registerStyreneButadieneRubber();
        registerSiliconeRubber();
        registerPolyphenyleneSulfide();
        registerEpoxyResin();
        registerPolybenzimidazole();
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

    /** 高辛烷值汽油 / High Octane Gasoline, GregTech's {@link Materials#GasolinePremium}. */
    private static void registerHighOctaneGasoline() {
        GTRecipeBuilder.builder()
            .circuit(7)
            .fluidInputs(
                ANY_OIL,
                Materials.Steam.getGas(10870),
                Materials.Oxygen.getGas(2990),
                Materials.Nitrogen.getGas(4180))
            .fluidOutputs(
                Materials.GasolinePremium.getFluid(11130),
                Materials.Hydrogen.getGas(11620),
                Materials.NaphthenicAcid.getFluid(250))
            .itemOutputs(dust(Materials.Carbon, 80), dust(Materials.Sulfur, 3))
            .outputChances(8800, 8000)
            .duration(30 * SECONDS)
            .eut(6144)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 丁苯橡胶 / Styrene-Butadiene Rubber (SBR). */
    private static void registerStyreneButadieneRubber() {
        GTRecipeBuilder.builder()
            .circuit(8)
            .itemInputs(dust(Materials.Carbon, 9), dust(Materials.Sulfur, 1))
            .fluidInputs(Materials.Hydrogen.getGas(26000))
            .fluidOutputs(Materials.StyreneButadieneRubber.getMolten(9000))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 硅橡胶 / Silicone Rubber, GregTech's {@link Materials#RubberSilicone}. */
    private static void registerSiliconeRubber() {
        GTRecipeBuilder.builder()
            .circuit(9)
            .itemInputs(dust(Materials.Carbon, 2), dust(Materials.Silicon, 1), dust(Materials.Sulfur, 1))
            .fluidInputs(Materials.Hydrogen.getGas(24000), Materials.Oxygen.getGas(10000))
            .fluidOutputs(Materials.RubberSilicone.getMolten(9000))
            .duration(16 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 聚苯硫醚 / Polyphenylene Sulfide (PPS). */
    private static void registerPolyphenyleneSulfide() {
        GTRecipeBuilder.builder()
            .circuit(10)
            .itemInputs(
                dust(Materials.Carbon, 6),
                dust(Materials.Sodium, 6),
                dust(Materials.Sulfur, 4))
            .fluidInputs(
                Materials.Hydrogen.getGas(4000),
                Materials.Oxygen.getGas(8000),
                Materials.Chlorine.getGas(2000))
            .fluidOutputs(Materials.PolyphenyleneSulfide.getMolten(1000))
            .duration(12 * SECONDS)
            .eut(1960)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 环氧树脂 / Epoxy Resin, GregTech's {@link Materials#Epoxid}. */
    private static void registerEpoxyResin() {
        GTRecipeBuilder.builder()
            .circuit(11)
            .itemInputs(
                dust(Materials.Carbon, 25),
                dust(Materials.Sodium, 2),
                dust(Materials.Sulfur, 1))
            .fluidInputs(
                Materials.Hydrogen.getGas(30000),
                Materials.Oxygen.getGas(31000),
                Materials.Chlorine.getGas(32000))
            .fluidOutputs(Materials.Epoxid.getMolten(9000))
            .duration(16 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(ModRecipeMaps.petrochemicalComplexRecipes);
    }

    /** 聚苯并咪唑 / Polybenzimidazole (PBI). */
    private static void registerPolybenzimidazole() {
        GTRecipeBuilder.builder()
            .circuit(12)
            .itemInputs(dust(Materials.Carbon, 20))
            .fluidInputs(
                Materials.Hydrogen.getGas(12000),
                Materials.Oxygen.getGas(14000),
                Materials.Nitrogen.getGas(4000),
                Materials.Chlorine.getGas(4000))
            .fluidOutputs(Materials.Polybenzimidazole.getMolten(1000))
            .duration(16 * SECONDS)
            .eut(10240)
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
