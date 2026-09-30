package com.qionsi.simplification.item;

import net.minecraft.init.Items;
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

    /** The belt bauble. Set during pre-init and never replaced after that. */
    public static Item nascentWhiteBranch;

    private static boolean registered;

    private ModItems() {}

    /**
     * Creates and registers the bauble, its event handler and its recipe. Called from the common pre-init; does nothing
     * on a second call.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        try {
            nascentWhiteBranch = new ItemNascentWhiteBranch();
            GameRegistry.registerItem(nascentWhiteBranch, "nascent_white_branch");
            MinecraftForge.EVENT_BUS.register(new BaubleEventHandler());
            MyMod.LOG.info("Registered the Nascent White Branch bauble (belt slot)");
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Nascent White Branch bauble", t);
            return;
        }

        registerRecipe();
    }

    /**
     * Any sapling and a pair of shears, in any arrangement. The sapling is taken from the {@code treeSapling} ore
     * dictionary so that every mod's sapling works, exactly as the request asked for.
     */
    private static void registerRecipe() {
        try {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(new ItemStack(nascentWhiteBranch), "treeSapling", new ItemStack(Items.shears)));
            MyMod.LOG.info(
                "Registered the Nascent White Branch recipe (any sapling + shears); the treeSapling ore dictionary entry exists: {}",
                OreDictionary.doesOreNameExist("treeSapling"));
        } catch (Throwable t) {
            MyMod.LOG.error("Could not register the Nascent White Branch recipe", t);
        }
    }
}
