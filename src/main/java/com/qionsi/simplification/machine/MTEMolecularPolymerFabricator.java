package com.qionsi.simplification.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;
import static gregtech.common.misc.WirelessNetworkManager.processInitialSettings;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.dreammaster.block.BlockList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.recipe.ModRecipeMaps;

import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.enums.SoundResource;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoidingMode;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import thaumcraft.common.config.ConfigBlocks;

/**
 * 分子聚合物制造机 / Molecular Polymer Fabricator.
 * <p>
 * One machine that runs a whole handful of lategame polymer and super glue recipes in a single step, with two modes and
 * two structure tiers:
 *
 * <ul>
 * <li><b>单步制造 / single step</b> ({@link #MODE_SINGLE}) consumes the reagents and turns them into the product through
 * "atomic manipulation". Its pool is {@link ModRecipeMaps#molecularPolymerSingleRecipes}.</li>
 * <li><b>平行时空制造 / parallel timeline</b> ({@link #MODE_PARALLEL}) tears spacetime open and pulls the reagents out of a
 * parallel timeline instead, so it consumes no reagents at all. Its pool is
 * {@link ModRecipeMaps#molecularPolymerParallelRecipes}.</li>
 * </ul>
 *
 * <h2>The two structure tiers</h2>
 *
 * The shape is the same 16 x 12 x 16 cylinder in both tiers; only three sets of blocks change, and the higher tier is
 * built out of the space time continuum rippers that the parallel timeline mode needs:
 *
 * <table>
 * <caption>tier 1 and tier 2 blocks</caption>
 * <tr>
 * <th>symbol</th>
 * <th>tier 1</th>
 * <th>tier 2</th>
 * </tr>
 * <tr>
 * <td>C / F</td>
 * <td>Tungstensteel Frame Box</td>
 * <td>Neutronium Frame Box</td>
 * </tr>
 * <tr>
 * <td>D / G</td>
 * <td>Thaumcraft mysterious block</td>
 * <td>Space Time Continuum Ripper</td>
 * </tr>
 * <tr>
 * <td>E / H</td>
 * <td>Reinforced Block</td>
 * <td>Neutronium Plated Reinforced Stone</td>
 * </tr>
 * </table>
 *
 * The tier is not a mode: it is read straight off the blocks the player used. A tier 1 structure runs the single step
 * mode only, because the parallel timeline mode needs the rippers; a tier 2 structure runs both. This is why the two
 * tiers are two structure pieces rather than one shape with interchangeable blocks - the machine has to know afterwards
 * which one it is standing in.
 * <p>
 * Which tier the hologram projector and the NEI preview build is chosen the way GT's industrial coke oven does it: one
 * projector builds tier 1, a stack of two or more builds tier 2.
 */
