package com.qionsi.simplification.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shape of a multiblock, written as a stack of depth slices.
 * <p>
 * The layout is the one StructureLib itself walks a shape in, so it can be read straight off the page:
 *
 * <pre>
 * stage 0   the depth slice nearest the player, the one the controller's front faces
 * stage 1   the slice behind it, and so on away from the player
 *
 * row 0     the top level of the machine
 * row 1     the level below it, and so on down to the bottom
 * column 0  the leftmost block as seen from the front, counting to the right
 * </pre>
 *
 * <p>
 * StructureLib wants its shape as {@code shape[depth][level][column]} and walks it in exactly that order, which is why
 * {@link #toShapeArray()} hands the stages over unchanged.
 *
 * <h2>The anchor</h2>
 *
 * {@code checkPiece} is told where inside the shape the controller sits, in StructureLib's own axes:
 *
 * <pre>
 * A  blocks to the left of the controller              = the controller's column
 * B  blocks above the controller                       = the controller's row, as row 0 is the top
 * C  blocks between the player and the controller      = the controller's stage, as stage 0 is the front
 * </pre>
 *
 * All three are derived from the {@code ~} marker so the position is written down exactly once. Getting them wrong
 * moves the whole shape relative to the controller, which is why they are not passed in by hand.
 */
public final class StructureBlueprint {

    /** The one symbol that marks where the controller goes. */
    public static final char CONTROLLER_SYMBOL = '~';

    /**
     * The depth slices, front first. Each slice holds one string per level of the machine, written from the top down.
     */
    public final List<List<String>> stages;

    /** Column of the controller marker: the number of blocks to its left. */
    private final int offsetA;

    /** Row of the controller marker: the number of levels above it, zero when it is in the top level. */
    private final int offsetB;

    /** Slice of the controller marker: the number of blocks between the player and the controller. */
    private final int offsetC;

    public StructureBlueprint(List<List<String>> stages, int offsetA, int offsetB, int offsetC) {
        List<List<String>> copy = new ArrayList<>(stages.size());
        for (List<String> stage : stages) copy.add(Collections.unmodifiableList(new ArrayList<>(stage)));
        this.stages = Collections.unmodifiableList(copy);

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

    /**
     * The {@code (column, row, stage)} the {@code ~} marker sits at - that is, {@code (A, B, C)} - or {@code null} when
     * the shape has no marker at all.
     */
    private static int[] findControllerIn(List<List<String>> stages) {
        for (int stage = 0; stage < stages.size(); stage++) {
            List<String> rows = stages.get(stage);
            for (int row = 0; row < rows.size(); row++) {
                String text = rows.get(row);
                for (int column = 0; column < text.length(); column++) {
                    if (text.charAt(column) == CONTROLLER_SYMBOL) {
                        return new int[] { column, row, stage };
                    }
                }
            }
        }
        return null;
    }

    /** Number of depth slices, i.e. how deep the machine is. */
    public int depth() {
        return stages.size();
    }

    /** Number of levels per slice, i.e. how tall the machine is. */
    public int height() {
        int tallest = 0;
        for (List<String> stage : stages) tallest = Math.max(tallest, stage.size());
        return tallest;
    }

    /** Number of symbols per row, i.e. how wide the machine is. */
    public int width() {
        int widest = 0;
        for (List<String> stage : stages) {
            for (String row : stage) widest = Math.max(widest, row.length());
        }
        return widest;
    }

    /**
     * Pads every slice out to the full width and level count. A shape is written by hand and the rows on the far side
     * are naturally empty, so they are written shorter; StructureLib needs a rectangular block of text. A padded
     * position is a space, which is a position the structure check does not look at.
     */
    public StructureBlueprint padded() {
        int width = width();
        int height = height();
        List<List<String>> padded = new ArrayList<>(stages.size());
        for (List<String> stage : stages) {
            List<String> rows = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                StringBuilder builder = new StringBuilder(row < stage.size() ? stage.get(row) : "");
                while (builder.length() < width) builder.append(' ');
                rows.add(builder.toString());
            }
            padded.add(rows);
        }
        return new StructureBlueprint(padded, offsetA, offsetB, offsetC);
    }

    /**
     * The shape as StructureLib wants it: {@code shape[depth][level][column]}, front slice first and top level first.
     * The blueprint already uses that order, so this only pads the rows out to a rectangle.
     */
    public String[][] toShapeArray() {
        return padded().stages.stream()
            .map(stage -> stage.toArray(new String[0]))
            .toArray(String[][]::new);
    }

    /**
     * Checks the shape for mistakes that would make the machine impossible to build. Returns a list of problems, empty
     * when the shape is usable. This is a development aid: it is only reported, never fatal, because a structural
     * problem must not be able to stop the game from starting.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (stages.isEmpty()) return Collections.singletonList("the structure has no stages");
        problems.addAll(checkControllerMarker());
        return problems;
    }

    /**
     * Reports a missing or duplicated {@code ~} marker. Without one there is nothing for the offsets to point at.
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
     * Prints the shape with slice and level headings, so it can be checked from a log or from a debugger without
     * opening the source.
     */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("size: ")
            .append(width())
            .append(" wide x ")
            .append(height())
            .append(" tall x ")
            .append(depth())
            .append(" deep (slice 1 is the front, level 1 the top)\n");
        text.append("controller at A/B/C ")
            .append(offsetSummary())
            .append(" (column ")
            .append(offsetA)
            .append(", ")
            .append(offsetB)
            .append(" levels above it, ")
            .append(offsetC)
            .append(" slices behind the front)\n");
        for (int stage = 0; stage < stages.size(); stage++) {
            text.append("slice ")
                .append(stage + 1)
                .append(stage == 0 ? " (front):\n" : ":\n");
            List<String> rows = stages.get(stage);
            for (int row = 0; row < rows.size(); row++) {
                text.append("  level ")
                    .append(row + 1)
                    .append(": ")
                    .append(
                        rows.get(row)
                            .replace(' ', '.'))
                    .append('\n');
            }
        }
        return text.toString();
    }

    /**
     * Builds a shape from one {@code "row | row | row"} string per depth slice, slice 0 being the front and each row
     * holding one level with the top first. This is only sugar for writing a shape in code in the same readable form a
     * text file would use.
     */
    static StructureBlueprint ofStages(int offsetA, int offsetB, int offsetC, String... stages) {
        List<List<String>> parsed = new ArrayList<>(stages.length);
        for (String stage : stages) {
            List<String> rows = new ArrayList<>();
            for (String row : stage.split("\\|")) {
                String trimmed = row.trim();
                if (!trimmed.isEmpty()) rows.add(trimmed);
            }
            parsed.add(rows);
        }
        return new StructureBlueprint(parsed, offsetA, offsetB, offsetC);
    }

    /**
     * A one block shape holding nothing but the controller marker. Used as the last resort when the real shape cannot
     * be turned into a StructureLib definition: registering the machine with a degenerate structure is far better than
     * throwing out of a class initialiser, which would stop the game from starting.
     */
    static StructureBlueprint placeholder() {
        return new StructureBlueprint(
            Collections.singletonList(Collections.singletonList(String.valueOf(CONTROLLER_SYMBOL))),
            0,
            0,
            0);
    }

    /** Blocks to the left of the controller. */
    public int offsetA() {
        return offsetA;
    }

    /** Levels above the controller. */
    public int offsetB() {
        return offsetB;
    }

    /** Blocks between the player and the controller. */
    public int offsetC() {
        return offsetC;
    }

    /** The offsets as one string, for logging. */
    public String offsetSummary() {
        return offsetA + "/" + offsetB + "/" + offsetC;
    }
}
