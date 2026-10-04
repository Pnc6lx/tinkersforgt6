package com.tinkersgt6.power;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import gregapi.code.TagData;
import gregapi.data.IL;
import gregapi.data.TD;
import gregapi.item.IItemEnergy;
import gregapi.util.ST;
import tconstruct.library.tools.ToolCore;

/**
 * The charge a battery-upgraded TConstruct tool carries, and how a GT6 battery is recognised.
 *
 * <p>
 * GregTech exposes everything about an energy carrying stack through {@link IItemEnergy}, which every GT6 energy item
 * implements - both the classic {@code MultiItem} gadgets and the later multi-tile-entity ones such as the Power Cells,
 * whose item delegates straight to the tile entity. Nothing here inspects item classes: capacity, charge and packet
 * size
 * come from the interface, so a battery from another addition implementing the same interface works too.
 * </p>
 *
 * <p>
 * Three numbers describe an installation, all inherited from the battery that was built in:
 * </p>
 * <ul>
 * <li><b>capacity</b> - how much EU fits, {@code getEnergyCapacity}.</li>
 * <li><b>size</b> - the recommended input packet size, which in GT6 <em>is</em> the voltage: 32 for LV, 128 for MV and
 * so
 * on. A source battery only accepts a transfer inside {@code [size/2, size*2]}, so this single number is what makes a
 * tool "an LV tool" and keeps an LV charger from filling a tool built around something else.</li>
 * <li>the charge itself, which starts as whatever was inside the battery at the moment it was built in.</li>
 * </ul>
 *
 * <p>
 * The NBT keys are deliberately <em>not</em> {@code Energy}: that one belongs to TConstruct's own Redstone Flux
 * upgrade,
 * and writing it would hand every durability hit to {@code AbilityHelper.damageEnergyTool}, which decides its own price
 * per hit and pays nothing to us.
 * </p>
 */
public final class GTBattery {

    /** {@code InfiTool} flag set by {@link GTBatteryModifier}. */
    public static final String KEY_INSTALLED = "GTEnergy";

    private static final String NBT_STORED = "TG6.EU";
    private static final String NBT_CAPACITY = "TG6.EUCapacity";
    private static final String NBT_SIZE = "TG6.EUSize";

    /** Batteries that are deliberately not accepted yet; see {@code docs/gt-battery.md}. */
    public enum Kind {
        /** A rechargeable EU battery - everything the upgrade accepts today. */
        SUPPORTED,
        /** Carries energy, but through no interface GregTech exposes to us. */
        NOT_ENERGY_ITEM,
        /** Single use ({@code EnergyStat.makeSUBattery}): it can be drained but not charged. */
        SINGLE_USE,
        /** Power Cell (Hydrogen). */
        HYDROGEN_FUEL_CELL,
        /** Aneutronic Fusion Power Cell. */
        ANEUTRONIC_FUSION,
        /** Zero-Point-Module - stores QU, not EU. */
        ZPM,
        /** Stores Light energy (LU) instead of power. */
        LIGHT_ENERGY,
        /** Some other energy type GregTech knows. */
        OTHER_ENERGY_TYPE,
        /** Registered as an EU container but reports no capacity. */
        NO_CAPACITY
    }

    /** What a battery is and what it can hold. Charge itself changes too often to sit in here. */
    public static final class Spec {

        public final Kind kind;
        public final long capacity;
        public final long size;

        private Spec(Kind kind, long capacity, long size) {
            this.kind = kind;
            this.capacity = capacity;
            this.size = size;
        }

        public boolean usable() {
            return kind == Kind.SUPPORTED && capacity > 0 && size > 0;
        }
    }

    private static final Map<String, Spec> CACHE = new HashMap<>();

    /* ------------------------------------------------------------------ */
    /* recognising batteries */
    /* ------------------------------------------------------------------ */

