package com.tinkersgt6.power;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregapi.oredict.OreDictMaterial;
import gregapi.util.UT;
import tconstruct.library.ActiveToolMod;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.tools.ToolCore;

/**
 * Spends GregTech EU instead of TConstruct durability.
 *
 * <p>
 * {@code AbilityHelper.damageTool} is the single funnel every TConstruct cost goes through - mining a block, hitting a
 * mob, hoeing soil, firing a crossbow, even our own forwarded GregTech right click interactions - and before applying
 * durability it asks every registered {@link ActiveToolMod} whether one of them wants the hit. Returning true means
 * "handled", and no durability is taken. That one hook therefore covers all uses with no per-tool patching.
 * </p>
 *
 * <p>
 * Two ways of paying are available and picked in the config, because they answer different pack designs:
 * </p>
 * <ul>
 * <li><b>{@code ENERGY_ONLY}</b> - the charge pays, the tool never wears. What TConstruct's own Flux upgrade does.</li>
 * <li><b>{@code ENERGY_AND_DURABILITY}</b> - the charge pays and, on top of that, durability is taken with the very odds
 * GregTech uses for its electric tools: {@code MultiItemTool.doDamage} applies the durability roll only when
 * {@code nextInt(max(10, toolQuality * 20)) == 0}, so a good head material rarely wears while a poor one often does.
 * This is the default because it is what GregTech does.</li>
 * </ul>
 *
 * <p>
 * Without enough charge the tool falls back completely to its own durability rather than refusing to work. GregTech's
 * own electric tools simply do nothing when empty, which for a TConstruct tool would read as "broken" with no way to
 * tell why.
 * </p>
 */
public class GTBatteryToolMod extends ActiveToolMod {

    private static final Random RANDOM = new Random();

    /** TConstruct material ID -> the denominator of GregTech's durability roll; asked for on every hit. */
    private static final Map<Integer, Integer> ROLLS = new HashMap<>();

    public static void register() {
        if (!TGConfig.batteryUpgrade()) return;

        GTBatteryToolMod instance = new GTBatteryToolMod();
        TConstructRegistry.registerActiveToolMod(instance);
        MinecraftForge.EVENT_BUS.register(instance);
        GTBatteryModifier.register();

        String mode = TGConfig.batteryDurabilityMode();
        TinkersGT6Log.info(
            "GregTech battery upgrade registered: tools pay "
                + TGConfig.batteryEuPerDurability()
                + " EU per durability point, mode "
                + mode
                + (TGConfig.batteryRechargeFromHotbar() ? ", charging from EU batteries in the hotbar." : "."));
    }

    /* ------------------------------------------------------------------ */
    /* paying for a use */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean damageTool(ItemStack stack, int damage, EntityLivingBase entity) {
        if (damage <= 0) return false; // healing is not ours to pay for
        if (!GTBattery.installed(stack)) return false;

        long cost = Math.round(damage * TGConfig.batteryEuPerDurability());
        if (cost <= 0) return false; // configured to be free, leave it to durability

        if (TGConfig.batteryRechargeFromHotbar() && entity instanceof EntityPlayer) {
            // A use is the one moment we know the tool is being held, so top it up before asking for payment.
            GTBattery.refillFromHotbar(
                stack,
                (EntityPlayer) entity,
                entity.worldObj,
                TGConfig.batteryRechargePacketsPerTick());
        }

        if (!GTBattery.take(stack, cost)) return false; // empty battery: an ordinary tool again

        // true = "handled", skip durability. Conversely false lets vanilla take its durability as usual.
        return !wearsToolToo(stack);
    }

    /* ------------------------------------------------------------------ */
    /* idling */
    /* ------------------------------------------------------------------ */

    /**
     * Ticks while the tool is held, so carrying charged batteries keeps it topped up between uses.
     *
     * <p>
     * The check itself only happens once per {@code batteryRechargeIntervalTicks}, and nothing is written unless a
     * battery actually hands over EU, so an idle tool costs nothing but a comparison per tick.
     * </p>
     */
    @Override
    public void updateTool(ToolCore tool, ItemStack stack, World world, Entity entity) {
        if (!TGConfig.batteryRechargeFromHotbar()) return;
        if (!(entity instanceof EntityPlayer)) return;
        if (world == null || world.isRemote) return; // the server owns the charge
        if (!GTBattery.installed(stack)) return;

        int interval = TGConfig.batteryRechargeIntervalTicks();
        if (interval > 1 && world.getTotalWorldTime() % interval != 0) return;

        GTBattery.refillFromHotbar(stack, (EntityPlayer) entity, world, TGConfig.batteryRechargePacketsPerTick());
    }

    /* ------------------------------------------------------------------ */
    /* durability roll */
    /* ------------------------------------------------------------------ */

    /**
     * Whether this particular use also costs durability.
     *
     * <p>
     * Copied from GregTech, where {@code MultiItemTool.doDamage} takes durability with a probability of
     * {@code 1 / max(10, toolQuality * 20)} per use and spends charge every time. Quality here is the head material's,
     * which is what decides the tool's harvest level and therefore the only sensible stand-in for GregTech's tool
     * quality.
     * </p>
     */
    private static boolean wearsToolToo(ItemStack stack) {
        if (!"ENERGY_AND_DURABILITY".equalsIgnoreCase(TGConfig.batteryDurabilityMode())) return false;
        return RANDOM.nextInt(durabilityRoll(stack)) == 0;
    }

    private static int durabilityRoll(ItemStack stack) {
        NBTTagCompound tags = stack.getTagCompound();
        if (tags == null) return 20;

        int head = tags.getCompoundTag("InfiTool")
            .getInteger("Head");
        Integer cached = ROLLS.get(head);
        if (cached != null) return cached;

        OreDictMaterial material = MaterialRegistry.fromID(head);
        int quality = material == null ? 1 : material.mToolQuality;
        if (quality < 1) quality = 1;
        int roll = Math.max(10, quality * 20);
        ROLLS.put(head, roll);
        return roll;
    }

    /* ------------------------------------------------------------------ */
    /* tooltip */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (event == null || event.toolTip == null) return;

        ItemStack stack = event.itemStack;
        if (!GTBattery.installed(stack)) return;

        long size = GTBattery.size(stack);
        String line = EnumChatFormatting.BLUE + UT.Code.makeString(GTBattery.stored(stack))
            + " / "
            + UT.Code.makeString(GTBattery.capacity(stack))
            + " EU";
        if (size > 0) line += EnumChatFormatting.DARK_GRAY + " - Size: " + UT.Code.makeString(size) + " EU/packet";
        event.toolTip.add(line);

        if (TGConfig.batteryRechargeFromHotbar()) {
            event.toolTip.add(
                EnumChatFormatting.DARK_GRAY + "Charges from EU batteries carried in the hotbar ("
                    + EnumChatFormatting.GRAY
                    + "Size "
                    + UT.Code.makeString(size)
                    + EnumChatFormatting.DARK_GRAY
                    + ")");
        }
    }
}
