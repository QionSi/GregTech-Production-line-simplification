package com.qionsi.simplification.recipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MetaTileIDs;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.item.ModItems;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import tectech.recipe.TTRecipeAdder;
import tectech.recipe.TecTechRecipeMaps;

/**
 * 阿如的倒影 / Reflection of Aru 的控制器合成配方。
 * <p>
 * The design document asks for an assembly line recipe gated behind the Research Station:
 * <ul>
 * <li>4 quantum computers, 16 crystal mainframes, 32 quantum mainframes, 64 nano circuits,</li>
 * <li>128,000 mB of molten soldering alloy,</li>
 * <li>131,072 EU/t for 2400 seconds,</li>
 * <li>research item 初生白枝, at 524,288 EU/t, no less than 200,000 computation per second, 2,400,000,000
 * computation in total.</li>
 * </ul>
 *
 * <h2>Which circuit is the "nano mainframe"</h2>
 *
 * GT ships crystal and quantum circuits in all three sizes (processor / computer / mainframe) but nano only in two -
 * there is {@code Circuit_Nanoprocessor} and {@code Circuit_Nanocomputer}, and no {@code Circuit_Nanomainframe}. The
 * nano tier's top item is therefore the computer, and that is what the 64 are.
 */
public final class AruReflectionRecipe {

    /** 131,072 EU/t, as the design document states. */
    private static final int RECIPE_EUT = 131_072;

    /** 2400 seconds. */
    private static final int RECIPE_DURATION = 2400 * 20;

    /** The research gate: 524,288 EU/t. */
    private static final int RESEARCH_VOLTAGE = 524_288;

    /** One amp of research power. The document does not name an amperage. */
    private static final int RESEARCH_AMPERAGE = 1;

    /** Minimum computation per second while scanning. */
    private static final int COMPUTATION_PER_SECOND = 200_000;

    /** How long the scan lasts: 1200 seconds, i.e. 20 minutes. This is what the total computation is derived from. */
    private static final int RESEARCH_SECONDS = 1_200;

    /**
     * Total computation the research consumes: {@code COMPUTATION_PER_SECOND * RESEARCH_SECONDS} = 240,000,000.
     * <p>
     * The design document writes 2,400,000,000, which at 200,000 per second would be 12,000 seconds; 1200 seconds was
     * confirmed as the intended figure instead, and 1200 * 200,000 also happens to fit in the {@code int} that
     * TecTech's API demands, so no clamping is needed.
     */
    private static final int TOTAL_COMPUTATION = COMPUTATION_PER_SECOND * RESEARCH_SECONDS;

    /** 128,000 mB of molten soldering alloy. */
    private static final int SOLDER_AMOUNT = 128_000;

    private static boolean registered;

    private AruReflectionRecipe() {}

    /** Registers the research-station / assembly line recipe. Safe to call twice. */
    public static void init() {
        if (registered) return;
        registered = true;

        ItemStack researchItem = ModItems.nascentWhiteBranch == null ? null
            : new ItemStack(ModItems.nascentWhiteBranch, 1);
        ItemStack controller = GregTechAPI.METATILEENTITIES[MetaTileIDs.ARU_REFLECTION_CONTROLLER] == null ? null
            : GregTechAPI.METATILEENTITIES[MetaTileIDs.ARU_REFLECTION_CONTROLLER].getStackForm(1);
        ItemStack quantumComputer = GregTechAPI.METATILEENTITIES[15311] == null ? null
            : GregTechAPI.METATILEENTITIES[15311].getStackForm(4);

        if (researchItem == null || controller == null || quantumComputer == null) {
            MyMod.LOG.error(
                "Aru's Reflection: the research item, the controller or the Quantum Computer is missing, so the "
                    + "assembly line recipe is skipped (research item {}, controller {}, quantum computer {})",
                researchItem,
                controller,
                quantumComputer);
            return;
        }

        ItemStack crystalMainframe = ItemList.Circuit_Crystalmainframe.get(16);
        ItemStack quantumMainframe = ItemList.Circuit_Quantummainframe.get(32);
        ItemStack nanoTop = ItemList.Circuit_Nanocomputer.get(64);
        if (crystalMainframe == null || quantumMainframe == null || nanoTop == null) {
            MyMod.LOG.error("Aru's Reflection: one of the circuit tiers does not resolve; the recipe is skipped");
            return;
        }

        int before = TecTechRecipeMaps.researchableALRecipeList.size();
        boolean added = TTRecipeAdder.addResearchableAssemblylineRecipe(
            researchItem,
            TOTAL_COMPUTATION,
            COMPUTATION_PER_SECOND,
            RESEARCH_VOLTAGE,
            RESEARCH_AMPERAGE,
            new ItemStack[] { quantumComputer, crystalMainframe, quantumMainframe, nanoTop },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(SOLDER_AMOUNT) },
            controller,
            RECIPE_DURATION,
            RECIPE_EUT);

        if (!added) {
            MyMod.LOG.error(
                "TecTech refused Aru's Reflection's assembly line recipe ({} EU/t, {} ticks, {} mB solder)",
                RECIPE_EUT,
                RECIPE_DURATION,
                SOLDER_AMOUNT);
            return;
        }

        int after = TecTechRecipeMaps.researchableALRecipeList.size();
        if (after != before + 1) {
            MyMod.LOG.error(
                "Aru's Reflection: the recipe count went {} -> {} instead of +1; something else registered at the same "
                    + "time",
                before,
                after);
        } else {
            MyMod.LOG.info(
                "Registered Aru's Reflection's assembly line recipe ({} EU/t for {} ticks = {} s); research item 初生白枝, "
                    + "{} computation/s for {} s = {} in total",
                RECIPE_EUT,
                RECIPE_DURATION,
                RECIPE_DURATION / 20,
                COMPUTATION_PER_SECOND,
                RESEARCH_SECONDS,
                TOTAL_COMPUTATION);
        }
    }
}
