package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BakedModelProviderTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Minecraft client = Mc.client();
        TextureMap textures = Mc.mock(TextureMap.class);
        TextureAtlasSprite missing = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(missing, "iconName", "missingno");
        when(textures.getMissingSprite()).thenReturn(missing);
        when(textures.getAtlasSprite(any())).thenReturn(missing);
        when(client.getTextureMapBlocks()).thenReturn(textures);
    }

    private static UnbakedModelProvider models() {
        return new UnbakedModelProvider(new java.util.LinkedHashSet<>());
    }

    private static IModel unbaked(IBakedModel baked) {
        IModel model = mock(IModel.class);
        when(model.bake(any(), any(), any())).thenReturn(baked);
        return model;
    }

    @Test
    void aModelIsBakedOnRequestAndThenServedFromTheCache() {
        UnbakedModelProvider models = models();
        BakedModelProvider provider = new BakedModelProvider(models);
        ModelResourceLocation location = new ModelResourceLocation("minecraft:stone", "normal");
        IBakedModel baked = mock(IBakedModel.class);
        models.putObject(location, unbaked(baked));

        assertNull(provider.getIfBaked(location));
        assertSame(baked, provider.getObject(location));
        assertSame(baked, provider.getIfBaked(location));
        assertEquals(1, provider.cachedCount());
        // The registry's own map is never used
        assertTrue(Mixins.<Map<ModelResourceLocation, IBakedModel>>call(provider, "createUnderlyingMap").isEmpty());
    }

    @Test
    void aModelAModPutIsHeldPermanentlyAndListed() {
        BakedModelProvider provider = new BakedModelProvider(models());
        ModelResourceLocation location = new ModelResourceLocation("examplemod:machine", "normal");
        IBakedModel model = mock(IBakedModel.class);

        provider.putObject(location, model);
        assertSame(model, provider.getObject(location));
        assertEquals(1, provider.permanentCount());
        assertEquals(java.util.Set.of(location), provider.getKeys());
        assertSame(model, provider.iterator().next());

        // Invalidating drops the baked and the unbaked entry, for a mod that replaced a model after a bake
        provider.invalidate(location);
        assertNull(provider.getIfBaked(location));
    }

    @Test
    void aKnownVariantThatFailsAnswersTheMissingModel() {
        UnbakedModelProvider models = models();
        BakedModelProvider provider = new BakedModelProvider(models);
        IBakedModel missing = mock(IBakedModel.class);
        Mixins.set(provider, "missingModel", missing);

        // An item variant that cannot be loaded answers the missing model rather than null
        ModelResourceLocation item = new ModelResourceLocation("examplemod:thing", "inventory");
        ModelLocations.ITEM_VARIANTS.add(item);
        assertSame(missing, provider.getObject(item));

        // So does a block variant the state mapper produced
        ModelResourceLocation block = new ModelResourceLocation("examplemod:machine", "normal");
        // The table holds the trimmable sets the location scan builds, so a test entry has to be one too
        ModelLocations.VARIANTS_BY_BLOCKSTATE.put(new ResourceLocation("examplemod:machine"),
                new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>(java.util.List.of(block)));
        assertSame(missing, provider.getObject(block));

        // Anything else is simply absent, the way vanilla's registry answered
        assertNull(provider.getObject(new ModelResourceLocation("examplemod:nothing", "normal")));
    }

    @Test
    void aBakeThatThrowsIsReportedAndFallsBackTheSameWay() {
        UnbakedModelProvider models = models();
        BakedModelProvider provider = new BakedModelProvider(models);
        ModelResourceLocation location = new ModelResourceLocation("examplemod:broken", "normal");
        IModel broken = mock(IModel.class);
        when(broken.bake(any(), any(), any())).thenThrow(new IllegalStateException("bad model"));
        models.putObject(location, broken);

        assertNull(provider.getObject(location));
    }

    @Test
    void theStateStoreResolvesThroughBlockModelShapes() {
        BakedModelProvider provider = new BakedModelProvider(models());
        Map<IBlockState, IBakedModel> store = provider.stateStore();

        BlockModelShapes shapes = Mc.mock(BlockModelShapes.class, LocationAwareBlockModelShapes.class);
        BlockRendererDispatcher dispatcher = mock(BlockRendererDispatcher.class);
        when(dispatcher.getBlockModelShapes()).thenReturn(shapes);
        when(Minecraft.getMinecraft().getBlockRendererDispatcher()).thenReturn(dispatcher);

        IBlockState stone = Blocks.STONE.getDefaultState();
        IBakedModel model = mock(IBakedModel.class);
        when(shapes.getModelForState(stone)).thenReturn(model);
        assertSame(model, store.get(stone));
        assertNull(store.get("not a state"));

        // Writing pins the model for that state's location, which is what a mod replacing a model expects
        ModelResourceLocation location = new ModelResourceLocation("minecraft:stone", "normal");
        when(((LocationAwareBlockModelShapes) shapes).coarctatio$locationForState(stone)).thenReturn(location);
        IBakedModel replacement = mock(IBakedModel.class);
        assertNull(store.put(stone, replacement));
        assertSame(replacement, provider.getObject(location));
        // A state with no location of its own is simply not stored
        when(((LocationAwareBlockModelShapes) shapes).coarctatio$locationForState(any())).thenReturn(null);
        assertNull(store.put(Blocks.DIRT.getDefaultState(), replacement));

        store.putAll(Map.of(Blocks.DIRT.getDefaultState(), replacement));
    }

    @Test
    void theStateStoreAnswersLikeTheFieldItReplaced() {
        BakedModelProvider provider = new BakedModelProvider(models());
        Map<IBlockState, IBakedModel> store = provider.stateStore();
        IBakedModel model = mock(IBakedModel.class);
        provider.putObject(new ModelResourceLocation("minecraft:stone", "normal"), model);

        // Mods probe this map rather than iterate it, so it claims to hold everything and lists nothing
        assertEquals(1, store.size());
        assertFalse(store.isEmpty());
        assertTrue(store.containsKey(Blocks.STONE.getDefaultState()));
        assertTrue(store.containsValue(model));
        assertTrue(store.keySet().isEmpty());
        assertTrue(store.entrySet().isEmpty());
        assertTrue(store.values().contains(model));
        assertNull(store.remove(Blocks.STONE.getDefaultState()));
        store.clear();
        assertEquals(1, store.size());
    }
}
