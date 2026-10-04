package com.tinkersgt6.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.tinkersgt6.util.TinkersGT6Log;

/**
 * Hands our mixins to UniMixins' GTNHMixins module.
 *
 * <p>
 * Mixin on its own only ever transforms core mods: every one of its phases runs before Forge has discovered the
 * ordinary mods and put them on the class path, so {@code tconstruct.library.tools.ToolCore} is simply not visible to
 * them yet. GTNHMixins adds a late phase that fires when Forge starts constructing mods - after every mod has been
 * discovered but before any of them reaches preInit, which is when TConstruct registers its tools. That is exactly the
 * window a mixin into a TConstruct class needs, and it is why this mod does not have to become a core mod.
 * </p>
 *
 * <p>
 * Everything is conditional: without UniMixins nothing loads this class at all, and the features it carries fall back
 * to
 * their non-mixin implementations (see {@code MazeBreakerCompat}) or are simply absent. The mod must never crash or
 * misbehave because the loader is missing.
 * </p>
 */
@LateMixin
public class TinkersGT6LateMixins implements ILateMixinLoader {

    /** TConstruct's mod id, as FML reports it in the set of loaded mods. */
    private static final String TCONSTRUCT = "TConstruct";

    @Override
    public String getMixinConfig() {
        return "mixins.tinkersforgt6.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();

        if (loadedMods.contains(TCONSTRUCT)) {
            // Makes every Tinker tool answer to GregTech's IItemEnergy, which is the only thing a GregTech battery box,
            // charger or any other GT6 machine asks of an item before it charges it.
            mixins.add("ToolCoreEnergyMixin");
            // Puts MazeBreaker's speed bonus inside TConstruct's own mining speed calculation.
            mixins.add("HarvestToolMazeBreakerMixin");
        } else {
            TinkersGT6Log.info("TConstruct not present, skipping its mixins.");
        }

        return mixins;
    }
}
