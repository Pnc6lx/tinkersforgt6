package com.tinkersgt6.material;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.reference.Config;
import com.tinkersgt6.reference.Mods;
import com.tinkersgt6.util.MathUtil;

import cpw.mods.fml.common.Loader;
import gregapi.oredict.OreDictMaterial;

/**
 * Maps GT6 material stats onto TConstruct material stats.
 *
 * <p>
 * Inputs: {@code Q = mToolQuality} (0..15), {@code D = mToolDurability} (1 .. 1e9),
 * {@code S = mToolSpeed} (1 .. 1e9).
 * </p>
 *
 * <p>
 * Every output is clamped to {@code int}: TConstruct stores durability and mining speed in plain ints (and the
 * {@code MiningSpeed} NBT tag is an int too), while GT6 happily hands us 1e9 for Infinity. Mining speed is stored
 * multiplied by 100, so values above the extreme threshold must <em>not</em> be scaled again or they overflow.
 * </p>
 */
public final class MaterialStats {

    private static Boolean iguanaLoaded;

    /** IguanaTweaksTConstruct exposes 8 effective harvest levels while GT6 qualities rarely exceed 4. */
    public static boolean isIguanaLoaded() {
        if (iguanaLoaded == null) iguanaLoaded = Loader.isModLoaded(Mods.IGUANA_TWEAKS_TCONSTRUCT);
        return iguanaLoaded;
    }

    /**
     * Per-material multiplier wins over the global one; {@code NO_DOUBLE_OVERRIDE} means "use global". Both live under
     * their own category, so {@code stats.durabilityMul} and {@code materials.Fe.durability} are distinct keys.
     */
    private static double multiplier(OreDictMaterial m, String statsKey, String materialKey, double globalDefault,
        String comment) {
        double global = TGConfig.stat(statsKey, globalDefault, comment);
        double perMaterial = TGConfig.getMaterialMultiplier(m.mNameInternal, materialKey);
        return perMaterial == Config.NO_DOUBLE_OVERRIDE ? global : perMaterial;
    }

    /* ------------------------------------------------------------------ */

