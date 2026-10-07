package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.wayland.FakeWayland.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.ByteBuffer;
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

    // An xdg_toplevel proxy as libwayland lays one out: a pointer to its wl_interface, whose first field points to the name
    private static MemorySegment toplevel(Arena arena) {
        MemorySegment wlInterface = arena.allocate(WaylandClient.WL_INTERFACE);
        wlInterface.set(ADDRESS, 0L, arena.allocateFrom("xdg_toplevel"));
        MemorySegment proxy = arena.allocate(16L, 8L);
        proxy.set(ADDRESS, 0L, wlInterface);
        return proxy;
    }

    // Square RGBA images of one repeated pixel
    private static ByteBuffer icon(int size, int red, int green, int blue, int alpha) {
        ByteBuffer pixels = ByteBuffer.allocate(size * size * 4);
        for (int i = 0; i < size * size; i++) {
            pixels.put((byte) red).put((byte) green).put((byte) blue).put((byte) alpha);
        }
        return pixels.flip();
    }

    private void setIcon(MemorySegment window, MemorySegment surface, ByteBuffer... icons) {
        try (MockedStatic<GLFWNativeWayland> glfw = Mockito.mockStatic(GLFWNativeWayland.class)) {
            glfw.when(() -> GLFWNativeWayland.glfwGetWaylandWindow(window.address())).thenReturn(surface.address());
            glfw.when(GLFWNativeWayland::glfwGetWaylandDisplay).thenReturn(this.wayland.display.address());
            WaylandWindowIcon.setIcon(window.address(), icons);
        }
    }

    @Test
    void theIconGoesToTheToplevelGlfwKeepsNearItsSurface() {
        this.wayland.globals.put("wl_shm", 3);
        this.wayland.globals.put("xdg_toplevel_icon_manager_v1", 9);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment surface = arena.allocate(8L, 8L);
            MemorySegment toplevel = toplevel(arena);
            // GLFW's window data: the wl_surface, then a few fields on, the toplevel
            MemorySegment window = arena.allocate(16384L + 1024L, 8L);
            window.set(JAVA_LONG, 256L, surface.address());
            window.set(JAVA_LONG, 256L + 40L, toplevel.address());

            setIcon(window, surface, icon(2, 255, 0, 0, 128), icon(1, 10, 20, 30, 255));
            assertEquals(List.of("wl_display.1->wl_registry",
                    "wl_registry.0->xdg_toplevel_icon_manager_v1", "wl_registry.0->wl_shm",
                    "wl_shm.0->wl_shm_pool", "wl_shm_pool.0->wl_buffer", "wl_shm_pool.0->wl_buffer",
                    "xdg_toplevel_icon_manager_v1.1->xdg_toplevel_icon_v1",
                    "xdg_toplevel_icon_v1.2", "xdg_toplevel_icon_v1.2",
                    "xdg_toplevel_icon_manager_v1.2", "xdg_toplevel_icon_v1.0",
                    "wl_buffer.0", "wl_buffer.0", "wl_shm_pool.1", "xdg_toplevel_icon_manager_v1.0"), this.wayland.summary());

            List<Request> requests = this.wayland.requests;
            assertEquals(List.of(9, "xdg_toplevel_icon_manager_v1", 1, 0L), requests.get(1).arguments());
            // One pool for both, each buffer at its offset with its size, stride and ARGB8888
            assertEquals(20, requests.get(3).arguments().get(2));
            assertEquals(List.of(0L, 0, 2, 2, 8, 0), requests.get(4).arguments());
            assertEquals(List.of(0L, 16, 1, 1, 4, 0), requests.get(5).arguments());
            assertEquals(1, requests.get(7).arguments().get(1));
            assertEquals(toplevel.address(), requests.get(9).arguments().get(0));
            // Premultiplied, little-endian ARGB: half-transparent red, then an opaque pixel with its channels swapped round
            byte[] expected = new byte[20];
            for (int i = 0; i < 16; i += 4) {
                expected[i + 2] = (byte) 128;
                expected[i + 3] = (byte) 128;
            }
            expected[16] = 30;
            expected[17] = 20;
            expected[18] = 10;
            expected[19] = (byte) 255;
            assertArrayEquals(expected, this.wayland.pool);
        }
    }

    @Test
    void underLibdecorTheToplevelSitsBehindTheFrame() {
        this.wayland.globals.put("wl_shm", 3);
        this.wayland.globals.put("xdg_toplevel_icon_manager_v1", 9);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment surface = arena.allocate(8L, 8L);
            MemorySegment toplevel = toplevel(arena);
            // The frame's first field points at libdecor's private data, which holds the surface and the toplevel
            MemorySegment frameData = arena.allocate(512L, 8L);
            frameData.set(JAVA_LONG, 64L, surface.address());
            frameData.set(JAVA_LONG, 96L, toplevel.address());
            MemorySegment frame = arena.allocate(16L, 8L);
            frame.set(JAVA_LONG, 0L, frameData.address());
            MemorySegment window = arena.allocate(16384L + 1024L, 8L);
            window.set(JAVA_LONG, 128L, surface.address());
            // A misaligned value near the surface is no proxy
            window.set(JAVA_LONG, 136L, 3L);
            window.set(JAVA_LONG, 144L, frame.address());

            setIcon(window, surface, icon(1, 0, 0, 0, 0));
            Request setIcon = this.wayland.requests.stream()
                    .filter(r -> "xdg_toplevel_icon_manager_v1".equals(r.target()) && r.opcode() == 2).findFirst().orElseThrow();
            assertEquals(toplevel.address(), setIcon.arguments().get(0));
        }
    }

    @Test
    void withoutAToplevelOrTheProtocolTheIconIsLeftAlone() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment surface = arena.allocate(8L, 8L);
            MemorySegment toplevel = toplevel(arena);
            MemorySegment window = arena.allocate(16384L + 1024L, 8L);
            // No surface in the window data: nothing is sent at all
            setIcon(window, surface, icon(1, 0, 0, 0, 0));
            assertEquals(List.of(), this.wayland.requests);

            window.set(JAVA_LONG, 64L, surface.address());
            window.set(JAVA_LONG, 72L, toplevel.address());
            // A compositor without the icon protocol, then one without shared memory
            this.wayland.globals.put("wl_shm", 3);
            setIcon(window, surface, icon(1, 0, 0, 0, 0));
            this.wayland.globals.clear();
            this.wayland.globals.put("xdg_toplevel_icon_manager_v1", 9);
            setIcon(window, surface, icon(1, 0, 0, 0, 0));
            assertEquals(List.of("wl_display.1->wl_registry", "wl_display.1->wl_registry"), this.wayland.summary());

            // A connection lost after the icon went out still frees everything
            this.wayland.globals.put("wl_shm", 3);
            this.wayland.requests.clear();
            this.wayland.failingRoundtrip = 4;
            setIcon(window, surface, icon(1, 0, 0, 0, 0));
            assertTrue(this.wayland.summary().contains("xdg_toplevel_icon_manager_v1.2"));
        }
    }
}
