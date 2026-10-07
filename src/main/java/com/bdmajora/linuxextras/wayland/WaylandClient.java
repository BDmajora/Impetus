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

// libwayland-client over FFM on GLFW's own connection, with a private event queue so GLFW's dispatch never sees these objects and nothing here dispatches GLFW's events; requests go out the way wayland-scanner's generated wrappers send them
final class WaylandClient {
    static final int MARSHAL_FLAG_DESTROY = 1;
    private static final int DISPLAY_GET_REGISTRY = 1;
    private static final int REGISTRY_BIND = 0;
    static final StructLayout WL_MESSAGE = MemoryLayout.structLayout(ADDRESS.withName("name"), ADDRESS.withName("signature"), ADDRESS.withName("types"));
    static final StructLayout WL_INTERFACE = MemoryLayout.structLayout(ADDRESS.withName("name"), JAVA_INT.withName("version"),
            JAVA_INT.withName("method_count"), ADDRESS.withName("methods"), JAVA_INT.withName("event_count"), MemoryLayout.paddingLayout(4L),
            ADDRESS.withName("events"));
    // Every wl_argument is a union no wider than a pointer
    private static final long ARGUMENT_SIZE = 8L;
    private static final FunctionDescriptor GLOBAL = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT);
    private static final FunctionDescriptor GLOBAL_REMOVE = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT);

    // libwayland-client as the dynamic linker finds it; tests put a compositor written in Java here
    static final AtomicReference<Function<Arena, SymbolLookup>> LIBRARY = new AtomicReference<>(WaylandClient::systemLibrary);

    final Arena arena;
    final Linker linker = Linker.nativeLinker();
    private final SymbolLookup library;
    private final MemorySegment display;
    private final MethodHandle createQueue;
    private final MethodHandle destroyQueue;
    private final MethodHandle createWrapper;
    private final MethodHandle destroyWrapper;
    private final MethodHandle setQueue;
    private final MethodHandle marshal;
    private final MethodHandle addListener;
    private final MethodHandle getVersion;
    private final MethodHandle destroyProxy;
    private final MethodHandle roundtrip;
    private MemorySegment queue = MemorySegment.NULL;
    private MemorySegment wrapper = MemorySegment.NULL;
    private MemorySegment registry = MemorySegment.NULL;
    // The interfaces globals() looks for and the registry names announced for them; names start at 1, so 0 is one never announced
    private String[] wanted = new String[0];
    private int[] found = new int[0];

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

    static SymbolLookup systemLibrary(Arena arena) {
        return SymbolLookup.libraryLookup("libwayland-client.so.0", arena);
    }

    // Lists the compositor's globals once, on the private queue; each wanted interface's registry name comes back in order, 0 for one it does not offer
    int[] globals(String... interfaces) throws Throwable {
        this.queue = (MemorySegment) this.createQueue.invoke(this.display);
        this.wrapper = (MemorySegment) this.createWrapper.invoke(this.display);
        this.setQueue.invoke(this.wrapper, this.queue);
        this.registry = request(this.wrapper, DISPLAY_GET_REGISTRY, dataSymbol("wl_registry_interface"), version(this.wrapper), 0, MemorySegment.NULL);
        this.wanted = interfaces;
        this.found = new int[interfaces.length];
        listen(this.registry, upcall(MethodHandles.lookup(), this, "global", GLOBAL),
                this.linker.upcallStub(MethodHandles.empty(GLOBAL_REMOVE.toMethodType()), GLOBAL_REMOVE, this.arena));
        roundtrip("listing globals");
        return this.found;
    }

    // wl_registry.global, called back from inside libwayland, where a thrown exception would abort the JVM, so it only compares strings
    private void global(MemorySegment data, MemorySegment registry, int name, MemorySegment wlInterface, int version) {
        String announced = wlInterface.reinterpret(Integer.MAX_VALUE).getString(0L, StandardCharsets.US_ASCII);
        for (int i = 0; i < this.wanted.length; i++) {
            if (this.wanted[i].equals(announced)) {
                this.found[i] = name;
            }
        }
    }

    // Binds a global listed by globals() at the given version
    MemorySegment bind(int name, MemorySegment wlInterface, int version) throws Throwable {
        MemorySegment interfaceName = wlInterface.reinterpret(WL_INTERFACE.byteSize()).get(ADDRESS, WL_INTERFACE.byteOffset(groupElement("name")));
        return request(this.registry, REGISTRY_BIND, wlInterface, version, 0, name, interfaceName, version, MemorySegment.NULL);
    }

    // Sends a request; arguments are Integers or MemorySegments, a new_id goes as NULL, and the new proxy, of wlInterface, comes back
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

    // A destructor request, after which libwayland frees the proxy
    void destroy(MemorySegment proxy, int opcode) throws Throwable {
        request(proxy, opcode, MemorySegment.NULL, version(proxy), MARSHAL_FLAG_DESTROY);
    }

    // Frees a proxy whose interface has no destructor request
    void destroyProxy(MemorySegment proxy) throws Throwable {
        this.destroyProxy.invoke(proxy);
    }

    int version(MemorySegment proxy) throws Throwable {
        return (int) this.getVersion.invoke(proxy);
    }

    // Points a proxy's events at upcalls, in event opcode order
    void listen(MemorySegment proxy, MemorySegment... handlers) throws Throwable {
        MemorySegment listener = this.arena.allocate(ADDRESS, handlers.length);
        for (int i = 0; i < handlers.length; i++) {
            listener.setAtIndex(ADDRESS, i, handlers[i]);
        }
        this.addListener.invoke(proxy, listener, MemorySegment.NULL);
    }

    // Waits until the compositor has answered everything sent on the private queue, delivering its events to the listeners
    void roundtrip(String during) throws Throwable {
        if ((int) this.roundtrip.invoke(this.display, this.queue) < 0) {
            throw new IllegalStateException("The Wayland connection failed while " + during);
        }
    }

    // Frees the registry, wrapper and queue, skipping what never got made
    void close() throws Throwable {
        if (this.registry.address() != 0L) {
            this.destroyProxy.invoke(this.registry);
        }
        if (this.wrapper.address() != 0L) {
            this.destroyWrapper.invoke(this.wrapper);
        }
        if (this.queue.address() != 0L) {
            this.destroyQueue.invoke(this.queue);
        }
    }

    // One of libwayland's own interface descriptions, such as wl_shm_interface
    MemorySegment dataSymbol(String name) {
        return this.library.find(name).orElseThrow(() -> new IllegalStateException("libwayland-client has no " + name));
    }

    // A wl_message table for count requests or events
    MemorySegment messages(int count) {
        return this.arena.allocate(WL_MESSAGE, count);
    }

    // Fills one wl_message; types holds the interface of each object or new_id argument, NULL for the rest
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

    // A protocol's wl_interface as wayland-scanner would generate it; every interface here is declared at version 1
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

    // A C function pointer calling one of target's methods; lookup is the caller's own, which can see its private handlers
    MemorySegment upcall(MethodHandles.Lookup lookup, Object target, String method, FunctionDescriptor descriptor) throws ReflectiveOperationException {
        MethodHandle handle = lookup.findVirtual(target.getClass(), method, descriptor.toMethodType()).bindTo(target);
        return this.linker.upcallStub(handle, descriptor, this.arena);
    }

    MethodHandle function(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return this.linker.downcallHandle(lookup.find(name).orElseThrow(() -> new IllegalStateException("Missing native function " + name)), descriptor);
    }
}
