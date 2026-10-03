package com.tinkersgt6.material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.reference.Mods;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.Loader;
import gregapi.oredict.OreDictMaterial;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.tools.ToolMaterial;

/**
 * Registers GT6 materials into TConstruct's material registry.
 *
 * <p>
 * Two-phase by necessity:
 * </p>
 * <ul>
 * <li><b>preInit</b> - we load before TConstruct, so the IDs have to be in place before
 * {@code DynamicToolPart.registerIcons} sizes its {@code IIcon[maxID + 1]} array.</li>
 * <li><b>init</b> - GT6 only applies its {@code Materials.cfg} overrides to
 * {@code mToolQuality / mToolSpeed / mToolDurability} during gregapi's own init phase, which runs after every
 * preInit. Since {@code ToolMaterial} fields are final we swap in freshly built instances for the materials whose
 * stats changed.</li>
 * </ul>
 */
public final class MaterialRegistry {

    /** Highest ID TConstruct will accept; {@code DynamicToolPart} allocates an icon array of maxID + 1. */
    private static final int MAX_ID = 16383;

    /** TConstruct's own materials live in 0..31; never hand those out. */
    private static final int MIN_SAFE_ID = 300;

    public static final class Registered {

        public final OreDictMaterial gt;
        public final int id;
        public ToolMaterial toolMaterial;
        public final boolean creativeTab;

        /**
         * True once the material is actually inside TConstruct's registry. A material that GT6 hides keeps
         * {@code pending = true} and only gets registered if GT6 reveals it later - its ID stays reserved either way.
         */
        public boolean active;

        /** Registered nowhere yet, waiting for GT6 to un-hide the material. */
        public boolean pending;

        /** GT6 stats as seen at registration time, used to detect Materials.cfg overrides. */
        public final byte quality;
        public final long durability;
        public final float speed;

        Registered(OreDictMaterial gt, int id, boolean creativeTab) {
            this.gt = gt;
            this.id = id;
            this.creativeTab = creativeTab;
            this.quality = gt.mToolQuality;
            this.durability = gt.mToolDurability;
            this.speed = gt.mToolSpeed;
        }

        public boolean statsChanged() {
            return gt.mToolQuality != quality || gt.mToolDurability != durability || gt.mToolSpeed != speed;
        }
    }

    private static final List<Registered> registered = new ArrayList<>();
    private static final Map<Integer, OreDictMaterial> idToMaterial = new HashMap<>();
    private static final Map<OreDictMaterial, Integer> materialToID = new HashMap<>();
    private static final Set<Integer> usedIDs = new HashSet<>();

    private static int latestAvailableNumber;

    /* Counters for the single summary log line. */
    private static int registeredCount;
    private static int skippedByConfig;
    private static int skippedByPolicy;
    private static int conflictCount;
    private static int deferredCount;
    private static int deactivatedCount;

    /* ------------------------------------------------------------------ */

    public static void registerAll() {
        latestAvailableNumber = resolveRangeStart();

        for (OreDictMaterial material : MaterialSource.collect()) {
            if (!MaterialSource.isEnabled(material)) {
                skippedByConfig++;
                continue;
            }

            int id = allocateID(material);
            if (id < 0) {
                skippedByPolicy++;
                continue;
            }

            usedIDs.add(id);
            idToMaterial.put(id, material);
            materialToID.put(material, id);

            Registered entry = new Registered(material, id, MaterialSource.shouldShowInCreativeTab(material));
            registered.add(entry);

            // Hidden materials still keep their ID: GT6 can reveal them later (see MaterialSource.isVisible), and a
            // material that reappears must not silently shift every ID after it.
            if (MaterialSource.isVisible(material)) {
                if (activate(entry)) registeredCount++;
                else skippedByPolicy++;
            } else {
                entry.pending = true;
                deferredCount++;
            }

            if (TGConfig.debugLogging() && entry.toolMaterial != null) {
                TinkersGT6Log.debug(material, id, entry.toolMaterial);
            }
        }

        TGConfig.saveIfChanged();
    }

