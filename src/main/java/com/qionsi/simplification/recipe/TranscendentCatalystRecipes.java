package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MyMod;

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;

/**
 * The five catalysts the 超维度催化剂制造机 / Transcendent Catalyst Maker makes, plus the coolants it burns while doing it.
 * <p>
 * The numbers come straight from the design document: each recipe takes a growing list of dusts and gases, a
 * programming circuit from 1 to 5, and gives back a thousand millibuckets of the catalyst. The voltage of the machine's
 * energy hatch never caps these; the machine is built to skip tiers without limit.
 * <p>
 * One number the document gives cannot be stored: GregTech keeps a recipe's voltage in an {@code int}, and the last
 * three catalysts are 5,293,264,510 / 20,730,073,930 / 21,383,837,600 EU/t. Those three are fitted in by
 * {@link #power},
 * which caps the voltage at {@link Integer#MAX_VALUE}. The radiant catalyst then keeps the document's total energy by
 * stretching its duration, while the alien and the stellar catalyst stay at that capped voltage and take the duration
 * the user asked for instead - 10 s for the alien and 5 s for the stellar, which is the opposite way round from the
 * document - so for those two the total energy deliberately no longer matches the document. The final numbers are
 * written into the log.
 */
public final class TranscendentCatalystRecipes {

    /** One of the four coolants, with the amount a single run of a recipe burns per thousand millibuckets produced. */
    public static final class Coolant {

        public final String name;
        public final FluidStack fluid;
        public final int amount;

        Coolant(String name, FluidStack fluid, int amount) {
            this.name = name;
            this.fluid = fluid;
            this.amount = amount;
        }
    }

    private static final Random RANDOM = new Random();

    /** Filled with {@link #init()}; empty when a coolant could not be resolved. */
    private static final List<Coolant> COOLANTS = new ArrayList<>();

    private static boolean initialised;
    private static boolean deferred;

    private TranscendentCatalystRecipes() {}

    /** The coolants that could be resolved, one of which is picked at random for every run. */
    public static List<Coolant> coolants() {
        return COOLANTS;
    }

    /** Picks one of the coolants at random, or {@code null} when none could be resolved. */
    public static Coolant pickCoolant() {
        return COOLANTS.isEmpty() ? null : COOLANTS.get(RANDOM.nextInt(COOLANTS.size()));
    }

