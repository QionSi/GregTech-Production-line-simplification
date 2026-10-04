package com.qionsi.simplification.item;

import java.util.ArrayList;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.event.BaubleEventHandler;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipeBuilder;

/**
 * The item side of the mod: the 初生白枝 / Nascent White Branch and its recipe, plus the plain item
 * 初步研究的超维度催化剂制造机 / Preliminary Study: Transcendent Catalyst Maker and the assembler recipe that makes it.
 */
public final class ModItems {

    /** The registry name of the item inside this mod; with the mod id it is what identifies it everywhere. */
    public static final String ITEM_NAME = "nascent_white_branch";

    /** The registry name of the preliminary study item, which the assembly line recipe uses as its research item. */
    public static final String PRELIMINARY_CATALYST_MAKER_NAME = "preliminary_catalyst_maker";

    /** The ore dictionary entry every sapling - vanilla or modded - is filed under. */
    public static final String SAPLING_ORE = "treeSapling";

    /** The ore dictionary entry GregTech files every wire cutter of every tier under. */
    public static final String WIRE_CUTTER_ORE = "craftingToolWireCutter";

    /** The belt bauble. Set during pre-init and never replaced after that. */
    public static Item nascentWhiteBranch;

    /** The 初步研究的超维度催化剂制造机 / Preliminary Study: Transcendent Catalyst Maker. */
    public static Item preliminaryCatalystMaker;

    private static boolean registered;
    private static boolean recipesRegistered;
    private static boolean assemblerRecipeRegistered;

    private ModItems() {}

    /**
     * Creates and registers the bauble and its event handler. Called from the common pre-init; does nothing on a second
     * call.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        try {
            nascentWhiteBranch = new ItemNascentWhiteBranch();
            GameRegistry.registerItem(nascentWhiteBranch, ITEM_NAME);
            MinecraftForge.EVENT_BUS.register(new BaubleEventHandler());
            MyMod.LOG.info("Registered the Nascent White Branch bauble (belt slot)");
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Nascent White Branch bauble", t);
        }

        try {
            preliminaryCatalystMaker = new ItemPreliminaryCatalystMaker();
            GameRegistry.registerItem(preliminaryCatalystMaker, PRELIMINARY_CATALYST_MAKER_NAME);
            MyMod.LOG.info(
                "Registered the Preliminary Study: Transcendent Catalyst Maker item as {}:{}",
                MyMod.MODID,
                PRELIMINARY_CATALYST_MAKER_NAME);
        } catch (Throwable t) {
            preliminaryCatalystMaker = null;
            MyMod.LOG.error("Could not register the Preliminary Study: Transcendent Catalyst Maker item", t);
        }
    }

    /**
     * Registers the shapeless recipe: any sapling and any wire cutter, in any arrangement.
     * <p>
     * This waits for the post-init instead of running beside the item, because GregTech only files its tools under the
     * {@code craftingToolWireCutter} ore dictionary while its own items are being created - which is after this mod's
     * pre-init. Both ingredients are ore dictionary entries, so every tier of wire cutter and every mod's sapling is
     * accepted.
     */
    public static void registerRecipes() {
        if (recipesRegistered || nascentWhiteBranch == null) return;
        recipesRegistered = true;

        try {
            GameRegistry
                .addRecipe(new ShapelessOreRecipe(new ItemStack(nascentWhiteBranch), SAPLING_ORE, WIRE_CUTTER_ORE));
            MyMod.LOG.info(
                "Registered the Nascent White Branch recipe (any sapling + any wire cutter); the ore dictionary knows {} saplings and {} wire cutters",
                oreCount(SAPLING_ORE),
                oreCount(WIRE_CUTTER_ORE));
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Nascent White Branch recipe", t);
        }

        registerAssemblerRecipe();
    }

