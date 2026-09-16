package com.bdmajora.coarctatio.mixin.client.model;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every model that fails to load gets its own FancyMissingModel rendering the failed location as text, with its own baked quads per state; the plain missing model is one shared instance (UniversalTweaks). remap = false, Forge class
@Mixin(value = ModelLoaderRegistry.class, remap = false)
public abstract class ModelLoaderRegistryMissingMixin {
    @Shadow
    public static IModel getMissingModel() {
        throw new AssertionError();
    }

    @Inject(method = "getMissingModel(Lnet/minecraft/util/ResourceLocation;Ljava/lang/Throwable;)Lnet/minecraftforge/client/model/IModel;",
            at = @At("HEAD"), cancellable = true)
    private static void coarctatio$plainMissingModel(ResourceLocation location, Throwable cause, CallbackInfoReturnable<IModel> cir) {
        cir.setReturnValue(getMissingModel());
    }
}
