package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MetaTileIDs;
import com.qionsi.simplification.MyMod;

import bartworks.common.loaders.ItemRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialMisc;

/**
 * The recipes of the 分子聚合物制造机 / Molecular Polymer Fabricator.
 * <p>
 * Two pools, five recipes each, plus the assembler recipe that makes the controller:
 *
 * <ul>
 * <li>the single step pool consumes the reagents and produces the polymer in one go;</li>
 * <li>the parallel timeline pool produces the very same things out of a parallel timeline, so it lists no reagents at
 * all - the only thing it consumes is nothing, which is exactly the point of the mode.</li>
 * </ul>
 *
 * The two pools deliberately share the same circuit numbers, because the circuit is what picks a recipe: circuit 1 is
 * always the Radox polymer, circuit 2 the Xenoxene, and so on. That way the player sets up the same pattern in a bus
 * and only the mode differs.
 * <p>
 * Every number here comes from the design document, which lists "total EU", seconds and "EU/t" per recipe. The total
 * and the duration are what is written down here; EU/t is simply the one divided by the other, and every one of the ten
 * recipes divides exactly, so no rounding was involved anywhere.
 */
public final class MolecularPolymerRecipes {

    /** Fluid amounts the design document gives in "kmb" (thousands of millibuckets). */
    private static final int KMB = 1_000;

    private static boolean registered;
    private static boolean deferred;

    private MolecularPolymerRecipes() {}

    /**
     * Registers both pools and the controller recipe.
     * <p>
     * The super glue recipe needs a fluid that GT++ keeps in its own material registry rather than in GregTech's. GT++
     * fills that registry during its own pre-initialization, so the fluid is probed here: if it is not there yet the
     * work is handed to {@code GregTechAPI.sAfterGTPostload} and tried again at the very end of GregTech's postload,
     * which is the same trick the rare earth recipes use for the Bartworks fluids. Without it the recipe would be
     * dropped silently, because GregTech discards any recipe with an ingredient it cannot resolve.
     */
    public static void init() {
        if (registered) return;
        if (superGlueFluid() == null) {
            if (!deferred) {
                deferred = true;
                MyMod.LOG.info(
                    "GT++ has not registered its materials yet; the Molecular Polymer Fabricator recipes will be "
                        + "registered after GregTech's postload.");
                GregTechAPI.sAfterGTPostload.add(MolecularPolymerRecipes::init);
            }
            return;
        }
        registered = true;

        int singleBefore = size(ModRecipeMaps.molecularPolymerSingleRecipes);
        int parallelBefore = size(ModRecipeMaps.molecularPolymerParallelRecipes);

        registerSingleStep();
        registerParallelTimeline();
        registerControllerAssembler();

        MyMod.LOG.info(
            "Registered {} single step and {} parallel timeline Molecular Polymer Fabricator recipes",
            size(ModRecipeMaps.molecularPolymerSingleRecipes) - singleBefore,
            size(ModRecipeMaps.molecularPolymerParallelRecipes) - parallelBefore);
    }

    private static int size(gregtech.api.recipe.RecipeMap<gregtech.api.recipe.RecipeMapBackend> map) {
        return map.getBackend()
            .getAllRecipes()
            .size();
    }

