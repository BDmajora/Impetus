package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;

import java.util.Objects;
import org.lwjgl.system.MemoryUtil;

// Draw command batch that assembles glMultiDrawElementsIndirect commands in client memory and draws them from its own buffer after upload
public final class IndirectMultiDrawBatch extends MultiDrawBatch {
    // Byte size of one command of five 32 bit fields
    private static final int COMMAND_SIZE = 5 * Integer.BYTES;

    // Byte offset of the element count field in a command
    private static final long COUNT_OFFSET = 0L;
    // Byte offset of the instance count field in a command
    private static final long INSTANCE_COUNT_OFFSET = 4L;
    // Byte offset of the first index field in a command
    private static final long FIRST_INDEX_OFFSET = 8L;
    // Byte offset of the base vertex field in a command
    private static final long BASE_VERTEX_OFFSET = 12L;
    // Byte offset of the base instance field in a command
    private static final long BASE_INSTANCE_OFFSET = 16L;

    // Byte alignment of the command block
    private static final long ALIGNMENT = 32L;

    // Shift that turns an element pointer into the index of a 32 bit element
    private static final int INDEX_SHIFT = Integer.numberOfTrailingZeros(Integer.BYTES);
    // Largest element pointer whose index fits the 32 bit first index field
    private static final long MAX_ELEMENT_POINTER = 0xFFFF_FFFFL << INDEX_SHIFT;
    // Mask of the bits that a valid element pointer leaves clear
    private static final long INVALID_POINTER_BITS = ~MAX_ELEMENT_POINTER;

    // Message of every refusal on a deleted batch
    private static final String DELETED = "Batch is deleted";
    // Message of every refusal of a write or an upload after the upload
    private static final String UPLOADED = "Batch is already uploaded";

    // Address of the command block in client memory
    private final long pCommands;
    // Buffer object that holds the commands after the upload
    private final GlMutableBuffer bufferObject;

    // Number of slots open to writes and zero once the batch is uploaded or deleted
    private int slotLimit;
    // Flag that the upload sets and the deletion clears
    private boolean uploaded;

