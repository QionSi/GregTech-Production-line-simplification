package com.qionsi.simplification.recipe;

import gregtech.api.enums.Materials;
import gregtech.api.objects.SubstituteFluidStack;

/**
 * The default contents of the Petrochemical Complex recipe file, and the small helpers that the file format needs.
 * <p>
 * The recipes live in a text file (see {@link RecipeConfig}) rather than in code so that
 * {@code /simplification reload} can pick up changes without restarting the game. This class is only the seed for
 * that file: it is written out when the file does not exist yet.
 */
final class ModRecipesFile {

    private ModRecipesFile() {}

    /** Any of GregTech's four oil grades, all at the same amount. */
    static SubstituteFluidStack anyOil(int amount) {
        return new SubstituteFluidStack(
            Materials.Oil.getFluid(amount),
            Materials.OilLight.getFluid(amount),
            Materials.OilHeavy.getFluid(amount),
            Materials.OilExtraHeavy.getFluid(amount));
    }

    /**
     * The recipe file written on first run. Kept in the same syntax the parser accepts, so the first thing a player
     * sees is a working, editable example.
     */
    static String defaultFile() {
        // spotless:off
        return """
            # 石油化工综合体 / Petrochemical Complex recipes.
            #
            # One [section] per recipe. Values are separated by `|`.
            #   <name> * <amount>            an item or fluid
            #   <name> * <amount> @ <chance> an item output with a drop chance, e.g. @ 33%
            #   oredict:<oreDictName> * <n>  an item taken from the ore dictionary
            #
            #   `oil` as a fluid input accepts any of GregTech's four oil grades.
            #   Other fluid names are GregTech material names: oxygen, steam, polyethylene, ...
            #   Item names are GregTech material names (meaning the dust) or an ItemList constant.
            #
            # Edit this file, then run `/simplification reload` in game. No restart needed.

            [polyethylene]
            circuit  = 1
            duration = 600
            eut      = 32
            fluidIn  = oil * 10000 | oxygen * 10000 | steam * 12000
            fluidOut = polyethylene * 1656
            itemOut  = sulfur * 1 | carbon * 1 @ 33%

            [polyvinylchloride]
            circuit  = 2
            duration = 600
            eut      = 120
            fluidIn  = oil * 10000 | chlorine * 1104 | oxygen * 10000 | steam * 12000
            fluidOut = polyvinylchloride * 1656
            itemOut  = sulfur * 1 | carbon * 1 @ 33%

            [polytetrafluoroethylene]
            circuit  = 3
            duration = 600
            eut      = 120
            fluidIn  = oil * 10000 | hydrogen * 2208 | fluorine * 2208 | oxygen * 10000 | steam * 12000
            fluidOut = polytetrafluoroethylene * 828
            itemOut  = sulfur * 1 | carbon * 1 @ 33%

            [polystyrene]
            circuit  = 4
            duration = 600
            eut      = 120
            fluidIn  = oil * 10000 | hydrogen * 552 | oxygen * 10000 | steam * 12000
            fluidOut = styrene * 1656
            itemOut  = carbon * 1

            [cetane_boosted_diesel]
            circuit  = 5
            duration = 320
            eut      = 480
            fluidIn  = oil * 10000 | steam * 12000 | oxygen * 4080 | nitrogen * 960
            fluidOut = nitrofuel * 12000 | hydrogen * 5520 | water * 2040 | helium * 1440 | methane * 10080 | ethane * 144 | propene * 144
            itemOut  = sulfur * 1 | carbon * 12

            [diesel]
            circuit  = 6
            duration = 720
            eut      = 120
            fluidIn  = oil * 10000 | steam * 12000
            fluidOut = diesel * 12000 | methane * 10080 | propene * 144 | helium * 1000
            itemOut  = sulfur * 2 | carbon * 3
            """;
        // spotless:on
    }
}
