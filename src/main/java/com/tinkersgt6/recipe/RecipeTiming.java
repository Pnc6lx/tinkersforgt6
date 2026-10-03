package com.tinkersgt6.recipe;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialStats;
import com.tinkersgt6.reference.Config;

import gregapi.oredict.OreDictMaterial;

/**
 * Turns "how expensive is this part" and "how durable is this material" into a recipe duration.
 *
 * <pre>
 * duration = clamp(round(costHalfIngots * durability * durationPerPoint), minDuration, maxDuration)
 * </pre>
 *
 * <p>
 * {@code costHalfIngots} is TConstruct's own {@code IPattern.getPatternCost()} in half-ingots - a tool rod is 1, a
 * pickaxe head 2, a hammer head 16 - so a part that eats eight ingots also takes eight times as long.
 * {@code durability}
 * is the very number {@link MaterialStats} hands to TConstruct, which already carries the extreme-value handling, so
 * Infinity enters this formula as 140,000 and not as the raw 1e9 GT6 stores.
 * </p>
 *
 * <p>
 * Two things are worth knowing about the numbers that come out of this. GT6's machines <em>overclock</em> a recipe
 * whose
 * EUt is below their own input window: every step quadruples the power and halves the duration, so a 16 EU/t recipe
 * finishes in half the time on an MV machine and in a quarter on an HV one. And the EUt has to stay <em>below</em> the
 * machine's {@code mInputMax * mPower} or the recipe is not found at all - which is why 16 is both the safest and the
 * fastest choice, and why a low EUt never makes anything slower.
 * </p>
 *
 * <p>
 * The ceiling exists because a linear rule explodes at the top end: eight ingots of Infinity is 16 * 140,000 * 0.05,
 * about 19 hours at face value. With {@code maxDuration} at 72,000 ticks a full set of Infinity parts is a one-hour
 * job at most, while an iron tool rod stays at the 16 tick floor.
 * </p>
 */
public final class RecipeTiming {

    /** Energy per tick. 16 is what GT6 itself uses for the overwhelming majority of its recipes. */
    public static long eut() {
        return TGConfig.recipeLong(
            Config.PART_RECIPE_EUT,
            16,
            "Energy per tick consumed by part recipes. Keep this at or below 16: a recipe whose EUt exceeds the"
                + " machine's input window is never found, and a lower EUt does not slow anything down because GT6"
                + " overclocks it instead (each step quadruples the power and halves the duration).");
    }

    public static long duration(OreDictMaterial material, int costHalfIngots) {
        double perPoint = TGConfig.recipeDouble(
            Config.PART_RECIPE_DURATION_PER_POINT,
            0.05,
            "Ticks per point of material durability, multiplied by the part's cost in half-ingots. GT6 tool"
                + " durabilities run from about 64 (copper, gold) through 256 (iron) to a few thousand (tungsten,"
                + " iridium) - with 0.05 an iron tool rod lands on the floor while a tungsten hammer head takes a few"
                + " minutes.");
        long min = TGConfig.recipeLong(
            Config.PART_RECIPE_MIN_DURATION,
            16,
            "Lower bound in ticks (20 ticks = 1 second). Never set this to 0: GT6 warns about it and then treats the"
                + " recipe as disabled once the config file is written.");
        long max = TGConfig.recipeLong(
            Config.PART_RECIPE_MAX_DURATION,
            72000,
            "Upper bound in ticks; 72000 is one hour. This is what keeps an eight-ingot Infinity part from becoming a"
                + " multi-day job.");

        long raw = Math.round(costHalfIngots * (double) MaterialStats.durability(material) * perPoint);
        return Math.max(min, Math.min(max, raw));
    }

    private RecipeTiming() {}
}
