package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.LinuxExtras;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;

// Reads the output order a KDE compositor reports with the primary output first.
public final class WaylandOutputOrder {
    // Name of the output order interface.
    private static final String INTERFACE = "kde_output_order_v1";
    // Opcode of the destroy request.
    private static final int DESTROY = 0;
    // Signature of the output event.
    private static final FunctionDescriptor OUTPUT = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS);
    // Signature of the done event.
    private static final FunctionDescriptor DONE = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS);

    // Outputs announced since the last done event.
    private final List<String> announced = new ArrayList<>();
    // Outputs of the last done event.
    private List<String> outputs = List.of();

    // Prevents instantiation outside the queries
    private WaylandOutputOrder() {
    }

    // Connects to the display of the environment and returns its output order or an empty list.
    public static List<String> outputNames() {
        try (Arena arena = Arena.ofConfined()) {
            var library = WaylandClient.LIBRARY.get().apply(arena);
            var linker = Linker.nativeLinker();
            var connect = linker.downcallHandle(library.find("wl_display_connect").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
            var disconnect = linker.downcallHandle(library.find("wl_display_disconnect").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
            String name = System.getenv("WAYLAND_DISPLAY");
            MemorySegment display = (MemorySegment) connect.invoke(arena.allocateFrom(name == null || name.isBlank() ? "wayland-0" : name));
            if (display.address() == 0L) {
                return List.of();
            }
            try {
                return new WaylandOutputOrder().query(new WaylandClient(arena, display.address()));
            } finally {
                disconnect.invoke(display);
            }
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            LinuxExtras.LOGGER.warn("Could not read the Wayland output order", t);
            return List.of();
        }
    }

    // Returns the output order of a connected display or an empty list.
    public static List<String> outputNames(long display) {
        try (Arena arena = Arena.ofConfined()) {
            return new WaylandOutputOrder().query(new WaylandClient(arena, display));
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            LinuxExtras.LOGGER.warn("Could not read the Wayland output order", t);
            return List.of();
        }
    }

    // Binds the output order global and waits for its events and releases every proxy.
    private List<String> query(WaylandClient client) throws Throwable {
        try {
            int global = client.globals(INTERFACE)[0];
            if (global != 0) {
                MemorySegment order = client.bind(global, describe(client), 1);
                try {
                    client.listen(order, client.upcall(MethodHandles.lookup(), this, "output", OUTPUT),
                            client.upcall(MethodHandles.lookup(), this, "done", DONE));
                    client.roundtrip("reading the output order");
                } finally {
                    client.destroy(order, DESTROY);
                }
            }
        } finally {
            client.close();
        }
        return this.outputs;
    }

    // Returns the native description of the output order interface.
    private static MemorySegment describe(WaylandClient client) {
        MemorySegment requests = client.messages(1);
        client.setMessage(requests, 0, "destroy", "");
        MemorySegment events = client.messages(2);
        client.setMessage(events, 0, "output", "s", MemorySegment.NULL);
        client.setMessage(events, 1, "done", "");
        return client.createInterface(INTERFACE, requests, 1, events, 2);
    }

    // Records an announced output name.
    private void output(MemorySegment data, MemorySegment order, MemorySegment name) {
        this.announced.add(name.reinterpret(Integer.MAX_VALUE).getString(0L, StandardCharsets.UTF_8));
    }

    // Publishes the announced outputs.
    private void done(MemorySegment data, MemorySegment order) {
        this.outputs = List.copyOf(this.announced);
        this.announced.clear();
    }
}
