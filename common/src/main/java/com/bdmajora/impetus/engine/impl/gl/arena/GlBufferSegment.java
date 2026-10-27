package com.bdmajora.impetus.engine.impl.gl.arena;

import java.util.Objects;

// Range of whole elements in the buffer of an arena that links to the neighbouring ranges by offset
public class GlBufferSegment {
    // Message of a range that ends beyond the int range
    private static final String END_BEYOND_RANGE = "Segment end must not exceed " + Integer.MAX_VALUE;

    // Message of a stored length below one
    private static final String LENGTH_BELOW_ONE = "Length must be positive";

    // Arena that receives the segment on delete or null for a search key
    private final GlBufferArena arena;

    // Flag set while the segment holds free space
    private boolean free;

    // Index of the first element in the arena buffer
    private int offset;

    // Number of elements
    private int length;

    // Following segment by offset or null at the end of the list
    private GlBufferSegment next;

    // Preceding segment by offset or null at the start of the list
    private GlBufferSegment prev;

    // Creates a live unlinked segment and rejects a negative offset or length and an end beyond the int range
    public GlBufferSegment(GlBufferArena arena, int offset, int length) {
        // Reads negative when either bound is negative or the end wraps past the int range
        if ((offset | length | (offset + length)) < 0) {
            throw rangeError(offset, length);
        }

        this.arena = arena;
        this.offset = offset;
        this.length = length;
    }

    // Creates the exception that names the first broken bound of a range
    private static IllegalArgumentException rangeError(int offset, int length) {
        if (offset < 0) {
            return new IllegalArgumentException("Offset must not be negative");
        }

        if (length < 0) {
            return new IllegalArgumentException("Length must not be negative");
        }

        return new IllegalArgumentException(END_BEYOND_RANGE);
    }

    // Returns the segment to its arena and rejects a segment that belongs to no arena
    public void delete() {
        GlBufferArena arena = this.arena;

        if (arena == null) {
            throw new IllegalStateException("Segment belongs to no arena");
        }

        arena.free(this);
    }

    // Returns the index that follows the last element
    protected int getEnd() {
        return this.offset + this.length;
    }

    // Returns the number of elements
    public int getLength() {
        return this.length;
    }

    // Stores the number of elements and rejects a length below one or an end beyond the int range
    protected void setLength(int length) {
        // Reads negative when the length is below one or the end wraps past the int range
        if (((length - 1) | (this.offset + length)) < 0) {
            throw new IllegalArgumentException(length <= 0 ? LENGTH_BELOW_ONE : END_BEYOND_RANGE);
        }

        this.length = length;
    }

    // Returns the index of the first element in the arena buffer
    public int getOffset() {
        return this.offset;
    }

    // Stores the index of the first element and rejects a negative offset or an end beyond the int range
    protected void setOffset(int offset) {
        // Reads negative when the offset is negative or the end wraps past the int range
        if ((offset | (offset + this.length)) < 0) {
            throw rangeError(offset, this.length);
        }

        this.offset = offset;
    }

    // Stores whether the segment holds free space
    protected void setFree(boolean free) {
        this.free = free;
    }

    // Returns whether the segment holds free space
    protected boolean isFree() {
        return this.free;
    }

    // Stores the following segment
    protected void setNext(GlBufferSegment next) {
        this.next = next;
    }

    // Returns the following segment or null at the end of the list
    protected GlBufferSegment getNext() {
        return this.next;
    }

    // Returns the preceding segment or null at the start of the list
    protected GlBufferSegment getPrev() {
        return this.prev;
    }

    // Stores the preceding segment
    protected void setPrev(GlBufferSegment prev) {
        this.prev = prev;
    }

    // Absorbs the following segment and rejects null or any other segment or an end beyond the int range
    protected void mergeInto(GlBufferSegment entry) {
        Objects.requireNonNull(entry, "Segment must not be null");

        if (entry != this.next) {
            throw new IllegalArgumentException("Segment does not follow this segment");
        }

        int length = this.length + entry.length;

        // Reads negative when both lengths are zero or the combined end wraps past the int range
        if (((length - 1) | (this.offset + length)) < 0) {
            throw new IllegalArgumentException(length == 0 ? LENGTH_BELOW_ONE : END_BEYOND_RANGE);
        }

        GlBufferSegment after = entry.next;

        this.length = length;
        this.next = after;

        // Links the segment after the absorbed one back to this segment
        if (after != null) {
            after.prev = this;
        }
    }
}
