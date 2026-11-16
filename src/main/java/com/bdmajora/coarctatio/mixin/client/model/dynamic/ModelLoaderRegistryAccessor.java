package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;

// Mixin that changes ModelLoaderRegistry.
@Mixin(value = ModelLoaderRegistry.class, remap = false)
public interface ModelLoaderRegistryAccessor {
    // Gives access to the field loaders.
    @Accessor("loaders")
    static Set<ICustomModelLoader> coarctatio$loaders() {
        throw new AssertionError();
    }

    // Gives access to the field cache.
    @Accessor("cache")
    static Map<ResourceLocation, IModel> coarctatio$cache() {
        throw new AssertionError();
    }

    // Calls the method textures.
    @Invoker("getTextures")
    static Iterable<ResourceLocation> coarctatio$textures() {
        throw new AssertionError();
    }
}
