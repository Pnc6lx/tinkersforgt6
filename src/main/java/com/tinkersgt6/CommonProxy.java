package com.tinkersgt6;

import com.tinkersgt6.compat.MazeBreakerCompat;
import com.tinkersgt6.compat.RodCompat;
import com.tinkersgt6.compat.ToolClickCompat;
import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.material.ToolMaterialFactory;
import com.tinkersgt6.recipe.PartRecipeLoader;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        TGConfig.init(event.getSuggestedConfigurationFile());

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
                + ", waiting on GT6 to reveal them: "
                + MaterialRegistry.deferredCount()
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

    public void postInit(FMLPostInitializationEvent event) {
        // GT6 can reveal materials after preInit (anything registering under one of its standard OreDict names does
        // that), so the hidden ones get a second chance here - using the IDs that were reserved for them.
        int revealed = MaterialRegistry.activatePending();
        if (revealed > 0) {
            TinkersGT6Log.info("Registered " + revealed + " materials GT6 revealed after startup.");
        }

        PartRecipeLoader.registerAll();
        RodCompat.register();
        ToolClickCompat.register();
        MazeBreakerCompat.register();
    }

    public void serverStarting(FMLServerStartingEvent event) {}
}
