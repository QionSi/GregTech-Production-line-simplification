package com.qionsi.simplification.recipe;

import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_STATION_DATA;
import static gregtech.api.util.GTRecipeConstants.SCANNING;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.qionsi.simplification.MyMod;
import com.qionsi.simplification.item.ModItems;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/**
 * The assembly line recipe of the 初步研究的超维度催化剂制造机 / Preliminary Study: Transcendent Catalyst Maker.
 * <p>
 * The recipe takes, in the order the design document lists them, 64 of each of the eight controllers the catalyst
 * production line is built out of, plus the four 激发的…超维度催化剂 fluids at 1024,000 / 512,000 / 256,000 / 128,000 mB.
 * It runs for 3600 seconds at 33,554,432 EU/t, and it carries the Research Station data: the research item is the
 * preliminary study item itself, so the station scans that item and writes the recipe onto a 闪存 / Data Stick.
 *
 * <h2>Why this is registered so late, and why it is a separate file</h2>
 *
 * The four catalyst fluids are Bartworks werkstoffe, and Bartworks only turns those into fluids during its own init
 * phase - see {@link TranscendentCatalystRecipes}. Until that has happened there is nothing to put into a recipe, so
 * the registration is handed to {@link GregTechAPI#sAfterGTPostload}, exactly like the catalyst maker's own recipes.
 * That also means every machine below is long registered by the time this runs.
 *
 * <h2>Where each ingredient comes from</h2>
 *
 * Every stack below was read out of the GT5U 5.09.54.20 sources rather than guessed, and the source is named next to
 * it. The names are written into the log at registration time, together with the display name each stack actually
 * resolves to, so a pack author can compare them against the design document in a single startup log.
 */
public final class TranscendentCatalystAssemblyLine {

    /** 33,554,432 EU/t, the voltage the design document puts on the recipe. */
    private static final long RECIPE_EUT = 33_554_432L;

    /** 3600 seconds, the duration the design document puts on the recipe. */
    private static final int RECIPE_DURATION = 3600 * 20;

    /** The four catalyst fluids, in the order the design document lists them. */
    private static final int CRUDE_AMOUNT = 1_024_000;
    private static final int MUNDANE_AMOUNT = 512_000;
    private static final int RADIANT_AMOUNT = 256_000;
    private static final int ALIEN_AMOUNT = 128_000;

    /**
     * The Research Station data. 1200 ticks is 924,249,600 / 770,208, the scan time the design document gives, and the
     * voltage is the one the document pairs with it.
     */
    private static final int SCAN_TIME = 1200;
    private static final long SCAN_VOLTAGE = 1_327_684_600L;

    /** 770,208, the research station data value the design document specifies. */
    private static final int RESEARCH_STATION_DATA_VALUE = 770_208;

    private static boolean registered;
    private static boolean deferred;

    /** Filled by {@link #nameLookup()}: every named machine in {@code GregTechAPI.METATILEENTITIES}. */
    private static List<IMetaTileEntity> NAME_LOOKUP;

    private TranscendentCatalystAssemblyLine() {}

    /**
     * Registers the recipe. Safe to call more than once.
     * <p>
     * The Bartworks werkstoffe this needs do not exist until Bartworks has run, so the first call - which happens
     * during
     * this mod's pre-init - only hands the work to {@link GregTechAPI#sAfterGTPostload}. The second call, at the end of
     * GregTech's postload, does the real work.
     */
    public static void init() {
        if (registered) return;
        if (WerkstoffLoader.items.isEmpty()) {
            if (!deferred) {
                deferred = true;
                MyMod.LOG.info(
                    "Bartworks has not built its Werkstoff items yet; the Preliminary Study assembly line recipe will "
                        + "be registered after GregTech's postload.");
                GregTechAPI.sAfterGTPostload.add(TranscendentCatalystAssemblyLine::init);
            }
            return;
        }
        registered = true;

        try {
            register();
        } catch (Throwable t) {
            MyMod.LOG.error(
                "Could not register the assembly line recipe of the Preliminary Study: Transcendent Catalyst Maker; "
                    + "the recipe will be missing.",
                t);
        }
    }

