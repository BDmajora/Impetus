package com.bdmajora.impetus.mixin.core.collections;

import net.minecraft.util.ClassInheritanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.AbstractSet;
import java.util.List;
import java.util.function.Consumer;

// Mixin that changes ClassInheritanceMultiMap.
@Mixin(ClassInheritanceMultiMap.class)
public abstract class ClassInheritanceMultiMapMixin<T> extends AbstractSet<T> {
    // Reference to the field values of the target.
    @Shadow
    @Final
    private List<T> values;

    // Handles for each.
    @Override
    public void forEach(Consumer<? super T> action) {
        this.values.forEach(action);
    }
}
