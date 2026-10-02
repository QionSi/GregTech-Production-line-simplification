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

/**
 * The item side of the mod: the 初生白枝 / Nascent White Branch and the recipe that makes it.
 */
public final class ModItems {

    /** The registry name of the item inside this mod; with the mod id it is what identifies it everywhere. */
    public static final String ITEM_NAME = "nascent_white_branch";

    /** The ore dictionary entry every sapling - vanilla or modded - is filed under. */
    public static final String SAPLING_ORE = "treeSapling";

    /** The ore dictionary entry GregTech files every wire cutter of every tier under. */
    public static final String WIRE_CUTTER_ORE = "craftingToolWireCutter";

    /** The belt bauble. Set during pre-init and never replaced after that. */
    public static Item nascentWhiteBranch;

    private static boolean registered;
    private static boolean recipesRegistered;

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
        if (nascentWhiteBranch == null) return;
        MyMod.LOG.info(
            "The Nascent White Branch is registered by name as {}:{}; FML assigned it the numeric item id {}",
            MyMod.MODID,
            ITEM_NAME,
            Item.getIdFromItem(nascentWhiteBranch));
    }

    private static int oreCount(String oreName) {
        ArrayList<ItemStack> entries = OreDictionary.getOres(oreName);
        return entries == null ? 0 : entries.size();
    }
}
