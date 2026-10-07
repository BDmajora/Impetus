package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.LinuxExtras;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;

// KDE's kde-output-order-v1, the desktop's outputs from the primary down (the order KWin also gives XWayland); GLFW's Wayland backend has no primary monitor and reports whichever output was announced first, and no other compositor offers this
public final class WaylandOutputOrder {
    private static final String INTERFACE = "kde_output_order_v1";
    private static final int DESTROY = 0;
    private static final FunctionDescriptor OUTPUT = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS);
    private static final FunctionDescriptor DONE = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS);

    private final List<String> announced = new ArrayList<>();
    private List<String> outputs = List.of();

    private WaylandOutputOrder() {
    }

    // Output names as wl_output reports them, primary first; empty when the compositor lacks the protocol or the query fails
    public static List<String> outputNames(long display) {
        try (Arena arena = Arena.ofConfined()) {
            return new WaylandOutputOrder().query(new WaylandClient(arena, display));
        } catch (Throwable t) {
            LinuxExtras.LOGGER.warn("Could not read the Wayland output order", t);
            return List.of();
        }
    }

    private List<String> query(WaylandClient client) throws Throwable {
        try {
            int global = client.globals(INTERFACE)[0];
            if (global != 0) {
                // The compositor sends the whole order, then done, as soon as the global is bound
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

    // kde_output_order_v1 as wayland-scanner would describe it: the destroy request, then the output and done events, which libwayland must know of to accept
    private static MemorySegment describe(WaylandClient client) {
        MemorySegment requests = client.messages(1);
        client.setMessage(requests, 0, "destroy", "");
        MemorySegment events = client.messages(2);
        client.setMessage(events, 0, "output", "s", MemorySegment.NULL);
        client.setMessage(events, 1, "done", "");
        return client.createInterface(INTERFACE, requests, 1, events, 2);
    }

    // kde_output_order_v1.output, the next output in priority order; called from inside libwayland, so it only copies a string
    private void output(MemorySegment data, MemorySegment order, MemorySegment name) {
        this.announced.add(name.reinterpret(Integer.MAX_VALUE).getString(0L, StandardCharsets.UTF_8));
    }

    // kde_output_order_v1.done: the list is complete
    private void done(MemorySegment data, MemorySegment order) {
        this.outputs = List.copyOf(this.announced);
        this.announced.clear();
    }
}