    /**
     * The assembler recipe of the 初步研究的超维度催化剂制造机 / Preliminary Study: Transcendent Catalyst Maker:
     * circuit 15, one 超维度等离子搅拌机 / Transcendent Plasma Mixer and one 闪存 / Data Stick, for one of the item.
     * <p>
     * This is the study model of the Transcendent Catalyst Maker, so what it is assembled from is the machine the
     * design document points at - GregTech's own Transcendent Plasma Mixer,
     * {@link ItemList#Machine_Multi_TranscendentPlasmaMixer} -
     * plus the data stick the Research Station writes its results onto, {@link ItemList#Tool_DataStick}. Both stacks
     * are
     * looked up with {@link ItemList#get(long)} and their display names are written into the log, because the item a
     * player sees in NEI is the only thing that can be checked against the design document.
     */
    private static void registerAssemblerRecipe() {
        if (assemblerRecipeRegistered || preliminaryCatalystMaker == null) return;
        assemblerRecipeRegistered = true;

        try {
            ItemStack plasmaMixer = ItemList.Machine_Multi_TranscendentPlasmaMixer.get(1);
            ItemStack dataStick = ItemList.Tool_DataStick.get(1);
            if (isMissing(plasmaMixer) || isMissing(dataStick)) {
                MyMod.LOG.error(
                    "Could not look up every ingredient of the Preliminary Study: Transcendent Catalyst Maker; the "
                        + "assembler recipe is not registered. Transcendent Plasma Mixer = {}, Data Stick = {}",
                    describe(plasmaMixer),
                    describe(dataStick));
                return;
            }

            GTRecipeBuilder.builder()
                .circuit(15)
                .itemInputs(plasmaMixer, dataStick)
                .itemOutputs(new ItemStack(preliminaryCatalystMaker, 1))
                .duration(1800)
                .eut(2048)
                .addTo(RecipeMaps.assemblerRecipes);

            MyMod.LOG.info(
                "Registered the assembler recipe of the Preliminary Study: Transcendent Catalyst Maker "
                    + "(circuit 15 + 1x {} + 1x {})",
                describe(plasmaMixer),
                describe(dataStick));
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not register the assembler recipe of the Preliminary Study: Transcendent Catalyst Maker",
                t);
        }
    }

    /** Whether a looked-up ingredient is unusable, i.e. absent or an empty stack. */
    private static boolean isMissing(ItemStack stack) {
        return stack == null || stack.getItem() == null;
    }

    /** How an ingredient is shown, for the log: its display name and how many there are of it. */
    private static String describe(ItemStack stack) {
        if (stack == null) return "<null>";
        if (stack.getItem() == null) return "<empty stack>";
        String name = stack.getDisplayName();
        return (name == null ? stack.getUnlocalizedName() : name.replaceAll("\u00a7.", "")) + " x" + stack.stackSize;
    }

    /**
     * Logs how the item is registered and which numeric id it ended up with.
     * <p>
     * Nothing in this mod picks that number: {@code GameRegistry.registerItem} only takes a name, and FML hands out the
     * first free id while the game starts. Printing it is the only way for a pack author to see which id was taken -
     * and
     * to see that this mod cannot have caused an id clash, because it never asked for a particular id. It is logged in
     * the post-init, by which time FML has finished handing ids out.
     */
    public static void logRegistration() {
        if (nascentWhiteBranch != null) {
            MyMod.LOG.info(
                "The Nascent White Branch is registered by name as {}:{}; FML assigned it the numeric item id {}",
                MyMod.MODID,
                ITEM_NAME,
                Item.getIdFromItem(nascentWhiteBranch));
        }
        if (preliminaryCatalystMaker != null) {
            MyMod.LOG.info(
                "The Preliminary Study: Transcendent Catalyst Maker is registered by name as {}:{}; FML assigned it "
                    + "the numeric item id {} and it is shown as '{}'",
                MyMod.MODID,
                PRELIMINARY_CATALYST_MAKER_NAME,
                Item.getIdFromItem(preliminaryCatalystMaker),
                new ItemStack(preliminaryCatalystMaker).getDisplayName());
        }
    }

    private static int oreCount(String oreName) {
        ArrayList<ItemStack> entries = OreDictionary.getOres(oreName);
        return entries == null ? 0 : entries.size();
    }
}
