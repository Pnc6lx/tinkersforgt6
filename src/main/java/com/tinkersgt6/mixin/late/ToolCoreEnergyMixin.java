package com.tinkersgt6.mixin.late;

import java.util.Collection;
import java.util.Collections;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

import com.tinkersgt6.power.GTBattery;

import gregapi.code.TagData;
import gregapi.data.TD;
import gregapi.item.IItemEnergy;
import tconstruct.library.tools.ToolCore;

/**
 * Makes a Tinker tool answer to GregTech's {@link IItemEnergy}, so GregTech's own machines charge it.
 *
 * <p>
 * GregTech never looks at NBT when it charges something, it looks at the class:
 * {@code TileEntityBase10EnergyBatBox} - the battery box - only lets an item into its slots when
 * {@code stack.getItem() instanceof IItemEnergy}, and only then does its once-per-second loop push EU into it. That is
 * an interface on the <em>Item</em>, and TConstruct's tools are TConstruct's own classes, so there is no way to add it
 * without changing them. This mixin adds it at load time instead: {@code ToolCore} is the abstract base every Tinker
 * tool
 * extends, so the interface lands on all of them at once.
 * </p>
 *
 * <p>
 * Two answers matter more than the rest:
 * </p>
 * <ul>
 * <li>{@code canEnergyExtraction} is always false. A battery box that runs low also <em>drains</em> the batteries it
 * holds to keep its output up, so a tool that offered to be discharged would be emptied into the grid while it was
 * simply parked in a slot.</li>
 * <li>{@code isEnergyType} is only true for EU and only for receiving, and only once a battery is actually installed -
 * so an ordinary Tinker tool is still not an energy item to any GregTech machine.</li>
 * </ul>
 *
 * <p>
 * Everything is answered from the tool's own NBT through {@link GTBattery}, which is the same source the durability
 * hook
 * and the hotbar refill use, so a tool charged in a battery box and one refilled from a carried battery are
 * interchangeable.
 * </p>
 */
// remap = false: ToolCore is TConstruct's class, there is nothing here for the obfuscation maps to translate. The
// methods below are ours and get reobfuscated together with the rest of the mod, so the descriptor GregTech's
// IItemEnergy asks for matches in the released jar as well as in the dev environment.
@Mixin(value = ToolCore.class, remap = false)
public abstract class ToolCoreEnergyMixin implements IItemEnergy {

    /* ------------------------------------------------------------------ */
    /* what kind of energy this is */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean isEnergyType(TagData aEnergyType, ItemStack aStack, boolean aEmitting) {
        // true only when asked whether it can be charged - never as a source, see the class comment.
        return aEnergyType == TD.Energy.EU && !aEmitting && GTBattery.installed(aStack);
    }

    @Override
    public Collection<TagData> getEnergyTypes(ItemStack aStack) {
        return GTBattery.installed(aStack) ? TD.Energy.EU.AS_LIST : Collections.<TagData>emptyList();
    }

    /* ------------------------------------------------------------------ */
    /* charging */
    /* ------------------------------------------------------------------ */

    @Override
    public long doEnergyInjection(TagData aEnergyType, ItemStack aStack, long aSize, long aAmount,
        IInventory aInventory, World aWorld, int aX, int aY, int aZ, boolean aDoInject) {
        if (aEnergyType != TD.Energy.EU) return 0;
        return GTBattery.inject(aStack, aSize, aAmount, aDoInject);
    }

    @Override
    public boolean canEnergyInjection(TagData aEnergyType, ItemStack aStack, long aSize) {
        return aEnergyType == TD.Energy.EU && GTBattery.accepts(aStack, aSize);
    }

    /* ------------------------------------------------------------------ */
    /* deliberately not a power source */
    /* ------------------------------------------------------------------ */

    @Override
    public long doEnergyExtraction(TagData aEnergyType, ItemStack aStack, long aSize, long aAmount,
        IInventory aInventory, World aWorld, int aX, int aY, int aZ, boolean aDoExtract) {
        return 0;
    }

    @Override
    public boolean canEnergyExtraction(TagData aEnergyType, ItemStack aStack, long aSize) {
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* spending */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean useEnergy(TagData aEnergyType, ItemStack aStack, long aEnergyAmount, EntityLivingBase aPlayer,
        IInventory aInventory, World aWorld, int aX, int aY, int aZ, boolean aDoUse) {
        if (aEnergyType != TD.Energy.EU || !GTBattery.installed(aStack)) return false;
        if (GTBattery.stored(aStack) < aEnergyAmount) return false;
        if (aDoUse) GTBattery.take(aStack, aEnergyAmount);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* the numbers GregTech asks for */
    /* ------------------------------------------------------------------ */

    @Override
    public ItemStack setEnergyStored(TagData aEnergyType, ItemStack aStack, long aAmount) {
        if (aEnergyType == TD.Energy.EU && GTBattery.installed(aStack)) GTBattery.setStored(aStack, aAmount);
        return aStack;
    }

    @Override
    public long getEnergyStored(TagData aEnergyType, ItemStack aStack) {
        return aEnergyType == TD.Energy.EU ? GTBattery.stored(aStack) : 0;
    }

    @Override
    public long getEnergyCapacity(TagData aEnergyType, ItemStack aStack) {
        return aEnergyType == TD.Energy.EU ? GTBattery.capacity(aStack) : 0;
    }

    @Override
    public long getEnergySizeInputMin(TagData aEnergyType, ItemStack aStack) {
        return aEnergyType == TD.Energy.EU ? GTBattery.sizeMin(aStack) : 0;
    }

    @Override
    public long getEnergySizeInputRecommended(TagData aEnergyType, ItemStack aStack) {
        return aEnergyType == TD.Energy.EU ? GTBattery.size(aStack) : 0;
    }

    @Override
    public long getEnergySizeInputMax(TagData aEnergyType, ItemStack aStack) {
        return aEnergyType == TD.Energy.EU ? GTBattery.sizeMax(aStack) : 0;
    }

    @Override
    public long getEnergySizeOutputMin(TagData aEnergyType, ItemStack aStack) {
        return 0;
    }

    @Override
    public long getEnergySizeOutputRecommended(TagData aEnergyType, ItemStack aStack) {
        return 0;
    }

    @Override
    public long getEnergySizeOutputMax(TagData aEnergyType, ItemStack aStack) {
        return 0;
    }
}
