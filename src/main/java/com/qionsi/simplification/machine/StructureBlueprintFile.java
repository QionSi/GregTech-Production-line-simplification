package com.qionsi.simplification.machine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.qionsi.simplification.MyMod;

/**
 * Reads a multiblock blueprint written as an {@code .mb} text file, the way GT-Not-Leisure writes them.
 * <p>
 * A blueprint is plain text, one line per level of the machine, and every line is one cell per depth position separated
 * by commas. Inside a cell the characters run left to right, so a cell is exactly as wide as the machine:
 *
 * <pre>
 * line n                level n, counting from the top
 * cell k of a line      the depth position k, counting away from the player
 * character i of a cell column i, counting from the left as the player sees it
 * </pre>
 *
 * <p>
 * A character is either a symbol the machine binds with {@code addElement}, {@code ~} for the controller, or one of the
 * characters StructureLib understands on its own: a space is a position the check does not look at, {@code -} has to be
 * air and {@code +} has to be something else. The shape is returned as {@code [line][cell]}; StructureLib wants the
 * other order, so the caller hands it through {@code StructureUtility.transpose}.
 * <p>
 * Nothing here is allowed to throw into a class initialiser: a blueprint that cannot be read is reported and the game
 * keeps running with a one block machine, because a structure problem must never stop the game from starting.
 */
public final class StructureBlueprintFile {

    /** Where the blueprints sit inside the jar: {@code assets/<modid>/multiblock/<name>.mb}. */
    private static final String BASE_PATH = "/assets/";

    /** Blueprints already read, so building a definition does not touch the jar again. */
    private static final ConcurrentHashMap<String, String[][]> CACHE = new ConcurrentHashMap<>();

    private StructureBlueprintFile() {}

    /**
     * Reads the blueprint {@code structureName}, written as {@code <modid>:multiblock/<name>}.
     *
     * @return the shape as {@code [line][cell]}, or a one block shape when the blueprint cannot be read
     */
    public static String[][] read(String structureName) {
        return CACHE.computeIfAbsent(structureName, name -> {
            try {
                String[][] shape = parse(readLines(name));
                int[] controller = controllerIn(shape);
                if (controller == null) {
                    MyMod.LOG.info(
                        "Structure blueprint {}: {} wide x {} tall x {} deep, no '~' controller marker",
                        name,
                        width(shape),
                        height(shape),
                        depth(shape));
                } else {
                    MyMod.LOG.info(
                        "Structure blueprint {}: {} wide x {} tall x {} deep, controller '~' at X={} Y={} Z={}",
                        name,
                        width(shape),
                        height(shape),
                        depth(shape),
                        controller[0],
                        controller[1],
                        controller[2]);
                }
                return shape;
            } catch (Throwable t) {
                MyMod.LOG.error(
                    "Could not read the structure blueprint {}; the machine falls back to a single block so the game "
                        + "keeps running.",
                    name,
                    t);
                return fallback();
            }
        });
    }

    /**
     * Checks that the {@code ~} of a blueprint sits where the machine's own offset constants say it does, and says so
     * in the log. A blueprint and a machine that disagree about it would silently move the whole shape relative to the
     * controller, which is hard to read off the game, so it is worth a warning.
     */
    public static void verifyControllerPosition(String structureName, String[][] shape, int horizontal, int vertical,
        int depth) {
        int[] controller = controllerIn(shape);
        if (controller == null) {
            MyMod.LOG.warn(
                "Structure blueprint {} has no '~' controller marker, so the machine's offsets {}/{}/{} cannot be "
                    + "checked against it.",
                structureName,
                horizontal,
                vertical,
                depth);
            return;
        }
        if (controller[0] != horizontal || controller[1] != vertical || controller[2] != depth) {
            MyMod.LOG.warn(
                "Structure blueprint {} puts the controller at X={} Y={} Z={}, but the machine uses the offsets "
                    + "{}/{}/{}. One of the two is wrong.",
                structureName,
                controller[0],
                controller[1],
                controller[2],
                horizontal,
                vertical,
                depth);
            return;
        }
        MyMod.LOG.info(
            "Structure blueprint {}: controller position matches the machine offsets A/B/C={}/{}/{}",
            structureName,
            horizontal,
            vertical,
            depth);
    }

