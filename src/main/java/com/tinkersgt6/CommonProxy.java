package com.tinkersgt6;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.material.ToolMaterialFactory;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        TGConfig.init(event.getSuggestedConfigurationFile());

        // Snapshot TConstruct's existing names before we add ours, so ToolMaterialFactory can avoid clashing.
        ToolMaterialFactory.recordExistingNames();

        MaterialRegistry.registerAll();

        TinkersGT6Log.info(
            "Registered " + MaterialRegistry.registeredCount()
                + " GregTech 6 materials"
                + " (disabled by config: "
                + MaterialRegistry.skippedByConfig()
                + ", skipped after ID conflict: "
                + MaterialRegistry.skippedByPolicy()
                + ", conflicts: "
                + MaterialRegistry.conflictCount()
                + ").");
    }

    public void init(FMLInitializationEvent event) {
        // GT6 applies its Materials.cfg overrides during gregapi's own init phase, which runs before ours.
        // ToolMaterial fields are final, so swap in fresh instances for any material whose stats changed.
        int updated = MaterialRegistry.refreshStats();
        if (updated > 0) {
            TinkersGT6Log.info("Re-evaluated " + updated + " materials after GregTech's Materials.cfg was applied.");
        }
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
