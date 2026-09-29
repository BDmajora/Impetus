package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltDataTest {
    @Test
    void visualBitmasksReflectContents() {
        BuiltRenderSectionData empty = new BuiltRenderSectionData();
        assertEquals(0, empty.getVisualBitmaskForSection());
        empty.bake();
        empty.hasBlockGeometry = true;
        assertEquals(1 << RenderVisualsService.HAS_BLOCK_GEOMETRY, empty.getVisualBitmaskForSection());
        BuiltRenderSectionData same = new BuiltRenderSectionData();
        same.hasBlockGeometry = true;
        assertEquals(empty, same);
        assertEquals(empty.hashCode(), same.hashCode());
        same.visibilityData = VisibilityEncoding.EVERYTHING;
        assertNotEquals(empty, same);
        assertNotEquals(empty, null);
        assertNotEquals(empty, "x");

        MinecraftBuiltRenderSectionData<String, Integer> mc = new MinecraftBuiltRenderSectionData<>();
        mc.animatedSprites.add("sprite");
        mc.globalBlockEntities.add(1);
        int flags = mc.getVisualBitmaskForSection();
        assertTrue((flags & (1 << RenderVisualsService.HAS_SPRITES)) != 0);
        assertTrue((flags & (1 << RenderVisualsService.HAS_BLOCK_ENTITIES)) != 0);
        mc.bake();
        assertThrows(UnsupportedOperationException.class, () -> mc.animatedSprites.add("x"));
        MinecraftBuiltRenderSectionData<String, Integer> other = new MinecraftBuiltRenderSectionData<>();
        assertNotEquals(mc, other);
        other.animatedSprites.add("sprite");
        other.globalBlockEntities.add(1);
        other.bake();
        assertEquals(mc, other);
        assertEquals(mc.hashCode(), other.hashCode());
        assertNotEquals(mc, new BuiltRenderSectionData());
        assertNotEquals(mc, null);
        MinecraftBuiltRenderSectionData<String, Integer> culled = new MinecraftBuiltRenderSectionData<>();
        culled.culledBlockEntities.add(2);
        assertTrue((culled.getVisualBitmaskForSection() & (1 << RenderVisualsService.HAS_BLOCK_ENTITIES)) != 0);
        new RenderVisualsService();
    }

    @Test
    void globalSectionDataVisitsOnlyOurData() {
        RenderSection withData = new RenderSection(null, 0, 0, 0);
        MinecraftBuiltRenderSectionData<String, Integer> mc = new MinecraftBuiltRenderSectionData<>();
        RenderSection plain = new RenderSection(null, 1, 0, 0);
        List<MinecraftBuiltRenderSectionData<?, ?>> seen = new ArrayList<>();
        withDataSet(withData, mc);
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(List.of(withData, plain), seen::add);
        assertEquals(List.of(mc), seen);
    }

    private static void withDataSet(RenderSection section, BuiltRenderSectionData data) {
        try {
            var field = RenderSection.class.getDeclaredField("contextData");
            field.setAccessible(true);
            field.set(section, data);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void meshPartsFreeBothBuffers() {
        NativeBuffer vertices = new NativeBuffer(8);
        NativeBuffer indices = new NativeBuffer(8);
        BuiltSectionMeshParts parts = new BuiltSectionMeshParts(vertices, indices, null, Map.of(ModelQuadFacing.POS_Y, new VertexRange(0, 4)));
        parts.free();
        assertThrows(IllegalStateException.class, vertices::getDirectBuffer);
        assertThrows(IllegalStateException.class, indices::getDirectBuffer);
        NativeBuffer only = new NativeBuffer(8);
        new BuiltSectionMeshParts(only, null, null, Map.of()).free();
        assertThrows(IllegalStateException.class, only::getDirectBuffer);
    }
}
