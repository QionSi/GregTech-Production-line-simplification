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
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.recipe.ModRecipeMaps;

import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Materials;
import gregtech.api.enums.SoundResource;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
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
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings8;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;
import gtPlusPlus.core.block.ModBlocks;

/**
 * 稀土综合处理 / Rare Earth Processing Complex.
 * <p>
 * One machine for the rare earth production line with two modes, each with its own recipe map:
 * <ul>
 * <li>{@link #MODE_DUST} 矿粉模式 works on refined dusts. It starts at {@value #DUST_BASE_PARALLELS} parallels and
 * doubles them for every tier of the heating coils, overclocks without loss, shortens the recipe by
 * {@value #DUST_TIME_PER_HATCH_TIER_PERCENT}% per energy hatch tier and cheapens it by
 * {@value #DUST_ENERGY_PER_GLASS_TIER_PERCENT}% per structure glass tier.</li>
 * <li>{@link #MODE_ORE} 矿石模式 works on crushed ores. Its parallel count is fixed at {@value #ORE_PARALLELS} and it
 * overclocks with the usual loss until the structure glass reaches {@link #ORE_LOSSLESS_GLASS_TIER}; against the coil
 * the recipe asks for it saves {@value #ORE_TIME_PER_COIL_TIER_PERCENT}% of the time per coil tier and
 * {@value #ORE_ENERGY_PER_HATCH_TIER_PERCENT}% of the energy per energy hatch tier.</li>
 * </ul>
 * The mode is picked with the button in the machine's GUI, next to the input separation button that GT draws there.
 * The shape lives in the blueprint {@code assets/simplification/multiblock/rare_earth_complex.mb}, which
 * {@link StructureBlueprintFile} reads at runtime.
 */
