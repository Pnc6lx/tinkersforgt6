package com.tinkersgt6.enchant;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.enchant.EnchantmentSpec.Slot;
import com.tinkersgt6.material.MaterialRegistry;

import gregapi.code.ObjectStack;
import gregapi.oredict.OreDictMaterial;
import tconstruct.library.tools.ToolCore;

/**
 * Turns a tool's four part materials into the map of enchantment levels the GT6 materials contribute.
 *
 * <p>
 * {@code updateTool} runs once per tick for every tool in the player's inventory, so the result is cached per part
 * combination - this method must never walk GT6's material lists.
 * </p>
 */
public final class EnchantResolver {

    private static final Map<String, Map<Integer, Integer>> CACHE = new HashMap<>();
    private static final Map<Integer, Integer> EMPTY = Collections.emptyMap();

    /** @return effect ID -> level contributed by the materials, or an empty map when there is nothing to do. */
    public static Map<Integer, Integer> baseFor(ToolCore tool, ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return EMPTY;
        if (!stack.stackTagCompound.hasKey("InfiTool")) return EMPTY;

        Slot slot = EnchantmentTable.slotFor(tool.getTraits());
        if (slot == null) return EMPTY;

        NBTTagCompound tags = stack.stackTagCompound.getCompoundTag("InfiTool");
        int head = tags.getInteger("Head");
        int handle = tags.getInteger("Handle");
        int accessory = tags.getInteger("Accessory");
        int extra = tags.getInteger("Extra");

        String key = slot.name() + "|" + head + "|" + handle + "|" + accessory + "|" + extra;
        Map<Integer, Integer> cached = CACHE.get(key);
        if (cached != null) return cached;

        Map<Integer, Integer> result = compute(slot, head, handle, accessory, extra);
        Map<Integer, Integer> stored = result.isEmpty() ? EMPTY : result;
        CACHE.put(key, stored);
        return stored;
    }

    private static Map<Integer, Integer> compute(Slot slot, int head, int handle, int accessory, int extra) {
        int cap = TGConfig.enchantInt("levelCap", 0, 0, 255, "Global enchantment level cap, 0 = unlimited.");

        Map<Integer, Integer> result = new HashMap<>();
        collect(result, head, slot, cap);
        collect(result, handle, slot, cap);
        collect(result, accessory, slot, cap);
        collect(result, extra, slot, cap);
        return result;
    }

    private static void collect(Map<Integer, Integer> target, int materialID, Slot slot, int cap) {
        if (materialID < 0) return;
        OreDictMaterial material = MaterialRegistry.fromID(materialID);
        if (material == null) return;

        List<ObjectStack<Enchantment>> entries = EnchantmentTable.listFor(material, slot);
        if (entries == null) return;

        for (ObjectStack<Enchantment> entry : entries) {
            if (entry == null || entry.mObject == null) continue;

            Enchantment enchantment = entry.mObject;
            int level = (int) entry.mAmount;
            if (level <= 0) continue;

            EnchantmentSpec spec = EnchantmentTable.specFor(enchantment.effectId);
            if (spec != null) {
                if (spec.mode() != EnchantmentSpec.Mode.TRAIT) continue;
                level = spec.effectiveMaxLevel(level);
            }

            if (cap > 0 && level > cap) level = cap;

            // Same enchantment from several parts: keep the highest, never stack.
            Integer current = target.get(enchantment.effectId);
            if (current == null || level > current) target.put(enchantment.effectId, level);
        }
    }

    /** Drops cached entries; only useful when the config is changed at runtime. */
    public static void invalidate() {
        CACHE.clear();
    }

    private EnchantResolver() {}
}
