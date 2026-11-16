package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.play.client.CPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes CPacketCustomPayload.
@Mixin(CPacketCustomPayload.class)
public abstract class CPacketCustomPayloadMixin {
    // Changes a constant in constructor and readPacketData.
    @ModifyConstant(method = {"<init>(Ljava/lang/String;Lnet/minecraft/network/PacketBuffer;)V", "readPacketData"}, constant = @Constant(intValue = 32767))
    private int impetus$payloadLimit(int vanilla) {
        return NetworkLimits.serverboundPayloadLimit();
    }
}
