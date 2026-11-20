package com.qionsi.simplification.recipe;

import gregtech.api.recipe.RecipeMetadataKey;

/**
 * 试验模式的发电功率，用 {@code long} 存放。
 * <p>
 * {@code GTRecipe.mEUt} is an {@code int}, and the design document's payout figures run from 3.1e8 up to 9.2e14 - most
 * of them do not fit. This metadata key carries the exact {@code long} instead, and
 * {@link NaquadahFuelPowerFormatter} draws it on the NEI page. This is the same split 大型硅岩反应堆 uses: its
 * {@code NaquadahReactorMetadata} extends {@code RecipeMetadataKey<Pair<Integer, Long>>} and a companion
 * {@code INEISpecialInfoFormatter} formats the long for display.
 */
public class NaquadahFuelPowerMetadata extends RecipeMetadataKey<Long> {

    /** The single instance every recipe and formatter refers to. */
    public static final NaquadahFuelPowerMetadata INSTANCE = new NaquadahFuelPowerMetadata();

    private NaquadahFuelPowerMetadata() {
        super(Long.class, "simplification_naquadah_fuel_power");
    }

    /**
     * Nothing is drawn from here: the power line is produced by {@link NaquadahFuelPowerFormatter}, which is attached
     * to
     * the recipe map as its special-information formatter. {@code RecipeMetadataKey} still declares this abstract, so
     * it
     * has to exist.
     */
    @Override
    public void drawInfo(gregtech.nei.RecipeDisplayInfo recipeInfo, Object value) {}
}
