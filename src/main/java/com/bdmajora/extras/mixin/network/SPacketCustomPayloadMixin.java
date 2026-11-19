package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes SPacketCustomPayload.
@Mixin(SPacketCustomPayload.class)
public abstract class SPacketCustomPayloadMixin {
    @ModifyExpressionValue(method = {"<init>(Ljava/lang/String;Lnet/minecraft/network/PacketBuffer;)V", "readPacketData"},
            at = @At(value = "CONSTANT", args = "intValue=" + NetworkLimits.VANILLA_CLIENTBOUND_PAYLOAD), require = 0)
    private int impetus$payloadLimit(int limit) {
        return limit == NetworkLimits.VANILLA_CLIENTBOUND_PAYLOAD ? NetworkLimits.clientboundPayloadLimit() : limit;
    }
}
