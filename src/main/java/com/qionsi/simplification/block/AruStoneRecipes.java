package com.qionsi.simplification.block;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 阿如石各材质的合成表。
 * <p>
 * The rule is one rule for all thirteen: four stone plus one catalyst makes four of the material, and the same recipe
 * runs backwards to turn the material back into stone. The catalyst is what tells the thirteen apart, because a 2x2 of
 * stone on its own would match every one of them and only the first would ever be reachable.
 * <p>
 * The catalysts are chosen to hint at what the material is - coal for blackstone, lapis for the blue-grey deepslate,
 * and so on - rather than because 1.20.1 uses them.
 */
public final class AruStoneRecipes {

    /**
     * One catalyst per material, indexed the same way as {@link BlockAruStone#NAMES}.
     * <p>
     * {@code null} means "no recipe", which is only used as a safety escape.
     */
    private static final Object[] CATALYSTS = { Items.coal, // polished deepslate
        Items.flint, // deepslate tiles
        Items.coal, // blackstone
        Blocks.sand, // smooth sandstone
        Blocks.sand, // smooth red sandstone
        Items.clay_ball, // smooth stone
        Items.iron_ingot, // polished andesite
        Items.gold_nugget, // polished granite
        Items.quartz, // polished diorite
        Items.redstone, // chiseled red sandstone
        Items.quartz, // smooth quartz
        Items.brick, // brown concrete
        Items.gold_ingot, // gilded blackstone
    };

    private AruStoneRecipes() {}

    /** Registers the thirteen recipes. Called from the post-init so the blocks and items all exist. */
    public static void init() {
        if (ModBlocks.aruStone == null) return;
        int added = 0;
        for (int i = 0; i < BlockAruStone.NAMES.length; i++) {
            Object catalyst = i < CATALYSTS.length ? CATALYSTS[i] : null;
            if (catalyst == null) continue;
            ItemStack out = new ItemStack(ModBlocks.aruStone, 4, i);
            // 4 stone in a 2x2 plus one catalyst -> 4 of the material.
            GameRegistry.addShapelessRecipe(out, Blocks.stone, Blocks.stone, Blocks.stone, Blocks.stone, catalyst);
            // and back again, so a player who changes their mind is not stuck with a wall of it.
            GameRegistry.addShapelessRecipe(
                new ItemStack(Blocks.stone, 4),
                new ItemStack(ModBlocks.aruStone, 1, i),
                new ItemStack(ModBlocks.aruStone, 1, i),
                new ItemStack(ModBlocks.aruStone, 1, i),
                new ItemStack(ModBlocks.aruStone, 1, i),
                catalyst);
            added += 2;
        }
        int shapes = 0;
        // Stairs, slabs and walls follow the vanilla shapes from the material itself.
        for (int i = 0; i < BlockAruStone.NAMES.length; i++) {
            ItemStack material = new ItemStack(ModBlocks.aruStone, 1, i);
            if (ModBlocks.aruStairs[i] != null) {
                GameRegistry.addRecipe(new ItemStack(ModBlocks.aruStairs[i], 4), "X  ", "XX ", "XXX", 'X', material);
                shapes++;
            }
            if (ModBlocks.aruSlabs[i] != null) {
                GameRegistry.addRecipe(new ItemStack(ModBlocks.aruSlabs[i], 6), "XXX", 'X', material);
                shapes++;
            }
        }
        for (int i = 0; i < BlockAruStone.NAMES.length; i++) {
            ItemStack wall = ModBlocks.aruWalls[i] == null ? null : new ItemStack(ModBlocks.aruWalls[i], 6);
            if (wall != null) {
                GameRegistry.addRecipe(wall, "XXX", "XXX", 'X', new ItemStack(ModBlocks.aruStone, 1, i));
                shapes++;
            }
        }
        com.qionsi.simplification.MyMod.LOG
            .info("Registered {} 阿如石 material recipes and {} shape recipes", added, shapes);
    }
}
