package com.qionsi.simplification.recipe;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MyMod;

import goodgenerator.items.GGMaterial;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;

/**
 * 硅岩燃料研究与试验机 的配方。
 * <p>
 * Two pools, one per mode:
 * <ul>
 * <li><b>研究模式</b> - feedstock in, 硅岩基流体燃料 Mk-I .. Mk-VI out. 5 s each, 10 s for Mk-VI.</li>
 * <li><b>试验模式</b> - the same feedstock, burned for power. The fuel it leaves behind is the "枯竭的" grade.</li>
 * </ul>
 *
 * <h2>The coil gate</h2>
 *
 * Each recipe stores the field-restriction coil tier it needs in {@code mSpecialValue}, and the machine refuses
 * anything above the coil that is actually in the structure:
 *
 * <pre>
 * Mk-I, Mk-II   : 1  (改良型超导线圈 or better)
 * Mk-III        : 2  (力场约束线圈 or better)
 * Mk-IV, Mk-V   : 3  (进阶力场约束线圈 or better)
 * Mk-VI         : 4  (终极力场约束线圈)
 * </pre>
 *
 * <h2>Materials the document names but GTNH spells differently</h2>
 *
 * <ul>
 * <li>泰伯利亚粉 - {@code WerkstoffLoader.Tiberium.get(OrePrefixes.dust, n)}, Bartworks' Werkstoff. It has to be
 * referenced directly rather than through the ore dictionary: the dictionary entry is only added once
 * {@code WerkstoffLoader} has run, which is after this class registers its recipes.</li>
 * <li>彩色玻璃粉 - GT++'s Chromatic Glass, again through the ore dictionary.</li>
 * <li>浓缩原始恒星等离子体混合物 - {@code Materials.RawStarMatter}, what the Eye of Harmony produces.</li>
 * <li>熔融三元金属 - {@code Materials.Trinium}, GT's 三元金属.</li>
 * <li>熔融时空 - {@code Materials.SpaceTime}.</li>
 * <li>熔融无尽 - {@code Materials.Infinity}.</li>
 * </ul>
 */
public final class NaquadahFuelRecipes {

    /** 5 seconds, the duration of every grade but Mk-VI. */
    private static final int SECONDS_5 = 5 * 20;

    /**
     * The design document's 配方发电功率 per grade, indexed by grade - 1.
     * <p>
     * Mk-II and above exceed {@code Integer.MAX_VALUE}, which is why the recipes carry their grade in
     * {@code mSpecialValue} and {@code LNG_BASIC_OUTPUT} only holds a clamped copy for display.
     */
    private static final long[] TEST_POWER = { 314_572_800L, 40_265_318_400L, 1_288_490_183_680L, 4_123_168_587_780L,
        230_897_440_915_000L, 923_589_763_662_000L };

    private static boolean registered;
    private static boolean deferred;

    /** Ore dictionary names that were not found, so each is only reported once. */
    private static final java.util.Set<String> MISSING_INGREDIENTS = new java.util.HashSet<>();

    private NaquadahFuelRecipes() {}

    /** A grade of fuel, its inputs, power and coil requirement. */
    private static final class Grade {

        final int index;
        final int circuit;
        final int euPerTick;
        final int durationTicks;
        final int coilTier;
        final int testEuPerTick;
        final int testDurationTicks;

        Grade(int index, int circuit, int euPerTick, int durationTicks, int coilTier, int testEuPerTick,
            int testDurationTicks) {
            this.index = index;
            this.circuit = circuit;
            this.euPerTick = euPerTick;
            this.durationTicks = durationTicks;
            this.coilTier = coilTier;
            this.testEuPerTick = testEuPerTick;
            this.testDurationTicks = testDurationTicks;
        }
    }

    private static final Grade[] GRADES = { new Grade(1, 1, 32_000, SECONDS_5, 1, 314_572_800, 256 * 20),
        new Grade(2, 2, 131_000, SECONDS_5, 1, 40_265_318, 256 * 20),
        new Grade(3, 3, 520_000, SECONDS_5, 2, 1_288_490, 350 * 20),
        new Grade(4, 4, 2_090_000, SECONDS_5, 3, 4_123_168, 480 * 20),
        new Grade(5, 5, 8_100_000, SECONDS_5, 3, 230_897_440, 500 * 20),
        new Grade(6, 6, 32_000_000, 10 * 20, 4, 923_589_763, 550 * 20), };

