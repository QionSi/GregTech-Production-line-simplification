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
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;
import static gregtech.common.misc.WirelessNetworkManager.processInitialSettings;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
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
import com.qionsi.simplification.item.ModItems;
import com.qionsi.simplification.recipe.ModRecipeMaps;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
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
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 简易净水机 / Simple Water Purifier.
 * <p>
 * 抛弃了复杂的净化水流程 / it throws the whole purification line away and produces purified water in one step. The
 * design document's own joke is that the price is a quantum-superposition power draw and "Schrödinger's cat" style
 * reagent consumption - the machine burns absurd EU and a little UU matter to skip the plant.
 *
 * <h2>Four structures, one chip each</h2>
 *
 * The machine has four structures, and which one it forms is decided by the 净水厂升级芯片 in the controller's own slot:
 *
 * <table>
 * <caption>chip to structure</caption>
 * <tr>
 * <th>chip in the controller slot</th>
 * <th>structure it forms</th>
 * <th>purified water it can make</th>
 * </tr>
 * <tr>
 * <td>none (t1)</td>
 * <td>16 x 10 x 11</td>
 * <td>grade 1-2</td>
 * </tr>
 * <tr>
 * <td>T2 chip</td>
 * <td>24 x 10 x 19</td>
 * <td>grade 1-4</td>
 * </tr>
 * <tr>
 * <td>T3 chip</td>
 * <td>40 x 15 x 26</td>
 * <td>grade 1-6</td>
 * </tr>
 * <tr>
 * <td>T4 chip</td>
 * <td>40 x 15 x 26</td>
 * <td>grade 1-8</td>
 * </tr>
 * </table>
 *
 * The chip tier has to *equal* the structure tier, not merely exceed it: a T2 chip forms the t2 shape and nothing
 * else. That is why the machine does not try every piece in turn - it asks the chip which one to check, so a structure
 * built to the wrong tier simply does not form and the player can see which chip is expected.
 *
 * <h2>Recipes are gated by tier</h2>
 *
 * Each recipe carries its minimum tier in {@code mSpecialValue}; the recipe logic refuses anything above the structure
 * that is standing. That is what "this structure can make grades 1-2" means in practice.
 *
 * <h2>No overclocking at all</h2>
 *
 * Every recipe is fixed at 120 seconds. The overclock calculator is {@code ofNoOverclock}, so a higher tier energy
 * hatch buys nothing but headroom for the parallels.
 */