public class MTERareEarthComplex extends MTEExtendedPowerMultiBlockBase<MTERareEarthComplex>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** The blueprint this machine is built from: {@code assets/simplification/multiblock/rare_earth_complex.mb}. */
    private static final String STRUCTURE_FILE_PATH = MyMod.MODID + ":multiblock/rare_earth_complex";

    /**
     * The shape of the machine, read from the blueprint: one line per level, the top level first, and one comma
     * separated cell per depth position, the one nearest the player first. See {@link StructureBlueprintFile} for the
     * format. It is transposed into StructureLib's own order when the definition is built.
     */
    private static final String[][] shape = StructureBlueprintFile.read(STRUCTURE_FILE_PATH);

    /** Where the {@code ~} of the blueprint sits inside the shape, which is where the controller goes. */
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 10;
    private static final int DEPTH_OFF_SET = 0;

    /**
     * Chemically Inert Machine Casings the shell has to keep. Everything else marked {@code A} may be a hatch instead,
     * and a hatch does not count as a casing.
     */
    public static final int MIN_INERT_CASINGS = 11;

    /** Muffler positions the blueprint draws, every one of which has to be filled. */
    public static final int MUFFLER_SLOTS = 12;

    /** 矿粉模式, the mode the machine starts in. */
    public static final int MODE_DUST = 0;
    /** 矿石模式. */
    public static final int MODE_ORE = 1;

    /** Parallels in dust mode before the coil bonus. */
    public static final int DUST_BASE_PARALLELS = 8;
    /** Parallels in ore mode, fixed. */
    public static final int ORE_PARALLELS = 256;

    /** Percent of the duration saved per energy hatch tier in dust mode. */
    private static final int DUST_TIME_PER_HATCH_TIER_PERCENT = 5;
    /** Percent of the energy saved per glass tier in dust mode. */
    private static final int DUST_ENERGY_PER_GLASS_TIER_PERCENT = 5;
    /** Percent of the duration saved per coil tier above the recipe's minimum in ore mode. */
    private static final int ORE_TIME_PER_COIL_TIER_PERCENT = 20;
    /** Percent of the energy saved per energy hatch tier above the recipe's in ore mode. */
    private static final int ORE_ENERGY_PER_HATCH_TIER_PERCENT = 15;

    /** Glass tier that unlocks lossless overclocking in ore mode. */
    private static final int ORE_LOSSLESS_GLASS_TIER = VoltageIndex.UHV;

    static {
        // The blueprint is checked as soon as this class is loaded, which is when the controller is registered, so a
        // blueprint that disagrees with the offsets above is reported while the log is still short. The definition
        // itself is still only built on the first structure check, see getDefinition().
        try {
            prepareStructure();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not check the Rare Earth Processing Complex structure blueprint.", t);
        }
    }

    /**
     * The one definition, built from the shape above the first time it is asked for, which is the first structure
     * check rather than registration: the heating coils and the structure glass come from other mods that only register
     * them during their own init, so a definition built any earlier would bind a glass chain that knows no glasses.
     */
    private static IStructureDefinition<MTERareEarthComplex> DEFINITION;

    /** Chemically Inert Machine Casings the last structure check found. */
    private int inertCasings;

    /** Heating coil level of the structure; {@link HeatingCoilLevel#None} until the machine forms. */
    private HeatingCoilLevel coilLevel = HeatingCoilLevel.None;

    /** Structure glass tier of the last structure check, or -1 when the structure has no tiered glass. */
    private int glassTier = -1;

    public MTERareEarthComplex(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTERareEarthComplex(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTERareEarthComplex(this.mName);
    }

    /**
     * Checks the blueprint against the machine's own offsets and writes what it asks for into the log. Safe to call at
     * registration: it does not build the definition, which has to wait for the other mods' coils and glasses.
     */
    public static void prepareStructure() {
        StructureBlueprintFile
            .verifyControllerPosition(STRUCTURE_FILE_PATH, shape, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        reportCounts();
    }

    /** Writes what the blueprint asks for into the log, and complains when that no longer matches the constants. */
    private static void reportCounts() {
        int mufflers = StructureBlueprintFile.count(shape, 'H');
        if (mufflers != MUFFLER_SLOTS) {
            MyMod.LOG.warn(
                "The Rare Earth Processing Complex blueprint draws {} muffler positions, but the machine requires {}.",
                mufflers,
                MUFFLER_SLOTS);
        }
        MyMod.LOG.info(
            "Rare Earth Processing Complex: the blueprint draws {} Chemically Inert Machine Casings and {} muffler "
                + "positions; the shell has to keep {} of the casings",
            StructureBlueprintFile.count(shape, 'A'),
            mufflers,
            MIN_INERT_CASINGS);
    }

    /** The structure definition, built from the blueprint the first time it is asked for. */
    private static IStructureDefinition<MTERareEarthComplex> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    /**
     * The StructureLib form of the blueprint.
     * <p>
     * Every symbol is a block or a casing, and the positions that take hatches put the hatch adder in front of the
     * casing, so such a position may hold either. The casing element is what counts the shell for
     * {@link #checkMachine}.
     */
    private static IStructureDefinition<MTERareEarthComplex> buildDefinition() {
        try {
            return StructureDefinition.<MTERareEarthComplex>builder()
                .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
                // The wall: every hatch this machine supports goes on the chemically inert casing.
                .addElement(
                    'A',
                    buildHatchAdder(MTERareEarthComplex.class)
                        .atLeast(Energy.or(ExoticEnergy), Maintenance, InputBus, InputHatch, OutputBus, OutputHatch)
                        .casingIndex(inertCasingTextureIndex())
                        .hint(1)
                        .buildAndChain(onElementPass(x -> ++x.inertCasings, ofBlock(GregTechAPI.sBlockCasings8, 0))))
                // The heating coils, which set the machine's coil level.
                .addElement(
                    'B',
                    GTStructureChannels.HEATING_COIL
                        .use(activeCoils(ofCoil(MTERareEarthComplex::setCoilLevel, MTERareEarthComplex::getCoilLevel))))
                // The structure glass, which sets the machine's glass tier.
                .addElement(
                    'C',
                    chainAllGlasses(-1, (te, tier) -> te.setGlassTier(tier), MTERareEarthComplex::getGlassTier))
                .addElement('D', ofFrame(Materials.BlackSteel))
                .addElement('E', ofBlock(GregTechAPI.sBlockCasings4, 0))
                .addElement('F', ofBlock(Blocks.iron_block, 0))
                .addElement('G', ofBlock(GregTechAPI.sBlockCasings4, 1))
                // A muffler position and nothing else: a plain casing is not accepted there.
                .addElement(
                    'H',
                    buildHatchAdder(MTERareEarthComplex.class).atLeast(Muffler)
                        .casingIndex(heatProofCasingTextureIndex())
                        .hint(3)
                        .build())
                // GT++ only creates its casings during its own pre-init, which runs after this machine is registered,
                // so the block is looked up lazily, at the first structure check rather than here.
                .addElement('I', lazy(t -> ofBlock(ModBlocks.blockCasingsMisc, 0)))
                .addElement('J', ofBlock(GregTechAPI.sBlockCasings1, 11))
                .addElement('K', ofBlock(GregTechAPI.sBlockCasings8, 1))
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Rare Earth Processing Complex structure definition; the machine falls back to a "
                    + "single block so the game keeps running.",
                t);
            return StructureDefinition.<MTERareEarthComplex>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "~" } })
                .build();
        }
    }

    @Override
    public IStructureDefinition<MTERareEarthComplex> getStructureDefinition() {
        return getDefinition();
    }

    /** Texture index of the chemically inert machine casing, i.e. of {@code sBlockCasings8:0}. */
    private static int inertCasingTextureIndex() {
        return ((BlockCasings8) GregTechAPI.sBlockCasings8).getTextureIndex(0);
    }

    /** Texture index of the heat proof machine casing, i.e. of {@code sBlockCasings1:11}. */
    private static int heatProofCasingTextureIndex() {
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(11);
    }

    public HeatingCoilLevel getCoilLevel() {
        return coilLevel;
    }

    public void setCoilLevel(HeatingCoilLevel aCoilLevel) {
        coilLevel = aCoilLevel;
    }

    public int getGlassTier() {
        return glassTier;
    }

    public void setGlassTier(int aGlassTier) {
        glassTier = aGlassTier;
    }

    /** True when the machine is in 矿石模式. */
    public boolean isOreMode() {
        return machineMode == MODE_ORE;
    }

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    /**
     * The machine's GUI, with the 矿粉模式 / 矿石模式 icons the mode switch button draws.
     * <p>
     * The icons have to be handed to the GUI itself: that list is what the button checks to decide whether the machine
     * has modes at all, so filling in the legacy {@code setMachineModeIcons()} alone leaves the button out of the
     * window. This is the same wiring the mega distillation tower uses.
     */
    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this).withMachineModeIcons(
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_SEPARATOR);
    }

    /**
     * Language key of the mode the machine is in, which is what the GUI shows next to the mode switch button.
     * <p>
     * Without it the machine reports "unknown mode", because the base class has no idea what the mode indices of this
     * machine mean. This is the same wiring the mega distillation tower uses.
     */
    @Override
    public String getMachineModeKey() {
        return isOreMode() ? "simplification.rare_earth.mode.ore" : "simplification.rare_earth.mode.dust";
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return isOreMode() ? ModRecipeMaps.rareEarthOreRecipes : ModRecipeMaps.rareEarthDustRecipes;
    }

    /**
     * Both pools, so that NEI lists this machine as the catalyst of the ore mode page as well as the dust mode one.
     * <p>
     * NEI builds its catalyst list by asking every machine which recipe maps it can use. A machine that only answers
     * with the map of its current mode is listed on that one page and nowhere else, which is what made the ore mode
     * look unbound. GT5U documents this method for machines that have several pools.
     */
    @Override
    public Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.asList(ModRecipeMaps.rareEarthDustRecipes, ModRecipeMaps.rareEarthOreRecipes);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        inertCasings = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkCasingMin(errors, inertCasings, MIN_INERT_CASINGS);
        // Every muffler position the blueprint draws has to be filled with one: the shape marks them all, so all of
        // them are required, not just one or two.
        checkHatchExact(errors, HatchElement.Muffler, MUFFLER_SLOTS);
        checkHasAnyEnergy(errors);
        checkOneMaintenanceHatch(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
    }

    /**
     * Runs the recipe logic, falling back to the other mode's pool when the current one has nothing.
     * <p>
     * The two pools are fed by different inputs, and the player should not have to guess which mode an input belongs
     * to, nor should the ore mode be invisible in recipe searches. Both pools are therefore always tried, and the
     * machine stays in whichever mode matched.
     */
    @Override
    public CheckRecipeResult checkProcessing() {
        CheckRecipeResult result = super.checkProcessing();
        if (result.wasSuccessful()) return result;
        int previous = machineMode;
        machineMode = isOreMode() ? MODE_DUST : MODE_ORE;
        CheckRecipeResult fallback = super.checkProcessing();
        if (fallback.wasSuccessful()) return fallback;
        machineMode = previous;
        return result;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
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
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { "\u00a7e\u7a00\u571f\u7efc\u5408\u5904\u7406\u00a7r / Rare Earth Processing Complex",
            "\u00a77The controller goes in the middle of the front wall. The shape is "
                + StructureBlueprintFile.width(shape)
                + " wide x "
                + StructureBlueprintFile.height(shape)
                + " tall x "
                + StructureBlueprintFile.depth(shape)
                + " deep.",
            "\u00a77A = Chemically Inert Machine Casing: every hatch goes here.",
            "\u00a77B = any heating coil, C = any tiered structure glass.",
            "\u00a77H = the muffler slots drawn in the blueprint.",
            "\u00a77Sneak-right-click the controller with the projector to build it automatically." };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
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

    /**
     * Parallels the machine runs at once, before the limit the player set on the power panel.
     * <p>
     * Dust mode doubles {@value #DUST_BASE_PARALLELS} for every coil tier; ore mode is a flat {@value #ORE_PARALLELS}.
     */
    @Override
    public int getMaxParallelRecipes() {
        if (isOreMode()) return ORE_PARALLELS;
        return DUST_BASE_PARALLELS << Math.max(0, coilTier());
    }

    /** Coil tier of the installed coils, or 0 when there are none. */
    private int coilTier() {
        return Math.max(0, coilLevel.getTier());
    }

    /** Voltage tier of the installed energy hatches, capped so the arithmetic below cannot overflow. */
    private int hatchTier() {
        long voltage = getAverageInputVoltage();
        if (voltage <= 0) return 0;
        int tier = (64 - Long.numberOfLeadingZeros(voltage) - 2) / 2;
        return Math.max(0, Math.min(tier, VoltageIndex.MAX));
    }

    /** Voltage tier of a recipe. */
    private static int recipeTier(GTRecipe recipe) {
        return Math
            .max(0, Math.min((64 - Long.numberOfLeadingZeros(Math.max(1, recipe.mEUt)) - 2) / 2, VoltageIndex.MAX));
    }

    /** Minimum coil heat a recipe asks for, from {@code COIL_HEAT} or the recipe's special value. */
    private static int requiredCoilHeat(GTRecipe recipe) {
        int heat = recipe.getMetadataOrDefault(GTRecipeConstants.COIL_HEAT, 0);
        return heat > 0 ? heat : recipe.mSpecialValue;
    }

    /** Coil tier a recipe asks for, read back from its minimum heat. */
    private static int requiredCoilTier(GTRecipe recipe) {
        int required = requiredCoilHeat(recipe);
        if (required <= 0) return 0;
        for (HeatingCoilLevel level : HeatingCoilLevel.values()) {
            if (level != HeatingCoilLevel.None && level.getHeat() >= required) return Math.max(0, level.getTier());
        }
        return HeatingCoilLevel.getMaxTier();
    }

    /** Fraction of the duration kept: dust mode saves per energy hatch tier, ore mode per coil tier over the recipe. */
    private double durationModifier(GTRecipe recipe) {
        int levels;
        int percentPerLevel;
        if (isOreMode()) {
            levels = Math.max(0, coilTier() - requiredCoilTier(recipe));
            percentPerLevel = ORE_TIME_PER_COIL_TIER_PERCENT;
        } else {
            levels = Math.max(0, hatchTier() - recipeTier(recipe));
            percentPerLevel = DUST_TIME_PER_HATCH_TIER_PERCENT;
        }
        return Math.max(0.05, 1.0 - levels * percentPerLevel / 100.0);
    }

    /** Fraction of the energy paid: dust mode saves per glass tier, ore mode per energy hatch tier over the recipe. */
    private double energyModifier(GTRecipe recipe) {
        int levels;
        int percentPerLevel;
        if (isOreMode()) {
            levels = Math.max(0, hatchTier() - recipeTier(recipe));
            percentPerLevel = ORE_ENERGY_PER_HATCH_TIER_PERCENT;
        } else {
            levels = Math.max(0, glassTier);
            percentPerLevel = DUST_ENERGY_PER_GLASS_TIER_PERCENT;
        }
        return Math.max(0.05, 1.0 - levels * percentPerLevel / 100.0);
    }

    /**
     * The recipe logic of the machine.
     * <p>
     * {@code setUnlimitedTierSkips()} is what lifts the usual "the recipe is a higher tier than the energy hatch"
     * refusal, as the design document asks for. The overclock ratio, the duration and the energy are then adjusted per
     * mode and per recipe, and ore mode recipes are refused outright while the coils are below what they ask for.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Override
            protected @Nonnull CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                if (isOreMode() && coilLevel.getHeat() < requiredCoilHeat(recipe)) {
                    return CheckRecipeResultRegistry.insufficientHeat(requiredCoilHeat(recipe));
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            protected @Nonnull OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                OverclockCalculator calculator = super.createOverclockCalculator(recipe);
                if (isOreMode()) {
                    boolean lossless = glassTier >= ORE_LOSSLESS_GLASS_TIER;
                    calculator.setDurationDecreasePerOC(lossless ? 4 : 2)
                        .setEUtIncreasePerOC(4);
                } else {
                    calculator.setDurationDecreasePerOC(4)
                        .setEUtIncreasePerOC(4);
                }
                return calculator.setDurationModifier(durationModifier(recipe))
                    .setEUtDiscount(energyModifier(recipe));
            }
        }.setMaxParallelSupplier(this::getTrueParallel)
            .setUnlimitedTierSkips();
    }

    /**
     * Input separation, the button GregTech draws next to the mode switch. The machine offers it and leaves the choice
     * to the player; unlike most GregTech multiblocks it starts with separation off, because these recipes take their
     * solids from several buses at once.
     */
    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean getDefaultInputSeparationMode() {
        return false;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
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
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("glassTier", glassTier);
        aNBT.setInteger("coilLevel", coilLevel.ordinal());
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        glassTier = aNBT.getInteger("glassTier");
        int coil = aNBT.getInteger("coilLevel");
        if (coil >= 0 && coil < HeatingCoilLevel.values().length) {
            coilLevel = HeatingCoilLevel.values()[coil];
        }
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        glassTier = -1;
        coilLevel = HeatingCoilLevel.None;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Rare Earth Processing Complex")
            .addInfo("Gathers most of the rare earth line into one machine, in an ore mode and a dust mode")
            .addInfo("It looks like a lot of the machinery is missing - how is all of it done in here??")
            .addInfo("Recipe voltage is not limited by the energy hatch tier")
            .addSeparator()
            .addInfo("\u00a76Dust mode\u00a77, the default:")
            .addStaticParallelInfo(DUST_BASE_PARALLELS)
            .addInfo("x2 parallels per heating coil tier")
            .addPerfectOCInfo()
            .addInfo("-" + DUST_TIME_PER_HATCH_TIER_PERCENT + "% duration per energy hatch tier")
            .addInfo("-" + DUST_ENERGY_PER_GLASS_TIER_PERCENT + "% energy per structure glass tier")
            .addSeparator()
            .addInfo("\u00a76Ore mode\u00a77:")
            .addStaticParallelInfo(ORE_PARALLELS)
            .addInfo("-" + ORE_TIME_PER_COIL_TIER_PERCENT + "% duration per coil tier over the recipe's")
            .addInfo("-" + ORE_ENERGY_PER_HATCH_TIER_PERCENT + "% energy per energy hatch tier over the recipe's")
            .addInfo("UHV structure glass unlocks lossless overclocking")
            .addSupportMultiAmp()
            .addSeparator()
            // beginStructureBlock takes width, height and depth in that order, all three read off the blueprint so an
            // edit to the structure keeps the tooltip correct.
            .beginStructureBlock(
                StructureBlueprintFile.width(shape),
                StructureBlueprintFile.height(shape),
                StructureBlueprintFile.depth(shape),
                false)
            .addController("Front wall, middle of the " + (VERTICAL_OFF_SET + 1) + "th level from the top")
            .addCasing(MIN_INERT_CASINGS + "+", "Chemically Inert Machine Casing", false)
            .addEnergyHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addMaintenanceHatch("1", "Any Chemically Inert Machine Casing", 1)
            .addInputHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addOutputHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addInputBus("1+", "Any Chemically Inert Machine Casing", 1)
            .addOutputBus("1+", "Any Chemically Inert Machine Casing", 1)
            .addMufflerHatch(String.valueOf(MUFFLER_SLOTS), "Every position marked H", 3)
            .addOtherStructurePart("Any heating coil", "The B positions, sets the coil tier")
            .addOtherStructurePart("Any tiered structure glass", "The C positions, sets the glass tier")
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .addSubChannel(GTStructureChannels.BOROGLASS)
            .toolTipFinisher();
        return tt;
    }
}
