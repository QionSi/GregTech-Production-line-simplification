package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.qionsi.simplification.MetaTileIDs;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.item.ModItems;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTUtility;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.core.material.MaterialMisc;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/**
 * The recipes of the 简易净水机 / Simple Water Purifier, the parts it is built from, and the four chips.
 *
 * <h2>The machine recipes</h2>
 *
 * Four recipes, one per grade of purified water, picked apart by the programming circuit. Each stores its minimum
 * structure tier in {@code mSpecialValue}: the design document writes it as "at least t1" and so on, and the machine
 * refuses a recipe whose minimum is above the structure standing.
 * <p>
 * Every one of them runs for exactly 120 seconds and the machine does not overclock, so the EU/t figure is the number
 * in the document and the total is that times 2400 ticks. For circuits 3 and 4 the document's own "total EU" column
 * disagrees with its "EU/t" column by a factor of 20 - it had multiplied by 120 instead of by 120 *ticks* - so the
 * EU/t column is what is used here, which is also the column that makes the four tiers rise as they should (32k, 131k,
 * 524k, 16.8M).
 */
public final class SimpleWaterPurifierRecipes {

    /** One bucket, the amount the design document writes as "1kmb". */
    private static final int KMB = 1_000;

    /** Every recipe runs 120 seconds. */
    private static final int RECIPE_TICKS = 120 * SECONDS;

    private static boolean registered;
    private static boolean deferred;

    private SimpleWaterPurifierRecipes() {}

    /**
     * Registers the pool and the part recipes.
     * <p>
     * This class runs as part of the controller registration, which happens before {@link ModItems} creates the report
     * and the three chips. While those are still null the work is handed to GregTech's postload, by which time they
     * exist - the same deferral the rare earth and park recipes use.
     */
    public static void init() {
        if (registered) return;
        if (ModItems.waterPurifierReport == null || ModItems.waterPurifierChipT2 == null) {
            if (!deferred) {
                deferred = true;
                MyMod.LOG.info(
                    "The Simple Water Purifier parts are not registered yet; its recipes will be registered after "
                        + "GregTech's postload.");
                GregTechAPI.sAfterGTPostload.add(SimpleWaterPurifierRecipes::init);
            }
            return;
        }
        registered = true;

        int before = ModRecipeMaps.simpleWaterPurifierRecipes.getBackend()
            .getAllRecipes()
            .size();
        registerMachineRecipes();
        registerParts();
        MyMod.LOG.info(
            "Registered {} Simple Water Purifier recipes",
            ModRecipeMaps.simpleWaterPurifierRecipes.getBackend()
                .getAllRecipes()
                .size() - before);
    }

    private static void registerMachineRecipes() {
        // circuit 1, at least t1: 1000 mb of water in, grade 1 and grade 2 out. 32,000 EU/t for 120 s.
        GTRecipeBuilder.builder()
            .circuit(1)
            .fluidInputs(Materials.Water.getFluid(1 * KMB))
            .fluidOutputs(
                Materials.Grade1PurifiedWater.getFluid(1 * KMB),
                Materials.Grade2PurifiedWater.getFluid(1 * KMB))
            .duration(RECIPE_TICKS)
            .eut(32_000)
            .specialValue(MTESimpleWaterPurifierTier.T1)
            .addTo(ModRecipeMaps.simpleWaterPurifierRecipes);

        // circuit 2, at least t2: water plus UU amplifier, grades 1 to 4. 131,000 EU/t for 120 s.
        GTRecipeBuilder.builder()
            .circuit(2)
            .fluidInputs(Materials.Water.getFluid(1 * KMB), Materials.UUAmplifier.getFluid(100))
            .fluidOutputs(
                Materials.Grade1PurifiedWater.getFluid(1 * KMB),
                Materials.Grade2PurifiedWater.getFluid(1 * KMB),
                Materials.Grade3PurifiedWater.getFluid(1 * KMB),
                Materials.Grade4PurifiedWater.getFluid(1 * KMB))
            .duration(RECIPE_TICKS)
            .eut(131_000)
            .specialValue(MTESimpleWaterPurifierTier.T2)
            .addTo(ModRecipeMaps.simpleWaterPurifierRecipes);

        // circuit 3, at least t3: a whole bucket of UU amplifier, grades 1 to 6. 524,000 EU/t for 120 s.
        GTRecipeBuilder.builder()
            .circuit(3)
            .fluidInputs(Materials.Water.getFluid(1 * KMB), Materials.UUAmplifier.getFluid(1 * KMB))
            .fluidOutputs(
                Materials.Grade1PurifiedWater.getFluid(1 * KMB),
                Materials.Grade2PurifiedWater.getFluid(1 * KMB),
                Materials.Grade3PurifiedWater.getFluid(1 * KMB),
                Materials.Grade4PurifiedWater.getFluid(1 * KMB),
                Materials.Grade5PurifiedWater.getFluid(1 * KMB),
                Materials.Grade6PurifiedWater.getFluid(1 * KMB))
            .duration(RECIPE_TICKS)
            .eut(524_000)
            .specialValue(MTESimpleWaterPurifierTier.T3)
            .addTo(ModRecipeMaps.simpleWaterPurifierRecipes);

        // circuit 4, at least t4: 100 mb of UU matter, grades 1 to 8. 16,776,000 EU/t for 120 s.
        GTRecipeBuilder.builder()
            .circuit(4)
            .fluidInputs(Materials.Water.getFluid(1 * KMB), Materials.UUMatter.getFluid(100))
            .fluidOutputs(
                Materials.Grade1PurifiedWater.getFluid(1 * KMB),
                Materials.Grade2PurifiedWater.getFluid(1 * KMB),
                Materials.Grade3PurifiedWater.getFluid(1 * KMB),
                Materials.Grade4PurifiedWater.getFluid(1 * KMB),
                Materials.Grade5PurifiedWater.getFluid(1 * KMB),
                Materials.Grade6PurifiedWater.getFluid(1 * KMB),
                Materials.Grade7PurifiedWater.getFluid(1 * KMB),
                Materials.Grade8PurifiedWater.getFluid(1 * KMB))
            .duration(RECIPE_TICKS)
            .eut(16_776_000)
            .specialValue(MTESimpleWaterPurifierTier.T4)
            .addTo(ModRecipeMaps.simpleWaterPurifierRecipes);
    }

