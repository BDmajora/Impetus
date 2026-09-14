package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// NBT and string caps on the buffer itself: the 2 MB NBT tracker behind "Tried to read NBT tag that was too big", the 32767-byte string encoder, and the generic 32767 that nearly every reader passes to readString
@Mixin(PacketBuffer.class)
public abstract class PacketBufferMixin {
    @ModifyConstant(method = "readCompoundTag", constant = @Constant(longValue = 2097152L))
    private long impetus$nbtLimit(long vanilla) {
        return NetworkLimits.nbtLimit();
    }

    @ModifyConstant(method = "writeString", constant = @Constant(intValue = 32767))
    private int impetus$writeStringLimit(int vanilla) {
        return NetworkLimits.stringLimit();
    }

    // Only the generic cap is widened; a caller that asked for a small explicit limit (channel names, player names) keeps it
    @ModifyVariable(method = "readString", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int impetus$readStringLimit(int maxLength) {
        return maxLength == NetworkLimits.VANILLA_STRING ? NetworkLimits.stringLimit() : maxLength;
    }
}
