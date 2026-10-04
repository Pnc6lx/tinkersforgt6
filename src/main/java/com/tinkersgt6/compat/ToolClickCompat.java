package com.tinkersgt6.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tinkersgt6.config.TGConfig;
import com.tinkersgt6.util.TinkersGT6Log;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregapi.block.IBlockToolable;
import gregapi.block.tree.BlockBaseBeam;
import gregapi.data.CS;
import gregapi.util.ST;
import gregapi.util.UT;
import gregapi.wooddict.BeamEntry;
import gregapi.wooddict.WoodDictionary;
import gregapi.wooddict.WoodEntry;
import tconstruct.library.tools.AbilityHelper;
import tconstruct.library.tools.ToolCore;
import tconstruct.tools.TinkerTools;

/**
 * Lets TConstruct tools right-click GregTech blocks the way a GregTech tool does.
 *
 * <p>
 * GregTech never looks at the ItemStack when deciding what a tool does - it hands a tool <em>name</em> (a plain
 * lowercase String) to the clicked Block, which then decides what to do. {@link gregapi.block.IBlockToolable.Util}
 * performs that hand-off, so forwarding it costs nothing more than knowing which name to claim, and the GregTech side
 * stays untouched: every condition (which face, whether the block is sneaking, whether the Crucible has cooled) and
 * every effect still lives in GregTech's own tile entities.
 * </p>
 *
 * <p>
 * Two things come out of it:
 * </p>
 * <ul>
 * <li><b>Tool interactions</b> - the Shovel pulls the slag out of a Crucible, Smeltery or solid Generator, exactly
 * like {@code MultiTileEntityCrucible.java:562} does for a GregTech shovel, and the Chisel carves a Mold
 * ({@code MultiTileEntityMold.java:328}) or cleans the coke off a Boiler.</li>
 * <li><b>Logs to beams</b> - the Hatchet and the Lumber Axe turn a log into the GregTech beam <em>of that wood</em>.
 * This one is deliberately not forwarded: GregTech's own axe falls back to a single generic beam for any log whose
 * tree it does not have a table entry for, while its wood dictionary knows the exact beam per wood type.</li>
 * </ul>
 *
 * <p>
 * The aim vector is re-traced here because Forge's 1.7.10 {@link PlayerInteractEvent} does not carry the exact spot
 * on the block that was clicked, and both the Mold grid and the Boiler depend on it. Anything GregTech returns is
 * still treated as damage in GregTech units: {@code 10000 = 1} durability point.
 * </p>
 */
public class ToolClickCompat {

    /** How far a vanilla right click reaches. Magic value, but it only ever has to be "far enough". */
    private static final double REACH = 5.0D;

    /** TConstruct tool -> the GregTech tool names it answers to. */
    private static final Map<Item, ClickProfile> PROFILES = new HashMap<>();
    private static boolean profilesBuilt = false;

    /** Tools that get the log -> beam conversion instead of being forwarded. */
    private static final Set<Item> BEAM_TOOLS = new HashSet<>();

    public static void register() {
        if (!TGConfig.toolClickCompat()) return;
        MinecraftForge.EVENT_BUS.register(new ToolClickCompat());
        TinkersGT6Log.info("TConstruct tools answer to GregTech tool interactions (" + describe() + ").");
    }

    private static String describe() {
        buildProfiles();
        StringBuilder names = new StringBuilder();
        if (PROFILES.containsKey(TinkerTools.shovel)) appendName(names, "Shovel");
        if (PROFILES.containsKey(TinkerTools.chisel)) appendName(names, "Chisel");
        if (BEAM_TOOLS.contains(TinkerTools.hatchet)) appendName(names, "Hatchet");
        if (BEAM_TOOLS.contains(TinkerTools.lumberaxe)) appendName(names, "Lumber Axe");
        return names.length() == 0 ? "none found" : names.toString();
    }

    private static void appendName(StringBuilder builder, String name) {
        if (builder.length() > 0) builder.append(", ");
        builder.append(name);
    }

    @SubscribeEvent
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        World world = event.world;
        if (world == null || world.isRemote) return; // the tiles do the work server-side, once

        EntityPlayer player = event.entityPlayer;
        if (player == null) return;

        ItemStack stack = player.inventory.getCurrentItem();
        if (stack == null) return;

        Item item = stack.getItem();
        buildProfiles();

