package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.network.VarInts;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes PacketBuffer.
@Mixin(PacketBuffer.class)
public abstract class PacketBufferVarIntMixin {
    // Runs at the start of getVarIntSize and can cancel it.
    @Inject(method = "getVarIntSize", at = @At("HEAD"), cancellable = true)
    private static void impetus$tableSize(int value, CallbackInfoReturnable<Integer> cir) {
        if (Extras.options().network.fastVarInts) {
            cir.setReturnValue(VarInts.size(value));
        }
    }

    // Runs at the start of writeVarInt and can cancel it.
    @Inject(method = "writeVarInt", at = @At("HEAD"), cancellable = true)
    private void impetus$wideWrite(int value, CallbackInfoReturnable<PacketBuffer> cir) {
        if (Extras.options().network.fastVarInts) {
            VarInts.write((PacketBuffer) (Object) this, value);
            cir.setReturnValue((PacketBuffer) (Object) this);
        }
    }
}
