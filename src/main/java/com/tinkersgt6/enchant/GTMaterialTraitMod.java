package com.tinkersgt6.enchant;

import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentDamage;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import tconstruct.library.ActiveToolMod;
import tconstruct.library.tools.ToolCore;

/**
 * The GregTech material trait.
 *
 * <p>
 * TConstruct 1.7.10 has no {@code ITrait} system - {@code ToolCore.getTraits()} returns tool <em>type</em> tags, and
 * TConstruct implements every material behaviour it ships (bacon, slime, moss) inside {@code ActiveToolMod} instances
 * driven from {@code ToolCore.onUpdate}. This is the same carrier, which is what makes it a real trait rather than a
 * one-off write at crafting time: a tool that is rebuilt, spawned from NEI or loaded from an old world is brought back
 * to the correct state on the next tick.
 * </p>
 */
public class GTMaterialTraitMod extends ActiveToolMod {

    @Override
    public void updateTool(ToolCore tool, ItemStack stack, World world, Entity entity) {
        if (stack == null || stack.stackTagCompound == null) return;
        if (!stack.stackTagCompound.hasKey("InfiTool")) return;

        NBTTagCompound tags = stack.stackTagCompound.getCompoundTag("InfiTool");
        if (tags.getInteger("Head") < 0 && tags.getInteger("Handle") < 0) return;

        // Cached per part combination, so this is a map lookup in the steady state.
        Map<Integer, Integer> base = EnchantResolver.baseFor(tool, stack);

        boolean hadContribution = tags.hasKey(EnchantReconciler.KEY_CONTRIB);
        if (base.isEmpty() && !hadContribution) return;

        EnchantReconciler.reconcile(stack, tags, base);

        if (!base.isEmpty() && !tags.getBoolean(EnchantReconciler.KEY_TIP_DONE)) {
            writeTraitTip(tags, base);
        }
    }

    /**
     * Same layout TConstruct's {@code ItemModifier.addModifierTip} produces: an empty {@code TooltipN} plus a
     * {@code ModifierTipN} carrying the formatted text.
     */
    private void writeTraitTip(NBTTagCompound tags, Map<Integer, Integer> base) {
        int tipNum = 1;
        while (tags.hasKey("Tooltip" + tipNum)) tipNum++;
        tags.setString("Tooltip" + tipNum, "");
        tags.setString("ModifierTip" + tipNum, EnchantReconciler.modifierTip(base));
        tags.setBoolean(EnchantReconciler.KEY_TIP_DONE, true);
    }

    /**
     * Melee path only. {@code AbilityHelper.onLeftClickEntity} deals damage through
     * {@code attackEntityFrom(DamageSource.causePlayerDamage(...))} and never calls
     * {@code EnchantmentHelper.func_151385_b}, so the extra effect of GT6's {@code EnchantmentDamage} subclasses would
     * never fire. The arrow path is fine already ({@code ProjectileBase} calls it).
     */
    @Override
    public int attackDamage(int modDamage, int currentDamage, ToolCore tool, NBTTagCompound tags,
        NBTTagCompound toolTags, ItemStack stack, EntityLivingBase player, Entity entity) {
        if (player == null || !(entity instanceof EntityLivingBase)) return modDamage;
        if (stack == null || stack.stackTagCompound == null) return modDamage;

        Map<Integer, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) return modDamage;

        for (Map.Entry<Integer, Integer> entry : enchants.entrySet()) {
            Enchantment enchantment = safeEnchantment(entry.getKey());
            if (enchantment == null) continue;
            if (!EnchantmentTable.isGregTechDamage(enchantment)) continue;
            if (!(enchantment instanceof EnchantmentDamage)) continue;

            ((EnchantmentDamage) enchantment).func_151367_b((EntityLivingBase) entity, player, entry.getValue());
        }
        return modDamage;
    }

    private static Enchantment safeEnchantment(int effectId) {
        Enchantment[] list = Enchantment.enchantmentsList;
        if (list == null || effectId < 0 || effectId >= list.length) return null;
        return list[effectId];
    }
}
