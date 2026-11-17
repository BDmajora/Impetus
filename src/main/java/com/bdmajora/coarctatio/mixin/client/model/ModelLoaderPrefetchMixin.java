package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.client.model.ModelPrefetch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes ModelLoader.
@Mixin(ModelLoader.class)
public abstract class ModelLoaderPrefetchMixin extends ModelBakery {
    // Creates a model loader prefetch mixin from its manager and atlas and shapes.
    private ModelLoaderPrefetchMixin(IResourceManager manager, TextureMap atlas, BlockModelShapes shapes) {
        super(manager, atlas, shapes);
    }

    // Reads every model file on worker threads before vanilla bakes.
    @WrapMethod(method = "setupModelRegistry()Lnet/minecraft/util/registry/IRegistry;")
    private IRegistry<ModelResourceLocation, IBakedModel> coarctatio$prefetchModels(Operation<IRegistry<ModelResourceLocation, IBakedModel>> original) {
        ModelPrefetch.run(this.resourceManager, this::loadModel);
        try {
            return original.call();
        } finally {
            ModelPrefetch.clear();
        }
    }
}
