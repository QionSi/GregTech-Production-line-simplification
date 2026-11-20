package com.qionsi.simplification.machine;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_RESEARCH_COMPLETER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_RESEARCH_COMPLETER_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_RESEARCH_COMPLETER_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_RESEARCH_COMPLETER_GLOW;

import java.util.ArrayList;
import java.util.List;

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

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import tectech.mechanics.dataTransport.QuantumDataPacket;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataOutput;

/**
 * 阿如的倒影 / Reflection of Aru.
 * <p>
 * The design document's premise: Aru was the virtual paradise the Red King built for the Golden Dreamland. It never
 * opened, was soaked through by forbidden knowledge, and stood as a tomb for thousands of years; when the World Tree
 * was corrupted, the Lord of Wisdom found Aru shared its technology, rewrote it, and put it in the Tree's place as the
 * vessel for the world's data and memory. What is left of the paradise threads still runs underneath, but the promise
 * of the far shore has become the centre of this one.
 * <p>
 * Mechanically it is a computation generator in the style of the Quantum Computer: it burns EU and yields one unit of
 * computation per {@value #EU_PER_COMPUTATION} EU.
 *
 * <h2>The structure</h2>
 *
 * The shape is {@code kongchiwl.schem}, a 279 x 275 x 275 WorldEdit schematic exported from 1.20.1, scaled to two
 * thirds (186 x 183 x 183) by nearest-neighbour resampling. Its single emerald block becomes the controller.
 * <p>
 * The first attempt used the schematic at full size and was unusable: structure checks walked 3.9 million positions,
 * so the projector and the NEI preview both crawled and the preview stopped early. Two thirds brings that down to
 * about 228,000, which is a normal size for GT's larger multiblocks.
 * <p>
 * Air is written as a space, which StructureLib does not check at all, so only the non-air positions have to be
 * filled.
 */
