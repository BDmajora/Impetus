package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.UnbakedModelProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Forge resolves every variant's dependencies while constructing the model, to register their textures; they load when baked instead, except during the stitch fallback that is gathering textures. remap = false, Forge class
@Mixin(targets = "net/minecraftforge/client/model/ModelLoader$WeightedRandomModel", remap = false)
public abstract class WeightedRandomModelDynamicMixin {
    @Redirect(method = "<init>(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/block/model/VariantList;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/ModelLoaderRegistry;getModelOrMissing(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/client/model/IModel;"))
    private IModel coarctatio$deferDependencies(ResourceLocation location) {
        return UnbakedModelProvider.textureCapture != null ? ModelLoaderRegistry.getModelOrMissing(location) : null;
    }
}
