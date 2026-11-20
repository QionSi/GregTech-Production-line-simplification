package com.qionsi.simplification.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 阿如石楼梯。
 * <p>
 * {@code BlockStairs} takes its icons from the block it was built from and its constructor is protected, so this thin
 * subclass exists only to be constructible from {@link ModBlocks} and to forward icon lookups to the right
 * {@code aruStone} metadata - a stair's own metadata is used by the facing and the half, so it cannot name a material.
 */
public class BlockAruStairs extends BlockStairs {

    /** The {@code aruStone} metadata this stair is made of. */
    private final int stoneMeta;

    public BlockAruStairs(Block source, int stoneMeta) {
        super(source, stoneMeta);
        this.stoneMeta = stoneMeta;
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        // Icons come from aruStone, keyed by the metadata this stair was built for.
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return ModBlocks.aruStone == null ? null : ModBlocks.aruStone.getIcon(side, stoneMeta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return ModBlocks.aruStone == null ? null : ModBlocks.aruStone.getIcon(side, stoneMeta);
    }
}