    /**
     * Registers the recipes and resolves the coolants. Safe to call more than once.
     * <p>
     * The catalyst fluids are Bartworks werkstoffe, and Bartworks only turns those into fluids and cells during its own
     * init phase. Until that has happened there is nothing to put into a recipe, so the work is handed to
     * {@link GregTechAPI#sAfterGTPostload}, which GregTech runs at the very end of its own postload. A second call then
     * does nothing.
     */
    public static void init() {
        if (initialised) return;
        if (WerkstoffLoader.items.isEmpty()) {
            if (!deferred) {
                deferred = true;
                MyMod.LOG.info(
                    "Bartworks has not built its Werkstoff items yet; the Transcendent Catalyst Maker recipes will be "
                        + "registered after GregTech's postload.");
                GregTechAPI.sAfterGTPostload.add(TranscendentCatalystRecipes::init);
            }
            return;
        }
        initialised = true;

        resolveCoolants();

        // 1: the crude catalyst, circuit 1, 5s at 285,149,830 EU/t. Below the int ceiling, so the document's own
        // voltage and time are used unchanged.
        RecipePower crude = power("粗制超维度催化剂 / crude", 285_149_830L, 5 * SECONDS);
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(dust(Materials.Iron, 7), dust(Materials.Calcium, 7), dust(Materials.Niobium, 7))
            .fluidInputs(Materials.Helium.getGas(1000))
            .fluidOutputs(Materials.ExcitedDTCC.getFluid(1000))
            .duration(crude.duration)
            .eut(crude.eut)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 2: the mundane catalyst, circuit 2. Also below the ceiling.
        RecipePower mundane = power("平凡超维度催化剂 / mundane", 1_327_684_600L, 5 * SECONDS);
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(
                dust(Materials.Iron, 7),
                dust(Materials.Calcium, 7),
                dust(Materials.Niobium, 7),
                dust(Materials.Nickel, 7),
                dust(Materials.Boron, 7),
                dust(Materials.Sulfur, 7))
            .fluidInputs(Materials.Helium.getGas(1000), Materials.Radon.getGas(1000))
            .fluidOutputs(Materials.ExcitedDTPC.getFluid(1000))
            .duration(mundane.duration)
            .eut(mundane.eut)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 3: the radiant catalyst, circuit 3. From here on the document's voltage no longer fits in an int.
        RecipePower radiant = power("光辉超维度催化剂 / radiant", 5_293_264_510L, 5 * SECONDS);
        GTRecipeBuilder.builder()
            .circuit(3)
            .itemInputs(
                dust(Materials.Iron, 7),
                dust(Materials.Calcium, 7),
                dust(Materials.Niobium, 7),
                dust(Materials.Nickel, 7),
                dust(Materials.Boron, 7),
                dust(Materials.Sulfur, 7),
                dust(Materials.Zinc, 7),
                dust(Materials.Silver, 7),
                dust(Materials.Titanium, 7))
            .fluidInputs(Materials.Helium.getGas(1000), Materials.Radon.getGas(1000), Materials.Nitrogen.getGas(1000))
            .fluidOutputs(Materials.ExcitedDTRC.getFluid(1000))
            .duration(radiant.duration)
            .eut(radiant.eut)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 4: the alien catalyst, circuit 4. The document's voltage does not fit in an int, so it is capped; the user
        // asked for a fixed duration of ten seconds instead of the document's five, so the total energy is not kept.
        RecipePower alien = power("异星超维度催化剂 / alien", 20_730_073_930L, 5 * SECONDS, 10 * SECONDS);
        GTRecipeBuilder.builder()
            .circuit(4)
            .itemInputs(
                dust(Materials.Iron, 7),
                dust(Materials.Calcium, 7),
                dust(Materials.Niobium, 7),
                dust(Materials.Nickel, 7),
                dust(Materials.Boron, 7),
                dust(Materials.Sulfur, 7),
                dust(Materials.Zinc, 7),
                dust(Materials.Silver, 7),
                dust(Materials.Titanium, 7),
                dust(Materials.Americium, 7),
                dust(Materials.Bismuth, 7),
                dust(Materials.Tin, 7))
            .fluidInputs(
                Materials.Helium.getGas(1000),
                Materials.Radon.getGas(1000),
                Materials.Nitrogen.getGas(1000),
                Materials.Oxygen.getGas(1000))
            .fluidOutputs(Materials.ExcitedDTEC.getFluid(1000))
            .duration(alien.duration)
            .eut(alien.eut)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 5: the stellar catalyst, circuit 5. The document's voltage does not fit in an int either, so it is capped;
        // the
        // user asked for a fixed duration of five seconds instead of the document's ten. Its last ingredient is the
        // concentrated primordial stellar plasma mixture, which the player knows as a filled cell.
        FluidStack stellarPlasma = fluidByContainerName(STELLAR_PLASMA_NAMES, 25);
        if (stellarPlasma == null) {
            MyMod.LOG.error(
                "Could not find the concentrated primordial stellar plasma mixture; the stellar catalyst recipe is not "
                    + "registered.");
        } else {
            RecipePower stellar = power("恒星超维度催化剂 / stellar", 21_383_837_600L, 10 * SECONDS, 5 * SECONDS);
            GTRecipeBuilder.builder()
                .circuit(5)
                .itemInputs(
                    dust(Materials.Iron, 7),
                    dust(Materials.Calcium, 7),
                    dust(Materials.Niobium, 7),
                    dust(Materials.Nickel, 7),
                    dust(Materials.Boron, 7),
                    dust(Materials.Sulfur, 7),
                    dust(Materials.Zinc, 7),
                    dust(Materials.Silver, 7),
                    dust(Materials.Titanium, 7),
                    dust(Materials.Americium, 7),
                    dust(Materials.Bismuth, 7),
                    dust(Materials.Tin, 7),
                    dust(Materials.Lead, 7),
                    dust(Materials.Thorium, 7),
                    dust(Materials.Naquadria, 1))
                .fluidInputs(
                    Materials.Helium.getGas(1000),
                    Materials.Radon.getGas(1000),
                    Materials.Nitrogen.getGas(1000),
                    Materials.Oxygen.getGas(1000),
                    stellarPlasma)
                .fluidOutputs(Materials.ExcitedDTSC.getFluid(1000))
                .duration(stellar.duration)
                .eut(stellar.eut)
                .addTo(ModRecipeMaps.transcendentCatalystRecipes);
        }

        MyMod.LOG.info(
            "Registered {} Transcendent Catalyst Maker recipes and {} coolants",
            ModRecipeMaps.transcendentCatalystRecipes.getBackend()
                .getAllRecipes()
                .size(),
            COOLANTS.size());

        logPoolCapacity();
    }

