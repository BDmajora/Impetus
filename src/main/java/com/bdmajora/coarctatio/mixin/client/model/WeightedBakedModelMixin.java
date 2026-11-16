package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.collections.CollectionHelper;
import net.minecraft.client.renderer.block.model.WeightedBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin that changes WeightedBakedModel.
@Mixin(WeightedBakedModel.class)
public class WeightedBakedModelMixin {
    // Reference to the field models of the target.
    @Mutable
    @Shadow
    @Final
    private List<WeightedBakedModel.WeightedModel> models;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$compactModelList(CallbackInfo ci) {
        this.models = CollectionHelper.fixed(this.models);
    }
}
