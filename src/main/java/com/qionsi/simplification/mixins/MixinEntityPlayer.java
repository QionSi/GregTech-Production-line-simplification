package com.qionsi.simplification.mixins;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.qionsi.simplification.item.ItemNascentWhiteBranch;

/**
 * Pins the armour value of a player wearing the 初生白枝 / Nascent White Branch at 40.
 * <p>
 * 1.7.10 has no armour attribute: the armour bar and the damage reduction both come from
 * {@code EntityPlayer.getTotalArmorValue()}, which adds up the four armour slots. Overriding that number while the
 * bauble is worn is therefore the only way to make the stat actually read 40 - and, unlike a fake HUD value, it is the
 * same number every other mod and the damage formula sees.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer {

    @Inject(method = "getTotalArmorValue", at = @At("HEAD"), cancellable = true)
    private void simplification$nascentWhiteBranchArmour(CallbackInfoReturnable<Integer> callback) {
        if (ItemNascentWhiteBranch.isWorn((EntityPlayer) (Object) this)) {
            callback.setReturnValue(ItemNascentWhiteBranch.ARMOR);
        }
    }
}