        if (BEAM_TOOLS.contains(item)) {
            if (convertLogToBeam(player, world, event.x, event.y, event.z)) {
                chargeTool(stack, player, 10000);
                event.setCanceled(true);
            }
            return;
        }

        ClickProfile profile = PROFILES.get(item);
        if (profile == null) return;

        Aim aim = aim(player, world, event.x, event.y, event.z, event.face);
        long remaining = remainingDurability(stack);
        long quality = quality(stack);

        for (String tool : profile.tools) {
            List<String> chat = new ArrayList<>();
            long damage;
            try {
                damage = IBlockToolable.Util.onToolClick(
                    tool,
                    remaining,
                    quality,
                    player,
                    chat,
                    player.inventory,
                    player.isSneaking(),
                    stack,
                    world,
                    aim.side,
                    event.x,
                    event.y,
                    event.z,
                    aim.hitX,
                    aim.hitY,
                    aim.hitZ);
            } catch (RuntimeException e) {
                TinkersGT6Log.debug("GregTech rejected tool '" + tool + "': " + e);
                return;
            }
            UT.Entities.sendchat(player, chat, false);
            if (damage <= 0) continue;

            chargeTool(stack, player, damage);
            event.setCanceled(true);
            return;
        }
    }

    /* ------------------------------------------------------------------ */
    /* forwarding */
    /* ------------------------------------------------------------------ */

    /**
     * Converts the durability GregTech asked for into TConstruct durability.
     *
     * <p>
     * GregTech charges in units of 1/10000 durability - a single Mold carve is 10000, pulling the slag out of a
     * Crucible is 500. TConstruct counts whole durability points on tools that only have a few hundred to start with,
     * so anything above zero is rounded up to one point and then scaled by {@code toolClickDamage}.
     * </p>
     */
    private static void chargeTool(ItemStack stack, EntityPlayer player, long gtDamage) {
        if (gtDamage <= 0) return;
        int points = (int) ((gtDamage + 9999L) / 10000L);
        if (points < 1) points = 1;
        points *= TGConfig.toolClickDamage();
        if (points <= 0) return; // configured to be free
        try {
            AbilityHelper.damageTool(stack, points, player, false);
        } catch (RuntimeException e) {
            TinkersGT6Log.debug("Could not charge the tool: " + e);
        }
    }

    /** Durability left in GregTech units, so tiles that care can still say no. */
    private static long remainingDurability(ItemStack stack) {
        if (stack.getItem() instanceof ToolCore) {
            ToolCore core = (ToolCore) stack.getItem();
            int left = core.getMaxDamage(stack) - core.getDamage(stack);
            if (left > 0) return left * 10000L;
        }
        return Long.MAX_VALUE;
    }

    /** Tool quality as GregTech understands it - only ever asked for by tools we do not forward. */
    private static long quality(ItemStack stack) {
        if (stack.stackTagCompound == null) return 1;
        NBTTagCompound tags = stack.stackTagCompound.getCompoundTag("InfiTool");
        int level = tags.hasKey("HarvestLevel") ? tags.getInteger("HarvestLevel") : 1;
        return level > 0 ? level : 1;
    }

    /* ------------------------------------------------------------------ */
    /* logs to beams */
    /* ------------------------------------------------------------------ */

    /**
     * Turns the clicked log into the beam GregTech associates with that exact wood.
     *
     * <p>
     * GregTech builds one {@code WoodEntry} per log, and each entry knows its own beam - oak goes to the oak beam, and
     * only logs without an entry fall back to {@code WoodDictionary.DEFAULT_BEAM}, the generic beam. Those are skipped
     * here on purpose: something the dictionary does not know about is left as a log rather than turned into a beam
     * that belongs to no tree.
     * </p>
     */
    private static boolean convertLogToBeam(EntityPlayer player, World world, int x, int y, int z) {
        if (!TGConfig.toolClickBeams()) return false;

        Block log = world.getBlock(x, y, z);
        if (log == null || log == Blocks.air) return false;

        int meta = world.getBlockMetadata(x, y, z);
        WoodEntry wood = WoodDictionary.WOODS.get(log, meta);
        boolean viaWoodType = false;
        if (wood == null) {
            // A sideways log still carries its wood type in the lower two bits, and that is the entry GregTech stores.
            wood = WoodDictionary.WOODS.get(log, meta & 3);
            viaWoodType = wood != null;
        }
        if (wood == null) return false;

        BeamEntry beam = wood.mBeamEntry;
        if (beam == null || beam == WoodDictionary.DEFAULT_BEAM || ST.invalid(beam.mBeam)) return false;

        Block beamBlock = ST.block(beam.mBeam);
        if (beamBlock == null || beamBlock == Blocks.air) return false;

        int beamMeta = beam.mBeam.getItemDamage();
        if (beamMeta < 0 || beamMeta > 15) beamMeta = 0; // wildcard registrations store hardly a meta at all
        // GregTech's beams keep the pillar direction in the same bits as a vanilla log, so give back the rotation a
        // sideways log had. Only its own blocks do that, third party beams are left alone.
        if (viaWoodType && (meta & 12) != 0 && beamBlock instanceof BlockBaseBeam) {
            beamMeta = (meta & 12) | (beamMeta & 3);
        }
        if (beamBlock == log && beamMeta == meta) return false; // already the beam it should become

        if (!world.setBlock(x, y, z, beamBlock, beamMeta, 3)) return false;

        // GregTech hands bark out for the same conversion, so keep that.
        if (ST.valid(wood.mBark)) {
            ST.give(player, ST.amount(1, wood.mBark), world, x + 0.5, y + 0.5, z + 0.5);
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* aiming */
    /* ------------------------------------------------------------------ */

    /** Side and exact spot of the click - Forge does not give us either, and the Mold grid needs both. */
    private static Aim aim(EntityPlayer player, World world, int x, int y, int z, int face) {
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = start.addVector(look.xCoord * REACH, look.yCoord * REACH, look.zCoord * REACH);
        MovingObjectPosition hit = world.rayTraceBlocks(start, end);

        if (
            hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == x
                && hit.blockY == y
                && hit.blockZ == z
        ) {
            return new Aim(
                (byte) hit.sideHit,
                (float) (hit.hitVec.xCoord - x),
                (float) (hit.hitVec.yCoord - y),
                (float) (hit.hitVec.zCoord - z));
        }
        return fallback(face);
    }

    /** Middle of the clicked face, used when the trace and the packet disagree. */
    private static Aim fallback(int face) {
        byte side = face >= 0 && face <= 5 ? (byte) face : 6; // 6 is what GregTech reads as "no specific side"
        switch (face) {
            case 0:
                return new Aim(side, 0.5F, 0.0F, 0.5F);
            case 1:
                return new Aim(side, 0.5F, 1.0F, 0.5F);
            case 2:
                return new Aim(side, 0.5F, 0.5F, 0.0F);
            case 3:
                return new Aim(side, 0.5F, 0.5F, 1.0F);
            case 4:
                return new Aim(side, 0.0F, 0.5F, 0.5F);
            case 5:
                return new Aim(side, 1.0F, 0.5F, 0.5F);
            default:
                return new Aim(side, 0.5F, 0.5F, 0.5F);
        }
    }

    private static final class Aim {

        final byte side;
        final float hitX, hitY, hitZ;

        Aim(byte side, float hitX, float hitY, float hitZ) {
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }
    }

    /* ------------------------------------------------------------------ */
    /* TConstruct tools */
    /* ------------------------------------------------------------------ */

    private static final class ClickProfile {

        final List<String> tools;

        ClickProfile(String... tools) {
            this.tools = new ArrayList<>();
            for (String tool : tools) this.tools.add(tool);
        }
    }

    /**
     * Snapshots the tools TConstruct registered. They only exist once TConstruct has run its own preInit - we load
     * before it - so this happens on the first right click instead, which is long after everything is registered.
     */
    private static void buildProfiles() {
        if (profilesBuilt) return;
        addTool(TinkerTools.shovel, CS.TOOL_shovel);
        addTool(TinkerTools.chisel, CS.TOOL_chisel);
        addBeamTool(TinkerTools.hatchet);
        addBeamTool(TinkerTools.lumberaxe);
        profilesBuilt = PROFILES.size() + BEAM_TOOLS.size() > 0; // keep retrying while TConstruct has none ready
    }

    private static void addTool(ToolCore tool, String name) {
        // ToolCore extends Item and every registered tool is a singleton, so the instance is its own identity.
        if (tool != null && name != null) PROFILES.put(tool, new ClickProfile(name));
    }

    private static void addBeamTool(ToolCore tool) {
        if (tool != null) BEAM_TOOLS.add(tool);
    }
}
