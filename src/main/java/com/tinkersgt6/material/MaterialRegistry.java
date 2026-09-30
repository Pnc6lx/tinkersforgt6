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

        /** GT6 stats as seen at registration time, used to detect Materials.cfg overrides. */
        public final byte quality;
        public final long durability;
        public final float speed;

        Registered(OreDictMaterial gt, int id, ToolMaterial toolMaterial, boolean creativeTab) {
            this.gt = gt;
            this.id = id;
            this.toolMaterial = toolMaterial;
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

            ToolMaterial toolMaterial;
            try {
                toolMaterial = ToolMaterialFactory.create(material);
                TConstructRegistry.addToolMaterial(
                    id,
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
            } catch (Throwable t) {
                // Last line of defence: addToolMaterial throws on an occupied ID. One bad material must never
                // take the whole game down with it.
                skippedByPolicy++;
                TinkersGT6Log.error("Skipping GT6 material " + material.mNameInternal + " (ID " + id + ")", t);
                continue;
            }

            boolean creativeTab = MaterialSource.shouldShowInCreativeTab(material);
            if (creativeTab) TConstructRegistry.addDefaultToolPartMaterial(id);

            usedIDs.add(id);
            idToMaterial.put(id, material);
            materialToID.put(material, id);
            registered.add(new Registered(material, id, toolMaterial, creativeTab));
            registeredCount++;

            if (TGConfig.debugLogging()) {
                TinkersGT6Log.debug(material, id, toolMaterial);
            }
        }

        TGConfig.saveIfChanged();
    }

    /**
     * Rebuilds the materials whose GT6 stats were overridden by {@code Materials.cfg}. Safe to call during init:
     * TConstruct has not built any tool yet.
     */
    public static int refreshStats() {
        int changed = 0;
        for (Registered entry : registered) {
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

        if (Loader.isModLoaded(Mods.TGREGWORKS) && start < 2500) {
            // TinkersGregworks defaults to 1500 and occupies roughly 1500-1875, and it does *not* re-check
            // occupancy once its config has been written. Staying above it keeps both addons usable together.
            TinkersGT6Log
                .warn("TinkersGregworks detected: raising the material ID range start from " + start + " to 2500.");
            start = 2500;
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

    public static int registeredCount() {
        return registeredCount;
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