public class MTEAruReflection extends MTEExtendedPowerMultiBlockBase<MTEAruReflection>
    implements ISurvivalConstructable, ICasingTextureProvider {

    /** Structure piece name. */
    public static final String STRUCTURE_PIECE_MAIN = "main";

    /**
     * The blueprint is a placeholder on purpose: the real shape is going to be built in a save and exported with GT's
     * structure scanner, and swapping it in is a matter of dropping the exported file next to this class and pointing
     * {@code STRUCTURE_FILE_PATH} at it. Until then the machine is a single controller block so it can still be
     * placed, opened and inspected.
     */
    private static final String STRUCTURE_FILE_PATH = MyMod.MODID + ":multiblock/aru_reflection";

    private static final String[][] shape = { { "~" } };

    /** Where the {@code ~} sits inside the shape, in the A/B/C order: across, up, deep. */
    public static final int HORIZONTAL_OFF_SET = 0;
    public static final int VERTICAL_OFF_SET = 0;
    public static final int DEPTH_OFF_SET = 0;

    /** The design document asks for at least this many cut sandstone in the shell. */
    public static final int MIN_CUT_SANDSTONE = 200;

    /** EU per unit of computation, as the design document states. */
    public static final int EU_PER_COMPUTATION = 100;

    /**
     * Ceiling on the computation this machine holds: {@code Integer.MAX_VALUE}.
     * <p>
     * Computation travels out as a {@code QuantumDataPacket}, whose payload the Research Station reads as a plain
     * {@code long} but which the research recipe's total is an {@code int} of. Capping here keeps the two from ever
     * disagreeing, and it stops a long-running machine from accumulating a number that no longer fits the recipe
     * system.
     */
    public static final long MAX_COMPUTATION = Integer.MAX_VALUE;

    /**
     * A free casing-texture page for the cut sandstone hatch positions. GT's own casings live on page 0, so a high page
     * keeps out of their way.
     */
    private static final int CASING_PAGE = 120;
    private static final int CASING_INDEX = CASING_PAGE * 128;

    private static IStructureDefinition<MTEAruReflection> DEFINITION;

    private int cutSandstone;

    /**
     * 光学发送接口 / optical transmission connectors, and the cloud computation server hatches that extend them.
     * Both carry computation out of the machine as {@link QuantumDataPacket}s.
     */
    public final List<MTEHatchDataOutput> mDataOutHatches = new ArrayList<>();

    /** Computation this machine has produced so far. */
    private long computation;

    public MTEAruReflection(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEAruReflection(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAruReflection(this.mName);
    }

    // ------------------------------------------------------------------ structure

    /**
     * Reports the blueprint's size and gives the cut sandstone a casing texture.
     * <p>
     * The usual per-symbol counting is skipped on purpose: the blueprint is twenty-one million characters, and walking
     * it twice at startup just to log a number is not worth the seconds.
     */
    /** Reports that the blueprint is not in place yet. */
    public static void prepareStructure() {
        MyMod.LOG.info(
            "Aru's Reflection: the structure is a placeholder ({}x{}x{}); export the real shape from a save and drop "
                + "it in as {}.mb to replace it",
            StructureBlueprintFile.width(shape),
            StructureBlueprintFile.height(shape),
            StructureBlueprintFile.depth(shape),
            STRUCTURE_FILE_PATH);
    }

    private static IStructureDefinition<MTEAruReflection> getDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    private static IStructureDefinition<MTEAruReflection> buildDefinition() {
        try {
            StructureDefinition.Builder<MTEAruReflection> builder = StructureDefinition.builder();
            builder.addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape));
            return builder.build();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not build Aru's Reflection structure definition; falling back to a single block so the game "
                    + "keeps running.",
                t);
            return StructureDefinition.<MTEAruReflection>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "~" } })
                .build();
        }
    }

    @Override
    public IStructureDefinition<MTEAruReflection> getStructureDefinition() {
        return getDefinition();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkOneMaintenanceHatch(errors);
        checkHasAnyEnergy(errors);
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
        return new String[] { "\u00a7e\u963f\u5982\u7684\u5012\u5f71\u00a7r / Reflection of Aru",
            "\u00a77The controller sits where the emerald block was: column " + (HORIZONTAL_OFF_SET + 1)
                + ", level "
                + (VERTICAL_OFF_SET + 1)
                + " from the bottom, depth "
                + (DEPTH_OFF_SET + 1)
                + ".",
            "\u00a77The shape is " + StructureBlueprintFile.width(shape)
                + " wide x "
                + StructureBlueprintFile.height(shape)
                + " tall x "
                + StructureBlueprintFile.depth(shape)
                + " deep.",
            "\u00a77Cut sandstone takes every hatch; at least " + MIN_CUT_SANDSTONE + " of them are in the shell.",
            "\u00a77Air in the original schematic is not required to be air.",
            "\u00a7eHint 1 dot: \u00a77Energy, maintenance, input and output hatches - any cut sandstone" };
    }

    /**
     * Takes a hatch into this machine's list if it is a data output hatch.
     * <p>
     * The hatch is also given this machine's casing texture, so it looks like the cut sandstone it replaced - the
     * design asks for the hatch texture to match the structure block.
     */
    public boolean addDataOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchDataOutput) {
            ((gregtech.api.metatileentity.implementations.MTEHatch) mte).updateTexture(aBaseCasingIndex);
            return mDataOutHatches.add((MTEHatchDataOutput) mte);
        }
        return false;
    }

    // ------------------------------------------------------------------ computation

    /** EU per unit of computation, for the tooltip and any GUI that wants it. */
    public static int euPerComputation() {
        return EU_PER_COMPUTATION;
    }

    /** Computation this machine has produced so far. */
    public long getComputation() {
        return computation;
    }

    /**
     * Adds the computation an amount of spent EU buys.
     * <p>
     * The design document states the ratio plainly: {@value #EU_PER_COMPUTATION} EU per unit. The running total is kept
     * on the machine and saved with it, and is capped at {@value #MAX_COMPUTATION}.
     */
    protected void produceComputation(long euSpent) {
        if (euSpent <= 0) return;
        long produced = euSpent / EU_PER_COMPUTATION;
        if (produced <= 0) return;
        long total = computation + produced;
        computation = total > MAX_COMPUTATION ? MAX_COMPUTATION : total;
    }

    /**
     * Hands the computation produced this tick to every optical / cloud connector.
     * <p>
     * The packet's content <em>is</em> the computation - {@link QuantumDataPacket} stores a single long under the key
     * {@code computation} - so this is the same channel the Quantum Computer uses.
     */
    protected void outputComputation() {
        if (computation <= 0 || mDataOutHatches.isEmpty()) return;
        for (MTEHatchDataOutput hatch : mDataOutHatches) {
            if (hatch == null) continue;
            hatch.providePacket(new QuantumDataPacket(computation));
        }
        computation = 0;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == 0) outputComputation();
    }

    @Override
    protected void setProcessingLogicPower(gregtech.api.logic.ProcessingLogic logic) {
        super.setProcessingLogicPower(logic);
        logic.setUnlimitedTierSkips();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("aruComputation", computation);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        computation = aNBT.getLong("aruComputation");
    }

    // ------------------------------------------------------------------ looks

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return Textures.BlockIcons.createTextureWithCasing(
                this,
                side,
                aFacing,
                aActive,
                OVERLAY_FRONT_RESEARCH_COMPLETER,
                OVERLAY_FRONT_RESEARCH_COMPLETER_GLOW,
                OVERLAY_FRONT_RESEARCH_COMPLETER_ACTIVE,
                OVERLAY_FRONT_RESEARCH_COMPLETER_ACTIVE_GLOW);
        }
        // Every other face: cut sandstone, as the design asks.
        return new ITexture[] { TextureFactory.of(Blocks.sandstone, 2) };
    }

    @Override
    public ITexture getCasingTexture() {
        // The controller wears cut sandstone on every face but the front.
        return TextureFactory.of(Blocks.sandstone, 2);
    }

    // ------------------------------------------------------------------ tooltip

    private static final int TOOLTIP_LINES = 24;

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Computation Generator");
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            String key = "simplification.tooltip.aruReflection." + line;
            if (StatCollector.canTranslate(key)) tt.addInfo(StatCollector.translateToLocal(key));
        }
        tt.addSeparator()
            .addInfo("Consumes " + EU_PER_COMPUTATION + " EU per unit of computation produced.")
            .addUnlimitedTierSkips()
            .addSupportMultiAmp()
            .addSeparator()
            .beginStructureBlock(
                StructureBlueprintFile.width(shape),
                StructureBlueprintFile.height(shape),
                StructureBlueprintFile.depth(shape),
                false)
            .addController("Where the emerald block was")
            .addCasing(MIN_CUT_SANDSTONE + "+", "Cut Sandstone", false)
            .addEnergyHatch("1+", "Any Cut Sandstone", 1)
            .addMaintenanceHatch("1", "Any Cut Sandstone", 1)
            .addInputHatch("1+", "Any Cut Sandstone", 1)
            .addOutputHatch("1+", "Any Cut Sandstone", 1)
            .addInputBus("1+", "Any Cut Sandstone", 1)
            .addOutputBus("1+", "Any Cut Sandstone", 1)
            .toolTipFinisher();
        return tt;
    }
}
