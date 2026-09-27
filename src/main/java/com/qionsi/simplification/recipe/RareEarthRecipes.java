package com.qionsi.simplification.recipe;

import static bartworks.system.material.WerkstoffLoader.CrudeRhMetall;
import static bartworks.system.material.WerkstoffLoader.IrLeachResidue;
import static bartworks.system.material.WerkstoffLoader.IrOsLeachResidue;
import static bartworks.system.material.WerkstoffLoader.IridiumDioxide;
import static bartworks.system.material.WerkstoffLoader.LeachResidue;
import static bartworks.system.material.WerkstoffLoader.PDAmmonia;
import static bartworks.system.material.WerkstoffLoader.PDMetallicPowder;
import static bartworks.system.material.WerkstoffLoader.PTMetallicPowder;
import static bartworks.system.material.WerkstoffLoader.PTResidue;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.qionsi.simplification.MetaTileIDs;
import com.qionsi.simplification.MyMod;

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.Material;
import gtPlusPlus.core.material.MaterialsElements;

/**
 * The recipes of the 稀土综合处理 / Rare Earth Processing Complex: 11 dust mode recipes and 12 ore mode recipes.
 * <p>
 * The dust mode group is table 0 of {@code more/2.docx}. Each of the eleven recipes takes refined dusts - several of
 * them the intermediates of GregTech's platinum group line - plus a programming circuit and a batch of reagent gases,
 * and gives back the platinum group and rare earth metals. Circuit 1 picks the seven platinum recipes and circuit 2
 * the two rare earth ones.
 * <p>
 * The ore mode group is table 1 of the same document. Each of the twelve recipes washes one crushed ore with 64000 mB
 * of water and gives four dusts; the last row eats netherrack rather than an ore. Circuits 1, 2 and 3 pick between
 * them, and every one of these recipes carries the minimum heating coil the machine compares its own coils against in
 * {@link gregtech.api.util.GTRecipeConstants#COIL_HEAT}: 3601 for Nichrome, 4501 for TPV-Alloy, 6301 for HSS-S and
 * 8101 for Naquadah Alloy.
 * <p>
 * The recipes live in code on purpose: while debugging, editing one of these numbers and letting the IDE swap the
 * method body in takes effect without restarting the game.
 */
public final class RareEarthRecipes {

    /** Every ore mode recipe starts from this much water, in mB. */
    private static final int ORE_WATER = 64000;

    /** 镍铬合金线圈 / Nichrome coil, HV. */
    private static final int COIL_NICHROME = 3601;

    /** 钛铂钒合金线圈 / TPV-Alloy coil, EV. */
    private static final int COIL_TPV = 4501;

    /** 高速钢-s线圈 / HSS-S coil, LuV. */
    private static final int COIL_HSS_S = 6301;

    /** 硅岩合金线圈 / Naquadah Alloy coil, UV. */
    private static final int COIL_NAQUADAH_ALLOY = 8101;

    /** GT++ is where the elements below live that GregTech itself never got around to. */
    private static final MaterialsElements ELEMENTS = MaterialsElements.getInstance();

    /** Set once the recipes are in their maps, so that a late retry cannot register them twice. */
    private static boolean registered;

    /** Set when the registration was handed over to GregTech's postload because the items were not ready yet. */
    private static boolean deferred;

    private RareEarthRecipes() {}

    /**
     * Registers the 23 recipes into their two maps.
     * <p>
     * The dusts of this machine are not all GregTech's own: Bartworks only creates its Werkstoff items and fluids
     * during its init phase and GT++ creates its element dusts during its pre-init, while this mod asks for the
     * recipes during pre-init. When the items are not there yet - which is the normal case, since Bartworks comes
     * later - the registration is handed to {@link GregTechAPI#sAfterGTPostload}, which GregTech runs at the very end
     * of its own postload. A second call then does nothing, so calling this from the init phase instead is just as
     * fine and simply skips the detour.
     */
    public static void init() {
        if (registered) return;
        if (WerkstoffLoader.items.isEmpty()) {
            if (!deferred) {
                deferred = true;
                MyMod.LOG.info(
                    "Bartworks has not built its Werkstoff items yet; the Rare Earth recipes will be registered "
                        + "after GregTech's postload.");
                GregTechAPI.sAfterGTPostload.add(RareEarthRecipes::init);
            }
            return;
        }
        registered = true;
        registerDustMode();
        registerOreMode();
        registerAssemblerRecipe();
        MyMod.LOG.info(
            "Registered {} dust mode and {} ore mode Rare Earth recipes",
            ModRecipeMaps.rareEarthDustRecipes.getBackend()
                .getAllRecipes()
                .size(),
            ModRecipeMaps.rareEarthOreRecipes.getBackend()
                .getAllRecipes()
                .size());
    }

