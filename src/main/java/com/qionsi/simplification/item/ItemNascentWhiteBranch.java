package com.qionsi.simplification.item;

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
    public static final int WARP_TEMP = 50;

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
            MyMod.LOG.debug("Could not read the bauble inventory of " + player.getCommandSenderName(), t);
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

    private static void applyEffects(EntityPlayer player, boolean justEquipped) {
        try {
            keepAlive(player);
            keepFed(player);
            keepFlying(player);
            keepBuffed(player);
            keepWarp(player);
            if (justEquipped || player.ticksExisted % RESEARCH_INTERVAL == 0) unlockAllResearch(player);
            rescueFromTheVoid(player);
        } catch (Throwable t) {
            MyMod.LOG.error(
                "The Nascent White Branch could not apply its effects to " + player.getCommandSenderName()
                    + "; the next tick will try again.",
                t);
        }
    }

    private static void removeEffects(EntityPlayer player) {
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

    /** Food to {@value #FOOD}: level and saturation, every tick. */
    private static void keepFed(EntityPlayer player) {
        if (player.getFoodStats()
            .getFoodLevel() != FOOD) {
            player.getFoodStats()
                .setFoodLevel(FOOD);
        }
        if (player.getFoodStats()
            .getSaturationLevel() < FOOD) {
            player.getFoodStats()
                .setFoodSaturationLevel(FOOD);
        }
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

    /** Temporary warp at {@value #WARP_TEMP}, sticky and permanent warp at zero, told to the client when it changes. */
    private static void keepWarp(EntityPlayer player) {
        PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        if (knowledge == null) return;
        String name = player.getCommandSenderName();
        boolean changed = false;
        if (knowledge.getWarpTemp(name) != WARP_TEMP) {
            knowledge.setWarpTemp(name, WARP_TEMP);
            changed = true;
        }
        if (knowledge.getWarpPerm(name) != 0) {
            knowledge.setWarpPerm(name, 0);
            changed = true;
        }
        if (knowledge.getWarpSticky(name) != 0) {
            knowledge.setWarpSticky(name, 0);
            changed = true;
        }
        if (changed) syncWarp(player);
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