    /**
     * Puts a single material into TConstruct's registry.
     *
     * @return false when the material had to be dropped - {@code addToolMaterial} throws on an occupied ID, and one
     *         bad material must never take the whole game down with it.
     */
    private static boolean activate(Registered entry) {
        try {
            ToolMaterial toolMaterial = ToolMaterialFactory.create(entry.gt);
            TConstructRegistry.addToolMaterial(
                entry.id,
                toolMaterial.materialName,
                toolMaterial.localizationString,
                toolMaterial.harvestLevel,
                toolMaterial.durability,
                toolMaterial.miningspeed,
                toolMaterial.attack,
                toolMaterial.handleModifier,
                toolMaterial.reinforced,
                toolMaterial.stonebound,
                toolMaterial.tipStyle,
                toolMaterial.primaryColor);
            if (entry.creativeTab) TConstructRegistry.addDefaultToolPartMaterial(entry.id);

            // Read the instance back out: addToolMaterial does not store the one we handed it, it builds its own.
            // Anything that later compares against TConstructRegistry has to use that copy, not ours.
            entry.toolMaterial = TConstructRegistry.toolMaterials.get(entry.id);
            entry.active = true;
            entry.pending = false;
            return true;
        } catch (Throwable t) {
            entry.active = false;
            entry.pending = false;
            TinkersGT6Log.error("Skipping GT6 material " + entry.gt.mNameInternal + " (ID " + entry.id + ")", t);
            return false;
        }
    }

    /**
     * Takes a material back out of TConstruct's registry and leaves its ID reserved. Used for materials that turned
     * out to have no craftable form - a tool material nobody can build a part from is worse than no material at all.
     */
    public static void deactivate(Registered entry) {
        if (!entry.active) return;

        ToolMaterial mine = entry.toolMaterial;
        if (mine != null) {
            // Only remove the name if it still points at *our* material: TConstruct may have overwritten it with its
            // own, and that one has to stay.
            ToolMaterial byName = TConstructRegistry.toolMaterialStrings.get(mine.materialName);
            if (byName == mine) TConstructRegistry.toolMaterialStrings.remove(mine.materialName);
        }
        TConstructRegistry.toolMaterials.remove(Integer.valueOf(entry.id));
        TConstructRegistry.defaultToolPartMaterials.remove(Integer.valueOf(entry.id));

        entry.active = false;
        entry.pending = false;
        deactivatedCount++;
    }

    /**
     * Registers everything GT6 has revealed since preInit.
     *
     * <p>
     * Safe to call during postInit: every ID was already handed out during preInit, so nothing here can push the
     * highest ID past what {@code DynamicToolPart} sized its icon array to.
     * </p>
     */
    public static int activatePending() {
        int activated = 0;
        for (Registered entry : registered) {
            if (!entry.pending) continue;
            if (!MaterialSource.isVisible(entry.gt)) continue;

            entry.pending = false;
            if (activate(entry)) {
                registeredCount++;
                deferredCount--;
                activated++;
            } else {
                skippedByPolicy++;
                deferredCount--;
            }
        }
        return activated;
    }

    /**
     * Rebuilds the materials whose GT6 stats were overridden by {@code Materials.cfg}. Safe to call during init:
     * TConstruct has not built any tool yet.
     */
    public static int refreshStats() {
        int changed = 0;
        for (Registered entry : registered) {
            if (!entry.active) continue;
            if (!entry.statsChanged()) continue;

            ToolMaterial fresh = ToolMaterialFactory.create(entry.gt);
            // ToolMaterial fields are final, so replace the registry entry wholesale.
            TConstructRegistry.toolMaterials.put(entry.id, fresh);
            TConstructRegistry.toolMaterialStrings.put(fresh.materialName, fresh);
            entry.toolMaterial = fresh;
            changed++;

            if (TGConfig.debugLogging()) {
                TinkersGT6Log.debug(entry.gt, entry.id, fresh);
            }
        }
        return changed;
    }

    /* ------------------------------------------------------------------ */

