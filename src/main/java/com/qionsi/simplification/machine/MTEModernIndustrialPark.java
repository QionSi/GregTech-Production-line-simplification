package com.qionsi.simplification.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR_GLOW;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
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
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.SoundResource;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoidingMode;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.tooltip.TooltipTier;
import gregtech.common.misc.GTStructureChannels;
import gtPlusPlus.core.block.ModBlocks;
import gtPlusPlus.core.material.MaterialsAlloy;

/**
 * 现代产业园 / Modern Industrial Park.
 * <p>
 * The whole ore processing line of a factory stuffed into one machine: the design document calls it "modern industry's
 * integration of a production line", and the joke in its own tooltip is that a great many machines appear to be
 * missing - they are all in here.
 *
 * <h2>What it does with power and parallels</h2>
 *
 * <ul>
 * <li>Every recipe can run at any voltage: the energy hatch tier never limits which recipe may start.</li>
 * <li>Overclocking is lossless - four times the EU/t for a quarter of the time, every step.</li>
 * <li>It starts at {@value #BASE_PARALLELS} parallels and every tier of the borosilicate glass in the walls doubles
 * that.</li>
 * <li>Every tier of the heating coils shaves {@value #TIME_PER_COIL_TIER_PERCENT}% off the duration.</li>
 * </ul>
 *
 * <h2>The structure</h2>
 *
 * A 41 x 7 x 26 flat factory made of forty different block types - the frames of five different alloys, ten machine
 * casings, the IsaMill and flotation plant casings, item and fluid pipe casings, the whole vocabulary of the GTNH ore
 * line. Two symbols are not fixed blocks but tiers:
 *
 * <ul>
 * <li>{@code A} is the borosilicate glass, and any tier is accepted; the tier the walls are built from is what sets the
 * parallel bonus. This is the StructureLib "glass" channel, the same one GT's industrial autoclave uses.</li>
 * <li>{@code T} is the heating coil, and any tier is accepted; the tier sets the duration bonus. This is the "coil"
 * channel, the same one GT's electric blast furnace uses.</li>
 * </ul>
 *
 * Both channels are advertised in the tooltip with {@code addSubChannel}, which is what puts the tier selector into the
 * NEI structure preview.
 */
