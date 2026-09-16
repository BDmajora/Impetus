package com.bdmajora.coarctatio.mixin.nbt;

import com.bdmajora.coarctatio.nbt.NbtPrimitivePool;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Lists of numerics (inventories' Slot bytes, enchantment ids and levels) go through add on read and append, and set on replace
@Mixin(NBTTagList.class)
public abstract class NBTTagListPoolMixin {
    @ModifyArg(method = {"read", "appendTag"}, at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false))
    private Object coarctatio$poolAdded(Object value) {
        return value instanceof NBTBase ? NbtPrimitivePool.canonical((NBTBase) value) : value;
    }

    @ModifyArg(method = "set", at = @At(value = "INVOKE", target = "Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;", remap = false), index = 1)
    private Object coarctatio$poolSet(Object value) {
        return value instanceof NBTBase ? NbtPrimitivePool.canonical((NBTBase) value) : value;
    }
}
