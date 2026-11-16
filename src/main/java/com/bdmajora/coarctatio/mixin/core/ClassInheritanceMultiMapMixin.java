package com.bdmajora.coarctatio.mixin.core;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.util.ClassInheritanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Mixin that changes ClassInheritanceMultiMap.
@Mixin(ClassInheritanceMultiMap.class)
public abstract class ClassInheritanceMultiMapMixin<T> {
    @Mutable
    @Shadow
    @Final
    private Map<Class<?>, List<T>> map;

    // Reference to the field known keys of the target.
    @Mutable
    @Shadow
    @Final
    private Set<Class<?>> knownKeys;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$compactLookups(CallbackInfo ci) {
        this.map = new Reference2ObjectOpenHashMap<>(this.map);
        this.knownKeys = new ReferenceOpenHashSet<>(this.knownKeys);
    }
}
