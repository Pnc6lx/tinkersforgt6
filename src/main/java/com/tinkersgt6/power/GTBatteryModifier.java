package com.tinkersgt6.power;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.tinkersgt6.config.TGConfig;

import gregapi.util.ST;
import tconstruct.library.crafting.ModifyBuilder;
import tconstruct.library.modifier.IModifyable;
import tconstruct.library.modifier.ItemModifier;
import tconstruct.library.tools.ToolCore;

/**
 * Builds a GregTech battery into a TConstruct tool, at the cost of one modifier slot.
 *
 * <p>
 * The one already made by TConstruct, {@code ModFlux}, does this for Redstone Flux: it accepts any CoFH
 * {@code IEnergyContainerItem} whose capacity is below a tenth of the tool's durability, copies capacity, charge and
 * transfer rates into the tool's root NBT as {@code Energy / EnergyMax / EnergyReceiveRate / EnergyExtractionRate}, and
 * from then on {@code AbilityHelper.damageTool} prefers paying those hits out of the charge instead of the tool's own
 * durability. Everything afterwards is TConstruct's doing - including calling {@code ToolCore.receiveEnergy} and, for
 * CoFH users, refilling from any charged cell in the hotbar.
 * </p>
 *
 * <p>
 * The shape of ours is the same - one {@link ItemModifier}, one upgrade slot, stats read off whatever battery is put in
 * next to the tool - but nothing downstream can be reused: every price in GregTech is quoted in EU packets of a given
 * voltage, which is a different unit and a different rule book than Redstone Flux. So the charge lives under our own
 * keys (see {@link GTBattery}) and the spending is done by {@link GTBatteryToolMod}.
 * </p>
 *
 * <p>
 * Anything incompatible instead of a battery simply fails to match, which is how TConstruct itself handles the RF
 * version: the Tool Station silently shows no result. The kinds we recognise but have declared not-supported-yet are
 * listed in {@code docs/gt-battery.md}.
 * </p>
 */
public class GTBatteryModifier extends ItemModifier {

    /** One modifier slot, same as TConstruct charges for its own battery upgrade. */
    public static final int MODIFIERS_REQUIRED = 1;

    /**
     * Sprite index 9 is the "electric tool" overlay TConstruct uses for its Flux upgrade. Reused here because the tool
     * gains exactly that: it runs on electricity.
     */
    public GTBatteryModifier() {
        super(new ItemStack[0], 9, GTBattery.KEY_INSTALLED);
    }

    public static void register() {
        if (!TGConfig.batteryUpgrade()) return;
        ModifyBuilder.registerModifier(new GTBatteryModifier());
    }

    /* ------------------------------------------------------------------ */
    /* matching */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean matches(ItemStack[] input, ItemStack tool) {
        if (!TGConfig.batteryUpgrade() || tool == null) return false;

        Item item = tool.getItem();
        if (!(item instanceof ToolCore) || !(item instanceof IModifyable)) return false;

        NBTTagCompound root = tool.getTagCompound();
        if (root == null) return false;

        // TConstruct's own RF upgrade is already in there - two batteries on one tool would spend whichever its own
        // code happens to check first.
        if (root.hasKey("Energy")) return false;

        NBTTagCompound tags = root.getCompoundTag("InfiTool");
        if (tags.getBoolean("Broken")) return false;

        // Weapons like the bow have no durability to pay with, same reasoning that made TConstruct exclude them.
        for (String trait : ((IModifyable) item).getTraits()) {
            if ("ammo".equals(trait)) return false;
        }

        GTBattery.Spec spec = battery(input);
        if (spec == null || !spec.usable()) return false;
        if (spec.capacity < (long) TGConfig.batteryMinCapacity()) return false;

        if (tags.getBoolean(key)) {
            // Already installed. Swapping in a bigger battery is allowed (and free), a smaller one is not.
            return TGConfig.batteryAllowUpgrade() && spec.capacity > GTBattery.capacity(tool);
        }
        return tags.getInteger("Modifiers") >= MODIFIERS_REQUIRED;
    }

    /**
     * @return the battery in the modifier slots, or null when there is none or more than one - with two there is no way
     *         to know which one the player meant, exactly the problem TConstruct's own version refuses to guess at.
     */
    private static GTBattery.Spec battery(ItemStack[] input) {
        if (input == null) return null;

        GTBattery.Spec found = null;
        for (ItemStack stack : input) {
            if (ST.invalid(stack)) continue;
            GTBattery.Spec spec = GTBattery.inspect(stack);
            if (!spec.usable()) continue;
            if (found != null) return null;
            found = spec;
        }
        return found;
    }

    /* ------------------------------------------------------------------ */
    /* applying */
    /* ------------------------------------------------------------------ */

    @Override
    public void modify(ItemStack[] input, ItemStack tool) {
        NBTTagCompound root = tool.getTagCompound();
        NBTTagCompound tags = root.getCompoundTag("InfiTool");

        boolean upgrading = tags.getBoolean(key);
        long previous = upgrading ? GTBattery.stored(tool) : 0;

        ItemStack battery = firstUsable(input);
        if (battery == null) return;
        GTBattery.Spec spec = GTBattery.inspect(battery);

        GTBattery.install(tool, spec.capacity, spec.size, previous + GTBattery.chargeOf(battery));
        tags.setBoolean(key, true);

        if (!upgrading) {
            tags.setInteger("Modifiers", tags.getInteger("Modifiers") - MODIFIERS_REQUIRED);
            addToolTip(
                tool,
                EnumChatFormatting.YELLOW + "GregTech Battery",
                EnumChatFormatting.YELLOW + "GregTech Battery");
        }
    }

    private static ItemStack firstUsable(ItemStack[] input) {
        if (input == null) return null;
        for (ItemStack stack : input) {
            if (ST.invalid(stack)) continue;
            if (
                GTBattery.inspect(stack)
                    .usable()
            ) return stack;
        }
        return null;
    }
}
