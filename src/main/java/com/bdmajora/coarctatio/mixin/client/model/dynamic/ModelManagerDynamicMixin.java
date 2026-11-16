package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.BakedModelProvider;
import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.registry.IRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

// Mixin that changes ModelManager.
@Mixin(ModelManager.class)
public abstract class ModelManagerDynamicMixin implements DynamicModels.ModelManagerAccess {
    @Shadow
    private IRegistry<ModelResourceLocation, IBakedModel> modelRegistry;

    // Reference to the field default model of the target.
    @Shadow
    private IBakedModel defaultModel;

    // Reference to the field tex map of the target.
    @Shadow
    @Final
    private TextureMap texMap;

    // Reference to the field model provider of the target.
    @Shadow
    @Final
    private BlockModelShapes modelProvider;

    // Replaces the whole method onResourceManagerReload.
    @Overwrite
    public void onResourceManagerReload(IResourceManager resourceManager) {
        DynamicModels.reload((ModelManager) (Object) this, resourceManager, this.texMap, this.modelProvider);
    }

    // Handles set model registry.
    @Override
    public void coarctatio$setModelRegistry(BakedModelProvider registry) {
        this.modelRegistry = registry;
    }

    // Handles set default model.
    @Override
    public void coarctatio$setDefaultModel(IBakedModel model) {
        this.defaultModel = model;
    }
}
