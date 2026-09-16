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

// The reload no longer runs ModelLoader.setupModelRegistry; the registry it installs bakes on request
@Mixin(ModelManager.class)
public abstract class ModelManagerDynamicMixin implements DynamicModels.ModelManagerAccess {
    @Shadow
    private IRegistry<ModelResourceLocation, IBakedModel> modelRegistry;

    @Shadow
    private IBakedModel defaultModel;

    @Shadow
    @Final
    private TextureMap texMap;

    @Shadow
    @Final
    private BlockModelShapes modelProvider;

    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Replace the eager load-and-bake of every model with the dynamic providers
     */
    @Overwrite
    public void onResourceManagerReload(IResourceManager resourceManager) {
        DynamicModels.reload((ModelManager) (Object) this, resourceManager, this.texMap, this.modelProvider);
    }

    @Override
    public void coarctatio$setModelRegistry(BakedModelProvider registry) {
        this.modelRegistry = registry;
    }

    @Override
    public void coarctatio$setDefaultModel(IBakedModel model) {
        this.defaultModel = model;
    }
}
