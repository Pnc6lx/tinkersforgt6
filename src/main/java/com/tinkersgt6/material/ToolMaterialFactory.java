package com.tinkersgt6.material;

import com.tinkersgt6.config.TGConfig;

import gregapi.oredict.OreDictMaterial;
import tconstruct.library.tools.ToolMaterial;

/**
 * Builds the TConstruct {@link ToolMaterial} for a GT6 material.
 */
public final class ToolMaterialFactory {

    /** Tip style shown in tool tooltips. */
    private static final String DEFAULT_STYLE = "\u00A7f";

    /**
     * Material name as registered into TConstruct. A prefix from the config is prepended.
     *
     * <p>
     * A name TConstruct itself also uses is deliberately left alone: the two materials only ever meet by name, every
     * part and tool resolves its material through the numeric ID, and GT6's extruder is the only way to shape ours -
     * so there is no recipe the two could fight over.
     * </p>
     */
    public static String materialName(OreDictMaterial m) {
        return TGConfig.materialNamePrefix() + m.mNameInternal;
    }

    /**
     * GT6 registers its own localisation keys as {@code gt.material.<internal name>} during its client proxy, so we can
     * reuse them verbatim instead of duplicating every material name in our own lang files.
     */
    public static String localizationString(OreDictMaterial m) {
        return "gt.material." + m.mNameInternal;
    }

    public static ToolMaterial create(OreDictMaterial m) {
        return new ToolMaterial(
            materialName(m),
            localizationString(m),
            MaterialStats.harvestLevel(m),
            MaterialStats.durability(m),
            MaterialStats.miningSpeed(m),
            MaterialStats.attack(m),
            MaterialStats.handleModifier(m),
            MaterialStats.reinforced(m),
            MaterialStats.stonebound(m),
            DEFAULT_STYLE,
            MaterialStats.color(m));
    }

    private ToolMaterialFactory() {}
}
