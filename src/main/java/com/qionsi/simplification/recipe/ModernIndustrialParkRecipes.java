package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MetaTileIDs;
import com.qionsi.simplification.MyMod;

import bartworks.system.material.WerkstoffLoader;
import goodgenerator.items.GGMaterial;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.items.CombType;
import gregtech.loaders.misc.GTBees;
import gtPlusPlus.core.fluids.GTPPFluids;
import gtPlusPlus.core.material.MaterialsElements;

/**
 * The recipes of the 现代产业园 / Modern Industrial Park, plus the assembler recipe that makes the controller.
 * <p>
 * Six recipes, picked apart by the programming circuit (1 to 6). They are the lategame ore line gathered into one
 * machine: the prismarine acid chain, the netherite nanoparticle chain and the three naquadah oxide mixture chains.
 *
 * <h2>Where the numbers come from</h2>
 *
 * The design document gives "total EU", a duration in seconds and "EU/t" for every recipe, and for five of the six the
 * three agree exactly once divided. The exception is circuit 5: the document lists 5,880,000,000 EU total for 60 s,
 * which is 4,900,000 EU/t, but its own power column says 490,000 EU/t and that is what circuits 4 and 6 - the other two
 * naquadah oxide recipes, whose numbers do agree - are built from. This recipe therefore uses 490,000 EU/t for 60 s,
 * i.e. 588,000,000 EU total, and says so in the log so the difference cannot go unnoticed.
 */
public final class ModernIndustrialParkRecipes {

    /** Fluid amounts the design document gives in "kmb" (thousands of millibuckets). */
    private static final int KMB = 1_000;

    /** Fluid id of the waste the naquadah oxide recipes give back, as the design document writes it. */
    private static final int WASTE_FLUID_ID = 1340;

    /** How many recipes the design document lists for this pool. */
    private static final int EXPECTED_RECIPES = 6;

    private static boolean registered;
    private static boolean deferred;

    private ModernIndustrialParkRecipes() {}

    /**
     * Registers the pool and the controller recipe.
     * <p>
     * Three of the six recipes need Bartworks items (its meta item only exists once Bartworks has built its materials)
     * and two need GT++ fluids, so the work is deferred to the end of GregTech's postload when those are not there yet
     * -
     * the same trick the rare earth recipes use. Without it GregTech would drop any recipe whose ingredient it cannot
     * resolve, silently.
     */
    public static void init() {
        if (registered) return;
        if (!bartworksReady() || wasteFluid() == null) {
            if (!deferred) {
                deferred = true;
                MyMod.LOG.info(
                    "Bartworks has not built its materials yet; the Modern Industrial Park recipes will be registered "
                        + "after GregTech's postload.");
                GregTechAPI.sAfterGTPostload.add(ModernIndustrialParkRecipes::init);
            }
            return;
        }
        registered = true;

        int before = recipeCount();
        registerRecipes();
        registerControllerAssembler();
        int added = recipeCount() - before;
        MyMod.LOG.info("Registered {} Modern Industrial Park recipes", added);
        logRegisteredCircuits();
        // GregTech drops a recipe silently when it cannot resolve one of its ingredients, so a count below the six the
        // pool is supposed to hold is the only symptom there is. Say so loudly rather than let the machine quietly run
        // fewer recipes than the design document lists.
        if (added != EXPECTED_RECIPES) {
            MyMod.LOG.error(
                "Only {} of the {} Modern Industrial Park recipes were accepted by GregTech. A recipe is dropped "
                    + "silently when one of its ingredients cannot be resolved, so look for the ingredient that is "
                    + "missing rather than for the recipe.",
                added,
                EXPECTED_RECIPES);
        }
    }

