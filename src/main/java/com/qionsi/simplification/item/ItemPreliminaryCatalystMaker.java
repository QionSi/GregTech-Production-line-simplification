package com.qionsi.simplification.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.qionsi.simplification.MyMod;

/**
 * 初步研究的超维度催化剂制造机 / Preliminary Study: Transcendent Catalyst Maker.
 * <p>
 * A plain item, not a bauble: it is the "preliminary research" stand-in for the 超维度催化剂制造机 / Transcendent Catalyst
 * Maker, and its only purpose is to be the research item the Research Station scans before the real machine can be
 * built on the assembly line. Its texture is the lit front overlay of the Transcendent Plasma Mixer, cropped out of
 * GregTech's own {@code assets/gregtech/textures/blocks/iconsets/OVERLAY_DTPF_ON.png} strip.
 */
public class ItemPreliminaryCatalystMaker extends Item {

    public ItemPreliminaryCatalystMaker() {
        setUnlocalizedName("simplification.preliminaryCatalystMaker");
        setTextureName(MyMod.MODID + ":preliminary_catalyst_maker");
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMisc);
    }
}
