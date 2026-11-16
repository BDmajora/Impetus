package com.bdmajora.impetus.engine.impl.render.mesh.util;

import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.DeviceBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MappedDownloadBuffer;
import org.lwjgl.opengl.GL32;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongConsumer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Reads device buffers back a few frames later without blocking.
public class DownloadStream {
    // Allocator of the staging ring.
    private final SegmentedAllocator allocator = new SegmentedAllocator();
    // Staging buffer.
    private final MappedDownloadBuffer staging;

    // Downloads of the current frame.
    private final List<Download> pending = new ArrayList<>();
    // Frames that wait for their fence.
    private final Deque<Frame> frames = new ArrayDeque<>();
    // Whether the stream was deleted.
    private boolean deleted;

    // Creates a stream with a staging ring of a size.
    public DownloadStream(long size) {
        this.allocator.setLimit(size);
        this.staging = new MappedDownloadBuffer(size);
    }

    // Queues a copy of a buffer range and returns false when the ring is full.
    public boolean download(DeviceBuffer source, long offset, int size, LongConsumer callback) {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(callback, "callback");
        if (size <= 0 || offset < 0 || size > source.getSize() - offset) {
            throw new IllegalArgumentException("Download runs outside the source buffer");
        }
        long address = this.allocator.alloc(size);

        if (address == SegmentedAllocator.OUT_OF_SPACE) {
            return false;
        }

        LWJGL.glCopyNamedBufferSubData(source.getId(), this.staging.getId(), offset, address, size);
        this.pending.add(new Download(address, callback));
        return true;
    }

    // Fences the frame and runs the callbacks of the finished frames.
    public void endFrame() {
        if (!this.pending.isEmpty()) {
            this.frames.add(new Frame(new GlFence(LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)), List.copyOf(this.pending)));
            this.pending.clear();
        }

        while (!this.frames.isEmpty() && this.frames.peek().fence.isCompleted()) {
            Frame frame = this.frames.poll();

            RuntimeException failure = null;

            for (Download download : frame.downloads) {
                try {
                    download.callback.accept(this.staging.getClientAddress() + download.address);
                } catch (RuntimeException e) {
                    if (failure == null) failure = e; else failure.addSuppressed(e);
                } finally {
                    this.allocator.free(download.address);
                }
            }

            frame.fence.delete();

            if (failure != null) {
                throw failure;
            }
        }
    }

    // Deletes the staging ring once.
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;

        try {
            for (Frame frame : this.frames) {
                frame.fence.delete();
            }
        } finally {
            this.frames.clear();
            this.pending.clear();
            this.staging.delete();
        }
    }

    // Copy with its callback.
    private record Download(long address, LongConsumer callback) {}

    // Downloads of one frame and its fence.
    private record Frame(GlFence fence, List<Download> downloads) {}
}
