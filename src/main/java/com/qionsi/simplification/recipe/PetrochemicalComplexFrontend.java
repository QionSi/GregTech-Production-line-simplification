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
 * The generic GregTech layout puts every fluid on a single row at {@code y = 62}: the inputs right-aligned at {@code
 * x = 16} and the outputs starting at {@code x = 106}. That works for the two or three fluids a normal machine takes,
 * but this machine has up to five fluid inputs and seven fluid outputs, and a row of seven starts at 106 and runs to
 * 232 - straight off the 170 pixel wide page, over the GregTech logo and over the slot under the progress bar.
 * <p>
 * This frontend keeps the familiar bands and wraps the fluids inside them:
 *
 * <pre>
 *   item inputs   3 per row at x = 16,  y = 24     item outputs   3 per row at x = 106, y = 24
 *   fluid inputs  5 per row at x = 16,  y = 62     fluid outputs  3 per row at x = 106, y = 62
 * </pre>
 *
 * Every slot then sits inside the page: the widest fluid row ends at {@code x = 160} and the longest, three rows, ends
 * at {@code y = 116}. {@link #backgroundHeight()} is the page height that fits exactly that, and no two slots share a
 * position.
 */
public class PetrochemicalComplexFrontend extends RecipeMapFrontend {

    /** Left edge of the input band. */
    private static final int INPUT_X = 16;
    /** Left edge of the output band. */
    private static final int OUTPUT_X = 106;
    /** Top edge of the item band. */
    private static final int ITEM_Y = 24;
    /** Top edge of the fluid band, clear of the two item rows (24, 42) the recipe map allows for. */
    private static final int FLUID_Y = 62;
    /** Distance between two slots; the slots themselves are 18x18. */
    private static final int SLOT = 18;
    /** Fluid inputs per row. Five of them end at x = 106, exactly where the output band starts. */
    private static final int FLUID_INPUTS_PER_ROW = 5;
    /**
     * Fluid outputs per row. Three of them end at x = 160, inside the page and clear of the GregTech logo, which
     * starts at x = 152 but sits one slot lower.
     */
    private static final int FLUID_OUTPUTS_PER_ROW = 3;

    /**
     * Rows the fluid band needs for the {@code 9} fluid inputs and {@code 9} fluid outputs the recipe map allows:
     * {@code ceil(9 / 5) = 2} and {@code ceil(9 / 3) = 3}, so three.
     */
    private static final int FLUID_ROWS = 3;

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
        return rows(INPUT_X, FLUID_Y, fluidInputCount, FLUID_INPUTS_PER_ROW);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return rows(OUTPUT_X, FLUID_Y, fluidOutputCount, FLUID_OUTPUTS_PER_ROW);
    }

    /**
     * Fluids are laid out as full rows rather than as one long line, so a recipe with seven fluids stays inside the
     * window instead of running past the page.
     */
    private static List<Pos2d> rows(int x, int y, int count, int perRow) {
        List<Pos2d> positions = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            positions.add(new Pos2d(x + (i % perRow) * SLOT, y + (i / perRow) * SLOT));
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

    /** Height the recipe page needs: the item band, the fluid band and a little breathing room below it. */
    public static int backgroundHeight() {
        return FLUID_Y + FLUID_ROWS * SLOT + 3;
    }
}
