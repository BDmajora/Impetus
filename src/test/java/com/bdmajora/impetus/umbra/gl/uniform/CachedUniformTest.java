package com.bdmajora.impetus.umbra.gl.uniform;

import com.bdmajora.impetus.umbra.gl.GlResource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Tests the change detecting uniform base and the GL resource base.
class CachedUniformTest {
    // Uniform over a one value list that can fail its next upload.
    private static final class Probe extends CachedUniform<List<Integer>> {
        // Values uploaded so far.
        final List<Integer> uploads = new ArrayList<>();
        // Flag that makes the next upload throw.
        boolean failNext;

        // Creates a probe over a value source.
        Probe(AtomicInteger source) {
            super(3, () -> List.of(source.get()), new ArrayList<>(List.of(-1)));
        }

        // Copies the value into the holder.
        @Override
        protected void store(List<Integer> cached, List<Integer> newValue) {
            cached.set(0, newValue.get(0));
        }

        // Records the value or throws once.
        @Override
        protected void upload(List<Integer> newValue) {
            if (this.failNext) {
                this.failNext = false;
                throw new IllegalStateException("upload");
            }
            this.uploads.add(newValue.get(0));
        }
    }

    // Uploads once per change and retries a failed upload.
    @Test
    void uploadsOnChangeAndRetriesFailures() {
        AtomicInteger source = new AtomicInteger(0);
        Probe probe = new Probe(source);
        probe.update();
        probe.update();
        assertEquals(List.of(0), probe.uploads);
        source.set(5);
        probe.failNext = true;
        assertThrows(IllegalStateException.class, probe::update);
        probe.update();
        assertEquals(List.of(0, 5), probe.uploads);
    }

    // Deletes once and keeps the resource usable when the delete throws.
    @Test
    void resourceDeletesOnce() {
        AtomicInteger deletes = new AtomicInteger();
        boolean[] fail = {true};
        GlResource resource = new GlResource() {
            {
                setHandle(7);
            }

            @Override
            protected void destroyInternal() {
                deletes.incrementAndGet();
                if (fail[0]) {
                    fail[0] = false;
                    throw new IllegalStateException("delete");
                }
            }
        };
        assertThrows(IllegalStateException.class, resource::destroy);
        assertFalse(resource.isDestroyed());
        assertEquals(7, resource.getGlId());
        resource.destroy();
        resource.destroy();
        assertEquals(2, deletes.get());
        assertTrue(resource.isDestroyed());
        assertThrows(IllegalStateException.class, resource::getGlId);
    }
}
