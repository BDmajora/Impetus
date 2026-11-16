package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests argument checks and page counting of the mesh buffers.
class MeshBufferEdgeTest {
    // Makes the mocked GL capable of the mesh path.
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    // Rejects sizes that are not positive.
    @Test
    void sizeRefusals() {
        for (long size : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new BindlessBuffer(size));
            assertThrows(IllegalArgumentException.class, () -> new MappedUploadBuffer(size));
            assertThrows(IllegalArgumentException.class, () -> new MappedDownloadBuffer(size));
            assertThrows(IllegalArgumentException.class, () -> new SparseBindlessBuffer(size));
        }
    }

    // Rejects ranges outside a dense buffer and use after delete.
    @Test
    void denseBufferRanges() {
        BindlessBuffer buffer = new BindlessBuffer(100);
        assertThrows(IllegalArgumentException.class, () -> buffer.clearRange(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> buffer.clearRange(0, -1));
        assertThrows(IllegalArgumentException.class, () -> buffer.clearRange(0, 101));
        assertThrows(IllegalArgumentException.class, () -> buffer.clearRange(Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> buffer.clearRange(1, Long.MAX_VALUE));
        buffer.clearRange(100, 0);
        buffer.clearRange(0, 100);
        buffer.clear();
        buffer.delete();
        buffer.delete();
        assertThrows(IllegalStateException.class, buffer::clear);
        assertThrows(IllegalStateException.class, () -> buffer.clearRange(0, 1));
        assertEquals(100, buffer.getSize());
    }

    // Rejects flushes outside the upload buffer and after delete.
    @Test
    void uploadBufferFlush() {
        MappedUploadBuffer buffer = new MappedUploadBuffer(64);
        assertThrows(IllegalArgumentException.class, () -> buffer.flush(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> buffer.flush(0, 65));
        assertThrows(IllegalArgumentException.class, () -> buffer.flush(Long.MAX_VALUE, Long.MAX_VALUE));
        buffer.flush(0, 64);
        buffer.flush(64, 0);
        buffer.delete();
        buffer.delete();
        assertThrows(IllegalStateException.class, () -> buffer.flush(0, 1));
    }

    // Aligns sizes up to whole pages and rejects bad alignment and overflow.
    @Test
    void alignment() {
        assertEquals(0, SparseBindlessBuffer.alignUp(0, 1024));
        assertEquals(1024, SparseBindlessBuffer.alignUp(1, 1024));
        assertEquals(1024, SparseBindlessBuffer.alignUp(1024, 1024));
        assertEquals(2048, SparseBindlessBuffer.alignUp(1025, 1024));
        assertEquals(-1024, SparseBindlessBuffer.alignUp(-1025, 1024));
        assertThrows(IllegalArgumentException.class, () -> SparseBindlessBuffer.alignUp(1, 0));
        assertThrows(IllegalArgumentException.class, () -> SparseBindlessBuffer.alignUp(1, -4));
        assertThrows(ArithmeticException.class, () -> SparseBindlessBuffer.alignUp(Long.MAX_VALUE, 1024));
    }

    // Counts references per page and releases pages only at zero.
    @Test
    void pageReferences() {
        SparseBindlessBuffer buffer = new SparseBindlessBuffer(10 * SparseBindlessBuffer.PAGE_SIZE);
        assertEquals(10 * SparseBindlessBuffer.PAGE_SIZE, buffer.getSize());
        long page = SparseBindlessBuffer.PAGE_SIZE;
        buffer.ensureCommitted(0, 100);
        buffer.ensureCommitted(50, 100);
        assertEquals(1, buffer.getCommittedPages());
        buffer.ensureCommitted(page - 10, 20);
        assertEquals(2, buffer.getCommittedPages());
        assertEquals(2 * page, buffer.getCommittedBytes());
        buffer.release(0, 100);
        assertEquals(2, buffer.getCommittedPages());
        buffer.release(50, 100);
        assertEquals(2, buffer.getCommittedPages());
        buffer.release(page - 10, 20);
        assertEquals(0, buffer.getCommittedPages());
        buffer.release(0, 100);
        assertEquals(0, buffer.getCommittedPages());
        buffer.ensureCommitted(123, 0);
        assertEquals(0, buffer.getCommittedPages());
        buffer.ensureCommitted(0, 10 * page);
        assertEquals(10, buffer.getCommittedPages());
        buffer.delete();
        buffer.delete();
        assertEquals(0, buffer.getCommittedPages());
    }

    // Rejects ranges outside the sparse buffer and use after delete.
    @Test
    void sparseRangeRefusals() {
        SparseBindlessBuffer buffer = new SparseBindlessBuffer(SparseBindlessBuffer.PAGE_SIZE);
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCommitted(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCommitted(0, -1));
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCommitted(0, SparseBindlessBuffer.PAGE_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> buffer.release(Long.MAX_VALUE, Long.MAX_VALUE));
        buffer.delete();
        assertThrows(IllegalStateException.class, () -> buffer.ensureCommitted(0, 1));
        assertThrows(IllegalStateException.class, () -> buffer.release(0, 1));
        Mockito.reset();
    }
}
