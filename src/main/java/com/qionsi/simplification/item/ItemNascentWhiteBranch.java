package com.qionsi.simplification.item;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;

import com.qionsi.simplification.MyMod;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.research.PlayerKnowledge;
import thaumcraft.common.lib.research.ResearchManager;

/**
 * 初生白枝 / Nascent White Branch: a belt bauble that turns its wearer into something that cannot die.
 *
 * <h2>What it does while worn</h2>
 *
 * <ul>
 * <li>every Thaumcraft research is completed for the wearer;</li>
 * <li>temporary warp is pinned at {@value #WARP_TEMP}, sticky and permanent warp at 0;</li>
 * <li>no damage is taken at all - {@link com.qionsi.simplification.event.BaubleEventHandler} cancels it, which also
 * covers {@code /kill}, because 1.7.10's kill command is an ordinary {@code attackEntityFrom};</li>
 * <li>armour 40 (through {@link com.qionsi.simplification.mixins.MixinEntityPlayer}), maximum health
 * {@value #HEALTH} and food {@value #FOOD};</li>
 * <li>a fixed set of buffs, and every bad potion effect is removed again as soon as it appears;</li>
 * <li>creative flight;</li>
 * <li>a fall into the void (below {@value #VOID_RESCUE_Y}) is undone by teleporting back to the surface;</li>
 * <li>the inventory survives death, should anything ever manage to kill the wearer.</li>
 * </ul>
 *
 * <p>
 * Everything except the armour is re-applied on every tick and taken back on unequip, so the player's own numbers are
 * only borrowed, never permanently rewritten.
 */
public class ItemNascentWhiteBranch extends Item implements IBauble {

    /** Armour points the wearer is given, i.e. the value {@code getTotalArmorValue()} is fixed to. */
    public static final int ARMOR = 40;

    /** Maximum health the wearer is given. */
    public static final int HEALTH = 100;

    /** Food level the wearer is given. */
    public static final int FOOD = 40;

    /** Temporary warp the wearer is pinned to; the other two warps are pinned to zero. */
    /** Set once when the Baubles inventory cannot be read, so the per-tick scan does not log a trace every tick. */
    private static final java.util.concurrent.atomic.AtomicBoolean baubleFailureReported = new java.util.concurrent.atomic.AtomicBoolean();

    public static final int WARP_TEMP = 30;

    /**
     * 永久 and sticky warp last written per player, so a tick that changed neither can be skipped.
     * <p>
     * Keyed by player name rather than by entity because the knowledge store is keyed that way too; the entry is
     * dropped when the branch is taken off so putting it back on pins the warp again.
     */
    private static final java.util.Map<String, int[]> LAST_SEEN_WARP = new java.util.concurrent.ConcurrentHashMap<>();

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("3d1c8a52-6f47-4c1e-9b2e-5a7c4f0d8b31");
    private static final String HEALTH_MODIFIER_NAME = "Nascent White Branch";

    private static final int BUFF_DURATION = 400;
    private static final int BUFF_REFRESH_BELOW = 300;
    private static final int RESEARCH_INTERVAL = 100;
    private static final int VOID_RESCUE_Y = -64;

    /** The buffs the wearer is given: III, III, II, II, II, then the single level ones. */
    private static final Potion[] BUFFS = { Potion.resistance, Potion.regeneration, Potion.digSpeed, Potion.moveSpeed,
        Potion.jump, Potion.nightVision, Potion.waterBreathing, Potion.fireResistance, Potion.damageBoost };

    private static final int[] BUFF_AMPLIFIERS = { 2, 2, 1, 1, 1, 0, 0, 0, 2 };

    private static final int DESCRIPTION_LINES = 5;
    private static final int EFFECT_LINES = 7;

    /** Cache of the last worn check, so the armour Mixin does not walk the bauble inventory on every frame. */
    private static EntityPlayer cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static boolean cachedWorn;

