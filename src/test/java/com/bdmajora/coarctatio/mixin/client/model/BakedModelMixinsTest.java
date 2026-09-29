package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.collections.ArrayBackedLinkedMap;
import com.bdmajora.coarctatio.collections.FixedArrayList;
import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.coarctatio.dedup.TransformCaches;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.WeightedBakedModel;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BakedModelMixinsTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshPools() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
        ModelCaches.open();
        TransformCaches.open();
    }

    @Test
    void aQuadSubclassIsCountedRatherThanPooled() {
        // Only an exact BakedQuad is pooled, since the subclasses mutate their array after construction; a mixin
        // instance is always a subclass here, which is the rejected case
        CoarctatioBakedQuadMixin quad = Mixins.instance(CoarctatioBakedQuadMixin.class);
        assertNotEquals(BakedQuad.class, ((Object) quad).getClass());
        ModelCaches.QUADS.deduplicate(new int[28]);
        int[] own = new int[28];
        Mixins.set(quad, "vertexData", own);
        Mixins.call(quad, "coarctatio$poolVertexData", Mixins.ci());
        assertSame(own, Mixins.get(quad, "vertexData"));
        assertTrue(ModelCaches.skippedSummary().contains(((Object) quad).getClass().getName()));
    }

    @Test
    void aSimpleModelsQuadListsAndTransformsAreCompacted() {
        SimpleBakedModelMixin model = Mixins.instance(SimpleBakedModelMixin.class);
        Mixins.set(model, "generalQuads", new ArrayList<BakedQuad>());
        Map<EnumFacing, List<BakedQuad>> faces = new HashMap<>();
        faces.put(EnumFacing.UP, new ArrayList<>());
        Mixins.set(model, "faceQuads", faces);
        Mixins.set(model, "cameraTransforms", ItemCameraTransforms.DEFAULT);
        Mixins.set(model, "itemOverrideList", new ItemOverrideList(ImmutableList.of()));

        Mixins.call(model, "coarctatio$compactQuadLists", Mixins.ci());
        assertInstanceOf(FixedArrayList.class, Mixins.get(model, "generalQuads"));
        Map<EnumFacing, List<BakedQuad>> compacted = Mixins.get(model, "faceQuads");
        assertInstanceOf(EnumMap.class, compacted);
        assertInstanceOf(FixedArrayList.class, compacted.get(EnumFacing.UP));
        // Every model carries these two and almost none of them differ
        assertSame(ItemCameraTransforms.DEFAULT, Mixins.get(model, "cameraTransforms"));
        assertSame(ItemOverrideList.NONE, Mixins.get(model, "itemOverrideList"));
    }

    @Test
    void aWeightedModelsListIsCompacted() {
        WeightedBakedModelMixin model = Mixins.instance(WeightedBakedModelMixin.class);
        Mixins.set(model, "models", new ArrayList<WeightedBakedModel.WeightedModel>());
        Mixins.call(model, "coarctatio$compactModelList", Mixins.ci());
        assertInstanceOf(FixedArrayList.class, Mixins.get(model, "models"));
    }

    @Test
    void multipartSelectorsKeepTheirOrderInAFlatMap() {
        MultipartBakedModelMixin model = Mixins.instance(MultipartBakedModelMixin.class);
        Map<Predicate<IBlockState>, IBakedModel> selectors = new LinkedHashMap<>();
        Predicate<IBlockState> first = state -> true;
        Predicate<IBlockState> second = state -> false;
        selectors.put(first, mock(IBakedModel.class));
        selectors.put(second, mock(IBakedModel.class));
        Mixins.set(model, "selectors", selectors);

        Mixins.call(model, "coarctatio$compactSelectors", Mixins.ci());
        Map<Predicate<IBlockState>, IBakedModel> compacted = Mixins.get(model, "selectors");
        assertInstanceOf(ArrayBackedLinkedMap.class, compacted);
        // Selectors apply in declaration order, so the order has to survive
        assertSame(first, compacted.keySet().iterator().next());

        // A mod re-baking in place must not copy an already compacted map
        Mixins.call(model, "coarctatio$compactSelectors", Mixins.ci());
        assertSame(compacted, Mixins.get(model, "selectors"));
    }
}
