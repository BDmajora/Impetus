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

// Decodes the atlas's sprites in parallel before the load loop, then feeds the loop from the prefetch; dependencies registered mid-loop and custom-loader sprites keep the vanilla path
@Mixin(TextureMap.class)
public abstract class TextureMapPrefetchMixin {
    @Unique
    private static final String LOAD_TEXTURE = "loadTexture(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/resources/IResourceManager;"
            + "Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
            + "Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)I";

    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapRegisteredSprites;

    @Shadow
    protected abstract ResourceLocation getResourceLocation(TextureAtlasSprite sprite);

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void coarctatio$prefetchSprites(IResourceManager resourceManager, CallbackInfo ci) {
        SpritePrefetch.run(resourceManager, new ArrayList<>(this.mapRegisteredSprites.values()), this::getResourceLocation);
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void coarctatio$dropPrefetch(IResourceManager resourceManager, CallbackInfo ci) {
        SpritePrefetch.clear();
    }

    // Both lookups in loadTexture, the header read and the metadata read; the full descriptor names Forge's added overload, which keeps its name at runtime, rather than the inherited loadTexture(IResourceManager)
    @WrapOperation(method = LOAD_TEXTURE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;"))
    private IResource coarctatio$prefetchedForLoad(IResourceManager manager, ResourceLocation location, Operation<IResource> original,
                                                   @Local(argsOnly = true) TextureAtlasSprite sprite) throws IOException {
        IResource prefetched = SpritePrefetch.resource(sprite);
        return prefetched != null ? prefetched : original.call(manager, location);
    }

    // The header is already parsed; the vanilla call would reopen the file for it
    @WrapOperation(method = LOAD_TEXTURE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/PngSizeInfo;makeFromResource(Lnet/minecraft/client/resources/IResource;)Lnet/minecraft/client/renderer/texture/PngSizeInfo;"))
    private PngSizeInfo coarctatio$prefetchedSize(IResource resource, Operation<PngSizeInfo> original) throws IOException {
        if (resource instanceof SpritePrefetch.PrefetchedResource) {
            return ((SpritePrefetch.PrefetchedResource) resource).sizeInfo();
        }
        return original.call(resource);
    }

    // The frame read after stitching, which is where the decoded image is finally consumed
    @WrapOperation(method = "generateMipmaps", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;"))
    private IResource coarctatio$prefetchedForFrames(IResourceManager manager, ResourceLocation location, Operation<IResource> original,
                                                     @Local(argsOnly = true) TextureAtlasSprite sprite) throws IOException {
        IResource prefetched = SpritePrefetch.resource(sprite);
        return prefetched != null ? prefetched : original.call(manager, location);
    }
}
