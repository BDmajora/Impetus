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

// GPU-to-CPU readback a few frames late and never blocking: each download is a buffer copy into mapped storage, handed to its callback once the frame it was issued in has retired (Nvidium's DownloadTaskStream)
public class DownloadStream {
    private final SegmentedAllocator allocator = new SegmentedAllocator();
    private final MappedDownloadBuffer staging;

    private final List<Download> pending = new ArrayList<>();
    private final Deque<Frame> frames = new ArrayDeque<>();

    public DownloadStream(long size) {
        this.allocator.setLimit(size);
        this.staging = new MappedDownloadBuffer(size);
    }

    // Queues a copy of source[offset, offset + size); false when the ring is full this frame, since a skipped statistic or visibility sample is harmless
    public boolean download(DeviceBuffer source, long offset, int size, LongConsumer callback) {
        long address = this.allocator.alloc(size);

        if (address == SegmentedAllocator.OUT_OF_SPACE) {
            return false;
        }

        LWJGL.glCopyNamedBufferSubData(source.getId(), this.staging.getId(), offset, address, size);
        this.pending.add(new Download(address, callback));
        return true;
    }

    // Fences this frame's copies and runs the callbacks of every earlier frame the GPU has finished
    public void endFrame() {
        if (!this.pending.isEmpty()) {
            this.frames.add(new Frame(new GlFence(LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)), List.copyOf(this.pending)));
            this.pending.clear();
        }

        while (!this.frames.isEmpty() && this.frames.peek().fence.isCompleted()) {
            Frame frame = this.frames.poll();

            for (Download download : frame.downloads) {
                download.callback.accept(this.staging.getClientAddress() + download.address);
                this.allocator.free(download.address);
            }

            frame.fence.delete();
        }
    }

    // Drops pending results and frees the ring
    public void delete() {
        for (Frame frame : this.frames) {
            frame.fence.delete();
        }
        this.frames.clear();
        this.pending.clear();
        this.staging.delete();
    }

    // A copy issued this frame and who wants its bytes
    private record Download(long address, LongConsumer callback) {}

    // A frame's copies, released when its fence signals
    private record Frame(GlFence fence, List<Download> downloads) {}
}
