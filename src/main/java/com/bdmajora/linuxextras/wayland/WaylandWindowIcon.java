package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.LinuxExtras;
import org.lwjgl.glfw.GLFWNativeWayland;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

// The window icon on Wayland through xdg-toplevel-icon-v1, which GLFW does not implement; without it the compositor looks for a desktop file named after the app id, and a launcher-started game has none, so it shows a generic icon
public final class WaylandWindowIcon {
    private static final String TOPLEVEL = "xdg_toplevel";
    private static final String ICON_MANAGER = "xdg_toplevel_icon_manager_v1";
    private static final String SHM = "wl_shm";
    // How much of GLFW's window data to search for the wl_surface pointer
    private static final int WINDOW_DATA_BYTES = 16384;
    // How far past the wl_surface pointer the toplevel can sit
    private static final int NEAR_SURFACE_BYTES = 512;
    private static final long USER_SPACE_END = 0x800000000000L;
    private static final int SHM_CREATE_POOL = 0;
    private static final int SHM_POOL_CREATE_BUFFER = 0;
    private static final int SHM_POOL_DESTROY = 1;
    private static final int BUFFER_DESTROY = 0;
    private static final int SHM_FORMAT_ARGB8888 = 0;
    private static final int ICON_MANAGER_DESTROY = 0;
    private static final int ICON_MANAGER_CREATE_ICON = 1;
    private static final int ICON_MANAGER_SET_ICON = 2;
    private static final int ICON_DESTROY = 0;
    private static final int ICON_ADD_BUFFER = 2;
    private static final int MFD_CLOEXEC = 1;

    private final WaylandClient client;
    private final MethodHandle memfdCreate;
    private final MethodHandle write;
    private final MethodHandle close;

