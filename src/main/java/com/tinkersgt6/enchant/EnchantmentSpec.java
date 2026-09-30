package com.tinkersgt6.enchant;

import java.util.function.Supplier;

import net.minecraft.enchantment.Enchantment;

import com.tinkersgt6.config.TGConfig;

/**
 * One row of the enchantment decision table. Every row can be flipped to {@link Mode#DISABLED} from the config.
 */
public final class EnchantmentSpec {

    /** Mirrors the six {@code OreDictMaterial.mEnchantment*} lists. */
    public enum Slot {
        TOOLS,
        WEAPONS,
        AMMO,
        RANGED,
        FISHING,
        ARMORS
    }

    public enum Mode {

        /** Exposed as a material trait: kept on the tool and re-applied whenever a modifier overwrites it. */
        TRAIT,
        /** Never written. */
        DISABLED
    }

    public final String key;
    public final Slot slot;
    public final Supplier<Enchantment> supplier;
    public final Mode defaultMode;

    /** 0 = unlimited. */
    public final int maxLevel;

    public EnchantmentSpec(String key, Slot slot, Supplier<Enchantment> supplier, Mode defaultMode, int maxLevel) {
        this.key = key;
        this.slot = slot;
        this.supplier = supplier;
        this.defaultMode = defaultMode;
        this.maxLevel = maxLevel;
    }

    /** @return the enchantment, or {@code null} when the owning mod is not installed. */
    public Enchantment enchantment() {
        if (supplier == null) return null;
        try {
            return supplier.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Modes are resolved per launch so a config reload is honoured without rebuilding the table. */
    public Mode mode() {
        String configured = TGConfig.enchantString(
            "mode." + key,
            defaultMode.name(),
            "TRAIT = write and maintain this enchantment, DISABLED = never write it.");
        return "DISABLED".equalsIgnoreCase(configured) ? Mode.DISABLED : Mode.TRAIT;
    }

    public int effectiveMaxLevel(int level) {
        if (maxLevel > 0 && level > maxLevel) return maxLevel;
        return level;
    }
}
