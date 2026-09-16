package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.UnbakedModelProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Every model request, from Forge's loaders and from mods, goes through the dynamic provider. remap = false, Forge class
@Mixin(value = ModelLoaderRegistry.class, remap = false)
public abstract class ModelLoaderRegistryDynamicMixin {
    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Loading and caching live in UnbakedModelProvider
     */
    @Overwrite
    public static IModel getModel(ResourceLocation location) throws Exception {
        UnbakedModelProvider provider = DynamicModels.unbaked();
        if (provider == null) {
            throw new ModelLoaderRegistry.LoaderException("Using ModelLoaderRegistry too early, no model reload has run yet");
        }
        return provider.getObject(location);
    }
}
