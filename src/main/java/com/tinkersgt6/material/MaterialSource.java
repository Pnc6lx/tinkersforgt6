package com.tinkersgt6.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tinkersgt6.config.TGConfig;

import gregapi.data.TD;
import gregapi.oredict.OreDictMaterial;

/**
 * Collects the GT6 materials that should become TConstruct tool materials.
 */
public final class MaterialSource {

    /**
     * @return every GT6 material that can be turned into tools.
     *
     *         <p>
     *         {@code OreDictMaterial.MATERIAL_ARRAY} is indexed by the material's own numeric ID and, unlike
     *         {@code MATERIAL_MAP}, contains no alias entries (aliases are registered with ID -1 and are therefore
     *         never stored). Iterating it gives a stable registration order.
     *         </p>
     */
    public static List<OreDictMaterial> collect() {
        // Keyed by internal name, keeping the last declaration: GT6 defines some materials twice (MT.java declares
        // Moonstone at both mID 0 and 8452, and only the second carries the real .qual() tool stats), and
        // MATERIAL_ARRAY retains both instances. Registering both would burn an extra ID and, worse, make the second
        // one read back the ID the first had just written to the config, producing a bogus "already taken" conflict.
        Map<String, OreDictMaterial> byName = new LinkedHashMap<>();

        for (OreDictMaterial material : OreDictMaterial.MATERIAL_ARRAY) {
            if (material == null) continue;
            // Same predicate GT6 itself uses (GT_API.onLoad, Loader_Tools, UT) to decide "is this a tool material".
            if (material.mToolTypes <= 0) continue;
            if (material.contains(TD.Properties.INVALID_MATERIAL)) continue;
            if (material.mNameInternal == null || material.mNameInternal.isEmpty()) continue;
            byName.put(material.mNameInternal, material);
        }

        return new ArrayList<>(byName.values());
    }

    /** A material is skipped when the user disabled it in the config. */
    public static boolean isEnabled(OreDictMaterial material) {
        return TGConfig.isMaterialEnabled(material.mNameInternal);
    }

    /** {@code ENABLED_NOT_HIDDEN} filters out materials GT6 itself hides from the player. */
    public static boolean shouldShowInCreativeTab(OreDictMaterial material) {
        String mode = TGConfig.creativeTabMode();
        if ("NONE".equalsIgnoreCase(mode)) return false;

        boolean default_;
        if ("ALL".equalsIgnoreCase(mode)) {
            default_ = true;
        } else {
            default_ = !material.mHidden;
        }
        return TGConfig.showInCreativeTab(material.mNameInternal, default_);
    }

    private MaterialSource() {}
}
