package com.tinkersgt6.enchant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;

import com.tinkersgt6.enchant.EnchantmentSpec.Mode;
import com.tinkersgt6.enchant.EnchantmentSpec.Slot;

import gregapi.code.ObjectStack;
import gregapi.enchants.Enchantment_EnderDamage;
import gregapi.enchants.Enchantment_Radioactivity;
import gregapi.enchants.Enchantment_SlimeDamage;
import gregapi.enchants.Enchantment_WerewolfDamage;
import gregapi.oredict.OreDictMaterial;

/**
 * The enchantment decision table.
 *
 * <p>
 * Rows are keyed so the config can disable them one by one. Anything GT6 attaches to a material that is <em>not</em>
 * listed here is still picked up: {@link EnchantResolver} accepts an enchantment with no matching spec, and
 * {@link #slotFor(String[])} only ever returns the four slots TConstruct can actually make use of
 * (Tools / Weapons / Ammo / Ranged). Fishing and Armors are therefore skipped automatically - TConstruct has no fishing
 * rod and no armour part system.
 * </p>
 */
public final class EnchantmentTable {

    private static final List<EnchantmentSpec> SPECS = new ArrayList<>();

    private static final Map<Integer, EnchantmentSpec> BY_EFFECT_ID = new HashMap<>();
    private static boolean byEffectIdBuilt;

    static {
        // --- Tools ---
        add("tools.fortune", Slot.TOOLS, () -> Enchantment.fortune);
        add("tools.silkTouch", Slot.TOOLS, () -> Enchantment.silkTouch);
        add("tools.fireAspect", Slot.TOOLS, () -> Enchantment.fireAspect);

        // --- Weapons ---
        add("weapons.looting", Slot.WEAPONS, () -> Enchantment.looting);

        // --- Damage (GT6 writes these into both Weapons and Ammo) ---
        add("damage.sharpness", Slot.WEAPONS, () -> Enchantment.sharpness);
        add("damage.smite", Slot.WEAPONS, () -> Enchantment.smite);
        add("damage.baneOfArthropods", Slot.WEAPONS, () -> Enchantment.baneOfArthropods);
        add("damage.knockback", Slot.WEAPONS, () -> Enchantment.knockback);
        add("damage.fireAspect", Slot.WEAPONS, () -> Enchantment.fireAspect);

        // --- Ranged ---
        add("ranged.power", Slot.RANGED, () -> Enchantment.power);
        add("ranged.punch", Slot.RANGED, () -> Enchantment.punch);
        add("ranged.flame", Slot.RANGED, () -> Enchantment.flame);
        add("ranged.infinity", Slot.RANGED, () -> Enchantment.infinity);

        // --- Ammo: applies to the thrown item / arrow material itself, not to the held weapon ---
        add("ammo.looting", Slot.AMMO, () -> Enchantment.looting);

        // --- GT6 custom, all extend EnchantmentDamage ---
        add("gt.radioactivity", Slot.WEAPONS, () -> Enchantment_Radioactivity.INSTANCE);
        add("gt.enderdamage", Slot.WEAPONS, () -> Enchantment_EnderDamage.INSTANCE);
        add("gt.slimedamage", Slot.WEAPONS, () -> Enchantment_SlimeDamage.INSTANCE);
        add("gt.werewolfdamage", Slot.WEAPONS, () -> Enchantment_WerewolfDamage.INSTANCE);

        // --- Third party, resolved by name at runtime ---
        add("ext.magnetization", Slot.TOOLS, () -> byName("enchantment.Magnetization"));
        add("ext.coldtouch", Slot.WEAPONS, () -> byName("enchantment.Cold Touch"));
        add("ext.railcraft.implosion", Slot.WEAPONS, () -> byName("enchantment.railcraft.crowbar.implosion"));
    }

    private static void add(String key, Slot slot, java.util.function.Supplier<Enchantment> supplier) {
        SPECS.add(new EnchantmentSpec(key, slot, supplier, Mode.TRAIT, 0));
    }

    public static List<EnchantmentSpec> all() {
        return Collections.unmodifiableList(SPECS);
    }

    /**
     * Ammo wins over Weapon: arrows ({@code {"ammo","projectile","weapon"}}) and javelins
     * ({@code {"weapon","thrown","ammo","windup"}}) carry both, and GT6's Ammo list is exactly the enchantments meant
     * for the thrown item.
     */
    public static Slot slotFor(String[] traits) {
        if (traits == null) return null;
        List<String> t = Arrays.asList(traits);
        if (t.contains("ammo") || t.contains("projectile") || t.contains("thrown")) return Slot.AMMO;
        if (t.contains("bow")) return Slot.RANGED;
        if (t.contains("harvest")) return Slot.TOOLS;
        if (t.contains("weapon")) return Slot.WEAPONS;
        return null;
    }

    public static List<ObjectStack<Enchantment>> listFor(OreDictMaterial material, Slot slot) {
        if (material == null || slot == null) return Collections.emptyList();
        switch (slot) {
            case TOOLS:
                return material.mEnchantmentTools;
            case WEAPONS:
                return material.mEnchantmentWeapons;
            case AMMO:
                return material.mEnchantmentAmmo;
            case RANGED:
                return material.mEnchantmentRanged;
            case FISHING:
                return material.mEnchantmentFishing;
            case ARMORS:
                return material.mEnchantmentArmors;
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Look up the spec for an effect ID. Built lazily, because third-party enchantments are only registered during
     * gregapi_post (which runs after TConstruct, hence after our own preInit).
     */
    public static EnchantmentSpec specFor(int effectId) {
        if (!byEffectIdBuilt) {
            synchronized (BY_EFFECT_ID) {
                if (!byEffectIdBuilt) {
                    for (EnchantmentSpec spec : SPECS) {
                        Enchantment enchantment = spec.enchantment();
                        if (enchantment != null) BY_EFFECT_ID.put(enchantment.effectId, spec);
                    }
                    byEffectIdBuilt = true;
                }
            }
        }
        return BY_EFFECT_ID.get(effectId);
    }

    /** Effect IDs of the GT6 custom enchantments, whose extra effect has to be applied manually in melee. */
    public static boolean isGregTechDamage(Enchantment enchantment) {
        return enchantment instanceof Enchantment_Radioactivity || enchantment instanceof Enchantment_EnderDamage
            || enchantment instanceof Enchantment_SlimeDamage
            || enchantment instanceof Enchantment_WerewolfDamage;
    }

    /** Finds a third-party enchantment by its registered name; returns null when the mod is absent. */
    public static Enchantment byName(String name) {
        Enchantment[] list = Enchantment.enchantmentsList;
        if (list == null) return null;
        for (Enchantment enchantment : list) {
            if (enchantment != null && name.equals(enchantment.getName())) return enchantment;
        }
        return null;
    }

    private EnchantmentTable() {}
}
