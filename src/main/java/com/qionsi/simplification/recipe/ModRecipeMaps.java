package com.qionsi.simplification.recipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.maps.LargeNEIFrontend;

/**
 * Recipe pools added by this mod.
 * <p>
 * A {@link RecipeMap} is GregTech's "recipe type": it owns the recipes, the NEI page and the machine GUI layout. The
 * instance below is created once, when this class is first touched, which happens during
 * {@link com.qionsi.simplification.CommonProxy}'s pre-initialization - after GregTech has finished its own preload
 * phase, so every GregTech class this builder touches is already usable.
 */
public final class ModRecipeMaps {

    /**
     * 石油化工综合体配方池 / Petrochemical Complex recipe pool.
     * <p>
     * The UI is sized for the widest recipe in the pool: the three solid reagents plus the programming circuit that
     * circuits 9, 10 and 11 take, up to three dust by-products, up to nine fluid inputs and up to nine fluid outputs.
     * These numbers only describe the NEI and GUI layout and do not restrict what a recipe may declare.
     * <p>
     * The page uses GregTech's own {@link LargeNEIFrontend}, which is made for exactly this: machines with more items
     * and fluids than fit in the default single row. It puts both bands into grids three slots wide, one band under the
     * other, sizes the page from the row counts and moves the GregTech logo to a spot no slot reaches. The default
     * frontend instead lays every fluid out in one row at {@code y = 62}, so a recipe with seven fluid outputs runs
     * from x = 106 to x = 232, off the 170 pixel wide page.
     */
    public static final RecipeMap<RecipeMapBackend> petrochemicalComplexRecipes = RecipeMapBuilder
        .of("simplification.recipe.petrochemical_complex")
        .maxIO(4, 3, 9, 9)
        .minInputs(0, 1)
        .neiTransferRect(52, 24, 18, 54)
        .neiTransferRect(106, 24, 18, 54)
        .frontend(LargeNEIFrontend::new)
        .build();

    private ModRecipeMaps() {}
}