    // spotless:off

    /** The eleven 矿粉模式 / dust mode recipes, table 0 of {@code more/2.docx}. */
    private static void registerDustMode() {
        dust("platinum metallic powder", RareEarthRecipes::registerPlatinumMetallicPowder);
        dust("iridium dioxide", RareEarthRecipes::registerIridiumDioxide);
        dust("palladium metallic powder", RareEarthRecipes::registerPalladiumMetallicPowder);
        dust("palladium enriched ammonia", RareEarthRecipes::registerPalladiumEnrichedAmmonia);
        dust("leach residue", RareEarthRecipes::registerLeachResidue);
        dust("platinum residue", RareEarthRecipes::registerPlatinumResidue);
        dust("crude rhodium metal", RareEarthRecipes::registerCrudeRhodiumMetal);
        dust("rarest metal residue", RareEarthRecipes::registerRarestMetalResidue);
        dust("iridium metal residue", RareEarthRecipes::registerIridiumMetalResidue);
        dust("bastnasite", RareEarthRecipes::registerBastnasite);
        dust("rare earth", RareEarthRecipes::registerRareEarth);
    }

    /**
     * Registers one dust mode recipe and reports it when nothing arrived in the map.
     * <p>
     * GregTech drops a recipe whose ingredient list contains a null - which is what a material without that item form
     * gives back - without saying so, so without this the machine would quietly be missing a recipe.
     */
    private static void dust(String name, Runnable recipe) {
        int before = ModRecipeMaps.rareEarthDustRecipes.getBackend()
            .getAllRecipes()
            .size();
        recipe.run();
        if (ModRecipeMaps.rareEarthDustRecipes.getBackend()
            .getAllRecipes()
            .size() == before) {
            MyMod.LOG.error(
                "The '{}' dust mode Rare Earth recipe was rejected by GregTech, most likely because one of its "
                    + "ingredients does not exist.",
                name);
        }
    }

    /** The twelve 矿石模式 / ore mode recipes, table 1 of {@code more/2.docx}. */
    private static void registerOreMode() {
        registerPyropeOre();
        registerAlmandineOre();
        registerGrossularOre();
        registerSpessartineOre();
        registerRedstoneOre();
        registerNickelOre();
        registerChalcopyriteOre();
        registerPentlanditeOre();
        registerSphaleriteOre();
        registerPlatinumOre();
        registerMonaziteOre();
        registerNetherrack();
    }

