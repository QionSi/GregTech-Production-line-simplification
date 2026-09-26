package com.qionsi.simplification.recipe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;

/**
 * The Petrochemical Complex recipes, read from an editable text file.
 * <p>
 * The file lives at {@code config/simplification/petrochemical_complex_recipes.cfg} inside the game directory. If it
 * does not exist it is written with the default recipes, so the current values can always be inspected and changed
 * without touching code. {@code /simplification reload} re-reads the file and swaps the recipes in the running game -
 * no restart needed.
 *
 * <h2>File format</h2>
 *
 * <pre>
 * # One [section] per recipe.
 * [polyethylene]
 * circuit  = 1                 # programming circuit, 0 for none
 * duration = 600               # ticks
 * eut      = 32                # EU per tick
 * fluidIn  = oil * 10000 | oxygen * 10000 | steam * 12000
 * fluidOut = polyethylene * 1656
 * itemOut  = sulfur * 1 | carbon * 1 | carbon * 1 @ 33%
 * </pre>
 *
 * Values are separated by {@code |}. An entry is either {@code <name> * <amount>}, optionally followed by
 * {@code @ <chance>}, or {@code oredict:<oreDictName> * <amount>}.
 * <ul>
 * <li>{@code oil} is special: it accepts any of GregTech's four oil grades.</li>
 * <li>Other fluid names are GregTech material names, e.g. {@code oxygen}, {@code steam}, {@code polyethylene}. The
 * state is looked up automatically (gas, then liquid, then molten).</li>
 * <li>Item names are GregTech material names with a known prefix, e.g. {@code sulfur} (dust), {@code carbon}
 * (dust). Well known GregTech items can also be named by their {@code ItemList} constant, e.g.
 * {@code Hull_LV}.</li>
 * </ul>
 */
public final class RecipeConfig {

    /** Name written into the config file; also the command name. */
    public static final String FILE_NAME = "petrochemical_complex_recipes.cfg";

    private static final String ANY_OIL_KEYWORD = "oil";
    private static final String STEAM_KEYWORD = "steam";
    private static final String WATER_KEYWORD = "water";

    /** Parsed recipes, in file order. */
    private final List<Recipe> recipes = new ArrayList<>();

    private final List<String> errors = new ArrayList<>();

    /** One recipe as written in the file. */
    public static final class Recipe {

        public String name = "";
        public int circuit;
        public int duration;
        public int eut;
        public final List<Weighted<ItemStack>> itemIn = new ArrayList<>();
        public final List<Weighted<ItemStack>> itemOut = new ArrayList<>();
        public final List<FluidEntry> fluidIn = new ArrayList<>();
        public final List<FluidEntry> fluidOut = new ArrayList<>();
    }

    /** An item together with its input/output chance in percent; 100 means always. */
    public static final class Weighted<T> {

        public final T value;
        public final int chancePercent;

        Weighted(T value, int chancePercent) {
            this.value = value;
            this.chancePercent = chancePercent;
        }
    }

    /** A fluid amount. {@code anyOil} marks the special "any oil grade" input. */
    public static final class FluidEntry {

        public final Fluid fluid;
        public final int amount;
        public final boolean anyOil;

        FluidEntry(Fluid fluid, int amount, boolean anyOil) {
            this.fluid = fluid;
            this.amount = amount;
            this.anyOil = anyOil;
        }
    }

    public List<Recipe> recipes() {
        return recipes;
    }

    /** Problems found while parsing the last file. Empty when everything was understood. */
    public List<String> errors() {
        return errors;
    }

    /**
     * Reads the config file, creating it with the built-in defaults when it is missing.
     *
     * @return this, for chaining
     */
    public RecipeConfig load(File file) {
        recipes.clear();
        errors.clear();
        try {
            if (!file.isFile()) writeDefaultFile(file);
            parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors.add("Could not read " + file + ": " + e);
        }
        return this;
    }

    /**
     * Parses the built-in default recipes. Used as a safety net so the machine always has recipes, and by
     * {@link RecipeManager} to repair a broken file.
     */
    public RecipeConfig loadDefaults() {
        recipes.clear();
        errors.clear();
        parse(
            Arrays.asList(
                ModRecipesFile.defaultFile()
                    .split("\\R")));
        return this;
    }

