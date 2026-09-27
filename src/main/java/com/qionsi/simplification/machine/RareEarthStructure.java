package com.qionsi.simplification.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import net.minecraft.init.Blocks;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings4;
import gregtech.common.blocks.BlockCasings8;
import gtPlusPlus.core.block.ModBlocks;

/**
 * The structure of the 稀土综合处理 / Rare Earth Processing Complex.
 * <p>
 * The shape is written in {@link #shapeText()}: one string per depth slice, the front slice first, and inside a slice
 * one field per level with the top level first. See {@link StructureBlueprint} for the exact axis order, which is the
 * one StructureLib walks.
 *
 * <h2>Symbols</h2>
 *
 * <ul>
 * <li>{@code A} Chemically Inert Machine Casing: takes every hatch this machine supports</li>
 * <li>{@code B} any GregTech heating coil, which is what sets the machine's coil level</li>
 * <li>{@code C} any tiered structure glass, which is what sets the machine's glass tier</li>
 * <li>{@code D} Black Steel frame</li>
 * <li>{@code E} Robust Tungstensteel Machine Casing</li>
 * <li>{@code F} block of iron</li>
 * <li>{@code G} Clean Stainless Steel Machine Casing</li>
 * <li>{@code H} the muffler slots, drawn where the blueprint wants them</li>
 * <li>{@code I} Centrifuge Casing (GT++)</li>
 * <li>{@code J} Heat Proof Machine Casing</li>
 * <li>{@code K} PTFE Pipe Casing</li>
 * <li>{@code ~} the controller</li>
 * <li>a space, a position the structure check does not look at</li>
 * </ul>
 */
public final class RareEarthStructure {

    /**
     * The only symbols this class registers with StructureLib. Anything else found in a shape is replaced by a space
     * before the definition is built: StructureLib throws {@code Missing Structure Element bindings} for an unknown
     * character, and an exception from a structure check is not something the game handles well.
     */
    private static final String KNOWN_SYMBOLS = "ABCDEFGHIJK~ ";

    /**
     * Minimum number of Chemically Inert Machine Casings the shell has to keep. Everything else marked {@code A} may be
     * a hatch instead, and a hatch does not count as a casing.
     */
    static final int MIN_INERT_CASINGS = 11;

    private RareEarthStructure() {}

    /**
     * The shape written out, straight from {@code more/结构文档.txt}: 11 wide, 12 levels tall and 12 slices deep, with
     * the controller in the middle of the front wall, 11 levels down from the top.
     * <p>
     * Slices 7 to 12 of the source document only list eleven levels instead of twelve; the last one is written here as
     * an empty field, which the structure check ignores.
     */
    static String[] shapeText() {
        return new String[] {
            // slice 1, the front wall the controller sits in
            "           |           |           |           |           |           |           |           |           |AAAAAAAAAAA|AAAAA~AAAAA|AAAAAAAAAAA",
            // slice 2
            "           |           |           |           |   EEEEE   |   DCCCD   |   DCCCD   |   DCCCD   |   EEEEE   |ABABABABABA|AKAKAKAKAKA|AAAAAAAAAAA",
            // slice 3
            "    EEE    |    D D    |    D D    |    D D    |   EEEEE   |   CBBBC   |   CBBBC   |   CBBBC   |   EEEEE   |AAAAAAAAAAA|AAAAAAAAAAA|AAAAAAAAAAA",
            // slice 4
            "    EEE    |     F     |     F     |     F     |   EEFEE   |   CBFBC   |   CBFBC   |   CBFBC   |   EEEEE   |IIIIIIIIIII|IIIIIIIIIII|IIIIIIIIIII",
            // slice 5
            "    EEE    |    D D    |    D D    |    D D    |   EEEEE   |   CBBBC   |   CBBBC   |   CBBBC   |   EEEEE   |IHIIIIIIIHI|I I I I I I|IIIIIIIIIII",
            // slice 6
            "           |           |           |           |   EEEEE   |   DCCCD   |   DCCCD   |   DCCCD   |   EEEEE   |IIIIIIIIIII|IIIIIIIIIII|IIIIIIIIIII",
            // slice 7
            "           |           |           |           |           |           |           |JJJJJJJJJJJ|BBBBBBBBBBB|BBBBBBBBBBB|JJJJJJJJJJJ",
            // slice 8
            "           |           |           |           |           |           |           |JHJHJHJHJHJ|B B B B B B|B B B B B B|JJJJJJJJJJJ",
            // slice 9
            "           |           |           |           |           |           |           |JJJJJJJJJJJ|BBBBBBBBBBB|BBBBBBBBBBB|JJJJJJJJJJJ",
            // slice 10
            "           |           |           |           |           |           |GGGGGGGGGGG|GGGGGGGGGGG|GGGGGGGGGGG|GGGGGGGGGGG|GGGGGGGGGGG",
            // slice 11
            "           |           |           |           |           |           |GHGHGHGHGHG|G G G G G G|G G G G G G|G G G G G G|GGGGGGGGGGG",
            // slice 12
            "           |           |           |           |           |           |GGGGGGGGGGG|GGGGGGGGGGG|GGGGGGGGGGG|GGGGGGGGGGG|GGGGGGGGGGG" };
    }

