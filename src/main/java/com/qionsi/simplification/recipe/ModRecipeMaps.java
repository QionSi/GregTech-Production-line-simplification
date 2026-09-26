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
     * The UI is sized for the widest recipe in the pool: 1 programming circuit plus up to 5 item outputs, up to 9
     * fluid inputs and up to 9 fluid outputs. These numbers only describe the NEI/GUI layout, they do not restrict
     * what a recipe may declare. {@link PetrochemicalComplexFrontend} decides where each slot is drawn.
     */
    public static final RecipeMap<RecipeMapBackend> petrochemicalComplexRecipes = RecipeMapBuilder
        .of("simplification.recipe.petrochemical_complex")
        .maxIO(6, 7, 9, 9)
        .minInputs(0, 1)
        .neiRecipeBackgroundSize(170, PetrochemicalComplexFrontend.backgroundHeight())
        .neiTransferRect(52, 24, 18, 54)
        .neiTransferRect(106, 24, 18, 54)
        .frontend(PetrochemicalComplexFrontend::new)
        .build();

    private ModRecipeMaps() {}
}