    private static int resolveRangeStart() {
        int start = TGConfig.materialIDRangeStart();
        if (start < MIN_SAFE_ID) start = MIN_SAFE_ID;

        if (Loader.isModLoaded(Mods.TGREGWORKS) && start < 3000) {
            // TinkersGregworks defaults to 1500 and occupies roughly 1500-1875, and it does *not* re-check
            // occupancy once its config has been written. Staying above it keeps both addons usable together -
            // and above 3000 also dodges the ExtraTiC window that TiC-Tooltips assumes.
            TinkersGT6Log
                .warn("TinkersGregworks detected: raising the material ID range start from " + start + " to 3000.");
            start = 3000;
        }
        return start;
    }

    private static int allocateID(OreDictMaterial material) {
        String name = material.mNameInternal;
        int configID = TGConfig.getMaterialID(name);

        if (configID > 0) {
            if (configID > MAX_ID) {
                TinkersGT6Log.warn("Configured material ID " + configID + " for " + name + " is out of range.");
                return onConflict(material, -1);
            }
            if (configID < MIN_SAFE_ID) {
                TinkersGT6Log.warn(
                    "Configured material ID " + configID + " for " + name + " collides with TConstruct's own IDs.");
                return onConflict(material, -1);
            }
            if (!isIDFree(configID)) {
                conflictCount++;
                TinkersGT6Log.warn(
                    "Material ID " + configID
                        + " for "
                        + name
                        + " is already taken by "
                        + (TConstructRegistry.toolMaterials.containsKey(configID)
                            ? TConstructRegistry.toolMaterials.get(configID).materialName
                            : "another TinkersGT6 material"));
                return onConflict(material, configID);
            }
            return configID;
        }

        int id = firstFree();
        if (id < 0) throw new RuntimeException("TConstruct tool material registry ran out of IDs!");
        TGConfig.setMaterialID(name, id);
        return id;
    }

    private static int onConflict(OreDictMaterial material, int offendingID) {
        String policy = TGConfig.onIdConflict();
        if ("CRASH".equalsIgnoreCase(policy)) {
            throw new IllegalArgumentException(
                "[TinkersGT6] Material ID " + offendingID + " for " + material.mNameInternal + " is already taken.");
        }
        if ("SKIP".equalsIgnoreCase(policy)) return -1;

        int id = firstFree();
        if (id < 0) throw new RuntimeException("TConstruct tool material registry ran out of IDs!");
        TinkersGT6Log.warn("Reallocated " + material.mNameInternal + " to material ID " + id + ".");
        TGConfig.setMaterialID(material.mNameInternal, id);
        return id;
    }

    /** TConstruct loads after us, so its own IDs are not in {@code toolMaterials} yet - track ours separately. */
    private static boolean isIDFree(int id) {
        return !usedIDs.contains(id) && !TConstructRegistry.toolMaterials.containsKey(id);
    }

    private static int firstFree() {
        for (int i = Math.max(latestAvailableNumber, MIN_SAFE_ID); i <= MAX_ID; i++) {
            if (isIDFree(i)) {
                latestAvailableNumber = i + 1;
                return i;
            }
        }
        return -1;
    }

    /* ------------------------------------------------------------------ */

    public static OreDictMaterial fromID(int ticMaterialID) {
        return idToMaterial.get(ticMaterialID);
    }

    public static int toID(OreDictMaterial material) {
        Integer id = materialToID.get(material);
        return id == null ? -1 : id;
    }

    public static List<Registered> all() {
        return Collections.unmodifiableList(registered);
    }

    /**
     * @return every entry that is actually visible to TConstruct right now - this is what parts and recipes may be
     *         generated for.
     */
    public static List<Registered> active() {
        List<Registered> result = new ArrayList<>();
        for (Registered entry : registered) {
            if (entry.active) result.add(entry);
        }
        return result;
    }

    public static int registeredCount() {
        return registeredCount;
    }

    public static int deferredCount() {
        return deferredCount;
    }

    public static int deactivatedCount() {
        return deactivatedCount;
    }

    public static int skippedByConfig() {
        return skippedByConfig;
    }

    public static int skippedByPolicy() {
        return skippedByPolicy;
    }

    public static int conflictCount() {
        return conflictCount;
    }

    private MaterialRegistry() {}
}