    public ItemNascentWhiteBranch() {
        setUnlocalizedName("simplification.nascentWhiteBranch");
        setTextureName(MyMod.MODID + ":nascent_white_branch");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    /**
     * @return whether {@code player} is wearing the branch in one of their bauble slots
     */
    public static boolean isWorn(EntityPlayer player) {
        if (player == null) return false;
        if (player == cachedPlayer && player.ticksExisted == cachedTick) return cachedWorn;
        boolean worn = scanForBranch(player);
        cachedPlayer = player;
        cachedTick = player.ticksExisted;
        cachedWorn = worn;
        return worn;
    }

    private static boolean scanForBranch(EntityPlayer player) {
        try {
            IInventory baubles = BaublesApi.getBaubles(player);
            if (baubles == null) return false;
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                ItemStack stack = baubles.getStackInSlot(slot);
                if (stack != null && stack.getItem() == ModItems.nascentWhiteBranch) return true;
            }
        } catch (Throwable t) {
            // Baubles is reached through a reflective helper; if it is ever unhappy, the branch is simply not worn.
            // This runs every tick, so the failure is reported once - the trace is dropped from the message for the
            // same reason: log4j walks the frames to format it, and that is not worth doing once per tick.
            if (baubleFailureReported.compareAndSet(false, true)) {
                MyMod.LOG.debug(
                    "Could not read the bauble inventory of " + player.getCommandSenderName()
                        + "; the Nascent White Branch will be treated as not worn. This is reported once.",
                    t);
            }
        }
        return false;
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.BELT;
    }

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && !entity.worldObj.isRemote) {
            applyEffects((EntityPlayer) entity, true);
        }
    }

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && !entity.worldObj.isRemote) {
            removeEffects((EntityPlayer) entity);
        }
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && !entity.worldObj.isRemote) {
            applyEffects((EntityPlayer) entity, false);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        addLocalised(
            list,
            "simplification.tooltip.nascentWhiteBranch.desc.",
            DESCRIPTION_LINES,
            EnumChatFormatting.GRAY,
            true);
        list.add("");
        addLocalised(
            list,
            "simplification.tooltip.nascentWhiteBranch.effect.",
            EFFECT_LINES,
            EnumChatFormatting.GOLD,
            false);
    }

    private static void addLocalised(List<String> list, String prefix, int count, EnumChatFormatting colour,
        boolean italic) {
        String style = colour.toString() + (italic ? EnumChatFormatting.ITALIC.toString() : "");
        for (int line = 1; line <= count; line++) {
            String key = prefix + line;
            if (!StatCollector.canTranslate(key)) continue;
            list.add(style + StatCollector.translateToLocal(key));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Effects
    // ---------------------------------------------------------------------------------------------------------------

    /** Set after the first failure so a broken tick cannot flood the log. */
    private static boolean effectsFailureReported;

    private static final Field SATURATION_LEVEL = saturationLevelField();

    private static void applyEffects(EntityPlayer player, boolean justEquipped) {
        try {
            keepAlive(player);
            keepFed(player);
            keepFlying(player);
            keepBuffed(player);
            keepWarp(player, justEquipped);
            if (justEquipped || player.ticksExisted % RESEARCH_INTERVAL == 0) unlockAllResearch(player);
            rescueFromTheVoid(player);
        } catch (Throwable t) {
            // This runs every tick for every wearer. Reporting the full trace each time is what filled a player's log
            // with hundreds of megabytes per minute, so a repeated failure is reported once and then stays quiet.
            if (!effectsFailureReported) {
                effectsFailureReported = true;
                MyMod.LOG.error(
                    "The Nascent White Branch could not apply its effects to " + player.getCommandSenderName()
                        + "; it will keep retrying every tick, but this is the only time the failure is reported.",
                    t);
            }
        }
    }

    private static void removeEffects(EntityPlayer player) {
        // Forget what the warp was pinned to, so putting the branch back on pins it again.
        LAST_SEEN_WARP.remove(player.getCommandSenderName());
        try {
            IAttributeInstance health = player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
            AttributeModifier healthModifier = health.getModifier(HEALTH_MODIFIER_ID);
            if (healthModifier != null) health.removeModifier(healthModifier);
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        } catch (Throwable t) {
            MyMod.LOG.error("The Nascent White Branch could not take its health bonus back.", t);
        }

        // Flight is only taken back from players who do not have it for another reason.
        if (!player.capabilities.isCreativeMode) {
            player.capabilities.allowFlying = false;
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
        }
    }

    /** Health to {@value #HEALTH}, topped up every tick; this is also the net under a direct "set health to zero". */
    private static void keepAlive(EntityPlayer player) {
        IAttributeInstance maxHealth = player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        double bonus = HEALTH - maxHealth.getBaseValue();
        AttributeModifier modifier = maxHealth.getModifier(HEALTH_MODIFIER_ID);
        if (modifier == null || modifier.getAmount() != bonus) {
            if (modifier != null) maxHealth.removeModifier(modifier);
            maxHealth.applyModifier(new AttributeModifier(HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, bonus, 0));
        }
        if (player.getHealth() < player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /**
     * Food to {@value #FOOD}: level and saturation, every tick.
     * <p>
     * {@code FoodStats.getSaturationLevel()} and {@code setFoodSaturationLevel(float)} are
     * {@code @SideOnly(Side.CLIENT)} in 1.7.10, so the server build does not contain them at all - calling them there
     * throws {@code NoSuchMethodError} on every tick. That is what filled the log: the call sites were wrapped in the
     * per-tick catch in {@link #applyEffects}, so every tick wrote a full stack trace and a minute of it came to
     * hundreds of megabytes.
     * <p>
     * The {@code saturationLevel} field itself exists on both sides, so it is written through reflection instead. The
     * food level alone is still set the normal way, and if the field cannot be reached the saturation is simply left
     * where it is rather than throwing.
     */
    private static void keepFed(EntityPlayer player) {
        FoodStats stats = player.getFoodStats();
        if (stats.getFoodLevel() != FOOD) stats.setFoodLevel(FOOD);
        if (SATURATION_LEVEL == null) return;
        try {
            if (SATURATION_LEVEL.getFloat(stats) < FOOD) SATURATION_LEVEL.setFloat(stats, FOOD);
        } catch (Throwable ignored) {
            // The food level is the part that matters for survival; saturation is the visual overlay.
        }
    }

    /**
     * {@code FoodStats.foodSaturationLevel}, which has no cross-side accessor in 1.7.10.
     * <p>
     * The field is named {@code foodSaturationLevel} in MCP and {@code field_75125_b} in the obfuscated runtime, so
     * both are tried. An earlier version asked for {@code saturationLevel}, which is not a field of {@code FoodStats}
     * at all - the real names are {@code foodSaturationLevel} / {@code field_75125_b}, and {@code field_75124_e} is
     * {@code prevFoodLevel}.
     * <p>
     * The failure message is built from the throwable's class and text instead of handing the throwable itself to the
     * logger. Log4j formats a throwable by walking its stack frames, which makes it load the classes those frames
     * name; under GTNH's class loader that load can fail inside the logger, and the logger's own exception then
     * escapes this catch and takes the whole pre-init down with it.
     */
    private static Field saturationLevelField() {
        // The field is called foodSaturationLevel in MCP and field_75125_b in the obfuscated runtime. Both names are
        // tried, so the same jar works in a dev environment and in a pack.
        for (String name : new String[] { "foodSaturationLevel", "field_75125_b" }) {
            try {
                Field field = FoodStats.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        MyMod.LOG.warn(
            "Could not reach FoodStats.foodSaturationLevel; the Nascent White Branch will keep the food level up but "
                + "leave saturation alone.");
        return null;
    }

    /** Creative flight, handed out once and then left alone. */
    private static void keepFlying(EntityPlayer player) {
        if (!player.capabilities.allowFlying) {
            player.capabilities.allowFlying = true;
            player.sendPlayerAbilities();
        }
    }

    /** The fixed buffs, plus the removal of every bad potion effect. */
    private static void keepBuffed(EntityPlayer player) {
        for (int i = 0; i < BUFFS.length; i++) {
            PotionEffect active = player.getActivePotionEffect(BUFFS[i]);
            if (active == null || active.getDuration() < BUFF_REFRESH_BELOW
                || active.getAmplifier() != BUFF_AMPLIFIERS[i]) {
                player.addPotionEffect(new PotionEffect(BUFFS[i].id, BUFF_DURATION, BUFF_AMPLIFIERS[i], true));
            }
        }
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion != null && potion.isBadEffect()) player.removePotionEffect(effect.getPotionID());
        }
    }

    /**
     * 临时扭曲钉在 {@value #WARP_TEMP}，粘性与永久扭曲清零。
     * <p>
     * Pinned only when the branch is first worn and then only when a warp change is detected - the previous version
     * compared against the target every tick, but temporary warp <em>decays on its own</em>, so
     * {@code getWarpTemp() != WARP_TEMP} was true almost every second. Each of those wrote the value back and sent
     * three sync packets, which is what made Thaumcraft spam the warp notice in the corner.
     * <p>
     * Decay is deliberately not treated as a change: re-pinning it would just restart the same loop. Only permanent and
     * sticky warp are watched, because those are what other Thaumcraft content adds and what the branch exists to
     * suppress.
     */
    private static void keepWarp(EntityPlayer player, boolean justEquipped) {
        PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        if (knowledge == null) return;
        String name = player.getCommandSenderName();

        int permanent = knowledge.getWarpPerm(name);
        int sticky = knowledge.getWarpSticky(name);
        int[] last = LAST_SEEN_WARP.get(name);

        // Nothing to do on a tick where the warp we watch has not moved and the branch was already worn.
        boolean changed = justEquipped || last == null || last[0] != permanent || last[1] != sticky;
        if (!changed) return;

        knowledge.setWarpTemp(name, WARP_TEMP);
        knowledge.setWarpPerm(name, 0);
        knowledge.setWarpSticky(name, 0);
        LAST_SEEN_WARP.put(name, new int[] { 0, 0 });
        syncWarp(player);
    }

    /**
     * Sends the three warp values Thaumcraft syncs - permanent (0), sticky (1) and temporary (2) - to the client. The
     * packet reads the value out of the knowledge itself, so it only has to be sent after a change.
     */
    private static void syncWarp(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        for (byte type = 0; type <= 2; type++) {
            PacketHandler.INSTANCE.sendTo(new PacketSyncWarp(player, type), serverPlayer);
        }
    }

    /** Completes every research the wearer does not have yet. */
    private static void unlockAllResearch(EntityPlayer player) {
        ResearchManager manager = Thaumcraft.proxy.getResearchManager();
        if (manager == null) return;
        String name = player.getCommandSenderName();
        int unlocked = 0;
        for (ResearchCategoryList category : ResearchCategories.researchCategories.values()) {
            for (String key : category.research.keySet()) {
                // Keys starting with `!` are the notes Thaumcraft keeps for its own bookkeeping, not real research.
                if (key == null || key.isEmpty() || key.charAt(0) == '!') continue;
                if (ResearchManager.isResearchComplete(name, key)) continue;
                try {
                    manager.completeResearch(player, key);
                    unlocked++;
                } catch (Throwable t) {
                    MyMod.LOG.debug("Could not complete the Thaumcraft research " + key + " for " + name, t);
                }
            }
        }
        if (unlocked > 0) {
            MyMod.LOG.info("Nascent White Branch: unlocked {} Thaumcraft researches for {}", unlocked, name);
        }
    }

    /** Teleports a wearer who fell out of the world back onto the surface. */
    private static void rescueFromTheVoid(EntityPlayer player) {
        if (player.posY >= VOID_RESCUE_Y) return;
        int x = MathHelper.floor_double(player.posX);
        int z = MathHelper.floor_double(player.posZ);
        int y = player.worldObj.getTopSolidOrLiquidBlock(x, z);
        if (y <= 0) {
            // A void world has no surface to come back to, so the world's spawn point is the next best thing.
            ChunkCoordinates spawn = player.worldObj.getSpawnPoint();
            x = spawn.posX;
            z = spawn.posZ;
            y = Math.max(spawn.posY, 1);
        }
        player.setPositionAndUpdate(x + 0.5D, y + 1.0D, z + 0.5D);
        player.fallDistance = 0.0F;
        player.motionY = 0.0D;
        MyMod.LOG.info("Nascent White Branch: pulled {} back out of the void", player.getCommandSenderName());
    }
}
