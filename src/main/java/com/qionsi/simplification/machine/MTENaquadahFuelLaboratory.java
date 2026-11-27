package com.qionsi.simplification.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

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

import goodgenerator.loader.Loaders;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 硅岩燃料研究与试验机 / Naquadah Fuel Research and Test Machine.
 *
 * <h2>Two modes</h2>
 *
 * <b>Research</b> turns feedstock into 硅岩基流体燃料 Mk-I .. Mk-VI. It runs 256 parallels, doubled once per level of
 * the heating coil in the structure.
 * <p>
 * <b>Test</b> burns the same feedstock for power instead, either into the dynamo hatches or, if the wireless switch is
 * on, straight into the player's wireless network. It always runs 16,777,216 parallels and each level of the heating
 * coil doubles the final output.
 *
 * <h2>Two kinds of coil</h2>
 *
 * The structure has two different coil positions and they mean different things:
 * <ul>
 * <li><b>B</b> - the field-restriction family. Its tier gates which fuel grades the machine may work with: Mk-I/II need
 * the ameliorated superconducting coil or better, Mk-III needs the field restriction coil, Mk-IV/V the advanced one,
 * Mk-VI the ultimate one. The minimum is stored in each recipe's {@code mSpecialValue} and checked in
 * {@code validateRecipe}.</li>
 * <li><b>D</b> - ordinary GregTech heating coils, the same ones the blast furnace uses. These set the parallel count in
 * research mode and the output multiplier in test mode.</li>
 * </ul>
 */
