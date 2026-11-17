package com.bdmajora.impetus.engine.impl.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests handle validity and one time destruction.
class GlObjectTest {
    // Object that counts destroy calls and can fail on demand.
    private static final class Probe extends GlObject {
        // Number of destroyInternal calls.
        int destroyed;
        // Failure thrown by destroyInternal or null.
        RuntimeException failure;
        // Handle seen during destroyInternal.
        int seen;

        // Creates a probe with a handle.
        Probe(int handle) {
            if (handle != 0) {
                this.setHandle(handle);
            }
        }

        @Override
        protected void destroyInternal() {
            this.destroyed++;
            this.seen = this.handle();
            if (this.failure != null) {
                throw this.failure;
            }
        }

        // Exposes the validity check.
        boolean valid() {
            return this.isHandleValid();
        }
    }

    // Returns the stored handle while it is valid.
    @Test
    void handleIsReturned() {
        Probe probe = new Probe(7);
        assertTrue(probe.valid());
        assertEquals(7, probe.handle());
        probe.checkHandle();
    }

    // Accepts negative and maximum handles.
    @Test
    void unusualHandlesAreValid() {
        assertEquals(-1, new Probe(-1).handle());
        assertEquals(Integer.MAX_VALUE, new Probe(Integer.MAX_VALUE).handle());
        assertEquals(Integer.MIN_VALUE, new Probe(Integer.MIN_VALUE).handle());
    }

    // Rejects use of an object that never got a handle.
    @Test
    void unsetHandleIsRejected() {
        Probe probe = new Probe(0);
        assertFalse(probe.valid());
        assertThrows(IllegalStateException.class, probe::handle);
        assertThrows(IllegalStateException.class, probe::checkHandle);
    }

    // Frees once and keeps later deletes quiet.
    @Test
    void deleteRunsOnce() {
        Probe probe = new Probe(3);
        probe.delete();
        probe.delete();
        probe.destroy();
        assertEquals(1, probe.destroyed);
        assertEquals(3, probe.seen);
        assertFalse(probe.valid());
        assertThrows(IllegalStateException.class, probe::handle);
    }

    // Deleting an object without a handle frees nothing.
    @Test
    void deleteWithoutHandleIsNoop() {
        Probe probe = new Probe(0);
        probe.delete();
        assertEquals(0, probe.destroyed);
    }

    // Deprecated destroy frees the object.
    @Test
    @SuppressWarnings("deprecation")
    void destroyDeletes() {
        Probe probe = new Probe(5);
        probe.destroy();
        assertEquals(1, probe.destroyed);
        assertFalse(probe.valid());
    }

    // Checks a failed destroy.
    @Test
    void failedDestroyKeepsHandle() {
        Probe probe = new Probe(9);
        probe.failure = new IllegalStateException("boom");
        assertSame(probe.failure, assertThrows(IllegalStateException.class, probe::delete));
        assertTrue(probe.valid());
        assertEquals(9, probe.handle());
        probe.failure = null;
        probe.delete();
        assertFalse(probe.valid());
        assertEquals(2, probe.destroyed);
    }

    // Allows a new handle after deletion.
    @Test
    void handleCanBeSetAgain() {
        Probe probe = new Probe(2);
        probe.delete();
        probe.setHandle(4);
        assertEquals(4, probe.handle());
        probe.delete();
        assertEquals(2, probe.destroyed);
    }
}
