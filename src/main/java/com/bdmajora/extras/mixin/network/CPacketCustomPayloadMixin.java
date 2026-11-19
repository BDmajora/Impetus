package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.play.client.CPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes CPacketCustomPayload.
@Mixin(CPacketCustomPayload.class)
public abstract class CPacketCustomPayloadMixin {
    // Leave each limit in place if another transformer replaced the vanilla value.
    @ModifyExpressionValue(method = {"<init>(Ljava/lang/String;Lnet/minecraft/network/PacketBuffer;)V", "readPacketData"},
            at = @At(value = "CONSTANT", args = "intValue=" + NetworkLimits.VANILLA_SERVERBOUND_PAYLOAD), require = 0)
    private int impetus$payloadLimit(int limit) {
        return limit == NetworkLimits.VANILLA_SERVERBOUND_PAYLOAD ? NetworkLimits.serverboundPayloadLimit() : limit;
    }
}