    /**
     * 净水厂研究报告 and the three chips, plus the controller itself.
     * <p>
     * The controller and all three chips are assembly line recipes: their inputs are ordered, and each one is gated by
     * the data stick of the previous step, which is what the {@code RESEARCH_ITEM} and {@code SCANNING} metadata
     * describe. The report itself is a plain assembler recipe.
     */
    private static void registerParts() {
        ItemStack report = ModItems.waterPurifierReport == null ? null : new ItemStack(ModItems.waterPurifierReport);
        ItemStack chipT2 = ModItems.waterPurifierChipT2 == null ? null : new ItemStack(ModItems.waterPurifierChipT2);
        ItemStack chipT3 = ModItems.waterPurifierChipT3 == null ? null : new ItemStack(ModItems.waterPurifierChipT3);
        ItemStack chipT4 = ModItems.waterPurifierChipT4 == null ? null : new ItemStack(ModItems.waterPurifierChipT4);
        var controller = GregTechAPI.METATILEENTITIES[MetaTileIDs.SIMPLE_WATER_PURIFIER_CONTROLLER];
        if (report == null || chipT2 == null || chipT3 == null || chipT4 == null || controller == null) {
            MyMod.LOG
                .error("The Simple Water Purifier items or controller are missing, so its parts recipes are skipped.");
            return;
        }

        // 净水厂研究报告: 1 purification plant + 64 paper + 1 ink sac + 1 feather, assembler circuit 15.
        GTRecipeBuilder.builder()
            .circuit(15)
            .itemInputs(
                ItemList.Machine_Multi_PurificationPlant.get(1),
                new ItemStack(Items.paper, 64),
                new ItemStack(Items.dye, 1, 0),
                new ItemStack(Items.feather, 1))
            .itemOutputs(report)
            .duration(60 * SECONDS)
            .eut(8_192)
            .addTo(RecipeMaps.assemblerRecipes);

        // The controller: 1 plant + 1 clarifier + 64 screws + a large chemical reactor + a Volcanus + a
        // hyper-intensity laser engraver, wet with distilled water and Indalloy 140. 3270 EU/t for 60 s.
        GTRecipeBuilder.builder()
            .itemInputs(
                ItemList.Machine_Multi_PurificationPlant.get(1),
                ItemList.Machine_Multi_PurificationUnitClarifier.get(1),
                screw(Materials.NaquadahAlloy, 64),
                ItemList.Machine_Multi_LargeChemicalReactor.get(1),
                GregtechItemList.Machine_Adv_BlastFurnace.get(1),
                ItemList.Machine_Multi_IndustrialLaserEngraver.get(1))
            .fluidInputs(GTModHandler.getDistilledWater(512 * KMB), MaterialsAlloy.INDALLOY_140.getFluidStack(8_192))
            .itemOutputs(controller.getStackForm(1))
            .duration(60 * SECONDS)
            .eut(3_270)
            .metadata(GTRecipeConstants.RESEARCH_ITEM, report)
            .metadata(GTRecipeConstants.SCANNING, new Scanning(3600 * SECONDS, 8_100))
            .addTo(GTRecipeConstants.AssemblyLine);

        // T2 chip: 1 flocculation unit + 64 neutronium screws + a large chemical reactor + a Dangote Distillus.
        GTRecipeBuilder.builder()
            .itemInputs(
                ItemList.Machine_Multi_PurificationUnitFlocculator.get(1),
                screw(Materials.Neutronium, 64),
                ItemList.Machine_Multi_LargeChemicalReactor.get(1),
                GregtechItemList.Machine_Adv_DistillationTower.get(1))
            .fluidInputs(
                Materials.Grade2PurifiedWater.getFluid(1_024 * KMB),
                MaterialsAlloy.INDALLOY_140.getFluidStack(16_384),
                Materials.Lubricant.getFluid(40 * KMB))
            .itemOutputs(chipT2)
            .duration(320 * SECONDS)
            .eut(52_400)
            .metadata(GTRecipeConstants.RESEARCH_ITEM, controller.getStackForm(1))
            .metadata(GTRecipeConstants.SCANNING, new Scanning(3600 * SECONDS, 131_000))
            .addTo(GTRecipeConstants.AssemblyLine);

        // T3 chip: 1 plasma heater unit + 64 cosmic neutronium screws + a hyper-intensity laser engraver. The design
        // document lists only three items, and the assembly line insists on at least four ordered inputs, so a
        // programming circuit is added as the fourth - it is not consumed, it is only there to satisfy the shape.
        GTRecipeBuilder.builder()
            .itemInputs(
                ItemList.Machine_Multi_PurificationUnitPlasmaHeater.get(1),
                screw(Materials.CosmicNeutronium, 64),
                ItemList.Machine_Multi_IndustrialLaserEngraver.get(1),
                modernIndustrialPark())
            .fluidInputs(
                Materials.Grade4PurifiedWater.getFluid(2_048 * KMB),
                MaterialsAlloy.INDALLOY_140.getFluidStack(32_768),
                Materials.Lubricant.getFluid(80 * KMB))
            .itemOutputs(chipT3)
            .duration(1_200 * SECONDS)
            .eut(209_000)
            .metadata(GTRecipeConstants.RESEARCH_ITEM, chipT2)
            .metadata(GTRecipeConstants.SCANNING, new Scanning(3600 * SECONDS, 524_000))
            .addTo(GTRecipeConstants.AssemblyLine);

        // T4 chip: 1 degasser unit + 64 bedrockium screws + a large chemical reactor + a Dangote Distillus.
        GTRecipeBuilder.builder()
            .itemInputs(
                ItemList.Machine_Multi_PurificationUnitDegasifier.get(1),
                screw(Materials.Bedrockium, 64),
                ItemList.Machine_Multi_LargeChemicalReactor.get(1),
                GregtechItemList.Machine_Adv_DistillationTower.get(1))
            .fluidInputs(
                Materials.Grade6PurifiedWater.getFluid(4_096 * KMB),
                MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(65_536),
                Materials.Lubricant.getFluid(160 * KMB))
            .itemOutputs(chipT4)
            .duration(3_600 * SECONDS)
            .eut(838_860)
            .metadata(GTRecipeConstants.RESEARCH_ITEM, chipT3)
            .metadata(GTRecipeConstants.SCANNING, new Scanning(3600 * SECONDS, 524_000))
            .addTo(GTRecipeConstants.AssemblyLine);
    }

    /** One 现代产业园 / Modern Industrial Park controller, the fourth input the T3 chip recipe needs. */
    private static ItemStack modernIndustrialPark() {
        var park = GregTechAPI.METATILEENTITIES[MetaTileIDs.MODERN_INDUSTRIAL_PARK_CONTROLLER];
        if (park == null) {
            MyMod.LOG.error(
                "The Modern Industrial Park controller is missing, so the T3 chip recipe cannot be built; a programming "
                    + "circuit is used instead.");
            return GTUtility.getIntegratedCircuit(1);
        }
        return park.getStackForm(1);
    }

    /** A screw stack of exactly {@code amount}, however large. */
    private static ItemStack screw(Materials material, int amount) {
        ItemStack base = GTOreDictUnificator.get(OrePrefixes.screw, material, 1);
        if (base == null) return null;
        return GTUtility.copyAmountUnsafe(amount, base);
    }

    /**
     * The four structure tiers, as integers, kept here only so the recipe file can name them without depending on the
     * machine class.
     */
    private static final class MTESimpleWaterPurifierTier {

        static final int T1 = 1;
        static final int T2 = 2;
        static final int T3 = 3;
        static final int T4 = 4;

        private MTESimpleWaterPurifierTier() {}
    }
}
