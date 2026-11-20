package com.qionsi.simplification.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.qionsi.simplification.MyMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 阿如的倒影结构里那些 1.7.10 没有的方块，全部装在一个 Block 里，用 metadata 区分材质。
 * <p>
 * The 1.20.1 schematic uses thirteen blocks that do not exist in 1.7.10 - polished deepslate, deepslate tiles,
 * blackstone, smooth sandstone and the rest. Registering thirteen separate Block classes would mean thirteen classes,
 * thirteen item blocks and thirteen lang entries for what is really one thing: a stone-like block whose only difference
 * is its texture. So they share one block and are told apart by metadata, which is exactly how vanilla handles stone
 * variants.
 * <p>
 * The textures are taken straight out of a 1.20.1 client jar, one per metadata value.
 */
public class BlockAruStone extends Block {

    /** Metadata order; the texture for meta {@code i} is {@code simplification:aru_<NAMES[i]>}. */
    public static final String[] NAMES = { "polished_deepslate", // 0
        "deepslate_tiles", // 1
        "blackstone", // 2
        "smooth_sandstone", // 3
        "smooth_red_sandstone", // 4
        "smooth_stone", // 5
        "polished_andesite", // 6
        "polished_granite", // 7
        "polished_diorite", // 8
        "chiseled_red_sandstone", // 9
        "smooth_quartz", // 10
        "brown_concrete", // 11
        "gilded_blackstone", // 12
    };

    /** Human readable names, for the item tooltips and the ore dictionary. */
    public static final String[] DISPLAY = { "\u78e8\u5236\u6df1\u677f\u5ca9", // polished deepslate
        "\u6df1\u677f\u5ca9\u74e6", // deepslate tiles
        "\u9ed1\u77f3", // blackstone
        "\u5e73\u6ed1\u7802\u5ca9", // smooth sandstone
        "\u5e73\u6ed1\u7ea2\u7802\u5ca9", // smooth red sandstone
        "\u5e73\u6ed1\u77f3\u5934", // smooth stone
        "\u78e8\u5236\u5b89\u5c71\u5ca9", // polished andesite
        "\u78e8\u5236\u82b1\u5c97\u5ca9", // polished granite
        "\u78e8\u5236\u95ea\u957f\u5ca9", // polished diorite
        "\u8e2a\u5236\u7ea2\u7802\u5ca9", // chiseled red sandstone
        "\u5e73\u6ed1\u77f3\u82f1", // smooth quartz
        "\u68d5\u8272\u6df7\u51dd\u571f", // brown concrete
        "\u9576\u91d1\u9ed1\u77f3", // gilded blackstone
    };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockAruStone() {
        super(Material.rock);
        setBlockName("simplification.aruStone");
        setHardness(2.0F);
        setResistance(10.0F);
        setStepSound(soundTypePiston);
        setCreativeTab(CreativeTabs.tabBlock);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            icons[i] = register.registerIcon(MyMod.MODID + ":aru_" + NAMES[i]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (icons == null) return null;
        return icons[clamp(meta)];
    }

    @Override
    public int damageDropped(int meta) {
        return clamp(meta);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < NAMES.length; i++) list.add(new ItemStack(item, 1, i));
    }

    private static int clamp(int meta) {
        return meta < 0 || meta >= NAMES.length ? 0 : meta;
    }
}
