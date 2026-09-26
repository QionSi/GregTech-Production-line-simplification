package com.qionsi.simplification.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A multiblock structure, written as text so it can live in an editable file.
 * <p>
 * See {@link PetrochemicalComplexStructure} for the file itself.
 */
public final class StructureBlueprint {

    /** The one symbol that marks where the controller goes. */
    public static final char CONTROLLER_SYMBOL = '~';

    /**
     * Accepted as a stand-in for "air" so a shape can be written with padding that is actually visible in a text
     * editor. It is converted to a space while parsing, because StructureLib rejects characters it has no element
     * registered for - which is what a stray {@code .} used to do.
     */
    public static final char EMPTY_ALIAS = '.';

    /**
     * One stage of the machine: rows along the depth axis, each row read left to right. The first row is the front
     * (the side the controller faces). Stage 0 is the bottom layer.
     */
    public final List<List<String>> stages;

    /**
     * Where the controller sits inside the shape, in the coordinates StructureLib wants: A is the column, B counts
     * stages down from the top and C counts rows back from the player.
     * <p>
     * These are <strong>derived from the {@code ~} marker</strong>, never taken from the file. Having one description
     * of the controller - the marker - means a stale or out of range offset key cannot make the machine unbuildable;
     * the keys are still read so a mismatch can be reported.
     */
    private final int offsetA;
    private final int offsetB;
    private final int offsetC;

    public StructureBlueprint(List<List<String>> stages, int offsetA, int offsetB, int offsetC) {
        List<List<String>> copy = new ArrayList<>(stages.size());
        for (List<String> stage : stages) copy.add(Collections.unmodifiableList(new ArrayList<>(stage)));
        this.stages = Collections.unmodifiableList(copy);

        // The marker is the single source of truth; the arguments are only a fallback for a shape without one.
        int[] marker = findControllerIn(this.stages);
        if (marker != null) {
            this.offsetA = marker[0];
            this.offsetB = marker[1];
            this.offsetC = marker[2];
        } else {
            this.offsetA = offsetA;
            this.offsetB = offsetB;
            this.offsetC = offsetC;
        }
    }

    /** Column, down-from-top stage and back-from-player row of the {@code ~} marker, or null when absent. */
    private static int[] findControllerIn(List<List<String>> stages) {
        for (int stage = 0; stage < stages.size(); stage++) {
            List<String> rows = stages.get(stage);
            for (int row = 0; row < rows.size(); row++) {
                String text = rows.get(row);
                for (int column = 0; column < text.length(); column++) {
                    if (text.charAt(column) == CONTROLLER_SYMBOL) {
                        return new int[] { column, stages.size() - 1 - stage, row };
                    }
                }
            }
        }
        return null;
    }

    /** Number of stages, i.e. the height of the machine. */
    public int height() {
        return stages.size();
    }

    /** Number of rows per stage, i.e. the depth of the machine. */
    public int depth() {
        return stages.isEmpty() ? 0
            : stages.get(0)
                .size();
    }

    /** Number of symbols per row, i.e. the width of the machine. */
    public int width() {
        if (stages.isEmpty() || stages.get(0)
            .isEmpty()) return 0;
        return stages.get(0)
            .get(0)
            .length();
    }

    /**
     * The shape in the world orientation the file uses: {@code [stage][row][column]}, where stage 0 is the
     * <strong>bottom</strong> layer and row 0 is the <strong>front</strong> row.
     * <p>
     * {@link #toShapeArray()} has to flip both of these, because StructureLib measures the world the other way round.
     */
    public String[][] toWorldShape() {
        String[][] shape = new String[stages.size()][];
        for (int stage = 0; stage < stages.size(); stage++) {
            shape[stage] = stages.get(stage)
                .toArray(new String[0]);
        }
        return shape;
    }

    /**
     * Converts to the array StructureLib wants.
     * <p>
     * Two things differ from the file's own numbering and both matter:
     * <ul>
     * <li>the stage list has to be reversed, because the first array index counts <em>down</em> from the top;</li>
     * <li>rows must <em>not</em> be reversed. The file lists rows front to back and the array wants them back to
     * front, and those two cancel out: array row 0 is the back row, which is the file's last row, so the file's row
     * order already lands correctly.</li>
     * </ul>
     * Getting the row direction wrong mirrors the machine front to back, which puts the controller on the far side
     * instead of the face the player stands at.
     */
    public String[][] toShapeArray() {
        int height = stages.size();
        String[][] shape = new String[height][];
        for (int stage = 0; stage < height; stage++) {
            shape[stage] = stages.get(height - 1 - stage)
                .toArray(new String[0]);
        }
        return shape;
    }

    /**
     * The {@code (A, B, C)} position the controller's {@code ~} occupies inside the array handed to StructureLib.
     */
    public int[] controllerArrayPosition() {
        int[] found = findControllerIn(stages);
        return found;
    }

