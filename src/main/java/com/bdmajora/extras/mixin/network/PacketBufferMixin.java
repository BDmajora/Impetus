package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin that changes PacketBuffer.
@Mixin(PacketBuffer.class)
public abstract class PacketBufferMixin {
    @ModifyExpressionValue(method = "readCompoundTag",
            at = @At(value = "CONSTANT", args = "longValue=" + NetworkLimits.VANILLA_NBT), require = 0)
    private long impetus$nbtLimit(long limit) {
        return limit == NetworkLimits.VANILLA_NBT ? NetworkLimits.nbtLimit() : limit;
    }

    // Leave the writer's limit to any transformer that replaces the vanilla constant.
    @ModifyExpressionValue(method = "writeString",
            at = @At(value = "CONSTANT", args = "intValue=" + NetworkLimits.VANILLA_STRING), require = 0)
    private int impetus$writeStringLimit(int limit) {
        return limit == NetworkLimits.VANILLA_STRING ? NetworkLimits.stringLimit() : limit;
    }

    // Changes a local variable in readString.
    @ModifyVariable(method = "readString(I)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int impetus$readStringLimit(int maxLength) {
        return maxLength == NetworkLimits.VANILLA_STRING ? NetworkLimits.stringLimit() : maxLength;
    }
}
