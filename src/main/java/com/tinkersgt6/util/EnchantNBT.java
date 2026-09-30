package com.tinkersgt6.util;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Reads and writes a tool's {@code ench} tag.
 *
 * <p>
 * The write path mirrors what TConstruct's own {@code ModLapis} does: read everything into a map, adjust, then rebuild
 * the whole {@code NBTTagList}. Going through a map is what guarantees a single entry per enchantment ID, which is
 * exactly the "one level per enchantment, never stacked" rule we want.
 * </p>
 */
public final class EnchantNBT {

    public static Map<Integer, Integer> read(ItemStack stack) {
        return new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
    }

    public static void setLevel(ItemStack stack, int effectId, int level) {
        Map<Integer, Integer> enchants = read(stack);
        if (level <= 0) {
            if (!enchants.containsKey(effectId)) return;
            enchants.remove(effectId);
        } else {
            Integer current = enchants.get(effectId);
            if (current != null && current == level) return;
            enchants.put(effectId, level);
        }
        write(stack, enchants);
    }

    public static void write(ItemStack stack, Map<Integer, Integer> enchants) {
        if (stack.stackTagCompound == null) stack.setTagCompound(new NBTTagCompound());

        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, Integer> entry : enchants.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort(
                "id",
                (short) entry.getKey()
                    .intValue());
            tag.setShort(
                "lvl",
                (short) entry.getValue()
                    .intValue());
            list.appendTag(tag);
        }
        stack.stackTagCompound.setTag("ench", list);
    }

    /** int[] laid out as [id, value, id, value, ...]. */
    public static Map<Integer, Integer> readPairs(NBTTagCompound tags, String key) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (tags == null || !tags.hasKey(key)) return result;
        int[] pairs = tags.getIntArray(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return result;
    }

    public static void writePairs(NBTTagCompound tags, String key, Map<Integer, Integer> values) {
        int[] pairs = new int[values.size() * 2];
        int i = 0;
        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            pairs[i++] = entry.getKey();
            pairs[i++] = entry.getValue();
        }
        tags.setIntArray(key, pairs);
    }

    private EnchantNBT() {}
}
