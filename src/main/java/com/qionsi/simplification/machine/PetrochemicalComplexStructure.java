package com.qionsi.simplification.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;

/**
 * The structure of the 石油化工综合体 / Petrochemical Complex, loaded from an editable file.
 * <p>
 * Keeping the shape in a file rather than in code means the structure can be changed and applied with
 * {@code /simplification reload} while the game is running - only {@link #build} has to stay in code, because the
 * mapping from a symbol to a block and to the hatches it accepts is behaviour, not data.
 *
 * <h2>File format</h2>
 *
 * <pre>
 * [structure]
 * offsetA = 1          # controller column, 0 = leftmost
 * offsetB = 2          # controller stage, 0 = TOPMOST, height - 1 = bottom
 * offsetC = 4          # controller row,   0 = BACK, depth - 1 = front row the player faces
 * stage1 = BBB | B~B | BBB
 * stage2 = BBB | B B | BBB
 * ...
 * </pre>
 *
 * The three offset keys must name the position of the {@code ~} marker. They are not in the file's own stage/row
 * numbering: StructureLib's first axis counts down from the top and its second counts back from the player, which is
 * also why writing stage 1 as the bottom layer works out. The parser checks the two against each other and refuses a
 * file where they disagree.
 * <p>
 * Each stage is one horizontal layer, written as one string per row separated by {@code |}. Stage 1 is the bottom
 * layer and the first row of a stage is the front row; symbols run left to right. The symbols are:
 * <ul>
 * <li>{@code B} Bronze Plated Bricks, accepts item buses and fluid input/output hatches</li>
 * <li>{@code S} Solid Steel Machine Casing, accepts energy, maintenance and fluid input hatches</li>
 * <li>{@code O} Solid Steel Machine Casing that also accepts item/fluid output hatches</li>
 * <li>{@code M} the muffler hatch slot (and plain casing if left empty)</li>
 * <li>{@code ~} the controller block</li>
 * <li>space a position that must stay empty (the machine's interior)</li>
 * </ul>
 */
public final class PetrochemicalComplexStructure {

    /** Region of the structure file that this class reads. */
    static final String SECTION = "structure";

    /** Name of the editable structure file. */
    public static final String FILE_NAME = "petrochemical_complex_structure.cfg";

    /**
     * The text of the embedded default structure file. Exposed so the shipped default can be parsed and built in a
     * plain unit test - a bad default used to crash the game at startup and only show up in a crash report.
     */
    public static String defaultFileText() {
        return defaultFile();
    }

    /**
     * The only symbols this class registers with StructureLib. Anything else found in a shape is replaced by a space
     * before the definition is built: StructureLib throws {@code Missing Structure Element bindings} for an unknown
     * character, and that exception, thrown from the machine's class initialiser, takes the whole game down.
     */
    private static final String KNOWN_SYMBOLS = "BSOM~ ";

    private PetrochemicalComplexStructure() {}

