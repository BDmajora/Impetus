package com.bdmajora.coarctatio.mixin.client.model;

import net.minecraft.client.renderer.block.model.IBakedModel;
import com.bdmajora.coarctatio.state.BakeStateReleasable;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes ModelLoader.
@Mixin(ModelLoader.class)
public abstract class ModelLoaderCleanupMixin {
    @Shadow(remap = false)
    @Final
    private Map<ModelResourceLocation, IModel> stateModels;

    @Shadow(remap = false)
    @Final
    private Map<ResourceLocation, Exception> loadingExceptions;

    @Shadow(remap = false)
    @Final
    private Map<ModelResourceLocation, ModelBlockDefinition> multipartDefinitions;

    @Shadow(remap = false)
    @Final
    private Map<ModelBlockDefinition, IModel> multipartModels;

    // Runs before every return of onPostBakeEvent.
    @Inject(method = "onPostBakeEvent", at = @At("RETURN"), remap = false)
    private void coarctatio$releaseBakeState(IRegistry<ModelResourceLocation, IBakedModel> registry,
                                            CallbackInfo ci) {
        int models = this.stateModels.size() + this.multipartModels.size();

        this.stateModels.clear();
        this.loadingExceptions.clear();
        this.multipartDefinitions.clear();
        this.multipartModels.clear();
        models += ((BakeStateReleasable) this).coarctatio$releaseBakeryState();

    }
}