    /**
     * Classifies an ItemStack once and remembers it per item and meta - the Tool Station asks for every arrangement of
     * its slots each time anything changes, and asking {@code IItemEnergy} costs a tile entity round trip for anything
     * that is a multi-tile-entity item.
     */
    public static Spec inspect(ItemStack stack) {
        Item item = stack == null ? null : stack.getItem();
        if (item == null || ST.invalid(stack)) return spec(Kind.NOT_ENERGY_ITEM, 0, 0);

        String cacheKey = Item.getIdFromItem(item) + ":" + ST.meta(stack);
        Spec cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        Spec spec = classify(stack, item);
        CACHE.put(cacheKey, spec);
        return spec;
    }

    private static Spec classify(ItemStack stack, Item item) {
        // A battery-upgraded tool is an IItemEnergy itself (see ToolCoreEnergyMixin), so without this a second tool in
        // the hotbar would look like a battery and be drained to refill the one in hand.
        if (item instanceof ToolCore) return spec(Kind.NOT_ENERGY_ITEM, 0, 0);

        // GregTech's own identifiers are checked first: these four are easy to recognise and all of them are on hold.
        if (IL.ZPM.equal(stack, true, true)) return spec(Kind.ZPM, 0, 0);
        if (IL.Power_Cell_Empty.equal(stack, true, true) || IL.Power_Cell_H.equal(stack, true, true))
            return spec(Kind.HYDROGEN_FUEL_CELL, 0, 0);
        if (IL.Aneutronic_Fusion_Empty.equal(stack, true, true) || IL.Aneutronic_Fusion_He3.equal(stack, true, true))
            return spec(Kind.ANEUTRONIC_FUSION, 0, 0);

        if (!(item instanceof IItemEnergy)) return spec(Kind.NOT_ENERGY_ITEM, 0, 0);

        IItemEnergy energy = (IItemEnergy) item;
        try {
            Collection<TagData> types = energy.getEnergyTypes(stack);
            if (types == null || !types.contains(TD.Energy.EU)) {
                if (types != null && types.contains(TD.Energy.LU)) return spec(Kind.LIGHT_ENERGY, 0, 0);
                return spec(Kind.OTHER_ENERGY_TYPE, 0, 0);
            }

            long capacity = energy.getEnergyCapacity(TD.Energy.EU, stack);
            if (capacity <= 0) return spec(Kind.NO_CAPACITY, 0, 0);

            long size = energy.getEnergySizeInputRecommended(TD.Energy.EU, stack);
            if (size <= 0) size = capacity; // Some implementations only ever recommend to fill it in one go.

            // false = asked whether it accepts energy. A battery that cannot accept it again is a single use cell, and
            // there is nothing to refill here.
            if (!energy.isEnergyType(TD.Energy.EU, stack, false)) return spec(Kind.SINGLE_USE, capacity, size);

            return spec(Kind.SUPPORTED, capacity, size);
        } catch (RuntimeException e) {
            // A felmodded item that claims to be an energy container and then throws on the second question.
            return spec(Kind.NOT_ENERGY_ITEM, 0, 0);
        }
    }

    private static Spec spec(Kind kind, long capacity, long size) {
        return new Spec(kind, capacity, size);
    }

