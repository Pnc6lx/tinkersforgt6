package com.tinkersgt6.compat;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregapi.data.OP;
import gregapi.oredict.OreDictItemData;
import gregapi.oredict.OreDictMaterial;
import gregapi.util.OM;
import tconstruct.library.event.ToolBuildEvent;
import tconstruct.tools.TinkerTools;

/**
 * Makes GregTech rods usable as TConstruct tool handles, and keeps our own rods out of GregTech's unification.
 *
 * <p>
 * TConstruct decides what a handle is by asking the item for a material ID, and only its own {@code IToolPart}s answer
 * that - a GregTech rod in the Tool Station slot is simply rejected. GregTech, on the other side, treats the
 * {@code stick} prefix as unificatable, and its {@code LoaderOreDictReRegistrations} explicitly maps names like
 * {@code ironRod} onto {@code stickIron}, so handles can quietly turn into GregTech sticks in an inventory.
 * </p>
 *
 * <p>
 * Both directions are handled here rather than by touching GregTech's state:
 * </p>
 * <ul>
 * <li>{@link #onToolBuild(ToolBuildEvent)} translates a GregTech rod into the matching TinkersGT6 tool rod. The event
 * is fired by {@code ToolBuilder} <em>before</em> it asks for material IDs, which is the same hook TConstruct itself
 * uses to turn a bone or a vanilla stick into a tool rod.</li>
 * <li>{@link #blacklistOwnRods()} exempts the rods we registered, so GregTech never swaps them for one of its
 * own.</li>
 * </ul>
 */
public class RodCompat {

    public static void register() {
        if (TGConfig.rodCompat()) {
            MinecraftForge.EVENT_BUS.register(new RodCompat());
        }
        if (TGConfig.rodBlacklist()) {
            blacklistOwnRods();
        }
    }

    /**
     * Swaps a GregTech rod for the TinkersGT6 tool rod of the same material.
     *
     * <p>
     * GregTech's OreDict data is used instead of an item comparison because that is where GregTech itself records
     * "this is a stick of steel". Materials this mod did not register are left alone - a wooden stick still goes
     * through TConstruct's own handling.
     * </p>
     */
    @SubscribeEvent
    public void onToolBuild(ToolBuildEvent event) {
        if (event.handleStack == null) return;

        OreDictItemData data = OM.anyassociation(event.handleStack);
        if (data == null || !data.validPrefix() || !data.validMaterial()) return;
        if (data.mPrefix != OP.stick) return;

        OreDictMaterial material = OreDictMaterial.get(data.mMaterial.mMaterial);
        int materialID = MaterialRegistry.toID(material);
        if (materialID < 0) return;

        ItemStack handle = new ItemStack(TinkerTools.toolRod, 1, materialID);
        handle.stackSize = event.handleStack.stackSize;
        event.handleStack = handle;
    }

    /**
     * Adds our tool rods to GregTech's unification blacklist.
     *
     * <p>
     * Purely defensive: TConstruct only registers OreDict names for its own first eighteen materials, and ours live far
     * above that, so nothing currently points GregTech at them. Rods added by another addon later are covered by this
     * too, and it costs one set entry per material.
     * </p>
     */
    private static void blacklistOwnRods() {
        int count = 0;
        for (MaterialRegistry.Registered entry : MaterialRegistry.active()) {
            OM.blacklist(new ItemStack(TinkerTools.toolRod, 1, entry.id));
            count++;
        }
        if (count > 0) TinkersGT6Log.info("Exempted " + count + " tool rods from GregTech's OreDict unification.");
    }
}
