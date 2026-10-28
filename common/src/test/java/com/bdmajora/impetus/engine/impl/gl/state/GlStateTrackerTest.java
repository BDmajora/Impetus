package com.bdmajora.impetus.engine.impl.gl.state;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

// Tests of the binding cache of the render device
class GlStateTrackerTest {
    // Every binding point in declaration order
    private static final GlBufferTarget[] TARGETS = GlBufferTarget.values();

    // Tracker under test
    private final GlStateTracker tracker = new GlStateTracker();

    // Returns a buffer whose GL name is the given name
    private static GlMutableBuffer buffer(int name) {
        Mockito.doReturn(name).when(TestGl.gl()).glGenBuffers();
        return new GlMutableBuffer();
    }

    // Returns a vertex array whose GL name is the given name
    private static GlVertexArray array(int name) {
        Mockito.doReturn(name).when(TestGl.gl()).glGenVertexArrays();
        return new GlVertexArray();
    }

    // Checks that a fresh tracker needs a bind for null on every binding point and for vertex array zero
    @Test
    void freshTrackerKnowsNoBinding() {
        for (GlBufferTarget target : TARGETS) {
            assertTrue(tracker.makeBufferActive(target, null), target.name());
            assertFalse(tracker.makeBufferActive(target, null), target.name());
        }
        assertTrue(tracker.makeVertexArrayActive(null));
        assertFalse(tracker.makeVertexArrayActive(null));
    }

    // Checks that every binding point skips the buffer it holds and needs a bind for any other
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void makeBufferActiveSkipsTheBufferItHolds(GlBufferTarget target) {
        GlMutableBuffer first = buffer(1);
        GlMutableBuffer second = buffer(2);
        assertTrue(tracker.makeBufferActive(target, first));
        assertFalse(tracker.makeBufferActive(target, first));
        assertTrue(tracker.makeBufferActive(target, second));
        assertTrue(tracker.makeBufferActive(target, first));
        assertFalse(tracker.makeBufferActive(target, buffer(1)));
    }

    // Checks that a null buffer is recorded as name zero and a repeated null is skipped
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void nullBufferIsRecordedAsNameZero(GlBufferTarget target) {
        GlMutableBuffer buffer = buffer(3);
        assertTrue(tracker.makeBufferActive(target, buffer));
        assertTrue(tracker.makeBufferActive(target, null));
        assertFalse(tracker.makeBufferActive(target, null));
        assertTrue(tracker.makeBufferActive(target, buffer));
    }

