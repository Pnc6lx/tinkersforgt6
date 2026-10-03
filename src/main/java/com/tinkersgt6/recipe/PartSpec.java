package com.tinkersgt6.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.tinkersgt6.config.TGConfig;

import tconstruct.smeltery.TinkerSmeltery;
import tconstruct.tools.TinkerTools;
import tconstruct.weaponry.TinkerWeaponry;

/**
 * The TConstruct parts we can manufacture, and where each one lives.
 *
 * <p>
 * {@code patternMeta} is the damage value of the {@b metal cast} ({@code TinkerSmeltery.metalPattern}) that shapes the
 * part - the same numbering TConstruct uses for its casting table, so the cast a player makes in the Stencil Table is
 * exactly the one these recipes ask for. {@code costHalfIngots} is TConstruct's {@code IPattern.getPatternCost()} in
 * half-ingots, which is both the material a part costs and the weight its crafting time gets.
 * </p>
 *
 * <p>
 * Meta 23 (bowstring) and 24 (fletching) are deliberately absent: they are plain {@code CraftingItem}s rather than
 * {@code DynamicToolPart}s, have no material and no part mapping, and would produce a part nobody can use. Meta 0 is
 * the
 * ingot cast, which is not a part at all.
 * </p>
 *
 * <p>
 * The frying pan and battlesign heads ship disabled: they are joke tools, and at a few hundred materials each part
 * costs
 * a few hundred recipes.
 * </p>
 */
public final class PartSpec {

    public final String key;
    public final int patternMeta;
    public final int costHalfIngots;
    public final boolean defaultEnabled;

    private final Item partItem;

    private static final List<PartSpec> ALL = new ArrayList<>();

    static {
        add("toolRod", 1, TinkerTools.toolRod, 1, true);
        add("pickaxeHead", 2, TinkerTools.pickaxeHead, 2, true);
        add("shovelHead", 3, TinkerTools.shovelHead, 2, true);
        add("hatchetHead", 4, TinkerTools.hatchetHead, 2, true);
        add("swordBlade", 5, TinkerTools.swordBlade, 2, true);
        add("wideGuard", 6, TinkerTools.wideGuard, 1, true);
        add("handGuard", 7, TinkerTools.handGuard, 1, true);
        add("crossbar", 8, TinkerTools.crossbar, 1, true);
        add("binding", 9, TinkerTools.binding, 1, true);
        add("frypanHead", 10, TinkerTools.frypanHead, 2, false);
        add("signHead", 11, TinkerTools.signHead, 2, false);
        add("knifeBlade", 12, TinkerTools.knifeBlade, 1, true);
        add("chiselHead", 13, TinkerTools.chiselHead, 1, true);
        add("toughRod", 14, TinkerTools.toughRod, 6, true);
        add("toughBinding", 15, TinkerTools.toughBinding, 6, true);
        add("largePlate", 16, TinkerTools.largePlate, 16, true);
        add("broadAxeHead", 17, TinkerTools.broadAxeHead, 16, true);
        add("scytheBlade", 18, TinkerTools.scytheBlade, 16, true);
        add("excavatorHead", 19, TinkerTools.excavatorHead, 16, true);
        add("largeSwordBlade", 20, TinkerTools.largeSwordBlade, 16, true);
        add("hammerHead", 21, TinkerTools.hammerHead, 16, true);
        add("fullGuard", 22, TinkerTools.fullGuard, 6, true);
        add("arrowHead", 25, TinkerWeaponry.arrowhead, 2, true);
    }

    private PartSpec(String key, int patternMeta, Item partItem, int costHalfIngots, boolean defaultEnabled) {
        this.key = key;
        this.patternMeta = patternMeta;
        this.partItem = partItem;
        this.costHalfIngots = costHalfIngots;
        this.defaultEnabled = defaultEnabled;
    }

    private static void add(String key, int patternMeta, Item partItem, int costHalfIngots, boolean defaultEnabled) {
        if (partItem != null) ALL.add(new PartSpec(key, patternMeta, partItem, costHalfIngots, defaultEnabled));
    }

    /** @return every part that exists and has not been switched off in the config. */
    public static List<PartSpec> enabled() {
        List<PartSpec> result = new ArrayList<>();
        for (PartSpec spec : ALL) {
            if (TGConfig.isPartEnabled(spec.key, spec.defaultEnabled)) result.add(spec);
        }
        return result;
    }

    public static int count() {
        return ALL.size();
    }

    public static boolean castsAvailable() {
        return TinkerSmeltery.metalPattern != null;
    }

    /**
     * The cast, with a stack size of 0. GT6 subtracts the recipe input's stack size, so 0 means "the cast is required
     * but never consumed" - the same deal TConstruct's own casting table gives you.
     */
    public ItemStack cast() {
        ItemStack cast = new ItemStack(TinkerSmeltery.metalPattern, 1, patternMeta);
        cast.stackSize = 0;
        return cast;
    }

    public ItemStack output(int materialID) {
        // A half-ingot part is a cheap one: one ingot yields two tool rods, exactly as it does in the Part Builder.
        return output(materialID, costHalfIngots <= 1 ? 2 : 1);
    }

    public ItemStack output(int materialID, int count) {
        return new ItemStack(partItem, Math.max(1, count), materialID);
    }

    /** Ingots to consume: one per two half-ingots, rounded up. */
    public int inputAmount() {
        return Math.max(1, (costHalfIngots + 1) / 2);
    }

    /** Never feed a recipe more than this many ingots, no matter what the part costs. */
    public int inputAmount(int cap) {
        return cap > 0 ? Math.min(cap, inputAmount()) : inputAmount();
    }
}
