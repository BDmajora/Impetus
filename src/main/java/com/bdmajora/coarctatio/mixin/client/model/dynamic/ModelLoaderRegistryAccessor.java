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

// The registry's loader set, seeded cache and texture set, which the dynamic provider takes over. remap = false, Forge class
@Mixin(value = ModelLoaderRegistry.class, remap = false)
public interface ModelLoaderRegistryAccessor {
    @Accessor("loaders")
    static Set<ICustomModelLoader> coarctatio$loaders() {
        throw new AssertionError();
    }

    @Accessor("cache")
    static Map<ResourceLocation, IModel> coarctatio$cache() {
        throw new AssertionError();
    }

    @Invoker("getTextures")
    static Iterable<ResourceLocation> coarctatio$textures() {
        throw new AssertionError();
    }
}
