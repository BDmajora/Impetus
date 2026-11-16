package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes SPacketCustomPayload.
@Mixin(SPacketCustomPayload.class)
public abstract class SPacketCustomPayloadMixin {
    // Changes a constant in constructor and readPacketData.
    @ModifyConstant(method = {"<init>(Ljava/lang/String;Lnet/minecraft/network/PacketBuffer;)V", "readPacketData"}, constant = @Constant(intValue = 1048576))
    private int impetus$payloadLimit(int vanilla) {
        return NetworkLimits.clientboundPayloadLimit();
    }
}
