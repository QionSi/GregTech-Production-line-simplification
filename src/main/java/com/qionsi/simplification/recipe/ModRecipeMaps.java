package com.qionsi.simplification.recipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

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
     * The UI is sized for the widest recipe in the pool: one programming circuit as the item input, up to two dust
     * by-products, up to five fluid inputs and up to seven fluid outputs. These numbers only describe the NEI and GUI
     * layout and do not restrict what a recipe may declare. {@link PetrochemicalComplexFrontend} decides where each
     * slot is drawn.
     */
    public static final RecipeMap<RecipeMapBackend> petrochemicalComplexRecipes = RecipeMapBuilder
        .of("simplification.recipe.petrochemical_complex")
        .maxIO(3, 6, 9, 9)
        .minInputs(0, 1)
        .neiRecipeBackgroundSize(170, PetrochemicalComplexFrontend.backgroundHeight())
        .neiTransferRect(52, 24, 18, 54)
        .neiTransferRect(106, 24, 18, 54)
        .frontend(PetrochemicalComplexFrontend::new)
        .build();

    private ModRecipeMaps() {}
}