public class MTESimpleWaterPurifier extends MTEExtendedPowerMultiBlockBase<MTESimpleWaterPurifier>
    implements ISurvivalConstructable, ICasingTextureProvider {

    public static final int TIER_1 = 1;
    public static final int TIER_2 = 2;
    public static final int TIER_3 = 3;
    public static final int TIER_4 = 4;

    /** Structure piece names, indexed by tier - 1. */
    public static final String[] PIECES = { "t1", "t2", "t3", "t4" };

    private static final String[] BLUEPRINTS = { MyMod.MODID + ":multiblock/simple_water_purifier_t1",
        MyMod.MODID + ":multiblock/simple_water_purifier_t2", MyMod.MODID + ":multiblock/simple_water_purifier_t3",
        MyMod.MODID + ":multiblock/simple_water_purifier_t4", };

    private static final String[][][] SHAPES = { StructureBlueprintFile.read(BLUEPRINTS[0]),
        StructureBlueprintFile.read(BLUEPRINTS[1]), StructureBlueprintFile.read(BLUEPRINTS[2]),
        StructureBlueprintFile.read(BLUEPRINTS[3]), };

    /**
     * Controller offsets per tier, in the usual A/B/C order (blocks left of the controller, blocks above it, blocks
     * between the player and it).
     */
    public static final int[][] OFFSETS = { { 10, 8, 0 }, { 10, 8, 0 }, { 10, 13, 0 }, { 10, 13, 0 } };

    /** 强化无菌净水厂机械方块 / Reinforced Sterile Water Plant Casing the shell has to keep. */
    public static final int MIN_REINFORCED_CASINGS = 20;

    /** Parallels the machine starts with; the player raises it in the power panel. */
    public static final int MIN_PARALLEL = 1;
    /** Highest parallel count the power panel will accept, as the design document specifies. */
    public static final int MAX_PARALLEL = 2_147_483_640;

    /** Number of lines the tooltip takes from the language file; missing ones are skipped. */
    private static final int TOOLTIP_LINES = 14;

    private static IStructureDefinition<MTESimpleWaterPurifier> DEFINITION;

    /** Reinforced casings the last structure check found. */
    private int reinforcedCasings;

    /** Which of the four structures is formed, 0 when none. */
    private int structureTier;

    /** True when the GUI switch has the machine paying out of the wireless network. */
    private boolean wirelessEnergyMode;

    /** 电网升级仓 hatches on the structure. Only their presence matters; they store nothing. */
    private final java.util.List<MTEHatchGridUpgrade> gridUpgradeHatches = new java.util.ArrayList<>();

    /** Multiplier applied to the recipe's EU/t while the machine runs without a grid upgrade hatch. */
    private double powerMultiplier = 1.0D;

    /** Which progress marker the last random power change happened at: 0, 25 or 75. */
    private int powerMarker;

    /** Consecutive ticks the available power has been off the wanted value by more than 5%. */
    private int offTargetTicks;

    /** True once the current run has been punished, so the output is only reduced once. */
    private boolean penaltyApplied;

    /** EU/t the run started at, before any random variation. */
    private int baseEut;

    private static final java.util.Random RANDOM = new java.util.Random();

    private UUID ownerUUID;

    private BigInteger finalConsumption = BigInteger.ZERO;

    public MTESimpleWaterPurifier(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        startAtOneParallel();
    }

    public MTESimpleWaterPurifier(String aName) {
        super(aName);
        startAtOneParallel();
    }

    private void startAtOneParallel() {
        alwaysMaxParallel = false;
        powerPanelMaxParallel = MIN_PARALLEL;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESimpleWaterPurifier(this.mName);
    }

    // ------------------------------------------------------------------ structure

    /** Checks the four blueprints against their offsets and writes what they ask for into the log. */
    public static void prepareStructure() {
        for (int i = 0; i < 4; i++) {
            StructureBlueprintFile
                .verifyControllerPosition(BLUEPRINTS[i], SHAPES[i], OFFSETS[i][0], OFFSETS[i][1], OFFSETS[i][2]);
        }
        for (int i = 0; i < 4; i++) {
            MyMod.LOG.info(
                "Simple Water Purifier {}: {} wide x {} tall x {} deep, {} reinforced sterile water plant casings drawn",
                PIECES[i],
                StructureBlueprintFile.width(SHAPES[i]),
                StructureBlueprintFile.height(SHAPES[i]),
                StructureBlueprintFile.depth(SHAPES[i]),
                StructureBlueprintFile.count(SHAPES[i], 'F'));
        }
        MyMod.LOG.info(
            "Simple Water Purifier: the structure tier is chosen by the chip in the controller slot - none for t1, then "
                + "the T2/T3/T4 净水厂升级芯片; each chip forms only its own tier. The shell has to keep {} reinforced "
                + "casings",
            MIN_REINFORCED_CASINGS);
    }

    private static IStructureDefinition<MTESimpleWaterPurifier> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    /**
     * The four shapes over one shared symbol table.
     * <p>
     * The four tiers were laid out with different letters for the same blocks, so before the blueprints were turned
     * into structure definitions every block was given one letter of its own. That is why the symbol table below looks
     * alphabetically ordered rather than following any one tier's own numbering: {@code 'F'} is the reinforced sterile
     * water plant casing in all four tiers, which is what makes the hatch adder a single element.
     */
    private static IStructureDefinition<MTESimpleWaterPurifier> buildDefinition() {
        try {
            StructureDefinition.Builder<MTESimpleWaterPurifier> builder = StructureDefinition.builder();
            for (int i = 0; i < 4; i++) {
                builder.addShape(PIECES[i], StructureUtility.transpose(SHAPES[i]));
            }
            return builder
                // F: 强化无菌净水厂机械方块, the only place a hatch may go.
                .addElement(
                    'F',
                    buildHatchAdder(MTESimpleWaterPurifier.class)
                        .atLeast(
                            Energy.or(ExoticEnergy),
                            Maintenance,
                            InputBus,
                            InputHatch,
                            OutputBus,
                            OutputHatch,
                            GridUpgradeHatchElement.INSTANCE)
                        .casingIndex(Casings.ReinforcedSterileWaterPlantCasing.getTextureId())
                        .hint(1)
                        .buildAndChain(
                            onElementPass(
                                x -> ++x.reinforcedCasings,
                                Casings.ReinforcedSterileWaterPlantCasing.asElement())))
                .addElement('A', Casings.SuperconductingCoilBlock.asElement())
                .addElement('B', Casings.QuarkExclusionCasing.asElement())
                .addElement('C', Casings.FilterMachineCasing.asElement())
                .addElement('D', Casings.PTFEPipeCasing.asElement())
                .addElement('E', Casings.SterileWaterPlantCasing.asElement())
                .addElement('G', Casings.SlickSterileFlocculationCasing.asElement())
                .addElement('H', Casings.StabilizedNaquadahWaterPlantCasing.asElement())
                .addElement('I', Casings.InertNeutralizationWaterPlantCasing.asElement())
                .addElement('J', Casings.ReactiveGasContainmentCasing.asElement())
                .addElement('K', Casings.InertFiltrationCasing.asElement())
                .addElement('L', Casings.HeatResistantTriniumPlatedCasing.asElement())
                .addElement('M', Casings.NaquadriaReinforcedWaterPlantCasing.asElement())
                .addElement('N', Casings.HighEnergyUltravioletEmitterCasing.asElement())
                .addElement('O', Casings.ParticleBeamGuidancePipeCasing.asElement())
                .addElement('P', Casings.FemtometerCalibratedParticleBeamCasing.asElement())
                // Q..Y: GT frame boxes, by their exact metadata.
                .addElement('Q', ofBlock(GregTechAPI.sBlockFrames, 35))
                .addElement('R', ofBlock(GregTechAPI.sBlockFrames, 84))
                .addElement('S', ofBlock(GregTechAPI.sBlockFrames, 316))
                .addElement('T', ofBlock(GregTechAPI.sBlockFrames, 319))
                .addElement('U', ofBlock(GregTechAPI.sBlockFrames, 325))
                .addElement('V', ofBlock(GregTechAPI.sBlockFrames, 335))
                .addElement('W', ofBlock(GregTechAPI.sBlockFrames, 395))
                .addElement('X', ofBlock(GregTechAPI.sBlockFrames, 405))
                .addElement('Y', ofBlock(GregTechAPI.sBlockFrames, 992))
                // Z, 0, 1, 2: GT glass, by their exact metadata.
                .addElement('Z', ofBlock(GregTechAPI.sBlockGlass1, 0))
                .addElement('0', ofBlock(GregTechAPI.sBlockGlass1, 1))
                .addElement('1', ofBlock(GregTechAPI.sBlockGlass1, 2))
                .addElement('2', ofBlock(GregTechAPI.sBlockGlass1, 3))
                // 3: metal block, 4: tinted glass, 6: water.
                .addElement('3', ofBlock(GregTechAPI.sBlockMetal6, 13))
                .addElement('4', ofBlock(GregTechAPI.sBlockTintedGlass, 0))
                .addElement('6', ofBlock(Blocks.water, 0))
                // 5: Chisel's neonite block, which only exists once Chisel has run its own pre-init.
                .addElement('5', lazy(t -> ofBlock(neonite(), 7)))
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Simple Water Purifier structure definition; the machine falls back to a single "
                    + "block so the game keeps running.",
                t);
            return StructureDefinition.<MTESimpleWaterPurifier>builder()
                .addShape(PIECES[0], new String[][] { { "~" } })
                .addShape(PIECES[1], new String[][] { { "~" } })
                .addShape(PIECES[2], new String[][] { { "~" } })
                .addShape(PIECES[3], new String[][] { { "~" } })
                .build();
        }
    }

    /** Chisel's neonite block, resolved at the first structure check. */
    private static Block neonite() {
        Block block = GameRegistry.findBlock("chisel", "neonite");
        if (block == null) {
            MyMod.LOG.error(
                "Chisel did not register its neonite block; those positions of the Simple Water Purifier accept iron "
                    + "blocks until that is fixed.");
            return Blocks.iron_block;
        }
        return block;
    }

    @Override
    public IStructureDefinition<MTESimpleWaterPurifier> getStructureDefinition() {
        return getDefinition();
    }

    /** The tier the chip in the controller slot unlocks: 0 when it holds something that is not a purifier chip. */
    private int chipTier() {
        ItemStack chip = getControllerSlot();
        if (chip == null || chip.getItem() == null) return TIER_1;
        if (chip.getItem() == ModItems.waterPurifierChipT2) return TIER_2;
        if (chip.getItem() == ModItems.waterPurifierChipT3) return TIER_3;
        if (chip.getItem() == ModItems.waterPurifierChipT4) return TIER_4;
        return 0;
    }

    /**
     * Forms the structure the chip asks for.
     * <p>
     * The tier is read from the chip first and only that one piece is checked, so a machine built to the wrong tier
     * does not form at all rather than silently downgrading to a tier the player did not intend.
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        reinforcedCasings = 0;
        structureTier = 0;
        gridUpgradeHatches.clear();
        // A new structure means a new run: forget the previous variation and any punishment.
        powerMultiplier = 1.0D;
        powerMarker = 0;
        offTargetTicks = 0;
        penaltyApplied = false;
        baseEut = 0;
        int tier = chipTier();
        if (tier == 0) {
            MyMod.LOG.warn(
                "Simple Water Purifier: the controller slot holds something that is not a 净水厂升级芯片, so no structure "
                    + "can form. Remove it, or put a T2/T3/T4 chip in.");
            return;
        }
        int[] offset = OFFSETS[tier - 1];
        if (!checkPiece(PIECES[tier - 1], offset[0], offset[1], offset[2], errors)) return;
        structureTier = tier;
        checkCasingMin(errors, reinforcedCasings, MIN_REINFORCED_CASINGS);
        checkOneMaintenanceHatch(errors);
        checkHasAnyEnergy(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
    }

    /** The structure tier the machine is standing in, 0 when it is not formed. */
    public int getStructureTier() {
        return structureTier;
    }

    /**
     * Builds the tier the projector's stack size names: one projector builds t1, a stack of two builds t2, three t3,
     * four t4.
     * <p>
     * This is also what makes the NEI preview show four tiers. The preview's tier slider does not read anything from
     * the machine - it walks {@code stackSize} from 1 upwards, rebuilds, and counts the sizes that actually placed
     * blocks ({@code ObserverWorld.estimateTier}). So a {@code construct} that keys off {@code stackSize.stackSize} is
     * what gives the slider its range; keying it off the chip, as this used to, pinned the preview to t1 because the
     * preview's stack is always a single controller.
     */
    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int tier = Math.max(TIER_1, Math.min(TIER_4, stackSize.stackSize));
        int[] offset = OFFSETS[tier - 1];
        buildPiece(PIECES[tier - 1], stackSize, hintsOnly, offset[0], offset[1], offset[2]);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int tier = Math.max(TIER_1, Math.min(TIER_4, stackSize.stackSize));
        int[] offset = OFFSETS[tier - 1];
        return survivalBuildPiece(
            PIECES[tier - 1],
            stackSize,
            offset[0],
            offset[1],
            offset[2],
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { "\u00a7e\u7b80\u6613\u51c0\u6c34\u673a\u00a7r / Simple Water Purifier",
            "\u00a77t1 (no chip) 16x10x11, t2 24x10x19, t3/t4 40x15x26.",
            "\u00a77The chip in the controller slot decides which of the four shapes forms; a T2 chip forms t2 only.",
            "\u00a77F = Reinforced Sterile Water Plant Casing: every hatch goes on an F position.",
            "\u00a77Sneak-right-click the controller with the projector to build a tier: 1 projector = t1, 2 = t2, 3 = t3, 4 = t4." };
    }

    // ------------------------------------------------------------------ running

    @Override
    public RecipeMap<?> getRecipeMap() {
        return ModRecipeMaps.simpleWaterPurifierRecipes;
    }

    /**
     * The recipe logic: no overclocking, unlimited tier skips, and a per-recipe minimum structure tier.
     * <p>
     * Every recipe stores its minimum tier in {@code mSpecialValue} - the design document writes it as "at least t1"
     * and
     * so on. A recipe whose minimum is above the structure that is standing is refused here, which is what turns one
     * pool of four recipes into "a t1 structure makes grades 1-2, a t4 structure makes grades 1-8".
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Override
            protected @Nonnull CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                if (structureTier < recipe.mSpecialValue) return CheckRecipeResultRegistry.NO_RECIPE;
                // The wireless network only becomes usable once a 电网升级仓 is on the structure.
                if (!wirelessEnergyMode || !hasGridUpgrade()) return CheckRecipeResultRegistry.SUCCESSFUL;
                BigInteger cost = BigInteger.valueOf(recipe.mEUt)
                    .multiply(BigInteger.valueOf(recipe.mDuration));
                BigInteger wanted = cost.multiply(BigInteger.valueOf(Math.max(1, maxParallel)));
                if (getUserEU(wirelessOwner()).compareTo(wanted) < 0) {
                    finalConsumption = BigInteger.ZERO;
                    return CheckRecipeResultRegistry.insufficientStartupPower(wanted);
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            protected @Nonnull OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                // 该机器不支持超频，所有配方固定 120s / the design document fixes every recipe at 120 seconds.
                return OverclockCalculator.ofNoOverclock(recipe);
            }

            @Override
            protected @Nonnull CheckRecipeResult onRecipeStart(@Nonnull GTRecipe recipe) {
                if (!wirelessEnergyMode || !hasGridUpgrade()) return CheckRecipeResultRegistry.SUCCESSFUL;
                finalConsumption = BigInteger.valueOf(recipe.mEUt)
                    .multiply(BigInteger.valueOf(recipe.mDuration))
                    .multiply(BigInteger.valueOf(-calculatedParallels));
                if (!addEUToGlobalEnergyMap(wirelessOwner(), finalConsumption)) {
                    return CheckRecipeResultRegistry.insufficientStartupPower(finalConsumption);
                }
                overwriteCalculatedEut(0);
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }.setMaxParallelSupplier(this::getTrueParallel)
            .setUnlimitedTierSkips();
    }

    @Override
    public int getMaxParallelRecipes() {
        return MAX_PARALLEL;
    }

    // ------------------------------------------------------------------ variable power

    /**
     * 变功率运行 / variable power draw.
     * <p>
     * Without a 电网升级仓 the machine does not hold a steady draw: at 25% and again at 75% of the recipe it re-rolls
     * its consumption to a random point within +-20% of the recipe's own EU/t. It also watches what the energy hatches
     * can actually deliver - if that differs from the value it is drawing by more than 5% for a continuous 15 seconds,
     * the run is punished and produces only 5% of its output.
     * <p>
     * With a 电网升级仓 installed none of this happens: the draw is the recipe's own EU/t and there is no punishment.
     */
    private void updateVariablePower() {
        if (mMaxProgresstime <= 0) return;
        if (baseEut <= 0) baseEut = Math.max(1, mEUt);
        int percent = (int) (100L * mProgresstime / mMaxProgresstime);
        boolean atFirst = powerMarker == 0 && percent >= 25;
        boolean atSecond = powerMarker == 25 && percent >= 75;
        if (atFirst || atSecond) {
            powerMarker = atSecond ? 75 : 25;
            // -20% .. +20%
            powerMultiplier = 0.8D + 0.4D * RANDOM.nextDouble();
            if (baseEut > 0) mEUt = Math.max(1, (int) (baseEut * powerMultiplier));
        }

        long wanted = Math.max(1, mEUt);
        long available = getMaxInputEu();
        if (Math.abs(available - wanted) > wanted * 0.05D) {
            if (++offTargetTicks > 15 * 20 && !penaltyApplied) {
                penaltyApplied = true;
                scaleOutputs(0.05D);
                MyMod.LOG.info(
                    "Simple Water Purifier: the energy hatches deliver {} EU/t but the machine is drawing {} EU/t, so "
                        + "this run is punished to 5% output.",
                    available,
                    wanted);
            }
        } else {
            offTargetTicks = 0;
        }
    }

    /** Reduces every fluid this run will output, once. */
    private void scaleOutputs(double factor) {
        if (mOutputFluids == null) return;
        for (net.minecraftforge.fluids.FluidStack stack : mOutputFluids) {
            if (stack != null) stack.amount = Math.max(1, (int) (stack.amount * factor));
        }
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (!hasGridUpgrade()) updateVariablePower();
        return super.onRunningTick(aStack);
    }

    // ------------------------------------------------------------------ grid upgrade hatch

    /** Called by {@link GridUpgradeHatchElement} for each 电网升级仓 found on the structure. */
    public boolean addGridUpgradeHatch(MTEHatchGridUpgrade hatch) {
        return hatch != null && !gridUpgradeHatches.contains(hatch) && gridUpgradeHatches.add(hatch);
    }

    public java.util.List<MTEHatchGridUpgrade> getGridUpgradeHatches() {
        return gridUpgradeHatches;
    }

    /** True when the structure carries at least one 电网升级仓. */
    public boolean hasGridUpgrade() {
        return !gridUpgradeHatches.isEmpty();
    }

    // ------------------------------------------------------------------ wireless

    public boolean isWirelessEnergyMode() {
        return wirelessEnergyMode;
    }

    public void setWirelessEnergyMode(boolean wireless) {
        if (wirelessEnergyMode == wireless) return;
        wirelessEnergyMode = wireless;
        MyMod.LOG.info(
            "Simple Water Purifier: power source switched to {}",
            wireless ? "the wireless network" : "the energy hatches");
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && (aTick == 1)) {
            ownerUUID = processInitialSettings(aBaseMetaTileEntity);
        }
    }

    private UUID wirelessOwner() {
        if (ownerUUID == null) ownerUUID = processInitialSettings(getBaseMetaTileEntity());
        return ownerUUID;
    }

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

    // ------------------------------------------------------------------ toggles

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new SimpleWaterPurifierGui(this);
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
        aNBT.setBoolean("simpleWaterPurifierWireless", wirelessEnergyMode);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessEnergyMode = aNBT.getBoolean("simpleWaterPurifierWireless");
        if (powerPanelMaxParallel < MIN_PARALLEL) powerPanelMaxParallel = MIN_PARALLEL;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        structureTier = 0;
        reinforcedCasings = 0;
    }

    // ------------------------------------------------------------------ looks

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        // The same face as the 澄清净化单元 / Clarifier purification unit, as the design asks for.
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_DISTILLATION_TOWER,
            OVERLAY_FRONT_DISTILLATION_TOWER_GLOW,
            OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE,
            OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.ReinforcedSterileWaterPlantCasing.getCasingTexture();
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Simple Water Purifier");
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            String key = "simplification.tooltip.simpleWaterPurifier." + line;
            if (StatCollector.canTranslate(key)) tt.addInfo(StatCollector.translateToLocal(key));
        }
        tt.addSeparator()
            .addStaticParallelInfo(MIN_PARALLEL)
            .addInfo("Parallels are typed into the power panel: " + MIN_PARALLEL + " to " + MAX_PARALLEL + ".")
            .addInfo("No overclocking: every recipe is fixed at 120 seconds.")
            .addUnlimitedTierSkips()
            .addSupportMultiAmp()
            .addSeparator()
            .beginStructureBlock(16, 10, 11, false)
            .addController(
                "Front face, column " + (OFFSETS[0][0] + 1) + ", level " + (OFFSETS[0][1] + 1) + " from the top")
            .addCasing(MIN_REINFORCED_CASINGS + "+", "Reinforced Sterile Water Plant Casing", false)
            .addEnergyHatch("1+", "Any Reinforced Sterile Water Plant Casing", 1)
            .addMaintenanceHatch("1", "Any Reinforced Sterile Water Plant Casing", 1)
            .addInputHatch("1+", "Any Reinforced Sterile Water Plant Casing", 1)
            .addOutputHatch("1+", "Any Reinforced Sterile Water Plant Casing", 1)
            .addInputBus("1+", "Any Reinforced Sterile Water Plant Casing", 1)
            .addOutputBus("1+", "Any Reinforced Sterile Water Plant Casing", 1)
            .addOtherStructurePart("净水厂升级芯片", "In the controller slot: none for t1, T2/T3/T4 for those tiers")
            .toolTipFinisher();
        return tt;
    }
}
