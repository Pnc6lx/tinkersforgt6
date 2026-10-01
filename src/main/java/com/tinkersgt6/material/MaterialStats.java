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
 * Inputs: {@code Q = mToolQuality}, {@code D = mToolDurability}, {@code S = mToolSpeed}.
 * </p>
 *
 * <p>
 * Every knob mentioned below lives in the {@code stats} category of the config file, where each key repeats this text,
 * and every one of them can be overridden per material under {@code materials.<name>}. The per-material keys are
 * multipliers applied on top of the global rule, except {@code reinforced} / {@code stonebound}, which are absolute
 * values. Nothing here is a design-level guarantee: raise a multiplier and the result follows.
 * </p>
 *
 * <pre>
 * harvestLevel   = clamp(round(Q * harvestLevelMul + harvestLevelAdd + iguanaAdd), 0, harvestLevelCap)
 * durability     = clamp(round(D * durabilityMul  | extreme rule), 1, durabilityCap)
 * miningspeed    = clamp(round((S * miningSpeedMul + Q * miningSpeedPerLevel) * 100 | extreme rule), 1, miningSpeedCap)
 * attack         = clamp(round(Q * attackMul + S * attackSpeed), 0, attackCap)
 * handleModifier = clamp(base + (max(Q, 1) - 1) * handleModifierMul + handleModifierAdd, 0, handleModifierCap)
 * reinforced     = Q at least 8 ? 3 : Q at least 6 ? 2 : Q at least 4 ? 1 : 0
 * stonebound     = 0
 * primaryColor   = mRGBaSolid
 * </pre>
 *
 * <p>
 * "extreme rule" is the piecewise branch for GT6's endgame materials (Infinity hands us 1e9 for both durability and
 * speed): everything at or above {@code *ExtremeThreshold} either takes a fixed {@code *ExtremeValue} (mode CLAMP) or
 * is
 * squashed through {@code log(1 + x) / log(base)} (mode LOG) instead of being scaled linearly.
 * </p>
 *
 * <p>
 * Downstream, TConstruct feeds these into {@code ToolBuilder.buildTool}: part durability is summed over the "head"
 * parts,
 * averaged, then multiplied by a head count bonus, the handle modifier and the tool's own durability modifier (a hammer
 * with three head slots reaches roughly nine times a single part value). Attack is averaged over the same head parts
 * and
 * the tool's base damage is added. Mining speed is averaged over the head parts and multiplied by the tool's
 * {@code breakSpeedModifier}. Those totals are what the player sees on the tool.
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

    /**
     * Harvest level reported to TConstruct.
     *
     * <p>
     * {@code result = clamp(round(Q * harvestLevelMul + harvestLevelAdd + iguanaAdd), 0, harvestLevelCap)} with
     * {@code Q = mToolQuality}.
     * </p>
     *
     * <p>
     * Nothing in TConstruct multiplies or accumulates this value - Forge simply compares it against a block's harvest
     * level - so there is no overflow risk and the cap is off by default. Lower it to gate progression.
     * </p>
     *
     * <p>
     * {@code iguanaAdd} is the IguanaTweaksTconstruct knob. Two things stack: Iguana rebuilds every registered material
     * with {@code harvestLevel + 2} afterwards, and it takes another level off pickaxes and hammers on creation
     * ({@code pickaxeBoostRequired}). One extra level here lines GT6's quality scale up with Iguana's ten levels, which
     * is what decides what a maxed-out modpack material can actually mine - see
     * {@code harvestLevelAddIguanaTweaks}.
     * </p>
     */
    public static int harvestLevel(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.HARVEST_LEVEL_MUL,
            Config.MATERIAL_HARVEST_LEVEL,
            1.0,
            "Multiplies GT6 mToolQuality, which is what defines the harvest level.");
        double add = TGConfig.stat(Config.HARVEST_LEVEL_ADD, 0.0, "Added after the multiplier.");
        double iguanaAdd = 0.0;
        if (isIguanaLoaded()) {
            iguanaAdd = TGConfig.stat(
                Config.HARVEST_LEVEL_ADD_IGUANA,
                1.0,
                "Levels added on top when IguanaTweaksTConstruct is installed. Iguana spreads ores across ten levels -"
                    + " cobalt, titanium and iridium sit at 7, manyullyn at 8 - and afterwards raises each registered"
                    + " material by 2, so the default 1.0 puts GT6 quality Q at Iguana level Q + 3: quality 4 mines"
                    + " cobalt, quality 5 mines manyullyn. 0.0 keeps only Iguana's own +2, negative values are allowed.");
        }
        int cap = TGConfig.statInt(
            Config.HARVEST_LEVEL_CAP,
            Integer.MAX_VALUE,
            0,
            Integer.MAX_VALUE,
            "Upper bound for the harvest level. Defaults to unlimited.");
        return MathUtil.clampInt(m.mToolQuality * mul + add + iguanaAdd, 0, cap);
    }

    /**
     * Mining speed reported to TConstruct, stored already multiplied by 100.
     *
     * <p>
     * {@code result = clamp(round((S * miningSpeedMul + Q * miningSpeedPerLevel) * 100), 1, miningSpeedCap)} for the
     * normal range, with {@code S = mToolSpeed}.
     * </p>
     *
     * <p>
     * Anything at or above {@code miningSpeedExtremeThreshold} - Infinity hands us 1e9 - follows
     * {@code miningSpeedExtremeMode} instead. CLAMP writes {@code miningSpeedExtremeValue} verbatim, LOG compresses
     * with
     * {@code mul * 100 * log(1 + S) / log(logBase)}. Either way the x100 scaling is skipped there: 1e9 times 100 does
     * not
     * fit in an int, which is exactly the overflow this branch exists to avoid.
     * </p>
     *
     * <p>
     * No int overflow can happen downstream either - TConstruct only divides this number - so the cap is a playability
     * choice, not a technical one. A tool's effective speed is {@code stored / (headParts * 100) * breakSpeedModifier},
     * meaning a hammer averages over three heads and then takes 40 percent, so even 1,000,000 stored is several
     * thousand
     * blocks per second. Raise it freely if the pack wants bigger numbers.
     * </p>
     */
    public static int miningSpeed(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.MINING_SPEED_MUL,
            Config.MATERIAL_MINING_SPEED,
            1.0,
            "Multiplies GT6 mToolSpeed; the result is stored multiplied by 100.");
        double perLevel = TGConfig.stat(
            Config.MINING_SPEED_PER_LEVEL,
            0.0,
            "Flat speed added per point of mToolQuality, before the x100 scaling.");
        double threshold = TGConfig.stat(
            Config.MINING_SPEED_EXTREME_THRESHOLD,
            1.0E6,
            "Speeds at or above this are treated as extreme (Infinity, Neutronium, ...) and follow the extreme mode"
                + " below.");
        String mode = TGConfig.statString(
            Config.MINING_SPEED_EXTREME_MODE,
            "CLAMP",
            "How a material at or above the extreme threshold is handled: CLAMP writes miningSpeedExtremeValue, LOG"
                + " turns it into 100 * mul * log(1 + speed) / log(miningSpeedLogBase).");
        int extremeValue = TGConfig.statInt(
            Config.MINING_SPEED_EXTREME_VALUE,
            1_000_000,
            1,
            Integer.MAX_VALUE,
            "Stored value written when a speed is extreme and the mode is CLAMP. Stored means 100x the speed a"
                + " single-slot head would actually use.");
        double logBase = TGConfig.stat(
            Config.MINING_SPEED_LOG_BASE,
            10.0,
            "Log base used by the LOG mode; a smaller base compresses less aggressively.");
        int cap = TGConfig.statInt(
            Config.MINING_SPEED_CAP,
            1_000_000,
            1,
            Integer.MAX_VALUE,
            "Upper bound for the stored value, i.e. 10,000 by default once the implicit factor 100 is divided out."
                + " Nothing technical enforces this, it just keeps GT6 endgame and everything else in the same league.");

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

    /**
     * Tool part durability reported to TConstruct.
     *
     * <p>
     * Normal range: {@code result = clamp(round(D * durabilityMul), 1, durabilityCap)} with
     * {@code D = mToolDurability}.
     * At or above {@code durabilityExtremeThreshold} the extreme mode decides instead: CLAMP writes
     * {@code durabilityExtremeValue} verbatim, LOG writes
     * {@code mul * durabilityLogScale * log(1 + D) / log(durabilityLogBase)}.
     * </p>
     *
     * <p>
     * This is the one value that keeps a cap, and it is not a design choice. {@code ToolBuilder.buildTool} sums the
     * part
     * durability over every "head" slot, averages it, then multiplies it by {@code 0.5 + heads * 0.5}, by the handle
     * modifier and by the tool's own durability modifier - a hammer has three head slots and a 4.5 multiplier, so it
     * costs about {@code partDurability * 2.0 * handleModifier * 4.5}. That tool total then flows into
     * {@code ToolCore.getDamage}, which computes {@code damage * 100 / total} in plain int arithmetic, so any tool
     * whose
     * total exceeds {@code Integer.MAX_VALUE / 100 = 21474836} starts reporting negative damage once it is worn past
     * that
     * point - the Infinity hammer that showed negative durability. Three heads, the default handle modifier ceiling of
     * 8
     * and the hammer multiplier together spend 72 points of tool durability per part point, so a part value around
     * 298,000 is as high as the default settings can safely go. Both knobs are exposed: raise either one and lower the
     * cap accordingly, and conversely a modpack that wants truly enormous numbers has the LOG mode to stay linear-ish.
     * </p>
     *
     * <p>
     * Two multipliers sit downstream and both apply to the value computed here: IguanaTweaksTconstruct rebuilds every
     * registered material scaled by its {@code durabilityPercentage} (80 by default, so it takes a fifth off), and
     * TConstruct's own durability modifiers multiply the finished tool once more. Leave room for them.
     * </p>
     */
    public static int durability(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.DURABILITY_MUL,
            Config.MATERIAL_DURABILITY,
            1.0,
            "Multiplies GT6 mToolDurability. This is the value a single tool part contributes, not what the finished"
                + " tool ends up with.");
        double threshold = TGConfig.stat(
            Config.DURABILITY_EXTREME_THRESHOLD,
            1.0E6,
            "Durabilities at or above this are treated as extreme (Infinity hands us 1e9) and follow the extreme mode"
                + " below.");
        String mode = TGConfig.statString(
            Config.DURABILITY_EXTREME_MODE,
            "CLAMP",
            "How a material at or above the extreme threshold is handled: CLAMP writes durabilityExtremeValue, LOG"
                + " turns it into mul * durabilityLogScale * log(1 + durability) / log(durabilityLogBase).");
        int extremeValue = TGConfig.statInt(
            Config.DURABILITY_EXTREME_VALUE,
            140_000,
            1,
            Integer.MAX_VALUE,
            "Part durability written when a durability is extreme and the mode is CLAMP. 1e9 here is what made an all"
                + " Infinity hammer overflow - see the durabilityCap note for the budget. At the default handle"
                + " modifier an all-Infinity hammer lands near 10,000,000 total.");
        double logScale = TGConfig.stat(
            Config.DURABILITY_LOG_SCALE,
            1000.0,
            "Scale factor used by the LOG mode; together with durabilityLogBase it decides how flat the top end gets.");
        double logBase = TGConfig.stat(
            Config.DURABILITY_LOG_BASE,
            10.0,
            "Log base used by the LOG mode; a smaller base compresses less aggressively.");
        int cap = TGConfig.statInt(
            Config.DURABILITY_CAP,
            298_000,
            1,
            Integer.MAX_VALUE,
            "Upper bound for a single tool part. Not a design limit: keep it at or below"
                + " 21474836 / (heads x handleModifierCap x toolDurabilityModifier) or tools will report negative"
                + " damage when worn. Raise it only together with a lower handleModifierCap.");

        double raw;
        if (m.mToolDurability >= threshold) {
            raw = "LOG".equalsIgnoreCase(mode) ? mul * logScale * MathUtil.logCompress(m.mToolDurability, logBase)
                : extremeValue;
        } else {
            raw = m.mToolDurability * mul;
        }
        return MathUtil.clampInt(raw, 1, cap);
    }

    /**
     * Bonus attack damage contributed by a single head part.
     *
     * <p>
     * {@code result = clamp(round(Q * attackMul + S * attackSpeed), 0, attackCap)}.
     * </p>
     *
     * <p>
     * {@code ToolBuilder.buildTool} averages the head parts and then adds the tool's own base damage, so this number
     * can
     * never come near an int overflow and the cap is off by default - a modpack is free to set its own ceiling, nothing
     * technical is being defended here.
     * </p>
     */
    public static int attack(OreDictMaterial m) {
        double mul = multiplier(
            m,
            Config.ATTACK_MUL,
            Config.MATERIAL_ATTACK,
            1.0,
            "Attack damage per point of mToolQuality.");
        double speed = TGConfig.stat(
            Config.ATTACK_SPEED,
            0.0,
            "Attack damage per point of mToolSpeed. Keep this tiny or zero - GT6 scales speed up to 1e9, so even 0.001"
                + " hands over a million damage.");
        int cap = TGConfig.statInt(
            Config.ATTACK_CAP,
            Integer.MAX_VALUE,
            0,
            Integer.MAX_VALUE,
            "Upper bound for attack damage. Defaults to unlimited.");
        return MathUtil.clampInt(m.mToolQuality * mul + m.mToolSpeed * speed, 0, cap);
    }

    /**
     * Handle modifier, which ToolBuilder uses as a straight multiplier on the tool's durability.
     *
     * <p>
     * {@code result = clamp(handleModifierBase + (max(Q, 1) - 1) * handleModifierMul + handleModifierAdd, 0,
     * handleModifierCap)}.
     * </p>
     *
     * <p>
     * The base term is what makes this behave like vanilla: wood sits at 1.0, which leaves the tool untouched, stone at
     * 0.5, iron at 1.3 - anything below 1.0 makes the tool weaker. Starting from {@code handleModifierBase} means
     * {@code Q = 1} is neutral and every further point of quality buys {@code handleModifierMul} on top.
     * </p>
     *
     * <p>
     * The ceiling is only there to keep the durability budget honest: each point here multiplies into every head part,
     * see the note on {@code durabilityCap}.
     * </p>
     */
    public static float handleModifier(OreDictMaterial m) {
        double base = TGConfig.stat(
            Config.HANDLE_MODIFIER_BASE,
            1.0,
            "Handle modifier for a material whose mToolQuality is 1. 1.0 is the neutral value: vanilla wood uses it,"
                + " stone is 0.5, iron 1.3. Values below 1.0 make the finished tool weaker.");
        double mul = multiplier(
            m,
            Config.HANDLE_MODIFIER_MUL,
            Config.MATERIAL_HANDLE_MODIFIER,
            0.5,
            "Multiplier gained per point of mToolQuality above 1.");
        double add = TGConfig.stat(Config.HANDLE_MODIFIER_ADD, 0.0, "Flat bonus added on top of base + quality steps.");
        float cap = (float) TGConfig.stat(
            Config.HANDLE_MODIFIER_CAP,
            8.0,
            "Upper bound for the handle multiplier. It multiplies into the tool durability budget described on"
                + " durabilityCap, so lowering one lets you raise the other.");
        double raw = base + (Math.max(m.mToolQuality, 1) - 1) * mul + add;
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