    public static void init() {
        if (registered) return;
        // Everything this recipe needs - Bartworks' Werkstoff fluids, GT++'s Chromatic Glass - only exists after
        // GregTech's postload. Running earlier threw a NullPointerException out of Werkstoff.getFluidOrGas and took the
        // entire pool with it, which is why NEI showed no recipe page at all.
        if (!deferred) {
            deferred = true;
            MyMod.LOG.info("The Naquadah fuel recipes will be registered after GregTech's postload.");
            gregtech.api.GregTechAPI.sAfterGTPostload.add(NaquadahFuelRecipes::init);
            return;
        }
        registered = true;

        int research = 0;
        int test = 0;
        for (Grade g : GRADES) {
            if (registerResearch(g)) research++;
            if (registerTest(g)) test++;
        }
        MyMod.LOG.info("Registered {} Naquadah fuel research recipes and {} test recipes", research, test);
        registerController();
    }

    private static boolean registerResearch(Grade g) {
        ItemStack[] solids = solids(g.index);
        FluidStack[] fluids = fluids(g.index);
        FluidStack out = fuel(g.index, false);
        if (solids == null || fluids == null || out == null) {
            MyMod.LOG.error("Skipping Naquadah fuel research recipe Mk-{}: an ingredient is missing", g.index);
            return false;
        }
        GTRecipeBuilder b = GTRecipeBuilder.builder()
            .circuit(g.circuit)
            .itemInputsUnsafe(solids)
            .fluidInputs(fluids)
            .fluidOutputs(out)
            .duration(g.durationTicks)
            .eut(g.euPerTick)
            .specialValue(g.index);
        b.addTo(com.qionsi.simplification.recipe.ModRecipeMaps.naquadahFuelResearchRecipes);
        return true;
    }

    /**
     * 试验模式: burns 1250 mB of the fuel itself and leaves 1250 mB of the depleted grade behind.
     * <p>
     * The design document's table repeats the whole feedstock list here, which made the NEI page eight items and five
     * fluids wide and never rendered. Burning the fuel you already made is also what the machine's own description
     * says the test mode does, and it matches how 大型硅岩反应堆 reads its fuel recipes: one fluid in, one fluid out,
     * with the power in the recipe rather than in the inputs.
     * <p>
     * The payout is <em>not</em> taken from {@code mEUt} - those figures run to 9.2e14 and do not fit the field. The
     * machine reads {@code mSpecialValue} (the fuel grade, 1..6) and looks the power up in its own table.
     */
    /**
     * 试验模式: the same feedstock as the research mode, burned for power, leaving the depleted grade behind.
     * <p>
     * The NEI page is wide (eight items, five fluids) but the pool is sized for it - {@code maxIO(16, 4, 8, 4)} on the
     * test map - and the power is drawn by {@link NaquadahFuelPowerFormatter} from a {@code long} metadata rather than
     * from {@code mEUt}, which is zero because the mode produces power instead of consuming it.
     */
    private static boolean registerTest(Grade g) {
        ItemStack[] solids = solids(g.index);
        FluidStack[] fluids = fluids(g.index);
        FluidStack out = fuel(g.index, true);
        if (solids == null || fluids == null || out == null) {
            MyMod.LOG.error("Skipping Naquadah fuel test recipe Mk-{}: an ingredient is missing", g.index);
            return false;
        }
        GTRecipeBuilder.builder()
            .circuit(g.circuit)
            .itemInputsUnsafe(solids)
            .fluidInputs(fluids)
            .fluidOutputs(out)
            .duration(g.testDurationTicks)
            .eut(0)
            .specialValue(g.index)
            .metadata(NaquadahFuelPowerMetadata.INSTANCE, TEST_POWER[g.index - 1])
            .metadata(
                gregtech.api.util.GTRecipeConstants.LNG_BASIC_OUTPUT,
                (int) Math.min(TEST_POWER[g.index - 1], Integer.MAX_VALUE))
            .addTo(com.qionsi.simplification.recipe.ModRecipeMaps.naquadahFuelTestRecipes);
        return true;
    }

