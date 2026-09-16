package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.UnbakedModelProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Unlimited Chisel Works reads and writes the loader's stateModels map through its early view; those go to the dynamic provider instead. Applied only when UCW is present
@Pseudo
@Mixin(targets = "pl/asie/ucw/util/ModelLoaderEarlyView", remap = false)
public abstract class UcwEarlyViewMixin {
    private IModel coarctatio$missing;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$keepMissing(CallbackInfo ci) {
        this.coarctatio$missing = DynamicModels.unbaked().getObject(UnbakedModelProvider.MISSING);
    }

    @Redirect(method = "getModel(Lnet/minecraft/client/renderer/block/model/ModelResourceLocation;)Lnet/minecraftforge/client/model/IModel;", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object coarctatio$getDynamic(Map<Object, Object> stateModels, Object location) {
        try {
            return DynamicModels.unbaked().getObject((ResourceLocation) location);
        } catch (RuntimeException e) {
            Coarctatio.LOGGER.error("Error retrieving model {} for UCW: {}", location, e.toString());
            return this.coarctatio$missing;
        }
    }

    @Redirect(method = "putModel", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object coarctatio$putDynamic(Map<Object, Object> stateModels, Object location, Object model) {
        DynamicModels.unbaked().putObject((ResourceLocation) location, (IModel) model);
        return null;
    }
}