    /** Table 0, row 01: 铂金属粉 / Platinum Metallic Powder, refined into the whole platinum group at once. */
    private static void registerPlatinumMetallicPowder() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(
                dust(PTMetallicPowder, 192),
                dust(Materials.Sulfur, 13),
                dust(Materials.Saltpeter, 137),
                dust(Materials.Sodium, 131))
            .fluidInputs(
                Materials.Oxygen.getGas(743000),
                Materials.Nitrogen.getGas(190000),
                Materials.Hydrogen.getGas(167000))
            .itemOutputs(
                dust(Materials.Platinum, 138),
                dust(PDMetallicPowder, 176),
                dust(ELEMENTS.RHODIUM, 29),
                dust(ELEMENTS.RUTHENIUM, 99),
                dust(Materials.Iridium, 42),
                dust(Materials.Osmium, 5),
                dust(Materials.Gold, 17),
                dust(Materials.Copper, 21),
                dust(Materials.Nickel, 21),
                dust(Materials.Silicon, 9))
            .fluidOutputs(Materials.Chlorine.getGas(158000))
            .duration(300 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 02: 二氧化铱 / Iridium Dioxide, split back into iridium. */
    private static void registerIridiumDioxide() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(dust(IridiumDioxide, 2))
            .itemOutputs(dust(Materials.Iridium, 2), dust(Materials.Nickel, 1), dust(Materials.Copper, 1))
            .fluidOutputs(Materials.Oxygen.getGas(4000))
            .duration(10 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 03: 钯金属粉 / Palladium Metallic Powder. */
    private static void registerPalladiumMetallicPowder() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(dust(PDMetallicPowder, 90))
            .fluidInputs(
                Materials.Oxygen.getGas(64000),
                Materials.Nitrogen.getGas(224000),
                Materials.Hydrogen.getGas(546000))
            .itemOutputs(dust(Materials.Palladium, 64), dust(Materials.Carbon, 64))
            .duration(120 * SECONDS)
            .eut(16384)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 04: 富钯氨 / Palladium Enriched Ammonia, the fluid form of the same feed. */
    private static void registerPalladiumEnrichedAmmonia() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .fluidInputs(
                PDAmmonia.getFluidOrGas(90000),
                Materials.Hydrogen.getGas(538000),
                Materials.Oxygen.getGas(64000),
                Materials.Nitrogen.getGas(134000))
            .itemOutputs(dust(Materials.Palladium, 64), dust(Materials.Carbon, 64))
            .duration(120 * SECONDS)
            .eut(16384)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 05: 浸出渣粉 / Leach Residue. */
    private static void registerLeachResidue() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(
                dust(LeachResidue, 64),
                dust(Materials.Saltpeter, 64),
                dust(Materials.Sodium, 25))
            .fluidInputs(Materials.Hydrogen.getGas(175000))
            .itemOutputs(
                dust(ELEMENTS.RUTHENIUM, 46),
                dust(Materials.Iridium, 20),
                dust(Materials.Osmium, 2),
                dust(Materials.Gold, 8),
                dust(Materials.Copper, 10),
                dust(Materials.Silicon, 4),
                dust(Materials.Nickel, 10))
            .fluidOutputs(Materials.Oxygen.getGas(14000), Materials.Chlorine.getGas(107000))
            .duration(180 * SECONDS)
            .eut(16384)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 06: 铂渣粉 / Platinum Residue. */
    private static void registerPlatinumResidue() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(
                dust(PTResidue, 96),
                dust(Materials.Sodium, 49),
                dust(Materials.Saltpeter, 100))
            .fluidInputs(
                Materials.Nitrogen.getGas(44000),
                Materials.Oxygen.getGas(184000),
                Materials.Hydrogen.getGas(220000))
            .itemOutputs(
                dust(ELEMENTS.RHODIUM, 20),
                dust(ELEMENTS.RUTHENIUM, 72),
                dust(Materials.Iridium, 30),
                dust(Materials.Osmium, 3),
                dust(Materials.Gold, 12),
                dust(Materials.Copper, 15),
                dust(Materials.Nickel, 15),
                dust(Materials.Silicon, 6))
            .fluidOutputs(Materials.Chlorine.getGas(198000))
            .duration(180 * SECONDS)
            .eut(16384)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 07: 粗制铑金属粉 / Crude Rhodium Metal, refined into rhodium and hydrogen. */
    private static void registerCrudeRhodiumMetal() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(dust(CrudeRhMetall, 100), dust(Materials.Sodium, 50))
            .fluidInputs(
                Materials.Nitrogen.getGas(1000),
                Materials.Oxygen.getGas(180000),
                Materials.Chlorine.getGas(90000))
            .itemOutputs(dust(ELEMENTS.RHODIUM, 57))
            .fluidOutputs(Materials.Hydrogen.getGas(71000))
            .duration(180 * SECONDS)
            .eut(16384)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 08: 稀有金属渣粉 / Rarest Metal Residue. */
    private static void registerRarestMetalResidue() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(dust(IrOsLeachResidue, 20))
            .fluidInputs(Materials.Hydrogen.getGas(50000))
            .itemOutputs(
                dust(Materials.Iridium, 10),
                dust(Materials.Osmium, 1),
                dust(Materials.Copper, 5),
                dust(Materials.Silicon, 2),
                dust(Materials.Nickel, 5),
                dust(Materials.Gold, 4))
            .fluidOutputs(Materials.Oxygen.getGas(4000), Materials.Chlorine.getGas(2000))
            .duration(150 * SECONDS)
            .eut(32768)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 09: 铱金属渣粉 / Iridium Metal Residue. */
    private static void registerIridiumMetalResidue() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputsUnsafe(dust(IrLeachResidue, 20))
            .fluidInputs(Materials.Hydrogen.getGas(8000), Materials.Chlorine.getGas(40000))
            .itemOutputs(
                dust(Materials.Iridium, 20),
                dust(Materials.Gold, 8),
                dust(Materials.Copper, 10),
                dust(Materials.Silicon, 10),
                dust(Materials.Nickel, 10))
            .fluidOutputs(Materials.Oxygen.getGas(8000))
            .duration(150 * SECONDS)
            .eut(32768)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 0, row 10: 氟碳镧铈矿粉 / Bastnasite, the light rare earths plus zirconium and titanium. */
    private static void registerBastnasite() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputsUnsafe(
                dust(Materials.Bastnasite, 64),
                dust(Materials.Carbon, 200),
                dust(Materials.Sodium, 16),
                dust(Materials.Saltpeter, 59),
                dust(Materials.Copper, 8))
            .fluidInputs(
                Materials.Hydrogen.getGas(808000),
                Materials.Nitrogen.getGas(210000),
                Materials.Chlorine.getGas(260000))
            .itemOutputs(
                dust(Materials.Cerium, 79),
                dust(Materials.Neodymium, 42),
                dust(Materials.Lanthanum, 26),
                dust(Materials.Holmium, 17),
                dust(Materials.Samarium, 11),
                dust(ELEMENTS.ZIRCONIUM, 11),
                dust(Materials.Gadolinium, 6),
                dust(Materials.Terbium, 3),
                dust(Materials.Silicon, 192),
                dust(Materials.Titanium, 163))
            .fluidOutputs(Materials.Fluorine.getGas(12000), Materials.Oxygen.getGas(30000))
            .duration(300 * SECONDS)
            .eut(32768)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /**
     * Table 0, row 11: 稀土 / Rare Earth. This is the recipe that hands out the elements which only show up in traces
     * elsewhere. The document lists lanthanum twice, which is two lanthanum dust here.
     */
    private static void registerRareEarth() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputsUnsafe(dust(Materials.RareEarth, 10))
            .fluidInputs(
                Materials.Oxygen.getGas(12000),
                Materials.Nitrogen.getGas(1000),
                Materials.Hydrogen.getGas(19000))
            .itemOutputs(
                dust(Materials.Yttrium, 1),
                dust(MaterialsElements.STANDALONE.RUNITE, 1),
                dust(ELEMENTS.ZIRCONIUM, 1),
                dust(Materials.Caesium, 1),
                dust(Materials.Ytterbium, 1),
                dust(Materials.Erbium, 1),
                dust(Materials.Lanthanum, 2),
                dust(ELEMENTS.IODINE, 1),
                dust(Materials.Gadolinium, 1),
                dust(Materials.Dysprosium, 1),
                dust(ELEMENTS.GERMANIUM, 1),
                dust(Materials.Tellurium, 1),
                dust(Materials.Tantalum, 1),
                dust(Materials.Niobium, 1),
                dust(MaterialsElements.STANDALONE.BLACK_METAL, 1),
                dust(Materials.Samarium, 1),
                dust(Materials.Cerium, 1),
                dust(Materials.Holmium, 1))
            .fluidOutputs(ELEMENTS.BROMINE.getFluidStack(1000))
            .duration(16 * SECONDS)
            .eut(32768)
            .addTo(ModRecipeMaps.rareEarthDustRecipes);
    }

    /** Table 1, row 01: 粉碎的镁铝榴石矿石 / crushed Pyrope ore, 镍铬合金线圈. */
    private static void registerPyropeOre() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(crushedOre(Materials.Pyrope))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Magnesium, 110),
                dust(Materials.Manganese, 70),
                dust(Materials.Borax, 60),
                dust(ELEMENTS.RHENIUM, 20))
            .metadata(COIL_HEAT, COIL_NICHROME)
            .duration(120 * SECONDS)
            .eut(480)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 02: 粉碎的铁铝榴石矿石 / crushed Almandine ore, 高速钢-s线圈. */
    private static void registerAlmandineOre() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(crushedOre(Materials.Almandine))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Aluminium, 150),
                dust(Materials.Magnesium, 75),
                dust(Materials.Yttrium, 25),
                dust(Materials.Ytterbium, 15))
            .metadata(COIL_HEAT, COIL_HSS_S)
            .duration(60 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 03: 粉碎的钙铝榴石矿石 / crushed Grossular ore, 高速钢-s线圈. */
    private static void registerGrossularOre() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(crushedOre(Materials.Grossular))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Calcium, 180),
                dust(Materials.Aluminium, 110),
                dust(Materials.Tungsten, 60),
                dust(ELEMENTS.THALLIUM, 15))
            .metadata(COIL_HEAT, COIL_HSS_S)
            .duration(60 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 04: 粉碎的锰铝榴石矿石 / crushed Spessartine ore, 高速钢-s线圈. */
    private static void registerSpessartineOre() {
        GTRecipeBuilder.builder()
            .circuit(1)
            .itemInputs(crushedOre(Materials.Spessartine))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Manganese, 150),
                dust(Materials.Aluminium, 90),
                dust(Materials.Osmium, 30),
                dust(Materials.Strontium, 20))
            .metadata(COIL_HEAT, COIL_HSS_S)
            .duration(60 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 05: 粉碎的红石矿石 / crushed Redstone ore, 钛铂钒合金线圈. */
    private static void registerRedstoneOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Redstone))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Redstone, 300),
                dust(Materials.Chrome, 60),
                dust(Materials.Flint, 45),
                dust(Materials.Dysprosium, 16))
            .metadata(COIL_HEAT, COIL_TPV)
            .duration(120 * SECONDS)
            .eut(1960)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 06: 粉碎的镍矿石 / crushed Nickel ore, 钛铂钒合金线圈. */
    private static void registerNickelOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Nickel))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Nickel, 150),
                dust(Materials.Cobalt, 120),
                dust(ELEMENTS.RHODIUM, 32),
                dust(ELEMENTS.RUTHENIUM, 16))
            .metadata(COIL_HEAT, COIL_TPV)
            .duration(120 * SECONDS)
            .eut(1960)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /**
     * Table 1, row 07: 粉碎的黄铜矿石 / crushed brass ore, 钛铂钒合金线圈. The document writes 黄铜, but GregTech has
     * no crushed brass ore - 黄铜矿 / Chalcopyrite is the copper ore that yields this recipe's copper and cadmium, so
     * that is what is used here.
     */
    private static void registerChalcopyriteOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Chalcopyrite))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Copper, 180),
                dust(Materials.Iron, 120),
                dust(Materials.Cadmium, 50),
                dust(Materials.Indium, 10))
            .metadata(COIL_HEAT, COIL_TPV)
            .duration(120 * SECONDS)
            .eut(1960)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 08: 粉碎的镍黄铁矿石 / crushed Pentlandite ore, 高速钢-s线圈. */
    private static void registerPentlanditeOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Pentlandite))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Iron, 150),
                dust(Materials.Nickel, 100),
                dust(Materials.Promethium, 20),
                dust(ELEMENTS.HAFNIUM, 10))
            .metadata(COIL_HEAT, COIL_HSS_S)
            .duration(120 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 09: 粉碎的闪锌矿石 / crushed Sphalerite ore, 高速钢-s线圈. */
    private static void registerSphaleriteOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Sphalerite))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Zinc, 180),
                dust(Materials.Iron, 120),
                dust(Materials.Indium, 64),
                dust(ELEMENTS.GERMANIUM, 15))
            .metadata(COIL_HEAT, COIL_HSS_S)
            .duration(180 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 10: 粉碎的铂矿石 / crushed Platinum ore, 高速钢-s线圈. */
    private static void registerPlatinumOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Platinum))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(PTMetallicPowder, 128),
                dust(ELEMENTS.RHODIUM, 60),
                dust(ELEMENTS.SELENIUM, 40),
                dust(Materials.Tellurium, 10))
            .metadata(COIL_HEAT, COIL_HSS_S)
            .duration(180 * SECONDS)
            .eut(7680)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 11: 粉碎的独居石矿石 / crushed Monazite ore, 硅岩合金线圈. */
    private static void registerMonaziteOre() {
        GTRecipeBuilder.builder()
            .circuit(2)
            .itemInputs(crushedOre(Materials.Monazite))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Erbium, 64),
                dust(Materials.Lanthanum, 32),
                dust(Materials.Lutetium, 16),
                dust(Materials.Europium, 8))
            .metadata(COIL_HEAT, COIL_NAQUADAH_ALLOY)
            .duration(180 * SECONDS)
            .eut(12288)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    /** Table 1, row 12: 地狱岩 / Netherrack, the one ore mode recipe that does not start from an ore. */
    private static void registerNetherrack() {
        GTRecipeBuilder.builder()
            .circuit(3)
            .itemInputs(new ItemStack(Blocks.netherrack, 64))
            .fluidInputs(Materials.Water.getFluid(ORE_WATER))
            .itemOutputs(
                dust(Materials.Sulfur, 256),
                dust(Materials.Antimony, 55),
                dust(Materials.CertusQuartz, 40),
                dust(Materials.NetherQuartz, 40),
                dust(Materials.Ardite, 32),
                dust(Materials.Cobalt, 32))
            .metadata(COIL_HEAT, COIL_NAQUADAH_ALLOY)
            .duration(180 * SECONDS)
            .eut(12288)
            .addTo(ModRecipeMaps.rareEarthOreRecipes);
    }

    // spotless:on

    /**
     * The controller's own assembler recipe: the four machines the line is built out of, four EV circuits and 16000 mB
     * of polystyrene, at 480 EU/t for 30 seconds.
     */
    private static void registerAssemblerRecipe() {
        var controller = GregTechAPI.METATILEENTITIES[MetaTileIDs.RARE_EARTH_COMPLEX_CONTROLLER];
        if (controller == null) return;
        GTRecipeBuilder.builder()
            .itemInputs(
                ItemList.Machine_Multi_LargeChemicalReactor.get(1),
                ItemList.IndustrialCentrifuge.get(1),
                ItemList.Machine_Multi_BlastFurnace.get(1),
                ItemList.Distillation_Tower.get(1),
                // The oredict entry for any EV circuit.
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 4))
            .fluidInputs(Materials.Polystyrene.getMolten(16000))
            .itemOutputs(controller.getStackForm(1))
            .duration(30 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /**
     * {@code amount} of a GregTech dust. The ordinary GregTech helper would cut every amount above 64 down to one
     * stack; the design document is full of amounts like 192 or 300, so the copies made here are allowed to be bigger.
     */
    private static ItemStack dust(Materials material, int amount) {
        return GTUtility.copyAmountUnsafe(amount, GTOreDictUnificator.get(OrePrefixes.dust, material, 1));
    }

    /**
     * {@code amount} of a Bartworks dust, such as one of the platinum group intermediates.
     * <p>
     * Bartworks looks a Werkstoff up through the ore dictionary first and only falls back to its own meta item, and a
     * few of its dusts - Iridium Dioxide among them - are not in the dictionary, which makes the lookup return null.
     * GregTech then drops the whole recipe without a word, so the meta item is used directly when that happens.
     */
    private static ItemStack dust(Werkstoff werkstoff, int amount) {
        ItemStack stack = GTUtility.copyAmountUnsafe(amount, werkstoff.get(OrePrefixes.dust, 1));
        if (stack == null) {
            stack = new ItemStack(WerkstoffLoader.items.get(OrePrefixes.dust), amount, werkstoff.getmID());
            MyMod.LOG.info(
                "Bartworks has no ore dictionary dust for {}; using its meta item instead.",
                werkstoff.getVarName());
        }
        return stack;
    }

    /** {@code amount} of a GT++ dust, which is where the elements GregTech does not have live. */
    private static ItemStack dust(Material element, int amount) {
        return GTUtility.copyAmountUnsafe(amount, element.getDust(1));
    }

    /** 64 of a crushed ore, the solid input every ore mode recipe starts from. */
    private static ItemStack crushedOre(Materials material) {
        return GTUtility.copyAmountUnsafe(64, GTOreDictUnificator.get(OrePrefixes.crushed, material, 1));
    }
}
