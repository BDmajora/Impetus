package com.bdmajora.linuxextras.wayland;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

// A compositor written in Java behind libwayland-client's entry points: each symbol is an upcall stub into this class, so the code under test makes its real FFM downcalls, and events reach its listeners the way libwayland delivers them
final class FakeWayland implements AutoCloseable {
    private static final FunctionDescriptor GLOBAL = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT);
    private static final FunctionDescriptor OUTPUT = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS);
    private static final FunctionDescriptor DONE = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS);

    // One request as the compositor read it: the target's interface, the opcode, the new proxy's interface (null for none), the flags, and the arguments (Integer, String, or Long for objects and new_ids)
    record Request(String target, int opcode, String created, int flags, List<Object> arguments) {
    }

    final Arena arena = Arena.ofShared();
    private final Linker linker = Linker.nativeLinker();
    private final Map<String, MemorySegment> symbols = new HashMap<>();
    // The wl_display GLFW would hand out
    final MemorySegment display;
    // Interface name to registry name, in announcement order
    final Map<String, Integer> globals = new LinkedHashMap<>();
    // What kde_output_order_v1 sends, primary first
    List<String> outputOrder = List.of();
    // The roundtrip, counted from 1, that reports a lost connection; 0 for none
    int failingRoundtrip;
    // Constructor requests answer NULL, as libwayland does when it cannot make the proxy
    boolean refuseProxies;
    final List<Request> requests = new ArrayList<>();
    // Queues, wrappers and proxies made and not yet freed, by address
    final Set<Long> live = new LinkedHashSet<>();
    // The bytes behind the descriptor wl_shm.create_pool received, read while it was still open
    byte[] pool;
    // Whatever went wrong inside an upcall, where a thrown exception would abort the JVM
    Throwable failure;

    private final MemorySegment displayInterface;
    private final Map<Long, String> interfaces = new HashMap<>();
    private final Map<Long, MemorySegment> listeners = new LinkedHashMap<>();
    private final Set<Long> answered = new HashSet<>();
    private int roundtrips;

    FakeWayland() throws ReflectiveOperationException {
        this.displayInterface = describe("wl_display", "n", "n");
        this.symbols.put("wl_registry_interface", describe("wl_registry", "usun"));
        this.symbols.put("wl_shm_interface", describe("wl_shm", "nhi", "2"));
        this.symbols.put("wl_shm_pool_interface", describe("wl_shm_pool", "niiiiu", "", "i"));
        this.symbols.put("wl_buffer_interface", describe("wl_buffer", ""));
        this.display = this.arena.allocate(8L, 8L);
        stub("wl_display_create_queue", "createQueue", FunctionDescriptor.of(ADDRESS, ADDRESS));
        stub("wl_event_queue_destroy", "destroyQueue", FunctionDescriptor.ofVoid(ADDRESS));
        stub("wl_proxy_create_wrapper", "createWrapper", FunctionDescriptor.of(ADDRESS, ADDRESS));
        stub("wl_proxy_wrapper_destroy", "destroyWrapper", FunctionDescriptor.ofVoid(ADDRESS));
        stub("wl_proxy_set_queue", "setQueue", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        stub("wl_proxy_marshal_array_flags", "marshal", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
        stub("wl_proxy_add_listener", "addListener", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        stub("wl_proxy_get_version", "version", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        stub("wl_proxy_destroy", "destroyProxy", FunctionDescriptor.ofVoid(ADDRESS));
        stub("wl_display_roundtrip_queue", "roundtrip", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    }

    // The library WaylandClient.LIBRARY hands out, without the named symbols
    SymbolLookup lookup(String... missing) {
        Set<String> gone = Set.of(missing);
        return name -> gone.contains(name) ? Optional.empty() : Optional.ofNullable(this.symbols.get(name));
    }

    // Each request as target.opcode, with ->created for a constructor
    List<String> summary() {
        return this.requests.stream().map(r -> r.target() + "." + r.opcode() + (r.created() == null ? "" : "->" + r.created())).toList();
    }

    @Override
    public void close() {
        this.arena.close();
    }

    private void stub(String symbol, String method, FunctionDescriptor descriptor) throws ReflectiveOperationException {
        MethodHandle handle = MethodHandles.lookup().findVirtual(FakeWayland.class, method, descriptor.toMethodType()).bindTo(this);
        this.symbols.put(symbol, this.linker.upcallStub(handle, descriptor, this.arena));
    }

    // A wl_interface whose requests carry these signatures
    private MemorySegment describe(String name, String... signatures) {
        MemorySegment methods = this.arena.allocate(WaylandClient.WL_MESSAGE, Math.max(1, signatures.length));
        for (int i = 0; i < signatures.length; i++) {
            MemorySegment message = methods.asSlice(i * WaylandClient.WL_MESSAGE.byteSize(), WaylandClient.WL_MESSAGE);
            message.set(ADDRESS, WaylandClient.WL_MESSAGE.byteOffset(groupElement("name")), this.arena.allocateFrom("request" + i));
            message.set(ADDRESS, WaylandClient.WL_MESSAGE.byteOffset(groupElement("signature")), this.arena.allocateFrom(signatures[i]));
        }
        MemorySegment wlInterface = this.arena.allocate(WaylandClient.WL_INTERFACE);
        wlInterface.set(ADDRESS, WaylandClient.WL_INTERFACE.byteOffset(groupElement("name")), this.arena.allocateFrom(name));
        wlInterface.set(JAVA_INT, WaylandClient.WL_INTERFACE.byteOffset(groupElement("method_count")), signatures.length);
        wlInterface.set(ADDRESS, WaylandClient.WL_INTERFACE.byteOffset(groupElement("methods")), methods);
        return wlInterface;
    }

    private static String nameOf(MemorySegment wlInterface) {
        return wlInterface.reinterpret(WaylandClient.WL_INTERFACE.byteSize())
                .get(ADDRESS, WaylandClient.WL_INTERFACE.byteOffset(groupElement("name"))).reinterpret(Integer.MAX_VALUE).getString(0L);
    }

    // A proxy laid out as libwayland's: a pointer to its wl_interface first
    private MemorySegment proxy(MemorySegment wlInterface) {
        MemorySegment proxy = this.arena.allocate(16L, 8L);
        proxy.set(ADDRESS, 0L, wlInterface);
        this.interfaces.put(proxy.address(), nameOf(wlInterface));
        this.live.add(proxy.address());
        return proxy;
    }

    private void release(MemorySegment object) {
        if (!this.live.remove(object.address())) {
            this.failure = new AssertionError("Freed something never made or already freed: " + object);
        }
    }

    // The signature of a request, read from the target proxy's own interface as libwayland does
    private static String signature(MemorySegment proxy, int opcode) {
        MemorySegment wlInterface = proxy.reinterpret(8L).get(ADDRESS, 0L).reinterpret(WaylandClient.WL_INTERFACE.byteSize());
        long size = WaylandClient.WL_MESSAGE.byteSize();
        MemorySegment messages = wlInterface.get(ADDRESS, WaylandClient.WL_INTERFACE.byteOffset(groupElement("methods"))).reinterpret(size * (opcode + 1));
        return messages.asSlice(size * opcode).get(ADDRESS, WaylandClient.WL_MESSAGE.byteOffset(groupElement("signature")))
                .reinterpret(Integer.MAX_VALUE).getString(0L);
    }

    private static List<Object> decode(String signature, MemorySegment arguments) {
        String types = signature.replaceAll("[0-9?]", "");
        MemorySegment array = arguments.reinterpret(8L * Math.max(1, types.length()));
        List<Object> decoded = new ArrayList<>();
        for (int i = 0; i < types.length(); i++) {
            switch (types.charAt(i)) {
                case 'i', 'u', 'h', 'f' -> decoded.add(array.get(JAVA_INT, 8L * i));
                case 's' -> decoded.add(array.get(ADDRESS, 8L * i).reinterpret(Integer.MAX_VALUE).getString(0L));
                default -> decoded.add(array.get(ADDRESS, 8L * i).address());
            }
        }
        return decoded;
    }

    private MemorySegment createQueue(MemorySegment display) {
        MemorySegment queue = this.arena.allocate(8L, 8L);
        this.live.add(queue.address());
        return queue;
    }

    private void destroyQueue(MemorySegment queue) {
        release(queue);
    }

    private MemorySegment createWrapper(MemorySegment proxy) {
        return proxy(this.displayInterface);
    }

    private void destroyWrapper(MemorySegment wrapper) {
        release(wrapper);
    }

    private void setQueue(MemorySegment proxy, MemorySegment queue) {
    }

    private MemorySegment marshal(MemorySegment proxy, int opcode, MemorySegment wlInterface, int version, int flags, MemorySegment arguments) {
        try {
            String target = this.interfaces.get(proxy.address());
            List<Object> decoded = decode(signature(proxy, opcode), arguments);
            String created = wlInterface.address() == 0L ? null : nameOf(wlInterface);
            this.requests.add(new Request(target, opcode, created, flags, decoded));
            if ("wl_shm".equals(target) && opcode == 0) {
                this.pool = Files.readAllBytes(Path.of("/proc/self/fd/" + decoded.get(1)));
            }
            if ((flags & WaylandClient.MARSHAL_FLAG_DESTROY) != 0) {
                release(proxy);
            }
            return created == null || this.refuseProxies ? MemorySegment.NULL : proxy(wlInterface);
        } catch (Throwable t) {
            this.failure = t;
            return MemorySegment.NULL;
        }
    }

    private int addListener(MemorySegment proxy, MemorySegment listener, MemorySegment data) {
        this.listeners.put(proxy.address(), listener);
        return 0;
    }

    private int version(MemorySegment proxy) {
        return 1;
    }

    private void destroyProxy(MemorySegment proxy) {
        release(proxy);
    }

    // Sends each newly listened-to proxy its events: the registry its globals, the output order its outputs and done
    private int roundtrip(MemorySegment display, MemorySegment queue) {
        try {
            if (++this.roundtrips == this.failingRoundtrip) {
                return -1;
            }
            for (Map.Entry<Long, MemorySegment> entry : List.copyOf(this.listeners.entrySet())) {
                if (!this.answered.add(entry.getKey())) {
                    continue;
                }
                MemorySegment proxy = MemorySegment.ofAddress(entry.getKey());
                MemorySegment listener = entry.getValue().reinterpret(2L * ADDRESS.byteSize());
                String wlInterface = this.interfaces.get(entry.getKey());
                if ("wl_registry".equals(wlInterface)) {
                    MethodHandle global = handler(listener, 0, GLOBAL);
                    for (Map.Entry<String, Integer> announced : this.globals.entrySet()) {
                        global.invoke(MemorySegment.NULL, proxy, (int) announced.getValue(), this.arena.allocateFrom(announced.getKey()), 1);
                    }
                } else if ("kde_output_order_v1".equals(wlInterface)) {
                    MethodHandle output = handler(listener, 0, OUTPUT);
                    for (String name : this.outputOrder) {
                        output.invoke(MemorySegment.NULL, proxy, this.arena.allocateFrom(name));
                    }
                    handler(listener, 1, DONE).invoke(MemorySegment.NULL, proxy);
                }
            }
            return 0;
        } catch (Throwable t) {
            this.failure = t;
            return -1;
        }
    }

    private MethodHandle handler(MemorySegment listener, int index, FunctionDescriptor descriptor) {
        return this.linker.downcallHandle(listener.getAtIndex(ADDRESS, index), descriptor);
    }
}