    /**
     * Validates the shape. Returns a list of problems, empty when the blueprint is usable.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (stages.isEmpty()) {
            problems.add("the structure has no stages");
            return problems;
        }
        int depth = depth();
        int width = width();
        if (depth == 0) problems.add("the structure has no rows");
        if (width == 0) problems.add("the structure has no columns");
        for (int stage = 0; stage < stages.size(); stage++) {
            List<String> rows = stages.get(stage);
            if (rows.size() != depth) {
                problems.add("stage " + (stage + 1) + " has " + rows.size() + " rows but the first stage has " + depth);
            }
            for (int row = 0; row < rows.size(); row++) {
                if (rows.get(row)
                    .length() != width) {
                    problems.add(
                        "stage " + (stage + 1)
                            + " row "
                            + (row + 1)
                            + " is "
                            + rows.get(row)
                                .length()
                            + " columns wide but should be "
                            + width);
                }
            }
        }
        // The offsets were derived from the marker in the constructor, so they are always inside the shape by
        // construction. Only the marker itself can be missing or duplicated, which is what gets checked here.
        problems.addAll(checkControllerMarker());
        return problems;
    }

    /**
     * Reports a missing or duplicated {@code ~} marker. A disagreement between the marker and the offset keys in the
     * file is <em>not</em> an error any more - the marker wins and the parser warns - because two descriptions of the
     * same thing used to be able to make the machine unbuildable.
     */
    private List<String> checkControllerMarker() {
        List<String> problems = new ArrayList<>();
        int count = 0;
        for (List<String> rows : stages) {
            for (String text : rows) {
                for (int column = 0; column < text.length(); column++) {
                    if (text.charAt(column) == CONTROLLER_SYMBOL) count++;
                }
            }
        }
        if (count == 0) {
            problems.add("no controller marker `" + CONTROLLER_SYMBOL + "` in the shape");
        } else if (count > 1) {
            problems.add("the shape has " + count + " controller markers but there can only be one");
        }
        return problems;
    }

    /**
     * The offsets as derived from the marker, formatted for the log, so a stale key in the file can be spotted.
     */
    public String offsetSummary() {
        return offsetA + "/" + offsetB + "/" + offsetC;
    }

    public int offsetA() {
        return offsetA;
    }

    public int offsetB() {
        return offsetB;
    }

    public int offsetC() {
        return offsetC;
    }

    /**
     * Prints the blueprint the same way it is written in the file, with stage and row headings. Used by the
     * {@code /simplification structure} command so the shape can be checked without opening the file.
     */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("controller offset: column ")
            .append(offsetA)
            .append(", stage ")
            .append(offsetB)
            .append(", row ")
            .append(offsetC)
            .append('\n');
        text.append("size: ")
            .append(width())
            .append(" wide x ")
            .append(height())
            .append(" tall x ")
            .append(depth())
            .append(" deep (rows are listed front to back)\n");
        for (int stage = 0; stage < stages.size(); stage++) {
            text.append("stage ")
                .append(stage + 1)
                .append(":\n");
            for (String row : stages.get(stage)) {
                text.append("  ")
                    .append(row.replace(' ', '.'))
                    .append('\n');
            }
        }
        return text.toString();
    }

    /**
     * A single block with the controller in it. Used only when the real structure cannot be built, so that the machine
     * still registers and the game starts instead of dying on a class initialiser.
     */
    public static StructureBlueprint placeholder() {
        List<List<String>> stages = new ArrayList<>(1);
        List<String> rows = new ArrayList<>(1);
        rows.add(String.valueOf(CONTROLLER_SYMBOL));
        stages.add(rows);
        return new StructureBlueprint(stages, 0, 0, 0);
    }

    /**
     * Parses the {@code structure} section of the file: {@code offsetA}, {@code offsetB}, {@code offsetC} and
     * {@code stage1}, {@code stage2}, ... Each stage holds one string per row, separated by {@code |}.
     */
    public static StructureBlueprint parse(List<String> errors, String source, int offsetA, int offsetB, int offsetC,
        List<String> stageValues) {
        List<List<String>> stages = new ArrayList<>();
        for (int i = 0; i < stageValues.size(); i++) {
            String value = stageValues.get(i);
            List<String> rows = new ArrayList<>();
            for (String row : value.split("\\|")) {
                // The empty-alias is turned into a real space first, then trailing space is dropped. A row therefore
                // ends after its last block, so a shape may be written without any padding.
                String trimmed = row.replace(EMPTY_ALIAS, ' ')
                    .trim();
                if (!trimmed.isEmpty()) rows.add(trimmed);
            }
            if (rows.isEmpty()) {
                errors.add(source + ": stage" + (i + 1) + " is empty; write one string per row separated by `|`");
                continue;
            }
            stages.add(rows);
        }
        if (stages.isEmpty()) {
            errors.add(source + ": no stages defined; add stage1, stage2, ... under [structure]");
        }

        // Trimmed rows end up with different lengths whenever a row's right hand side is empty. Pad them all to the
        // widest row instead of rejecting the shape: the missing cells can only mean "empty" and padding keeps
        // StructureLib happy, which requires a rectangular shape.
        padToWidestRow(stages);

        StructureBlueprint blueprint = new StructureBlueprint(stages, offsetA, offsetB, offsetC);
        for (String problem : blueprint.validate()) {
            errors.add(source + ": " + problem);
        }
        return blueprint;
    }

    private static void padToWidestRow(List<List<String>> stages) {
        int widest = 0;
        for (List<String> rows : stages) {
            for (String row : rows) widest = Math.max(widest, row.length());
        }
        if (widest == 0) return;
        for (List<String> rows : stages) {
            for (int i = 0; i < rows.size(); i++) {
                String row = rows.get(i);
                if (row.length() < widest) {
                    rows.set(i, padRight(row, widest));
                }
            }
        }
    }

    private static String padRight(String text, int width) {
        StringBuilder padded = new StringBuilder(width);
        padded.append(text);
        while (padded.length() < width) padded.append(' ');
        return padded.toString();
    }
}
