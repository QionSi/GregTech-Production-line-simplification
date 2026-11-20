package com.qionsi.simplification.block;

import java.util.Random;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 阿如石台阶。
 * <p>
 * 1.7.10 splits a slab into two blocks - one for the single slab and one for the double - so each {@code aruStone}
 * metadata needs a pair. Both classes draw their icons from the {@code aruStone} variant they were built from, because
 * slab metadata is used for the half (bottom/top/double) and cannot also carry the material.
 */
public class BlockAruSlab extends BlockSlab {

    /** The {@code aruStone} metadata this slab is made of. */
    private final int stoneMeta;
    /** Whether this is the double block. 1.7.10 keeps this in a protected field with an obfuscated name. */
    private final boolean doubled;
    /** The block this one turns into when two slabs are stacked, or this block's own pair. */
    private final BlockAruSlab pair;

    /** Set after construction, because the two halves refer to each other. */
    private BlockAruSlab other;

    public BlockAruSlab(boolean isDouble, int stoneMeta, String name) {
        super(isDouble, Material.rock);
        this.stoneMeta = stoneMeta;
        this.doubled = isDouble;
        this.pair = null;
        setBlockName("simplification.aruSlab." + (isDouble ? "double." : "single.") + name);
        setHardness(2.0F);
        setResistance(10.0F);
        setStepSound(soundTypePiston);
    }

    /** Links the single and the double block together. */
    public void link(BlockAruSlab other) {
        this.other = other;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        // Icons come from aruStone, keyed by the metadata this slab was built for.
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return ModBlocks.aruStone == null ? null : ModBlocks.aruStone.getIcon(side, stoneMeta);
    }

    @Override
    public String func_150002_b(int meta) {
        return getUnlocalizedName();
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return Item.getItemFromBlock(doubled ? other : this);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return new ItemStack(doubled ? other : this, 1, 0);
    }
}