    private static void register() {
        if (ModItems.preliminaryCatalystMaker == null) {
            MyMod.LOG.error(
                "The Preliminary Study: Transcendent Catalyst Maker item is not registered, so its assembly line "
                    + "recipe cannot be built; nothing was registered.");
            return;
        }

        // The research item, which the Research Station scans and which the recipe then produces.
        ItemStack researchItem = new ItemStack(ModItems.preliminaryCatalystMaker, 1);

        List<String> missing = new ArrayList<>();
        ItemStack megaBlastFurnace = megaMachine(
            MetaTileEntityIDs.LegacyMegaBlastFurnace.ID,
            new String[] { "Mega Electric Blast Furnace", "巨型工业高炉", "tile.bw.mbf" },
            "巨型工业高炉 (Bartworks, localisation key tile.bw.mbf, MetaTileEntity id "
                + MetaTileEntityIDs.LegacyMegaBlastFurnace.ID
                + ")",
            missing);
        ItemStack megaVacuumFreezer = megaMachine(
            MetaTileEntityIDs.LegacyMegaVacuumFreezer.ID,
            new String[] { "Mega Vacuum Freezer", "巨型真空冷冻机", "tile.bw.mvf" },
            "巨型真空冷冻机 (Bartworks, localisation key tile.bw.mvf, MetaTileEntity id "
                + MetaTileEntityIDs.LegacyMegaVacuumFreezer.ID
                + ")",
            missing);
        ItemStack megaAlloyBlastSmelter = gtpp(
            GregtechItemList.Mega_AlloyBlastSmelter,
            new String[] { "Mega Alloy Blast Smelter", "巨型合金冶炼炉" },
            "巨型合金冶炼炉 (GT++, item id " + MetaTileEntityIDs.Mega_AlloyBlastSmelter.ID
                + "; GT5U has no 'Mega Alloy Smelter' - the Mega Alloy Blast Smelter is the mega tier of the alloy "
                + "smelting line)",
            missing);
        ItemStack industrialMixer = item(
            ItemList.IndustrialMixer,
            new String[] { "Industrial Mixing Machine", "工业搅拌机" },
            "工业搅拌机 (ItemList.IndustrialMixer)",
            missing);
        ItemStack largeFluidExtractor = item(
            ItemList.LargeFluidExtractor,
            new String[] { "Large Fluid Extractor", "大型流体提取机" },
            "大型流体提取机 (ItemList.LargeFluidExtractor)",
            missing);
        // The achievement key of this one is fusioncomputer.tier.09; GT++ registers it as
        // GregtechItemList.FusionComputer_UV2.
        ItemStack fusionComputerMk4 = gtpp(
            GregtechItemList.FusionComputer_UV2,
            new String[] { "FusionTech MK IV", "Fusion Computer Mark IV", "聚变计算机MK IV", "核聚变反应堆控制电脑Mk IV" },
            "核聚变反应堆控制电脑 Mk-IV (GT++, achievement fusioncomputer.tier.09, item id "
                + MetaTileEntityIDs.FusionComputer_UV2.ID
                + "; GT5U's own ItemList.FusionComputer_UV is Mark III, achievement fusioncomputer.tier.08)",
            missing);
        ItemStack uevCircuit = circuit(missing);
        ItemStack fieldGeneratorUev = item(
            ItemList.Field_Generator_UEV,
            new String[] { "Field Generator (UEV)", "力场发生器(UEV)" },
            "力场发生器(UEV) (ItemList.Field_Generator_UEV)",
            missing);

        if (!missing.isEmpty()) {
            MyMod.LOG.error(
                "The assembly line recipe of the Preliminary Study: Transcendent Catalyst Maker is NOT registered; "
                    + "{} of its ingredients could not be resolved: {}",
                missing.size(),
                String.join(" | ", missing));
            return;
        }

        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .metadata(RESEARCH_ITEM, researchItem)
            .metadata(RESEARCH_STATION_DATA, RESEARCH_STATION_DATA_VALUE)
            .metadata(SCANNING, new Scanning(SCAN_TIME, SCAN_VOLTAGE))
            .itemInputs(
                megaBlastFurnace,
                megaVacuumFreezer,
                megaAlloyBlastSmelter,
                industrialMixer,
                largeFluidExtractor,
                fusionComputerMk4,
                uevCircuit,
                fieldGeneratorUev);

        // The four catalysts, each in the amount the design document gives. A missing fluid is fatal to the recipe, so
        // it is reported and the recipe is dropped rather than registered incomplete. They are collected first because
        // GTRecipeBuilder.fluidInputs replaces whatever was there before rather than appending to it.
        List<FluidStack> catalysts = new ArrayList<>();
        boolean fluidsComplete = true;
        fluidsComplete &= addFluid(catalysts, Materials.ExcitedDTCC, CRUDE_AMOUNT, "激发的粗制超维度催化剂");
        fluidsComplete &= addFluid(catalysts, Materials.ExcitedDTPC, MUNDANE_AMOUNT, "激发的平凡超维度催化剂");
        fluidsComplete &= addFluid(catalysts, Materials.ExcitedDTRC, RADIANT_AMOUNT, "激发的光辉超维度催化剂");
        fluidsComplete &= addFluid(catalysts, Materials.ExcitedDTEC, ALIEN_AMOUNT, "激发的异星超维度催化剂");
        if (!fluidsComplete) {
            MyMod.LOG.error(
                "The assembly line recipe of the Preliminary Study: Transcendent Catalyst Maker is NOT registered; "
                    + "at least one of the four catalyst fluids is missing.");
            return;
        }
        builder.fluidInputs(catalysts.toArray(new FluidStack[0]));

        // The recipe builds the controller of the machine itself. The scanned item is only what the assembly line reads
        // the recipe out of, which is what RESEARCH_ITEM above tells GregTech: the player scans the Preliminary Study
        // item at the Research Station and puts the resulting flash memory into the assembly line's data bank.
        ItemStack controller = controllerStack();
        if (controller == null) {
            MyMod.LOG.error(
                "The controller of the Transcendent Catalyst Maker is not registered, so its assembly line recipe is "
                    + "not registered either.");
            return;
        }
        builder.itemOutputs(controller)
            .duration(RECIPE_DURATION)
            .eut(RECIPE_EUT)
            .addTo(AssemblyLine);

        MyMod.LOG.info(
            "Registered the assembly line recipe of the Transcendent Catalyst Maker controller (output: {}; order: "
                + "64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {}, with {} / {} / {} / {} "
                + "mB of the four catalysts, {} EU/t for {} ticks, research item '{}', scanning {} ticks at {} EU/t)",
            describe(controller),
            describe(megaBlastFurnace),
            describe(megaVacuumFreezer),
            describe(megaAlloyBlastSmelter),
            describe(industrialMixer),
            describe(largeFluidExtractor),
            describe(fusionComputerMk4),
            describe(uevCircuit),
            describe(fieldGeneratorUev),
            CRUDE_AMOUNT,
            MUNDANE_AMOUNT,
            RADIANT_AMOUNT,
            ALIEN_AMOUNT,
            RECIPE_EUT,
            RECIPE_DURATION,
            describe(researchItem),
            SCAN_TIME,
            SCAN_VOLTAGE);
    }

