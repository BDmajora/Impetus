package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.ModelLocations;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

// Forge's wrapper loads its override models into the loader's stateModels while listing dependencies and bakes through a 100 ms cache keyed on the getter; neither suits on-demand loading, so overrides become aliases and bakes go straight to bakeImpl. remap = false, Forge class
@Mixin(targets = "net/minecraftforge/client/model/ModelLoader$VanillaModelWrapper", remap = false)
public abstract class VanillaModelWrapperDynamicMixin {
    @Shadow
    @Final
    private ModelBlock model;

    @Shadow
    public abstract IBakedModel bakeImpl(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter);

    @Redirect(method = "getDependencies", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object coarctatio$skipStateModelPut(Map<Object, Object> stateModels, Object key, Object value) {
        return null;
    }

    @Redirect(method = "getDependencies", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/ModelLoaderRegistry;getModelOrLogError(Lnet/minecraft/util/ResourceLocation;Ljava/lang/String;)Lnet/minecraftforge/client/model/IModel;"))
    private IModel coarctatio$skipOverrideLoad(ResourceLocation location, String error) {
        return null;
    }

    // The inventory variant of each override resolves to the override's model file, which is what vanilla's stateModels entry achieved
    @Inject(method = "getDependencies", at = @At("RETURN"))
    private void coarctatio$aliasOverrides(CallbackInfoReturnable<Collection<ResourceLocation>> cir) {
        for (ResourceLocation override : this.model.getOverrideLocations()) {
            DynamicModels.unbaked().putAlias(ModelLocations.inventoryVariant(override.toString()), override);
        }
    }

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void coarctatio$bakeDirectly(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter, CallbackInfoReturnable<IBakedModel> cir) {
        cir.setReturnValue(this.bakeImpl(state, format, bakedTextureGetter));
    }
}
