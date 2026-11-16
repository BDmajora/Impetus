package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.ModelLocations;
import com.google.common.collect.Maps;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.model.IModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes ModelLoaderEarlyView.
@Pseudo
@Mixin(targets = "pl/asie/debark/util/ModelLoaderEarlyView", remap = false)
public abstract class DebarkEarlyViewMixin {
    @Shadow
    private Map<ModelResourceLocation, IModel> secretSauce;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$liveView(CallbackInfo ci) {
        this.secretSauce = Maps.asMap(ModelLocations.ALL_KNOWN, location -> DynamicModels.unbaked().getModelOrMissing(location));
    }
}
