package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProbeTest {
    private final OsKind realOs = OsKind.current();

    @AfterEach
    void restoreOs() {
        Statics.set(OsKind.class, "CURRENT", realOs);
    }

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

    @Test
    void adaptersComeFromOneSharedProbeAndNeverThrow() {
        CompletableFuture<List<GraphicsAdapterInfo>> first = GraphicsAdapterProbe.prefetch();
        assertSame(first, GraphicsAdapterProbe.prefetch());
        assertEquals(first.join(), GraphicsAdapterProbe.adapters(10_000));

        AtomicReference<CompletableFuture<List<GraphicsAdapterInfo>>> shared = Statics.get(GraphicsAdapterProbe.class, "SHARED");
        try {
            // A probe slower than the wait, an interrupted waiter and a failed probe all read as no adapters
            shared.set(new CompletableFuture<>());
            assertTrue(GraphicsAdapterProbe.adapters(1).isEmpty());
            Thread.currentThread().interrupt();
            assertTrue(GraphicsAdapterProbe.adapters(10_000).isEmpty());
            assertTrue(Thread.interrupted());
            shared.set(CompletableFuture.failedFuture(new IllegalStateException("probe")));
            assertTrue(GraphicsAdapterProbe.adapters(10_000).isEmpty());
        } finally {
            shared.set(first);
        }
    }

    @Test
    void probeDegradesToEmptyOnEveryPlatform(@TempDir Path dir) throws Exception {
        assertNotNull(GraphicsAdapterProbe.probe());
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        assertTrue(GraphicsAdapterProbe.probe().isEmpty());
        Statics.set(OsKind.class, "CURRENT", OsKind.MACOS);
        assertTrue(GraphicsAdapterProbe.probe().isEmpty());

        Path vendor = dir.resolve("vendor");
        Files.writeString(vendor, "0x10de\n");
        assertEquals("0x10de", Statics.call(GraphicsAdapterProbe.class, "readTrimmed", vendor));
        assertEquals("", Statics.call(GraphicsAdapterProbe.class, "readTrimmed", dir.resolve("missing")));
        assertEquals(0x10de, (int) Statics.call(GraphicsAdapterProbe.class, "parsePciId", "0x10de"));
        assertEquals(0, (int) Statics.call(GraphicsAdapterProbe.class, "parsePciId", "junk"));
        Path uevent = dir.resolve("uevent");
        Files.writeString(uevent, "PCI_ID=1\nDRIVER=nvidia\n");
        assertEquals("nvidia", Statics.call(GraphicsAdapterProbe.class, "readUeventValue", uevent, "DRIVER"));
        assertEquals("", Statics.call(GraphicsAdapterProbe.class, "readUeventValue", uevent, "NOPE"));
        assertEquals("", Statics.call(GraphicsAdapterProbe.class, "readUeventValue", dir.resolve("missing"), "DRIVER"));
        assertEquals(GraphicsVendor.NVIDIA, Statics.call(GraphicsAdapterProbe.class, "classifyWindowsVendor", "NVIDIA GeForce"));
        assertEquals(GraphicsVendor.AMD, Statics.call(GraphicsAdapterProbe.class, "classifyWindowsVendor", "Radeon RX"));
        assertEquals(GraphicsVendor.INTEL, Statics.call(GraphicsAdapterProbe.class, "classifyWindowsVendor", "Intel UHD"));
        assertEquals(GraphicsVendor.OTHER, Statics.call(GraphicsAdapterProbe.class, "classifyWindowsVendor", "Something"));
        List<String> lines = Statics.call(GraphicsAdapterProbe.class, "runProcess", (Object) new String[]{"echo", "hello"});
        assertEquals(List.of("hello"), lines);
        List<String> missing = Statics.call(GraphicsAdapterProbe.class, "runProcess", (Object) new String[]{"definitely-not-a-command-" + System.nanoTime()});
        assertTrue(missing.isEmpty());
    }
}
