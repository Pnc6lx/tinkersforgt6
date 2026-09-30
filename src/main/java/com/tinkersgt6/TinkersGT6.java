package com.tinkersgt6;

import net.minecraftforge.common.MinecraftForge;

import com.tinkersgt6.enchant.AmmoLootingHandler;
import com.tinkersgt6.enchant.GTMaterialTraitMod;
import com.tinkersgt6.reference.Mods;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import tconstruct.library.TConstructRegistry;

@Mod(
    modid = TinkersGT6.MODID,
    version = Tags.VERSION,
    name = TinkersGT6.NAME,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:" + Mods.GREGAPI + "; before:" + Mods.TCONSTRUCT)
public class TinkersGT6 {

    public static final String MODID = "tinkersgt6";
    public static final String NAME = "TinkersGT6";

    @SidedProxy(clientSide = "com.tinkersgt6.ClientProxy", serverSide = "com.tinkersgt6.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);

        // The GregTech material trait: keeps material enchantments on the tool and re-applies them whenever a
        // TConstruct modifier overwrites the level.
        TConstructRegistry.registerActiveToolMod(new GTMaterialTraitMod());

        MinecraftForge.EVENT_BUS.register(new AmmoLootingHandler());
        TinkersGT6Log.info("GregTech material trait registered.");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