    // Rejects a capacity below 0 or equal to Integer.MAX_VALUE and allocates the command block and generates the buffer
    public IndirectMultiDrawBatch(int capacity) {
        // Rejects a negative capacity or one whose spare slot passes the int limit
        if (capacity + 1 <= 0) {
            throw new IllegalArgumentException("Capacity must be between 0 and " + (Integer.MAX_VALUE - 1) + ": " + capacity);
        }

        super(capacity);

        int slots = this.capacity();

        long block = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) slots * COMMAND_SIZE);
        if (block == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate indirect command buffer");
        }

        GlMutableBuffer buffer;

        // Frees the block when the buffer cannot be generated
        try {
            buffer = new GlMutableBuffer();
        } catch (Throwable failure) {
            MemoryUtil.nmemAlignedFree(block);
            throw failure;
        }

        this.pCommands = block;
        this.bufferObject = buffer;
        this.slotLimit = slots;
    }

    // Stores every field of the command in the slot at the current size and counts it only when it has elements
    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        int index = this.size;
        long pointerBits = elementPointer & INVALID_POINTER_BITS;

        // Rejects a negative count or a write past the last open slot or a pointer with any invalid bit set
        if ((elementCount | (this.slotLimit - 1 - index) | pointerBits | -pointerBits) < 0L) {
            throw this.appendError(elementCount, elementPointer);
        }

        long ptr = this.pCommands + (long) index * COMMAND_SIZE;
        MemoryUtil.memPutInt(ptr + COUNT_OFFSET, elementCount);
        MemoryUtil.memPutInt(ptr + INSTANCE_COUNT_OFFSET, 1);
        MemoryUtil.memPutInt(ptr + FIRST_INDEX_OFFSET, (int) (elementPointer >>> INDEX_SHIFT));
        MemoryUtil.memPutInt(ptr + BASE_VERTEX_OFFSET, baseVertex);
        MemoryUtil.memPutInt(ptr + BASE_INSTANCE_OFFSET, 0);

        // Advances the size by the sign bit of the negated count which is set for every positive count
        this.size = index + ((-elementCount) >>> 31);
        this.maxElementCount = Math.max(this.maxElementCount, elementCount);
    }

    // Adds the count to the last command and raises the largest count to match
    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        int last = this.size - 1;

        // Rejects a negative count or a batch that is empty or uploaded or deleted
        if ((additionalElementCount | last | (this.slotLimit - 1)) < 0) {
            throw this.refusal(additionalElementCount, "Batch is empty");
        }

        long countPointer = this.pCommands + (long) last * COMMAND_SIZE + COUNT_OFFSET;
        int mergedCount = Math.addExact(MemoryUtil.memGetInt(countPointer), additionalElementCount);

        MemoryUtil.memPutInt(countPointer, mergedCount);
        this.maxElementCount = Math.max(this.maxElementCount, mergedCount);
    }

    // Returns the exception for a negative count or an invalid pointer and otherwise the state exception of a full batch
    private RuntimeException appendError(int elementCount, long elementPointer) {
        if (elementCount >= 0 && (elementPointer & INVALID_POINTER_BITS) != 0L) {
            return new IllegalArgumentException("Element pointer must be a multiple of " + Integer.BYTES + " between 0 and "
                    + MAX_ELEMENT_POINTER + ": " + elementPointer);
        }
        return this.refusal(elementCount, "Batch is full");
    }

    // Returns the exception for a negative count or a closed batch and otherwise the state exception with the message
    private RuntimeException refusal(int elementCount, String stateMessage) {
        if (elementCount < 0) {
            return new IllegalArgumentException("Element count must not be negative: " + elementCount);
        }
        return new IllegalStateException(this.slotLimit != 0 ? stateMessage : this.closedMessage());
    }

    // Returns the refusal message of a batch whose slots are closed
    private String closedMessage() {
        return this.uploaded ? UPLOADED : DELETED;
    }

    // Rejects a null command list or a closed batch and copies the commands into the buffer and frees the client copy
    @Override
    public void upload(CommandList commandList) {
        Objects.requireNonNull(commandList, "Command list must not be null");

        if (this.slotLimit == 0) {
            throw new IllegalStateException(this.closedMessage());
        }

        // Unbinds the array buffer after the upload and keeps the upload failure when the unbind also fails
        try {
            commandList.uploadData(this.bufferObject, this.pCommands, (long) this.size * COMMAND_SIZE, GlBufferUsage.STATIC_DRAW);
        } catch (Throwable failure) {
            unbindAfter(commandList, GlBufferTarget.ARRAY_BUFFER, failure);
            throw failure;
        }

        commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);

        this.slotLimit = 0;
        this.uploaded = true;
        MemoryUtil.nmemAlignedFree(this.pCommands);
    }

    // Closes the batch and frees the client copy when it is still held and deletes the buffer
    @Override
    public void delete() {
        this.uploaded = false;

        if (this.slotLimit != 0) {
            this.slotLimit = 0;
            MemoryUtil.nmemAlignedFree(this.pCommands);
        }

        this.bufferObject.delete();
    }

    // Rejects null arguments or a batch that is not uploaded and draws every command from the buffer through the tessellation
    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        Objects.requireNonNull(commandList, "Command list must not be null");
        Objects.requireNonNull(tessellation, "Tessellation must not be null");
        Objects.requireNonNull(primitiveType, "Primitive type must not be null");

        if (!this.uploaded) {
            throw new IllegalStateException(this.slotLimit != 0 ? "Batch is not uploaded" : DELETED);
        }

        // Unbinds the indirect buffer after the draw and keeps the bind or draw failure when the unbind also fails
        try {
            commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.bufferObject);

            try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
                drawCommandList.multiDrawElementsIndirect(0L, this.size, primitiveType, GlIndexType.UNSIGNED_INT);
            }
        } catch (Throwable failure) {
            unbindAfter(commandList, GlBufferTarget.DRAW_INDIRECT_BUFFER, failure);
            throw failure;
        }

        commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
    }

    // Binds zero to the target and adds a failure of the bind to the suppressed list of the original failure
    private static void unbindAfter(CommandList commandList, GlBufferTarget target, Throwable failure) {
        try {
            commandList.bindBuffer(target, null);
        } catch (Throwable unbindFailure) {
            if (unbindFailure != failure) {
                failure.addSuppressed(unbindFailure);
            }
        }
    }
}
