package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import com.bdmajora.impetus.engine.impl.gl.buffer.*;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Staging buffer that writes each copy into a persistently mapped ring and issues the recorded copies on flush
public class MappedStagingBuffer implements StagingBuffer {
    // Size of the ring in bytes when no capacity is given
    private static final int DEFAULT_CAPACITY = 16 * 1024 * 1024;

    // Storage flags of the ring buffer
    private static final EnumBitField<GlBufferStorageFlags> STORAGE_FLAGS =
            EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.CLIENT_STORAGE, GlBufferStorageFlags.MAP_WRITE);

    // Access flags of the persistent ring mapping
    private static final EnumBitField<GlBufferMapFlags> MAP_FLAGS =
            EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH);

    // Staging path of copies larger than the free ring space
    private final FallbackStagingBuffer fallbackStagingBuffer;

    // Buffer that holds the ring
    private final GlImmutableBuffer buffer;
    // Persistent mapping of the ring buffer
    private final GlBufferMapping mapping;
    // Native address of the first mapped byte
    private final long mappingAddress;

    // Copy records reused across submissions with the live ones at the front
    private final ArrayList<CopyCommand> copies = new ArrayList<>();
    // Regions of the ring that wait on a fence in submission order
    private final ObjectArrayFIFOQueue<FencedMemoryRegion> fencedRegions = new ObjectArrayFIFOQueue<>();

    // Size of the ring in bytes
    private final int capacity;
    // Number of live copy records
    private int copyCount;
    // Ring offset of the first byte written after the last submission
    private int start;
    // Ring offset of the next write and never equal to the capacity
    private int pos;
    // Number of ring bytes written after the last submission
    private int pendingBytes;
    // Number of ring bytes that are neither pending nor fenced
    private int remaining;
    // Flag set once delete has begun freeing the ring
    private boolean deleted;

    // Creates a ring of 16 MiB through the command list
    public MappedStagingBuffer(CommandList commandList) {
        this(commandList, DEFAULT_CAPACITY);
    }

    // Creates and persistently maps a ring of the given size in bytes and creates the fallback staging path
    public MappedStagingBuffer(CommandList commandList, int capacity) {
        // Rejects a null command list
        Objects.requireNonNull(commandList, "Command list must not be null");

        // Rejects a ring without storage
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity " + capacity + " must be positive");
        }

        GlImmutableBuffer buffer = commandList.createImmutableBuffer(capacity, STORAGE_FLAGS);
        GlBufferMapping mapping = null;

        try {
            mapping = commandList.mapBuffer(buffer, 0L, capacity, MAP_FLAGS);
            this.mappingAddress = MemoryUtil.memAddress0(mapping.getMemoryBuffer());
            this.fallbackStagingBuffer = new FallbackStagingBuffer(commandList);
        } catch (Throwable failure) {
            // Unmaps and frees the ring buffer when a later step fails
            if (mapping != null) {
                commandList.unmap(mapping);
            }
            commandList.deleteBuffer(buffer);

            throw failure;
        }

        this.buffer = buffer;
        this.mapping = mapping;
        this.capacity = capacity;
        this.remaining = capacity;
    }

    // Returns whether the device copies and maps ranges in core and the context has buffer storage
    public static boolean isSupported(RenderDevice instance) {
        // Rejects a null device
        Objects.requireNonNull(instance, "Render device must not be null");

        DeviceFunctions functions = instance.getDeviceFunctions();

        return functions.bufferCopyFunctions() != BufferCopyFunctions.PIXEL_PACK
                && functions.bufferMapRangeFunctions() == BufferMapRangeFunctions.CORE
                && (LWJGL.isOpenGLVersionSupported(4, 4) || LWJGL.isExtensionSupported(GLExtension.ARB_buffer_storage));
    }

    // Stages the remaining bytes of the data for a copy into the destination at the write offset
    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        // Rejects a null command list or data or destination
        Objects.requireNonNull(commandList, "Command list must not be null");
        Objects.requireNonNull(data, "Data must not be null");
        Objects.requireNonNull(dst, "Destination must not be null");

        // Rejects data outside native memory
        if (!data.isDirect()) {
            throw new IllegalArgumentException("Data must be a direct buffer");
        }

        int length = data.remaining();

        // Rejects a negative write offset or a write end past the long limit
        if ((writeOffset | (writeOffset + length)) < 0L) {
            throw new IllegalArgumentException("Write offset " + writeOffset + " with " + length + " bytes is out of range");
        }

        // Rejects use after delete
        if (this.deleted) {
            throw new IllegalStateException("Staging buffer is deleted");
        }

        // Skips empty data
        if (length == 0) {
            return;
        }

        // Sends data larger than the free ring space through the fallback path after the pending ring copies
        if (length > this.remaining) {
            if (this.copyCount != 0) {
                this.submit(commandList);
            }

            this.fallbackStagingBuffer.enqueueCopy(commandList, data, dst, writeOffset);

            return;
        }

        long source = MemoryUtil.memAddress(data);
        int tail = this.capacity - this.pos;

        if (length < tail) {
            this.addTransfer(source, length, dst, writeOffset);
        } else {
            // Fills the ring up to its end and writes the rest from the start of the ring
            this.addTransfer(source, tail, dst, writeOffset);

            if (length > tail) {
                this.addTransfer(source + tail, length - tail, dst, writeOffset + tail);
            }
        }

        this.pendingBytes += length;
        this.remaining -= length;
    }

    // Copies native bytes into the ring at the write position and records their copy or extends the last record
    private void addTransfer(long source, int length, GlBuffer dst, long writeOffset) {
        int readOffset = this.pos;
        int end = readOffset + length;

        MemoryUtil.memCopy(source, this.mappingAddress + readOffset, length);

        // Wraps the write position at the end of the ring
        this.pos = end == this.capacity ? 0 : end;

        if (this.copyCount != 0) {
            CopyCommand last = this.copies.get(this.copyCount - 1);

            // Extends the last record when both ranges continue it
            if (last.buffer == dst && last.readOffset + last.bytes == readOffset && last.writeOffset + last.bytes == writeOffset) {
                last.bytes += length;

                return;
            }
        }

        CopyCommand command;

        // Reuses a record left by an earlier submission or adds a new one
        if (this.copyCount < this.copies.size()) {
            command = this.copies.get(this.copyCount);
        } else {
            command = new CopyCommand();
            this.copies.add(command);
        }

        command.buffer = dst;
        command.readOffset = readOffset;
        command.writeOffset = writeOffset;
        command.bytes = length;

        this.copyCount++;
    }

    // Releases the fallback scratch storage and issues every recorded copy under one fence
    @Override
    public void flush(CommandList commandList) {
        // Rejects a null command list
        Objects.requireNonNull(commandList, "Command list must not be null");

        // Rejects use after delete
        if (this.deleted) {
            throw new IllegalStateException("Staging buffer is deleted");
        }

        this.fallbackStagingBuffer.flush(commandList);

        if (this.copyCount != 0) {
            this.submit(commandList);
        }
    }

    // Flushes the pending ring bytes and issues the live records and fences their region
    private void submit(CommandList commandList) {
        int tail = this.capacity - this.start;

        // Flushes a wrapped region as its part before the end of the ring and its part from the start
        if (this.pendingBytes > tail) {
            commandList.flushMappedRange(this.mapping, this.start, tail);
            commandList.flushMappedRange(this.mapping, 0, this.pendingBytes - tail);
        } else {
            commandList.flushMappedRange(this.mapping, this.start, this.pendingBytes);
        }

        for (int i = 0; i < this.copyCount; i++) {
            CopyCommand command = this.copies.get(i);

            commandList.copyBufferSubData(this.buffer, command.buffer, command.readOffset, command.writeOffset, command.bytes);
        }

        this.fencedRegions.enqueue(new FencedMemoryRegion(commandList.createFence(), this.pendingBytes));

        this.start = this.pos;
        this.pendingBytes = 0;
        this.copyCount = 0;
    }

    // Unmaps and frees the ring and the fallback path and every fence on the first call and ignores later calls
    @Override
    public void delete(CommandList commandList) {
        // Rejects a null command list
        Objects.requireNonNull(commandList, "Command list must not be null");

        // Skips a ring that is already freed
        if (this.deleted) {
            return;
        }

        this.deleted = true;

        commandList.unmap(this.mapping);
        commandList.deleteBuffer(this.buffer);

        this.fallbackStagingBuffer.delete(commandList);

        this.copies.clear();
        this.copyCount = 0;

        while (!this.fencedRegions.isEmpty()) {
            this.fencedRegions.dequeue().fence().delete();
        }
    }

    // Reclaims the ring space of fenced regions in submission order up to the first unsignalled fence
    @Override
    public void flip() {
        while (!this.fencedRegions.isEmpty()) {
            FencedMemoryRegion region = this.fencedRegions.first();
            GlFence fence = region.fence();

            if (!fence.isCompleted()) {
                break;
            }

            fence.delete();

            this.fencedRegions.dequeue();
            this.remaining += region.length();
        }
    }

    // Returns the label of this staging path with the free and total ring size in MiB
    @Override
    public String toString() {
        return "Mapped (" + MathUtil.toMib(this.remaining) + "/" + MathUtil.toMib(this.capacity) + " MiB)";
    }

    // Mutable record of one copy from the ring into a destination buffer
    private static final class CopyCommand {
        // Destination buffer of the copy
        private GlBuffer buffer;
        // Ring offset of the first copied byte
        private int readOffset;
        // Destination offset of the first copied byte
        private long writeOffset;
        // Number of copied bytes
        private int bytes;
    }

    // Region of the ring that waits on a fence
    private record FencedMemoryRegion(GlFence fence, int length) {
    }
}