    /**
     * The controller this recipe builds.
     * <p>
     * This mod's own machine is registered as a MetaTileEntity, so its item is whatever that registration hands out and
     * is read from the registry rather than built here.
     */
    private static ItemStack controllerStack() {
        gregtech.api.interfaces.metatileentity.IMetaTileEntity controller = GregTechAPI.METATILEENTITIES[com.qionsi.simplification.MetaTileIDs.TRANSCENDENT_CATALYST_MAKER_CONTROLLER];
        return controller == null ? null : controller.getStackForm(1);
    }

    /**
     * Adds one catalyst fluid to the list. These are GregTech's own dimensionally transcendent catalysts, the same five
     * the Dimensionally Transcendent Plasma Forge runs on, in their excited form.
     */
    private static boolean addFluid(List<FluidStack> catalysts, Materials material, int amount, String chineseName) {
        if (material == null) {
            MyMod.LOG.error("The catalyst material {} does not exist", chineseName);
            return false;
        }
        FluidStack fluid = material.getFluid(amount);
        if (fluid == null) {
            MyMod.LOG.error(
                "The catalyst {} ({}) has no fluid, so the assembly line recipe cannot take it",
                chineseName,
                material.mName);
            return false;
        }
        catalysts.add(fluid);
        MyMod.LOG
            .info("The assembly line recipe takes {} mB of {} ({})", amount, chineseName, fluid.getUnlocalizedName());
        return true;
    }

