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

// Interns the texture variable names and paths of every unbaked model through a map that keeps interning what mods put in later (StellarCore's ModelBlock string canonicalization)
@Mixin(ModelBlock.class)
public abstract class ModelBlockMixin {
    @Shadow
    @Final
    @Mutable
    public Map<String, String> textures;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$internTextures(ResourceLocation parent, List<BlockPart> elements, Map<String, String> textures,
                                           boolean ambientOcclusion, boolean gui3d, ItemCameraTransforms transforms,
                                           List<ItemOverride> overrides, CallbackInfo ci) {
        if (this.textures != null && !(this.textures instanceof CanonicalStringMap)) {
            this.textures = new CanonicalStringMap(this.textures, ModelCaches.MODEL_TEXTURES);
        }
    }
}
