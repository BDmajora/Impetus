package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.WeakSpriteTextureMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

// Mixin that changes TextureMap.
@Mixin(TextureMap.class)
public abstract class TextureMapWeakSpriteMixin implements WeakSpriteTextureMap {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapRegisteredSprites;

    // Reference to the method register sprite of the target.
    @Shadow
    public abstract TextureAtlasSprite registerSprite(ResourceLocation location);

    // Names of sprites that may be dropped.
    private final Set<String> coarctatio$weakSprites = new ObjectOpenHashSet<>();

    // Runs around the call of ForgeHooksClient.onTextureStitchedPre in loadSprites.
    @Inject(method = "loadSprites", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre(Lnet/minecraft/client/renderer/texture/TextureMap;)V", remap = false))
    private void coarctatio$registerScannedSprites(IResourceManager resourceManager, net.minecraft.client.renderer.texture.ITextureMapPopulator populator, CallbackInfo ci) {
        this.coarctatio$weakSprites.clear();
        DynamicModels.registerDiscoveredSprites((TextureMap) (Object) this);
    }

    // Runs around the call of Map.get in registerSprite.
    @Inject(method = "registerSprite", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", remap = false))
    private void coarctatio$replaceWeak(ResourceLocation location, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        this.coarctatio$forgetWeak(location.toString());
    }

    // Runs at the start of setTextureEntry.
    @Inject(method = "setTextureEntry", at = @At("HEAD"), remap = false)
    private void coarctatio$replaceWeakEntry(TextureAtlasSprite sprite, CallbackInfoReturnable<Boolean> cir) {
        this.coarctatio$forgetWeak(sprite.getIconName());
    }

    // Drops a weakly registered sprite.
    private void coarctatio$forgetWeak(String name) {
        if (this.coarctatio$weakSprites.remove(name)) {
            this.mapRegisteredSprites.remove(name);
        }
    }

    // Registers a sprite that may be dropped if nothing ends up using it.
    @Override
    public void coarctatio$registerSpriteWeak(ResourceLocation location) {
        String name = location.toString();
        if (this.coarctatio$weakSprites.contains(name) || this.mapRegisteredSprites.containsKey(name)) {
            return;
        }
        this.registerSprite(location);
        this.coarctatio$weakSprites.add(name);
    }

    // Runs at the start of loadTextureAtlas.
    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void coarctatio$handOverWeakSprites(IResourceManager resourceManager, CallbackInfo ci) {
        DynamicModels.rememberWeakSprites(this.coarctatio$weakSprites);
        this.coarctatio$weakSprites.clear();
    }

    // Replaces the call of Stitcher.doStitch in finishLoading.
    @Redirect(method = "finishLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void coarctatio$stitchWithFallback(Stitcher stitcher) {
        DynamicModels.stitchWithFallback(stitcher);
    }
}
