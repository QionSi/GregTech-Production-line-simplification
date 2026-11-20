package com.qionsi.simplification.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.StatCollector;

import gregtech.api.util.GTUtility;
import gregtech.nei.RecipeDisplayInfo;
import gregtech.nei.formatter.INEISpecialInfoFormatter;

/**
 * 在 NEI 的试验模式配方页上画出真实的发电功率。
 * <p>
 * The recipe's {@code mEUt} is deliberately zero - the test mode produces power, it does not consume it - so the
 * standard NEI line would read "0 EU/t". The real figure lives in {@link NaquadahFuelPowerMetadata} as a {@code long}
 * and is drawn here instead, exactly the way 大型硅岩反应堆's {@code NaquadahReactorFormat} does it.
 */
public class NaquadahFuelPowerFormatter implements INEISpecialInfoFormatter {

    @Override
    public List<String> format(RecipeDisplayInfo recipeInfo) {
        Long power = recipeInfo.recipe.getMetadataOrDefault(NaquadahFuelPowerMetadata.INSTANCE, 0L);
        if (power == null || power <= 0) return Collections.emptyList();
        List<String> lines = new ArrayList<>(1);
        lines.add(
            StatCollector.translateToLocal("simplification.naqfuel.nei.power")
                + GTUtility.formatShortenedLong(power.longValue())
                + " EU/t");
        return lines;
    }
}
