package com.qionsi.simplification.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * 简易净水机的零件：净水厂研究报告与三张净水厂升级芯片。
 * <p>
 * The icon is a hard-coded texture path, on purpose. An earlier version borrowed the icon from another item at
 * {@code registerIcons} time, which works only while the donor's icon happens to be registered first - the order is
 * not guaranteed, and on some players' installs the chip came out wearing the fallback texture instead. A fixed path
 * cannot drift like that.
 * <p>
 * Both paths are real files inside the mods this addon already depends on:
 * <ul>
 * <li>{@code minecraft:book_writable} - {@code assets/minecraft/textures/items/book_writable.png}, the vanilla
 * "book and quill". Note the word order: the unlocalised name is {@code writable_book}, but the texture file is
 * <em>not</em> named that way, which is what made the report render as the missing-texture checkerboard.</li>
 * <li>{@code gregtech:gt.metaitem.01/708} - {@code assets/gregtech/textures/items/gt.metaitem.01/708.png}, which is
 * {@code IDMetaItem01.Tool_DataStick}, GregTech's flash memory.</li>
 * </ul>
 */
public class ItemWaterPurifierPart extends Item {

    public ItemWaterPurifierPart(String name, String textureName) {
        setUnlocalizedName("simplification." + name);
        setTextureName(textureName);
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMisc);
    }
}