    private WaylandWindowIcon(WaylandClient client) {
        this.client = client;
        SymbolLookup libc = client.linker.defaultLookup();
        this.memfdCreate = client.function(libc, "memfd_create", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        this.write = client.function(libc, "write", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));
        this.close = client.function(libc, "close", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    }

    // Sets a shown GLFW window's icon from square RGBA images, as Display.setIcon takes them; any failure keeps the compositor's default icon
    public static void setIcon(long window, ByteBuffer[] icons) {
        try (Arena arena = Arena.ofConfined(); ProcessMemory memory = new ProcessMemory()) {
            long toplevel = findToplevel(memory, window, GLFWNativeWayland.glfwGetWaylandWindow(window));
            if (toplevel == 0L) {
                LinuxExtras.LOGGER.warn("Could not find the window's Wayland toplevel; keeping the compositor's default icon");
                return;
            }
            // A proxy starts with a pointer to its wl_interface
            MemorySegment toplevelInterface = MemorySegment.ofAddress(memory.readPointer(toplevel));
            new WaylandWindowIcon(new WaylandClient(arena, GLFWNativeWayland.glfwGetWaylandDisplay()))
                    .send(MemorySegment.ofAddress(toplevel), toplevelInterface, icons);
        } catch (Throwable t) {
            LinuxExtras.LOGGER.warn("Could not set the Wayland window icon", t);
        }
    }

    // GLFW keeps the xdg_toplevel private, a few fields after the wl_surface it does expose; with libdecor decorations the toplevel belongs to the frame stored there instead, whose first field points to private data holding both the surface and the toplevel. 0 when neither turns up
    static long findToplevel(ProcessMemory memory, long window, long surface) {
        ByteBuffer windowData = memory.read(window, WINDOW_DATA_BYTES);
        for (int surfaceOffset = 0; surfaceOffset + 8 <= windowData.limit(); surfaceOffset += 8) {
            if (windowData.getLong(surfaceOffset) != surface) {
                continue;
            }
            int end = Math.min(windowData.limit(), surfaceOffset + NEAR_SURFACE_BYTES);
            for (int offset = surfaceOffset + 8; offset + 8 <= end; offset += 8) {
                long pointer = windowData.getLong(offset);
                if (memory.isProxy(pointer, TOPLEVEL)) {
                    return pointer;
                }
            }
            for (int offset = surfaceOffset + 8; offset + 8 <= end; offset += 8) {
                ByteBuffer frameData = memory.read(memory.readPointer(windowData.getLong(offset)), NEAR_SURFACE_BYTES);
                if (!containsPointer(frameData, surface)) {
                    continue;
                }
                for (int frameOffset = 0; frameOffset + 8 <= frameData.limit(); frameOffset += 8) {
                    long pointer = frameData.getLong(frameOffset);
                    if (memory.isProxy(pointer, TOPLEVEL)) {
                        return pointer;
                    }
                }
            }
        }
        return 0L;
    }

    private static boolean containsPointer(ByteBuffer data, long pointer) {
        for (int offset = 0; offset + 8 <= data.limit(); offset += 8) {
            if (data.getLong(offset) == pointer) {
                return true;
            }
        }
        return false;
    }

    private void send(MemorySegment toplevel, MemorySegment toplevelInterface, ByteBuffer[] icons) throws Throwable {
        try {
            int[] globals = this.client.globals(ICON_MANAGER, SHM);
            if (globals[0] == 0) {
                LinuxExtras.LOGGER.info("The Wayland compositor does not support {}; keeping its default window icon", ICON_MANAGER);
            } else if (globals[1] == 0) {
                LinuxExtras.LOGGER.warn("The Wayland compositor does not offer {}; keeping its default window icon", SHM);
            } else {
                MemorySegment bufferInterface = this.client.dataSymbol("wl_buffer_interface");
                MemorySegment iconInterface = describeIcon(bufferInterface);
                MemorySegment manager = this.client.bind(globals[0], describeManager(toplevelInterface, iconInterface), 1);
                MemorySegment shm = this.client.bind(globals[1], this.client.dataSymbol("wl_shm_interface"), 1);
                try {
                    attach(manager, shm, bufferInterface, iconInterface, toplevel, icons);
                } finally {
                    this.client.destroy(manager, ICON_MANAGER_DESTROY);
                    // Version 1 of wl_shm has no destroy request
                    this.client.destroyProxy(shm);
                }
                this.client.roundtrip("setting the window icon");
            }
        } finally {
            this.client.close();
        }
    }

    // Copies the icons into one shared-memory pool, adds a buffer per icon to a new toplevel icon and assigns it; the compositor keeps the icon after the icon object is destroyed, and the buffers may go once it has
    private void attach(MemorySegment manager, MemorySegment shm, MemorySegment bufferInterface, MemorySegment iconInterface,
                        MemorySegment toplevel, ByteBuffer[] icons) throws Throwable {
        int[] sizes = new int[icons.length];
        int[] offsets = new int[icons.length];
        int poolSize = 0;
        for (int i = 0; i < icons.length; i++) {
            sizes[i] = (int) Math.sqrt(icons[i].remaining() / 4.0);
            offsets[i] = poolSize;
            poolSize += sizes[i] * sizes[i] * 4;
        }

        MemorySegment pixels = this.client.arena.allocate(poolSize);
        for (int i = 0; i < icons.length; i++) {
            copyAsPremultipliedArgb(icons[i], pixels.asSlice(offsets[i], (long) sizes[i] * sizes[i] * 4));
        }

        MemorySegment pool;
        int fd = (int) this.memfdCreate.invoke(this.client.arena.allocateFrom("minecraft-window-icon"), MFD_CLOEXEC);
        if (fd < 0) {
            throw new IOException("memfd_create failed");
        }
        try {
            for (long written = 0L; written < poolSize; ) {
                long count = (long) this.write.invoke(fd, pixels.asSlice(written), poolSize - written);
                if (count <= 0L) {
                    throw new IOException("Could not write the window icon to shared memory");
                }
                written += count;
            }
            // libwayland sends a duplicate of the descriptor, so it can be closed right after
            pool = this.client.request(shm, SHM_CREATE_POOL, this.client.dataSymbol("wl_shm_pool_interface"), this.client.version(shm), 0,
                    MemorySegment.NULL, fd, poolSize);
        } finally {
            this.close.invoke(fd);
        }

        List<MemorySegment> buffers = new ArrayList<>();
        try {
            for (int i = 0; i < icons.length; i++) {
                buffers.add(this.client.request(pool, SHM_POOL_CREATE_BUFFER, bufferInterface, this.client.version(pool), 0,
                        MemorySegment.NULL, offsets[i], sizes[i], sizes[i], sizes[i] * 4, SHM_FORMAT_ARGB8888));
            }
            MemorySegment icon = this.client.request(manager, ICON_MANAGER_CREATE_ICON, iconInterface, this.client.version(manager), 0, MemorySegment.NULL);
            for (MemorySegment buffer : buffers) {
                this.client.request(icon, ICON_ADD_BUFFER, MemorySegment.NULL, this.client.version(icon), 0, buffer, 1);
            }
            this.client.request(manager, ICON_MANAGER_SET_ICON, MemorySegment.NULL, this.client.version(manager), 0, toplevel, icon);
            this.client.destroy(icon, ICON_DESTROY);
        } finally {
            for (MemorySegment buffer : buffers) {
                this.client.destroy(buffer, BUFFER_DESTROY);
            }
            this.client.destroy(pool, SHM_POOL_DESTROY);
        }
    }

    // RGBA to wl_shm's ARGB8888, a little-endian 0xAARRGGBB with premultiplied alpha
    static void copyAsPremultipliedArgb(ByteBuffer rgba, MemorySegment argb) {
        for (int i = 0; i < rgba.remaining(); i += 4) {
            int red = rgba.get(rgba.position() + i) & 255;
            int green = rgba.get(rgba.position() + i + 1) & 255;
            int blue = rgba.get(rgba.position() + i + 2) & 255;
            int alpha = rgba.get(rgba.position() + i + 3) & 255;
            argb.set(JAVA_BYTE, i, (byte) ((blue * alpha + 127) / 255));
            argb.set(JAVA_BYTE, i + 1, (byte) ((green * alpha + 127) / 255));
            argb.set(JAVA_BYTE, i + 2, (byte) ((red * alpha + 127) / 255));
            argb.set(JAVA_BYTE, i + 3, (byte) alpha);
        }
    }

    // xdg_toplevel_icon_v1: requests destroy, set_name, add_buffer
    private MemorySegment describeIcon(MemorySegment bufferInterface) {
        MemorySegment requests = this.client.messages(3);
        this.client.setMessage(requests, 0, "destroy", "");
        this.client.setMessage(requests, 1, "set_name", "s", MemorySegment.NULL);
        this.client.setMessage(requests, 2, "add_buffer", "oi", bufferInterface, MemorySegment.NULL);
        return this.client.createInterface("xdg_toplevel_icon_v1", requests, 3, MemorySegment.NULL, 0);
    }

    // xdg_toplevel_icon_manager_v1: requests destroy, create_icon, set_icon; its icon_size and done events are declared though nothing listens, because libwayland rejects events it does not know
    private MemorySegment describeManager(MemorySegment toplevelInterface, MemorySegment iconInterface) {
        MemorySegment requests = this.client.messages(3);
        this.client.setMessage(requests, 0, "destroy", "");
        this.client.setMessage(requests, 1, "create_icon", "n", iconInterface);
        this.client.setMessage(requests, 2, "set_icon", "o?o", toplevelInterface, iconInterface);
        MemorySegment events = this.client.messages(2);
        this.client.setMessage(events, 0, "icon_size", "i", MemorySegment.NULL);
        this.client.setMessage(events, 1, "done", "");
        return this.client.createInterface(ICON_MANAGER, requests, 3, events, 2);
    }

    // This process's memory through /proc/self/mem, where an unmapped address is a read error rather than a crash
    static final class ProcessMemory implements AutoCloseable {
        private final FileChannel channel;

        ProcessMemory() throws IOException {
            this.channel = FileChannel.open(Path.of("/proc/self/mem"), StandardOpenOption.READ);
        }

        // Up to length bytes from address, fewer when the readable memory ends first
        ByteBuffer read(long address, int length) {
            ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.nativeOrder());
            if (address > 0L && address < USER_SPACE_END) {
                try {
                    while (buffer.hasRemaining() && this.channel.read(buffer, address + buffer.position()) > 0) {
                        // Keeps reading until the channel stops
                    }
                } catch (IOException ignored) {
                    // An unmapped page ends the read; what came before it stands
                }
            }
            buffer.flip();
            return buffer;
        }

        long readPointer(long address) {
            ByteBuffer buffer = read(address, 8);
            return buffer.remaining() == 8 ? buffer.getLong(0) : 0L;
        }

        // Whether pointer reads as a wl_proxy of the named interface: a proxy starts with a pointer to its wl_interface, which starts with a pointer to the interface name
        boolean isProxy(long pointer, String interfaceName) {
            if (pointer == 0L || (pointer & 7L) != 0L) {
                return false;
            }
            byte[] expected = (interfaceName + '\0').getBytes(StandardCharsets.US_ASCII);
            return read(readPointer(readPointer(pointer)), expected.length).equals(ByteBuffer.wrap(expected));
        }

        @Override
        public void close() throws IOException {
            this.channel.close();
        }
    }
}
