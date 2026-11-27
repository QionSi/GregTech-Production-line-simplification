package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MyMod;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTOreDictUnificator;

/**
 * 电网升级仓 / Grid Upgrade Hatch 的配方,走研究站模式。
 *
 * <h2>为什么是这条通路</h2>
 *
 * {@code TTRecipeAdder.addResearchableAssemblylineRecipe} is the only registrar that writes into
 * {@code TecTechRecipeMaps.researchableALRecipeList}, which is the list the research station walks in its <em>research
 * station</em> mode. That is also the only place a computation requirement exists at all - a scanner-mode recipe
 * ({@code GTRecipeConstants.SCANNING} on {@code scannerFakeRecipes}) carries just a duration and a voltage. The two
 * modes never fall back to each other.
 *
 * <h2>扫描</h2>
 *
 * Point the research station at a UIV machine hull: 30,000 computation at 320,000,000 EU/t, which at 25 computation per
 * second takes 1,200 seconds. It produces the data stick.
 *
 * <h2>装配线</h2>
 *
 * The same call also registers the assembly line half, consuming that data stick plus 1 UIV wireless energy hatch, 1
 * debug maintenance hatch, 16 UIV circuit boards and 1 UIV machine hull, with 512,000 mB of 稳定重子物质, at 32,000,000
 * EU/t for 200 seconds.
 * <p>
 * The hull is the fourth item input. {@code GTRecipeConstants.AssemblyLine} enforces
 * {@code validateInputCount(4, 16)}, so a three-ingredient recipe is dropped without a word.
 */
public final class GridUpgradeHatchRecipes {

    /** Total computation the scan needs. */
    private static final int TOTAL_COMPUTATION = 30_000;

    /** Computation per second, so the scan takes exactly 30,000 / 25 = 1,200 seconds. */
    private static final int COMPUTATION_PER_SECOND = 25;

    /** EU/t the research station burns while scanning. */
    private static final int RESEARCH_EUT = 320_000_000;

    private static boolean registered;
    private static boolean deferred;

    private GridUpgradeHatchRecipes() {}

    public static void init() {
        if (registered) return;
        if (!deferred) {
            deferred = true;
            MyMod.LOG.info("The Grid Upgrade Hatch recipes will be registered after GregTech's postload.");
            GregTechAPI.sAfterGTPostload.add(GridUpgradeHatchRecipes::init);
            return;
        }
        registered = true;
        register();
    }

    private static void register() {
        var hatch = GregTechAPI.METATILEENTITIES[com.qionsi.simplification.MetaTileIDs.HATCH_GRID_UPGRADE];
        if (hatch == null) {
            MyMod.LOG.error("The Grid Upgrade Hatch is not registered, so its recipes are skipped.");
            return;
        }
        ItemStack output = hatch.getStackForm(1);
        ItemStack hull = ItemList.Hull_UIV.get(1);
        ItemStack wirelessEnergy = ItemList.Wireless_Hatch_Energy_UIV.get(1);
        ItemStack debugMaintenance = debugMaintenanceHatch();
        ItemStack circuits = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UIV, 16);
        FluidStack baryonicMatter = Materials.StableBaryonicMatter.getFluid(512_000);
        if (output == null || hull == null
            || wirelessEnergy == null
            || debugMaintenance == null
            || circuits == null
            || baryonicMatter == null) {
            MyMod.LOG.error(
                "An ingredient of the Grid Upgrade Hatch recipes is missing (output {}, UIV hull {}, UIV wireless "
                    + "energy {}, debug maintenance {}, circuits {}, baryonic matter {}), so they are skipped.",
                output,
                hull,
                wirelessEnergy,
                debugMaintenance,
                circuits,
                baryonicMatter);
            return;
        }

        boolean ok = tectech.recipe.TTRecipeAdder.addResearchableAssemblylineRecipe(
            hull,
            TOTAL_COMPUTATION,
            COMPUTATION_PER_SECOND,
            RESEARCH_EUT,
            1,
            new Object[] { wirelessEnergy, debugMaintenance, circuits, hull },
            new FluidStack[] { baryonicMatter },
            output,
            200 * SECONDS,
            32_000_000);
        if (ok) {
            MyMod.LOG.info(
                "Registered the Grid Upgrade Hatch: research {} computation at {} EU/t for {} s, assembly line "
                    + "32M EU/t for 200 s",
                TOTAL_COMPUTATION,
                RESEARCH_EUT,
                TOTAL_COMPUTATION / COMPUTATION_PER_SECOND);
        } else {
            MyMod.LOG.error("TTRecipeAdder refused the Grid Upgrade Hatch recipe; it will not appear in NEI.");
        }
    }

    /**
     * The debug maintenance hatch - TecTech's {@code MTEHatchCreativeMaintenance}, internal name
     * {@code debug.tt.maintenance}.
     * <p>
     * It has no {@code ItemList} constant, so it is found by class among the registered machines rather than by id;
     * MetaTileEntity ids differ between packs.
     */
    private static ItemStack debugMaintenanceHatch() {
        for (IMetaTileEntity mte : GregTechAPI.METATILEENTITIES) {
            if (mte instanceof tectech.thing.metaTileEntity.hatch.MTEHatchCreativeMaintenance) {
                return mte.getStackForm(1);
            }
        }
        return null;
    }
}
