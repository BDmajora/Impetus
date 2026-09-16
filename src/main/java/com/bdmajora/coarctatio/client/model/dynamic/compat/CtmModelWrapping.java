package com.bdmajora.coarctatio.client.model.dynamic.compat;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.client.model.dynamic.DynamicModelBakeEvent;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// CTM wraps every model that uses a connected texture during ModelBakeEvent by walking the loader's stateModels, which dynamic loading leaves empty; this does the same check per model as it bakes, through reflection so CTM need not be present at build time (VintageFix's CTMHelper)
public final class CtmModelWrapping {
    private final Object handler;
    private final Object2BooleanMap<ResourceLocation> wrappedModels;
    private final MethodHandle wrap;
    private final MethodHandle getMetadata;
    private final MethodHandle spriteToAbsolute;
    private final Class<?> ctmBakedModel;
    private final Class<?> multipartModel;
    private final Field partModels;
    private final Class<?> vanillaModelWrapper;
    private final Field wrappedModelBlock;

    private CtmModelWrapping() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> metadataHandler = Class.forName("team.chisel.ctm.client.util.TextureMetadataHandler");
        this.handler = metadataHandler.getField("INSTANCE").get(null);
        Field wrapped = metadataHandler.getDeclaredField("wrappedModels");
        wrapped.setAccessible(true);
        this.wrappedModels = (Object2BooleanMap<ResourceLocation>) wrapped.get(this.handler);
        Method wrapMethod = metadataHandler.getDeclaredMethod("wrap", IModel.class, IBakedModel.class);
        wrapMethod.setAccessible(true);
        this.wrap = lookup.unreflect(wrapMethod);
        Class<?> resourceUtil = Class.forName("team.chisel.ctm.client.util.ResourceUtil");
        this.getMetadata = lookup.unreflect(resourceUtil.getMethod("getMetadata", ResourceLocation.class));
        this.spriteToAbsolute = lookup.unreflect(resourceUtil.getMethod("spriteToAbsolute", ResourceLocation.class));
        this.ctmBakedModel = Class.forName("team.chisel.ctm.client.model.AbstractCTMBakedModel");
        this.multipartModel = Class.forName("net.minecraftforge.client.model.ModelLoader$MultipartModel");
        this.partModels = this.multipartModel.getDeclaredField("partModels");
        this.partModels.setAccessible(true);
        this.vanillaModelWrapper = Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper");
        this.wrappedModelBlock = this.vanillaModelWrapper.getDeclaredField("model");
        this.wrappedModelBlock.setAccessible(true);
    }

    // Registered at mod construction when CTM is present, ahead of the first bake
    public static void register() {
        if (!Loader.isModLoaded("ctm")) {
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.register(new CtmModelWrapping());
            Coarctatio.LOGGER.info("Wrapping dynamically baked models for CTM");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Coarctatio.LOGGER.error("Could not hook CTM; connected textures will not apply to dynamically loaded models", e);
        }
    }

    @SubscribeEvent
    public void onDynamicBake(DynamicModelBakeEvent event) {
        if (!(event.location instanceof ModelResourceLocation) || event.unbakedModel == null) {
            return;
        }
        if (this.ctmBakedModel.isInstance(event.bakedModel) || event.bakedModel.isBuiltInRenderer()) {
            return;
        }
        ModelResourceLocation location = (ModelResourceLocation) event.location;
        boolean shouldWrap;
        synchronized (this.wrappedModels) {
            shouldWrap = this.wrappedModels.getOrDefault(location, false);
        }
        if (!shouldWrap) {
            shouldWrap = this.referencesConnectedTexture(location, event.unbakedModel);
            synchronized (this.wrappedModels) {
                this.wrappedModels.put(location, shouldWrap);
            }
        }
        if (!shouldWrap) {
            return;
        }
        try {
            event.bakedModel = (IBakedModel) this.wrap.invoke(this.handler, event.unbakedModel, event.bakedModel);
        } catch (Throwable e) {
            Coarctatio.LOGGER.error("Could not wrap model {} for CTM", location, e);
        }
    }

    // Breadth-first over the model, its parents and its dependencies, stopping at the first texture with CTM metadata
    private boolean referencesConnectedTexture(ModelResourceLocation root, IModel rootModel) {
        Deque<ResourceLocation> pending = new ArrayDeque<>();
        Set<ResourceLocation> seen = new HashSet<>();
        pending.push(root);
        seen.add(root);
        while (!pending.isEmpty()) {
            ResourceLocation next = pending.pop();
            IModel model;
            try {
                model = next == root ? rootModel : ModelLoaderRegistry.getModel(next);
            } catch (Exception e) {
                continue;
            }
            Set<ResourceLocation> textures = new HashSet<>(model.getTextures());
            Set<ResourceLocation> dependencies = new HashSet<>(model.getDependencies());
            try {
                if (this.vanillaModelWrapper.isInstance(model)) {
                    // getTextures resolves "#name" references but not the parents' own texture maps
                    ModelBlock parent = ((ModelBlock) this.wrappedModelBlock.get(model)).parent;
                    while (parent != null) {
                        for (String texture : parent.textures.values()) {
                            if (!texture.startsWith("#")) {
                                textures.add(new ResourceLocation(texture));
                            }
                        }
                        parent = parent.parent;
                    }
                }
                if (this.multipartModel.isInstance(model)) {
                    // A multipart model reports no textures or dependencies of its own; its parts do
                    Map<?, IModel> parts = (Map<?, IModel>) this.partModels.get(model);
                    textures.clear();
                    for (IModel part : parts.values()) {
                        textures.addAll(part.getTextures());
                        dependencies.addAll(part.getDependencies());
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            for (ResourceLocation texture : textures) {
                if (this.hasMetadata(texture)) {
                    return true;
                }
            }
            for (ResourceLocation dependency : dependencies) {
                if (seen.add(dependency)) {
                    pending.push(dependency);
                }
            }
        }
        return false;
    }

    private boolean hasMetadata(ResourceLocation texture) {
        try {
            return this.getMetadata.invoke(this.spriteToAbsolute.invoke(texture)) != null;
        } catch (Throwable e) {
            return false;
        }
    }
}
