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
 * The Molecular Polymer Fabricator's window: GregTech's stock multiblock GUI plus one extra button.
 * <p>
 * That button is the power source switch the design document asks for: with it off the machine draws from its energy
 * hatches like any other multiblock, with it on the whole run is paid for out of the player's wireless network in one
 * go, the way GregTech's Transcendent Plasma Mixer does it.
 * <p>
 * Two things are needed for a button to work in MUI2, and both are easy to forget:
 * <ul>
 * <li>the value has to be registered in {@link #registerSyncValues} with {@code allowC2S()}, otherwise the client can
 * only display it and never change it;</li>
 * <li>the widget has to be added to the button column through {@link #createButtonColumn}, which is what the base GUI
 * renders down the right hand side of the window.</li>
 * </ul>
 */
public class MolecularPolymerFabricatorGui extends MTEMultiBlockBaseGui<MTEMolecularPolymerFabricator> {

    /** Name the sync value is registered under; the button looks it up by the same string. */
    private static final String WIRELESS_ENERGY_SYNC = "molecularPolymerWirelessEnergy";

    public MolecularPolymerFabricatorGui(MTEMolecularPolymerFabricator multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            WIRELESS_ENERGY_SYNC,
            new BooleanSyncValue(multiblock::isWirelessEnergyMode, multiblock::setWirelessEnergyMode).allowC2S());
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createButtonColumn(panel, syncManager).child(createWirelessEnergyButton(syncManager));
    }

    /**
     * The switch itself: the off icon is a crossed out power symbol, the on icon is a lit one, so the button reads the
     * same way GregTech's own power switches do.
     */
    private ToggleButton createWirelessEnergyButton(PanelSyncManager syncManager) {
        return createDisableableToggleButton(
            syncManager,
            syncManager.findSyncHandler(WIRELESS_ENERGY_SYNC, BooleanSyncValue.class),
            () -> true,
            GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_OFF,
            GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_ON,
            this::createWirelessEnergyTooltip);
    }

    private void createWirelessEnergyTooltip(RichTooltip t) {
        t.addLine(IKey.lang("simplification.molecular_polymer.button.wireless_energy"));
        t.addLine(
            IKey.dynamic(
                () -> StatCollector.translateToLocal(
                    multiblock.isWirelessEnergyMode() ? "simplification.molecular_polymer.button.wireless_energy.on"
                        : "simplification.molecular_polymer.button.wireless_energy.off")));
    }
}