public class MTEMolecularPolymerFabricator extends MTEExtendedPowerMultiBlockBase<MTEMolecularPolymerFabricator>
    implements ISurvivalConstructable, ICasingTextureProvider {

    /** Structure piece of the cheaper tier: tungstensteel frames, mysterious blocks, reinforced blocks. */
    public static final String STRUCTURE_PIECE_T1 = "t1";
    /** Structure piece of the higher tier: neutronium frames and the space time continuum rippers. */
    public static final String STRUCTURE_PIECE_T2 = "t2";

    private static final String BLUEPRINT_T1 = MyMod.MODID + ":multiblock/molecular_polymer_fabricator_t1";
    private static final String BLUEPRINT_T2 = MyMod.MODID + ":multiblock/molecular_polymer_fabricator_t2";

    /**
     * Both tiers share one shape, so they also share the controller offsets: the controller sits in the front face of
     * the cylinder, column 8 from the left, level 10 from the top, and two cells away from the player side.
     */
    private static final String[][] shapeT1 = StructureBlueprintFile.read(BLUEPRINT_T1);
    private static final String[][] shapeT2 = StructureBlueprintFile.read(BLUEPRINT_T2);

    public static final int HORIZONTAL_OFF_SET = 7;
    public static final int VERTICAL_OFF_SET = 9;
    public static final int DEPTH_OFF_SET = 2;

    /**
     * 化学惰性机械方块 / Chemically Inert Machine Casings the shell has to keep. Every hatch of this machine goes on one
     * of them, and a position that holds a hatch does not count as a casing, so this is a floor, not the number the
     * blueprint draws: the blueprint marks {@value #CASING_POSITIONS_IN_BLUEPRINT} of them.
     */
    public static final int MIN_INERT_CASINGS = 20;

    /** How many chemically inert casing positions the blueprint draws, printed in the log and in the tooltip. */
    public static final int CASING_POSITIONS_IN_BLUEPRINT = 376;

    /** 单步制造 / single step: the machine starts in this mode and tier 1 can only run it. */
    public static final int MODE_SINGLE = 0;
    /** 平行时空制造 / parallel timeline: needs a tier 2 structure, and consumes no reagents. */
    public static final int MODE_PARALLEL = 1;

    /** No structure matched. */
    public static final int TIER_NONE = 0;
    /** t1: tungstensteel frames, mysterious blocks, reinforced blocks. */
    public static final int TIER_1 = 1;
    /** t2: neutronium frames, space time continuum rippers, neutronium plated reinforced stone. */
    public static final int TIER_2 = 2;

    /**
     * Parallels the power panel offers: the whole int range. The design document says both modes reach 2147483647
     * parallels, which is exactly {@link Integer#MAX_VALUE}.
     */
    public static final int MIN_PARALLEL = 1;
    public static final int MAX_PARALLEL = Integer.MAX_VALUE;

    /**
     * Meta of the {@code gt.blockreinforced} block the tier 1 {@code E} positions want: the one whose own name is
     * "Reinforced Block", i.e. 防爆石. The log prints the display name this resolves to on startup so a wrong meta is
     * obvious without having to build the machine.
     */
    public static final int REINFORCED_BLOCK_META = 13;

    /** Meta of the Thaumcraft cosmetic solid the tier 1 {@code D} positions want: 神秘方块. */
    public static final int MYSTERIOUS_BLOCK_META = 4;

    /** Number of lines the tooltip takes from the language file; missing ones are skipped. */
    private static final int TOOLTIP_LINES = 20;

    private static IStructureDefinition<MTEMolecularPolymerFabricator> DEFINITION;

    /** Chemically inert casings the last structure check found. */
    private int inertCasings;

    /** Tier of the structure that was matched last, {@link #TIER_NONE} until the machine forms. */
    private int structureTier = TIER_NONE;

    /** True when the GUI switch has the machine paying for its runs out of the wireless network. */
    private boolean wirelessEnergyMode;

    /** Whose wireless network pays, worked out on the machine's first tick, as the plasma mixer does it. */
    private UUID ownerUUID;

    /** What the last run was charged to the wireless network, negative because it was taken away. */
    private BigInteger finalConsumption = BigInteger.ZERO;

    public MTEMolecularPolymerFabricator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        startAtOneParallel();
    }

    public MTEMolecularPolymerFabricator(String aName) {
        super(aName);
        startAtOneParallel();
    }

    /**
     * A freshly built machine runs one recipe at a time. The power panel's text box holds the player's own number
     * ({@code powerPanelMaxParallel}) and the "always use the maximum" checkbox is cleared, so
     * <em>nothing</em> starts out at two billion parallels by accident; that ceiling is only there for the player to
     * type into the box.
     */
    private void startAtOneParallel() {
        alwaysMaxParallel = false;
        powerPanelMaxParallel = MIN_PARALLEL;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEMolecularPolymerFabricator(this.mName);
    }

    // ------------------------------------------------------------------ structure

    /**
     * Checks the two blueprints against the machine's own offsets and writes what they ask for into the log. Called
     * once per tier at registration, while the log is still short.
     */
    public static void prepareStructure() {
        StructureBlueprintFile
            .verifyControllerPosition(BLUEPRINT_T1, shapeT1, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        StructureBlueprintFile
            .verifyControllerPosition(BLUEPRINT_T2, shapeT2, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        MyMod.LOG.info(
            "Molecular Polymer Fabricator: both tiers are {}x{}x{}, the tier 1 blueprint draws {} chemically inert "
                + "casings, {} mysterious blocks, {} reinforced blocks and {} tungstensteel frames, the tier 2 one "
                + "draws {} chemically inert casings, {} space time continuum rippers, {} neutronium plated reinforced "
                + "stone and {} neutronium frames; the shell has to keep {} of the casings",
            StructureBlueprintFile.width(shapeT1),
            StructureBlueprintFile.height(shapeT1),
            StructureBlueprintFile.depth(shapeT1),
            StructureBlueprintFile.count(shapeT1, 'B'),
            StructureBlueprintFile.count(shapeT1, 'D'),
            StructureBlueprintFile.count(shapeT1, 'E'),
            StructureBlueprintFile.count(shapeT1, 'C'),
            StructureBlueprintFile.count(shapeT2, 'B'),
            StructureBlueprintFile.count(shapeT2, 'G'),
            StructureBlueprintFile.count(shapeT2, 'H'),
            StructureBlueprintFile.count(shapeT2, 'F'),
            MIN_INERT_CASINGS);
        if (StructureBlueprintFile.count(shapeT1, 'B') != CASING_POSITIONS_IN_BLUEPRINT) {
            MyMod.LOG.warn(
                "The Molecular Polymer Fabricator blueprint draws {} chemically inert casings, but the machine's "
                    + "constants say {}. One of the two is out of date.",
                StructureBlueprintFile.count(shapeT1, 'B'),
                CASING_POSITIONS_IN_BLUEPRINT);
        }
        logBoundBlocks();
    }

    /**
     * Prints the block each of the three "tier dependent" symbols resolved to, so a wrong block id shows up in the
     * startup log instead of as "the machine never forms".
     */
    private static void logBoundBlocks() {
        MyMod.LOG.info(
            "Molecular Polymer Fabricator blocks: A = Clean Stainless Steel Machine Casing, B = Chemically Inert "
                + "Machine Casing (every hatch goes here), C = Tungstensteel Frame Box, D = {}:{}, E = {}:{}, F = "
                + "Neutronium Frame Box, G = {}, H = {}",
            blockId(ConfigBlocks.blockCosmeticSolid),
            MYSTERIOUS_BLOCK_META,
            blockId(GregTechAPI.sBlockReinforced),
            REINFORCED_BLOCK_META,
            "Space Time Continuum Ripper (gtplusplus blockCasings5Misc:10)",
            blockId(BlockList.NeutroniumPlatedReinforcedStone.block));
    }

    private static String blockId(Block block) {
        if (block == null) return "MISSING";
        try {
            return net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        } catch (Throwable t) {
            return String.valueOf(block.getUnlocalizedName());
        }
    }

    private static IStructureDefinition<MTEMolecularPolymerFabricator> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    /**
     * The two tiers as two pieces of one definition.
     * <p>
     * The {@code B} positions are the same in both tiers - chemically inert casings that take every hatch - so they are
     * bound once. The three sets that differ simply use different symbols, because a symbol belongs to the whole
     * definition rather than to a piece.
     * <p>
     * Everything that comes from another mod is resolved through a supplier that runs at the first structure check:
     * {@code Casings.SpaceTimeContinuumRipper.asElement()} looks its block up lazily, and the New Horizons and
     * Thaumcraft blocks are plain {@code Block} fields of their own registry enums. Handing StructureLib a null block
     * would crash the NEI preview, so a block that cannot be resolved falls back to a real one and says so.
     */
    private static IStructureDefinition<MTEMolecularPolymerFabricator> buildDefinition() {
        try {
            return StructureDefinition.<MTEMolecularPolymerFabricator>builder()
                .addShape(STRUCTURE_PIECE_T1, StructureUtility.transpose(shapeT1))
                .addShape(STRUCTURE_PIECE_T2, StructureUtility.transpose(shapeT2))
                // 洁净不锈钢机械方块: the floor plate of the inner chamber.
                .addElement('A', Casings.CleanStainlessSteelMachineCasing.asElement())
                // 化学惰性机械方块: the whole shell, and the one place a hatch may go.
                .addElement(
                    'B',
                    buildHatchAdder(MTEMolecularPolymerFabricator.class)
                        .atLeast(Energy.or(ExoticEnergy), Maintenance, InputBus, InputHatch, OutputBus, OutputHatch)
                        .casingIndex(Casings.ChemicallyInertMachineCasing.getTextureId())
                        .hint(1)
                        .buildAndChain(
                            onElementPass(x -> ++x.inertCasings, Casings.ChemicallyInertMachineCasing.asElement())))
                // --- tier 1 ---
                .addElement('C', ofFrame(Materials.TungstenSteel))
                .addElement('D', ofBlock(thaumcraftBlock(), MYSTERIOUS_BLOCK_META))
                .addElement('E', ofBlock(reinforcedBlock(), REINFORCED_BLOCK_META))
                // --- tier 2 ---
                .addElement('F', ofFrame(Materials.Neutronium))
                .addElement('G', Casings.SpaceTimeContinuumRipper.asElement())
                .addElement('H', ofBlock(neutroniumPlatedStone(), 0))
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Molecular Polymer Fabricator structure definition; the machine falls back to a "
                    + "single block so the game keeps running.",
                t);
            return StructureDefinition.<MTEMolecularPolymerFabricator>builder()
                .addShape(STRUCTURE_PIECE_T1, new String[][] { { "~" } })
                .addShape(STRUCTURE_PIECE_T2, new String[][] { { "~" } })
                .addElement('B', Casings.ChemicallyInertMachineCasing.asElement())
                .build();
        }
    }

    /** The Thaumcraft block the tier 1 {@code D} positions want: 神秘方块, {@code blockCosmeticSolid:4}. */
    private static Block thaumcraftBlock() {
        Block block = ConfigBlocks.blockCosmeticSolid;
        if (block == null) {
            MyMod.LOG.error(
                "Thaumcraft did not register blockCosmeticSolid; the D positions of the Molecular Polymer Fabricator's "
                    + "tier 1 structure accept iron blocks until that is fixed.");
            return net.minecraft.init.Blocks.iron_block;
        }
        return block;
    }

    /** The GregTech block the tier 1 {@code E} positions want: 防爆石, {@code gt.blockreinforced}. */
    private static Block reinforcedBlock() {
        Block block = GregTechAPI.sBlockReinforced;
        if (block == null) {
            MyMod.LOG.error(
                "GregTech did not register sBlockReinforced; the E positions of the Molecular Polymer Fabricator's "
                    + "tier 1 structure accept iron blocks until that is fixed.");
            return net.minecraft.init.Blocks.iron_block;
        }
        return block;
    }

    /** The New Horizons block the tier 2 {@code H} positions want: 中子镀层防爆石. */
    private static Block neutroniumPlatedStone() {
        try {
            Block block = BlockList.NeutroniumPlatedReinforcedStone.block;
            if (block != null) return block;
        } catch (Throwable t) {
            MyMod.LOG.error("Could not read NeutroniumPlatedReinforcedStone out of New Horizons Core Mod", t);
        }
        MyMod.LOG.error(
            "New Horizons Core Mod did not register NeutroniumPlatedReinforcedStone; the H positions of the Molecular "
                + "Polymer Fabricator's tier 2 structure accept iron blocks until that is fixed.");
        return net.minecraft.init.Blocks.iron_block;
    }

    @Override
    public IStructureDefinition<MTEMolecularPolymerFabricator> getStructureDefinition() {
        return getDefinition();
    }

    /**
     * Tries the higher tier first and falls back to the cheaper one.
     * <p>
     * This is safe because {@code checkPiece} only walks the shape twice: the first walk asks every element "is this
     * block right?" and changes nothing, and only when the whole shape matched does a second walk run the elements and
     * register the hatches. A tier 2 check that fails therefore leaves no hatch behind. The error list it filled is
     * cleared before the tier 1 check so that a successful tier 1 build is not still carrying tier 2's "expected
     * neutronium frame" complaints.
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        resetStructureState();
        if (checkPiece(STRUCTURE_PIECE_T2, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            structureTier = TIER_2;
            checkStructureRequirements(errors);
            return;
        }
        errors.clear();
        resetStructureState();
        if (!checkPiece(STRUCTURE_PIECE_T1, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            structureTier = TIER_NONE;
            return;
        }
        structureTier = TIER_1;
        checkStructureRequirements(errors);
    }

    private void resetStructureState() {
        inertCasings = 0;
    }

    /**
     * What the shape alone cannot say: enough casings left over, exactly one maintenance hatch, and at least one way
     * for items and fluids to go in and out.
     * <p>
     * There is deliberately no {@code checkHasAnyEnergy} here. The design document allows this machine to run straight
     * off the player's wireless network, so an energy hatch is optional; when the wireless switch is off and no hatch
     * is present, GregTech itself reports a power loss instead of the structure refusing to form.
     */
    private void checkStructureRequirements(List<StructureError> errors) {
        checkCasingMin(errors, inertCasings, MIN_INERT_CASINGS);
        checkOneMaintenanceHatch(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
    }

    /** The tier of the structure the machine is standing in, {@link #TIER_NONE} while it is not formed. */
    public int getStructureTier() {
        return structureTier;
    }

    /** True when the parallel timeline mode is unlocked, i.e. when the structure is the tier 2 one. */
    public boolean isParallelTimelineUnlocked() {
        return structureTier == TIER_2;
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity aBaseMetaTileEntity) {
        // A structure that dropped back to tier 1 can no longer run the parallel timeline mode, so the machine is put
        // back into the single step mode rather than left sitting in a mode its blocks do not support.
        if (structureTier != TIER_2 && machineMode == MODE_PARALLEL) {
            MyMod.LOG.info(
                "Molecular Polymer Fabricator: the structure is no longer tier 2, so the machine leaves the "
                    + "parallel timeline mode.");
            super.setMachineMode(MODE_SINGLE);
        }
    }

    /**
     * Builds the tier the projector's stack size asks for, exactly as GT's industrial coke oven does it: a single
     * projector builds the cheaper tier 1 shape, a stack of two or more builds tier 2.
     */
    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        String piece = stackSize.stackSize <= 1 ? STRUCTURE_PIECE_T1 : STRUCTURE_PIECE_T2;
        buildPiece(piece, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        String piece = stackSize.stackSize <= 1 ? STRUCTURE_PIECE_T1 : STRUCTURE_PIECE_T2;
        return survivalBuildPiece(
            piece,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] {
            "\u00a7e\u5206\u5b50\u805a\u5408\u7269\u5236\u9020\u673a\u00a7r / Molecular Polymer Fabricator",
            "\u00a77The controller goes in the front face of the cylinder. The shape is "
                + StructureBlueprintFile.width(shapeT1)
                + " wide x "
                + StructureBlueprintFile.height(shapeT1)
                + " tall x "
                + StructureBlueprintFile.depth(shapeT1)
                + " deep.",
            "\u00a77A = Clean Stainless Steel Machine Casing, B = Chemically Inert Machine Casing: every hatch goes "
                + "on a B position.",
            "\u00a77Tier 1: C = Tungstensteel Frame, D = the Thaumcraft mysterious block, E = Reinforced Block.",
            "\u00a77Tier 2: F = Neutronium Frame, G = Space Time Continuum Ripper, H = Neutronium Plated Reinforced "
                + "Stone.",
            "\u00a77Tier 2 is what unlocks the parallel timeline mode; tier 1 runs the single step mode only.",
            "\u00a77Sneak-right-click the controller with one projector to build tier 1, or with a stack of two to "
                + "build tier 2." };
    }

    // ------------------------------------------------------------------ modes

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public String getMachineModeKey() {
        return machineMode == MODE_PARALLEL ? "simplification.molecular_polymer.mode.parallel"
            : "simplification.molecular_polymer.mode.single";
    }

    /**
     * Refuses to enter the parallel timeline mode unless the structure is the tier 2 one, which is what the design
     * document means by "the mode can be switched on once the space time continuum rippers are there".
     */
    @Override
    public void setMachineMode(int mode) {
        if (mode == MODE_PARALLEL && structureTier != TIER_2) {
            MyMod.LOG.info(
                "Molecular Polymer Fabricator: the parallel timeline mode needs the tier 2 structure (space time "
                    + "continuum rippers); the machine stays in the single step mode.");
            return;
        }
        super.setMachineMode(mode);
    }

    @Override
    public int nextMachineMode() {
        if (machineMode == MODE_SINGLE && structureTier == TIER_2) setMachineMode(MODE_PARALLEL);
        else setMachineMode(MODE_SINGLE);
        return machineMode;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return machineMode == MODE_PARALLEL ? ModRecipeMaps.molecularPolymerParallelRecipes
            : ModRecipeMaps.molecularPolymerSingleRecipes;
    }

    /**
     * Both pools, so NEI lists this machine as the catalyst of the parallel timeline page as well as the single step
     * one even though only one of them is active at a time.
     */
    @Override
    public Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays
            .asList(ModRecipeMaps.molecularPolymerSingleRecipes, ModRecipeMaps.molecularPolymerParallelRecipes);
    }

    /**
     * The GUI, which is the stock multiblock GUI plus the wireless energy switch this machine adds.
     */
    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MolecularPolymerFabricatorGui(this).withMachineModeIcons(
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_SEPARATOR);
    }

    // ------------------------------------------------------------------ power

    /** True when the GUI switch has the machine paying out of the wireless network instead of its energy hatches. */
    public boolean isWirelessEnergyMode() {
        return wirelessEnergyMode;
    }

    /** Called by the GUI switch. */
    public void setWirelessEnergyMode(boolean wireless) {
        if (wirelessEnergyMode == wireless) return;
        wirelessEnergyMode = wireless;
        // Nothing else has to be rebuilt: GregTech calls setupProcessingLogic() - and through it
        // setProcessingLogicPower() - on every recipe check, so the new power source is picked up on the next check.
        MyMod.LOG.info(
            "Molecular Polymer Fabricator: power source switched to {}",
            wireless ? "the wireless network" : "the energy hatches");
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && (aTick == 1)) {
            ownerUUID = processInitialSettings(aBaseMetaTileEntity);
        }
    }

    /** The wireless account this machine spends from, worked out on demand if the first tick has not run yet. */
    private UUID wirelessOwner() {
        if (ownerUUID == null) ownerUUID = processInitialSettings(getBaseMetaTileEntity());
        return ownerUUID;
    }

    /**
     * The voltage the recipe logic sees.
     * <p>
     * With the wireless switch on there is nothing to take a tier from, so any recipe has to be findable - that is what
     * the plasma mixer does. With the switch off the hatches decide, exactly as on any other multiblock. Either way
     * {@code setUnlimitedTierSkips()} lifts the usual "this recipe is a higher tier than your energy hatch" refusal,
     * which is what the design document asks for.
     */
    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        if (wirelessEnergyMode) {
            logic.setAvailableVoltage(Long.MAX_VALUE);
            logic.setAvailableAmperage(1);
            logic.setAmperageOC(false);
            logic.setUnlimitedTierSkips();
            return;
        }
        super.setProcessingLogicPower(logic);
        logic.setUnlimitedTierSkips();
    }

    /**
     * The recipe logic: lossless overclocking, unlimited tier skips, and an optional wireless payment.
     * <p>
     * With the switch on the run is paid for the way GregTech's {@code MTETranscendentPlasmaMixer} pays: the balance is
     * checked before the run may start and the whole cost is taken at once when it does, after which
     * {@code overwriteCalculatedEut(0)} leaves nothing to drain per tick. With the switch off the recipe logic behaves
     * like any other multiblock and GregTech drains the energy hatches each tick.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            /** What one parallel of the run being looked at costs in total, in EU. */
            private BigInteger parallelCost = BigInteger.ZERO;

            @Override
            protected @Nonnull CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                if (!wirelessEnergyMode) return CheckRecipeResultRegistry.SUCCESSFUL;
                // EU/t times the number of ticks, times the parallels the player asked for. BigInteger because the
                // parallel count reaches Integer.MAX_VALUE.
                parallelCost = BigInteger.valueOf(recipe.mEUt)
                    .multiply(BigInteger.valueOf(recipe.mDuration));
                BigInteger wantedEU = parallelCost.multiply(BigInteger.valueOf(Math.max(1, maxParallel)));
                BigInteger availableEU = getUserEU(wirelessOwner());
                if (availableEU.compareTo(wantedEU) < 0) {
                    finalConsumption = BigInteger.ZERO;
                    return CheckRecipeResultRegistry.insufficientStartupPower(wantedEU);
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            protected @Nonnull CheckRecipeResult onRecipeStart(@Nonnull GTRecipe recipe) {
                if (!wirelessEnergyMode) return CheckRecipeResultRegistry.SUCCESSFUL;
                finalConsumption = parallelCost.multiply(BigInteger.valueOf(-calculatedParallels));
                if (!addEUToGlobalEnergyMap(wirelessOwner(), finalConsumption)) {
                    return CheckRecipeResultRegistry.insufficientStartupPower(finalConsumption);
                }
                overwriteCalculatedEut(0);
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }.setMaxParallelSupplier(this::getTrueParallel)
            .enablePerfectOverclock()
            .setUnlimitedTierSkips();
    }

    /**
     * The most parallels the machine can ever run: {@link Integer#MAX_VALUE}, as the design document says for both
     * modes. The number the player actually typed lives in {@code powerPanelMaxParallel} and the power panel's text box
     * takes its upper bound from here, so returning the ceiling is what makes the box accept the whole range.
     */
    @Override
    public int getMaxParallelRecipes() {
        return MAX_PARALLEL;
    }

    // ------------------------------------------------------------------ void / separation

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public Set<VoidingMode> getAllowedVoidingModes() {
        return VoidingMode.ALL_OPTIONS;
    }

    @Override
    public VoidingMode getDefaultVoidingMode() {
        return VoidingMode.VOID_NONE;
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean getDefaultInputSeparationMode() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    @Override
    protected SoundResource getProcessStartSound() {
        return SoundResource.GTCEU_LOOP_FIRE;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("molecularPolymerWirelessEnergy", wirelessEnergyMode);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessEnergyMode = aNBT.getBoolean("molecularPolymerWirelessEnergy");
        if (powerPanelMaxParallel < MIN_PARALLEL) powerPanelMaxParallel = MIN_PARALLEL;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        // The structure is gone, so the tier it had is gone too.
        structureTier = TIER_NONE;
        inertCasings = 0;
    }

    // ------------------------------------------------------------------ looks

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        // The same face as the Large Chemical Reactor, as the design asks for.
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.ChemicallyInertMachineCasing.getCasingTexture();
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Molecular Polymer Fabricator");
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            String key = "simplification.tooltip.molecularPolymerFabricator." + line;
            if (StatCollector.canTranslate(key)) tt.addInfo(StatCollector.translateToLocal(key));
        }
        tt.addSeparator()
            .addInfo("Parallels are typed into the power panel: " + MIN_PARALLEL + " to " + MAX_PARALLEL + ".")
            .addInfo("Tier 2 structure unlocks the parallel timeline mode; tier 1 runs single step only.")
            .addSeparator()
            // beginStructureBlock takes width, height and depth in that order, all three read off the blueprint so an
            // edit to the structure keeps the tooltip correct.
            .beginStructureBlock(
                StructureBlueprintFile.width(shapeT1),
                StructureBlueprintFile.height(shapeT1),
                StructureBlueprintFile.depth(shapeT1),
                false)
            .addController(
                "Front face of the cylinder, column " + (HORIZONTAL_OFF_SET + 1)
                    + ", level "
                    + (VERTICAL_OFF_SET + 1)
                    + " from the top")
            .addCasing(MIN_INERT_CASINGS + "+", "Chemically Inert Machine Casing", false)
            // Every hatch of this machine goes on a chemically inert casing, which is what the design document says.
            .addEnergyHatch("1+", "Any Chemically Inert Machine Casing (optional when the wireless switch is on)", 1)
            .addMaintenanceHatch("1", "Any Chemically Inert Machine Casing", 1)
            .addInputHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addOutputHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addInputBus("1+", "Any Chemically Inert Machine Casing", 1)
            .addOutputBus("1+", "Any Chemically Inert Machine Casing", 1)
            .addOtherStructurePart("Clean Stainless Steel Machine Casing", "The A positions")
            .addOtherStructurePart("Tungstensteel / Neutronium Frame Box", "The C positions (t1) or F positions (t2)")
            .addOtherStructurePart(
                "Thaumcraft mysterious block / Space Time Continuum Ripper",
                "The D positions (t1) or G positions (t2)")
            .addOtherStructurePart(
                "Reinforced Block / Neutronium Plated Reinforced Stone",
                "The E positions (t1) or H positions (t2)")
            .toolTipFinisher();
        return tt;
    }
}
