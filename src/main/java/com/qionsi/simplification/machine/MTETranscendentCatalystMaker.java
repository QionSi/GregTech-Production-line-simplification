package com.qionsi.simplification.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_ON;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FUSION1_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.recipe.ModRecipeMaps;
import com.qionsi.simplification.recipe.TranscendentCatalystRecipes;
import com.qionsi.simplification.recipe.TranscendentCatalystRecipes.Coolant;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEWirelessEnergy;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 超维度催化剂制造机 / Transcendent Catalyst Maker.
 * <p>
 * Heats dusts and gases with superconducting coils until they turn into one of the five dimensionally transcendent
 * catalysts. The shape lives in the blueprint
 * {@code assets/simplification/multiblock/transcendent_catalyst_maker.mb}, which {@link StructureBlueprintFile} reads
 * at
 * runtime.
 *
 * <h2>What is special about it</h2>
 *
 * <ul>
 * <li>It draws power from the wireless network only: an ordinary energy hatch, a multi amp hatch or a laser target
 * hatch is refused while the structure is being checked, and the player is told why.</li>
 * <li>The number of parallels is picked in the GUI rather than derived from the hatches, in powers of two from
 * {@value #MIN_PARALLEL_POWER} to {@value #MAX_PARALLEL_POWER}.</li>
 * <li>Recipe voltage is never capped by the tier of the energy hatch.</li>
 * <li>Hatches are kept apart by role: the recipe fluids go in on the dimensional injection casings, everything that
 * comes out and the item buses go on the transcendent casings, the maintenance hatch sits on an injection casing, and
 * the dimensional bridges are reserved for the coolants (which are not part of any recipe).</li>
 * </ul>
 */
public class MTETranscendentCatalystMaker extends MTEExtendedPowerMultiBlockBase<MTETranscendentCatalystMaker>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** The blueprint this machine is built from. */
    private static final String STRUCTURE_FILE_PATH = MyMod.MODID + ":multiblock/transcendent_catalyst_maker";

    /**
     * The shape, read from the blueprint: one line per level with the top level first and one comma separated cell per
     * depth position with the one nearest the player first. See {@link StructureBlueprintFile} for the exact format.
     */
    private static final String[][] shape = StructureBlueprintFile.read(STRUCTURE_FILE_PATH);

    /** Where the {@code ~} of the blueprint sits, which is where the controller goes. */
    private static final int HORIZONTAL_OFF_SET = 6;
    private static final int VERTICAL_OFF_SET = 6;
    private static final int DEPTH_OFF_SET = 0;

    /** 超维度机械方块 the shell has to keep; the rest may be a hatch instead. */
    public static final int MIN_TRANSCENDENT_CASINGS = 11;

    /** 维度注入机械方块 the shell has to keep. */
    public static final int MIN_INJECTION_CASINGS = 5;

    /** The parallel count is two to the power of the machine mode, between these two powers. */
    private static final int MIN_PARALLEL_POWER = 0;
    private static final int MAX_PARALLEL_POWER = 6;

    /** Number of lines the tooltip takes from the language file; missing ones are skipped. */
    private static final int TOOLTIP_LINES = 24;

    private static IStructureDefinition<MTETranscendentCatalystMaker> DEFINITION;

    private static final ITexture CASING = Casings.DimensionallyTranscendentCasing.getCasingTexture();
    private static final ITexture OVERLAY_ACTIVE = TextureFactory.builder()
        .addIcon(OVERLAY_DTPF_ON)
        .extFacing()
        .build();
    private static final ITexture OVERLAY_ACTIVE_GLOW = TextureFactory.builder()
        .addIcon(OVERLAY_FUSION1_GLOW)
        .extFacing()
        .glow()
        .build();
    private static final ITexture OVERLAY_IDLE = TextureFactory.builder()
        .addIcon(OVERLAY_DTPF_OFF)
        .extFacing()
        .build();

    /** How many transcendent casings the last structure check found. */
    private int transcendentCasings;
    /** How many dimensional injection casings the last structure check found. */
    private int injectionCasings;

    public MTETranscendentCatalystMaker(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTETranscendentCatalystMaker(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTETranscendentCatalystMaker(this.mName);
    }

    /**
     * Checks the blueprint against the machine's own offsets and writes what it asks for into the log. Safe to call at
     * registration; the definition itself is built later, on the first structure check.
     */
    public static void prepareStructure() {
        StructureBlueprintFile
            .verifyControllerPosition(STRUCTURE_FILE_PATH, shape, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        MyMod.LOG.info(
            "Transcendent Catalyst Maker: the blueprint draws {} transcendent casings, {} injection casings, {} bridge "
                + "casings, {} black plutonium blocks, {} neutronium frames and {} superconducting coil blocks; the "
                + "shell has to keep {} and {} of the first two",
            StructureBlueprintFile.count(shape, 'B'),
            StructureBlueprintFile.count(shape, 'A'),
            StructureBlueprintFile.count(shape, 'C'),
            StructureBlueprintFile.count(shape, 'D'),
            StructureBlueprintFile.count(shape, 'E'),
            StructureBlueprintFile.count(shape, 'F'),
            MIN_TRANSCENDENT_CASINGS,
            MIN_INJECTION_CASINGS);
    }

    private static IStructureDefinition<MTETranscendentCatalystMaker> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    private static IStructureDefinition<MTETranscendentCatalystMaker> buildDefinition() {
        try {
            return StructureDefinition.<MTETranscendentCatalystMaker>builder()
                .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
                // 维度注入机械方块: the recipe fluids come in here, and so does the maintenance hatch.
                .addElement(
                    'A',
                    buildHatchAdder(MTETranscendentCatalystMaker.class).atLeast(InputHatch, Maintenance)
                        .casingIndex(Casings.DimensionalInjectionCasing.getTextureId())
                        .hint(1)
                        .buildAndChain(
                            onElementPass(x -> ++x.injectionCasings, Casings.DimensionalInjectionCasing.asElement())))
                // 超维度机械方块: everything that leaves the machine plus the item buses, and the only place an energy
                // hatch may sit.
                .addElement(
                    'B',
                    buildHatchAdder(MTETranscendentCatalystMaker.class)
                        .atLeast(Energy, InputBus, OutputBus, OutputHatch)
                        .casingIndex(Casings.DimensionallyTranscendentCasing.getTextureId())
                        .hint(2)
                        .buildAndChain(
                            onElementPass(
                                x -> ++x.transcendentCasings,
                                Casings.DimensionallyTranscendentCasing.asElement())))
                // 维度桥接方块: reserved for the coolants, which no recipe asks for.
                .addElement(
                    'C',
                    buildHatchAdder(MTETranscendentCatalystMaker.class).atLeast(InputHatch)
                        .casingIndex(Casings.DimensionalBridge.getTextureId())
                        .hint(3)
                        .buildAndChain(Casings.DimensionalBridge.asElement()))
                // 黑钚块 comes from New Horizons Core Mod, which registers its blocks during its own pre-init, so it is
                // looked up lazily at the first structure check instead of here.
                .addElement('D', lazy(t -> ofBlock(GameRegistry.findBlock("dreamcraft", "blockBlackPlutonium"), 0)))
                .addElement('E', ofFrame(Materials.Neutronium))
                .addElement('F', Casings.SuperconductingCoilBlock.asElement())
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Transcendent Catalyst Maker structure definition; the machine falls back to a "
                    + "single block so the game keeps running.",
                t);
            return StructureDefinition.<MTETranscendentCatalystMaker>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "~" } })
                .build();
        }
    }

    @Override
    public IStructureDefinition<MTETranscendentCatalystMaker> getStructureDefinition() {
        return getDefinition();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        injectionCasings = 0;
        transcendentCasings = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkCasingMin(errors, transcendentCasings, MIN_TRANSCENDENT_CASINGS);
        checkCasingMin(errors, injectionCasings, MIN_INJECTION_CASINGS);
        checkOneMaintenanceHatch(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkWirelessEnergyOnly(errors);
    }

    /**
     * Refuses every energy hatch that is not a wireless one.
     * <p>
     * The machine is meant to be run off the wireless network only, so an ordinary energy hatch, a multi amp hatch or a
     * laser target hatch is a structure error rather than something that silently does nothing.
     */
    private void checkWirelessEnergyOnly(List<StructureError> errors) {
        if (mEnergyHatches.isEmpty()) {
            errors.add(StructureErrors.of("simplification.structure.error.wireless_energy_only"));
            return;
        }
        for (MTEHatchEnergy hatch : mEnergyHatches) {
            if (!(hatch instanceof MTEWirelessEnergy)) {
                errors.add(StructureErrors.of("simplification.structure.error.wireless_energy_only"));
                return;
            }
        }
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
    public RecipeMap<?> getRecipeMap() {
        return ModRecipeMaps.transcendentCatalystRecipes;
    }

    /** Parallels are two to the power of the machine mode the player picked in the GUI. */
    @Override
    public int getMaxParallelRecipes() {
        return 1 << machineMode;
    }

    /**
     * Runs the recipe, but only after one of the coolants has been picked at random and found to be available.
     * <p>
     * The coolants are not part of any recipe: the machine burns one of the four per run, and which one is decided when
     * the run starts. It is consumed from the input hatches on the dimensional bridges. If it is not there in the
     * amount
     * the recipe needs, the run does not start at all and the recipe's own inputs are left alone.
     */
    @Override
    public CheckRecipeResult checkProcessing() {
        Coolant coolant = TranscendentCatalystRecipes.pickCoolant();
        if (coolant == null) return super.checkProcessing();

        FluidStack wanted = coolant.fluid.copy();
        wanted.amount = coolant.amount * Math.max(1, getTrueParallel());
        if (!hasCoolant(wanted)) return CheckRecipeResultRegistry.NO_RECIPE;

        CheckRecipeResult result = super.checkProcessing();
        if (result.wasSuccessful()) depleteInput(wanted);
        return result;
    }

    /** True when the input hatches together hold at least this much of the fluid. */
    private boolean hasCoolant(FluidStack wanted) {
        int found = 0;
        for (MTEHatchInput hatch : mInputHatches) {
            FluidStack inHatch = hatch.getFluid();
            if (inHatch != null && GTUtility.areFluidsEqual(inHatch, wanted)) found += inHatch.amount;
        }
        return found >= wanted.amount;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] {
            "\u00a7e\u8d85\u7ef4\u5ea6\u50ac\u5316\u5242\u5236\u9020\u673a\u00a7r / Transcendent Catalyst Maker",
            "\u00a77The controller goes in the middle of the front wall. The shape is "
                + StructureBlueprintFile.width(shape)
                + " wide x "
                + StructureBlueprintFile.height(shape)
                + " tall x "
                + StructureBlueprintFile.depth(shape)
                + " deep.",
            "\u00a77A = Dimensional Injection Casing: recipe fluid inputs and the maintenance hatch.",
            "\u00a77B = Dimensionally Transcendent Casing: wireless energy hatch, item buses and outputs.",
            "\u00a77C = Dimensional Bridge: the coolant inputs.",
            "\u00a77D = black plutonium block, E = neutronium frame, F = superconducting coil block." };
    }

    /**
     * The parallel count is picked with the mode button in the GUI: mode {@code n} is {@code 2^n} parallels, from one
     * to sixty four. The base class stores the mode with the machine, so the choice survives a reload without any NBT
     * of our own.
     */
    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public void setMachineMode(int aMode) {
        machineMode = Math.max(MIN_PARALLEL_POWER, Math.min(MAX_PARALLEL_POWER, aMode));
    }

    /**
     * The machine's GUI, with one mode icon per selectable parallel count.
     * <p>
     * The icons have to be handed to the GUI itself: that list is what the button checks to decide whether the machine
     * has modes at all, so filling in the legacy {@code setMachineModeIcons()} alone leaves the button out of the
     * window. The icons are all the same here because what matters is the label, which reports the parallel count.
     */
    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this).withMachineModeIcons(
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT);
    }

    /**
     * Language key of the mode, which is what the GUI prints next to the button: mode {@code n} is {@code 2^n}
     * parallels.
     */
    @Override
    public String getMachineModeKey() {
        return "simplification.transcendent_catalyst_maker.parallel." + machineMode;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Transcendent Catalyst Maker");
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            String key = "simplification.tooltip.transcendentCatalystMaker." + line;
            if (StatCollector.canTranslate(key)) tt.addInfo(StatCollector.translateToLocal(key));
        }
        // beginStructureBlock takes width, height and depth in that order, all three read off the blueprint so that an
        // edit to the structure keeps the tooltip correct.
        tt.beginStructureBlock(
            StructureBlueprintFile.width(shape),
            StructureBlueprintFile.height(shape),
            StructureBlueprintFile.depth(shape),
            true)
            .addController("Front wall, middle of the " + (VERTICAL_OFF_SET + 1) + "th level from the top")
            .addCasing(MIN_TRANSCENDENT_CASINGS + "+", "Dimensionally Transcendent Casing", false)
            .addCasing(MIN_INJECTION_CASINGS + "+", "Dimensional Injection Casing", false)
            .addEnergyHatch("1+", "Any Dimensionally Transcendent Casing - wireless hatches only", 2)
            .addMaintenanceHatch("1", "Any Dimensional Injection Casing", 1)
            .addInputHatch("1+", "Recipe fluids: any Dimensional Injection Casing", 1)
            .addInputHatch("1+", "Coolants: any Dimensional Bridge", 3)
            .addOutputHatch("1+", "Any Dimensionally Transcendent Casing", 2)
            .addInputBus("1+", "Any Dimensionally Transcendent Casing", 2)
            .addOutputBus("1+", "Any Dimensionally Transcendent Casing", 2)
            .addOtherStructurePart("Dimensional Bridge", "The C positions, reserved for the coolants")
            .addOtherStructurePart("Superconducting Coil Block", "The F positions")
            .toolTipFinisher();
        return tt;
    }

    // The unused parameters are part of the interface.
    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side != facing) return new ITexture[] { CASING };
        return active ? new ITexture[] { CASING, OVERLAY_ACTIVE, OVERLAY_ACTIVE_GLOW }
            : new ITexture[] { CASING, OVERLAY_IDLE };
    }
}
