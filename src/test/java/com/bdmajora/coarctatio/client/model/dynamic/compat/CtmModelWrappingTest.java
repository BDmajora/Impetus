package com.bdmajora.coarctatio.client.model.dynamic.compat;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModelBakeEvent;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelLoaderRegistryAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.multipart.Multipart;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import team.chisel.ctm.client.model.AbstractCTMBakedModel;
import team.chisel.ctm.client.util.ResourceUtil;
import team.chisel.ctm.client.util.TextureMetadataHandler;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CtmModelWrappingTest {
    private final IBakedModel baked = mock(IBakedModel.class);
    private final IBakedModel wrapped = mock(IBakedModel.class);

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    @AfterEach
    void forgetCtmState() {
        TextureMetadataHandler.wrapped = null;
        ResourceUtil.WITH_METADATA.clear();
        ResourceUtil.UNREADABLE.clear();
        Mixins.<Object2BooleanMap<ResourceLocation>>get(TextureMetadataHandler.INSTANCE, "wrappedModels").clear();
    }

    private static IModel model(String... textures) {
        IModel model = mock(IModel.class);
        List<ResourceLocation> locations = new java.util.ArrayList<>();
        for (String texture : textures) {
            locations.add(new ResourceLocation(texture));
        }
        when(model.getTextures()).thenReturn(locations);
        when(model.uvlock(anyBoolean())).thenReturn(model);
        when(model.getDefaultState()).thenReturn(TRSRTransformation.identity());
        return model;
    }

    private static void connected(String sprite) {
        ResourceLocation location = new ResourceLocation(sprite);
        ResourceUtil.WITH_METADATA.add(new ResourceLocation(location.getNamespace(), "textures/" + location.getPath() + ".png"));
    }

    private IBakedModel bake(CtmModelWrapping wrapping, ResourceLocation location, IModel unbaked, IBakedModel model) {
        DynamicModelBakeEvent event = new DynamicModelBakeEvent(location, unbaked, model);
        wrapping.onDynamicBake(event);
        return event.bakedModel;
    }

    @Test
    void theHookIsOnlyRegisteredWithCtmPresent() {
        Mc.forge();
        CtmModelWrapping.register();
        Map<Object, ?> listeners = Mixins.get(MinecraftForge.EVENT_BUS, "listeners");
        assertTrue(listeners.keySet().stream().noneMatch(CtmModelWrapping.class::isInstance));

        // The bus names the registering mod, so one has to be active
        Mc.forge("ctm");
        LoadController controller = Mixins.get(Loader.instance(), "modController");
        when(controller.activeContainer()).thenReturn(mock(ModContainer.class));
        CtmModelWrapping.register();
        Object hook = listeners.keySet().stream().filter(CtmModelWrapping.class::isInstance).findFirst().orElseThrow();
        MinecraftForge.EVENT_BUS.unregister(hook);
    }

    @Test
    void onlyModelsThatReachAConnectedTextureAreWrapped() {
        CtmModelWrapping wrapping = Mixins.construct(CtmModelWrapping.class);
        TextureMetadataHandler.wrapped = wrapped;
        ModelResourceLocation location = new ModelResourceLocation("examplemod:pillar", "normal");
        IModel plain = model("examplemod:blocks/plain");

        // A location that is not a variant, or a load that failed, is not CTM's business
        assertSame(baked, bake(wrapping, new ResourceLocation("examplemod:block/pillar"), plain, baked));
        assertSame(baked, bake(wrapping, location, null, baked));
        // Nor is a model CTM already wrapped, or one drawn by its own renderer
        IBakedModel ctm = mock(AbstractCTMBakedModel.class);
        assertSame(ctm, bake(wrapping, location, plain, ctm));
        IBakedModel builtin = mock(IBakedModel.class);
        when(builtin.isBuiltInRenderer()).thenReturn(true);
        assertSame(builtin, bake(wrapping, location, plain, builtin));

        // A model without connected textures stays as baked, and one with them is wrapped
        assertSame(baked, bake(wrapping, location, plain, baked));
        connected("examplemod:blocks/pillar_top");
        ModelResourceLocation top = new ModelResourceLocation("examplemod:pillar", "axis=y");
        assertSame(wrapped, bake(wrapping, top, model("examplemod:blocks/pillar_top"), baked));
        // The answer is remembered per location, so the next bake wraps without walking the model again
        assertSame(wrapped, bake(wrapping, top, plain, baked));

        // An unreadable texture has no metadata; a wrap that fails leaves the model as baked
        ResourceUtil.UNREADABLE.add(new ResourceLocation("examplemod", "textures/blocks/pillar_top.png"));
        assertSame(baked, bake(wrapping, new ModelResourceLocation("examplemod:pillar", "axis=z"), model("examplemod:blocks/pillar_top"), baked));
        TextureMetadataHandler.wrapped = null;
        assertSame(baked, bake(wrapping, top, plain, baked));
    }

    @Test
    void theWalkFollowsDependenciesParentsAndMultipartParts() throws Exception {
        CtmModelWrapping wrapping = Mixins.construct(CtmModelWrapping.class);
        TextureMetadataHandler.wrapped = wrapped;
        connected("examplemod:blocks/connected");

        // A dependency is loaded through the registry; one that fails to load is skipped
        ResourceLocation parentLocation = new ResourceLocation("examplemod:block/connected_parent");
        ModelLoaderRegistryAccessor.coarctatio$cache().put(parentLocation, model("examplemod:blocks/connected"));
        IModel child = model("examplemod:blocks/plain");
        when(child.getDependencies()).thenReturn(List.of(new ResourceLocation("examplemod:block/absent"), parentLocation));
        assertSame(wrapped, bake(wrapping, new ModelResourceLocation("examplemod:child", "normal"), child, baked));

        // Forge's vanilla wrapper does not report its parents' textures, so the parent chain is read directly
        ModelBlock inner = ModelBlock.deserialize(new StringReader("{\"textures\":{\"side\":\"#all\"}}"));
        inner.parent = ModelBlock.deserialize(new StringReader("{\"textures\":{\"all\":\"examplemod:blocks/connected\",\"end\":\"#all\"}}"));
        Constructor<?> wrapper = Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper")
                .getDeclaredConstructor(ModelLoader.class, ResourceLocation.class, ModelBlock.class, boolean.class,
                        net.minecraftforge.client.model.animation.ModelBlockAnimation.class);
        wrapper.setAccessible(true);
        ModelResourceLocation column = new ModelResourceLocation("examplemod:column", "normal");
        IModel vanilla = (IModel) wrapper.newInstance(Mc.mock(ModelLoader.class), column, inner, false, null);
        assertSame(wrapped, bake(wrapping, column, vanilla, baked));

        // A multipart model reports nothing itself; its parts carry the textures
        ModelLoaderRegistryAccessor.coarctatio$cache().put(new ResourceLocation("examplemod:block/fence_post"), model("examplemod:blocks/connected"));
        Multipart multipart = ModelBlockDefinition.parseFromReader(
                new StringReader("{\"multipart\":[{\"apply\":{\"model\":\"examplemod:fence_post\"}}]}"),
                new ResourceLocation("examplemod:fence")).getMultipartData();
        Constructor<?> parts = Class.forName("net.minecraftforge.client.model.ModelLoader$MultipartModel")
                .getDeclaredConstructor(ResourceLocation.class, Multipart.class);
        parts.setAccessible(true);
        ModelResourceLocation fence = new ModelResourceLocation("examplemod:fence", "north=true");
        IModel multipartModel = (IModel) parts.newInstance(new ResourceLocation("examplemod:fence"), multipart);
        assertSame(wrapped, bake(wrapping, fence, multipartModel, baked));
    }
}