    /**
     * Builds the StructureLib definition for a blueprint.
     * <p>
     * The element callbacks go through the public {@code onXxx} hooks on the controller, which is what keeps this
     * class free of the controller's own state.
     */
    public static IStructureDefinition<MTEPetrochemicalComplex> build(StructureBlueprint blueprint) {
        String[][] shape = sanitise(blueprint.toShapeArray());
        // Belt and braces: StructureLib fails with an unhelpful "Missing Structure Element bindings" for a character it
        // has no element for, thrown from a class initialiser it takes the whole game down. sanitise() should have
        // removed every such character, so if one survived that is a bug worth naming clearly.
        String unknown = unknownSymbols(shape);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                "structure shape still contains symbols with no element bound: '" + unknown + "'");
        }

        StructureDefinition.Builder<MTEPetrochemicalComplex> builder = StructureDefinition
            .<MTEPetrochemicalComplex>builder()
            .addShape("main", shape);

        builder.addElement(
            'B',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                .casingIndex(Casings.BronzePlatedBricks.textureId)
                .hint(2)
                .buildAndChain(
                    onElementPass(MTEPetrochemicalComplex::bumpBronzeCasings, Casings.BronzePlatedBricks.asElement())));

        builder.addElement(
            'S',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(Energy.or(ExoticEnergy), Maintenance, InputHatch)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(1)
                .buildAndChain(
                    onElementPass(
                        MTEPetrochemicalComplex::bumpSolidSteelCasings,
                        Casings.SolidSteelMachineCasing.asElement())));

        builder.addElement(
            'O',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(OutputBus, OutputHatch)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(4)
                .buildAndChain(
                    onElementPass(
                        MTEPetrochemicalComplex::bumpSolidSteelCasings,
                        Casings.SolidSteelMachineCasing.asElement())));

        builder.addElement(
            'M',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(Muffler)
                .exclusive()
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(3)
                .buildAndChain(
                    onElementPass(
                        MTEPetrochemicalComplex::bumpSolidSteelCasings,
                        Casings.SolidSteelMachineCasing.asElement())));

        return builder.build();
    }

    /**
     * Replaces every character StructureLib has no element for with a space. Nothing should need this, since parsing
     * already maps the empty-alias to a space, but a structure file is edited by hand and this makes an unknown symbol
     * a cosmetic problem instead of a crash.
     */
    private static String[][] sanitise(String[][] shape) {
        String[][] clean = new String[shape.length][];
        for (int stage = 0; stage < shape.length; stage++) {
            String[] rows = shape[stage];
            clean[stage] = new String[rows.length];
            for (int row = 0; row < rows.length; row++) {
                String text = rows[row];
                StringBuilder fixed = new StringBuilder(text.length());
                for (int i = 0; i < text.length(); i++) {
                    char symbol = text.charAt(i);
                    fixed.append(KNOWN_SYMBOLS.indexOf(symbol) >= 0 ? symbol : ' ');
                }
                clean[stage][row] = fixed.toString();
            }
        }
        return clean;
    }

    /**
     * @return every character of the shape that StructureLib has no element for
     */
    static String unknownSymbols(String[][] shape) {
        StringBuilder unknown = new StringBuilder();
        for (String[] rows : shape) {
            for (String row : rows) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (KNOWN_SYMBOLS.indexOf(symbol) < 0 && unknown.indexOf(String.valueOf(symbol)) < 0) {
                        unknown.append(symbol);
                    }
                }
            }
        }
        return unknown.toString();
    }

    /** Minimum amount of each casing the blueprint asks for. */
    public static CasingCounts expectedCasings(StructureBlueprint blueprint) {
        int steel = 0;
        int bronze = 0;
        for (var stage : blueprint.stages) {
            for (String row : stage) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    switch (symbol) {
                        case 'B' -> bronze++;
                        case 'S', 'O', 'M' -> steel++;
                        default -> {
                            // space, controller and anything else contributes no casing
                        }
                    }
                }
            }
        }
        // The controller sits on a spot the shape marks with `~`, which is not a casing.
        return new CasingCounts(steel, bronze);
    }

    /** How many of each casing the shell needs. */
    public static final class CasingCounts {

        private final int solidSteel;
        private final int bronze;

        CasingCounts(int solidSteel, int bronze) {
            this.solidSteel = solidSteel;
            this.bronze = bronze;
        }

        public int solidSteel() {
            return solidSteel;
        }

        public int bronze() {
            return bronze;
        }
    }

    /**
     * Reads the structure section out of the config file, writing the default file when it is missing.
     * <p>
     * This never throws: a missing, unreadable or malformed file yields the built-in default plus a list of problems,
     * because a bad config file must not be able to stop the game from starting.
     *
     * @return the blueprint plus any problems found while reading it
     */
    public static Result load(File file) {
        List<String> errors = new ArrayList<>();
        try {
            if (!file.isFile()) {
                File parent = file.getParentFile();
                if (parent != null) {
                    // noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                Files.write(file.toPath(), defaultFile().getBytes(StandardCharsets.UTF_8));
            }
            return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8), file.getName());
        } catch (IOException | RuntimeException e) {
            errors.add("Could not read " + file + ": " + e);
            return new Result(defaultBlueprint(), errors);
        }
    }

    /** Parses an already written structure file. */
    public static Result parse(List<String> lines) {
        return parse(lines, "structure file");
    }

    private static Result parse(List<String> lines, String source) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();
        boolean inSection = false;
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                inSection = line.toLowerCase(Locale.ENGLISH)
                    .contains(SECTION);
                continue;
            }
            if (!inSection) continue;

            int equals = line.indexOf('=');
            if (equals < 0) {
                errors.add(source + " line " + lineNumber + ": expected `key = value`");
                continue;
            }
            values.put(
                line.substring(0, equals)
                    .trim()
                    .toLowerCase(Locale.ENGLISH),
                line.substring(equals + 1)
                    .trim());
        }

        List<String> stages = new ArrayList<>();
        for (int i = 1;; i++) {
            String value = values.get("stage" + i);
            if (value == null) break;
            stages.add(value);
        }
        if (stages.isEmpty()) {
            errors.add(source + ": no stage1..stageN found under [" + SECTION + "]");
            return new Result(defaultBlueprint(), errors);
        }

        StructureBlueprint blueprint = StructureBlueprint.parse(
            errors,
            source,
            // The offset keys are only a fallback: the controller position is read from the `~` marker itself, so a
            // stale or out of range value here can no longer stop the structure from loading.
            parseInt(values, "offseta", 1, errors, source),
            parseInt(values, "offsetb", 2, errors, source),
            parseInt(values, "offsetc", 4, errors, source),
            stages);

        String declaredOffsets = values.get("offseta") + "/" + values.get("offsetb") + "/" + values.get("offsetc");
        if (values.containsKey("offseta") && !declaredOffsets.equals(blueprint.offsetSummary())) {
            warnings.add(
                source + ": offsetA/offsetB/offsetC are "
                    + declaredOffsets
                    + " but the `"
                    + StructureBlueprint.CONTROLLER_SYMBOL
                    + "` marker needs "
                    + blueprint.offsetSummary()
                    + "; the marker was used. Update the keys to match.");
        }
        return new Result(blueprint, errors, warnings);
    }

    private static int parseInt(Map<String, String> values, String key, int fallback, List<String> errors,
        String source) {
        String value = values.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            errors.add(source + ": `" + key + "` must be a whole number, got `" + value + "`");
            return fallback;
        }
    }

    /**
     * The shape the machine is designed with. Never throws and never returns null: if the embedded default text were
     * ever malformed this degrades to a single block with the controller in it, which is enough for the machine to
     * register and for the game to start.
     */
    public static StructureBlueprint defaultBlueprint() {
        try {
            StructureBlueprint parsed = parse(Arrays.asList(defaultFile().split("\\R"))).blueprint();
            if (parsed != null && parsed.validate()
                .isEmpty()) {
                return parsed;
            }
        } catch (RuntimeException e) {
            // fall through to the placeholder below
        }
        return StructureBlueprint.placeholder();
    }

    /** Result of reading the structure file. */
    public static final class Result {

        private final StructureBlueprint blueprint;
        private final List<String> errors;
        private final List<String> warnings;

        Result(StructureBlueprint blueprint, List<String> errors) {
            this(blueprint, errors, new ArrayList<>());
        }

        Result(StructureBlueprint blueprint, List<String> errors, List<String> warnings) {
            this.blueprint = blueprint;
            this.errors = errors;
            this.warnings = warnings;
        }

        public StructureBlueprint blueprint() {
            return blueprint;
        }

        public List<String> errors() {
            return errors;
        }

        /** Non fatal remarks, such as an offset key that no longer matches the marker. */
        public List<String> warnings() {
            return warnings;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * The structure file written on first run.
     * <p>
     * Shape: 7 wide x 3 tall x 5 deep. The front three columns are Solid Steel Machine Casing and carry the
     * controller, the muffler and the utility hatches; the back four columns are Bronze Plated Bricks and carry the
     * item/fluid inputs and outputs.
     */
    static String defaultFile() {
        // spotless:off
        return """
            # 石油化工综合体 / Petrochemical Complex structure.
            #
            # Each stage is one horizontal layer, written as one string per row separated by `|`.
            # Rows run FRONT to BACK, symbols go LEFT to RIGHT.
            #
            #   B  Bronze Plated Bricks, accepts item buses and fluid input/output hatches
            #   S  Solid Steel Machine Casing, accepts energy, maintenance and fluid input hatches
            #   O  Solid Steel Machine Casing that also accepts item/fluid output hatches
            #   M  the muffler hatch slot (plain casing if left empty)
            #   ~  the controller block
            #   (space) a position that must stay empty
            #
            # The controller is the spot marked `~`. StructureLib measures the anchor in its own axes,
            # which run the opposite way to the stage/row numbering used above:
            #   offsetA = column, 0 = left                (same as the file)
            #   offsetB = stage counted from the TOP      (0 = topmost, height - 1 = bottom)
            #   offsetC = row counted from the BACK       (0 = back, depth - 1 = front row the player faces)
            # For this shape the bottom, front, middle column is therefore 1 / 2 / 4.
            # These keys are only a cross check: the controller position always comes from the `~` marker, so a
            # wrong value here is reported as a warning and ignored instead of stopping the structure from loading.
            #
            # Edit this file, then run `/simplification reload` in game. No restart needed.

            [structure]
            offsetA = 1
            offsetB = 2
            offsetC = 4

            stage1 = OOO | OOO | OOOBBBB | OOOBBBB | S~SBBBB
            stage2 = OMO | O O | O OBBBB | O O   B | SSSBBBB
            stage3 = OOO | OOO | OOOBBBB | OOOBBBB | SSSBBBB
            """;
        // spotless:on
    }
}
