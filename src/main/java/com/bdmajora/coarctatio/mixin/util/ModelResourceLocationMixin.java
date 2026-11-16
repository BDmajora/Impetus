package com.bdmajora.coarctatio.mixin.util;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ModelResourceLocation.
@Mixin(ModelResourceLocation.class)
public class ModelResourceLocationMixin {
    // Reference to the field variant of the target.
    @Mutable
    @Shadow
    @Final
    private String variant;

    // Runs before every return of constructor.
    @Inject(method = "<init>(I[Ljava/lang/String;)V", at = @At("RETURN"))
    private void coarctatio$internVariant(int unused, String[] parts, CallbackInfo ci) {
        this.variant = ModelCaches.VARIANTS.deduplicate(this.variant);
    }
}
