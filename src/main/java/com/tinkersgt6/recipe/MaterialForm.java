package com.tinkersgt6.recipe;

import net.minecraft.item.ItemStack;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.reference.Config;

import gregapi.data.OP;
import gregapi.oredict.OreDictMaterial;
import gregapi.util.ST;

/**
 * Picks the form a GT6 material has to be fed into a part recipe in.
 *
 * <p>
 * Priority is <b>ingot &rarr; gem &rarr; dust</b>: metals arrive as ingots, gem materials (Diamond, Ruby, Sapphire,
 * ...) as gems, and anything that only exists as a powder falls back to dust. Materials with none of the three - gases,
 * liquids, or the stone-type materials that only ever exist as blocks - cannot be turned into a part, and that is what
 * keeps them out of the tool material list entirely.
 * </p>
 *
 * <p>
 * The lookup is done exactly the way GT6 does it itself in {@code RecipeMapReplicator}: ask the prefix for a stack and
 * treat {@code null} as "this form does not exist". During postInit the OreDict registrations are complete, so
 * {@code OP.*.mat()} is a pure table lookup - no item gets synthesised behind our back.
 * </p>
 *
 * <p>
 * {@code partFormMode = METAL} drops the dust fallback, for packs that only want ingot/gem materials to be craftable.
 * </p>
 */
public final class MaterialForm {

    /**
     * @param amount number of items, not material units: {@code OP.ingot.mat(m, 8)} is eight ingots because ingot, gem
     *               and dust all carry {@code mAmount = U}.
     * @return the stack to consume, or null when the material has no usable form.
     */
    public static ItemStack input(OreDictMaterial material, long amount) {
        if (material == null || amount <= 0) return null;

        ItemStack stack = OP.ingot.mat(material, amount);
        if (ST.invalid(stack)) stack = OP.gem.mat(material, amount);
        if (ST.invalid(stack)) {
            if (metalOnly()) return null;
            stack = OP.dust.mat(material, amount);
        }
        return ST.invalid(stack) ? null : stack;
    }

    private static boolean metalOnly() {
        return "METAL".equalsIgnoreCase(
            TGConfig.recipeString(
                Config.PART_FORM_MODE,
                "ANY",
                new String[] { "ANY", "METAL" },
                "Which material forms may feed a part recipe. ANY = ingot, gem or dust, METAL = ingot or gem only,"
                    + " so dust-only materials stay out of the tool material list."));
    }

    private MaterialForm() {}
}