    // Checks that a null buffer after a clear needs the unbind once
    @Test
    void nullBufferAfterAClearNeedsTheUnbind() {
        tracker.makeBufferActive(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer(4));
        tracker.clear();
        assertTrue(tracker.makeBufferActive(GlBufferTarget.DRAW_INDIRECT_BUFFER, null));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.DRAW_INDIRECT_BUFFER, null));
    }

    // Checks that a null buffer after a vertex array change needs the unbind once
    @Test
    void nullBufferAfterAVertexArrayChangeNeedsTheUnbind() {
        tracker.makeBufferActive(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer(5));
        tracker.makeVertexArrayActive(array(6));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.DRAW_INDIRECT_BUFFER, null));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.DRAW_INDIRECT_BUFFER, null));
    }

    // Checks that a null buffer after the bound buffer is deleted needs the unbind once
    @Test
    void nullBufferAfterADeletionNeedsTheUnbind() {
        GlMutableBuffer buffer = buffer(7);
        tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer);
        tracker.notifyBufferDeleted(buffer);
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, null));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, null));
    }

    // Checks that a bind on one binding point keeps the bindings of every other binding point
    @Test
    void bindingPointsAreIndependent() {
        GlMutableBuffer shared = buffer(8);
        GlMutableBuffer other = buffer(9);
        for (GlBufferTarget target : TARGETS) {
            assertTrue(tracker.makeBufferActive(target, shared), target.name());
        }
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, other));
        for (GlBufferTarget target : TARGETS) {
            assertEquals(target == GlBufferTarget.ARRAY_BUFFER, tracker.makeBufferActive(target, shared), target.name());
        }
    }

    // Checks that names at the int limits and the all ones name are tracked after a clear
    @ParameterizedTest
    @ValueSource(ints = {1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void namesAtTheIntLimitsAreTracked(int name) {
        GlMutableBuffer buffer = buffer(name);
        GlVertexArray array = array(name);
        tracker.clear();
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertTrue(tracker.makeVertexArrayActive(array));
        assertFalse(tracker.makeVertexArrayActive(array));
        tracker.clear();
        assertTrue(tracker.makeVertexArrayActive(array));
        tracker.notifyVertexArrayDeleted(array);
        assertTrue(tracker.makeVertexArrayActive(array));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        tracker.notifyBufferDeleted(buffer);
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
    }

    // Checks that the vertex array binding skips the array it holds and needs a bind for any other or for zero
    @Test
    void makeVertexArrayActiveSkipsTheArrayItHolds() {
        GlVertexArray first = array(10);
        GlVertexArray second = array(11);
        assertTrue(tracker.makeVertexArrayActive(first));
        assertFalse(tracker.makeVertexArrayActive(first));
        assertTrue(tracker.makeVertexArrayActive(second));
        assertTrue(tracker.makeVertexArrayActive(null));
        assertFalse(tracker.makeVertexArrayActive(null));
        assertTrue(tracker.makeVertexArrayActive(first));
        assertFalse(tracker.makeVertexArrayActive(array(10)));
    }

    // Checks that a vertex array change forgets the buffer of every binding point
    @Test
    void vertexArrayChangeForgetsEveryBufferBinding() {
        GlMutableBuffer buffer = buffer(12);
        for (GlBufferTarget target : TARGETS) {
            tracker.makeBufferActive(target, buffer);
        }
        tracker.makeVertexArrayActive(array(13));
        for (GlBufferTarget target : TARGETS) {
            assertTrue(tracker.makeBufferActive(target, buffer), target.name());
        }
    }

    // Checks that a repeated vertex array bind keeps the buffer bindings
    @Test
    void repeatedVertexArrayKeepsTheBufferBindings() {
        GlMutableBuffer buffer = buffer(14);
        GlVertexArray array = array(15);
        tracker.makeVertexArrayActive(array);
        tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer);
        assertFalse(tracker.makeVertexArrayActive(array));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer));
    }

    // Checks that deleting the bound vertex array forgets it and the buffer of every binding point
    @Test
    void deletingTheBoundVertexArrayForgetsItAndEveryBufferBinding() {
        GlMutableBuffer buffer = buffer(16);
        GlVertexArray array = array(17);
        tracker.makeVertexArrayActive(array);
        tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer);
        tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer);
        tracker.notifyVertexArrayDeleted(array);
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertTrue(tracker.makeVertexArrayActive(array));
    }

    // Checks that deleting the bound vertex array needs a bind for vertex array zero
    @Test
    void deletingTheBoundVertexArrayNeedsTheUnbind() {
        GlVertexArray array = array(18);
        tracker.makeVertexArrayActive(array);
        tracker.notifyVertexArrayDeleted(array);
        assertTrue(tracker.makeVertexArrayActive(null));
        assertFalse(tracker.makeVertexArrayActive(null));
    }

    // Checks that deleting a vertex array that is not bound keeps every binding
    @Test
    void deletingAnotherVertexArrayKeepsEveryBinding() {
        GlMutableBuffer buffer = buffer(19);
        GlVertexArray bound = array(20);
        GlVertexArray other = array(21);
        tracker.makeVertexArrayActive(bound);
        tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer);
        tracker.notifyVertexArrayDeleted(other);
        tracker.notifyVertexArrayDeleted(other);
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer));
        assertFalse(tracker.makeVertexArrayActive(bound));
    }

    // Checks that deleting any vertex array while the vertex array binding is unknown forgets every buffer binding
    @Test
    void deletingAVertexArrayWhileTheBindingIsUnknownForgetsEveryBufferBinding() {
        GlMutableBuffer buffer = buffer(22);
        tracker.clear();
        tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer);
        tracker.notifyVertexArrayDeleted(array(23));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, buffer));
        GlStateTracker fresh = new GlStateTracker();
        fresh.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer);
        fresh.notifyVertexArrayDeleted(array(24));
        assertTrue(fresh.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
    }

    // Checks that deleting a buffer forgets only the binding points that hold it
    @Test
    void deletingABufferForgetsOnlyItsBindingPoints() {
        GlMutableBuffer deleted = buffer(25);
        GlMutableBuffer kept = buffer(26);
        GlVertexArray array = array(27);
        tracker.makeVertexArrayActive(array);
        tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, deleted);
        tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, deleted);
        tracker.makeBufferActive(GlBufferTarget.COPY_READ_BUFFER, kept);
        tracker.notifyBufferDeleted(deleted);
        assertFalse(tracker.makeBufferActive(GlBufferTarget.COPY_READ_BUFFER, kept));
        assertFalse(tracker.makeVertexArrayActive(array));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, deleted));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ELEMENT_BUFFER, deleted));
    }

    // Checks that deleting a buffer that no binding point holds keeps every binding
    @Test
    void deletingAnUnboundBufferKeepsEveryBinding() {
        GlMutableBuffer bound = buffer(28);
        GlMutableBuffer other = buffer(29);
        for (GlBufferTarget target : TARGETS) {
            tracker.makeBufferActive(target, bound);
        }
        tracker.notifyBufferDeleted(other);
        tracker.notifyBufferDeleted(other);
        for (GlBufferTarget target : TARGETS) {
            assertFalse(tracker.makeBufferActive(target, bound), target.name());
        }
    }

    // Checks that a binding point forgotten before the deletion stays forgotten and a later bind is kept
    @Test
    void deletingAForgottenBufferKeepsItForgotten() {
        GlMutableBuffer first = buffer(30);
        GlMutableBuffer second = buffer(31);
        tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, first);
        tracker.clear();
        tracker.notifyBufferDeleted(first);
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, first));
        tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, second);
        tracker.notifyBufferDeleted(first);
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, second));
    }

    // Checks that a clear forgets every buffer binding and the vertex array binding and repeats safely
    @Test
    void clearForgetsEveryBinding() {
        GlMutableBuffer buffer = buffer(32);
        GlVertexArray array = array(33);
        tracker.makeVertexArrayActive(array);
        for (GlBufferTarget target : TARGETS) {
            tracker.makeBufferActive(target, buffer);
        }
        tracker.clear();
        tracker.clear();
        for (GlBufferTarget target : TARGETS) {
            assertTrue(tracker.makeBufferActive(target, buffer), target.name());
        }
        assertTrue(tracker.makeVertexArrayActive(array));
    }

    // Checks that null arguments are refused and change no binding
    @Test
    void nullArgumentsAreRefusedWithoutAChange() {
        GlMutableBuffer buffer = buffer(34);
        GlVertexArray array = array(35);
        tracker.makeVertexArrayActive(array);
        tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer);
        assertEquals("Target must not be null", assertThrows(NullPointerException.class, () -> tracker.makeBufferActive(null, buffer)).getMessage());
        assertEquals("Target must not be null", assertThrows(NullPointerException.class, () -> tracker.makeBufferActive(null, null)).getMessage());
        assertEquals("Buffer must not be null", assertThrows(NullPointerException.class, () -> tracker.notifyBufferDeleted(null)).getMessage());
        assertEquals("Vertex array must not be null", assertThrows(NullPointerException.class, () -> tracker.notifyVertexArrayDeleted(null)).getMessage());
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertFalse(tracker.makeVertexArrayActive(array));
    }

    // Checks that deleted objects are refused and change no binding
    @Test
    void deletedObjectsAreRefusedWithoutAChange() {
        GlMutableBuffer buffer = buffer(36);
        GlVertexArray array = array(37);
        GlMutableBuffer deletedBuffer = buffer(38);
        GlVertexArray deletedArray = array(39);
        deletedBuffer.delete();
        deletedArray.delete();
        tracker.makeVertexArrayActive(array);
        tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer);
        assertThrows(IllegalStateException.class, () -> tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, deletedBuffer));
        assertThrows(IllegalStateException.class, () -> tracker.notifyBufferDeleted(deletedBuffer));
        assertThrows(IllegalStateException.class, () -> tracker.makeVertexArrayActive(deletedArray));
        assertThrows(IllegalStateException.class, () -> tracker.notifyVertexArrayDeleted(deletedArray));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertFalse(tracker.makeVertexArrayActive(array));
    }

    // Checks that every binding point and the vertex array bit fit in one int mask
    @Test
    void bindingPointsFitTheKnownMask() {
        assertTrue(GlBufferTarget.COUNT < Integer.SIZE);
        for (GlBufferTarget target : TARGETS) {
            assertTrue(target.ordinal() < GlBufferTarget.COUNT);
        }
    }

    // Checks the tracker against a reference model over random binds and deletions and clears
    @Test
    void randomOperationsMatchAReferenceModel() {
        int[] names = {1, 2, 3, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
        GlBuffer[] buffers = new GlBuffer[names.length];
        GlVertexArray[] arrays = new GlVertexArray[names.length];
        for (int i = 0; i < names.length; i++) {
            buffers[i] = buffer(names[i]);
            arrays[i] = array(names[i]);
        }
        // Holds the bound name of each known binding point and the bound vertex array name when known
        Map<GlBufferTarget, Integer> boundBuffers = new HashMap<>();
        Integer[] boundArray = {null};
        SplittableRandom random = new SplittableRandom(0x5EEDL);
        for (int step = 0; step < 200_000; step++) {
            int pick = random.nextInt(names.length + 1);
            int name = pick == names.length ? 0 : names[pick];
            switch (random.nextInt(10)) {
                case 0, 1, 2, 3 -> {
                    GlBufferTarget target = TARGETS[random.nextInt(TARGETS.length)];
                    boolean expected = !Integer.valueOf(name).equals(boundBuffers.get(target));
                    boundBuffers.put(target, name);
                    assertEquals(expected, tracker.makeBufferActive(target, pick == names.length ? null : buffers[pick]), "step " + step);
                }
                case 4, 5 -> {
                    boolean expected = !Integer.valueOf(name).equals(boundArray[0]);
                    if (expected) {
                        boundBuffers.clear();
                        boundArray[0] = name;
                    }
                    assertEquals(expected, tracker.makeVertexArrayActive(pick == names.length ? null : arrays[pick]), "step " + step);
                }
                case 6 -> {
                    if (pick < names.length) {
                        boundBuffers.values().removeIf(bound -> bound == names[pick]);
                        tracker.notifyBufferDeleted(buffers[pick]);
                    }
                }
                case 7 -> {
                    if (pick < names.length) {
                        if (boundArray[0] == null || boundArray[0] == names[pick]) {
                            boundBuffers.clear();
                            boundArray[0] = null;
                        }
                        tracker.notifyVertexArrayDeleted(arrays[pick]);
                    }
                }
                case 8 -> {
                    if (random.nextInt(8) == 0) {
                        boundBuffers.clear();
                        boundArray[0] = null;
                        tracker.clear();
                    }
                }
                default -> {
                    GlBufferTarget target = TARGETS[random.nextInt(TARGETS.length)];
                    boolean expected = !Integer.valueOf(0).equals(boundBuffers.get(target));
                    boundBuffers.put(target, 0);
                    assertEquals(expected, tracker.makeBufferActive(target, null), "step " + step);
                }
            }
        }
    }
}
