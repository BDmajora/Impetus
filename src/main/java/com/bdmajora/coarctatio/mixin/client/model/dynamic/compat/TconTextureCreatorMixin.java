package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.bdmajora.coarctatio.client.model.dynamic.compat.TconTextureExistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TConstruct asks whether a texture file exists for every material and part combination, thousands of probes that repeat across its models; remembered for the reload, cleared when the deferred texture creator runs. Applied only when TConstruct is present
@Pseudo
@Mixin(targets = "slimeknights/tconstruct/library/client/CustomTextureCreator", remap = false)
public abstract class TconTextureCreatorMixin {
    @Inject(method = "exists", at = @At("HEAD"), cancellable = true)
    private static void coarctatio$answerFromCache(String key, CallbackInfoReturnable<Boolean> cir) {
        Boolean known = TconTextureExistence.known(key);
        if (known != null) {
            cir.setReturnValue(known);
        }
    }

    @Inject(method = "exists", at = @At("RETURN"))
    private static void coarctatio$remember(String key, CallbackInfoReturnable<Boolean> cir) {
        TconTextureExistence.remember(key, cir.getReturnValueZ());
    }
}