    /**
     * 64 of a GregTech machine, or {@code null}. The machine is read straight out of
     * {@code GregTechAPI.METATILEENTITIES} by id, rather than out of an {@code ItemList} entry, because the Bartworks
     * mega machines - whose localisation keys are {@code tile.bw.mbf} and {@code tile.bw.mvf} - never got an ItemList
     * enum constant of their own. The resolved machine is then cross-checked against the registry by name.
     */
    private static ItemStack megaMachine(int id, String[] names, String what, List<String> missing) {
        IMetaTileEntity machine = GregTechAPI.METATILEENTITIES[id];
        if (machine == null) {
            missing.add(what + " -> no MetaTileEntity at id " + id);
            MyMod.LOG.error("{} is not registered: there is no MetaTileEntity at id {}", what, id);
            return null;
        }
        ItemStack stack = machine.getStackForm(1);
        if (stack == null || stack.getItem() == null) {
            missing.add(what + " -> the MetaTileEntity at id " + id + " has no item form");
            MyMod.LOG.error("{} has no item form (MetaTileEntity id {})", what, id);
            return null;
        }
        ItemStack sixtyFour = GTUtility.copyAmountUnsafe(64, stack);
        MyMod.LOG.info("Resolved {} to {}", what, describe(sixtyFour));
        verifyByLocalName(sixtyFour, names, what, "its MetaTileEntity id " + id);
        return sixtyFour;
    }

    /** 64 of a GT++ machine, or {@code null}; the caller names where the enum constant comes from. */
    private static ItemStack gtpp(GregtechItemList entry, String[] names, String what, List<String> missing) {
        ItemStack stack = entry.get(64);
        if (stack == null || stack.getItem() == null) {
            missing.add(what + " -> the ItemList entry resolves to nothing");
            MyMod.LOG.error("{} could not be resolved: the item is not registered", what);
            return null;
        }
        MyMod.LOG.info("Resolved {} to {}", what, describe(stack));
        verifyByLocalName(stack, names, what, "the GT++ ItemList entry " + entry.name());
        return stack;
    }

    /** 64 of a GregTech item, or {@code null}; the caller names the enum constant. */
    private static ItemStack item(ItemList entry, String[] names, String what, List<String> missing) {
        ItemStack stack = entry.get(64);
        if (stack == null || stack.getItem() == null) {
            missing.add(what + " -> the ItemList entry resolves to nothing");
            MyMod.LOG.error("{} could not be resolved: the item is not registered", what);
            return null;
        }
        MyMod.LOG.info("Resolved {} to {}", what, describe(stack));
        verifyByLocalName(stack, names, what, "the ItemList entry " + entry.name());
        return stack;
    }