    /**
     * The EU/t and the duration one recipe ends up with, after the document's voltage has been fitted into the
     * {@code int} GregTech stores.
     */
    private static final class RecipePower {

        final int eut;
        final int duration;

        RecipePower(int eut, int duration) {
            this.eut = eut;
            this.duration = duration;
        }
    }

    /**
     * Marks a recipe whose duration is not fixed by the user: it keeps the document's, stretched to keep the energy.
     */
    private static final int KEEP_DOCUMENTED_DURATION = -1;

    /**
     * Fits the document's voltage into the {@code int} GregTech stores and keeps the document's duration, stretched by
     * the same factor as the voltage was capped by.
     */
    private static RecipePower power(String name, long documentedEUt, int documentedDuration) {
        return power(name, documentedEUt, documentedDuration, KEEP_DOCUMENTED_DURATION);
    }

    /**
     * The largest EU/t a recipe can carry, which is what {@link GTRecipe#mEUt} can hold.
     * <p>
     * {@code GTRecipe.mEUt} is an {@code int}, and {@link GTRecipeBuilder#eut(long)} narrows its argument to an
     * {@code int} with a plain cast, so a recipe that asks for more than this silently wraps around to a meaningless -
     * often negative - voltage, and the machines then treat it as "no power and no time". The last three of the five
     * catalysts are above this ceiling in the design document - 5,293,264,510 / 20,730,073,930 / 21,383,837,600 EU/t -
     * which is exactly why those three used to show up without a voltage, a power draw or a duration at all.
     * <p>
     * The voltage is always capped at {@link Integer#MAX_VALUE} when the document asks for more. What happens to the
     * duration then depends on {@code userDuration}:
     * <ul>
     * <li>With {@link #KEEP_DOCUMENTED_DURATION} - which is what the radiant catalyst uses - the total energy the
     * document asks for is kept: the duration is stretched by the same factor the voltage was cut by, so {@code eut *
     * duration} is what the document specifies.</li>
     * <li>With a duration the user named - which is what the alien and the stellar catalyst use, 10 s and 5 s - that
     * duration is used as it is and the document's total energy is deliberately abandoned, because the user asked for
     * those two durations by name.</li>
     * </ul>
     * A recipe at or below the ceiling is left completely alone. Every decision is written into the log with both the
     * document's number and the one that was used.
     *
     * @param userDuration the duration to use, in ticks, or {@link #KEEP_DOCUMENTED_DURATION} to stretch the
     *                     document's duration instead so that its total energy stays the same.
     */
    private static RecipePower power(String name, long documentedEUt, int documentedDuration, int userDuration) {
        long totalEU = documentedEUt * documentedDuration;
        int eut = (int) Math.min(documentedEUt, (long) Integer.MAX_VALUE);
        if (eut < documentedEUt && userDuration != KEEP_DOCUMENTED_DURATION) {
            MyMod.LOG.warn(
                "Recipe '{}': the document asks for {} EU/t for {} ticks ({} EU in total), but GTRecipe.mEUt is an int "
                    + "and {} exceeds Integer.MAX_VALUE = {}. The voltage is capped at {} EU/t and stays there, because "
                    + "the user specified the duration of this recipe as {} ticks ({} s) - the document's duration of "
                    + "{} ticks is therefore not used, and the document's total of {} EU is deliberately not preserved. "
                    + "The recipe is registered at {} EU/t for {} ticks ({} EU in total).",
                name,
                documentedEUt,
                documentedDuration,
                totalEU,
                documentedEUt,
                Integer.MAX_VALUE,
                eut,
                userDuration,
                userDuration / SECONDS,
                documentedDuration,
                totalEU,
                eut,
                userDuration,
                (long) eut * userDuration);
            return new RecipePower(eut, userDuration);
        }
        if (userDuration != KEEP_DOCUMENTED_DURATION) {
            // Below the ceiling: the document's own voltage is used, only the user's duration replaces the document's.
            MyMod.LOG.info(
                "Recipe '{}': {} EU/t; below Integer.MAX_VALUE, so the document's own voltage is used unchanged. The "
                    + "user specified the duration of this recipe as {} ticks ({} s).",
                name,
                documentedEUt,
                userDuration,
                userDuration / SECONDS);
            return new RecipePower(eut, userDuration);
        }
        int duration = (int) Math.min(Integer.MAX_VALUE, (totalEU + eut - 1) / eut);
        if (eut < documentedEUt) {
            MyMod.LOG.warn(
                "Recipe '{}': the document asks for {} EU/t for {} ticks ({} EU in total), but GTRecipe.mEUt is an int "
                    + "and {} exceeds Integer.MAX_VALUE = {}. The voltage is capped at {} EU/t and the duration is "
                    + "stretched to {} ticks so that the document's total of {} EU is preserved (deviation: {} -> {} "
                    + "EU/t, {} -> {} ticks).",
                name,
                documentedEUt,
                documentedDuration,
                totalEU,
                documentedEUt,
                Integer.MAX_VALUE,
                eut,
                duration,
                totalEU,
                documentedEUt,
                eut,
                documentedDuration,
                duration);
        } else {
            MyMod.LOG.info(
                "Recipe '{}': {} EU/t for {} ticks ({} EU in total); below Integer.MAX_VALUE, so the document's own "
                    + "numbers are used unchanged.",
                name,
                documentedEUt,
                documentedDuration,
                totalEU);
        }
        return new RecipePower(eut, duration);
    }

