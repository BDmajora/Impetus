package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;

import java.util.Objects;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

// Draw command batch that keeps its command arrays in client memory for glMultiDrawElementsBaseVertex
public final class DirectMultiDrawBatch extends MultiDrawBatch {
    // Address of the element pointer array with one pointer per slot
    public final long pElementPointer;
    // Address of the element count array with one int per slot
    public final long pElementCount;
    // Address of the base vertex array with one int per slot
    public final long pBaseVertex;

    // Shift that turns a slot index into a byte offset in the element pointer array
    private static final int POINTER_SHIFT = Integer.numberOfTrailingZeros(Pointer.POINTER_SIZE);

    // Byte alignment of the block and of each array inside it
    private static final long ALIGNMENT = 32L;

    // Message of every refusal on a deleted batch
    private static final String DELETED = "Batch is deleted";

    // Number of slots open to writes and zero once the block is freed
    private int slotLimit;

    // Rejects a capacity below 0 or equal to Integer.MAX_VALUE and allocates the three arrays in one aligned block
    public DirectMultiDrawBatch(int capacity) {
        if (capacity < 0 || capacity == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Capacity must be between 0 and " + (Integer.MAX_VALUE - 1) + ": " + capacity);
        }

        super(capacity);

        int slots = this.capacity();
        long pointerBytes = alignUp((long) slots << POINTER_SHIFT);
        long intBytes = (long) slots * Integer.BYTES;
        long countBytes = alignUp(intBytes);

        long block = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, pointerBytes + countBytes + intBytes);
        if (block == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate draw command arrays");
        }

        this.pElementPointer = block;
        this.pElementCount = block + pointerBytes;
        this.pBaseVertex = this.pElementCount + countBytes;
        this.slotLimit = slots;
    }

    // Returns the byte count rounded up to the next multiple of the alignment
    private static long alignUp(long bytes) {
        return (bytes + (ALIGNMENT - 1)) & -ALIGNMENT;
    }

    // Closes every slot and frees the block on the first call only
    @Override
    public void delete() {
        if (this.slotLimit != 0) {
            this.slotLimit = 0;
            MemoryUtil.nmemAlignedFree(this.pElementPointer);
        }
    }

    // Stores the command in the slot at the current size and counts it only when it has elements
    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        int index = this.size;

        // Rejects a negative count or a write past the last open slot
        if ((elementCount | (this.slotLimit - 1 - index)) < 0) {
            throw this.refusal(elementCount, "Batch is full");
        }

        long intOffset = (long) index << 2;
        MemoryUtil.memPutInt(this.pBaseVertex + intOffset, baseVertex);
        MemoryUtil.memPutInt(this.pElementCount + intOffset, elementCount);
        MemoryUtil.memPutAddress(this.pElementPointer + ((long) index << POINTER_SHIFT), elementPointer);

        // Advances the size by the sign bit of the negated count which is set for every positive count
        this.size = index + ((-elementCount) >>> 31);
        this.maxElementCount = Math.max(this.maxElementCount, elementCount);
    }

    // Adds the count to the last command and raises the largest count to match
    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        int last = this.size - 1;

        // Rejects a negative count or a batch that is empty or deleted
        if ((additionalElementCount | last | (this.slotLimit - 1)) < 0) {
            throw this.refusal(additionalElementCount, "Batch is empty");
        }

        long countPointer = this.pElementCount + ((long) last << 2);
        int mergedCount = Math.addExact(MemoryUtil.memGetInt(countPointer), additionalElementCount);

        MemoryUtil.memPutInt(countPointer, mergedCount);
        this.maxElementCount = Math.max(this.maxElementCount, mergedCount);
    }

    // Returns the exception for a negative count or a deleted batch and otherwise the state exception with the message
    private RuntimeException refusal(int elementCount, String stateMessage) {
        if (elementCount < 0) {
            return new IllegalArgumentException("Element count must not be negative: " + elementCount);
        }
        return new IllegalStateException(this.slotLimit == 0 ? DELETED : stateMessage);
    }

    // Rejects a null command list or a deleted batch and leaves the arrays in client memory
    @Override
    public void upload(CommandList commandList) {
        Objects.requireNonNull(commandList, "Command list must not be null");
        this.requireLive();
    }

    // Rejects null arguments or a deleted batch and issues one multidraw against the tessellation
    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        Objects.requireNonNull(commandList, "Command list must not be null");
        Objects.requireNonNull(tessellation, "Tessellation must not be null");
        Objects.requireNonNull(primitiveType, "Primitive type must not be null");
        this.requireLive();

        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(this, primitiveType, GlIndexType.UNSIGNED_INT);
        }
    }

    // Rejects a batch whose block is freed
    private void requireLive() {
        if (this.slotLimit == 0) {
            throw new IllegalStateException(DELETED);
        }
    }
}
