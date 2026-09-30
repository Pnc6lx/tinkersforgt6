package com.tinkersgt6.enchant;

import java.util.List;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.tinkersgt6.config.TGConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import tconstruct.library.entity.ProjectileBase;

/**
 * Makes Looting on thrown weapons and ammunition actually produce extra drops.
 *
 * <p>
 * Vanilla computes the looting bonus from {@code DamageSource.getEntity() instanceof EntityPlayer}, and an arrow is not
 * a player, so a ranged kill always sees looting 0. TConstruct does not fix this either: every
 * {@code EnchantmentHelper} call in {@code ProjectileBase} is passed {@code shootingEntity}, i.e. it reads the bow in
 * the shooter's hand rather than the arrow that actually hit.
 * </p>
 *
 * <p>
 * GT6 stores the ammunition enchantments in {@code mEnchantmentAmmo}, and ammunition is itself a {@code ToolCore} with
 * its own part materials, so the trait already writes Looting onto the arrow / javelin. This handler reads it back off
 * the projectile and tops the drops up.
 * </p>
 */
public class AmmoLootingHandler {

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (
            !TGConfig.enchantBoolean(
                "ammoLootingCompensation",
                true,
                "Apply the Looting level of thrown weapons and ammunition to the mob drops.")
        ) return;

        if (event.source == null) return;
        Entity source = event.source.getSourceOfDamage();
        if (!(source instanceof ProjectileBase)) return;

        ItemStack ammo = ((ProjectileBase) source).returnStack;
        if (ammo == null || ammo.stackTagCompound == null) return;

        int looting = EnchantmentHelper.getEnchantmentLevel(Enchantment.looting.effectId, ammo);
        if (looting <= 0) return;

        List<EntityItem> drops = event.drops;
        if (drops == null || drops.isEmpty()) return;

        // Matches vanilla's "quantity += rand.nextInt(looting + 1)" roll.
        for (EntityItem drop : drops) {
            if (drop == null) continue;
            ItemStack dropped = drop.getEntityItem();
            if (dropped == null || dropped.getItem() == null || dropped.stackSize <= 0) continue;
            dropped.stackSize += event.entityLiving.worldObj.rand.nextInt(looting + 1);
        }
    }
}
