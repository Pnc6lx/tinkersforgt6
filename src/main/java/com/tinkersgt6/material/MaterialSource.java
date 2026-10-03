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
 *
 * <p>
 * Three different questions are answered here, and they are deliberately kept apart because they are asked at
 * different times during startup:
 * </p>
 * <ul>
 * <li>{@link #collect()} - "could this ever become a tool material, and does it come in a form we can shape?" Asked
 * once during preInit, before any ID is handed out.</li>
 * <li>{@link #isVisible(OreDictMaterial)} - "does GT6 currently show this material to the player?" Asked during
 * preInit <em>and</em> again during postInit, because GT6 can un-hide a material in between.</li>
 * <li>{@link #canMakeParts(OreDictMaterial)} - "does this material come in a form we can actually feed into a
 * recipe?" Asked during postInit, once GT6's registrations are complete.</li>
 * </ul>
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
     *
     *         <p>
     *         Materials GT6 currently hides are returned as well: they still get an ID reserved so that the config
     *         stays stable, they are simply not registered until (and unless) GT6 reveals them.
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

            // Follow the re-registration target first: an alias shares its name with the material it points at, so
            // keeping both would put two TConstruct materials on one GT6 material.
            OreDictMaterial resolved = OreDictMaterial.get(material);
            if (resolved != material) continue;
            if (resolved.mID <= 0) continue;

            // Same predicate GT6 itself uses (GT_API.onLoad, Loader_Tools, UT) to decide "is this a tool material".
            if (resolved.mToolTypes <= 0) continue;
            if (
                resolved.containsAny(
                    TD.Properties.INVALID_MATERIAL,
                    TD.Properties.UNUSED_MATERIAL,
                    TD.Properties.AUTO_MATERIAL)
            ) continue;
            if (resolved.mNameInternal == null || resolved.mNameInternal.isEmpty()) continue;

            // A tool material nobody can shape into a part is worse than no material at all: stone-type materials
            // (Enceladus stone and friends) carry tool stats but only ever exist as blocks, so they used to show up as
            // tools with no craftable parts behind them. The tag check is exact - OP.ingot/gem/dust generate an item
            // for precisely the materials carrying INGOTS/GEMS/DUSTS - so nothing that has a form is lost here.
            if (!canMakeParts(resolved)) continue;

            byName.put(resolved.mNameInternal, resolved);
        }

        return new ArrayList<>(byName.values());
    }

    /**
     * Whether GT6 currently shows this material.
     *
     * <p>
     * {@code mHidden} is GT6's single authority here: it is set by {@code hide()} and by {@code visDefault()}, which
     * hides a material unless the mod that originally contributed it is loaded. It is <em>not</em> final - GT6 re-
     * reveals a material as soon as anything that is not a GT item shows up under one of the standard
     * {@code ore/dust/gem/ingot/plate/stick + name} OreDict entries ({@code OreDictManager.triggerVisibility}), and
     * worldgen does the same. That is why the answer has to be checked again in postInit and not just cached.
     * </p>
     */
    public static boolean isVisible(OreDictMaterial material) {
        OreDictMaterial resolved = material == null ? null : OreDictMaterial.get(material);
        return resolved != null && !resolved.mHidden;
    }

    /**
     * Whether this material comes in a form a part recipe could consume.
     *
     * <p>
     * This only looks at GT6's item generator tags, which are available straight away, so it is a cheap pre-filter.
     * The authoritative check is {@code recipe/MaterialForm}, which asks GT6 for an actual stack once the registrations
     * are complete - a tag can promise a form that no mod ever delivered.
     * </p>
     */
    public static boolean canMakeParts(OreDictMaterial material) {
        OreDictMaterial resolved = material == null ? null : OreDictMaterial.get(material);
        if (resolved == null) return false;
        return resolved.containsAny(
            TD.ItemGenerator.INGOTS,
            TD.ItemGenerator.GEMS,
            TD.ItemGenerator.DUSTS,
            TD.ItemGenerator.DIRTY_DUSTS);
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
