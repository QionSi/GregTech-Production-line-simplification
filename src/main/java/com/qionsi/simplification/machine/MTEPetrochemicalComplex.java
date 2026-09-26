package com.qionsi.simplification.machine;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_GLOW;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.qionsi.simplification.MyMod;

import gregtech.api.casing.Casings;
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
 * The shape is written in code: {@link PetrochemicalComplexStructure#defaultBlueprint()} holds the one definition and
 * {@link PetrochemicalComplexStructure#build} turns it into the StructureLib form, so a change to the structure is a
 * plain edit to a Java file. While debugging, an edit inside an existing method body can be applied to the running game
 * with the IDE's HotSwap; anything that adds a field, method or class needs a restart.
 */
public class MTEPetrochemicalComplex extends MTEExtendedPowerMultiBlockBase<MTEPetrochemicalComplex>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** Base amount of parallel recipes this machine can run, before energy hatch bonus. */
    public static final int BASE_PARALLELS = 8;

    /** Solid Steel Machine Casing the shell has to keep, the rest of the marked spots being free for hatches. */
    public static final int MIN_SOLID_STEEL_CASINGS = 10;

    /** Bronze Plated Bricks the shell has to keep. */
    public static final int MIN_BRONZE_CASINGS = 8;

    /**
     * The shape of the machine and the StructureLib definition built from it. See
     * {@link PetrochemicalComplexStructure#shapeText()} for where the shape is written.
     * <p>
     * The pair is refreshed by {@link #getStructureDefinition()} whenever the shape text changes, which is what makes
     * the structure editable without a restart: with the client running under a debugger, editing the strings in
     * {@link PetrochemicalComplexStructure#shapeText()} and letting the IDE swap that method body into the running game
     * is enough for the next structure check to pick the new shape up.
     * <p>
     * Rebuilding is also why the definition is not a constant: StructureLib raises an exception for a shape containing
     * a character it has no element for, and a definition built once in a class initialiser could only ever report that
     * by taking the game down. Here the failure is caught and the machine falls back to a single block, so the game
     * keeps running and the reason is in the log.
     */
    private static IStructureDefinition<MTEPetrochemicalComplex> DEFINITION;

    /** The shape {@link #DEFINITION} was built from, and the text it was written as, so a change can be spotted. */
    private static StructureBlueprint builtFrom;
    private static String[] builtFromText;

    private int mSolidSteelCasings;
    private int mBronzeCasings;

    public MTEPetrochemicalComplex(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPetrochemicalComplex(String aName) {
        super(aName);
    }

    /**
     * Makes sure the definition matches the shape the source currently writes, and returns that shape.
     * <p>
     * Only the shape text is compared, so the usual case costs one array comparison and no parsing at all. The shape is
     * parsed and the definition rebuilt the moment that text changes - which, during development, is what a HotSwapped
     * edit to {@link PetrochemicalComplexStructure#shapeText()} looks like from here.
     */
    private static StructureBlueprint refreshStructure() {
        String[] currentText = PetrochemicalComplexStructure.shapeText();
        if (DEFINITION == null || builtFromText == null || !Arrays.equals(builtFromText, currentText)) {
            StructureBlueprint current = PetrochemicalComplexStructure.defaultBlueprint();
            DEFINITION = buildDefinition(current);
            builtFrom = current;
            builtFromText = currentText;
        }
        return builtFrom;
    }

    /**
     * Builds the structure definition now rather than at the first structure check. Called once, right after the
     * machine is registered, so that a problem with the shape is reported while the log is still short instead of the
     * first time a player builds the machine.
     */
    public static void prepareStructure() {
        refreshStructure();
    }

    private static IStructureDefinition<MTEPetrochemicalComplex> buildDefinition(StructureBlueprint blueprint) {
        try {
            IStructureDefinition<MTEPetrochemicalComplex> definition = PetrochemicalComplexStructure.build(blueprint);
            for (String problem : blueprint.validate()) {
                MyMod.LOG.warn("[petrochemical complex structure] {}", problem);
            }
            PetrochemicalComplexStructure.CasingCounts shell = PetrochemicalComplexStructure.expectedCasings(blueprint);
            MyMod.LOG.info(
                "Petrochemical Complex structure: {} wide x {} tall x {} deep, controller at A/B/C {} (front slice, "
                    + "level {} from the top, column {}), shell of {} steel and {} bronze, of which {} and {} must stay "
                    + "casings",
                blueprint.width(),
                blueprint.height(),
                blueprint.depth(),
                blueprint.offsetSummary(),
                blueprint.offsetB() + 1,
                blueprint.offsetA() + 1,
                shell.solidSteel(),
                shell.bronze(),
                minimumSolidSteelCasings(blueprint),
                minimumBronzeCasings(blueprint));
            return definition;
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build the Petrochemical Complex structure definition; the machine falls back to a single "
                    + "block so the game keeps running.",
                t);
            return PetrochemicalComplexStructure.build(StructureBlueprint.placeholder());
        }
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPetrochemicalComplex(this.mName);
    }

    @Override
    public IStructureDefinition<MTEPetrochemicalComplex> getStructureDefinition() {
        refreshStructure();
        return DEFINITION;
    }

    /** Structure element callback: one more Bronze Plated Bricks block was found. */
    public void bumpBronzeCasings() {
        mBronzeCasings++;
    }

    /** Structure element callback: one more Solid Steel Machine Casing block was found. */
    public void bumpSolidSteelCasings() {
        mSolidSteelCasings++;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mSolidSteelCasings = 0;
        mBronzeCasings = 0;
        StructureBlueprint blueprint = refreshStructure();
        if (!checkPiece(STRUCTURE_PIECE_MAIN, blueprint.offsetA(), blueprint.offsetB(), blueprint.offsetC(), errors))
            return;
        // Only a floor on the casing count, not the shape's full count: every other marked spot is free for a hatch,
        // and a hatch does not count as a casing because the hatch adder stops before the casing element runs.
        checkCasingMin(errors, mSolidSteelCasings, minimumSolidSteelCasings(blueprint));
        checkCasingMin(errors, mBronzeCasings, minimumBronzeCasings(blueprint));
        checkHasAnyEnergy(errors);
        checkOneMaintenanceHatch(errors);
        checkOneMufflerHatch(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
    }

    /**
     * Solid Steel Machine Casing the shell has to keep. The shape marks 41 of them, but all but ten may be replaced by
     * a hatch. Never more than the shape actually has, so that the fallback structure still forms.
     */
    private static int minimumSolidSteelCasings(StructureBlueprint blueprint) {
        return Math.min(
            MIN_SOLID_STEEL_CASINGS,
            PetrochemicalComplexStructure.expectedCasings(blueprint)
                .solidSteel());
    }

    /** Bronze Plated Bricks the shell has to keep. See {@link #minimumSolidSteelCasings}. */
    private static int minimumBronzeCasings(StructureBlueprint blueprint) {
        return Math.min(
            MIN_BRONZE_CASINGS,
            PetrochemicalComplexStructure.expectedCasings(blueprint)
                .bronze());
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        StructureBlueprint blueprint = refreshStructure();
        buildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            hintsOnly,
            blueprint.offsetA(),
            blueprint.offsetB(),
            blueprint.offsetC());
    }

    /**
     * Shown in chat by the Multiblock Structure Hologram Projector when it projects this machine's shape. Returning
     * something useful here is what tells the player the projector recognised the machine at all.
     */
    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        StructureBlueprint blueprint = refreshStructure();
        return new String[] { "\u00a7e\u77f3\u6cb9\u5316\u5de5\u7efc\u5408\u4f53\u00a7r / Petrochemical Complex",
            "\u00a77The controller goes in the front wall. The shape is " + blueprint
                .width() + " wide x " + blueprint.height() + " tall x " + blueprint.depth() + " deep.",
            "\u00a77S = bottom level of the steel tower (energy, maintenance and fluid input hatches).",
            "\u00a77O = steel above it (fluid output hatches).",
            "\u00a77B = Bronze Plated Bricks (item buses, fluid input and output hatches).",
            "\u00a77M = muffler in the middle of the top level, ~ = the controller.",
            "\u00a77Sneak-right-click the controller with the projector to build it automatically." };
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        StructureBlueprint blueprint = refreshStructure();
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            blueprint.offsetA(),
            blueprint.offsetB(),
            blueprint.offsetC(),
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
        // The item's tooltip is built once, the first time something asks for it, so an edit to the shape needs a
        // restart to show up here, even though the structure itself follows the source immediately.
        StructureBlueprint blueprint = refreshStructure();
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
            // Sizes, counts and the controller's own position come from the shape, so an edit to the structure keeps
            // the tooltip correct. beginStructureBlock takes depth, width and height in that order.
            .beginStructureBlock(blueprint.depth(), blueprint.width(), blueprint.height(), true)
            .addController(
                "Front slice, level " + (blueprint.offsetB() + 1)
                    + " from the top, column "
                    + (blueprint.offsetA() + 1))
            .addCasing(minimumBronzeCasings(blueprint) + "+", "Bronze Plated Bricks", false)
            .addCasing(minimumSolidSteelCasings(blueprint) + "+", "Solid Steel Machine Casing", false)
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
}
