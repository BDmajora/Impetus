package com.bdmajora.coarctatio.mixin.nbt;

import com.bdmajora.coarctatio.nbt.TagMap;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes NBTTagCompound.
@Mixin(NBTTagCompound.class)
public class NBTTagCompoundMixin {
    @Mutable
    @Shadow
    @Final
    private Map<String, NBTBase> tagMap;

    // Runs before every return of constructor.
    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void coarctatio$compactBackingMap(CallbackInfo ci) {
        this.tagMap = new TagMap();
    }
}