    /** How often {@code symbol} appears in the shape, i.e. how many blocks that position asks for. */
    public static int count(String[][] shape, char symbol) {
        int count = 0;
        for (String[] line : shape) {
            for (String cell : line) {
                for (int i = 0; i < cell.length(); i++) {
                    if (cell.charAt(i) == symbol) count++;
                }
            }
        }
        return count;
    }

    /** Number of characters per cell, i.e. how wide the machine is. */
    public static int width(String[][] shape) {
        int widest = 0;
        for (String[] line : shape) {
            for (String cell : line) widest = Math.max(widest, cell.length());
        }
        return widest;
    }

    /** Number of lines, i.e. how tall the machine is. */
    public static int height(String[][] shape) {
        return shape.length;
    }

    /** Number of cells per line, i.e. how deep the machine is. */
    public static int depth(String[][] shape) {
        return shape.length == 0 ? 0 : shape[0].length;
    }

    /**
     * Where the {@code ~} marker sits, as {@code {X, Y, Z}}, or {@code null} when the blueprint has no marker.
     */
    public static int[] controllerIn(String[][] shape) {
        for (int y = 0; y < shape.length; y++) {
            for (int z = 0; z < shape[y].length; z++) {
                String cell = shape[y][z];
                for (int x = 0; x < cell.length(); x++) {
                    if (cell.charAt(x) == '~') return new int[] { x, y, z };
                }
            }
        }
        return null;
    }

    /** The shape of a machine whose blueprint could not be read: the controller and nothing else. */
    private static String[][] fallback() {
        return new String[][] { { "~" } };
    }

    /** Reads the lines of the blueprint out of the jar. */
    private static List<String> readLines(String structureName) throws IOException {
        String path = BASE_PATH + structureName.replace(':', '/') + ".mb";
        InputStream stream = StructureBlueprintFile.class.getResourceAsStream(path);
        if (stream == null) throw new IOException("there is no blueprint at " + path);
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        // A trailing newline is normal; a line that is nothing but whitespace never is.
        while (!lines.isEmpty() && lines.get(lines.size() - 1)
            .trim()
            .isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) throw new IOException("the blueprint " + path + " is empty");
        return lines;
    }

    /**
     * Turns the lines of a blueprint into the shape. Every line has to hold the same number of cells; cells shorter
     * than
     * the widest one - which is what a hand written blueprint has, and what an editor that trims trailing whitespace
     * leaves behind - are padded with spaces, the character the structure check does not look at.
     */
    private static String[][] parse(List<String> lines) {
        int depth = -1;
        int width = 0;
        List<String[]> cellsPerLine = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String[] cells = lines.get(index)
                .split(",", -1);
            if (depth < 0) {
                depth = cells.length;
            } else if (cells.length != depth) {
                throw new IllegalArgumentException(
                    "line " + (index + 1)
                        + " of the blueprint has "
                        + cells.length
                        + " cells, but the first line has "
                        + depth);
            }
            for (String cell : cells) width = Math.max(width, cell.length());
            cellsPerLine.add(cells);
        }
        if (width == 0) throw new IllegalArgumentException("the blueprint has no cells with any characters in them");

        String[][] shape = new String[cellsPerLine.size()][depth];
        for (int y = 0; y < shape.length; y++) {
            for (int z = 0; z < depth; z++) {
                shape[y][z] = pad(cellsPerLine.get(y)[z], width);
            }
        }
        return shape;
    }

    /** Pads a cell out to the width of the machine with spaces. */
    private static String pad(String cell, int width) {
        if (cell.length() >= width) return cell;
        StringBuilder padded = new StringBuilder(width);
        padded.append(cell);
        while (padded.length() < width) padded.append(' ');
        return padded.toString();
    }
}
