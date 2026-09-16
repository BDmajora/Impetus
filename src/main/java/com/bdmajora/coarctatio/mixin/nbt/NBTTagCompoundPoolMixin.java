package com.bdmajora.coarctatio.mixin.nbt;

import com.bdmajora.coarctatio.nbt.NbtPrimitivePool;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Every way a numeric tag enters a compound: the typed setters and the deserialiser's put; setTag is covered too since mods build tags themselves and pass them in
@Mixin(NBTTagCompound.class)
public abstract class NBTTagCompoundPoolMixin {
    @ModifyArg(method = {"setByte", "setShort", "setInteger", "setLong", "setTag", "read"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", remap = false), index = 1)
    private Object coarctatio$poolNumeric(Object value) {
        return value instanceof NBTBase ? NbtPrimitivePool.canonical((NBTBase) value) : value;
    }
}
