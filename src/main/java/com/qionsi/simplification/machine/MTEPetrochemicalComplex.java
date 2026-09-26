package com.qionsi.simplification.machine;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_GLOW;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

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
 * The shape is not written here: it is read from the {@code [structure]} section of
 * {@code config/simplification/petrochemical_complex_structure.cfg} by {@link PetrochemicalComplexStructure}, so the
 * structure can be changed and applied with {@code /simplification reload} without recompiling or restarting. The
 * shipped default is 3 wide x 8 tall x 3 deep, Bronze Plated Bricks for the bottom four stages and Solid Steel
 * Machine Casing above.
 */
public class MTEPetrochemicalComplex extends MTEExtendedPowerMultiBlockBase<MTEPetrochemicalComplex>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** Base amount of parallel recipes this machine can run, before energy hatch bonus. */
    public static final int BASE_PARALLELS = 8;

    /**
     * The structure every machine instance currently uses. Replaced wholesale by
     * {@link #setStructureBlueprint(StructureBlueprint)} when the file is reloaded, which is why it is a mutable
     * holder rather than a constant.
     */
    /**
     * The structure every machine instance currently uses. Replaced wholesale by
     * {@link #setStructureBlueprint(StructureBlueprint)} when the file is reloaded, which is why it is a mutable
     * holder rather than a constant.
     * <p>
     * The initial value is built inside a try/catch: a class initialiser cannot throw, or the machine would fail to
     * register and the game would refuse to start. If anything goes wrong here the machine falls back to the built-in
     * shape and the reason is logged.
     */
    private static volatile StructureBlueprint blueprint;

    private static volatile IStructureDefinition<MTEPetrochemicalComplex> definition;

    static {
        try {
            blueprint = PetrochemicalComplexStructure.defaultBlueprint();
            definition = PetrochemicalComplexStructure.build(blueprint);
        } catch (Throwable t) {
            com.qionsi.simplification.MyMod.LOG.error(
                "Could not build the Petrochemical Complex structure definition; the machine will register with a "
                    + "single block so the game can still start. Fix the structure file and run /simplification reload.",
                t);
            blueprint = StructureBlueprint.placeholder();
            definition = PetrochemicalComplexStructure.build(blueprint);
        }
    }

    /** Bumped on every reload so machines notice the structure changed. */
    private static volatile int structureRevision;

    private int seenRevision = -1;

    private int mSolidSteelCasings;
    private int mBronzeCasings;

    public MTEPetrochemicalComplex(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPetrochemicalComplex(String aName) {
        super(aName);
    }

    /**
     * Swaps in a new shape. Called on the server thread during a reload; every machine picks the change up on its next
     * structure check.
     */
    public static void setStructureBlueprint(StructureBlueprint newBlueprint) {
        blueprint = newBlueprint;
        definition = PetrochemicalComplexStructure.build(newBlueprint);
        structureRevision++;
    }

    /** The shape currently in use. */
    public static StructureBlueprint currentBlueprint() {
        return blueprint;
    }

    /** Minimum casings the current shape asks for. */
    public static PetrochemicalComplexStructure.CasingCounts expectedCasings() {
        return PetrochemicalComplexStructure.expectedCasings(blueprint);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPetrochemicalComplex(this.mName);
    }

    @Override
    public IStructureDefinition<MTEPetrochemicalComplex> getStructureDefinition() {
        return definition;
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
        StructureBlueprint current = blueprint;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, current.offsetA(), current.offsetB(), current.offsetC(), errors)) return;
        PetrochemicalComplexStructure.CasingCounts minimum = PetrochemicalComplexStructure.expectedCasings(current);
        checkCasingMin(errors, mSolidSteelCasings, minimum.solidSteel());
        checkCasingMin(errors, mBronzeCasings, minimum.bronze());
        checkHasAnyEnergy(errors);
        checkOneMaintenanceHatch(errors);
        checkOneMufflerHatch(errors);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        seenRevision = structureRevision;
    }

    /**
     * Re-checks the structure when the file has been reloaded, so a changed shape takes effect on machines that are
     * already built instead of only on ones placed afterwards.
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (seenRevision != structureRevision && aBaseMetaTileEntity.isServerSide()) {
            seenRevision = structureRevision;
            // Ask the base class to re-run the structure check on one of its next few ticks.
            mStructureChanged = true;
            mUpdated = true;
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        StructureBlueprint current = blueprint;
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, current.offsetA(), current.offsetB(), current.offsetC());
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        StructureBlueprint current = blueprint;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            current.offsetA(),
            current.offsetB(),
            current.offsetC(),
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
        StructureBlueprint shape = blueprint;
        PetrochemicalComplexStructure.CasingCounts minimum = PetrochemicalComplexStructure.expectedCasings(shape);
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
            .addSeparator()
            // Sizes come from the shape in use, so the tooltip stays correct when the structure file is edited.
            .beginStructureBlock(shape.width(), shape.depth(), shape.height(), true)
            .addController("Column " + shape.offsetA() + ", stage " + (shape.offsetB() + 1) + ", front row")
            .addCasing(minimum.bronze() + "+", "Bronze Plated Bricks", false)
            .addCasing(minimum.solidSteel() + "+", "Solid Steel Machine Casing", false)
            .addEnergyHatch("1+", "Any Solid Steel Machine Casing marked S", 1)
            .addMaintenanceHatch("1", "Any Solid Steel Machine Casing marked S", 1)
            .addInputHatch("1+", "Any Solid Steel Machine Casing marked S", 1)
            .addInputBus("1+", "Any Bronze Plated Bricks", 2)
            .addOutputBus("1+", "Any Bronze Plated Bricks or Steel marked O", 2, 4)
            .addOutputHatch("1+", "Any Bronze Plated Bricks or Steel marked O", 2, 4)
            .addMufflerHatch("1", "The position marked M", 3)
            .addAir("Inside the structure")
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setMaxParallelSupplier(this::getTrueParallel)
            .enablePerfectOverclock();
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
