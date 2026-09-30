package com.tinkersgt6.enchant;

import java.util.ArrayList;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.util.EnchantNBT;

/**
 * Keeps the material enchantments on a tool in sync with whatever else may be writing to {@code ench}.
 *
 * <p>
 * TConstruct's Lapis modifier rewrites the level wholesale rather than adding to it
 * ({@code ModLapis.addEnchantment} sets {@code lvl = N} for the given ID), so a material-provided level gets wiped the
 * moment the player adds Lapis. This reconciler detects the wipe and re-applies our share on top of whatever the
 * outside world left behind.
 * </p>
 *
 * <p>
 * The material baseline itself is not persisted: it is derived from the four part IDs every tick by
 * {@link EnchantResolver} (cached per part combination), so it has to be passed back in as the {@code base} argument.
 * Only {@code TGregContrib} is stored.
 * </p>
 *
 * <p>
 * Per enchantment:
 * </p>
 *
 * <pre>
 * cur  = level currently on the tool (0 when absent)
 * last = the share we applied last tick
 * ext  = cur - last
 * if (ext &lt; 0) { ext = cur; last = 0; }   // someone overwrote us: treat what is left as the external level
 * desired = ADDITIVE ? ext + base : max(ext, base)
 * </pre>
 *
 * <p>
 * Example with a material giving Fortune 4 under ADDITIVE: 0 -> 4; player adds Lapis 1 (tool shows 1) -> we restore 5;
 * Lapis 2 (tool shows 2) -> we restore 6. Under FLOOR it stays at 4 in every case.
 * </p>
 */
public final class EnchantReconciler {

    public static final String KEY_CONTRIB = "TGregContrib";
    public static final String KEY_TIP_DONE = "TGregTipDone";

    private static final String ADDITIVE = "ADDITIVE";

    /** @return true when anything was written. */
    public static boolean reconcile(ItemStack stack, NBTTagCompound tags, Map<Integer, Integer> base) {
        boolean additive = ADDITIVE.equalsIgnoreCase(
            TGConfig.enchantString(
                "combineMode",
                ADDITIVE,
                "ADDITIVE = material level is added on top of modifiers, FLOOR = material level is a minimum."));

        Map<Integer, Integer> contrib = EnchantNBT.readPairs(tags, KEY_CONTRIB);

        // Retire enchantments the tool no longer gets from its materials.
        boolean contribChanged = false;
        for (Integer effectId : new ArrayList<>(contrib.keySet())) {
            if (base.containsKey(effectId)) continue;
            int applied = contrib.remove(effectId);
            contribChanged = true;

            int cur = EnchantmentHelper.getEnchantmentLevel(effectId, stack);
            if (cur > 0 && applied > 0) EnchantNBT.setLevel(stack, effectId, Math.max(0, cur - applied));
        }

        for (Map.Entry<Integer, Integer> entry : base.entrySet()) {
            int effectId = entry.getKey();
            int baseLevel = entry.getValue();

            int cur = EnchantmentHelper.getEnchantmentLevel(effectId, stack);
            int last = contrib.containsKey(effectId) ? contrib.get(effectId) : 0;

            int ext = cur - last;
            if (ext < 0) {
                // Our contribution was overwritten (or the tag was lost): everything left is external.
                ext = cur;
                last = 0;
            }

            int desired = additive ? ext + baseLevel : Math.max(ext, baseLevel);
            if (desired != cur) EnchantNBT.setLevel(stack, effectId, desired);

            int applied = desired - ext;
            if (applied != last) {
                contrib.put(effectId, applied);
                contribChanged = true;
            }
        }

        if (contribChanged) EnchantNBT.writePairs(tags, KEY_CONTRIB, contrib);
        return contribChanged;
    }

    /** Human readable tooltip listing the enchantments this tool gets from its materials. */
    public static String modifierTip(Map<Integer, Integer> base) {
        StringBuilder builder = new StringBuilder("\u00A7a");
        builder.append("GregTech Material");
        if (base.isEmpty()) return builder.toString();

        builder.append(":");
        for (Map.Entry<Integer, Integer> entry : base.entrySet()) {
            Enchantment enchantment = safeEnchantment(entry.getKey());
            if (enchantment == null) continue;
            builder.append(" \u00A77")
                .append(enchantment.getTranslatedName(entry.getValue()))
                .append("\u00A7a,");
        }
        int length = builder.length();
        if (builder.charAt(length - 1) == ',') builder.setLength(length - 1);
        return builder.toString();
    }

    private static Enchantment safeEnchantment(int effectId) {
        Enchantment[] list = Enchantment.enchantmentsList;
        if (list == null || effectId < 0 || effectId >= list.length) return null;
        return list[effectId];
    }

    private EnchantReconciler() {}
}
