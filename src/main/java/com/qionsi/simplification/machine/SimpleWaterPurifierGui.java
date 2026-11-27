package com.qionsi.simplification.machine;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * The Simple Water Purifier's window: the stock multiblock GUI plus the wireless power switch the design document asks
 * for. The switch works exactly like the one on the 分子聚合物制造机 / Molecular Polymer Fabricator.
 */
public class SimpleWaterPurifierGui extends MTEMultiBlockBaseGui<MTESimpleWaterPurifier> {

    private static final String WIRELESS_SYNC = "simpleWaterPurifierWireless";

    public SimpleWaterPurifierGui(MTESimpleWaterPurifier multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            WIRELESS_SYNC,
            new BooleanSyncValue(multiblock::isWirelessEnergyMode, multiblock::setWirelessEnergyMode).allowC2S());
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createButtonColumn(panel, syncManager).child(createWirelessButton(syncManager));
    }

    private ToggleButton createWirelessButton(PanelSyncManager syncManager) {
        return createDisableableToggleButton(
            syncManager,
            syncManager.findSyncHandler(WIRELESS_SYNC, BooleanSyncValue.class),
            () -> true,
            GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_OFF,
            GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_ON,
            this::createWirelessTooltip);
    }

    private void createWirelessTooltip(RichTooltip t) {
        t.addLine(IKey.lang("simplification.simple_water.button.wireless"));
        t.addLine(
            IKey.dynamic(
                () -> StatCollector.translateToLocal(
                    multiblock.isWirelessEnergyMode() ? "simplification.simple_water.button.wireless.on"
                        : "simplification.simple_water.button.wireless.off")));
    }
}
