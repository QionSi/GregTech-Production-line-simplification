package com.qionsi.simplification.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import com.qionsi.simplification.item.ItemNascentWhiteBranch;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The two things the 初生白枝 / Nascent White Branch has to do outside of its own tick: keep the wearer alive and keep
 * the wearer's inventory on death.
 *
 * <h2>Why cancelling the attack is enough for /kill</h2>
 *
 * 1.7.10's {@code CommandKill} calls {@code EntityPlayerMP.attackEntityFrom(DamageSource, Float.MAX_VALUE)}, and every
 * damage source - void, creative, magic, everything a mod invents - goes through the same method, where Forge fires
 * {@link LivingAttackEvent} first. Cancelling it there stops the damage before any of it is applied, so the wearer
 * cannot be hurt at all rather than being healed after the fact.
 *
 * <p>
 * {@link LivingDeathEvent} is kept as a second net for the paths that set the health to zero directly instead of going
 * through damage, and the tick in {@link ItemNascentWhiteBranch} refills the health bar for the same reason.
 */
public final class BaubleEventHandler {

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        if (ItemNascentWhiteBranch.isWorn((EntityPlayer) event.entityLiving)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (!ItemNascentWhiteBranch.isWorn(player)) return;
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
    }

    @SubscribeEvent
    public void onPlayerDrops(PlayerDropsEvent event) {
        if (event.entityPlayer == null) return;
        if (ItemNascentWhiteBranch.isWorn(event.entityPlayer)) event.setCanceled(true);
    }
}
