package com.bdmajora.coarctatio.mixin.client.model.part;

import com.bdmajora.coarctatio.client.model.CanonicalStringMap;
import com.bdmajora.coarctatio.dedup.ModelCaches;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

// Mixin that changes ModelBlock.
@Mixin(ModelBlock.class)
public abstract class ModelBlockMixin {
    @Shadow
    @Final
    @Mutable
    public Map<String, String> textures;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$internTextures(ResourceLocation parent, List<BlockPart> elements, Map<String, String> textures,
                                           // Flags and transforms of the model.
                                           boolean ambientOcclusion, boolean gui3d, ItemCameraTransforms transforms,
                                           // Overrides of the model.
                                           List<ItemOverride> overrides, CallbackInfo ci) {
        if (this.textures != null && !(this.textures instanceof CanonicalStringMap)) {
            this.textures = new CanonicalStringMap(this.textures, ModelCaches.MODEL_TEXTURES);
        }
    }
}