    /** The shape the machine is built from: {@link #shapeText()} parsed. */
    public static StructureBlueprint defaultBlueprint() {
        return StructureBlueprint.ofStages(0, 0, 0, shapeText())
            .padded();
    }

    /** Builds the StructureLib definition for a blueprint. */
    public static IStructureDefinition<MTERareEarthComplex> build(StructureBlueprint blueprint) {
        String[][] shape = sanitise(blueprint);
        StructureDefinition.Builder<MTERareEarthComplex> builder = StructureDefinition.<MTERareEarthComplex>builder()
            .addShape("main", shape);

        // The wall: every hatch this machine supports goes on the chemically inert casing.
        builder.addElement(
            'A',
            buildHatchAdder(MTERareEarthComplex.class)
                .atLeast(Energy.or(ExoticEnergy), Maintenance, InputBus, InputHatch, OutputBus, OutputHatch)
                .casingIndex(inertCasingTextureIndex())
                .hint(1)
                .buildAndChain(
                    onElementPass(MTERareEarthComplex::bumpInertCasings, ofBlock(GregTechAPI.sBlockCasings8, 0))));

        // The heating coils, which set the machine's coil level.
        builder
            .addElement('B', activeCoils(ofCoil(MTERareEarthComplex::setCoilLevel, MTERareEarthComplex::getCoilLevel)));

        // The structure glass, which sets the machine's glass tier.
        builder.addElement(
            'C',
            chainAllGlasses(-1, (te, tier) -> te.setGlassTier(tier), MTERareEarthComplex::getGlassTier));

        builder.addElement('D', ofFrame(Materials.BlackSteel));
        builder.addElement('E', ofBlock(GregTechAPI.sBlockCasings4, 0));
        builder.addElement('F', ofBlock(Blocks.iron_block, 0));
        builder.addElement('G', ofBlock(GregTechAPI.sBlockCasings4, 1));

        // The muffler sits on the casing the blueprint draws it in, which is either the heat proof or the clean
        // stainless steel casing depending on the slice.
        builder.addElement(
            'H',
            buildHatchAdder(MTERareEarthComplex.class).atLeast(Muffler)
                .casingIndex(heatProofCasingTextureIndex())
                .hint(3)
                .buildAndChain(ofBlock(GregTechAPI.sBlockCasings1, 11), ofBlock(GregTechAPI.sBlockCasings4, 1)));

        // GT++ only creates its casings during its own pre-init, which runs after this machine is registered, so the
        // block is looked up lazily, at the first structure check rather than here.
        builder.addElement('I', lazy(t -> ofBlock(ModBlocks.blockCasingsMisc, 0)));
        builder.addElement('J', ofBlock(GregTechAPI.sBlockCasings1, 11));
        builder.addElement('K', ofBlock(GregTechAPI.sBlockCasings8, 1));

        return builder.build();
    }

    /** Texture index of the chemically inert machine casing, i.e. of {@code sBlockCasings8:0}. */
    private static int inertCasingTextureIndex() {
        return ((BlockCasings8) GregTechAPI.sBlockCasings8).getTextureIndex(0);
    }

    /** The casing texture the controller itself shows, which is the machine's main casing. */
    static ITexture inertCasingTexture() {
        return Casings.ChemicallyInertMachineCasing.getCasingTexture();
    }

    /** How many times {@code symbol} appears in the shape, which is the number of blocks that position needs. */
    static int countSymbol(StructureBlueprint blueprint, char symbol) {
        int count = 0;
        for (var stage : blueprint.stages) {
            for (String row : stage) {
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) == symbol) count++;
                }
            }
        }
        return count;
    }

    /** Texture index of the heat proof machine casing, i.e. of {@code sBlockCasings:11}. */
    private static int heatProofCasingTextureIndex() {
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(11);
    }

    /** Texture index of the clean stainless steel machine casing, i.e. of {@code sBlockCasings4:1}. */
    static int cleanStainlessCasingTextureIndex() {
        return ((BlockCasings4) GregTechAPI.sBlockCasings4).getTextureIndex(1);
    }

    /**
     * Replaces every character StructureLib has no element for with a space. A shape is written by hand, so this makes
     * an unknown symbol a cosmetic problem instead of a crash.
     */
    private static String[][] sanitise(StructureBlueprint blueprint) {
        String[][] shape = blueprint.toShapeArray();
        String[][] clean = new String[shape.length][];
        for (int slice = 0; slice < shape.length; slice++) {
            String[] levels = shape[slice];
            clean[slice] = new String[levels.length];
            for (int level = 0; level < levels.length; level++) {
                String text = levels[level];
                StringBuilder fixed = new StringBuilder(text.length());
                for (int i = 0; i < text.length(); i++) {
                    char symbol = text.charAt(i);
                    fixed.append(KNOWN_SYMBOLS.indexOf(symbol) >= 0 ? symbol : ' ');
                }
                clean[slice][level] = fixed.toString();
            }
        }
        return clean;
    }
}
