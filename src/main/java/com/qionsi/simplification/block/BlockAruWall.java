package com.qionsi.simplification.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWall;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 阿如石墙。
 * <p>
 * {@code BlockWall} draws itself with the icon of the block it was built from, but that block is {@link BlockAruStone}
 * and its icon depends on metadata - which a wall does not carry. So the wall is told which {@code aruStone} metadata
 * it
 * stands for, and every icon lookup is forwarded to that variant.
 */
public class BlockAruWall extends BlockWall {

    /** The {@code aruStone} metadata this wall is made of. */
    private final int stoneMeta;

    public BlockAruWall(Block source, int stoneMeta, String name) {
        super(source);
        this.stoneMeta = stoneMeta;
        setBlockName("simplification.aruWall." + name);
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        // BlockWall registers nothing of its own; the icons come from aruStone.
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
