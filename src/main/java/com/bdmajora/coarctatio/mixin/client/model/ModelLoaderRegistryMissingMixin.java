package com.bdmajora.coarctatio.mixin.client.model;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ModelLoaderRegistry.
@Mixin(value = ModelLoaderRegistry.class, remap = false)
public abstract class ModelLoaderRegistryMissingMixin {
    // Reference to the method get missing model of the target.
    @Shadow
    public static IModel getMissingModel() {
        throw new AssertionError();
    }

    // Runs at the start of getMissingModel and can cancel it.
    @Inject(method = "getMissingModel(Lnet/minecraft/util/ResourceLocation;Ljava/lang/Throwable;)Lnet/minecraftforge/client/model/IModel;",
            at = @At("HEAD"), cancellable = true)
    private static void coarctatio$plainMissingModel(ResourceLocation location, Throwable cause, CallbackInfoReturnable<IModel> cir) {
        cir.setReturnValue(getMissingModel());
    }
}
