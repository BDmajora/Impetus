package com.bdmajora.coarctatio.mixin.forge;

import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// The values map is package-private; the codec fills it directly since the public adders route through array-building state
@Mixin(value = ModAnnotation.class, remap = false)
public interface ModAnnotationAccessor {
    @Accessor("values")
    Map<String, Object> coarctatio$values();

    @Accessor("values")
    void coarctatio$setValues(Map<String, Object> values);
}
