package com.qionsi.simplification.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * 电网升级仓 / Grid Upgrade Hatch.
 *
 * <p>
 * A marker hatch: it holds nothing and does nothing on its own. Its only job is to exist somewhere on the machine, so
 * the controller can look for it and decide whether the wireless network may be used at all.
 */
public class MTEHatchGridUpgrade extends MTEHatch {

    /** {@code iconsets/OVERLAY_FULLAUTOMAINTENANCE}, the icon 创造维护仓 (debug 维护仓) uses. */
    private static IIconContainer face;

    public MTEHatchGridUpgrade(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] {
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("simplification.hatch.gridUpgrade.desc.0"),
                StatCollector.translateToLocal("simplification.hatch.gridUpgrade.desc.1") });
    }

    public MTEHatchGridUpgrade(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("simplification.hatch.gridUpgrade.desc.0"),
            StatCollector.translateToLocal("simplification.hatch.gridUpgrade.desc.1") };
    }

    /**
     * Two different appearances, deliberately.
     * <p>
     * <b>As an item</b> it shows the debug 维护仓 icon ({@code iconsets/OVERLAY_FULLAUTOMAINTENANCE}), so it is
     * recognisable in NEI and in an inventory.
     * <p>
     * <b>Once placed</b> it shows only {@code aBaseTexture} - the casing it replaced, 强化无菌净水厂机械方块 on the Simple
     * Water Purifier - so it disappears into the structure instead of standing out as a foreign part.
     */
    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return texturesFor(aBaseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return texturesFor(aBaseTexture);
    }

    private ITexture[] texturesFor(ITexture aBaseTexture) {
        // No tile entity means this is the inventory / NEI form, which keeps the debug maintenance hatch icon.
        if (getBaseMetaTileEntity() == null) {
            return new ITexture[] { aBaseTexture, TextureFactory.of(face()) };
        }
        return new ITexture[] { aBaseTexture };
    }

    private static IIconContainer face() {
        if (face == null) face = Textures.BlockIcons.custom("iconsets/OVERLAY_FULLAUTOMAINTENANCE");
        return face;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchGridUpgrade(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public int getSizeInventory() {
        return 0;
    }
}
