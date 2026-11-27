package com.qionsi.simplification.machine;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 硅岩燃料研究与试验机的窗口：标准多方块界面加上无线电网开关。
 * <p>
 * The switch does opposite things in the two modes, which is why the tooltip spells it out: in research mode the
 * machine <em>draws</em> power from the wireless network instead of its energy hatches, and in test mode it
 * <em>uploads</em> the power it produces instead of feeding the dynamo hatches.
 */
public class NaquadahFuelLaboratoryGui extends MTEMultiBlockBaseGui<MTENaquadahFuelLaboratory> {

    private static final String WIRELESS_SYNC = "naqFuelWireless";

    public NaquadahFuelLaboratoryGui(MTENaquadahFuelLaboratory multiblock) {
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
    protected Flow createButtonColumn(com.cleanroommc.modularui.screen.ModularPanel panel,
        PanelSyncManager syncManager) {
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
        t.addLine(IKey.lang("simplification.naqfuel.button.wireless"));
        t.addLine(
            IKey.dynamic(
                () -> StatCollector.translateToLocal(
                    multiblock.isWirelessEnergyMode() ? "simplification.naqfuel.button.wireless.on"
                        : "simplification.naqfuel.button.wireless.off")));
    }
}