    /**
     * The solid inputs of a grade.
     * <p>
     * Mk-I .. Mk-III share one list; Mk-IV onwards scale the amounts up and swap some ingredients, exactly as the
     * design document's table does.
     */
    private static ItemStack[] solids(int grade) {
        ItemStack naquadah = dust(Materials.Naquadah, 8);
        ItemStack draconium = dust(Materials.Draconium, 1);
        ItemStack adamantium = dust(Materials.Adamantium, 1);
        ItemStack blaze = new ItemStack(Items.blaze_powder, 10);
        ItemStack plutonium = dust(Materials.Plutonium, 5);
        ItemStack silicon = dust(Materials.Silicon, 8);
        // 泰伯利亚粉 comes from Bartworks' Werkstoff, not from the ore dictionary at registration time: the
        // dictionary entry only exists after WerkstoffLoader has run, which is later than this class.
        ItemStack tiberium = bartworks.system.material.WerkstoffLoader.Tiberium == null ? null
            : bartworks.system.material.WerkstoffLoader.Tiberium.get(OrePrefixes.dust, 1);
        if (naquadah == null || draconium == null || adamantium == null || plutonium == null || silicon == null) {
            return null;
        }
        return switch (grade) {
            case 1 -> new ItemStack[] { naquadah, draconium, adamantium, blaze, plutonium, tiberium, silicon,
                dust(Materials.Gallium, 1) };
            case 2 -> new ItemStack[] { naquadah, draconium, adamantium, blaze, plutonium, tiberium, silicon,
                dust(Materials.Americium, 2) };
            case 3 -> new ItemStack[] { naquadah, draconium, adamantium, blaze, plutonium, tiberium, silicon,
                dust("ChromaticGlass", 36) };
            case 4 -> new ItemStack[] { dust(Materials.Naquadah, 160), dust(Materials.Draconium, 20),
                dust(Materials.Adamantium, 20), new ItemStack(Items.blaze_powder, 200), dust(Materials.Plutonium, 100),
                tiberium, dust(Materials.Silicon, 160), dust("ChromaticGlass", 135),
                block(Materials.DraconiumAwakened, 64) };
            case 5 -> new ItemStack[] { dust(Materials.Naquadah, 640), dust(Materials.Draconium, 200),
                dust(Materials.Adamantium, 100), new ItemStack(Items.blaze_powder, 400), tiberium };
            case 6 -> new ItemStack[] { dust(Materials.Naquadah, 1280), dust(Materials.Draconium, 800),
                dust(Materials.Adamantium, 800), new ItemStack(Items.blaze_powder, 800), tiberium };
            default -> null;
        };
    }

    /** The fluid inputs of a grade. */
    private static FluidStack[] fluids(int grade) {
        FluidStack nitrogen = gas(Materials.Nitrogen, 670);
        FluidStack oxygen = gas(Materials.Oxygen, 1_000);
        FluidStack hydrogen = gas(Materials.Hydrogen, 1_330);
        if (nitrogen == null || oxygen == null || hydrogen == null) return null;
        return switch (grade) {
            case 1 -> new FluidStack[] { nitrogen, oxygen, hydrogen, gas(Materials.Radon, 1_125) };
            case 2, 3 -> new FluidStack[] { nitrogen, oxygen, hydrogen };
            case 4 -> new FluidStack[] { gas(Materials.Nitrogen, 13_400), gas(Materials.Oxygen, 20_000),
                gas(Materials.Hydrogen, 26_600) };
            case 5 -> new FluidStack[] { gas(Materials.Nitrogen, 201_000), gas(Materials.Oxygen, 310_000),
                gas(Materials.Hydrogen, 266_000), fluid(Materials.Infinity, 4_608) };
            case 6 -> new FluidStack[] { gas(Materials.Nitrogen, 2_010_000), gas(Materials.Oxygen, 3_100_000),
                gas(Materials.Hydrogen, 2_660_000), fluid(Materials.RawStarMatter, 88),
                fluid(Materials.SpaceTime, 144) };
            default -> null;
        };
    }

    /** The fuel a grade produces, or the depleted fuel in test mode. */
    private static FluidStack fuel(int grade, boolean depleted) {
        return switch (grade) {
            case 1 -> werkstoff(depleted ? GGMaterial.naquadahBasedFuelMkIDepleted : GGMaterial.naquadahBasedFuelMkI);
            case 2 -> werkstoff(depleted ? GGMaterial.naquadahBasedFuelMkIIDepleted : GGMaterial.naquadahBasedFuelMkII);
            case 3 -> werkstoff(
                depleted ? GGMaterial.naquadahBasedFuelMkIIIDepleted : GGMaterial.naquadahBasedFuelMkIII);
            case 4 -> werkstoff(depleted ? GGMaterial.naquadahBasedFuelMkIVDepleted : GGMaterial.naquadahBasedFuelMkIV);
            case 5 -> werkstoff(depleted ? GGMaterial.naquadahBasedFuelMkVDepleted : GGMaterial.naquadahBasedFuelMkV);
            case 6 -> werkstoff(depleted ? GGMaterial.naquadahBasedFuelMkVIDepleted : GGMaterial.naquadahBasedFuelMkVI);
            default -> null;
        };
    }

