package com.tinkersgt6.compat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.material.MaterialRegistry;
import com.tinkersgt6.util.MixinSupport;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregapi.data.IL;
import gregapi.data.LH;
import gregapi.data.MD;
import gregapi.data.TD;
import gregapi.oredict.OreDictMaterial;
import gregapi.util.ST;
import tconstruct.library.tools.ToolCore;

/**
 * MazeBreaker, GregTech's Twilight Forest material trait, for TConstruct tools.
 *
 * <p>
 * GregTech gives the trait to a handful of materials and reads it in two places: {@code MultiItemTool.getDigSpeed}
 * multiplies the mining speed on Mazestone, Maze Hedge and Towerwood by 40, and {@code onBlockDestroyed} divides the
 * durability cost by the same 40 - or multiplies it by 16 for a tool without the trait, which is why these three blocks
 * chew through ordinary tools. TConstruct derives neither its speed nor its durability the way GregTech does, so none
 * of that transfers, and there is no hook an addon could use anyway: {@code HarvestTool.isEffective},
 * {@code calculateStrength} and {@code breakSpeedModifier} are subclass overrides, and {@code ActiveToolMod} has
 * neither a mining-speed nor a per-block callback.
 * </p>
 *
 * <p>
 * Forge itself has everything needed, so two events do the job:
 * </p>
 * <ul>
 * <li>{@link PlayerEvent.BreakSpeed} applies the speed multiplier.</li>
 * <li>{@link BlockEvent.BreakEvent} hands out the loot in the one case where vanilla throws it away. When a tool's
 * harvest level is below what the block asks for, {@code ItemInWorldManager} removes the block and skips
 * {@code harvestBlock} - the block disappears and nothing drops. Being too weak does not make it unbreakable,
 * {@code ForgeHooks.blockStrength} only divides the speed by 100 instead of 30, it merely takes longer and then pays
 * nothing. GregTech never hits this because its own tools answer {@code canHarvestBlock} with the tool quality, but a
 * TConstruct tool whose head is made of one of these materials can.</li>
 * </ul>
 *
 * <p>
 * Neither event touches durability, so the tool still pays what a normal block break costs; the division by 40 that
 * GregTech applies there is deliberately not reproduced, because TConstruct charges one durability point per block
 * regardless of hardness and a fractional point is not expressible.
 * </p>
 */
public class MazeBreakerCompat {

    /** Maze block -> the tooltip GregTech shows for it. Resolved once Twilight Forest is there. */
    private static final Map<Block, String> MAZE_BLOCKS = new LinkedHashMap<>();
    private static boolean blocksResolved = false;

    /** TConstruct material ID -> head material carries the trait; the BreakSpeed handler runs every tick. */
    private static final Map<Integer, Boolean> HEADS = new HashMap<>();

    /**
     * Whether the mining speed comes from our own mixin into {@code HarvestTool.getDigSpeed}.
     *
     * <p>
     * When it does, the {@link PlayerEvent.BreakSpeed} handler below must stay unregistered: Forge raises that event
     * with
     * the speed {@code getDigSpeed} returned, so applying the multiplier twice would hand out x1600 instead of x40.
     * GTNH
     * Mixins being present is used as the signal - it is the module that queues the mixin, and when it is missing
     * nothing
     * has transformed TConstruct, so the event has to do the job on its own.
     * </p>
     */
    private static boolean speedComesFromMixin() {
        return MixinSupport.lateMixins();
    }

    public static void register() {
        if (!MD.TF.mLoaded || !TGConfig.mazeBreaker()) return;

        MazeBreakerCompat instance = new MazeBreakerCompat();
        MinecraftForge.EVENT_BUS.register(instance);

        boolean byMixin = speedComesFromMixin();
        if (!byMixin) MinecraftForge.EVENT_BUS.register(new SpeedFallback());

        TinkersGT6Log.info(
            "MazeBreaker enabled for " + mazeBlocks().size()
                + " Twilight Forest block(s) at x"
                + TGConfig.mazeBreakerSpeedMultiplier()
                + (byMixin ? " (speed applied inside TConstruct)." : " (speed applied from the break speed event).")
                + (TGConfig.mazeBreakerDrops() ? " Keeps their drops." : " Drops left to vanilla."));
    }

    /**
     * Only used when no mixin loader is installed. Kept separate so that the event listener is simply never created
     * when
     * the mixin is doing the work.
     */
    private static final class SpeedFallback {

        @SubscribeEvent
        public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            if (event == null || event.entityPlayer == null) return;

            ItemStack stack = event.entityPlayer.getCurrentEquippedItem();
            if (!hasMazeBreaker(stack)) return;
            if (!isMazeBlock(event.block)) return;

