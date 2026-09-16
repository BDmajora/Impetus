package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.network.VarInts;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Krypton's varint micro-optimisations on the two entry points everything routes through; the frame encoder sizes and writes its length prefix via these, so it is covered without touching it. Separate from PacketBufferMixin, which owns the size caps, so either can be switched off alone
@Mixin(PacketBuffer.class)
public abstract class PacketBufferVarIntMixin {
    @Inject(method = "getVarIntSize", at = @At("HEAD"), cancellable = true)
    private static void impetus$tableSize(int value, CallbackInfoReturnable<Integer> cir) {
        if (Extras.options().network.fastVarInts) {
            cir.setReturnValue(VarInts.size(value));
        }
    }

    @Inject(method = "writeVarInt", at = @At("HEAD"), cancellable = true)
    private void impetus$wideWrite(int value, CallbackInfoReturnable<PacketBuffer> cir) {
        if (Extras.options().network.fastVarInts) {
            VarInts.write((PacketBuffer) (Object) this, value);
            cir.setReturnValue((PacketBuffer) (Object) this);
        }
    }
}