    /**
     * Writes the recipe pool's layout capacity and the widest recipe that actually went into it into the log, so that a
     * page that is too small for its own recipes - which is what
     * {@code maxIO(18, 6, 2, 2)} used to cause, by leaving room for two fluid inputs while the stellar catalyst takes
     * five - is visible in the startup log instead of only in the game.
     */
    private static void logPoolCapacity() {
        try {
            BasicUIProperties ui = ModRecipeMaps.transcendentCatalystRecipes.getFrontend()
                .getUIProperties();
            int widestItemInputs = 0;
            int widestItemOutputs = 0;
            int widestFluidInputs = 0;
            int widestFluidOutputs = 0;
            for (GTRecipe recipe : ModRecipeMaps.transcendentCatalystRecipes.getBackend()
                .getAllRecipes()) {
                widestItemInputs = Math.max(widestItemInputs, length(recipe.mInputs));
                widestItemOutputs = Math.max(widestItemOutputs, length(recipe.mOutputs));
                widestFluidInputs = Math.max(widestFluidInputs, length(recipe.mFluidInputs));
                widestFluidOutputs = Math.max(widestFluidOutputs, length(recipe.mFluidOutputs));
            }
            MyMod.LOG.info(
                "Transcendent Catalyst Maker recipe pool layout: maxIO is {} item inputs / {} item outputs / {} fluid "
                    + "inputs / {} fluid outputs (argument order: item in, item out, fluid in, fluid out); the widest "
                    + "registered recipe uses {} item inputs / {} item outputs / {} fluid inputs / {} fluid outputs.",
                ui.maxItemInputs,
                ui.maxItemOutputs,
                ui.maxFluidInputs,
                ui.maxFluidOutputs,
                widestItemInputs,
                widestItemOutputs,
                widestFluidInputs,
                widestFluidOutputs);
            if (widestFluidInputs > ui.maxFluidInputs) {
                MyMod.LOG.error(
                    "The Transcendent Catalyst Maker page is too small: a recipe needs {} fluid inputs but maxIO only "
                        + "lays out {}.",
                    widestFluidInputs,
                    ui.maxFluidInputs);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("Could not read back the Transcendent Catalyst Maker recipe pool layout", t);
        }
    }

    /** The length of a stack array, or zero when there is none. */
    private static int length(Object[] array) {
        return array == null ? 0 : array.length;
    }

    /** The item id and metadata of the concentrated primordial stellar plasma mixture cell. */
    private static final int STELLAR_PLASMA_ITEM_ID = 5653;
    private static final int STELLAR_PLASMA_META = 144;

    /** The item id and metadata of the liquid helium cell. */
    private static final int LIQUID_HELIUM_ITEM_ID = 5653;
    private static final int LIQUID_HELIUM_META = 1249;

    /** The item id and metadata of the IC2 coolant cell. */
    private static final int COOLANT_ITEM_ID = 5653;
    private static final int COOLANT_META = 5;

    /**
     * Resolves the four coolants by their cell ids. Three of them are cells the design document names by item id; the
     * fourth, liquid air, is a plain GregTech fluid.
     */
    private static void resolveCoolants() {
        addCoolant("liquid air", Materials.LiquidAir.getFluid(1), 100_000);
        // GregTech knows the IC2 coolant by name, which is steadier than going through its cell.
        addCoolant("IC2 coolant", GTModHandler.getIC2Coolant(1), 10_000);
        addCoolant("super coolant", Materials.SuperCoolant.getFluid(1), 1_000);
        // The two coolants that are not plain GregTech fluids are looked up by the name of the cell that holds them,
        // because a numerical item id is handed out at runtime and would point at something else in another pack.
        addCoolant("liquid helium", fluidByContainerName(LIQUID_HELIUM_NAMES, 1), 100);
    }

    /** Display names the liquid helium cell goes by; the first is the one the design document shows. */
    private static final String[] LIQUID_HELIUM_NAMES = { "液氦cell", "液氦 Cell", "液氦单元", "Liquid Helium Cell" };

    /** Display names the concentrated primordial stellar plasma mixture cell goes by. */
    private static final String[] STELLAR_PLASMA_NAMES = { "浓缩原始恒星等离子体混合物cell", "浓缩原始恒星等离子体混合物 Cell", "浓缩原始恒星等离子体混合物单元",
        "Concentrated Primordial Stellar Plasma Mixture Cell" };

    /**
     * Finds the fluid of a filled container by the name that container is shown under.
     * <p>
     * The design document names these fluids by cell, and the cells are not always in Forge's container registry under
     * a lookup that takes an item id, so the whole registry is walked instead and the display names are compared. That
     * also means the lookup survives a pack that hands out different ids. Colour codes are stripped from the names
     * before comparing, since the cells are coloured.
     */
    private static FluidStack fluidByContainerName(String[] candidates, int amount) {
        for (String candidate : candidates) {
            for (FluidContainerRegistry.FluidContainerData data : FluidContainerRegistry
                .getRegisteredFluidContainerData()) {
                if (data == null || data.filledContainer == null || data.fluid == null) continue;
                String display = withoutColours(data.filledContainer.getDisplayName());
                if (display.equalsIgnoreCase(candidate)) {
                    FluidStack found = data.fluid.copy();
                    found.amount = amount;
                    MyMod.LOG.info("Resolved '{}' to {} through its container", display, found.getUnlocalizedName());
                    return found;
                }
            }
        }
        MyMod.LOG.error(
            "None of the containers {} is registered; that fluid cannot be used",
            String.join(" / ", candidates));
        return null;
    }

    private static String withoutColours(String text) {
        return text == null ? null
            : text.replaceAll("\u00a7.", "")
                .trim();
    }

    private static void addCoolant(String name, FluidStack fluid, int amount) {
        if (fluid == null) {
            MyMod.LOG.error("Could not resolve the coolant '{}'; it is left out of the pool", name);
            return;
        }
        FluidStack unit = fluid.copy();
        unit.amount = 1;
        COOLANTS.add(new Coolant(name, unit, amount));
        MyMod.LOG.info(
            "Coolant '{}' resolved to {} and burns {} mB per 1000 mB of catalyst",
            name,
            unit.getUnlocalizedName(),
            amount);
    }

    /** The fluid of a GregTech catalyst, in the given amount, or {@code null} when the material is missing. */
    private static FluidStack fluid(Werkstoff werkstoff, int amount) {
        if (werkstoff == null) return null;
        FluidStack fluid = werkstoff.getFluidOrGas(amount);
        if (fluid == null) {
            MyMod.LOG.error("The catalyst '{}' has no fluid; its recipe will not work", werkstoff.getInternalName());
        }
        return fluid;
    }

    private static ItemStack dust(Materials material, int amount) {
        return GTUtility.copyAmountUnsafe(amount, GTOreDictUnificator.get(OrePrefixes.dust, material, 1));
    }

    /**
     * Reads the fluid out of a filled container, which is how the design document names the coolants and the stellar
     * plasma mixture: the id of the item and its metadata.
     * <p>
     * GregTech's own cells and fluid display items are not always in Forge's container registry, so the lookups are
     * tried in turn and the one that worked is written into the log.
     */
    private static FluidStack fluidFromCell(int itemId, int meta, int amount) {
        Item item = Item.getItemById(itemId);
        if (item == null) {
            MyMod.LOG.error("There is no item with the id {}", itemId);
            return null;
        }
        ItemStack stack = new ItemStack(item, 1, meta);
        FluidStack fluid = GTUtility.getFluidFromContainerOrFluidDisplay(stack);
        if (fluid == null) fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fluid == null) fluid = GTUtility.getFluidForFilledItem(stack, true);
        if (fluid == null) {
            MyMod.LOG.error("The item {}:{} does not hold a fluid", itemId, meta);
            return null;
        }
        FluidStack wanted = fluid.copy();
        wanted.amount = amount;
        MyMod.LOG.info("The item {}:{} holds {}", itemId, meta, wanted.getUnlocalizedName());
        return wanted;
    }
}