public class MTENaquadahFuelLaboratory extends MTEExtendedPowerMultiBlockBase<MTENaquadahFuelLaboratory>
    implements ISurvivalConstructable, ICasingTextureProvider {

    /** Blueprint piece name. */
    public static final String STRUCTURE_PIECE_MAIN = "main";

    private static final String STRUCTURE_FILE_PATH = MyMod.MODID + ":multiblock/naquadah_fuel_lab";

    private static final String[][] shape = StructureBlueprintFile.read(STRUCTURE_FILE_PATH);

    public static final int HORIZONTAL_OFF_SET = 13;
    public static final int VERTICAL_OFF_SET = 13;
    public static final int DEPTH_OFF_SET = 0;

    /** The design document asks for at least this many 硅岩燃料精炼机械方块. */
    public static final int MIN_REFINERY_CASINGS = 20;

    /**
     * A free casing-texture page for the refinery casing. GoodGenerator's {@code BlockCasing} is a plain {@link
     * net.minecraft.block.Block} with no {@code getTextureIndex}, so the hatch adder cannot look its texture up - it
     * has to be registered here. Page 121 is used to stay clear of GT's casings on page 0.
     */
    private static final int CASING_PAGE = 121;
    private static final int CASING_INDEX = CASING_PAGE * 128;

    /** Model index for the research mode. */
    public static final int MODE_RESEARCH = 0;
    /** Model index for the test mode. */
    public static final int MODE_TEST = 1;

    /** Research mode: 256 parallels, doubled per heating coil level. */
    public static final int RESEARCH_BASE_PARALLEL = 256;
    /** Test mode: a fixed 16,777,216 parallels. */
    public static final int TEST_PARALLEL = 16_777_216;

    /**
     * Field-restriction coil tier each fuel grade needs, indexed by grade - 1.
     * <p>
     * Mk-I/II need the ameliorated superconducting coil or better (tier 1), Mk-III the field restriction coil (2),
     * Mk-IV/V the advanced one (3), Mk-VI the ultimate one (4).
     */
    private static final int[] COIL_REQUIRED = { 1, 1, 2, 3, 3, 4 };

    /**
     * What one unit of the fuel is worth in the test mode, indexed by grade - 1.
     * <p>
     * These are the design document's 配方发电功率 figures. They do not fit an {@code int}, and they are far too large
     * to sit in a recipe's {@code mEUt}, so each test recipe stores only its grade in {@code mSpecialValue} and the
     * machine looks the power up here - the same split 大型硅岩反应堆 uses.
     */
    private static final long[] TEST_POWER = { 314_572_800L, 40_265_318_400L, 1_288_490_183_680L, 4_123_168_587_780L,
        230_897_440_915_000L, 923_589_763_662_000L };

    private static IStructureDefinition<MTENaquadahFuelLaboratory> DEFINITION;

    /** Highest tier found among the field-restriction coils: 1 = ameliorated superconductor, 4 = ultimate. */
    private int fieldCoilTier;

    /** The ordinary heating coil level, which drives parallelism and output. */
    private HeatingCoilLevel heatingCoil = HeatingCoilLevel.None;

    /** True when the GUI switch has the machine trading power with the wireless network. */
    private boolean wirelessEnergyMode;

    /**
     * Largest single payout a dynamo hatch is asked to swallow. Dynamos hold a long, so one chunk has to stay well
     * inside it; the rest waits in {@link #pendingDynamoEnergy}.
     */
    private static final long DYNAMO_CHUNK = 1_000_000_000_000_000_000L;

    /** Payout that has been produced but not handed to the dynamo hatches yet. */
    private java.math.BigInteger pendingDynamoEnergy = java.math.BigInteger.ZERO;

    /** This machine's wireless network account, resolved on the first tick. */
    private java.util.UUID ownerUUID;

    /** What the current research run has reserved from the wireless network, or null when it is not paying from it. */
    private java.math.BigInteger wirelessCost;

    /** Fuel grade of the run currently in progress, recorded when its recipe is validated. */
    private int activeGrade;

    public MTENaquadahFuelLaboratory(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTENaquadahFuelLaboratory(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENaquadahFuelLaboratory(this.mName);
    }

    // ------------------------------------------------------------------ modes

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public int nextMachineMode() {
        return (getMachineMode() + 1) % 2;
    }

    @Override
    public String getMachineModeName() {
        return StatCollector.translateToLocal(
            getMachineMode() == MODE_RESEARCH ? "simplification.naqfuel.mode.research"
                : "simplification.naqfuel.mode.test");
    }

    /**
     * Both pools, always.
     * <p>
     * NEI builds its recipe-catalyst index by asking every machine for {@code getAvailableRecipeMaps()}, and the
     * default implementation only returns {@code getRecipeMap()} - the pool for whichever mode the machine happens to
     * be in. A two-pool machine that only overrides {@code getRecipeMap()} therefore looks like it cannot run the
     * other pool's recipes at all: pressing U on it never shows them.
     */
    @Override
    public java.util.Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return java.util.Arrays
            .asList(ModRecipeMaps.naquadahFuelResearchRecipes, ModRecipeMaps.naquadahFuelTestRecipes);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return getMachineMode() == MODE_RESEARCH ? ModRecipeMaps.naquadahFuelResearchRecipes
            : ModRecipeMaps.naquadahFuelTestRecipes;
    }

    // ------------------------------------------------------------------ structure

    /** Reports the blueprint's size at startup. */
    public static void prepareStructure() {
        MyMod.LOG.info(
            "Naquadah Fuel Research and Test Machine: structure is {} wide x {} tall x {} deep, controller at "
                + "({}, {}, {})",
            StructureBlueprintFile.width(shape),
            StructureBlueprintFile.height(shape),
            StructureBlueprintFile.depth(shape),
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET);
    }

    private static IStructureDefinition<MTENaquadahFuelLaboratory> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    private static IStructureDefinition<MTENaquadahFuelLaboratory> buildDefinition() {
        try {
            StructureDefinition.Builder<MTENaquadahFuelLaboratory> builder = StructureDefinition.builder();
            MyMod.LOG.info(
                "Naquadah lab structure blocks: FRF_Casings={}, compactFusionCoil={}, FRF_Coil_1={}, FRF_Coil_2={}, "
                    + "FRF_Coil_3={}, fieldRestrictingGlass={}, radiationProtectionSteelFrame={}, sBlockCasings8={}",
                Loaders.FRF_Casings,
                Loaders.compactFusionCoil,
                Loaders.FRF_Coil_1,
                Loaders.FRF_Coil_2,
                Loaders.FRF_Coil_3,
                Loaders.fieldRestrictingGlass,
                Loaders.radiationProtectionSteelFrame,
                GregTechAPI.sBlockCasings8);
            String[][] transposed = StructureUtility.transpose(shape);
            MyMod.LOG.info(
                "Naquadah lab transposed shape: {} lines, first line {} cells long",
                transposed.length,
                transposed.length > 0 ? transposed[0].length : 0);
            builder.addShape(STRUCTURE_PIECE_MAIN, transposed);
            return builder
                // A: 硅岩燃料精炼机械方块, the only block that takes hatches.
                .addElement(
                    'A',
                    buildHatchAdder(MTENaquadahFuelLaboratory.class)
                        .atLeast(
                            Energy.or(ExoticEnergy),
                            Maintenance,
                            InputHatch,
                            OutputHatch,
                            InputBus,
                            OutputBus,
                            Dynamo)
                        .casingIndex(refineryCasingIndex())
                        .hint(1)
                        .buildAndChain(onElementPass(x -> ++x.refineryCasings, ofBlock(Loaders.FRF_Casings, 0))))
                // B: the field-restriction coil family. ofBlocksTiered rather than ofChain: a chain accepts any of
                // them when checking, but only ever places the first one, so the structure could only ever be built
                // out of 改良型超导线圈. ofBlocksTiered knows all four as ordered tiers, lets the projector pick one via
                // its stack size, and records the best tier present so the recipe gate can read it.
                .addElement(
                    'B',
                    lazy(
                        x -> StructureUtility.ofBlocksTiered(
                            // tierExtractor: (block, meta) -> tier, 1 = 改良型超导, 4 = 终极力场约束
                            (block, meta) -> {
                                if (block == Loaders.compactFusionCoil && meta == 0) return 1;
                                if (block == Loaders.FRF_Coil_1) return 2;
                                if (block == Loaders.FRF_Coil_2) return 3;
                                if (block == Loaders.FRF_Coil_3) return 4;
                                return 0;
                            },
                            java.util.Arrays.asList(
                                org.apache.commons.lang3.tuple.Pair.of(Loaders.compactFusionCoil, 0),
                                org.apache.commons.lang3.tuple.Pair.of(Loaders.FRF_Coil_1, 0),
                                org.apache.commons.lang3.tuple.Pair.of(Loaders.FRF_Coil_2, 0),
                                org.apache.commons.lang3.tuple.Pair.of(Loaders.FRF_Coil_3, 0)),
                            0,
                            (machine, tier) -> machine.fieldCoilTier = tier,
                            machine -> machine.fieldCoilTier)))
                // C: 力场约束玻璃
                .addElement('C', lazy(x -> ofBlock(Loaders.fieldRestrictingGlass, 0)))
                // D: ordinary heating coils, the blast furnace family.
                .addElement(
                    'D',
                    activeCoils(
                        GTStructureUtility.<MTENaquadahFuelLaboratory>ofCoil(
                            // Block-bodied on purpose: a bare assignment is compatible with both ofCoil overloads
                            // (BiConsumer and BiPredicate), and javac refuses to guess.
                            (x, l) -> { x.heatingCoil = l; },
                            x -> x.heatingCoil)))
                // E: 铕强化防辐射机械方块
                .addElement('E', lazy(x -> ofBlock(GregTechAPI.sBlockCasings8, 5)))
                // F: 防辐射钢框架
                .addElement('F', lazy(x -> ofBlock(Loaders.radiationProtectionSteelFrame, 0)))
                .build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Naquadah Fuel Research and Test Machine structure definition; falling back to a "
                    + "single block so the game keeps running.",
                t);
            return StructureDefinition.<MTENaquadahFuelLaboratory>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "~" } })
                .build();
        }
    }

    /**
     * Registers the refinery casing on a free casing-texture page and returns that page's index.
     * <p>
     * GoodGenerator's casing block implements no {@code IHasIndexedTexture}, so asking it for an index returns nothing
     * and a zero index makes {@code HatchElementBuilder.build()} throw - which the definition's catch turned into a
     * one-block fallback, leaving the NEI preview with nothing but the controller.
     */
    private static int refineryCasingIndex() {
        if (Textures.BlockIcons.casingTexturePages[CASING_PAGE] == null) {
            Textures.BlockIcons.casingTexturePages[CASING_PAGE] = new ITexture[128];
        }
        if (Textures.BlockIcons.casingTexturePages[CASING_PAGE][0] == null && Loaders.FRF_Casings != null) {
            Textures.BlockIcons.casingTexturePages[CASING_PAGE][0] = TextureFactory.of(Loaders.FRF_Casings, 0);
        }
        return CASING_INDEX;
    }

    private void raiseFieldCoil(int tier) {
        if (tier > fieldCoilTier) fieldCoilTier = tier;
    }

    private int refineryCasings;

    @Override
    public IStructureDefinition<MTENaquadahFuelLaboratory> getStructureDefinition() {
        return getDefinition();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        refineryCasings = 0;
        fieldCoilTier = 0;
        heatingCoil = HeatingCoilLevel.None;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkCasingMin(errors, refineryCasings, MIN_REFINERY_CASINGS);
        // The design document only makes the maintenance hatch mandatory: every other hatch is optional.
        checkOneMaintenanceHatch(errors);
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
        return new String[] {
            "\u00a7e\u7845\u5ca9\u71c3\u6599\u7814\u7a76\u4e0e\u8bd5\u9a8c\u673a\u00a7r / Naquadah Fuel "
                + "Research and Test Machine",
            "\u00a77The shape is " + StructureBlueprintFile.width(shape)
                + " wide x "
                + StructureBlueprintFile.height(shape)
                + " tall x "
                + StructureBlueprintFile.depth(shape)
                + " deep.",
            "\u00a77A = \u7845\u5ca9\u71c3\u6599\u7cbe\u70bc\u673a\u68b0\u65b9\u5757: every hatch goes here.",
            "\u00a77B = \u529b\u573a\u7ea6\u675f\u7ebf\u5708\u7cfb\u5217 (\u6539\u826f\u578b\u8d85\u5bfc / \u529b\u573a\u7ea6\u675f / "
                + "\u8fdb\u9636 / \u7ec8\u6781): gates the fuel grade.",
            "\u00a77C = \u529b\u573a\u7ea6\u675f\u73bb\u7483, D = \u5de5\u4e1a\u9ad8\u7089\u7ebf\u5708: sets parallel and output.",
            "\u00a77E = \u94d5\u5f3a\u5316\u9632\u8f90\u5c04\u673a\u68b0\u65b9\u5757, F = \u9632\u8f90\u5c04\u94a2\u6846\u67b6.",
            "\u00a7eHint 1 dot: \u00a77Energy, maintenance, input and output hatches - any refinery casing" };
    }

    // ------------------------------------------------------------------ running

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && aTick == 1) {
            ownerUUID = gregtech.common.misc.WirelessNetworkManager.processInitialSettings(aBaseMetaTileEntity);
        }
    }

    /** The wireless account, resolved lazily in case a recipe is checked before the first tick runs. */
    private java.util.UUID wirelessOwner() {
        if (ownerUUID == null) {
            ownerUUID = gregtech.common.misc.WirelessNetworkManager.processInitialSettings(getBaseMetaTileEntity());
        }
        return ownerUUID;
    }

    @Override
    protected gregtech.api.logic.ProcessingLogic createProcessingLogic() {
        return new gregtech.api.logic.ProcessingLogic() {

            @Override
            protected CheckRecipeResult validateRecipe(gregtech.api.util.GTRecipe recipe) {
                // mSpecialValue holds the fuel grade (1..6); the coil it needs comes from the table.
                int grade = recipe.mSpecialValue;
                if (grade < 1 || grade > COIL_REQUIRED.length) return CheckRecipeResultRegistry.NO_RECIPE;
                if (fieldCoilTier < COIL_REQUIRED[grade - 1]) return CheckRecipeResultRegistry.NO_RECIPE;
                wirelessCost = null;
                if (getMachineMode() == MODE_RESEARCH && wirelessEnergyMode) {
                    // Buying the whole run up front, the way the transcendent plasma mixer does: the wireless network
                    // is asked for recipe EU/t x duration x parallels, and the run does not start if that is not there.
                    java.math.BigInteger price = java.math.BigInteger.valueOf(recipe.mEUt)
                        .multiply(java.math.BigInteger.valueOf(recipe.mDuration))
                        .multiply(java.math.BigInteger.valueOf(Math.max(1, maxParallel)));
                    java.math.BigInteger balance = gregtech.common.misc.WirelessNetworkManager
                        .getUserEU(wirelessOwner());
                    if (balance == null || balance.compareTo(price) < 0) {
                        return CheckRecipeResultRegistry.insufficientStartupPower(price);
                    }
                    wirelessCost = price;
                }
                activeGrade = grade;
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            protected CheckRecipeResult onRecipeStart(gregtech.api.util.GTRecipe recipe) {
                if (wirelessCost != null) {
                    java.math.BigInteger price = wirelessCost;
                    wirelessCost = null;
                    if (!gregtech.common.misc.WirelessNetworkManager
                        .addEUToGlobalEnergyMap(wirelessOwner(), price.negate())) {
                        return CheckRecipeResultRegistry.insufficientStartupPower(price);
                    }
                    // The run is already paid for, so the machine must not also draw from any energy hatch.
                    overwriteCalculatedEut(0);
                }
                return super.onRecipeStart(recipe);
            }

            @Override
            protected gregtech.api.util.OverclockCalculator createOverclockCalculator(
                gregtech.api.util.GTRecipe recipe) {
                // The design document fixes every recipe's duration; there is no overclocking.
                return gregtech.api.util.OverclockCalculator.ofNoOverclock(recipe);
            }
        }.setMaxParallelSupplier(this::getTrueParallel)
            .setUnlimitedTierSkips();
    }

    @Override
    public int getMaxParallelRecipes() {
        if (getMachineMode() == MODE_RESEARCH) {
            int tier = Math.max(0, heatingCoil.getTier() + 1);
            // 256, doubled once per heating coil level.
            long parallel = (long) RESEARCH_BASE_PARALLEL << Math.min(tier, 20);
            return (int) Math.min(Integer.MAX_VALUE, parallel);
        }
        return TEST_PARALLEL;
    }

    /** Field-restriction coil tier currently in the structure, 1..4. */
    public int getFieldCoilTier() {
        return fieldCoilTier;
    }

    /** Ordinary heating coil level currently in the structure. */
    public HeatingCoilLevel getHeatingCoil() {
        return heatingCoil;
    }

    public boolean isWirelessEnergyMode() {
        return wirelessEnergyMode;
    }

    public void setWirelessEnergyMode(boolean wireless) {
        wirelessEnergyMode = wireless;
    }

    @Override
    protected void setProcessingLogicPower(gregtech.api.logic.ProcessingLogic logic) {
        super.setProcessingLogicPower(logic);
        logic.setUnlimitedTierSkips();
    }

    // ------------------------------------------------------------------ test mode power

    /**
     * Test mode: the recipe's EU/t is what the fuel <em>yields</em>, not what it costs.
     * <p>
     * Every finished run pays {@code mEUt x parallels x 2^coilTier} out, either into the dynamo hatches or, when the
     * wireless switch is on, straight into the owner's wireless network. The heating coil level doubles the payout once
     * per level, which is what the design document means by "每级线圈方块提供2倍最终能量产出".
     */
    /**
     * Test mode pays out continuously while a recipe runs, the way 大型硅岩反应堆 does it: every tick the run is
     * worth {@code TEST_POWER[grade] x parallels x 2^coilTier} and that goes straight into the dynamo hatches or the
     * wireless network.
     * <p>
     * Paying per tick rather than in one lump at the end avoids having to catch the exact tick a recipe finishes -
     * {@code mProgresstime} is already reset by the time a post-tick handler could look at it, and a {@code % 20}
     * guard only makes it worse.
     */
    @Override
    public boolean onRunningTick(ItemStack aStack) {
        boolean ok = super.onRunningTick(aStack);
        if (getMachineMode() != MODE_TEST) return ok;
        int grade = getCurrentFuelGrade();
        if (grade < 1 || grade > TEST_POWER.length) return ok;
        payOutTestEnergy(getBaseMetaTileEntity(), grade);
        return ok;
    }

    private void payOutTestEnergy(IGregTechTileEntity aBaseMetaTileEntity, int grade) {
        // BigDecimal-free but still unbounded: Mk-VI times 16,777,216 parallels times 2^coil runs to about 1.5e22 EU
        // per tick, which no 64-bit type holds. BigInteger does.
        int coilTier = Math.min(Math.max(0, heatingCoil.getTier() + 1), 30);
        java.math.BigInteger total = java.math.BigInteger.valueOf(TEST_POWER[grade - 1])
            .multiply(java.math.BigInteger.valueOf(Math.max(1, getTrueParallel())))
            .shiftLeft(coilTier);
        if (total.signum() <= 0) return;

        if (wirelessEnergyMode) {
            // The wireless map takes a BigInteger, so the whole payout goes in one call and nothing is lost.
            java.util.UUID owner = gregtech.common.misc.WirelessNetworkManager
                .processInitialSettings(aBaseMetaTileEntity);
            if (owner != null && gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap(owner, total)) {
                return;
            }
            // fall through to the dynamo hatches if the wireless side refuses
        }

        // Dynamo hatches only take a long, so the payout is buffered and drained at a bounded rate instead of being
        // clamped to Long.MAX_VALUE and losing the rest. This is the "several uploads instead of one" the payout needs
        // once it outgrows a single long.
        pendingDynamoEnergy = pendingDynamoEnergy.add(total);
        java.math.BigInteger chunk = pendingDynamoEnergy.min(java.math.BigInteger.valueOf(DYNAMO_CHUNK));
        if (chunk.signum() > 0) {
            addEnergyOutputMultipleDynamos(chunk.longValue(), true);
            pendingDynamoEnergy = pendingDynamoEnergy.subtract(chunk);
        }
        // A buffer that can never drain is just a memory leak; cap it at one chunk worth of backlog.
        java.math.BigInteger cap = java.math.BigInteger.valueOf(DYNAMO_CHUNK)
            .shiftLeft(8);
        if (pendingDynamoEnergy.compareTo(cap) > 0) pendingDynamoEnergy = cap;
    }

    /** The fuel grade of the run in progress, or 0 when there is none. */
    private int getCurrentFuelGrade() {
        return activeGrade;
    }

    /** How much one finished test run pays out, for the tooltip and for anything that wants to show it. */
    public long testPayout() {
        int grade = getCurrentFuelGrade();
        if (grade < 1 || grade > TEST_POWER.length) return 0;
        try {
            long total = Math.multiplyExact(TEST_POWER[grade - 1], (long) Math.max(1, getTrueParallel()));
            int coilTier = Math.min(Math.max(0, heatingCoil.getTier() + 1), 30);
            return Math.multiplyExact(total, 1L << coilTier);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    /**
     * The mode switch button only appears when the GUI is told about the icons through
     * {@code withMachineModeIcons}; filling in the legacy {@code setMachineModeIcons()} alone leaves it out of the
     * window. This is the same wiring 稀土综合处理 and the mega distillation tower use.
     */
    @Override
    protected @javax.annotation.Nonnull gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new NaquadahFuelLaboratoryGui(this).withMachineModeIcons(
            gregtech.api.modularui2.GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            gregtech.api.modularui2.GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_SEPARATOR);
    }

    @Override
    public String getMachineModeKey() {
        return getMachineMode() == MODE_RESEARCH ? "simplification.naqfuel.mode.research"
            : "simplification.naqfuel.mode.test";
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("naqFuelWireless", wirelessEnergyMode);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessEnergyMode = aNBT.getBoolean("naqFuelWireless");
    }

    // ------------------------------------------------------------------ looks

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        // Same front as the Naquadah Fuel Refinery, which is what the design asks for.
        if (side == aFacing) {
            return Textures.BlockIcons.createTextureWithCasing(
                this,
                side,
                aFacing,
                aActive,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW);
        }
        return new ITexture[] { TextureFactory.of(Loaders.FRF_Casings, 0) };
    }

    @Override
    public ITexture getCasingTexture() {
        return TextureFactory.of(Loaders.FRF_Casings, 0);
    }

    // ------------------------------------------------------------------ tooltip

    private static final int TOOLTIP_LINES = 30;

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Naquadah Fuel Research and Test Machine");
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            String key = "simplification.tooltip.naqFuel." + line;
            if (StatCollector.canTranslate(key)) tt.addInfo(StatCollector.translateToLocal(key));
        }
        tt.addSeparator()
            .addUnlimitedTierSkips()
            .addSupportMultiAmp()
            .addSeparator()
            .beginStructureBlock(
                StructureBlueprintFile.width(shape),
                StructureBlueprintFile.height(shape),
                StructureBlueprintFile.depth(shape),
                false)
            .addController("Front face")
            .addCasing(MIN_REFINERY_CASINGS + "+", "硅岩燃料精炼机械方块", false)
            .addEnergyHatch("1+", "Any Naquadah Fuel Refinery Casing", 1)
            .addMaintenanceHatch("1", "Any Naquadah Fuel Refinery Casing", 1)
            .addInputHatch("1+", "Any Naquadah Fuel Refinery Casing", 1)
            .addOutputHatch("1+", "Any Naquadah Fuel Refinery Casing", 1)
            .addInputBus("1+", "Any Naquadah Fuel Refinery Casing", 1)
            .addDynamoHatch("1+", "Any Naquadah Fuel Refinery Casing", 1)
            .toolTipFinisher();
        return tt;
    }
}
