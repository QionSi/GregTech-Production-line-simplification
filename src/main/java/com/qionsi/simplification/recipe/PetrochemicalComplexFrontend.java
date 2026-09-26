package com.qionsi.simplification.recipe;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.modularui.api.math.Pos2d;

import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;

/**
 * Recipe viewer layout for the Petrochemical Complex.
 * <p>
 * The generic GregTech layout puts every fluid on one row at {@code y = 62} and starts the output row at {@code x =
 * 106}, which for a recipe with five fluid inputs and seven fluid outputs makes the input and output slots overlap
 * each other and run off the side of the window. This frontend instead gives items and fluids their own bands:
 *
 * <pre>
 *   item inputs   : 3x3 grid on the left       item outputs : 3x3 grid on the right
 *   fluid inputs  : rows of up to 5, left       fluid outputs: rows of up to 5, left
 * </pre>
 *
 * Nothing shares a coordinate, so the recipe reads like any other GregTech multiblock page.
 */
public class PetrochemicalComplexFrontend extends RecipeMapFrontend {

    /** Left edge of the input band. */
    private static final int INPUT_X = 16;
    /** Left edge of the output band. */
    private static final int OUTPUT_X = 106;
    /** Top edge of the item band. */
    private static final int ITEM_Y = 24;
    /** Top edge of the fluid band, clear of the three item rows (24, 42, 60). */
    private static final int FLUID_Y = 80;
    /** Distance between two slots, slots themselves are 18x18. */
    private static final int SLOT = 18;
    /** How many fluid slots fit on one row. */
    private static final int FLUIDS_PER_ROW = 5;

    public PetrochemicalComplexFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
    }

    @Override
    public List<Pos2d> getItemInputPositions(int itemInputCount) {
        return grid(INPUT_X, ITEM_Y, 3, itemInputCount, itemInputCount == 1 ? 1 : 2);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int itemOutputCount) {
        // One item output is by far the common case; keep a lone output centred over the fluid outputs.
        return grid(OUTPUT_X, ITEM_Y, 3, itemOutputCount, itemOutputCount == 1 ? 2 : 3);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int fluidInputCount) {
        return rows(INPUT_X, FLUID_Y, fluidInputCount);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return rows(OUTPUT_X, FLUID_Y, fluidOutputCount);
    }

    /**
     * Fluids are laid out as full rows rather than as one long line, so a recipe with nine fluids stays inside the
     * window instead of running past the slot under the progress bar.
     */
    private static List<Pos2d> rows(int x, int y, int count) {
        List<Pos2d> positions = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            positions.add(new Pos2d(x + (i % FLUIDS_PER_ROW) * SLOT, y + (i / FLUIDS_PER_ROW) * SLOT));
        }
        return positions;
    }

    /**
     * Item slots, laid out from {@code x, y} filling {@code columns} per row, optionally centred when fewer than a
     * full row is used.
     */
    private static List<Pos2d> grid(int x, int y, int columns, int count, int centringColumns) {
        int offset = Math.max(0, (centringColumns - Math.min(count, columns)) / 2) * SLOT;
        List<Pos2d> positions = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            positions.add(new Pos2d(x + offset + (i % columns) * SLOT, y + (i / columns) * SLOT));
        }
        return positions;
    }

    /**
     * Height the recipe page needs: three item rows plus two fluid rows, plus a little breathing room at the bottom.
     */
    public static int backgroundHeight() {
        return FLUID_Y + 2 * SLOT - 3;
    }
}
