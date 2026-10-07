package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;
import com.bdmajora.impetus.lwjgl.LWJGLServiceProvider;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

public final class DirectMultiDrawBatch extends MultiDrawBatch {
    public final long pElementPointer;
    public final long pElementCount;
    public final long pBaseVertex;

    private static final int POINTER_SHIFT = Integer.numberOfTrailingZeros(Pointer.POINTER_SIZE);

    public DirectMultiDrawBatch(int capacity) {
        super(capacity);

        int allocCount = this.capacity();

        this.pElementPointer = MemoryUtil.nmemAlignedAlloc(32, (long) allocCount * Pointer.POINTER_SIZE);
        if (this.pElementPointer == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate element pointer array");
        }
        MemoryUtil.memSet(this.pElementPointer, 0x0, (long) allocCount * Pointer.POINTER_SIZE);

        this.pElementCount = MemoryUtil.nmemAlignedAlloc(32, (long) allocCount * Integer.BYTES);
        if (this.pElementCount == MemoryUtil.NULL) {
            MemoryUtil.nmemAlignedFree(this.pElementPointer);
            throw new OutOfMemoryError("Failed to allocate element count array");
        }
        this.pBaseVertex = MemoryUtil.nmemAlignedAlloc(32, (long) allocCount * Integer.BYTES);
        if (this.pBaseVertex == MemoryUtil.NULL) {
            MemoryUtil.nmemAlignedFree(this.pElementPointer);
            MemoryUtil.nmemAlignedFree(this.pElementCount);
            throw new OutOfMemoryError("Failed to allocate base vertex array");
        }
    }

    @Override
    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pElementPointer);
        MemoryUtil.nmemAlignedFree(this.pElementCount);
        MemoryUtil.nmemAlignedFree(this.pBaseVertex);
    }

    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        int index = this.size;

        MemoryUtil.memPutInt(this.pBaseVertex + ((long) index << 2), baseVertex);
        MemoryUtil.memPutInt(this.pElementCount + ((long) index << 2), elementCount);
        MemoryUtil.memPutAddress(this.pElementPointer + ((long) index << POINTER_SHIFT), elementPointer);

        // Element counts are never negative, so the sign bit of the negation is set iff the command is non-empty.
        this.size = index + ((-elementCount) >>> 31);

        // Safe to apply unconditionally: an empty command cannot raise the maximum.
        this.maxElementCount = Math.max(this.maxElementCount, elementCount);
    }

    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        long countPointer = this.pElementCount + ((long) (this.size - 1) << 2);
        int mergedCount = MemoryUtil.memGetInt(countPointer) + additionalElementCount;

        MemoryUtil.memPutInt(countPointer, mergedCount);
        this.maxElementCount = Math.max(this.maxElementCount, mergedCount);
    }

    @Override
    public void upload(CommandList commandList) {
        // no-op: the GL call reads these arrays directly out of CPU memory
    }

    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(this, primitiveType, GlIndexType.UNSIGNED_INT);
        }
    }
}
