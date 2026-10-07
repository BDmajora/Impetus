package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;
import com.bdmajora.impetus.lwjgl.LWJGLServiceProvider;
import org.lwjgl.system.MemoryUtil;

/**
 * A {@link MultiDrawBatch} that builds commands directly into the layout expected by
 * {@code glMultiDrawElementsIndirect}, then uploads them to its own GPU buffer once assembly is done.
 */
public final class IndirectMultiDrawBatch extends MultiDrawBatch {
    // uint  count;
    // uint  instanceCount;
    // uint  firstIndex;
    // int  baseVertex;
    // uint  baseInstance;
    private static final int COMMAND_SIZE = 4 * 5;

    private long pCommands;
    private final GlMutableBuffer bufferObject;

    public IndirectMultiDrawBatch(int capacity) {
        super(capacity);

        int allocCount = this.capacity();

        this.pCommands = MemoryUtil.nmemAlignedAlloc(32, (long) allocCount * COMMAND_SIZE);
        if (this.pCommands == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate indirect command buffer");
        }

        // Prefill the constants that never change between commands.
        long ptr = this.pCommands;
        for (int i = 0; i < allocCount; i++) {
            MemoryUtil.memPutInt(ptr + 4L, 1); // instanceCount
            MemoryUtil.memPutInt(ptr + 16L, 0); // baseInstance
            ptr += COMMAND_SIZE;
        }

        this.bufferObject = new GlMutableBuffer();
    }

    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        int index = this.size;

        long ptr = this.pCommands + ((long) index * COMMAND_SIZE);

        MemoryUtil.memPutInt(ptr + 0L, elementCount); // count
        MemoryUtil.memPutInt(ptr + 8L, (int) (elementPointer / 4L)); // firstIndex
        MemoryUtil.memPutInt(ptr + 12L, baseVertex); // baseVertex

        // Element counts are never negative, so the sign bit of the negation is set iff the command is non-empty.
        this.size = index + ((-elementCount) >>> 31);

        // Safe to apply unconditionally: an empty command cannot raise the maximum.
        this.maxElementCount = Math.max(this.maxElementCount, elementCount);
    }

    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        long countPointer = this.pCommands + ((long) (this.size - 1) * COMMAND_SIZE);
        int mergedCount = MemoryUtil.memGetInt(countPointer) + additionalElementCount;

        MemoryUtil.memPutInt(countPointer, mergedCount);
        this.maxElementCount = Math.max(this.maxElementCount, mergedCount);
    }

    @Override
    public void upload(CommandList commandList) {
        commandList.uploadData(this.bufferObject, this.pCommands, (long) this.size * COMMAND_SIZE, GlBufferUsage.STATIC_DRAW);

        // Nothing after this point needs the CPU-side copy.
        MemoryUtil.nmemAlignedFree(this.pCommands);
        this.pCommands = MemoryUtil.NULL;
    }

    @Override
    public void delete() {
        if (this.pCommands != MemoryUtil.NULL) {
            MemoryUtil.nmemAlignedFree(this.pCommands);
            this.pCommands = MemoryUtil.NULL;
        }
        this.bufferObject.delete();
    }

    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.bufferObject);
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsIndirect(0L, this.size(), primitiveType, GlIndexType.UNSIGNED_INT);
        }
        commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
    }
}
