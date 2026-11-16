package com.bdmajora.coarctatio.mixin.util;

import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ResourceLocation.
@Mixin(ResourceLocation.class)
public class ResourceLocationMixin {
    // Reference to the field namespace of the target.
    @Mutable
    @Shadow
    @Final
    protected String namespace;

    // Reference to the field path of the target.
    @Mutable
    @Shadow
    @Final
    protected String path;

    // Runs before every return of constructor.
    @Inject(method = "<init>(I[Ljava/lang/String;)V", at = @At("RETURN"))
    private void coarctatio$internComponents(int unused, String[] parts, CallbackInfo ci) {
        this.namespace = ResourceLocationCaches.DOMAINS.deduplicate(this.namespace);
        this.path = ResourceLocationCaches.PATHS.deduplicate(this.path);
    }
}