    /** Writes, or overwrites, the file with the built-in default recipes. */
    public static void writeDefaultFile(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            // noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        Files.write(
            file.toPath(),
            ModRecipesFile.defaultFile()
                .getBytes(StandardCharsets.UTF_8));
    }

    private void parse(List<String> lines) {
        Recipe current = null;
        Map<String, Integer> lineOfKey = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                current = new Recipe();
                current.name = line.substring(1, line.length() - 1)
                    .trim();
                recipes.add(current);
                lineOfKey.clear();
                continue;
            }

            int equals = line.indexOf('=');
            if (equals < 0) {
                errors.add("line " + lineNumber + ": expected `key = value`, got `" + line + "`");
                continue;
            }
            if (current == null) {
                errors.add("line " + lineNumber + ": value outside of any [section]");
                continue;
            }

            String key = line.substring(0, equals)
                .trim()
                .toLowerCase(Locale.ENGLISH);
            String value = line.substring(equals + 1)
                .trim();
            if (lineOfKey.containsKey(key)) {
                errors.add("line " + lineNumber + ": `" + key + "` already set on line " + lineOfKey.get(key));
                continue;
            }
            lineOfKey.put(key, lineNumber);

            try {
                switch (key) {
                    case "circuit" -> current.circuit = Integer.parseInt(value);
                    case "duration" -> current.duration = Integer.parseInt(value);
                    case "eut" -> current.eut = Integer.parseInt(value);
                    case "itemin" -> parseItems(value, current.itemIn, lineNumber);
                    case "itemout" -> parseItems(value, current.itemOut, lineNumber);
                    case "fluidin" -> parseFluids(value, current.fluidIn, lineNumber);
                    case "fluidout" -> parseFluids(value, current.fluidOut, lineNumber);
                    default -> errors.add("line " + lineNumber + ": unknown key `" + key + "`");
                }
            } catch (RuntimeException e) {
                errors.add("line " + lineNumber + ": " + e.getMessage());
            }
        }
    }

    private void parseItems(String value, List<Weighted<ItemStack>> target, int lineNumber) {
        for (String part : split(value)) {
            String text = part;
            int chance = 100;
            int at = text.indexOf('@');
            if (at >= 0) {
                chance = parsePercent(text.substring(at + 1));
                text = text.substring(0, at);
            }
            ParsedEntry parsed = splitAmount(text);
            ItemStack stack = resolveItem(parsed.name, parsed.amount, lineNumber);
            if (stack != null) target.add(new Weighted<>(stack, chance));
        }
    }

    private void parseFluids(String value, List<FluidEntry> target, int lineNumber) {
        for (String part : split(value)) {
            ParsedEntry parsed = splitAmount(part);
            String name = parsed.name.toLowerCase(Locale.ENGLISH);
            if (ANY_OIL_KEYWORD.equals(name)) {
                target.add(new FluidEntry(null, parsed.amount, true));
                continue;
            }
            Fluid fluid = resolveFluid(name, lineNumber);
            if (fluid != null) target.add(new FluidEntry(fluid, parsed.amount, false));
        }
    }

    /** One {@code <name> * <amount>} entry from the file. */
    private static final class ParsedEntry {

        final String name;
        final int amount;

        ParsedEntry(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<>();
        for (String part : value.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) parts.add(trimmed);
        }
        return parts;
    }

    /** Splits {@code "sulfur * 1"} into name and amount, defaulting the amount to 1. */
    private static ParsedEntry splitAmount(String text) {
        String name = text.trim();
        int amount = 1;
        int star = name.indexOf('*');
        if (star >= 0) {
            String amountText = name.substring(star + 1)
                .trim();
            name = name.substring(0, star)
                .trim();
            if (!amountText.isEmpty()) amount = Integer.parseInt(amountText);
        }
        return new ParsedEntry(name, amount);
    }

    private static int parsePercent(String text) {
        String trimmed = text.trim();
        boolean fraction = trimmed.endsWith("%");
        if (fraction) trimmed = trimmed.substring(0, trimmed.length() - 1)
            .trim();
        double parsed = Double.parseDouble(trimmed);
        if (fraction) return (int) Math.round(parsed);
        // A plain number above 1 is already a percentage, otherwise treat it as a 0..1 fraction.
        return parsed <= 1.0 ? (int) Math.round(parsed * 100) : (int) Math.round(parsed);
    }

    private ItemStack resolveItem(String name, int amount, int lineNumber) {
        if (name.toLowerCase(Locale.ENGLISH)
            .startsWith("oredict:")) {
            String oreName = name.substring("oredict:".length())
                .trim();
            ItemStack unified = GTOreDictUnificator.get(oreName, amount);
            if (unified != null) return unified;
            if (!OreDictionary.getOres(oreName)
                .isEmpty()) {
                ItemStack first = OreDictionary.getOres(oreName)
                    .get(0)
                    .copy();
                first.stackSize = amount;
                return first;
            }
            errors.add("line " + lineNumber + ": unknown ore dictionary entry `" + oreName + "`");
            return null;
        }

        // A GregTech material name means "dust of that material", which is what the by-products are.
        Materials material = findMaterial(name);
        if (material != null) {
            ItemStack dust = GTOreDictUnificator.get(OrePrefixes.dust, material, amount);
            if (dust != null) return dust;
        }

        // Otherwise fall back to GregTech's own item registry, e.g. `Hull_LV`.
        try {
            ItemList entry = ItemList.valueOf(name);
            ItemStack stack = entry.get(amount);
            if (stack != null) return stack;
        } catch (IllegalArgumentException ignored) {
            // not an ItemList constant
        }

        errors.add("line " + lineNumber + ": cannot resolve item `" + name + "`");
        return null;
    }

    private Fluid resolveFluid(String name, int lineNumber) {
        String key = normalise(name);

        // `steam` is a real fluid but not a real material: GregTech assigns Materials.Water = Materials.Steam, so a
        // name lookup for "steam" lands on the water material. `water` is that very material, and asking for its
        // "primary" fluid would hand back steam instead. Both therefore need to be named explicitly.
        if (STEAM_KEYWORD.equals(key) && Materials.Steam.mGas != null) {
            return Materials.Steam.mGas;
        }
        if (WATER_KEYWORD.equals(key) && Materials.Water.mFluid != null) {
            return Materials.Water.mFluid;
        }

        Materials material = findMaterial(name);
        if (material != null) {
            Fluid fluid = primaryFluidOf(material);
            if (fluid != null) return fluid;
        }
        Fluid registered = FluidRegistry.getFluid(name);
        if (registered != null) return registered;
        // The file is written in lower case for readability and GregTech registers its fluids in lower case too, but
        // tags such as ".liquid" may be missing, so fall back to a scan.
        registered = findRegisteredFluid(key);
        if (registered != null) return registered;
        errors.add("line " + lineNumber + ": unknown fluid `" + name + "`");
        return null;
    }

    /**
     * Looks a GregTech material up by name, ignoring case and separators.
     * <p>
     * {@link Materials#get(String)} is case sensitive and, worse, answers {@link Materials#_NULL} rather than
     * {@code null} when it does not know the name - checking the result for null silently accepts garbage. This
     * helper builds a normalised index once and never returns a placeholder.
     */
    private static Materials findMaterial(String name) {
        String key = normalise(name);
        if (key.isEmpty()) return null;
        return MATERIAL_INDEX.get(key);
    }

    private static final Map<String, Materials> MATERIAL_INDEX = buildMaterialIndex();

    /**
     * Every alias a material can be addressed by, normalised.
     * <p>
     * GregTech's own naming is inconsistent, so one name per material is not enough:
     * <ul>
     * <li>{@code Materials.Polyethylene} has the internal name {@code "Plastic"} and the display name
     * {@code "Polyethylene (PE)"}, so neither the field name nor the display name alone can be used.</li>
     * <li>Display names often carry a parenthesised abbreviation, e.g. {@code "Polyvinyl Chloride (PVC)"} - both the
     * full name and the abbreviation are registered.</li>
     * <li>The Java field name is added as well, since that is the name most players and addon authors see.</li>
     * </ul>
     */
    private static Map<String, Materials> buildMaterialIndex() {
        Map<String, Materials> index = new HashMap<>();
        for (Map.Entry<String, Materials> entry : Materials.getMaterialsMap()
            .entrySet()) {
            Materials material = entry.getValue();
            if (material == null || material == Materials._NULL) continue;
            addAliases(index, material, entry.getKey());
            addAliases(index, material, material.mName);
            addAliases(index, material, material.mDefaultLocalName);
            for (String field : materialFieldNames(material)) {
                addAliases(index, material, field);
            }
        }
        return index;
    }

    private static void addAliases(Map<String, Materials> index, Materials material, String alias) {
        if (alias == null || alias.isEmpty()) return;
        for (String variant : aliasVariants(alias)) {
            if (!variant.isEmpty()) index.putIfAbsent(variant, material);
        }
    }

    /** The full name, the name with any {@code (...)} part removed, and that part on its own. */
    private static List<String> aliasVariants(String alias) {
        List<String> variants = new ArrayList<>(3);
        variants.add(normalise(alias));

        int open = alias.indexOf('(');
        int close = alias.indexOf(')', Math.max(open, 0));
        if (open >= 0) {
            variants.add(normalise(alias.substring(0, open)));
            if (close > open) {
                variants.add(normalise(alias.substring(open + 1, close)));
            }
        }
        return variants;
    }

    /** Java field names of a material, read reflectively because GregTech keeps no such map. */
    private static List<String> materialFieldNames(Materials material) {
        List<String> names = new ArrayList<>(2);
        Class<?> type = Materials.class;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != Materials.class) continue;
                try {
                    if (field.get(null) == material) names.add(field.getName());
                } catch (ReflectiveOperationException ignored) {
                    // Field is not readable; nothing to add.
                }
            }
            type = type.getSuperclass();
        }
        return names;
    }

    /** Strips everything that is not a letter or digit and lower cases the rest. */
    private static String normalise(String text) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    /** The fluid a material is normally handled as, preferring gas then liquid then molten. */
    private static Fluid primaryFluidOf(Materials material) {
        if (material.mGas != null) return material.mGas;
        if (material.mFluid != null) return material.mFluid;
        return material.mStandardMoltenFluid;
    }

    private static Fluid findRegisteredFluid(String name) {
        String key = normalise(name);
        for (Fluid fluid : FluidRegistry.getRegisteredFluids()
            .values()) {
            if (fluid != null && normalise(fluid.getName()).equals(key)) return fluid;
        }
        return null;
    }

    /**
     * Builds every parsed recipe. Nothing is added to the recipe pool; the caller decides what to do with the result,
     * which is what makes a half-finished edit harmless - a broken file simply produces no recipes and a list of
     * errors instead of wiping the working ones.
     *
     * @return the recipe builders, ready to be handed to the recipe pool
     */
    public List<GTRecipeBuilder> buildAll() {
        List<GTRecipeBuilder> builders = new ArrayList<>();
        for (Recipe recipe : recipes) {
            GTRecipeBuilder builder = toBuilder(recipe);
            if (builder != null) builders.add(builder);
        }
        return builders;
    }

    private GTRecipeBuilder toBuilder(Recipe recipe) {
        if (recipe.duration <= 0) {
            errors.add("[" + recipe.name + "]: duration must be positive");
            return null;
        }
        if (recipe.eut <= 0) {
            errors.add("[" + recipe.name + "]: eut must be positive");
            return null;
        }
        if (recipe.fluidIn.isEmpty() && recipe.itemIn.isEmpty()) {
            errors.add("[" + recipe.name + "]: recipe has no inputs");
            return null;
        }
        if (recipe.fluidOut.isEmpty() && recipe.itemOut.isEmpty()) {
            errors.add("[" + recipe.name + "]: recipe has no outputs");
            return null;
        }

        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .duration(recipe.duration)
            .eut(recipe.eut);

        if (recipe.circuit > 0) builder.circuit(recipe.circuit);

        if (!recipe.itemIn.isEmpty()) {
            builder.itemInputs(itemsOf(recipe.itemIn));
        }
        if (!recipe.fluidIn.isEmpty()) {
            builder.fluidInputs(fluidInputObjects(recipe.fluidIn));
        }
        if (!recipe.fluidOut.isEmpty()) {
            builder.fluidOutputs(fluidStacksOf(recipe.fluidOut));
        }
        if (!recipe.itemOut.isEmpty()) {
            builder.itemOutputs(itemsOf(recipe.itemOut));
            int[] chances = new int[recipe.itemOut.size()];
            boolean anyChance = false;
            for (int i = 0; i < chances.length; i++) {
                chances[i] = Math.max(0, Math.min(10000, recipe.itemOut.get(i).chancePercent * 100));
                if (chances[i] != 10000) anyChance = true;
            }
            if (anyChance) builder.outputChances(chances);
        }
        return builder;
    }

    private static ItemStack[] itemsOf(List<Weighted<ItemStack>> entries) {
        ItemStack[] stacks = new ItemStack[entries.size()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = entries.get(i).value;
        }
        return stacks;
    }

    private static FluidStack[] fluidStacksOf(List<FluidEntry> entries) {
        FluidStack[] stacks = new FluidStack[entries.size()];
        for (int i = 0; i < stacks.length; i++) {
            FluidEntry entry = entries.get(i);
            stacks[i] = new FluidStack(entry.fluid, entry.amount);
        }
        return stacks;
    }

    /**
     * Fluid inputs as {@code Object[]} so the "any oil" entry can be handed over as a
     * {@link gregtech.api.objects.SubstituteFluidStack}.
     */
    private static Object[] fluidInputObjects(List<FluidEntry> entries) {
        Object[] objects = new Object[entries.size()];
        for (int i = 0; i < objects.length; i++) {
            FluidEntry entry = entries.get(i);
            objects[i] = entry.anyOil ? ModRecipesFile.anyOil(entry.amount) : new FluidStack(entry.fluid, entry.amount);
        }
        return objects;
    }
}
