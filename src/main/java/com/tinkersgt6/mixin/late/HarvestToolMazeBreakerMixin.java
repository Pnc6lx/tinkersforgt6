package com.tinkersgt6.mixin.late;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tinkersgt6.compat.MazeBreakerCompat;

import tconstruct.library.tools.HarvestTool;

/**
 * Puts MazeBreaker's speed bonus where TConstruct decides how fast a tool mines.
 *
 * <p>
 * {@code HarvestTool.getDigSpeed} is the single answer to "how fast is this tool on this block": it asks
 * {@code calculateStrength} - and through it {@code AbilityHelper.calcToolSpeed} - for anything the tool is effective
 * on,
 * and falls back to a flat 1.0 for everything else. It is also what Forge reads for {@code PlayerEvent.BreakSpeed}, so
 * multiplying here instead of in the event means the number every other mod sees is already the right one, and it
 * covers
 * the case where the tool is not effective on the block at all, which the event version could not reach.
 * </p>
 *
 * <p>
 * The multiplier is applied to {@code calculateStrength} rather than to its result afterwards, because that is the only
 * place TConstruct's own speed (stonebound, haste, tool speed) is in the number already.
 * </p>
 */
// remap = false: getDigSpeed belongs to TConstruct, not to Minecraft, so there is no obfuscation mapping for it. The
// target is found by name at runtime, and our own signature is reobfuscated by the build, which is what makes it match
// the released TConstruct jar just as well as the deobfuscated one used while developing.
@Mixin(value = HarvestTool.class, remap = false)
public abstract class HarvestToolMazeBreakerMixin {

    @Inject(method = "getDigSpeed", at = @At("HEAD"), cancellable = true)
    private void tinkersgt6$mazeBreakerSpeed(ItemStack stack, Block block, int meta,
        CallbackInfoReturnable<Float> callback) {
        float multiplier = MazeBreakerCompat.speedMultiplier();
        if (multiplier <= 1.0F) return;
        if (!MazeBreakerCompat.isMazeBlock(block) || !MazeBreakerCompat.hasMazeBreaker(stack)) return;

        NBTTagCompound tags = stack == null ? null : stack.getTagCompound();
        NBTTagCompound infi = tags == null ? new NBTTagCompound() : tags.getCompoundTag("InfiTool");

        HarvestTool tool = (HarvestTool) (Object) this;
        callback.setReturnValue(tool.calculateStrength(infi, block, meta) * multiplier);
    }
}
