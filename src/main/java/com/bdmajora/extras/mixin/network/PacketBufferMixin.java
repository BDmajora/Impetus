package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin that changes PacketBuffer.
@Mixin(PacketBuffer.class)
public abstract class PacketBufferMixin {
    // Changes a constant in readCompoundTag.
    @ModifyConstant(method = "readCompoundTag", constant = @Constant(longValue = 2097152L), require = 0)
    private long impetus$nbtLimit(long vanilla) {
        return NetworkLimits.nbtLimit();
    }

    // Changes a constant in writeString.
    @ModifyConstant(method = "writeString", constant = @Constant(intValue = 32767))
    private int impetus$writeStringLimit(int vanilla) {
        return NetworkLimits.stringLimit();
    }

    // Changes a local variable in readString.
    @ModifyVariable(method = "readString(I)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int impetus$readStringLimit(int maxLength) {
        return maxLength == NetworkLimits.VANILLA_STRING ? NetworkLimits.stringLimit() : maxLength;
    }
}
