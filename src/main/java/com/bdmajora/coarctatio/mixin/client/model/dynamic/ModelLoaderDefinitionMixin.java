package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.client.model.dynamic.DefinitionLoader;
import com.bdmajora.coarctatio.client.model.dynamic.ModelLocations;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// Blockstate definitions come from an expiring soft cache instead of the bakery's keep-everything map, and a multipart definition carries its block's state container the way loadBlock set it
@Mixin(ModelLoader.class)
public abstract class ModelLoaderDefinitionMixin implements DefinitionLoader {
    private final Cache<ResourceLocation, ModelBlockDefinition> coarctatio$definitions = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .maximumSize(100)
            .concurrencyLevel(8)
            .softValues()
            .build();

    @Inject(method = "getModelBlockDefinition", at = @At("HEAD"), cancellable = true)
    private void coarctatio$cachedDefinition(ResourceLocation location, CallbackInfoReturnable<ModelBlockDefinition> cir) {
        cir.setReturnValue(this.coarctatio$definition(location));
    }

    // Keyed by the plain location, since every variant of a block shares one definition file
    @Override
    public ModelBlockDefinition coarctatio$definition(ResourceLocation location) {
        ResourceLocation plain = new ResourceLocation(location.getNamespace(), location.getPath());
        try {
            return this.coarctatio$definitions.get(plain, () -> {
                try {
                    return ModelLocations.loadDefinition(plain);
                } catch (Exception e) {
                    // Vanilla's behaviour for a broken or absent file: an empty definition, and the variant load fails on it
                    Coarctatio.LOGGER.debug("Error loading model block definition {}", plain, e);
                    return new ModelBlockDefinition(new ArrayList<>());
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