    /** The GT++ super glue fluid, or {@code null} while GT++ has not built its material registry. */
    private static FluidStack superGlueFluid() {
        try {
            return new FluidStack(MaterialMisc.ETHYL_CYANOACRYLATE.getFluid(), 10 * KMB);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 单步制造

    /**
     * 单步制造 / single step: the reagents go in, the polymer comes out, in one step.
     * <p>
     * Total EU and duration are the design document's; EU/t is total divided by ticks.
     */
    private static void registerSingleStep() {
        // 1: 熔融拉多X聚合物 + 重拉多X. 1,310,720,000 EU over 32 s.
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(dust(Materials.Carbon, 64), dust(Materials.Osmium, 24))
            .fluidInputs(Materials.Hydrogen.getGas(16 * KMB), Materials.Nitrogen.getGas(16 * KMB))
            .fluidOutputs(Materials.RadoxPolymer.getMolten(9_216), Materials.RadoxHeavy.getFluid(8 * KMB))
            .duration(32 * SECONDS)
            .eut(2_048_000)
            .addTo(ModRecipeMaps.molecularPolymerSingleRecipes);

        // 2: 异氙 / Xenoxene. 1,310,720,000 EU over 32 s.
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(dust(Materials.Antimony, 6))
            .fluidInputs(Materials.Oxygen.getGas(9 * KMB), Materials.Oil.getFluid(100 * KMB))
            .fluidOutputs(Materials.Xenoxene.getFluid(100 * KMB))
            .duration(32 * SECONDS)
            .eut(2_048_000)
            .addTo(ModRecipeMaps.molecularPolymerSingleRecipes);

        // 3: 氰基丙烯酸乙酯 / 502 super glue. 12,928,000 EU over 80 s.
        GTRecipeBuilder.builder()
            .circuit(3)
            .itemInputs(dust(Materials.Carbon, 41))
            .fluidInputs(Materials.Hydrogen.getGas(142 * KMB), Materials.Oxygen.getGas(41 * KMB))
            .fluidOutputs(superGlueFluid())
            .duration(80 * SECONDS)
            .eut(8_080)
            .addTo(ModRecipeMaps.molecularPolymerSingleRecipes);

        // 4: 熔融凯芙拉 / molten Kevlar. 38,400,000 EU over 60 s.
        GTRecipeBuilder.builder()
            .circuit(4)
            .itemInputsUnsafe(dust(Materials.Carbon, 65))
            .fluidInputs(
                Materials.Hydrogen.getGas(52 * KMB),
                Materials.Nitrogen.getGas(8 * KMB),
                Materials.Oxygen.getGas(14 * KMB))
            .fluidOutputs(Materials.Kevlar.getMolten(2_144))
            .duration(60 * SECONDS)
            .eut(32_000)
            .addTo(ModRecipeMaps.molecularPolymerSingleRecipes);

        // 5: 邻苯二甲酸 / Phthalic Acid. 144,000 EU over 60 s.
        GTRecipeBuilder.builder()
            .circuit(5)
            .itemInputs(dust(Materials.Carbon, 8))
            .fluidInputs(Materials.Hydrogen.getGas(6 * KMB), Materials.Oxygen.getGas(4 * KMB))
            .fluidOutputs(Materials.PhthalicAcid.getFluid(1 * KMB))
            .duration(60 * SECONDS)
            .eut(120)
            .addTo(ModRecipeMaps.molecularPolymerSingleRecipes);
    }

    // ------------------------------------------------------------------ 平行时空制造

    /**
     * 平行时空制造 / parallel timeline: the same products, pulled out of a parallel timeline, so no reagent is listed
     * and none is consumed - the programming circuit is the only input, and GregTech does not consume an integrated
     * circuit.
     * <p>
     * The design document halves the total EU and the duration of the first three, keeps the duration of the last two
     * and raises their power, which is why the numbers below look uneven next to the single step ones.
     */
    private static void registerParallelTimeline() {
        // 1: 655,360,000 EU over 16 s.
        GTRecipeBuilder.builder()
            .circuit(1)
            .fluidOutputs(Materials.RadoxPolymer.getMolten(9_216), Materials.RadoxHeavy.getFluid(8 * KMB))
            .duration(16 * SECONDS)
            .eut(2_048_000)
            .addTo(ModRecipeMaps.molecularPolymerParallelRecipes);

        // 2: 655,360,000 EU over 16 s.
        GTRecipeBuilder.builder()
            .circuit(2)
            .fluidOutputs(Materials.Xenoxene.getFluid(100 * KMB))
            .duration(16 * SECONDS)
            .eut(2_048_000)
            .addTo(ModRecipeMaps.molecularPolymerParallelRecipes);

        // 3: 6,464,000 EU over 40 s.
        GTRecipeBuilder.builder()
            .circuit(3)
            .fluidOutputs(superGlueFluid())
            .duration(40 * SECONDS)
            .eut(8_080)
            .addTo(ModRecipeMaps.molecularPolymerParallelRecipes);

        // 4: 153,600,000 EU over 60 s - the same duration as the single step recipe but four times the power.
        GTRecipeBuilder.builder()
            .circuit(4)
            .fluidOutputs(Materials.Kevlar.getMolten(2_144))
            .duration(60 * SECONDS)
            .eut(128_000)
            .addTo(ModRecipeMaps.molecularPolymerParallelRecipes);

        // 5: 9,720,000 EU over 60 s.
        GTRecipeBuilder.builder()
            .circuit(5)
            .fluidOutputs(Materials.PhthalicAcid.getFluid(1 * KMB))
            .duration(60 * SECONDS)
            .eut(8_100)
            .addTo(ModRecipeMaps.molecularPolymerParallelRecipes);
    }

    // ------------------------------------------------------------------ controller

    /**
     * The controller's own assembler recipe: circuit 15, 64 Large Chemical Reactors, 64 Bacterial Vats and a bucket of
     * molten polytetrafluoroethylene, at 8000 EU/t for 300 seconds as the design document specifies.
     * <p>
     * Both machine stacks are made with {@code copyAmountUnsafe}, because a normal {@code copyAmount} would stop at one
     * stack of 64 - which happens to be what is wanted here, but is not what the call would do.
     */
    private static void registerControllerAssembler() {
        var controller = GregTechAPI.METATILEENTITIES[MetaTileIDs.MOLECULAR_POLYMER_FABRICATOR_CONTROLLER];
        if (controller == null) {
            MyMod.LOG.error(
                "The Molecular Polymer Fabricator controller was not registered, so its assembler recipe is skipped.");
            return;
        }
        ItemStack largeChemicalReactor = ItemList.Machine_Multi_LargeChemicalReactor.get(1);
        ItemStack bacterialVat = ItemRegistry.vat;
        if (largeChemicalReactor == null || bacterialVat == null) {
            MyMod.LOG.error(
                "The Large Chemical Reactor ({}) or the Bacterial Vat ({}) was not found, so the Molecular Polymer "
                    + "Fabricator assembler recipe is skipped.",
                largeChemicalReactor,
                bacterialVat);
            return;
        }
        GTRecipeBuilder.builder()
            .circuit(15)
            .itemInputs(
                GTUtility.copyAmountUnsafe(64, largeChemicalReactor),
                GTUtility.copyAmountUnsafe(64, bacterialVat))
            .fluidInputs(Materials.Polytetrafluoroethylene.getMolten(1 * KMB))
            .itemOutputs(controller.getStackForm(1))
            .duration(300 * SECONDS)
            .eut(8_000)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /** Convenience wrapper so a recipe can be written as "64 Carbon dust". */
    private static ItemStack dust(Materials material, int amount) {
        ItemStack base = GTOreDictUnificator.get(OrePrefixes.dust, material, 1);
        if (base == null) return null;
        // copyAmount clamps anything above 64 to 64, so a recipe written through it silently asks for one
        // stack instead of the amount it names. The Kevlar recipe needs 65 Carbon Dust, which is exactly the
        // kind of number that used to come out as 64.
        return GTUtility.copyAmountUnsafe(amount, base);
    }
}