            float multiplier = speedMultiplier();
            if (multiplier <= 1.0F) return;
            event.newSpeed = event.originalSpeed * multiplier;
        }
    }

    /* ------------------------------------------------------------------ */
    /* mining speed */
    /* ------------------------------------------------------------------ */

    /** The configured multiplier, as a float the mixin and the fallback can both use. */
    public static float speedMultiplier() {
        return (float) TGConfig.mazeBreakerSpeedMultiplier();
    }

    /* ------------------------------------------------------------------ */
    /* drops */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!TGConfig.mazeBreakerDrops()) return;

        // Someone else already refused this break - a protection or claim mod, most likely. Breaking the block after
        // them, which is what cancelling and re-doing it would amount to, would defeat whatever they decided.
        if (event.isCanceled()) return;

        World world = event.world;
        if (world == null || world.isRemote) return; // the server owns drops and blocks

        EntityPlayer player = event.getPlayer();
        if (player == null || player.capabilities.isCreativeMode) return; // creative already keeps everything

        Block block = event.block;
        if (!isMazeBlock(block)) return;

        // Only step in where vanilla would quietly lose the loot.
        if (ForgeHooks.canHarvestBlock(block, player, event.blockMetadata)) return;

        ItemStack stack = player.getCurrentEquippedItem();
        if (!hasMazeBreaker(stack)) return;

        event.setCanceled(true);
        harvest(world, player, stack, block, event.blockMetadata, event.x, event.y, event.z, event.getExpToDrop());
    }

    /**
     * Removes the block and pays out what a successful harvest would have given.
     *
     * <p>
     * Everything here follows {@code ItemInWorldManager.tryHarvestBlock} - including the order - except that the block
     * is
     * removed with {@code canHarvest = true} even though vanilla would have passed {@code false}. Tool damage and the
     * {@code onBlockStartBreak} veto come first because that is what vanilla does before touching the block, and both
     * are what other addons hook into.
     * </p>
     */
    private static void harvest(World world, EntityPlayer player, ItemStack stack, Block block, int meta, int x, int y,
        int z, int expFromEvent) {
        if (
            stack != null && stack.getItem()
                .onBlockStartBreak(stack, x, y, z, player)
        ) return;

        world.playAuxSFXAtEntity(player, 2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));

        // Charge the tool exactly like a normal block break would.
        if (stack != null) {
            stack.func_150999_a(world, block, x, y, z, player);
            if (stack.stackSize <= 0) {
                player.destroyCurrentEquippedItem();
                stack = null;
            }
        }

        block.onBlockHarvested(world, x, y, z, meta, player);
        if (!block.removedByPlayer(world, player, x, y, z, true)) return;

        block.onBlockDestroyedByPlayer(world, x, y, z, meta);
        block.harvestBlock(world, player, x, y, z, meta);

        // GregTech's own axe hands a spare hedge to whoever breaks it without silk touch.
        if (IL.TF_Mazehedge.equal(block) && !EnchantmentHelper.getSilkTouchModifier(player)) {
            ItemStack spare = IL.TF_Mazehedge.get(1);
            if (ST.valid(spare)) ST.give(player, spare, world, x, y, z);
        }

        // The event zeroed this because it was told the tool cannot harvest the block.
        int exp = expFromEvent;
        if (exp <= 0 && !block.canSilkHarvest(world, player, x, y, z, meta)) {
            exp = block.getExpDrop(world, meta, EnchantmentHelper.getFortuneModifier(player));
        }
        if (exp > 0) block.dropXpOnBlockBreak(world, x, y, z, exp);
    }

    /* ------------------------------------------------------------------ */
    /* tooltip */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (event == null || event.toolTip == null) return;
        if (!hasMazeBreaker(event.itemStack)) return;

        for (Map.Entry<Block, String> entry : mazeBlocks().entrySet()) {
            if (canDig(event.itemStack, entry.getKey())) {
                event.toolTip.add(EnumChatFormatting.LIGHT_PURPLE + LH.get(entry.getValue()));
            }
        }
    }

    /**
     * Whether the tool is strong enough for those blocks' own requirements.
     *
     * <p>
     * GregTech asks {@code canHarvestBlock}, which for its tools means "the tool quality covers the block's harvest
     * level". TConstruct keeps that same number in {@code InfiTool.HarvestLevel} and answers Forge's query with it, so
     * comparing the two directly gives the same answer.
     * </p>
     */
    private static boolean canDig(ItemStack stack, Block block) {
        if (block == null || stack == null || stack.stackTagCompound == null) return false;
        int required = block.getHarvestLevel(0);
        if (required < 0) return true; // the block asks for nothing in particular
        int harvestLevel = stack.stackTagCompound.getCompoundTag("InfiTool")
            .getInteger("HarvestLevel");
        return harvestLevel >= required;
    }

    /* ------------------------------------------------------------------ */
    /* identification */
    /* ------------------------------------------------------------------ */

    public static boolean isMazeBlock(Block block) {
        return block != null && mazeBlocks().containsKey(block);
    }

    /**
     * Resolved lazily: GregTech only knows these blocks once Twilight Forest has registered them, which is long after
     * our own preInit. An empty result keeps the lookup retrying on the next event instead of caching "nothing exists".
     */
    private static Map<Block, String> mazeBlocks() {
        if (!blocksResolved) {
            addBlock(IL.TF_Mazestone.get(1), LH.TOOLTIP_TWILIGHT_MAZE_STONE_BREAKING);
            addBlock(IL.TF_Mazehedge.get(1), LH.TOOLTIP_TWILIGHT_MAZE_HEDGE_BREAKING);
            addBlock(IL.TF_Towerwood.get(1), LH.TOOLTIP_TWILIGHT_TOWER_WOOD_BREAKING);
            blocksResolved = !MAZE_BLOCKS.isEmpty();
        }
        return MAZE_BLOCKS;
    }

    private static void addBlock(ItemStack source, String tooltipKey) {
        Block block = ST.block(source);
        if (block != null) MAZE_BLOCKS.put(block, tooltipKey);
    }

    /**
     * @return true when the tool's head material carries the trait. Cached per TConstruct material ID because
     *         {@link PlayerEvent.BreakSpeed} fires once per tick per mining player.
     */
    public static boolean hasMazeBreaker(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ToolCore)) return false;
        if (stack.stackTagCompound == null) return false;

        int head = stack.stackTagCompound.getCompoundTag("InfiTool")
            .getInteger("Head");
        Boolean cached = HEADS.get(head);
        if (cached != null) return cached;

        OreDictMaterial material = MaterialRegistry.fromID(head);
        boolean result = material != null && material.contains(TD.Properties.MAZEBREAKER);
        HEADS.put(head, result);
        return result;
    }
}
