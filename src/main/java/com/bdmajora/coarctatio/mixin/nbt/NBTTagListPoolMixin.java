package com.bdmajora.coarctatio.mixin.nbt;

import com.bdmajora.coarctatio.nbt.NbtPrimitivePool;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Mixin that changes NBTTagList.
@Mixin(NBTTagList.class)
public abstract class NBTTagListPoolMixin {
    // Changes an argument of the call of List.add in read and appendTag.
    @ModifyArg(method = {"read", "appendTag"}, at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false))
    private Object coarctatio$poolAdded(Object value) {
        return value instanceof NBTBase ? NbtPrimitivePool.canonical((NBTBase) value) : value;
    }

    // Changes an argument of the call of List.set in set.
    @ModifyArg(method = "set", at = @At(value = "INVOKE", target = "Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;", remap = false), index = 1)
    private Object coarctatio$poolSet(Object value) {
        return value instanceof NBTBase ? NbtPrimitivePool.canonical((NBTBase) value) : value;
    }
}