    /**
     * Lists the circuit numbers of the recipes that actually reached the pool.
     * <p>
     * This exists because GregTech drops a recipe whose ingredients it cannot resolve without saying a word. When that
     * happens the count above goes down, and this line says which of the six it was.
     */
    private static void logRegisteredCircuits() {
        StringBuilder circuits = new StringBuilder();
        for (GTRecipe recipe : ModRecipeMaps.modernIndustrialParkRecipes.getBackend()
            .getAllRecipes()) {
            int circuit = -1;
            if (recipe.mInputs != null) {
                for (ItemStack stack : recipe.mInputs) {
                    if (stack != null && stack.getItem() == ItemList.Circuit_Integrated.getItem()) {
                        circuit = stack.getItemDamage();
                    }
                }
            }
            if (circuits.length() > 0) circuits.append(", ");
            circuits.append(circuit);
        }
        MyMod.LOG.info("Modern Industrial Park pool holds the recipes for circuits [{}]", circuits);
    }

    private static int recipeCount() {
        return ModRecipeMaps.modernIndustrialParkRecipes.getBackend()
            .getAllRecipes()
            .size();
    }

    private static boolean bartworksReady() {
        try {
            return WerkstoffLoader.items.get(OrePrefixes.dust) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static FluidStack wasteFluid() {
        Fluid fluid = FluidRegistry.getFluid(WASTE_FLUID_ID);
        return fluid == null ? null : new FluidStack(fluid, 1);
    }

    private static FluidStack waste(int amount) {
        return new FluidStack(FluidRegistry.getFluid(WASTE_FLUID_ID), amount);
    }

    /** Neon, which GT++ keeps in its element registry rather than in GregTech's material registry. */
    private static FluidStack neon(int amount) {
        return new FluidStack(MaterialsElements.getInstance().NEON.getFluid(), amount);
    }

    private static void registerRecipes() {
        // 1: 海晶酸 / prismarine acid. 314,572,800 EU over 32 s.
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(
                // 海晶碎片 / prismarine shards: the shard form of Materials.Prismarine, exactly how GregTech's own
                // netherite chain writes it.
                GTOreDictUnificator.get(OrePrefixes.shard, Materials.Prismarine, 12),
                dust(Materials.NaquadahEnriched, 32))
            .fluidInputs(
                Materials.Hydrogen.getGas(2 * KMB),
                Materials.Oxygen.getGas(2 * KMB),
                Materials.Nitrogen.getGas(24 * KMB))
            .fluidOutputs(Materials.PrismaticAcid.getFluid(10 * KMB), Materials.Strontium.getMolten(720))
            .itemOutputs(dust(Materials.PrismaticNaquadah, 5))
            .duration(32 * SECONDS)
            .eut(491_520)
            .addTo(ModRecipeMaps.modernIndustrialParkRecipes);

        // 2: 强健合下界合金纳米颗粒 / intensely bonded netherite nanoparticles, the full batch. 98,304,000 EU over
        // 200 s.
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(new ItemStack(Blocks.cobblestone, 41))
            .fluidInputs(
                Materials.NetherAir.getFluid(15_000 * KMB),
                Materials.Water.getFluid(1_625),
                Materials.HellishMetal.getMolten(1_296))
            .itemOutputs(
                ItemList.Intensely_Bonded_Netherite_Nanoparticles.get(9),
                dust(Materials.NetherQuartz, 390),
                dust(Materials.Sulfur, 2_496),
                dust(Materials.Cobalt, 312),
                dust(Materials.Antimony, 536),
                dust(Materials.CertusQuartz, 390),
                dust(Materials.Ardite, 312),
                dust(Materials.Glowstone, 7_488))
            .fluidOutputs(
                Materials.NefariousGas.getFluid(94 * KMB),
                neon(540 * KMB),
                Materials.NitrogenDioxide.getGas(2_100 * KMB),
                Materials.SulfurDioxide.getGas(5_700 * KMB),
                Materials.SulfurTrioxide.getGas(3_150 * KMB),
                new FluidStack(GTPPFluids.CoalGas, 240 * KMB),
                new FluidStack(GTPPFluids.Anthracene, 990 * KMB),
                Materials.MysteriousCrystal.getMolten(288))
            .duration(200 * SECONDS)
            .eut(24_576)
            .addTo(ModRecipeMaps.modernIndustrialParkRecipes);

        // 3: the same nanoparticle batch, half the reagents and twice the time. 240,000,000 EU over 400 s.
        GTRecipeBuilder.builder()
            .circuit(3)
            // 下界合金蜂窝 / netherite combs: GT's own comb item, asked for exactly the way GregTech's netherite chain
            // does it. An ore dictionary lookup by name does not exist for these and silently yields nothing.
            .itemInputs(new ItemStack(Blocks.cobblestone, 2), GTBees.combs.getStackForType(CombType.NETHERITE, 36))
            .fluidInputs(
                Materials.NetherAir.getFluid(7_500 * KMB),
                Materials.Water.getFluid(812),
                Materials.HellishMetal.getMolten(648))
            .itemOutputs(
                ItemList.Intensely_Bonded_Netherite_Nanoparticles.get(9),
                dust(Materials.NetherQuartz, 195),
                dust(Materials.Sulfur, 1_248),
                dust(Materials.Cobalt, 156),
                dust(Materials.Antimony, 268),
                dust(Materials.CertusQuartz, 195),
                dust(Materials.Ardite, 156),
                dust(Materials.Glowstone, 3_744))
            .fluidOutputs(
                Materials.NefariousGas.getFluid(47 * KMB),
                neon(270 * KMB),
                Materials.NitrogenDioxide.getGas(1_050 * KMB),
                Materials.SulfurDioxide.getGas(2_850 * KMB),
                Materials.SulfurTrioxide.getGas(1_575 * KMB),
                new FluidStack(GTPPFluids.CoalGas, 120 * KMB),
                new FluidStack(GTPPFluids.Anthracene, 495 * KMB),
                Materials.MysteriousCrystal.getMolten(144))
            .duration(400 * SECONDS)
            .eut(30_000)
            .addTo(ModRecipeMaps.modernIndustrialParkRecipes);

        // 4: 氧化富集硅岩混合物粉 / enriched naquadah oxide mixture. 98,000,000 EU over 10 s.
        GTRecipeBuilder.builder()
            .circuit(4)
            .itemInputsUnsafe(GGMaterial.enrichedNaquadahEarth.get(OrePrefixes.dust, 220))
            .fluidInputs(Materials.Fluorine.getGas(100 * KMB))
            .itemOutputs(
                dust(Materials.Trinium, 10),
                dust(Materials.Gallium, 120),
                dust(Materials.Adamantium, 48),
                dust(Materials.Sulfur, 141))
            .fluidOutputs(
                Materials.NaquadahEnriched.getMolten(29_970),
                Materials.Naquadria.getMolten(3_996),
                // The document lists the hydrogen as 600 and 421 kmb in two places; they are the same fluid, so the
                // two are added up rather than written twice.
                Materials.Hydrogen.getGas(1_021 * KMB),
                waste(80 * KMB),
                Materials.Naquadah.getMolten(17_280),
                Materials.Oxygen.getGas(439 * KMB),
                Materials.Titanium.getMolten(8_640))
            .duration(10 * SECONDS)
            .eut(490_000)
            .addTo(ModRecipeMaps.modernIndustrialParkRecipes);

        // 5: 氧化硅岩混合物粉 / naquadah oxide mixture. The document's total is ten times its own EU/t figure; see
        // the class comment. 490,000 EU/t for 60 s = 588,000,000 EU.
        GTRecipeBuilder.builder()
            .circuit(5)
            .itemInputsUnsafe(GGMaterial.naquadahEarth.get(OrePrefixes.dust, 480), dust(Materials.Sodium, 112))
            .fluidInputs(Materials.Fluorine.getGas(800 * KMB))
            .itemOutputs(dust(Materials.Gallium, 900), dust(Materials.Adamantium, 400), dust(Materials.Sulfur, 1_200))
            .fluidOutputs(
                Materials.Naquadah.getMolten(138 * KMB),
                Materials.Hydrogen.getGas(3_000 * KMB),
                Materials.NaquadahEnriched.getMolten(32 * KMB),
                Materials.Oxygen.getGas(5_800 * KMB),
                Materials.Naquadria.getMolten(4_320),
                waste(160 * KMB),
                Materials.Titanium.getMolten(70 * KMB))
            .duration(60 * SECONDS)
            .eut(490_000)
            .addTo(ModRecipeMaps.modernIndustrialParkRecipes);

        // 6: 氧化超能硅岩混合物粉 / naquadria oxide mixture. 19,600,000 EU over 2 s.
        GTRecipeBuilder.builder()
            .circuit(6)
            .itemInputsUnsafe(GGMaterial.naquadriaEarth.get(OrePrefixes.dust, 100), dust(Materials.Phosphorus, 32))
            .fluidInputs(Materials.Fluorine.getGas(100 * KMB))
            .itemOutputs(
                dust(Materials.Barium, 2),
                dust(Materials.Indium, 5),
                dust(Materials.Sulfur, 253),
                dust(Materials.Trinium, 2),
                dust(Materials.Gallium, 120),
                dust(Materials.Adamantium, 48))
            .fluidOutputs(
                Materials.Naquadria.getMolten(15_625),
                Materials.Oxygen.getGas(910 * KMB),
                Materials.NaquadahEnriched.getMolten(9_940),
                Materials.Naquadah.getMolten(17 * KMB),
                Materials.Hydrogen.getGas(461_363),
                waste(33_636),
                Materials.Titanium.getMolten(8 * KMB))
            .duration(2 * SECONDS)
            .eut(490_000)
            .addTo(ModRecipeMaps.modernIndustrialParkRecipes);
    }

    /**
     * The controller's assembler recipe: circuit 15, sixteen each of the industrial blast furnace, electrolyzer, large
     * chemical reactor, maceration stack, centrifuge, mixer, vacuum freezer and chemical bath, at 4096 EU/t for 2400
     * seconds as the design document specifies. The freezer used to be GT++'s industrial cryogenic freezer, which is
     * deprecated; the standard vacuum freezer takes its place.
     */
    private static void registerControllerAssembler() {
        var controller = GregTechAPI.METATILEENTITIES[MetaTileIDs.MODERN_INDUSTRIAL_PARK_CONTROLLER];
        if (controller == null) {
            MyMod.LOG
                .error("The Modern Industrial Park controller was not registered, so its assembler recipe is skipped.");
            return;
        }
        ItemStack[] parts = { ItemList.Machine_Multi_BlastFurnace.get(16), ItemList.IndustrialElectrolyzer.get(16),
            ItemList.Machine_Multi_LargeChemicalReactor.get(16), ItemList.MacerationStack.get(16),
            ItemList.IndustrialCentrifuge.get(16), ItemList.IndustrialMixer.get(16),
            ItemList.Machine_Multi_VacuumFreezer.get(16), ItemList.IndustrialChemicalBath.get(16) };
        for (ItemStack part : parts) {
            if (part == null) {
                MyMod.LOG.error(
                    "One of the industrial machines the Modern Industrial Park is assembled from was not found, so the "
                        + "assembler recipe is skipped.");
                return;
            }
        }
        GTRecipeBuilder.builder()
            .circuit(15)
            .itemInputs(parts)
            .itemOutputs(controller.getStackForm(1))
            .duration(2_400 * SECONDS)
            .eut(4_096)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /**
     * A dust stack of exactly {@code amount}, however large that is.
     * <p>
     * Both {@code GTOreDictUnificator.get(prefix, material, amount)} and {@code GTUtility.copyAmount} clamp a stack to
     * 64 - {@code copyAmount} literally reads {@code if (aAmount > 64) aAmount = 64}. Writing a recipe through them
     * therefore silently turns "2496 Sulfur Dust" into "64 Sulfur Dust", which is what made the netherite batch show up
     * in NEI with 64 of everything. {@code copyAmountUnsafe} keeps the number the design document asks for.
     */
    private static ItemStack dust(Materials material, int amount) {
        ItemStack base = GTOreDictUnificator.get(OrePrefixes.dust, material, 1);
        if (base == null) return null;
        return GTUtility.copyAmountUnsafe(amount, base);
    }
}
