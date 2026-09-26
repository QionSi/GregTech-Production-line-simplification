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

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;

/**
 * The structure of the 石油化工综合体 / Petrochemical Complex.
 * <p>
 * The shape lives in code, so while debugging an edit to a method body can be swapped into the running game with the
 * IDE's HotSwap instead of restarting it. {@link #defaultBlueprint()} is the single definition and {@link #build} turns
 * it into the StructureLib form.
 *
 * <h2>Reading the shape</h2>
 *
 * One string per depth slice, front slice first, and inside a slice one line per level, the top level first. The
 * symbols are:
 * <ul>
 * <li>{@code B} Bronze Plated Bricks: takes item buses and fluid input and output hatches</li>
 * <li>{@code S} Solid Steel Machine Casing of the bottom level: takes energy, maintenance and fluid input hatches</li>
 * <li>{@code O} Solid Steel Machine Casing of every level above: takes fluid output hatches</li>
 * <li>{@code M} the muffler slot, in the middle of the top level</li>
 * <li>{@code ~} the controller block</li>
 * <li>a space, a position the structure check does not look at</li>
 * </ul>
 * Slices and rows may be written at different lengths; the shorter ones are padded with spaces. See
 * {@link StructureBlueprint} for the exact axis order, which is the one StructureLib walks.
 */
public final class PetrochemicalComplexStructure {

    /**
     * The only symbols this class registers with StructureLib. Anything else found in a shape is replaced by a space
     * before the definition is built: StructureLib throws {@code Missing Structure Element bindings} for an unknown
     * character, and an exception from a structure check is not something the game handles well.
     */
    private static final String KNOWN_SYMBOLS = "BSOM~ ";

    private PetrochemicalComplexStructure() {}

    /**
     * The shape written out: one string per depth slice, the front slice first, rows separated by {@code |} with the
     * top level first. This is the only place the machine's shape is written down.
     * <p>
     * The machine is 7 wide x 5 tall x 3 deep. The three columns on the left are Solid Steel Machine Casing and carry
     * the controller; the four on the right are the Bronze Plated Bricks that hold the item buses, and they only reach
     * up three levels, so the top two levels are the narrow steel tower that holds the muffler.
     * <p>
     * Editing these strings is the whole of a structure change. The machine re-reads them on every structure check and
     * rebuilds its definition when they differ, so with the client running under a debugger a HotSwapped edit takes
     * effect on the next check, without restarting the game.
     */
    static String[] shapeText() {
        return new String[] {
            // front slice, the wall the controller sits in
            "OOO | OOO | OOOBBBB | OOOBBBB | S~SBBBB",
            // middle slice
            "OMO | O O | O OBBBB | O O   B | SSSBBBB",
            // back slice
            "OOO | OOO | OOOBBBB | OOOBBBB | SSSBBBB" };
    }

    /**
     * The shape machine is built from: {@link #shapeText()} parsed.
     * <p>
     * The three numbers are only a fallback for a shape with no {@code ~} marker; normally all three come from the
     * marker's own position.
     */
    public static StructureBlueprint defaultBlueprint() {
        return StructureBlueprint.ofStages(0, 0, 0, shapeText())
            .padded();
    }

    /**
     * Builds the StructureLib definition for a blueprint.
     * <p>
     * The element callbacks go through the public hooks on the controller, which is what keeps this class free of the
     * controller's own state.
     */
    public static IStructureDefinition<MTEPetrochemicalComplex> build(StructureBlueprint blueprint) {
        String[][] shape = sanitise(blueprint);
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

        // Bronze Plated Bricks: everything the player feeds in and pulls out with buses and hatches.
        builder.addElement(
            'B',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                .casingIndex(Casings.BronzePlatedBricks.textureId)
                .hint(2)
                .buildAndChain(
                    onElementPass(MTEPetrochemicalComplex::bumpBronzeCasings, Casings.BronzePlatedBricks.asElement())));

        // The bottom level of the steel tower: power, maintenance and the fluid inputs.
        builder.addElement(
            'S',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(Energy.or(ExoticEnergy), Maintenance, InputHatch)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(1)
                .buildAndChain(
                    onElementPass(
                        MTEPetrochemicalComplex::bumpSolidSteelCasings,
                        Casings.SolidSteelMachineCasing.asElement())));

        // The steel above it: the fluid outputs.
        builder.addElement(
            'O',
            buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(OutputHatch)
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
     * Replaces every character StructureLib has no element for with a space. A shape is written by hand, so this makes
     * an unknown symbol a cosmetic problem instead of a crash.
     */
    private static String[][] sanitise(StructureBlueprint blueprint) {
        String[][] shape = blueprint.toShapeArray();
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
}