    public static int harvestLevel(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.HARVEST_LEVEL_MUL,
            Config.MATERIAL_HARVEST_LEVEL,
            1.0,
            "Multiplies GT6 mToolQuality.");
        double add = TGConfig.stat(Config.HARVEST_LEVEL_ADD, 0.0, "Added after the multiplier.");
        double extMul = 1.0;
        if (isIguanaLoaded()) {
            extMul = TGConfig.stat(
                Config.HARVEST_LEVEL_MUL_IGUANA,
                2.0,
                "Extra multiplier applied when IguanaTweaksTConstruct is installed (set to 1.0 to disable).");
        }
        int cap = TGConfig.statInt(Config.HARVEST_LEVEL_CAP, 15, 0, 1000, "Upper bound for the harvest level.");
        return MathUtil.clampInt(m.mToolQuality * mul * extMul + add, 0, cap);
    }

    public static int miningSpeed(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.MINING_SPEED_MUL,
            Config.MATERIAL_MINING_SPEED,
            1.0,
            "Multiplies GT6 mToolSpeed.");
        double perLevel = TGConfig.stat(
            Config.MINING_SPEED_PER_LEVEL,
            0.0,
            "Flat speed added per point of mToolQuality, before the x100 scaling.");
        double threshold = TGConfig.stat(
            Config.MINING_SPEED_EXTREME_THRESHOLD,
            1.0E6,
            "Speeds at or above this are treated as extreme (Infinity, Neutronium, ...).");
        String mode = TGConfig.statString(Config.MINING_SPEED_EXTREME_MODE, "CLAMP", "CLAMP or LOG.");
        int extremeValue = TGConfig.statInt(
            Config.MINING_SPEED_EXTREME_VALUE,
            1_000_000_000,
            1,
            Integer.MAX_VALUE,
            "Value written when a speed is extreme and the mode is CLAMP.");
        double logBase = TGConfig.stat(Config.MINING_SPEED_LOG_BASE, 10.0, "Log base used by the LOG mode.");
        int cap = TGConfig.statInt(Config.MINING_SPEED_CAP, 1_000_000_000, 1, Integer.MAX_VALUE, "Hard upper bound.");

        double raw;
        if (m.mToolSpeed >= threshold) {
            // Never multiply by 100 here: 1e9 * 100 overflows an int.
            raw = "LOG".equalsIgnoreCase(mode) ? 100.0 * mul * MathUtil.logCompress(m.mToolSpeed, logBase)
                : extremeValue;
        } else {
            raw = (m.mToolSpeed * mul + m.mToolQuality * perLevel) * 100.0;
        }
        return MathUtil.clampInt(raw, 1, cap);
    }

    public static int durability(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.DURABILITY_MUL,
            Config.MATERIAL_DURABILITY,
            1.0,
            "Multiplies GT6 mToolDurability.");
        double threshold = TGConfig
            .stat(Config.DURABILITY_EXTREME_THRESHOLD, 1.0E6, "Durabilities at or above this are treated as extreme.");
        String mode = TGConfig.statString(Config.DURABILITY_EXTREME_MODE, "CLAMP", "CLAMP or LOG.");
        int extremeValue = TGConfig.statInt(
            Config.DURABILITY_EXTREME_VALUE,
            1_000_000_000,
            1,
            Integer.MAX_VALUE,
            "Value written when a durability is extreme and the mode is CLAMP.");
        double logScale = TGConfig.stat(Config.DURABILITY_LOG_SCALE, 1000.0, "Scale factor used by the LOG mode.");
        double logBase = TGConfig.stat(Config.DURABILITY_LOG_BASE, 10.0, "Log base used by the LOG mode.");
        int cap = TGConfig.statInt(
            Config.DURABILITY_CAP,
            1_000_000_000,
            1,
            Integer.MAX_VALUE,
            "Hard upper bound. ToolBuilder multiplies durability by the handle modifier, so keep this below"
                + " Integer.MAX_VALUE / handleModifierCap.");

        double raw;
        if (m.mToolDurability >= threshold) {
            raw = "LOG".equalsIgnoreCase(mode) ? mul * logScale * MathUtil.logCompress(m.mToolDurability, logBase)
                : extremeValue;
        } else {
            raw = m.mToolDurability * mul;
        }
        return MathUtil.clampInt(raw, 1, cap);
    }

    public static int attack(OreDictMaterial m) {
        double mul = multiplier(m, Config.ATTACK_MUL, Config.MATERIAL_ATTACK, 1.0, "Damage per point of mToolQuality.");
        double speed = TGConfig.stat(Config.ATTACK_SPEED, 0.0, "Damage per point of mToolSpeed.");
        int cap = TGConfig.statInt(Config.ATTACK_CAP, 100, 0, 1000, "Hard upper bound for attack damage.");
        return MathUtil.clampInt(m.mToolQuality * mul + m.mToolSpeed * speed, 0, cap);
    }

    public static float handleModifier(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.HANDLE_MODIFIER_MUL,
            Config.MATERIAL_HANDLE_MODIFIER,
            0.25,
            "Handle bonus per point of mToolQuality above 1.");
        double add = TGConfig.stat(Config.HANDLE_MODIFIER_ADD, 0.0, "Flat handle bonus.");
        float cap = (float) TGConfig.stat(
            Config.HANDLE_MODIFIER_CAP,
            2.0,
            "Upper bound. Keep low enough that durability * cap stays inside an int.");
        double raw = (Math.max(m.mToolQuality, 1) - 1) * mul + add;
        return MathUtil.clampFloat(raw, 0.0f, cap);
    }

    public static int reinforced(OreDictMaterial m) {
        int override = TGConfig.getMaterialIntOverride(m.mNameInternal, Config.MATERIAL_REINFORCED);
        if (override != Config.NO_INT_OVERRIDE) return MathUtil.clampInt(override, 0, 3);
        int q = m.mToolQuality;
        return q >= 8 ? 3 : q >= 6 ? 2 : q >= 4 ? 1 : 0;
    }

    public static float stonebound(OreDictMaterial m) {
        double override = TGConfig.getMaterialDoubleOverride(m.mNameInternal, Config.MATERIAL_STONEBOUND);
        if (override != Config.NO_DOUBLE_OVERRIDE) return MathUtil.clampFloat(override, -3.0f, 3.0f);
        return 0.0f;
    }

    public static int color(OreDictMaterial m) {
        short[] rgb = m.mRGBaSolid;
        if (rgb == null || rgb.length < 3) return 0xFFFFFF;
        return MathUtil.color(rgb[0], rgb[1], rgb[2]);
    }

    private MaterialStats() {}
}
