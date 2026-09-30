package com.tinkersgt6;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import tconstruct.library.client.TConstructClientRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        registerRenderMappings();
    }

    /**
     * Without a render mapping TConstruct has no idea where to look for a material's textures, and the tool renders
     * with missing textures. Paths follow {@code tinker:<defaultFolder>/<materialName><iconSuffix>}, which is also what
     * resource packs override.
     */
    private void registerRenderMappings() {
        if (!TGConfig.clientRenderMapping()) return;

        int count = 0;
        for (MaterialRegistry.Registered entry : MaterialRegistry.all()) {
            if (entry.toolMaterial == null) continue;
            String renderName = entry.toolMaterial.materialName.toLowerCase();
            TConstructClientRegistry.addMaterialRenderMapping(entry.id, "tinker", renderName, true);
            count++;
        }
        TinkersGT6Log.info("Registered " + count + " client render mappings.");
    }
}
