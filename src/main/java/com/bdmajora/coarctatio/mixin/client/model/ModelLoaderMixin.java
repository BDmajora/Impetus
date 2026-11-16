package com.bdmajora.coarctatio.mixin.client.model;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

// Mixin that changes ModelLoader.
@Mixin(value = ModelLoader.class, remap = false)
public abstract class ModelLoaderMixin {
    @Mutable
    @Shadow
    @Final
    private Map<ModelResourceLocation, IModel> stateModels;

    @Mutable
    @Shadow
    @Final
    private Map<ModelResourceLocation, ModelBlockDefinition> multipartDefinitions;

    @Mutable
    @Shadow
    @Final
    private Map<ModelBlockDefinition, IModel> multipartModels;

    // Reference to the field missing variants of the target.
    @Mutable
    @Shadow
    @Final
    private Set<ModelResourceLocation> missingVariants;

    @Mutable
    @Shadow
    @Final
    private Map<ResourceLocation, Exception> loadingExceptions;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$compactLoaderMaps(CallbackInfo ci) {
        this.stateModels = new Object2ObjectOpenHashMap<>(this.stateModels);
        this.multipartDefinitions = new Object2ObjectOpenHashMap<>(this.multipartDefinitions);
        this.multipartModels = new Object2ObjectOpenHashMap<>(this.multipartModels);
        this.missingVariants = new ObjectOpenHashSet<>(this.missingVariants);
        this.loadingExceptions = new Object2ObjectOpenHashMap<>(this.loadingExceptions);
    }
}
