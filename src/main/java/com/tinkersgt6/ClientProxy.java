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
     * Off by default, and that is deliberate. When no mapping is registered {@code ToolCore.getCorrectIcon} falls back
     * to TConstruct's own default part silhouette and {@code getCorrectColor} tints it with the material's
     * {@code primaryColor} - exactly the "default part, GT6 colour" look we want. A mapping only pays off when the
     * material actually ships textures under {@code tinker:<folder>/<material><suffix>}; without them the name is
     * resolved against whatever material owns it (Iron, Steel, ...) and the tinting stops.
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
