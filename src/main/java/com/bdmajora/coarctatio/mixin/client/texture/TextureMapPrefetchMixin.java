package com.bdmajora.coarctatio.mixin.client.texture;

import com.bdmajora.coarctatio.client.texture.SpritePrefetch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

// Mixin that changes TextureMap.
@Mixin(TextureMap.class)
public abstract class TextureMapPrefetchMixin {
    // Field load texture added to the target.
    @Unique
    private static final String LOAD_TEXTURE = "loadTexture(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/resources/IResourceManager;"
            + "Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
            + "Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)I";

    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapRegisteredSprites;

    // Reference to the method get resource location of the target.
    @Shadow
    protected abstract ResourceLocation getResourceLocation(TextureAtlasSprite sprite);

    // Runs at the start of loadTextureAtlas.
    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void coarctatio$prefetchSprites(IResourceManager resourceManager, CallbackInfo ci) {
        SpritePrefetch.run(resourceManager, new ArrayList<>(this.mapRegisteredSprites.values()), this::getResourceLocation);
    }

    // Runs before every return of loadTextureAtlas.
    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void coarctatio$dropPrefetch(IResourceManager resourceManager, CallbackInfo ci) {
        SpritePrefetch.clear();
    }

    // Wraps a call.
    @WrapOperation(method = LOAD_TEXTURE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;"))
    private IResource coarctatio$prefetchedForLoad(IResourceManager manager, ResourceLocation location, Operation<IResource> original,
                                                   @Local(argsOnly = true) TextureAtlasSprite sprite) throws IOException {
        IResource prefetched = SpritePrefetch.resource(sprite);
        return prefetched != null ? prefetched : original.call(manager, location);
    }

    // Wraps a call.
    @WrapOperation(method = LOAD_TEXTURE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/PngSizeInfo;makeFromResource(Lnet/minecraft/client/resources/IResource;)Lnet/minecraft/client/renderer/texture/PngSizeInfo;"))
    private PngSizeInfo coarctatio$prefetchedSize(IResource resource, Operation<PngSizeInfo> original) throws IOException {
        if (resource instanceof SpritePrefetch.PrefetchedResource) {
            return ((SpritePrefetch.PrefetchedResource) resource).sizeInfo();
        }
        return original.call(resource);
    }

    // Wraps the call of IResourceManager.getResource in generateMipmaps.
    @WrapOperation(method = "generateMipmaps", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;"))
    private IResource coarctatio$prefetchedForFrames(IResourceManager manager, ResourceLocation location, Operation<IResource> original,
                                                     @Local(argsOnly = true) TextureAtlasSprite sprite) throws IOException {
        IResource prefetched = SpritePrefetch.resource(sprite);
        return prefetched != null ? prefetched : original.call(manager, location);
    }
}
