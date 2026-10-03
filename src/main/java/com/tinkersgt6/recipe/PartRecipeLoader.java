package com.tinkersgt6.recipe;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.reference.Config;
import com.tinkersgt6.util.TinkersGT6Log;

import gregapi.data.RM;

/**
 * Registers "material + cast &rarr; part" recipes on GT6's extruder.
 *
 * <p>
 * Runs during postInit, which is the earliest point where all three things it needs are settled: GT6 has registered its
 * machines (that happens in its init) and finished its own recipe loading, TConstruct has registered its parts, and
 * {@code OP.*.mat()} has become a plain table lookup so the form of every material can be trusted.
 * </p>
 *
 * <p>
 * One recipe per material and part. The cast goes in with a stack size of 0, so it is required but never consumed, and
 * the material is spent in the same quantities the Part Builder uses.
 * </p>
 */
public final class PartRecipeLoader {

    public static void registerAll() {
        if (!PartSpec.castsAvailable()) {
            TinkersGT6Log.warn("TConstruct has no metal casts registered - skipping all part recipes.");
            return;
        }

        List<PartSpec> parts = PartSpec.enabled();
        if (parts.isEmpty()) {
            TinkersGT6Log.warn("Every part is disabled in the config - no part recipes registered.");
            return;
        }

        boolean simple = "SIMPLE".equalsIgnoreCase(
            TGConfig.recipeString(
                Config.PART_INPUT_MODE,
                "EXACT",
                new String[] { "EXACT", "SIMPLE" },
                "How much material a part costs. EXACT follows TConstruct: a tool rod takes half an ingot so one ingot"
                    + " yields two, a hammer head takes eight. SIMPLE charges one ingot for one part of any size."));
        int cap = (int) TGConfig.recipeLong(
            Config.PART_INPUT_CAP,
            8,
            "Never consume more than this many ingots for a single part, no matter what the cast costs. 8 leaves"
                + " TConstruct's own costs untouched; lower it to make the big parts cheap.");

        long eut = RecipeTiming.eut();
        int recipes = 0;
        int failed = 0;
        int dropped = 0;

        for (MaterialRegistry.Registered entry : MaterialRegistry.active()) {
            ItemStack unit = MaterialForm.input(entry.gt, 1);
            if (unit == null) {
                // No ingot, no gem, no dust: nothing can shape this material into a part, so it should not have been
                // offered as a tool material in the first place.
                MaterialRegistry.deactivate(entry);
                dropped++;
                TinkersGT6Log
                    .debug("Dropped " + entry.gt.mNameInternal + ": no ingot, gem or dust form to craft from.");
                continue;
            }

            for (PartSpec spec : parts) {
                int amount = simple ? 1 : spec.inputAmount(cap);
                ItemStack input = unit.copy();
                input.stackSize = amount;

                ItemStack output = simple ? spec.output(entry.id, 1) : spec.output(entry.id);
                long duration = RecipeTiming.duration(entry.gt, spec.costHalfIngots);

                if (RM.Extruder.addRecipe2(true, eut, duration, input, spec.cast(), output) == null) {
                    failed++;
                    continue;
                }
                recipes++;
            }
        }

        TinkersGT6Log.info(
            "Registered " + recipes
                + " extruder part recipes for "
                + MaterialRegistry.active()
                    .size()
                + " materials across "
                + parts.size()
                + " parts"
                + (dropped > 0 ? " (dropped " + dropped + " materials with no craftable form" : "")
                + (failed > 0 ? ", rejected " + failed : "")
                + ").");
    }

    private PartRecipeLoader() {}
}
