package com.bdmajora.impetus.engine.impl.gpu.device;

import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests device description copying and backend names.
class GpuDeviceInfoTest {
    // Copies the feature set and ignores later changes to the source.
    @Test
    void featuresAreCopied() {
        Set<GpuDeviceFeature> source = EnumSet.of(GpuDeviceFeature.BUFFER_COPY);
        GpuDeviceInfo info = new GpuDeviceInfo(GraphicsBackend.OPENGL, "v", "r", "1", source);
        source.add(GpuDeviceFeature.BUFFER_STORAGE);
        assertEquals(Set.of(GpuDeviceFeature.BUFFER_COPY), info.supportedFeatures());
        assertFalse(info.supports(GpuDeviceFeature.BUFFER_STORAGE));
        assertThrows(UnsupportedOperationException.class, () -> info.supportedFeatures().add(GpuDeviceFeature.BUFFER_STORAGE));
    }

    // Accepts an empty set and a non enum set.
    @Test
    void emptyAndPlainSets() {
        assertTrue(new GpuDeviceInfo(GraphicsBackend.VULKAN, null, null, null, new HashSet<>()).supportedFeatures().isEmpty());
        GpuDeviceInfo info = new GpuDeviceInfo(GraphicsBackend.VULKAN, "a", "b", "c", new HashSet<>(Set.of(GpuDeviceFeature.MULTI_DRAW_INDIRECT)));
        assertTrue(info.supports(GpuDeviceFeature.MULTI_DRAW_INDIRECT));
        assertFalse(info.supports(null));
        assertEquals("a", info.vendor());
        assertEquals("b", info.renderer());
        assertEquals("c", info.version());
        assertEquals(GraphicsBackend.VULKAN, info.backend());
    }

    // Rejects a null set or null feature.
    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> new GpuDeviceInfo(GraphicsBackend.OPENGL, "", "", "", null));
        Set<GpuDeviceFeature> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new GpuDeviceInfo(GraphicsBackend.OPENGL, "", "", "", withNull));
        assertThrows(NullPointerException.class, () -> OpenGlDeviceInfo.capture((DeviceFunctions) null));
    }

    // Names every backend and feature.
    @Test
    void enumNames() {
        assertEquals("OpenGL", GraphicsBackend.OPENGL.getDisplayName());
        assertEquals("Vulkan", GraphicsBackend.VULKAN.getDisplayName());
        assertEquals(6, GpuDeviceFeature.values().length);
        assertSame(GpuDeviceFeature.BUFFER_MAP_RANGE, GpuDeviceFeature.valueOf("BUFFER_MAP_RANGE"));
    }

    // Copies every feature and rejects every mutation of the copy.
    @Test
    void fullSetIsCopiedAndSealed() {
        Set<GpuDeviceFeature> all = EnumSet.allOf(GpuDeviceFeature.class);
        GpuDeviceInfo info = new GpuDeviceInfo(GraphicsBackend.OPENGL, "", "", "", all);
        all.clear();
        assertEquals(EnumSet.allOf(GpuDeviceFeature.class), info.supportedFeatures());
        for (GpuDeviceFeature feature : GpuDeviceFeature.values()) {
            assertTrue(info.supports(feature));
        }
        assertThrows(UnsupportedOperationException.class, () -> info.supportedFeatures().clear());
        assertThrows(UnsupportedOperationException.class, () -> info.supportedFeatures().iterator().remove());
    }

    // Shares the empty set between descriptions without features.
    @Test
    void emptySetsAreShared() {
        GpuDeviceInfo first = new GpuDeviceInfo(GraphicsBackend.OPENGL, "", "", "", EnumSet.noneOf(GpuDeviceFeature.class));
        GpuDeviceInfo second = new GpuDeviceInfo(GraphicsBackend.OPENGL, "", "", "", Set.of());
        assertSame(first.supportedFeatures(), second.supportedFeatures());
        assertFalse(first.supports(GpuDeviceFeature.BUFFER_COPY));
        assertEquals(first, second);
    }

    // Accepts a null backend and null strings.
    @Test
    void nullComponentsAreStored() {
        GpuDeviceInfo info = new GpuDeviceInfo(null, null, null, null, Set.of(GpuDeviceFeature.BUFFER_COPY));
        assertNull(info.backend());
        assertNull(info.vendor());
        assertTrue(info.supports(GpuDeviceFeature.BUFFER_COPY));
    }
}
