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

// The scanned sprites go in ahead of TextureStitchEvent.Pre as weak registrations a mod's own registration replaces, since setTextureEntry refuses a name already present; and the stitch gets a retry path for when the scan collected more than fits
@Mixin(TextureMap.class)
public abstract class TextureMapWeakSpriteMixin implements WeakSpriteTextureMap {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapRegisteredSprites;

    @Shadow
    public abstract TextureAtlasSprite registerSprite(ResourceLocation location);

    private final Set<String> coarctatio$weakSprites = new ObjectOpenHashSet<>();

    @Inject(method = "loadSprites", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre(Lnet/minecraft/client/renderer/texture/TextureMap;)V", remap = false))
    private void coarctatio$registerScannedSprites(IResourceManager resourceManager, net.minecraft.client.renderer.texture.ITextureMapPopulator populator, CallbackInfo ci) {
        this.coarctatio$weakSprites.clear();
        DynamicModels.registerDiscoveredSprites((TextureMap) (Object) this);
    }

    @Inject(method = "registerSprite", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private void coarctatio$replaceWeak(ResourceLocation location, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        this.coarctatio$forgetWeak(location.toString());
    }

    @Inject(method = "setTextureEntry", at = @At("HEAD"), remap = false)
    private void coarctatio$replaceWeakEntry(TextureAtlasSprite sprite, CallbackInfoReturnable<Boolean> cir) {
        this.coarctatio$forgetWeak(sprite.getIconName());
    }

    private void coarctatio$forgetWeak(String name) {
        if (this.coarctatio$weakSprites.remove(name)) {
            this.mapRegisteredSprites.remove(name);
        }
    }

    @Override
    public void coarctatio$registerSpriteWeak(ResourceLocation location) {
        String name = location.toString();
        if (this.coarctatio$weakSprites.contains(name) || this.mapRegisteredSprites.containsKey(name)) {
            return;
        }
        this.registerSprite(location);
        this.coarctatio$weakSprites.add(name);
    }

    // From here on a registration is a registration, TextureStitchEvent.Post included; what stays weak is only the right to be dropped if the atlas overflows
    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void coarctatio$handOverWeakSprites(IResourceManager resourceManager, CallbackInfo ci) {
        DynamicModels.rememberWeakSprites(this.coarctatio$weakSprites);
        this.coarctatio$weakSprites.clear();
    }

    @Redirect(method = "finishLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void coarctatio$stitchWithFallback(Stitcher stitcher) {
        DynamicModels.stitchWithFallback(stitcher);
    }
}
