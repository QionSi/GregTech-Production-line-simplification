package com.qionsi.simplification.recipe;

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
import gregtech.api.util.GTUtility;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;
import tectech.recipe.TTRecipeAdder;
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.metaTileEntity.multi.MTEResearchStation;

/**
 * The assembly line recipe of the 初步研究的超维度催化剂制造机 / Preliminary Study: Transcendent Catalyst Maker.
 * <p>
 * The recipe takes, in the order the design document lists them, 64 of each of the eight controllers the catalyst
 * production line is built out of, plus the four 激发的…超维度催化剂 fluids at 1024,000 / 512,000 / 256,000 / 128,000 mB.
 * It runs for 3600 seconds at 33,554,432 EU/t.
 *
 * <h2>Which machine writes the 闪存 / data stick, and why that decides how this is registered</h2>
 *
 * The assembly line will not run from a plain recipe: it reads the recipe out of a data stick, and that stick has to be
 * written by a Research Station. The Research Station has two machine modes, and they are not interchangeable - see
 * {@code tectech.thing.metaTileEntity.multi.MTEResearchStation}:
 *
 * <ul>
 * <li>{@code MODE_SCANNER} goes through {@code findSBScannerRecipe} and
 * {@code ScannerHandlerLoader.doAssemblyLineResearch},
 * which look a recipe up in {@code GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes} by research item and take the
 * scanning time and voltage from the {@link gregtech.api.util.GTRecipeConstants#SCANNING} metadata. That is the mode
 * the old "scan the item, then scan it again at the scanner" flow uses, and the SCANNING metadata is what feeds
 * it.</li>
 * <li>{@code MODE_RESEARCH_STATION} goes through {@code findResearchStationRecipe}, which ignores SCANNING completely
 * and instead walks {@link TecTechRecipeMaps#researchableALRecipeList} looking for a recipe whose research item is the
 * stack in the station's object holder. It then writes the data stick itself.</li>
 * </ul>
 *
 * This recipe therefore has to be in {@code researchableALRecipeList}, and the only supported way to get there is
 * {@link TTRecipeAdder#addResearchableAssemblylineRecipe}. That single call also puts the recipe into
 * {@code RecipeAssemblyLine.sAssemblylineRecipes} - which is what the assembly line itself reads back out of the data
 * stick - and into the NEI pages of both the Research Station and the assembly line. Registering it through
 * {@code GTRecipeConstants.AssemblyLine} instead, as this class used to, only ever fed the scanner mode (and put a
 * second entry with the same output into the assembly line list, which
 * {@code AssemblyLineUtils.assertSingleRecipe} refuses).
 *
 * <h2>The research requirement</h2>
 *
 * The research is 32,767 computation per second for 1,200 seconds. {@code TTRecipeAdder} clamps the per-second figure
 * to
 * {@code Short.MAX_VALUE} = 32,767 - the value was chosen as that ceiling from the start, so both the calculation the
 * Research Station runs and the RESEARCH_STATION_DATA the station's NEI page displays use 32,767 and the clamp never
 * changes anything.
 * <p>
 * TecTech takes no research time at all: {@code addResearchableAssemblylineRecipe} takes the computation for the whole
 * research ({@code totalComputationRequired}) and the per-second figure, and stores
 * {@code totalComputationRequired / computationRequiredPerSec} as the research time in seconds. The total passed in is
 * therefore 32,767 x 1,200 = 39,320,400, which is what the Research Station's NEI page prints as "Total computation".
 * The station charges 39,320,400 x 20 computation over the research at 32,767 per second, which is exactly the 1,200
 * seconds asked for. (TecTech's own recipes read the same way: {@code total_computation / comp_per_second} is their
 * research time in seconds.)
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
    private static final int RECIPE_EUT = 33_554_432;

    /** 3600 seconds, the duration the design document puts on the recipe. */
    private static final int RECIPE_DURATION_SECONDS = 3600;
    private static final int RECIPE_DURATION = RECIPE_DURATION_SECONDS * 20;

    /** The four catalyst fluids, in the order the design document lists them. */
    private static final int CRUDE_AMOUNT = 1_024_000;
    private static final int MUNDANE_AMOUNT = 512_000;
    private static final int RADIANT_AMOUNT = 256_000;
    private static final int ALIEN_AMOUNT = 128_000;

    /**
     * The research requirement: 32,767 computation per second - {@code Short.MAX_VALUE}, which is also the largest
     * figure TecTech's clamp and its 16 bit RESEARCH_STATION_DATA field can carry - for {@value #RESEARCH_SECONDS}
     * seconds.
     * <p>
     * TecTech is handed the computation for the whole research rather than the time, and works the time out itself as
     * {@code totalComputationRequired / computationRequiredPerSec}, in seconds. The total is therefore exactly
     * {@code COMPUTATION_PER_SECOND * RESEARCH_SECONDS} = 39,320,400: that is what the station's NEI page prints as
     * "Total computation" (the recipe's {@code mComputation}) and what it charges its computation buffer
     * ({@code MTEResearchStation.computationRequired = mComputation * 20}) at {@code eRequiredData =
     * mComputationRequiredPerSec} per second, which is 1,200 seconds.
     */
    private static final int COMPUTATION_PER_SECOND = 32_767;
    private static final int RESEARCH_SECONDS = 1_200;
    private static final int TOTAL_COMPUTATION = COMPUTATION_PER_SECOND * RESEARCH_SECONDS;

    /** 1,327,684,600 EU/t, the research voltage the design document gives. */
    private static final int RESEARCH_VOLTAGE = 1_327_684_600;

    /**
     * The amperage the Research Station draws for the research. The design document does not give one, so the smallest
     * one is used; TecTech clamps it to 1..32767 anyway.
     */
    private static final int RESEARCH_AMPERAGE = 1;

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

        // The research item: this is what the player puts into the Research Station's object holder, and what the
        // station matches the recipe against in research-station mode. (It is the same stack the recipe then builds.)
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

        // The four catalysts, each in the amount the design document gives. A missing fluid is fatal to the recipe, so
        // it is reported and the recipe is dropped rather than registered incomplete. They are collected first because
        // the TecTech adder wants the whole array at once.
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

        // The recipe builds the controller of the machine itself. The scanned item is only what the Research Station
        // reads the recipe out of.
        ItemStack controller = controllerStack();
        if (controller == null) {
            MyMod.LOG.error(
                "The controller of the Transcendent Catalyst Maker is not registered, so its assembly line recipe is "
                    + "not registered either.");
            return;
        }

        // TecTech registers the recipe as a research-station one, which is what makes the Research Station write the
        // data stick in research-station mode. No SCANNING metadata is involved anywhere in this path.
        int stationRecipesBefore = TecTechRecipeMaps.researchableALRecipeList.size();
        boolean added = TTRecipeAdder.addResearchableAssemblylineRecipe(
            researchItem,
            TOTAL_COMPUTATION,
            COMPUTATION_PER_SECOND,
            RESEARCH_VOLTAGE,
            RESEARCH_AMPERAGE,
            new Object[] { megaBlastFurnace, megaVacuumFreezer, megaAlloyBlastSmelter, industrialMixer,
                largeFluidExtractor, fusionComputerMk4, uevCircuit, fieldGeneratorUev },
            catalysts.toArray(new FluidStack[0]),
            controller,
            RECIPE_DURATION,
            RECIPE_EUT);

        if (!added) {
            MyMod.LOG.error(
                "TecTech refused the research-station recipe of the Preliminary Study: Transcendent Catalyst Maker "
                    + "(research item {}, output {}, {} item inputs, {} fluid inputs); nothing was registered.",
                describe(researchItem),
                describe(controller),
                8,
                catalysts.size());
            return;
        }

        int stationRecipesAfter = TecTechRecipeMaps.researchableALRecipeList.size();
        if (stationRecipesAfter != stationRecipesBefore + 1) {
            MyMod.LOG.error(
                "TecTech's research-station recipe list grew by {} instead of 1, so the research requirement of {} "
                    + "computation per second for {} seconds may not be the one the Research Station runs.",
                stationRecipesAfter - stationRecipesBefore,
                COMPUTATION_PER_SECOND,
                RESEARCH_SECONDS);
        }

        // What the Research Station's own NEI page will show. TecTech packs RESEARCH_STATION_DATA as
        // "amperage | computationPerSecond << 16", with the computation clamped to 16 signed bits - and since
        // COMPUTATION_PER_SECOND is that ceiling, the displayed figure is the one that was asked for.
        MyMod.LOG.info(
            "The 闪存 / data stick of the Transcendent Catalyst Maker is written by the Research Station in "
                + "research-station mode ({} = {}), not in scanner mode: the recipe was registered through "
                + "TTRecipeAdder.addResearchableAssemblylineRecipe, which puts it into "
                + "TecTechRecipeMaps.researchableALRecipeList ({}) and into "
                + "GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes, and no SCANNING metadata is used anywhere. "
                + "Scan target (the stack the object holder has to hold): {}. Research requirement: {} computation per "
                + "second for {} seconds = {} computation in total, at {} EU/t and amperage {}. TecTech's NEI page for "
                + "the Research Station shows what was asked for rather than a clamped figure: Total computation {} / "
                + "min computation per second {} (= {} seconds).",
            "MTEResearchStation.MODE_RESEARCH_STATION",
            MTEResearchStation.MODE_RESEARCH_STATION,
            "TecTechRecipeMaps.researchableALRecipeList",
            describe(researchItem),
            COMPUTATION_PER_SECOND,
            RESEARCH_SECONDS,
            TOTAL_COMPUTATION,
            RESEARCH_VOLTAGE,
            RESEARCH_AMPERAGE,
            TOTAL_COMPUTATION,
            COMPUTATION_PER_SECOND,
            TOTAL_COMPUTATION / COMPUTATION_PER_SECOND);

        MyMod.LOG.info(
            "Registered the research-station and assembly line recipe of the Transcendent Catalyst Maker controller "
                + "(output: {}; order: "
                + "64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {} -> 64x {}, with {} / {} / {} / {} "
                + "mB of the four catalysts, {} EU/t for {} ticks ({} seconds), research item '{}', research {} EU/t at "
                + "amperage {}, research requirement {} computation per second for {} seconds = {} in total)",
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
            RECIPE_DURATION_SECONDS,
            describe(researchItem),
            RESEARCH_VOLTAGE,
            RESEARCH_AMPERAGE,
            COMPUTATION_PER_SECOND,
            RESEARCH_SECONDS,
            TOTAL_COMPUTATION);
    }

    /**
     * The controller this recipe builds.
     * <p>
     * This mod's own machine is registered as a MetaTileEntity, so its item is whatever that registration hands out and
     * is read from the registry rather than built here.
     */
    private static ItemStack controllerStack() {
        IMetaTileEntity controller = GregTechAPI.METATILEENTITIES[com.qionsi.simplification.MetaTileIDs.TRANSCENDENT_CATALYST_MAKER_CONTROLLER];
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
