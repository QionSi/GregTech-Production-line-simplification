package com.qionsi.simplification.block;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.qionsi.simplification.MyMod;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 本 mod 注册的方块。
 * <p>
 * The Aru schematic needs thirteen 1.20.1 materials that 1.7.10 never had, and each of them also appears as stairs, a
 * slab and a wall. Rather than forty-odd Block classes, the material lives in one block ({@link BlockAruStone}) keyed
 * by
 * metadata, and the shape variants are thin wrappers that draw their icons from it. Everything else in the structure is
 * vanilla and is referenced directly.
 */
public final class ModBlocks {

    /** 阿如石：十三种 1.20.1 材质，用 metadata 区分。 */
    public static Block aruStone;

    /** Stairs, indexed the same way as {@link BlockAruStone#NAMES}. */
    public static final Block[] aruStairs = new Block[BlockAruStone.NAMES.length];
    /** Single slabs, indexed the same way. */
    public static final Block[] aruSlabs = new Block[BlockAruStone.NAMES.length];
    /** Walls, indexed the same way. */
    public static final Block[] aruWalls = new Block[BlockAruStone.NAMES.length];
    /** Double slabs, indexed the same way. */
    public static final Block[] aruDoubleSlabs = new Block[BlockAruStone.NAMES.length];

    private static final String ARU_STONE_NAME = "aru_stone";

    private static boolean registered;

    private ModBlocks() {}

    /** Creates and registers every block. Called from the common pre-init; a second call does nothing. */
    public static void register() {
        if (registered) return;
        registered = true;

        try {
            aruStone = new BlockAruStone();
            GameRegistry.registerBlock(aruStone, ItemBlockAruStone.class, ARU_STONE_NAME);
            MyMod.LOG.info(
                "Registered 阿如石 (aru_stone) with {} metadata variants: {}",
                BlockAruStone.NAMES.length,
                String.join(", ", BlockAruStone.NAMES));
        } catch (Throwable t) {
            aruStone = null;
            MyMod.LOG.error("Could not register 阿如石 (aru_stone)", t);
            return;
        }

        for (int i = 0; i < BlockAruStone.NAMES.length; i++) {
            String name = BlockAruStone.NAMES[i];
            try {
                BlockAruStairs stairs = new BlockAruStairs(aruStone, i);
                stairs.setBlockName("simplification.aruStairs." + name);
                stairs.setCreativeTab(CreativeTabs.tabBlock);
                GameRegistry.registerBlock(stairs, "aru_stairs_" + name);
                aruStairs[i] = stairs;

                BlockAruWall wall = new BlockAruWall(aruStone, i, name);
                wall.setCreativeTab(CreativeTabs.tabBlock);
                GameRegistry.registerBlock(wall, "aru_wall_" + name);
                aruWalls[i] = wall;

                BlockAruSlab single = new BlockAruSlab(false, i, name);
                single.setCreativeTab(CreativeTabs.tabBlock);
                GameRegistry.registerBlock(single, "aru_slab_" + name);
                aruSlabs[i] = single;

                BlockAruSlab doubled = new BlockAruSlab(true, i, name);
                GameRegistry.registerBlock(doubled, "aru_double_slab_" + name);
                aruDoubleSlabs[i] = doubled;

                single.link((BlockAruSlab) aruDoubleSlabs[i]);
                ((BlockAruSlab) aruDoubleSlabs[i]).link((BlockAruSlab) single);
            } catch (Throwable t) {
                MyMod.LOG.error("Could not register the stairs/slab/wall of 阿如石 variant " + name, t);
            }
        }
        MyMod.LOG.info("Registered 阿如石 stairs, slabs and walls in {} materials", BlockAruStone.NAMES.length);
    }

    /** The index of a named material, or -1. */
    public static int indexOf(String variant) {
        for (int i = 0; i < BlockAruStone.NAMES.length; i++) {
            if (BlockAruStone.NAMES[i].equals(variant)) return i;
        }
        return -1;
    }

    /** One stack of the named material, or {@code null} when the block is missing. */
    public static ItemStack aruStone(String variant, int amount) {
        if (aruStone == null) return null;
        int i = indexOf(variant);
        if (i < 0) {
            MyMod.LOG.error("No 阿如石 variant is named {}; the known ones are {}", variant, BlockAruStone.NAMES);
            return null;
        }
        return new ItemStack(aruStone, amount, i);
    }

    /** The item form, so the metadata variants get their own names instead of all reading "aru_stone". */
    public static class ItemBlockAruStone extends ItemBlock {

        public ItemBlockAruStone(Block block) {
            super(block);
            setHasSubtypes(true);
            setMaxDamage(0);
        }

        @Override
        public int getMetadata(int meta) {
            return meta;
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            int meta = stack.getItemDamage();
            if (meta < 0 || meta >= BlockAruStone.NAMES.length) meta = 0;
            return super.getUnlocalizedName() + "." + BlockAruStone.NAMES[meta];
        }
    }
}
