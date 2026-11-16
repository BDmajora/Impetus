package com.bdmajora.linuxextras.wayland;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

// Minimal libwayland client bound through the foreign function interface.
final class WaylandClient {
    // Marshal flag that destroys the proxy.
    static final int MARSHAL_FLAG_DESTROY = 1;
    // Opcode of the registry request of the display.
    private static final int DISPLAY_GET_REGISTRY = 1;
    // Opcode of the bind request of the registry.
    private static final int REGISTRY_BIND = 0;
    // Layout of a native message description.
    static final StructLayout WL_MESSAGE = MemoryLayout.structLayout(ADDRESS.withName("name"), ADDRESS.withName("signature"), ADDRESS.withName("types"));
    // Layout of a native interface description.
    static final StructLayout WL_INTERFACE = MemoryLayout.structLayout(ADDRESS.withName("name"), JAVA_INT.withName("version"),
            JAVA_INT.withName("method_count"), ADDRESS.withName("methods"), JAVA_INT.withName("event_count"), MemoryLayout.paddingLayout(4L),
            ADDRESS.withName("events"));
    // Bytes of one marshalled argument.
    private static final long ARGUMENT_SIZE = 8L;
    // Signature of the global event.
    private static final FunctionDescriptor GLOBAL = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT);
    // Signature of the global remove event.
    private static final FunctionDescriptor GLOBAL_REMOVE = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT);

    // Loader of the client library.
    static final AtomicReference<Function<Arena, SymbolLookup>> LIBRARY = new AtomicReference<>(WaylandClient::systemLibrary);

    // Arena of every native allocation.
    final Arena arena;
    // Native linker.
    final Linker linker = Linker.nativeLinker();
    // Symbols of the client library.
    private final SymbolLookup library;
    // Display connection.
    private final MemorySegment display;
    // Handle of wl_display_create_queue.
    private final MethodHandle createQueue;
    // Handle of wl_event_queue_destroy.
    private final MethodHandle destroyQueue;
    // Handle of wl_proxy_create_wrapper.
    private final MethodHandle createWrapper;
    // Handle of wl_proxy_wrapper_destroy.
    private final MethodHandle destroyWrapper;
    // Handle of wl_proxy_set_queue.
    private final MethodHandle setQueue;
    // Handle of wl_proxy_marshal_array_flags.
    private final MethodHandle marshal;
    // Handle of wl_proxy_add_listener.
    private final MethodHandle addListener;
    // Handle of wl_proxy_get_version.
    private final MethodHandle getVersion;
    // Handle of wl_proxy_destroy.
    private final MethodHandle destroyProxy;
    // Handle of wl_display_roundtrip_queue.
    private final MethodHandle roundtrip;
    // Private event queue or null.
    private MemorySegment queue = MemorySegment.NULL;
    // Display wrapper on the private queue or null.
    private MemorySegment wrapper = MemorySegment.NULL;
    // Registry proxy or null.
    private MemorySegment registry = MemorySegment.NULL;
    // Interface names to look up.
    private String[] wanted = new String[0];
    // Global names of the wanted interfaces or zero.
    private int[] found = new int[0];

    // Creates a client for a display and binds every native function.
    WaylandClient(Arena arena, long display) {
        this.arena = arena;
        this.library = LIBRARY.get().apply(arena);
        this.display = MemorySegment.ofAddress(display);
        this.createQueue = function(this.library, "wl_display_create_queue", FunctionDescriptor.of(ADDRESS, ADDRESS));
        this.destroyQueue = function(this.library, "wl_event_queue_destroy", FunctionDescriptor.ofVoid(ADDRESS));
        this.createWrapper = function(this.library, "wl_proxy_create_wrapper", FunctionDescriptor.of(ADDRESS, ADDRESS));
        this.destroyWrapper = function(this.library, "wl_proxy_wrapper_destroy", FunctionDescriptor.ofVoid(ADDRESS));
        this.setQueue = function(this.library, "wl_proxy_set_queue", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        this.marshal = function(this.library, "wl_proxy_marshal_array_flags",
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
        this.addListener = function(this.library, "wl_proxy_add_listener", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.getVersion = function(this.library, "wl_proxy_get_version", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        this.destroyProxy = function(this.library, "wl_proxy_destroy", FunctionDescriptor.ofVoid(ADDRESS));
        this.roundtrip = function(this.library, "wl_display_roundtrip_queue", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    }

    // Returns the system client library.
    static SymbolLookup systemLibrary(Arena arena) {
        return SymbolLookup.libraryLookup("libwayland-client.so.0", arena);
    }

    // Returns the global names of the interfaces and releases an earlier lookup first.
    int[] globals(String... interfaces) throws Throwable {
        this.close();
        this.queue = notNull((MemorySegment) this.createQueue.invoke(this.display), "an event queue");
        this.wrapper = notNull((MemorySegment) this.createWrapper.invoke(this.display), "a display wrapper");
        this.setQueue.invoke(this.wrapper, this.queue);
        this.registry = request(this.wrapper, DISPLAY_GET_REGISTRY, dataSymbol("wl_registry_interface"), version(this.wrapper), 0, MemorySegment.NULL);
        this.wanted = interfaces;
        this.found = new int[interfaces.length];
        listen(this.registry, upcall(MethodHandles.lookup(), this, "global", GLOBAL),
                this.linker.upcallStub(MethodHandles.empty(GLOBAL_REMOVE.toMethodType()), GLOBAL_REMOVE, this.arena));
        roundtrip("listing globals");
        return this.found;
    }

    // Records the name of an announced global that is wanted.
    private void global(MemorySegment data, MemorySegment registry, int name, MemorySegment wlInterface, int version) {
        String announced = wlInterface.reinterpret(Integer.MAX_VALUE).getString(0L, StandardCharsets.US_ASCII);
        for (int i = 0; i < this.wanted.length; i++) {
            if (this.wanted[i].equals(announced)) {
                this.found[i] = name;
            }
        }
    }

    // Binds a global to an interface at a version.
    MemorySegment bind(int name, MemorySegment wlInterface, int version) throws Throwable {
        MemorySegment interfaceName = wlInterface.reinterpret(WL_INTERFACE.byteSize()).get(ADDRESS, WL_INTERFACE.byteOffset(groupElement("name")));
        return request(this.registry, REGISTRY_BIND, wlInterface, version, 0, name, interfaceName, version, MemorySegment.NULL);
    }

    // Sends a request and returns the new proxy and rejects a missing proxy.
    MemorySegment request(MemorySegment proxy, int opcode, MemorySegment wlInterface, int version, int flags, Object... arguments) throws Throwable {
        MemorySegment array = this.arena.allocate(ARGUMENT_SIZE * Math.max(1, arguments.length));
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Integer value) {
                array.set(JAVA_INT, ARGUMENT_SIZE * i, value);
            } else {
                array.set(ADDRESS, ARGUMENT_SIZE * i, (MemorySegment) arguments[i]);
            }
        }

        MemorySegment result = (MemorySegment) this.marshal.invoke(proxy, opcode, wlInterface, version, flags, array);
        if (wlInterface.address() != 0L && result.address() == 0L) {
            throw new IllegalStateException("libwayland could not create a proxy for request " + opcode);
        }
        return result;
    }

    // Sends a destroying request.
    void destroy(MemorySegment proxy, int opcode) throws Throwable {
        request(proxy, opcode, MemorySegment.NULL, version(proxy), MARSHAL_FLAG_DESTROY);
    }

    // Destroys a proxy.
    void destroyProxy(MemorySegment proxy) throws Throwable {
        this.destroyProxy.invoke(proxy);
    }

    // Returns the version of a proxy.
    int version(MemorySegment proxy) throws Throwable {
        return (int) this.getVersion.invoke(proxy);
    }

    // Adds a listener of event handlers to a proxy.
    void listen(MemorySegment proxy, MemorySegment... handlers) throws Throwable {
        MemorySegment listener = this.arena.allocate(ADDRESS, handlers.length);
        for (int i = 0; i < handlers.length; i++) {
            listener.setAtIndex(ADDRESS, i, handlers[i]);
        }
        this.addListener.invoke(proxy, listener, MemorySegment.NULL);
    }

    // Waits for the server to answer every request and rejects a failed connection.
    void roundtrip(String during) throws Throwable {
        if ((int) this.roundtrip.invoke(this.display, this.queue) < 0) {
            throw new IllegalStateException("The Wayland connection failed while " + during);
        }
    }

    // Destroys the registry and the wrapper and the queue once each and continues after a failure.
    void close() throws Throwable {
        MemorySegment registry = this.registry;
        MemorySegment wrapper = this.wrapper;
        MemorySegment queue = this.queue;
        this.registry = MemorySegment.NULL;
        this.wrapper = MemorySegment.NULL;
        this.queue = MemorySegment.NULL;
        try {
            if (registry.address() != 0L) {
                this.destroyProxy.invoke(registry);
            }
        } finally {
            try {
                if (wrapper.address() != 0L) {
                    this.destroyWrapper.invoke(wrapper);
                }
            } finally {
                if (queue.address() != 0L) {
                    this.destroyQueue.invoke(queue);
                }
            }
        }
    }

    // Returns a native result and rejects a null pointer.
    private static MemorySegment notNull(MemorySegment result, String what) {
        if (result.address() == 0L) {
            throw new IllegalStateException("libwayland could not create " + what);
        }
        return result;
    }

    // Returns a data symbol of the library and rejects a missing one.
    MemorySegment dataSymbol(String name) {
        return this.library.find(name).orElseThrow(() -> new IllegalStateException("libwayland-client has no " + name));
    }

    // Allocates an array of message descriptions.
    MemorySegment messages(int count) {
        return this.arena.allocate(WL_MESSAGE, count);
    }

    // Writes one message description.
    void setMessage(MemorySegment messages, int index, String name, String signature, MemorySegment... types) {
        MemorySegment message = messages.asSlice(index * WL_MESSAGE.byteSize(), WL_MESSAGE);
        MemorySegment typeTable = types.length == 0 ? MemorySegment.NULL : this.arena.allocate(ADDRESS, types.length);
        for (int i = 0; i < types.length; i++) {
            typeTable.setAtIndex(ADDRESS, i, types[i]);
        }
        message.set(ADDRESS, WL_MESSAGE.byteOffset(groupElement("name")), this.arena.allocateFrom(name));
        message.set(ADDRESS, WL_MESSAGE.byteOffset(groupElement("signature")), this.arena.allocateFrom(signature));
        message.set(ADDRESS, WL_MESSAGE.byteOffset(groupElement("types")), typeTable);
    }

    // Allocates an interface description.
    MemorySegment createInterface(String name, MemorySegment requests, int requestCount, MemorySegment events, int eventCount) {
        MemorySegment wlInterface = this.arena.allocate(WL_INTERFACE);
        wlInterface.set(ADDRESS, WL_INTERFACE.byteOffset(groupElement("name")), this.arena.allocateFrom(name));
        wlInterface.set(JAVA_INT, WL_INTERFACE.byteOffset(groupElement("version")), 1);
        wlInterface.set(JAVA_INT, WL_INTERFACE.byteOffset(groupElement("method_count")), requestCount);
        wlInterface.set(ADDRESS, WL_INTERFACE.byteOffset(groupElement("methods")), requests);
        wlInterface.set(JAVA_INT, WL_INTERFACE.byteOffset(groupElement("event_count")), eventCount);
        wlInterface.set(ADDRESS, WL_INTERFACE.byteOffset(groupElement("events")), events);
        return wlInterface;
    }

    // Creates a native stub that calls a method of a target.
    MemorySegment upcall(MethodHandles.Lookup lookup, Object target, String method, FunctionDescriptor descriptor) throws ReflectiveOperationException {
        MethodHandle handle = lookup.findVirtual(target.getClass(), method, descriptor.toMethodType()).bindTo(target);
        return this.linker.upcallStub(handle, descriptor, this.arena);
    }

    // Returns a downcall handle and rejects a missing function.
    MethodHandle function(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return this.linker.downcallHandle(lookup.find(name).orElseThrow(() -> new IllegalStateException("Missing native function " + name)), descriptor);
    }
}
