package com.tinkersgt6.material;

import java.util.HashSet;
import java.util.Set;

import com.tinkersgt6.config.TGConfig;

import gregapi.oredict.OreDictMaterial;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.tools.ToolMaterial;

/**
 * Builds the TConstruct {@link ToolMaterial} for a GT6 material.
 */
public final class ToolMaterialFactory {

    /** Tip style shown in tool tooltips. */
    private static final String DEFAULT_STYLE = "\u00A7f";

    /** Names already known to TConstruct, used to avoid silently overwriting {@code toolMaterialStrings}. */
    private static final Set<String> foreignNames = new HashSet<>();

    public static void recordExistingNames() {
        foreignNames.clear();
        for (ToolMaterial mat : TConstructRegistry.toolMaterials.values()) {
            if (mat != null && mat.materialName != null) foreignNames.add(mat.materialName);
        }
        for (ToolMaterial mat : TConstructRegistry.toolMaterialStrings.values()) {
            if (mat != null && mat.materialName != null) foreignNames.add(mat.materialName);
        }
    }

    /**
     * Material name as registered into TConstruct. A prefix from the config is prepended, and when the resulting name
     * would clash with an already registered one we append {@code _gt6} - {@code toolMaterialStrings} is a plain
     * HashMap, so a clash would silently shadow the other addon's material.
     */
    public static String materialName(OreDictMaterial m) {
        String name = TGConfig.materialNamePrefix() + m.mNameInternal;
        if (foreignNames.contains(name) && !TGConfig.addMaterialsAnyway()) name = name + "_gt6";
        return name;
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
