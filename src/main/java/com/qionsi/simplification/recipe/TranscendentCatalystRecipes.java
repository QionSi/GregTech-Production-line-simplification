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
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;

/**
 * The five catalysts the 超维度催化剂制造机 / Transcendent Catalyst Maker makes, plus the coolants it burns while doing it.
 * <p>
 * The numbers come straight from the design document: each recipe takes a growing list of dusts and gases, a
 * programming circuit from 1 to 5, and gives back a thousand millibuckets of the catalyst. The voltage of the machine's
 * energy hatch never caps these; the machine is built to skip tiers without limit.
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

        // 1: the crude catalyst, circuit 1, 5s at 285,149,830 EU/t.
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(dust(Materials.Iron, 7), dust(Materials.Calcium, 7), dust(Materials.Niobium, 7))
            .fluidInputs(Materials.Helium.getGas(1000))
            .fluidOutputs(Materials.ExcitedDTCC.getFluid(1000))
            .duration(5 * SECONDS)
            .eut(285149830L)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 2: the mundane catalyst, circuit 2.
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
            .duration(5 * SECONDS)
            .eut(1327684600L)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 3: the radiant catalyst, circuit 3.
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
            .duration(5 * SECONDS)
            .eut(5293264510L)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 4: the alien catalyst, circuit 4.
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
            .duration(5 * SECONDS)
            .eut(20730073930L)
            .addTo(ModRecipeMaps.transcendentCatalystRecipes);

        // 5: the stellar catalyst, circuit 5, ten seconds. Its last ingredient is the concentrated primordial stellar
        // plasma mixture, which the player knows as a filled cell.
        FluidStack stellarPlasma = fluidByContainerName(STELLAR_PLASMA_NAMES, 25);
        if (stellarPlasma == null) {
            MyMod.LOG.error(
                "Could not find the concentrated primordial stellar plasma mixture; the stellar catalyst recipe is not "
                    + "registered.");
        } else {
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
                .duration(10 * SECONDS)
                .eut(21383837600L)
                .addTo(ModRecipeMaps.transcendentCatalystRecipes);
        }

        MyMod.LOG.info(
            "Registered {} Transcendent Catalyst Maker recipes and {} coolants",
            ModRecipeMaps.transcendentCatalystRecipes.getBackend()
                .getAllRecipes()
                .size(),
            COOLANTS.size());
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
