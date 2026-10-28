package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;

// Draw command queue of fixed size whose subclasses own the command storage and the multidraw call
public abstract class MultiDrawBatch {
    // Command count of a region with one command for each facing of each section and one spare slot
    public static final int MAX_COMMAND_COUNT = (ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1;

    // Number of slots including the spare slot
    private final int capacity;

    // Number of stored commands
    protected int size;

    // Largest element count of any stored command
    protected int maxElementCount;

    // Rejects a capacity below 0 or equal to Integer.MAX_VALUE and adds one spare slot to the capacity
    protected MultiDrawBatch(int capacity) {
        int slots = capacity + 1;

        // Rejects a negative capacity or one whose spare slot passes the int limit
        if (slots <= 0) {
            throw new IllegalArgumentException("Capacity must be between 0 and " + (Integer.MAX_VALUE - 1) + ": " + capacity);
        }

        this.capacity = slots;
    }

    // Returns the number of stored commands
    public final int size() {
        return this.size;
    }

    // Returns the number of slots including the spare slot
    public final int capacity() {
        return this.capacity;
    }

    // Clears the command count and the largest element count and keeps the storage
    public void clear() {
        this.size = 0;
        this.maxElementCount = 0;
    }

    // Releases the command storage
    public abstract void delete();

    // Returns true when the batch holds no command
    public final boolean isEmpty() {
        return this.size <= 0;
    }

    // Returns the largest element count of any stored command
    public final int getIndexBufferSize() {
        return this.maxElementCount;
    }

    // Stores one draw command and leaves the size unchanged when the element count is zero
    public abstract void appendDrawCommand(int baseVertex, int elementCount, long elementPointer);

    // Adds the element count to the last stored command
    public abstract void mergeIntoLastCommand(int additionalElementCount);

    // Completes the assembly of the batch before its first execution
    public abstract void upload(CommandList commandList);

    // Issues one multidraw call for every stored command through the tessellation
    public abstract void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType);
}
