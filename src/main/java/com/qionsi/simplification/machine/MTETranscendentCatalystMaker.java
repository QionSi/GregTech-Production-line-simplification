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

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.dreammaster.block.BlockList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.recipe.ModRecipeMaps;
import com.qionsi.simplification.recipe.TranscendentCatalystRecipes;
import com.qionsi.simplification.recipe.TranscendentCatalystRecipes.Coolant;

import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEWirelessEnergy;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

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
 * <li>The number of parallels is typed into the machine's power panel rather than derived from the hatches: any whole
 * number from {@value #MIN_PARALLEL} to {@value #MAX_PARALLEL}, {@value #MIN_PARALLEL} until the player says otherwise,
 * and kept with the machine.</li>
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

    /**
     * The parallel count the player can set: one to {@value #MAX_PARALLEL} (33,554,432 = 2^25). {@value #MIN_PARALLEL}
     * is the default and the floor, and a count that came out of a save outside the range is brought back into it, so
     * the effective count can never be zero or negative.
     */
    public static final int MIN_PARALLEL = 1;
    public static final int MAX_PARALLEL = 33_554_432;

    /**
     * Marks how a save stores the parallel count. Version 2 is the power panel's own text box
     * ({@code powerPanelMaxParallel}) plus the {@code alwaysMaxParallel} flag; version 1 and earlier stored the count
     * in the machine mode. The marker is what lets a save written before the rework be recognised and started over at
     * {@value #MIN_PARALLEL} instead of being read as "use the maximum".
     */
    private static final String NBT_PARALLEL_VERSION = "simplificationParallelVersion";
    private static final int PARALLEL_VERSION = 2;

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
        startAtOneParallel();
    }

    public MTETranscendentCatalystMaker(String aName) {
        super(aName);
        startAtOneParallel();
    }

    /**
     * Sets the machine up so that its parallel count is the player's own number, starting at {@value #MIN_PARALLEL}.
     * <p>
     * GregTech's power panel has two halves: a text box holding {@code powerPanelMaxParallel}, which is what the player
     * types, and a "always use the maximum" checkbox holding {@code alwaysMaxParallel}, which makes the machine ignore
     * the box and run at {@link #getMaxParallelRecipes()} - here {@value #MAX_PARALLEL} recipes. The checkbox is off
     * and
     * the box is at one when a machine is first placed, so a freshly built machine runs one recipe at a time until its
     * owner says otherwise.
     */
    private void startAtOneParallel() {
        alwaysMaxParallel = false;
        powerPanelMaxParallel = MIN_PARALLEL;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger(NBT_PARALLEL_VERSION, PARALLEL_VERSION);
    }

    /**
     * Reads the parallel count back, and repairs the two ways a save can disagree with the current machine.
     * <p>
     * A save written before the parallel rework has no version marker: it kept the count as a machine mode of 0..6 and
     * left {@code alwaysMaxParallel} at GregTech's default of {@code true}, which against today's maximum would mean
     * "run {@value #MAX_PARALLEL} recipes at a time". Such a save is started over at {@value #MIN_PARALLEL}. A save
     * that does have the marker keeps its number, but never outside {@value #MIN_PARALLEL} to {@value #MAX_PARALLEL}:
     * {@code getTrueParallel()} floors the result at one anyway, and the coolant a run burns is multiplied by this
     * number, so a stored zero or a negative one is not something to pass on.
     */
    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.getInteger(NBT_PARALLEL_VERSION) < PARALLEL_VERSION) {
            MyMod.LOG.info(
                "Transcendent Catalyst Maker: this machine was saved before the parallel count became a typed-in "
                    + "number (save version {}); its parallel count starts over at {}.",
                aNBT.getInteger(NBT_PARALLEL_VERSION),
                MIN_PARALLEL);
            startAtOneParallel();
        }
        if (powerPanelMaxParallel < MIN_PARALLEL || powerPanelMaxParallel > MAX_PARALLEL) {
            int clamped = GTUtility.clamp(powerPanelMaxParallel, MIN_PARALLEL, MAX_PARALLEL);
            MyMod.LOG.info(
                "Transcendent Catalyst Maker: the stored parallel count {} is outside the allowed range {} to {}; it "
                    + "is brought back to {}.",
                powerPanelMaxParallel,
                MIN_PARALLEL,
                MAX_PARALLEL,
                clamped);
            powerPanelMaxParallel = clamped;
        }
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
        MyMod.LOG.info(
            "Transcendent Catalyst Maker: parallel count is the number typed into the power panel - allowed range {} "
                + "to {} (getMaxParallelRecipes() returns {} as the ceiling, the player's own number is "
                + "powerPanelMaxParallel and getTrueParallel() is what the run uses) - it starts at {} and is stored "
                + "with the machine.",
            MIN_PARALLEL,
            MAX_PARALLEL,
            MAX_PARALLEL,
            MIN_PARALLEL);
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
                // 黑钚块 comes from New Horizons Core Mod, which only fills its block list during its own pre-init, so
                // it is resolved when the structure is first walked. Handing StructureLib a null block here used to
                // crash the NEI structure preview, so the fallback is a real block plus a loud log line.
                .addElement('D', lazy(t -> blackPlutoniumElement()))
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

    /**
     * The structure element for the {@code D} positions: the black plutonium block of New Horizons Core Mod.
     * <p>
     * That mod keeps its blocks in the {@link BlockList} enum and only fills the entries in during its own pre-init, so
     * the lookup happens here, at the first walk of the structure, rather than while this class is being loaded. A null
     * block would make StructureLib throw while the NEI preview builds the machine - which is exactly what it used to
     * do - so a block that could not be resolved falls back to a real one and says so in the log.
     */
    private static IStructureElement<MTETranscendentCatalystMaker> blackPlutoniumElement() {
        ItemStack stack = null;
        try {
            stack = BlockList.BlackPlutonium.get();
        } catch (Throwable t) {
            MyMod.LOG.error("Could not read the black plutonium block out of New Horizons Core Mod", t);
        }
        if (stack == null || stack.getItem() == null) {
            MyMod.LOG.error(
                "The black plutonium block was not found; the D positions of the Transcendent Catalyst Maker accept "
                    + "iron blocks instead until that is fixed.");
            return ofBlock(Blocks.iron_block, 0);
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        MyMod.LOG.info(
            "Transcendent Catalyst Maker: the D positions are bound to {} (metadata {})",
            stack.getDisplayName(),
            stack.getItemDamage());
        return ofBlock(block, stack.getItemDamage());
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

    /**
     * The most parallels this machine can ever run: {@value #MAX_PARALLEL}, i.e. 2^25.
     * <p>
     * GregTech calls this "the absolute maximum number of parallels possible right now" and uses it for two things:
     * {@code getTrueParallel()} caps the player's number with it, and the power panel's text box takes its upper bound
     * from it - {@code makeParallelConfiguratorTextFieldWidget} builds the box as {@code numbersInt(1,
     * getMaxParallelRecipes())}, and the legacy panel's validator clamps to the same pair. Returning the ceiling here
     * is therefore what makes the box accept any number from {@value #MIN_PARALLEL} to {@value #MAX_PARALLEL}; the
     * number the player actually typed lives in {@code powerPanelMaxParallel} and is what
     * {@link #getTrueParallel()} returns, and "always use the maximum" makes the machine use this ceiling itself.
     * It used to return {@code 1 << machineMode}, which is what held the count to the seven powers of two from 1 to 64
     * that the mode button could cycle through.
     */
    @Override
    public int getMaxParallelRecipes() {
        return MAX_PARALLEL;
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

        // The coolant a run burns grows with the parallel count, and that count can go up to MAX_PARALLEL, so the
        // product is worked out as a long: 100,000 x 33,554,432 = 3.36e12 does not fit in an int, and an overflowed
        // requirement would come out negative and pass every "is there enough?" test.
        long wantedAmount = (long) coolant.amount * Math.max(1, getTrueParallel());
        if (!hasCoolant(coolant.fluid, wantedAmount)) return CheckRecipeResultRegistry.NO_RECIPE;

        CheckRecipeResult result = super.checkProcessing();
        if (result.wasSuccessful()) {
            // A single FluidStack cannot hold more millibuckets than an int, so a requirement larger than that is
            // drained up to the int limit. hasCoolant() above has already established that at least this much is
            // there, so nothing is taken that is not owed.
            FluidStack wanted = coolant.fluid.copy();
            wanted.amount = (int) Math.min(wantedAmount, (long) Integer.MAX_VALUE);
            depleteInput(wanted);
        }
        return result;
    }

    /** True when the input hatches together hold at least this much of the fluid. */
    private boolean hasCoolant(FluidStack fluid, long wantedAmount) {
        long found = 0;
        for (MTEHatchInput hatch : mInputHatches) {
            FluidStack inHatch = hatch.getFluid();
            if (inHatch != null && GTUtility.areFluidsEqual(inHatch, fluid)) found += inHatch.amount;
        }
        return found >= wantedAmount;
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
     * The machine has no machine modes: its one setting, the parallel count, is a number the player types into the
     * power panel rather than a mode the mode button cycles through. GregTech draws the power panel button, and the
     * text box inside it, for every multiblock - {@code supportsPowerPanel()} is already {@code true} by default - so
     * nothing has to be added to the GUI here.
     */

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
