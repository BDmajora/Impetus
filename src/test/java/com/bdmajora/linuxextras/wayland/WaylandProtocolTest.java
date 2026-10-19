package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.wayland.FakeWayland.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.*;

class WaylandProtocolTest {
    private FakeWayland wayland;
    private Function<Arena, SymbolLookup> realLibrary;

    @BeforeEach
    void compositor() throws ReflectiveOperationException {
        this.wayland = new FakeWayland();
        this.realLibrary = WaylandClient.LIBRARY.get();
        WaylandClient.LIBRARY.set(arena -> this.wayland.lookup());
    }

    @AfterEach
    void everythingFreed() {
        try {
            assertNull(this.wayland.failure);
            assertEquals(Set.of(), this.wayland.live);
        } finally {
            WaylandClient.LIBRARY.set(this.realLibrary);
            this.wayland.close();
        }
    }

    private List<String> outputNames() {
        return WaylandOutputOrder.outputNames(this.wayland.display.address());
    }

    @Test
    void proxiesWithoutADestructorAreFreedDirectly() throws Throwable {
        wayland.globals.put("wl_shm", 3);
        try (Arena arena = Arena.ofConfined()) {
            WaylandClient client = new WaylandClient(arena, wayland.display.address());
            try {
                assertEquals(3, client.globals("wl_shm")[0]);
                MemorySegment proxy = client.bind(3, client.dataSymbol("wl_shm_interface"), 1);
                client.destroyProxy(proxy);
            } finally {
                client.close();
            }
        }
    }

    @Test
    void thePreSdlQueryOwnsAndClosesItsConnection() {
        wayland.globals.put("kde_output_order_v1", 7);
        wayland.outputOrder = List.of("DP-2", "DP-1");
        assertEquals(List.of("DP-2", "DP-1"), WaylandOutputOrder.outputNames());
        assertTrue(wayland.connected);
        assertTrue(wayland.disconnected);
    }

    @Test
    void kdeListsItsOutputsPrimaryFirst() {
        this.wayland.globals.put("wl_compositor", 1);
        this.wayland.globals.put("kde_output_order_v1", 7);
        this.wayland.outputOrder = List.of("DP-2", "DP-1", "HDMI-A-1");
        assertEquals(List.of("DP-2", "DP-1", "HDMI-A-1"), outputNames());
        // The registry is asked for on the private wrapper, the order bound at version 1 by its registry name and destroyed after its one answer
        assertEquals(List.of(
                new Request("wl_display", 1, "wl_registry", 0, List.of(0L)),
                new Request("wl_registry", 0, "kde_output_order_v1", 0, List.of(7, "kde_output_order_v1", 1, 0L)),
                new Request("kde_output_order_v1", 0, null, WaylandClient.MARSHAL_FLAG_DESTROY, List.of())), this.wayland.requests);
    }

    @Test
    void otherCompositorsHaveNoOrderToGive() {
        this.wayland.globals.put("wl_compositor", 1);
        assertEquals(List.of(), outputNames());
        assertEquals(List.of("wl_display.1->wl_registry"), this.wayland.summary());
    }

    @Test
    void aLostConnectionGivesNoOrderAndLeavesNothingBehind() {
        this.wayland.globals.put("kde_output_order_v1", 7);
        this.wayland.outputOrder = List.of("DP-1");
        // While listing globals
        this.wayland.failingRoundtrip = 1;
        assertEquals(List.of(), outputNames());
        assertEquals(List.of("wl_display.1->wl_registry"), this.wayland.summary());
    }

    @Test
    void aConnectionLostWhileReadingTheOrderStillDestroysIt() {
        this.wayland.globals.put("kde_output_order_v1", 7);
        this.wayland.outputOrder = List.of("DP-1");
        this.wayland.failingRoundtrip = 2;
        assertEquals(List.of(), outputNames());
        assertEquals(List.of("wl_display.1->wl_registry", "wl_registry.0->kde_output_order_v1", "kde_output_order_v1.0"), this.wayland.summary());
    }

    @Test
    void aMissingLibwaylandPieceGivesNoOrder() {
        // A missing function stops the client before anything is made
        WaylandClient.LIBRARY.set(arena -> this.wayland.lookup("wl_proxy_destroy"));
        assertEquals(List.of(), outputNames());
        assertEquals(List.of(), this.wayland.requests);
        // A missing interface description stops it after the queue and wrapper, which still get freed
        WaylandClient.LIBRARY.set(arena -> this.wayland.lookup("wl_registry_interface"));
        assertEquals(List.of(), outputNames());
        assertEquals(List.of(), this.wayland.requests);
        // A proxy libwayland could not make
        WaylandClient.LIBRARY.set(arena -> this.wayland.lookup());
        this.wayland.refuseProxies = true;
        assertEquals(List.of(), outputNames());
        assertEquals(List.of("wl_display.1->wl_registry"), this.wayland.summary());
    }

    @Test
    void theRealLibraryIsLibwaylandClient() {
        try (Arena arena = Arena.ofConfined()) {
            assertTrue(WaylandClient.systemLibrary(arena).find("wl_display_roundtrip_queue").isPresent());
        }
    }

}
