package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;
import java.util.Set;

// The bakery's item variant table and the built-in sprite list, which the dynamic reload reads without running the bakery's load
@Mixin(ModelBakery.class)
public interface ModelBakeryAccessor {
    @Invoker("registerVariantNames")
    void coarctatio$registerVariantNames();

    @Accessor("variantNames")
    Map<Item, List<String>> coarctatio$variantNames();

    @Accessor("LOCATIONS_BUILTIN_TEXTURES")
    static Set<ResourceLocation> coarctatio$builtinTextures() {
        throw new AssertionError();
    }
}