    /**
     * The controller's own assembly.
     * <p>
     * 8 精金框架 + 16 防辐射板 + 2 力场发生器(Luv) + 8 电动泵(Luv) + 4 任意 ZPM 电路板 + 5760 mB 熔融三元金属, in
     * the assembler on circuit 15, 32,000 EU/t for 1200 seconds.
     */
    private static void registerController() {
        var controller = gregtech.api.GregTechAPI.METATILEENTITIES[com.qionsi.simplification.MetaTileIDs.NAQUADAH_FUEL_LAB_CONTROLLER];
        if (controller == null) {
            MyMod.LOG.error("Naquadah Fuel Research and Test Machine: controller is not registered, skipping recipe");
            return;
        }
        ItemStack frame = GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Adamantium, 8);
        ItemStack plates = new ItemStack(loadersPlate(), 16);
        ItemStack fieldGen = gregtech.api.enums.ItemList.Field_Generator_LuV.get(2);
        ItemStack pump = gregtech.api.enums.ItemList.Electric_Pump_LuV.get(8);
        ItemStack circuit = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.ZPM, 4);
        FluidStack trinium = Materials.Trinium.getMolten(5_760);
        if (frame == null || plates == null || fieldGen == null || pump == null || circuit == null || trinium == null) {
            MyMod.LOG.error(
                "Naquadah Fuel Research and Test Machine: an ingredient of the controller recipe is missing "
                    + "(frame {}, plates {}, field {}, pump {}, circuit {}, trinium {})",
                frame,
                plates,
                fieldGen,
                pump,
                circuit,
                trinium);
            return;
        }
        GTRecipeBuilder.builder()
            .circuit(15)
            .itemInputsUnsafe(frame, plates, fieldGen, pump, circuit)
            .fluidInputs(trinium)
            .itemOutputs(controller.getStackForm(1))
            .duration(1200 * 20)
            .eut(32_000)
            .addTo(gregtech.api.recipe.RecipeMaps.assemblerRecipes);
        MyMod.LOG
            .info("Registered the Naquadah Fuel Research and Test Machine controller recipe (32,000 EU/t, 1200 s)");
    }

    /** 防辐射板, from GoodGenerator. */
    private static net.minecraft.item.Item loadersPlate() {
        return goodgenerator.loader.Loaders.radiationProtectionPlate;
    }

    // ------------------------------------------------------------------ helpers

    private static FluidStack werkstoff(bartworks.system.material.Werkstoff w) {
        if (w == null) return null;
        try {
            return w.getFluidOrGas(1_250);
        } catch (Throwable t) {
            // getFluidOrGas throws while the Werkstoff's fluid has not been built yet.
            MyMod.LOG.warn("Naquadah fuel recipe: a Werkstoff has no fluid yet ({})", t);
            return null;
        }
    }

    private static ItemStack dust(Materials m, int amount) {
        if (m == null) return null;
        ItemStack base = GTOreDictUnificator.get(OrePrefixes.dust, m, 1);
        return base == null ? null : GTUtility.copyAmountUnsafe(amount, base);
    }

    private static ItemStack block(Materials m, int amount) {
        if (m == null) return null;
        ItemStack base = GTOreDictUnificator.get(OrePrefixes.block, m, 1);
        return base == null ? null : GTUtility.copyAmountUnsafe(amount, base);
    }

    private static ItemStack dust(String materialName, int amount) {
        return oreDict("dust" + materialName, amount);
    }

    /**
     * Looks an item up by ore dictionary name, falling back to 硅岩粉 when the entry is absent.
     * <p>
     * Used for 彩色玻璃粉 (GT++'s Chromatic Glass), whose dictionary entry may or may not be there depending on which
     * mods the pack carries. Dropping a whole recipe over one missing dust left the recipe map empty and therefore
     * invisible in NEI, so the ingredient degrades instead and the substitution is logged once.
     */
    private static ItemStack oreDict(String name, int amount) {
        ItemStack base = GTOreDictUnificator.get(name, amount);
        if (base != null) return base;
        if (MISSING_INGREDIENTS.add(name)) {
            MyMod.LOG.warn(
                "Naquadah fuel recipes: the ore dictionary has no '{}'; those recipes use 硅岩粉 in its place instead",
                name);
        }
        return dust(Materials.Naquadah, amount);
    }

    private static FluidStack gas(Materials m, int amount) {
        if (m == null) return null;
        FluidStack f = m.getGas(amount);
        return f != null ? f : m.getFluid(amount);
    }

    private static FluidStack fluid(Materials m, int amount) {
        if (m == null) return null;
        FluidStack f = m.getFluid(amount);
        return f != null ? f : m.getGas(amount);
    }
}
