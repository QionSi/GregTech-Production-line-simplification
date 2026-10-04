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

    /**
     * 稀土综合处理：矿粉模式 / Rare Earth Processing Complex, dust mode.
     * <p>
     * The dust recipes work on refined dusts and take a programming circuit plus up to seven reagents, up to eighteen
     * item outputs (the rare earth recipe splits into eighteen different dusts) and a few fluids. One of them, iridium
     * dioxide, has no fluid at all, so the map requires no fluid inputs.
     */
    public static final RecipeMap<RecipeMapBackend> rareEarthDustRecipes = RecipeMapBuilder
        .of("simplification.recipe.rare_earth_dust")
        .maxIO(8, 18, 4, 3)
        .minInputs(0, 0)
        .neiTransferRect(52, 24, 18, 54)
        .neiTransferRect(106, 24, 18, 54)
        .frontend(LargeNEIFrontend::new)
        .build();

    /**
     * 稀土综合处理：矿石模式 / Rare Earth Processing Complex, ore mode.
     * <p>
     * The ore recipes take a crushed ore plus water and give four dusts. Every one of them also carries the minimum
     * heating coil it needs in {@link gregtech.api.util.GTRecipeConstants#COIL_HEAT}: that is what the machine compares
     * its coils against, and what its time bonus is measured from.
     */
    public static final RecipeMap<RecipeMapBackend> rareEarthOreRecipes = RecipeMapBuilder
        .of("simplification.recipe.rare_earth_ore")
        .maxIO(2, 6, 2, 2)
        .minInputs(0, 1)
        .neiTransferRect(52, 24, 18, 54)
        .neiTransferRect(106, 24, 18, 54)
        .frontend(LargeNEIFrontend::new)
        .build();

    /** Item inputs the 超维度催化剂制造机 page is laid out for: the widest recipe uses sixteen. */
    public static final int TRANSCENDENT_CATALYST_MAX_ITEM_INPUTS = 18;

    /** Item outputs that page is laid out for; the recipes only ever return a fluid. */
    public static final int TRANSCENDENT_CATALYST_MAX_ITEM_OUTPUTS = 2;

    /**
     * Fluid inputs that page is laid out for. The stellar catalyst is the widest recipe and takes five fluids - helium,
     * radon, nitrogen, oxygen and the concentrated primordial stellar plasma mixture - so six is one row of three more
     * than the pool needs, which is what {@link LargeNEIFrontend} lays its fluid band out in.
     */
    public static final int TRANSCENDENT_CATALYST_MAX_FLUID_INPUTS = 6;

    /** Fluid outputs that page is laid out for; every recipe returns exactly one. */
    public static final int TRANSCENDENT_CATALYST_MAX_FLUID_OUTPUTS = 2;

    /**
     * 超维度催化剂制造机配方池 / Transcendent Catalyst Maker recipe pool.
     * <p>
     * The widest recipe takes fifteen dusts, the programming circuit and five fluids - helium, radon, nitrogen, oxygen
     * and the concentrated primordial stellar plasma mixture - and gives a single fluid back. The page is therefore
     * sized for a little more than that: eighteen item inputs, six fluid inputs, two item outputs and two fluid
     * outputs.
     * <p>
     * {@link RecipeMapBuilder#maxIO} takes its arguments in the order {@code (maxItemInputs, maxItemOutputs,
     * maxFluidInputs, maxFluidOutputs)}. This call used to read {@code maxIO(18, 6, 2, 2)}, which put the six in the
     * item <em>output</em> slot and left the fluid <em>input</em> capacity at two, so NEI and the machine GUI drew room
     * for two fluids while the stellar catalyst needs five and the page was too short for the rest. The numbers only
     * describe the NEI and GUI layout and do not restrict what a recipe may declare, which is why the recipes worked
     * but their page did not.
     */
    public static final RecipeMap<RecipeMapBackend> transcendentCatalystRecipes = RecipeMapBuilder
        .of("simplification.recipe.transcendent_catalyst")
        .maxIO(
            TRANSCENDENT_CATALYST_MAX_ITEM_INPUTS,
            TRANSCENDENT_CATALYST_MAX_ITEM_OUTPUTS,
            TRANSCENDENT_CATALYST_MAX_FLUID_INPUTS,
            TRANSCENDENT_CATALYST_MAX_FLUID_OUTPUTS)
        .minInputs(0, 1)
        .neiTransferRect(52, 24, 18, 54)
        .neiTransferRect(106, 24, 18, 54)
        .frontend(LargeNEIFrontend::new)
        .build();

    private ModRecipeMaps() {}
}
