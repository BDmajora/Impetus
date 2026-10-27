package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests the vendor classification of GraphicsVendor
class ProbeTest {
    // Verifies vendors classify from PCI ids and from the GL context strings
    @Test
    void vendorsClassifyFromPciIdsAndContextStrings() {
        assertEquals(GraphicsVendor.NVIDIA, GraphicsVendor.fromPciVendorId(0x10DE));
        assertEquals(GraphicsVendor.AMD, GraphicsVendor.fromPciVendorId(0x1002));
        assertEquals(GraphicsVendor.AMD, GraphicsVendor.fromPciVendorId(0x1022));
        assertEquals(GraphicsVendor.INTEL, GraphicsVendor.fromPciVendorId(0x8086));
        assertEquals(GraphicsVendor.OTHER, GraphicsVendor.fromPciVendorId(1));
        assertEquals(GraphicsVendor.MESA, GraphicsVendor.fromContext(new GlContextInfo("AMD", "Radeon", "4.6 Mesa 23")));
        assertEquals(GraphicsVendor.MESA, GraphicsVendor.fromContext(new GlContextInfo("X.Org", "r", "v")));
        assertEquals(GraphicsVendor.NVIDIA, GraphicsVendor.fromContext(new GlContextInfo("NVIDIA Corporation", "r", "v")));
        assertEquals(GraphicsVendor.AMD, GraphicsVendor.fromContext(new GlContextInfo("ATI Technologies", "r", "v")));
        assertEquals(GraphicsVendor.INTEL, GraphicsVendor.fromContext(new GlContextInfo("Intel", "r", "v")));
        assertEquals(GraphicsVendor.OTHER, GraphicsVendor.fromContext(new GlContextInfo("Apple", "r", "v")));
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.INTEL, "n", "1.0");
        assertEquals("n", info.name());
    }
}