    /**
     * Cross-checks one resolved machine against {@code GregTechAPI.METATILEENTITIES} by name.
     * <p>
     * Every machine GregTech or one of its addons registers is in that array, and each of them can say both the name it
     * was registered under ({@link IMetaTileEntity#getMetaName()}, which does not depend on the client language) and
     * its localised name. Walking the array once and comparing those two strings - plus the display name of the stack
     * that was picked - against the candidate names is therefore an independent confirmation that the id-based lookup
     * above picked the machine the design document means. Every hit is written into the log together with whether it is
     * the very same item that went into the recipe, and a warning is written when nothing matches. A hit that is not
     * the same item is not an error: GregTech keeps more than one MetaTileEntity per machine name for the machines that
     * have a legacy and a current controller, and the check is a name check, not an identity check.
     */
    private static void verifyByLocalName(ItemStack resolved, String[] names, String what, String pickedBy) {
        try {
            List<IMetaTileEntity> machines = nameLookup();
            String resolvedName = strip(resolved.getDisplayName());
            int hits = 0;
            for (IMetaTileEntity machine : machines) {
                String metaName = strip(machine.getMetaName());
                String localName = strip(machine.getLocalName());
                for (String name : names) {
                    if (name.equalsIgnoreCase(metaName) || name.equalsIgnoreCase(localName)) {
                        hits++;
                        ItemStack one = machine.getStackForm(1);
                        boolean same = GTUtility.areStacksEqual(resolved, one, true)
                            || (resolvedName != null && resolvedName.equalsIgnoreCase(strip(one.getDisplayName()))
                                && resolved.getItem() == one.getItem());
                        MyMod.LOG.info(
                            "Name lookup for {}: candidate '{}' matches MetaTileEntity '{}' / '{}', which is {} the "
                                + "same item as the one that was used ({} vs {})",
                            what,
                            name,
                            metaName,
                            localName,
                            same ? "" : "not ",
                            describe(one),
                            describe(resolved));
                        break;
                    }
                }
            }
            if (hits == 0) {
                // Not fatal, and not always a mistake: an item that is not a machine at all - a field generator, a
                // circuit - can never be found in this registry. The stack itself is still the one that went in.
                MyMod.LOG.warn(
                    "None of the candidate names {} is registered in GregTechAPI.METATILEENTITIES; the stack '{}' was "
                        + "picked by {} instead, so the name could not be confirmed against the registry.",
                    String.join(" / ", names),
                    describe(resolved),
                    pickedBy);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("Could not cross-check {} against the machine registry by name", what, t);
        }
    }

    /**
     * Every registered GregTech machine that has a name, in one list. Built once: {@code GregTechAPI.METATILEENTITIES}
     * is a sparse array of more than thirty thousand entries, so walking it for every ingredient would be wasteful.
     */
    private static List<IMetaTileEntity> nameLookup() {
        if (NAME_LOOKUP == null) {
            List<IMetaTileEntity> machines = new ArrayList<>();
            for (IMetaTileEntity machine : GregTechAPI.METATILEENTITIES) {
                if (machine == null) continue;
                if (strip(machine.getMetaName()) == null && strip(machine.getLocalName()) == null) continue;
                machines.add(machine);
            }
            NAME_LOOKUP = machines;
            MyMod.LOG.info(
                "{} machines are registered in GregTechAPI.METATILEENTITIES and can be looked up by name",
                machines.size());
        }
        return NAME_LOOKUP;
    }

    /** A display name without GregTech's colour codes, or {@code null} when there is no name at all. */
    private static String strip(String text) {
        return text == null ? null
            : text.replaceAll("\u00a7.", "")
                .trim();
    }

    /** 64 of any UEV circuit, out of the ore dictionary; {@code null} when no UEV circuit exists. */
    private static ItemStack circuit(List<String> missing) {
        ItemStack stack = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UEV, 64);
        if (stack == null || stack.getItem() == null) {
            missing.add("UEV 电路板 / any UEV circuit -> the ore dictionary has no UEV circuit");
            MyMod.LOG.error("No UEV circuit could be found in the ore dictionary");
            return null;
        }
        MyMod.LOG.info("Resolved any UEV circuit to {}", describe(stack));
        return stack;
    }

    /** How a stack is shown, for the log: its display name, its stack size and its unlocalised name. */
    private static String describe(ItemStack stack) {
        if (stack == null) return "<null>";
        if (stack.getItem() == null) return "<empty stack>";
        String name = stack.getDisplayName();
        return (name == null ? stack.getUnlocalizedName() : name.replaceAll("\u00a7.", "")) + " x"
            + stack.stackSize
            + " ["
            + stack.getUnlocalizedName()
            + "]";
    }
}
