package com.qionsi.simplification.machine;

import java.util.Collections;
import java.util.List;

import net.minecraft.util.StatCollector;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.IGTHatchAdder;

/**
 * 电网升级仓 hatch element for the Simple Water Purifier.
 * <p>
 * Placed in the machine's {@code atLeast(...)} alongside the ordinary hatches, so it can go on any reinforced sterile
 * water plant casing. {@link #count} is what the controller asks to decide whether the wireless network is allowed.
 * <p>
 * This is an enum only because {@link IHatchElement} is implemented as an enum in GT; {@code name()} cannot be
 * overridden (it is final on {@link Enum}), so the display name is overridden instead.
 */
public enum GridUpgradeHatchElement implements IHatchElement<MTESimpleWaterPurifier> {

    INSTANCE;

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return Collections.singletonList(MTEHatchGridUpgrade.class);
    }

    @Override
    public IGTHatchAdder<? super MTESimpleWaterPurifier> adder() {
        return (machine, tileEntity, textureIndex) -> {
            if (tileEntity == null) return false;
            IMetaTileEntity metaTileEntity = tileEntity.getMetaTileEntity();
            return metaTileEntity instanceof MTEHatchGridUpgrade hatch && machine.addGridUpgradeHatch(hatch);
        };
    }

    @Override
    public long count(MTESimpleWaterPurifier machine) {
        return machine.getGridUpgradeHatches()
            .size();
    }

    @Override
    public String getDisplayName() {
        return StatCollector.translateToLocal("simplification.hatch.gridUpgrade.element");
    }
}
