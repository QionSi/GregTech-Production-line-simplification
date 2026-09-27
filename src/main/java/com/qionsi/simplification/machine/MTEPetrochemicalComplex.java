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
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.qionsi.simplification.MyMod;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.SoundResource;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.tooltip.TooltipTier;

/**
 * 石油化工综合体 / Petrochemical Complex.
 * <p>
 * The shape lives in the blueprint {@code assets/simplification/multiblock/petrochemical_complex.mb}, which
 * {@link StructureBlueprintFile} reads at runtime, and {@link #getStructureDefinition()} turns into the StructureLib
 * form. A structure change is therefore a change to that text file; the shape is read once, when this class is first
 * loaded, so it takes a restart.
 */
public class MTEPetrochemicalComplex extends MTEExtendedPowerMultiBlockBase<MTEPetrochemicalComplex>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** The blueprint this machine is built from: {@code assets/simplification/multiblock/petrochemical_complex.mb}. */
    private static final String STRUCTURE_FILE_PATH = MyMod.MODID + ":multiblock/petrochemical_complex";

    /**
     * The shape of the machine, read from the blueprint: one line per level, the top level first, and one comma
     * separated cell per depth position, the one nearest the player first. See {@link StructureBlueprintFile} for the
     * format. It is transposed into StructureLib's own order when the definition is built.
     */
    private static final String[][] shape = StructureBlueprintFile.read(STRUCTURE_FILE_PATH);

    /** Where the {@code ~} of the blueprint sits inside the shape, which is where the controller goes. */
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 4;
    private static final int DEPTH_OFF_SET = 0;

    /** Base amount of parallel recipes this machine can run, before energy hatch bonus. */
    public static final int BASE_PARALLELS = 8;

    /** Solid Steel Machine Casing the shell has to keep, the rest of the marked spots being free for hatches. */
    public static final int MIN_SOLID_STEEL_CASINGS = 10;

    /** Bronze Plated Bricks the shell has to keep. */
    public static final int MIN_BRONZE_CASINGS = 8;

    /** Muffler positions the blueprint draws, every one of which has to be filled. */
    public static final int MUFFLER_SLOTS = 1;

    /**
     * The one definition, built from the shape above the first time it is asked for.
     * <p>
     * It is deliberately not built in a class initialiser: StructureLib raises an exception for a shape holding a
     * character it has no element for, and that is better caught and reported than thrown out of a class initialiser,
     * which would stop the game from starting.
     */
    private static IStructureDefinition<MTEPetrochemicalComplex> DEFINITION;

    private int mSolidSteelCasings;
    private int mBronzeCasings;

    public MTEPetrochemicalComplex(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPetrochemicalComplex(String aName) {
        super(aName);
    }

    /**
     * Builds the structure definition now rather than at the first structure check. Called once, right after the
     * machine is registered, so that a problem with the shape is reported while the log is still short instead of the
     * first time a player builds the machine.
     */
    public static void prepareStructure() {
        StructureBlueprintFile
            .verifyControllerPosition(STRUCTURE_FILE_PATH, shape, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        reportCounts();
        getDefinition();
    }

    /** Writes what the blueprint asks for into the log, and complains when that no longer matches the constants. */
    private static void reportCounts() {
        int mufflers = StructureBlueprintFile.count(shape, 'M');
        if (mufflers != MUFFLER_SLOTS) {
            MyMod.LOG.warn(
                "The Petrochemical Complex blueprint draws {} muffler positions, but the machine requires {}.",
                mufflers,
                MUFFLER_SLOTS);
        }
        MyMod.LOG.info(
            "Petrochemical Complex: the blueprint draws {} Bronze Plated Bricks, {} steel casing positions and {} "
                + "muffler positions; the shell has to keep {} and {} of the casings",
            StructureBlueprintFile.count(shape, 'B'),
            StructureBlueprintFile.count(shape, 'S') + StructureBlueprintFile.count(shape, 'O') + mufflers,
            mufflers,
            MIN_BRONZE_CASINGS,
            MIN_SOLID_STEEL_CASINGS);
    }

    /** The structure definition, built from the blueprint the first time it is asked for. */
    private static IStructureDefinition<MTEPetrochemicalComplex> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    /**
     * The StructureLib form of the blueprint.
     * <p>
     * Every symbol is a casing with the hatch adder in front of it, so a marked position may hold either that casing or
     * a hatch of the kinds listed. The casing elements are what count the shell for {@link #checkMachine}.
     */
    private static IStructureDefinition<MTEPetrochemicalComplex> buildDefinition() {
        try {
            return StructureDefinition.<MTEPetrochemicalComplex>builder()
                .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
                // Bronze Plated Bricks: everything the player feeds in and pulls out with buses and hatches.
                .addElement(
                    'B',
                    buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                        .casingIndex(Casings.BronzePlatedBricks.textureId)
                        .hint(2)
                        .buildAndChain(onElementPass(x -> ++x.mBronzeCasings, Casings.BronzePlatedBricks.asElement())))
                // The bottom level of the steel tower: power, maintenance and the fluid inputs.
                .addElement(
                    'S',
                    buildHatchAdder(MTEPetrochemicalComplex.class)
                        .atLeast(Energy.or(ExoticEnergy), Maintenance, InputHatch)
                        .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                        .hint(1)
                        .buildAndChain(
                            onElementPass(x -> ++x.mSolidSteelCasings, Casings.SolidSteelMachineCasing.asElement())))
                // The steel above it: the fluid outputs.
                .addElement(
                    'O',
                    buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(OutputHatch)
                        .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                        .hint(4)
                        .buildAndChain(
                            onElementPass(x -> ++x.mSolidSteelCasings, Casings.SolidSteelMachineCasing.asElement())))
                // The muffler position: this one takes a muffler hatch and nothing else.
                .addElement(
                    'M',
                    buildHatchAdder(MTEPetrochemicalComplex.class).atLeast(Muffler)
                        .exclusive()
                        .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                        .hint(3)
                        .buildAndChain(
                            onElementPass(x -> ++x.mSolidSteelCasings, Casings.SolidSteelMachineCasing.asElement())))
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Petrochemical Complex structure definition; the machine falls back to a single "
                    + "block so the game keeps running.",
                t);
            return StructureDefinition.<MTEPetrochemicalComplex>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "~" } })
                .build();
        }
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPetrochemicalComplex(this.mName);
    }

    @Override
    public IStructureDefinition<MTEPetrochemicalComplex> getStructureDefinition() {
        return getDefinition();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mSolidSteelCasings = 0;
        mBronzeCasings = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        // Only a floor on the casing count, not the shape's full count: every other marked spot is free for a hatch,
        // and a hatch does not count as a casing because the hatch adder stops before the casing element runs.
        checkCasingMin(errors, mSolidSteelCasings, MIN_SOLID_STEEL_CASINGS);
        checkCasingMin(errors, mBronzeCasings, MIN_BRONZE_CASINGS);
        checkHatchExact(errors, HatchElement.Muffler, MUFFLER_SLOTS);
        checkHasAnyEnergy(errors);
        checkOneMaintenanceHatch(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    /**
     * Shown in chat by the Multiblock Structure Hologram Projector when it projects this machine's shape. Returning
     * something useful here is what tells the player the projector recognised the machine at all.
     */
    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { "\u00a7e\u77f3\u6cb9\u5316\u5de5\u7efc\u5408\u4f53\u00a7r / Petrochemical Complex",
            "\u00a77The controller goes in the front wall. The shape is " + StructureBlueprintFile.width(shape)
                + " wide x "
                + StructureBlueprintFile.height(shape)
                + " tall x "
                + StructureBlueprintFile.depth(shape)
                + " deep.",
            "\u00a77S = bottom level of the steel tower (energy, maintenance and fluid input hatches).",
            "\u00a77O = steel above it (fluid output hatches).",
            "\u00a77B = Bronze Plated Bricks (item buses, fluid input and output hatches).",
            "\u00a77M = muffler in the middle of the top level, ~ = the controller.",
            "\u00a77Sneak-right-click the controller with the projector to build it automatically." };
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
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
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR_GLOW,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.SolidSteelMachineCasing.getCasingTexture();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return com.qionsi.simplification.recipe.ModRecipeMaps.petrochemicalComplexRecipes;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Petrochemical Complex")
            .addInfo("Processes Oil into various chemical products in a single step")
            .addInfo("Combines distillation, cracking, reforming and separation into one machine")
            .addInfo("All you have to do is supply the raw materials")
            .addInfo("How does it manage to do all that inside such a small machine??")
            .addSeparator()
            .addStaticParallelInfo(BASE_PARALLELS)
            .addDynamicMultiplicativeParallelInfo(2, TooltipTier.VOLTAGE)
            .addPerfectOCInfo()
            .addSupportMultiAmp()
            .addInfo("Accepts recipes of any voltage, whatever tier the energy hatches are")
            .addSeparator()
            // beginStructureBlock takes width, height and depth in that order, all three read off the blueprint so an
            // edit to the structure keeps the tooltip correct.
            .beginStructureBlock(
                StructureBlueprintFile.width(shape),
                StructureBlueprintFile.height(shape),
                StructureBlueprintFile.depth(shape),
                true)
            .addController(
                "Front slice, level " + (VERTICAL_OFF_SET + 1) + " from the top, column " + (HORIZONTAL_OFF_SET + 1))
            .addCasing(MIN_BRONZE_CASINGS + "+", "Bronze Plated Bricks", false)
            .addCasing(MIN_SOLID_STEEL_CASINGS + "+", "Solid Steel Machine Casing", false)
            .addEnergyHatch("1+", "Bottom level of the steel tower", 1)
            .addMaintenanceHatch("1", "Bottom level of the steel tower", 1)
            .addInputHatch("1+", "Bottom level of the steel tower", 1)
            .addOutputHatch("1+", "Steel above the bottom level", 4)
            .addInputBus("1+", "Any Bronze Plated Bricks", 2)
            .addOutputBus("1+", "Any Bronze Plated Bricks", 2)
            .addMufflerHatch("1", "Middle of the top level", 3)
            .toolTipFinisher();
        return tt;
    }

    /**
     * The recipe logic of the machine: perfect overclocking and no voltage ceiling.
     * <p>
     * {@code setUnlimitedTierSkips()} is what lifts the usual "the recipe is a higher tier than the energy hatch"
     * refusal, so an energy hatch of any tier may run a recipe of any tier. What is left is the ordinary power check:
     * the hatches still have to be able to supply the recipe's EU/t between them, which is a matter of how much power
     * they can deliver rather than of which tier they are.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setMaxParallelSupplier(this::getTrueParallel)
            .enablePerfectOverclock()
            .setUnlimitedTierSkips();
    }

    /**
     * Number of parallel recipes this machine may run at once: {@value #BASE_PARALLELS} as a base, doubled for every
     * voltage tier of the installed energy hatch. Perfect overclocking is enabled on top of this, so speeding a recipe
     * up never costs extra efficiency.
     * <p>
     * {@code getTrueParallel()} applies the limit the player set on the machine's power panel on top of this.
     */
    @Override
    public int getMaxParallelRecipes() {
        return BASE_PARALLELS << getVoltageTierForParallel();
    }

    /**
     * Voltage tier of the installed energy hatches, taken from the average input voltage. Capped at
     * {@link VoltageIndex#MAX} so the parallel count can never overflow.
     */
    private int getVoltageTierForParallel() {
        long voltage = getAverageInputVoltage();
        if (voltage <= 0) return 0;
        int tier = (64 - Long.numberOfLeadingZeros(voltage) - 2) / 2;
        return Math.max(0, Math.min(tier, VoltageIndex.MAX));
    }

    @Override
    protected SoundResource getProcessStartSound() {
        return SoundResource.GTCEU_LOOP_FIRE;
    }

    @Override
    protected int getTimeBetweenProcessSounds() {
        return 60;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    /**
     * Input separation, the button in the machine's GUI that GregTech draws as the separated hatches. With it on, every
     * input bus is searched as its own set of ingredients, so separate buses can feed separate recipes at the same
     * time; with it off, all the input buses are pooled and one recipe may take its solids from any of them. Fluids are
     * pooled either way.
     * <p>
     * Offering it is the point: most GregTech multiblocks either force it or forbid it, and this one leaves the choice
     * to the player through the button.
     */
    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * GregTech defaults this to {@link #supportsInputSeparation()}, which would switch separation on for every machine
     * that offers the button. This machine starts with it off instead: its recipes ask for a programming circuit plus
     * up to three different dusts, which anyone will naturally keep in different buses, so pooling them is the useful
     * default. Turning the button on is what runs a different recipe out of each bus.
     */
    @Override
    public boolean getDefaultInputSeparationMode() {
        return false;
    }
}