    /** Charge inside the battery itself, at this moment. */
    public static long chargeOf(ItemStack battery) {
        if (ST.invalid(battery)) return 0;
        Item item = battery.getItem();
        if (!(item instanceof IItemEnergy)) return 0;
        try {
            return ((IItemEnergy) item).getEnergyStored(TD.Energy.EU, battery);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /* the installed battery */
    /* ------------------------------------------------------------------ */

    public static boolean installed(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return false;
        if (
            !stack.stackTagCompound.getCompoundTag("InfiTool")
                .getBoolean(KEY_INSTALLED)
        ) return false;
        return stack.stackTagCompound.getLong(NBT_CAPACITY) > 0;
    }

    public static long stored(ItemStack stack) {
        return stack == null || stack.stackTagCompound == null ? 0 : stack.stackTagCompound.getLong(NBT_STORED);
    }

    public static long capacity(ItemStack stack) {
        return stack == null || stack.stackTagCompound == null ? 0 : stack.stackTagCompound.getLong(NBT_CAPACITY);
    }

    /** Packet size the tool was built for - the voltage it can be charged with. */
    public static long size(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return 0;
        long size = stack.stackTagCompound.getLong(NBT_SIZE);
        return size > 0 ? size : 0;
    }

    /**
     * GregTech's own rule, copied from {@code EnergyStat}: a battery accepts packets between half and twice its size,
     * and never below one EU.
     */
    public static long sizeMin(ItemStack stack) {
        long size = size(stack);
        return size <= 0 ? 0 : size <= 8 ? 1 : size / 2;
    }

    public static long sizeMax(ItemStack stack) {
        long size = size(stack);
        return size <= 0 ? 0 : size * 2;
    }

    public static boolean accepts(ItemStack stack, long size) {
        return installed(stack) && stack.stackSize == 1
            && size >= sizeMin(stack)
            && size <= sizeMax(stack)
            && stored(stack) < capacity(stack);
    }

    /**
     * The receiving half of {@code IItemEnergy}: how many packets of {@code size} EU fit, and - only when asked -
     * writing
     * them in. This is what a GregTech battery box calls once per second for every item in its inventory.
     *
     * @return the number of packets taken in, which GregTech multiplies by the packet size to bill the machine.
     */
    public static long inject(ItemStack stack, long size, long packets, boolean doInject) {
        if (!accepts(stack, size) || size <= 0 || packets <= 0) return 0;

        long room = (capacity(stack) - stored(stack)) / size;
        long taken = Math.min(packets, room);
        if (taken <= 0) return 0;

        if (doInject) setStored(stack, stored(stack) + taken * size);
        return taken;
    }

    public static void install(ItemStack tool, long capacity, long size, long charge) {
        tool.stackTagCompound.setLong(NBT_CAPACITY, capacity);
        tool.stackTagCompound.setLong(NBT_SIZE, size);
        setStored(tool, charge);
    }

    public static void setStored(ItemStack stack, long amount) {
        long capacity = capacity(stack);
        if (amount < 0) amount = 0;
        if (capacity > 0 && amount > capacity) amount = capacity;
        stack.stackTagCompound.setLong(NBT_STORED, amount);
    }

    public static boolean take(ItemStack stack, long eu) {
        long stored = stored(stack);
        if (stored < eu) return false;
        setStored(stack, stored - eu);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* refilling */
    /* ------------------------------------------------------------------ */

    /**
     * Pulls EU out of chargeable batteries in the player's hotbar, at most {@code maxPackets} packets in total.
     *
     * <p>
     * This is what TConstruct's own Flux upgrade does with Redstone Flux cells, and it is the only way to charge these
     * tools: a GT6 charger looks at {@code IItemEnergy} implementations, and a TConstruct tool is one fixed class we
     * cannot extend. Packets are asked for at the tool's own size, so only batteries of the voltage the tool was built
     * around can actually pay.
     * </p>
     *
     * @return how much EU moved in.
     */
    public static long refillFromHotbar(ItemStack tool, EntityPlayer player, World world, long maxPackets) {
        long capacity = capacity(tool);
        long size = size(tool);
        if (capacity <= 0 || size <= 0 || maxPackets <= 0) return 0;

        long missing = capacity - stored(tool);
        if (missing <= 0) return 0;

        long budget = Math.min(maxPackets, (missing + size - 1) / size);
        long moved = 0;

        for (int slot = 0; slot < 9 && budget > 0; slot++) {
            ItemStack battery = player.inventory.mainInventory[slot];
            if (ST.invalid(battery) || battery == tool) continue;

            Spec spec = inspect(battery);
            if (!spec.usable()) continue;

            IItemEnergy energy = (IItemEnergy) battery.getItem();
            long packets;
            try {
                packets = energy.doEnergyExtraction(
                    TD.Energy.EU,
                    battery,
                    size,
                    budget,
                    player.inventory,
                    world,
                    (int) player.posX,
                    (int) player.posY,
                    (int) player.posZ,
                    true);
            } catch (RuntimeException e) {
                continue;
            }
            if (packets <= 0) continue; // wrong voltage for us, or a battery stacked beyond one per slot

            moved += packets;
            budget -= packets;
        }

        if (moved > 0) setStored(tool, stored(tool) + moved * size);
        return moved * size;
    }

    private GTBattery() {}
}