public class MTEModernIndustrialPark extends MTEExtendedPowerMultiBlockBase<MTEModernIndustrialPark>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    private static final String BLUEPRINT = MyMod.MODID + ":multiblock/modern_industrial_park";

    private static final String[][] shape = StructureBlueprintFile.read(BLUEPRINT);

    /** Where the {@code ~} of the blueprint sits: column 39, level 5 from the top, right at the player side. */
    public static final int HORIZONTAL_OFF_SET = 38;
    public static final int VERTICAL_OFF_SET = 4;
    public static final int DEPTH_OFF_SET = 0;

    /** 化学惰性机械方块 / Chemically Inert Machine Casings the shell has to keep; every hatch goes on one. */
    public static final int MIN_INERT_CASINGS = 5;

    /** Parallels the machine starts with, before the glass bonus. */
    public static final int BASE_PARALLELS = 256;

    /** Percent of the duration the heating coils shave off per tier. */
    public static final int TIME_PER_COIL_TIER_PERCENT = 5;

    /** How many paralells each glass tier multiplies by. */
    public static final int PARALLELS_PER_GLASS_TIER = 2;

    /**
     * Most parallels the machine will ever report, so that {@code BASE_PARALLELS << tier} cannot overflow into a
     * negative number on an absurdly high glass tier.
     */
    private static final int MAX_PARALLELS = 33_554_432;

    /** Number of lines the tooltip takes from the language file; missing ones are skipped. */
    private static final int TOOLTIP_LINES = 12;

    private static IStructureDefinition<MTEModernIndustrialPark> DEFINITION;

    /** Chemically inert casings the last structure check found. */
    private int inertCasings;

    /** Heating coil level of the structure; {@link HeatingCoilLevel#None} until the machine forms. */
    private HeatingCoilLevel coilLevel = HeatingCoilLevel.None;

    /** Structure glass tier of the last structure check, or -1 when the structure has no tiered glass. */
    private int glassTier = -1;

    public MTEModernIndustrialPark(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        startAtOneParallel();
    }

    public MTEModernIndustrialPark(String aName) {
        super(aName);
        startAtOneParallel();
    }

    /**
     * The machine starts at one parallel and lets the player raise it in the power panel, up to the ceiling
     * {@link #getMaxParallelRecipes()} reports for the glass tier that is actually built in.
     */
    private void startAtOneParallel() {
        alwaysMaxParallel = false;
        powerPanelMaxParallel = 1;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEModernIndustrialPark(this.mName);
    }

    // ------------------------------------------------------------------ structure

    /** Checks the blueprint against the machine's offsets and writes what it asks for into the log. */
    public static void prepareStructure() {
        StructureBlueprintFile
            .verifyControllerPosition(BLUEPRINT, shape, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        MyMod.LOG.info(
            "Modern Industrial Park: the blueprint is {} wide x {} tall x {} deep and draws {} chemically inert "
                + "casings, {} borosilicate glass, {} heating coil blocks, {} clean stainless steel casings and {} "
                + "Eglin Steel frames; the shell has to keep {} of the inert casings",
            StructureBlueprintFile.width(shape),
            StructureBlueprintFile.height(shape),
            StructureBlueprintFile.depth(shape),
            StructureBlueprintFile.count(shape, 'U'),
            StructureBlueprintFile.count(shape, 'A'),
            StructureBlueprintFile.count(shape, 'T'),
            StructureBlueprintFile.count(shape, 'R'),
            StructureBlueprintFile.count(shape, 'B'),
            MIN_INERT_CASINGS);
        // Ask GT++ for each of the five alloy frames here, at registration, so that a wrong block shows up as a log
        // line at startup instead of as "the machine will not form" hours later.
        framesReported = false;
        frameBox(MaterialsAlloy.EGLIN_STEEL, "Eglin Steel");
        frameBox(MaterialsAlloy.INCONEL_690, "Inconel-690");
        frameBox(MaterialsAlloy.POTIN, "Potin");
        frameBox(MaterialsAlloy.STABALLOY, "Staballoy");
        frameBox(MaterialsAlloy.AQUATIC_STEEL, "Watertight Steel");
        framesReported = true;
    }

    private static IStructureDefinition<MTEModernIndustrialPark> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    /**
     * The forty symbols of the blueprint.
     * <p>
     * Most of them are GregTech casings and are taken straight out of the {@link Casings} enum, which is also what
     * gives each one its texture index. The five alloy frames come from GT++, whose blocks are looked up by their
     * registry name at the first structure check (GT++ builds them during its own pre-initialization).
     * <p>
     * Two symbols are tiered rather than fixed: {@code A} is any borosilicate glass from the "glass" channel and
     * {@code T} is any heating coil from the "coil" channel. Both hand their tier back to the machine through a
     * setter, which is what the parallel and duration bonuses are read from.
     */
    private static IStructureDefinition<MTEModernIndustrialPark> buildDefinition() {
        try {
            return StructureDefinition.<MTEModernIndustrialPark>builder()
                .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
                // A: 硼玻璃 / borosilicate glass, any tier. This is the "glass" channel.
                .addElement(
                    'A',
                    GTStructureChannels.BOROGLASS.use(
                        chainAllGlasses(
                            -1,
                            (te, tier) -> te.setGlassTier(tier),
                            MTEModernIndustrialPark::getGlassTier)))
                // T: 线圈方块 / heating coil, any tier. This is the "coil" channel.
                .addElement(
                    'T',
                    GTStructureChannels.HEATING_COIL.use(
                        activeCoils(
                            ofCoil(MTEModernIndustrialPark::setCoilLevel, MTEModernIndustrialPark::getCoilLevel))))
                // U: 化学惰性机械方块 / Chemically Inert Machine Casing - the only place a hatch may go.
                .addElement(
                    'U',
                    buildHatchAdder(MTEModernIndustrialPark.class)
                        .atLeast(Energy.or(ExoticEnergy), Maintenance, InputBus, InputHatch, OutputBus, OutputHatch)
                        .casingIndex(Casings.ChemicallyInertMachineCasing.getTextureId())
                        .hint(1)
                        .buildAndChain(
                            onElementPass(x -> ++x.inertCasings, Casings.ChemicallyInertMachineCasing.asElement())))
                // G..S, V: GregTech machine casings.
                .addElement('G', Casings.HeatProofMachineCasing.asElement())
                .addElement('H', Casings.PressureContainmentCasing.asElement())
                .addElement('I', Casings.TinItemPipeCasing.asElement())
                .addElement('J', Casings.BrassItemPipeCasing.asElement())
                .addElement('K', Casings.MixerCasing.asElement())
                .addElement('L', Casings.FrostProofMachineCasing.asElement())
                .addElement('M', Casings.SteelGearBoxCasing.asElement())
                .addElement('N', Casings.ProcessorMachineCasing.asElement())
                .addElement('O', Casings.BronzePipeCasing.asElement())
                .addElement('P', Casings.SteelPipeCasing.asElement())
                .addElement('Q', Casings.GrateMachineCasing.asElement())
                .addElement('R', Casings.CleanStainlessSteelMachineCasing.asElement())
                .addElement('S', Casings.TitaniumTurbineCasing.asElement())
                .addElement('V', Casings.PTFEPipeCasing.asElement())
                // 2..8, a..c: GT++ machine casings.
                .addElement('2', Casings.WashPlantCasing.asElement())
                .addElement('3', Casings.LargeSieveGrate.asElement())
                .addElement('4', Casings.InconelReinforcedCasing.asElement())
                .addElement('5', Casings.VacuumCasing.asElement())
                .addElement('6', Casings.IsaMillExteriorCasing.asElement())
                .addElement('7', Casings.IsaMillPiping.asElement())
                .addElement('8', Casings.IsaMillGearboxCasing.asElement())
                .addElement('a', Casings.CentrifugeCasing.asElement())
                .addElement('b', Casings.ElectrolyzerCasing.asElement())
                .addElement('c', Casings.MacerationStackCasing.asElement())
                // 9: 浮选机机械方块 / flotation plant casing.
                .addElement('9', lazy(t -> ofBlock(ModBlocks.blockSpecialMultiCasings, 9)))
                // B..F: GT++ alloy frames, resolved by registry name at the first structure check.
                // B..F: GT++ alloy frames. Each belongs to one GT++ material, which creates its frame block during
                // GT++'s own pre-initialization; ofFrame takes a supplier and resolves it on the first structure walk,
                // which is exactly the point where GT++ is guaranteed to have run.
                .addElement('B', ofFrame(() -> frameBox(MaterialsAlloy.EGLIN_STEEL, "Eglin Steel")))
                .addElement('C', ofFrame(() -> frameBox(MaterialsAlloy.INCONEL_690, "Inconel-690")))
                .addElement('D', ofFrame(() -> frameBox(MaterialsAlloy.POTIN, "Potin")))
                .addElement('E', ofFrame(() -> frameBox(MaterialsAlloy.STABALLOY, "Staballoy")))
                .addElement('F', ofFrame(() -> frameBox(MaterialsAlloy.AQUATIC_STEEL, "Watertight Steel")))
                // W, X, Y: GT frame boxes, by their exact metadata as the blueprint export lists them.
                .addElement('W', ofBlock(GregTechAPI.sBlockFrames, 305))
                .addElement('X', ofBlock(GregTechAPI.sBlockFrames, 372))
                .addElement('Y', ofBlock(GregTechAPI.sBlockFrames, 473))
                // Z: 铜块 / copper block, 0: 锌块 / zinc block, 1: 钨板金属 / tungsten sheetmetal block.
                .addElement('Z', ofBlock(GregTechAPI.sBlockMetal2, 7))
                .addElement('0', ofBlock(GregTechAPI.sBlockMetal8, 6))
                .addElement('1', ofBlock(GregTechAPI.sBlockSheetmetalGT, 81))
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Modern Industrial Park structure definition; the machine falls back to a single "
                    + "block so the game keeps running.",
                t);
            return StructureDefinition.<MTEModernIndustrialPark>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "~" } })
                .build();
        }
    }

    /** Set once the five alloy frames have been reported, so the log gets one line each and not one per walk. */
    private static boolean framesReported;

    /**
     * The frame box item of one GT++ alloy.
     * <p>
     * GT++ creates the frame block of a material together with the rest of that material's components, during its own
     * pre-initialization. Asking the material for its frame box is therefore the only reliable way to name it - the
     * block's display name ({@code block.EglinSteel.frame}) is not its registry name, which is what an earlier version
     * of this machine got wrong and why all five positions quietly accepted iron blocks.
     * <p>
     * A material that has no frame box falls back to a real block rather than a null one, because StructureLib would
     * crash the NEI preview on a null block, and says so in the log.
     */
    private static ItemStack frameBox(gtPlusPlus.core.material.Material material, String label) {
        ItemStack stack = null;
        try {
            stack = material.getFrameBox(1);
        } catch (Throwable t) {
            MyMod.LOG.error("Could not read the frame box of the GT++ material " + label, t);
        }
        if (stack == null || stack.getItem() == null) {
            MyMod.LOG.error(
                "GT++ has no frame box for {}; the {} positions of the Modern Industrial Park accept iron blocks "
                    + "until that is fixed.",
                label,
                label);
            return new ItemStack(net.minecraft.init.Blocks.iron_block);
        }
        if (!framesReported) {
            MyMod.LOG.info(
                "Modern Industrial Park: {} frame = {} ({}:{})",
                label,
                stack.getDisplayName(),
                net.minecraft.block.Block.blockRegistry
                    .getNameForObject(net.minecraft.block.Block.getBlockFromItem(stack.getItem())),
                stack.getItemDamage());
        }
        return stack;
    }

    @Override
    public IStructureDefinition<MTEModernIndustrialPark> getStructureDefinition() {
        return getDefinition();
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

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        inertCasings = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkCasingMin(errors, inertCasings, MIN_INERT_CASINGS);
        checkOneMaintenanceHatch(errors);
        checkHasAnyEnergy(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
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
        return new String[] { "\u00a7e\u73b0\u4ee3\u4ea7\u4e1a\u56ed\u00a7r / Modern Industrial Park",
            "\u00a77The controller goes in the front face, level " + (VERTICAL_OFF_SET + 1)
                + " from the top. The shape is "
                + StructureBlueprintFile.width(shape)
                + " wide x "
                + StructureBlueprintFile.height(shape)
                + " tall x "
                + StructureBlueprintFile.depth(shape)
                + " deep.",
            "\u00a77A = any borosilicate glass (the 'glass' channel: each tier doubles the parallels).",
            "\u00a77T = any heating coil (the 'coil' channel: each tier saves " + TIME_PER_COIL_TIER_PERCENT
                + "% of the duration).",
            "\u00a77U = Chemically Inert Machine Casing: every hatch goes on a U position.",
            "\u00a77Sneak-right-click the controller with the projector to build it automatically." };
    }

    // ------------------------------------------------------------------ running

    @Override
    public RecipeMap<?> getRecipeMap() {
        return ModRecipeMaps.modernIndustrialParkRecipes;
    }

    /** Coil tier of the installed coils, or 0 when there are none. */
    private int coilTier() {
        return Math.max(0, coilLevel.getTier());
    }

    /** The tier the borosilicate glass is built from, 0 when there is none. */
    private int glassTierForParallels() {
        return Math.max(0, glassTier);
    }

    /**
     * The ceiling the power panel offers: {@value #BASE_PARALLELS} parallels, doubled for every tier of the
     * borosilicate glass in the walls, clamped so the shift cannot overflow.
     */
    @Override
    public int getMaxParallelRecipes() {
        int tier = glassTierForParallels();
        if (tier > 20) return MAX_PARALLELS;
        long parallels = (long) BASE_PARALLELS << tier;
        return (int) Math.min(MAX_PARALLELS, Math.max(BASE_PARALLELS, parallels));
    }

    /**
     * The recipe logic: lossless overclocking, unlimited tier skips, and a duration that the heating coils shorten by
     * {@value #TIME_PER_COIL_TIER_PERCENT}% per tier.
     * <p>
     * The modifier is floored at 5% the way the other machines in this mod floor theirs: a duration of zero would let
     * the machine finish a recipe every tick and stall the server.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Override
            protected @Nonnull OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                return super.createOverclockCalculator(recipe).setDurationModifier(durationModifier());
            }
        }.setMaxParallelSupplier(this::getTrueParallel)
            .enablePerfectOverclock()
            .setUnlimitedTierSkips();
    }

    /** Fraction of the duration kept: the heating coils shave a flat percentage off per tier. */
    private double durationModifier() {
        return Math.max(0.05, 1.0 - coilTier() * TIME_PER_COIL_TIER_PERCENT / 100.0);
    }

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

    // ------------------------------------------------------------------ looks

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        // The same face as the Mega Chemical Reactor, as the design asks for.
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR,
            OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR_GLOW,
            OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR_ACTIVE,
            OVERLAY_FRONT_MEGA_CHEMICAL_REACTOR_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.ChemicallyInertMachineCasing.getCasingTexture();
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Modern Industrial Park");
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            String key = "simplification.tooltip.modernIndustrialPark." + line;
            if (StatCollector.canTranslate(key)) tt.addInfo(StatCollector.translateToLocal(key));
        }
        tt.addSeparator()
            .addStaticParallelInfo(BASE_PARALLELS)
            .addDynamicMultiplicativeParallelInfo(PARALLELS_PER_GLASS_TIER, TooltipTier.GLASS)
            .addInfo("+" + TIME_PER_COIL_TIER_PERCENT + "% recipe time saved per heating coil tier")
            .addPerfectOCInfo()
            .addUnlimitedTierSkips()
            .addSupportMultiAmp()
            .addSeparator()
            .beginStructureBlock(
                StructureBlueprintFile.width(shape),
                StructureBlueprintFile.height(shape),
                StructureBlueprintFile.depth(shape),
                false)
            .addController(
                "Front face, level " + (VERTICAL_OFF_SET + 1) + " from the top, column " + (HORIZONTAL_OFF_SET + 1))
            .addCasing(MIN_INERT_CASINGS + "+", "Chemically Inert Machine Casing", false)
            .addEnergyHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addMaintenanceHatch("1", "Any Chemically Inert Machine Casing", 1)
            .addInputHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addOutputHatch("1+", "Any Chemically Inert Machine Casing", 1)
            .addInputBus("1+", "Any Chemically Inert Machine Casing", 1)
            .addOutputBus("1+", "Any Chemically Inert Machine Casing", 1)
            .addOtherStructurePart("Any borosilicate glass", "The A positions, sets the parallel bonus")
            .addOtherStructurePart("Any heating coil", "The T positions, sets the duration bonus")
            .addSubChannel(GTStructureChannels.BOROGLASS)
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .toolTipFinisher();
        return tt;
    }
}
