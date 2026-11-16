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

// Mixin that changes ModelBakery.
@Mixin(ModelBakery.class)
public interface ModelBakeryAccessor {
    // Calls the method register variant names.
    @Invoker("registerVariantNames")
    void coarctatio$registerVariantNames();

    // Gives access to the field variant names.
    @Accessor("variantNames")
    Map<Item, List<String>> coarctatio$variantNames();

    // Gives access to the field builtin textures.
    @Accessor("LOCATIONS_BUILTIN_TEXTURES")
    static Set<ResourceLocation> coarctatio$builtinTextures() {
        throw new AssertionError();
    }
}
