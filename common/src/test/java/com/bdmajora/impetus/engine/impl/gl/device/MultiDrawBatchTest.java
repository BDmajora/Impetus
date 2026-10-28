package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the base draw command batch through a counter probe and through both concrete batches over native memory
class MultiDrawBatchTest {
    // Message prefix of every capacity refusal
    private static final String CAPACITY_MESSAGE = "Capacity must be between 0 and 2147483646: ";

    // Capacities that every constructor rejects
    private static final int[] INVALID_CAPACITIES = {-1, -2, -1024, Integer.MIN_VALUE + 1, Integer.MIN_VALUE, Integer.MAX_VALUE};

    // Concrete batches that a test created and has not deleted
    private final List<MultiDrawBatch> live = new ArrayList<>();

    // Concrete batch kinds that the contract tests cover
    enum Kind {
        DIRECT,
        INDIRECT;

        // Creates a batch of this kind with the capacity
        MultiDrawBatch create(int capacity) {
            return this == DIRECT ? new DirectMultiDrawBatch(capacity) : new IndirectMultiDrawBatch(capacity);
        }
    }

    // Batch that exposes the protected counters and rejects every command operation
    private static final class Probe extends MultiDrawBatch {
        // Creates a probe with the capacity
        Probe(int capacity) {
            super(capacity);
        }

        // Stores the command count and the largest element count
        void set(int size, int maxElementCount) {
            this.size = size;
            this.maxElementCount = maxElementCount;
        }

        // Releases nothing
        @Override
        public void delete() {
        }

        // Rejects every command
        @Override
        public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
            throw new UnsupportedOperationException();
        }

        // Rejects every merge
        @Override
        public void mergeIntoLastCommand(int additionalElementCount) {
            throw new UnsupportedOperationException();
        }

        // Rejects every upload
        @Override
        public void upload(CommandList commandList) {
            throw new UnsupportedOperationException();
        }

        // Rejects every execution
        @Override
        public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
            throw new UnsupportedOperationException();
        }
    }

    // Deletes every concrete batch that a test left live
    @AfterEach
    void release() {
        for (MultiDrawBatch batch : live) {
            batch.delete();
        }
        live.clear();
    }

    // Creates a concrete batch that the cleanup deletes
    private MultiDrawBatch batch(Kind kind, int capacity) {
        MultiDrawBatch batch = kind.create(capacity);
        live.add(batch);
        return batch;
    }

    // Removes the batch from the cleanup list and deletes it
    private void free(MultiDrawBatch batch) {
        live.remove(batch);
        batch.delete();
    }

    // Returns a command list mock that hands out the draw list for every tessellation
    private static CommandList listDrawingWith(DrawCommandList draw) {
        CommandList list = Mockito.mock(CommandList.class);
        Mockito.when(list.beginTessellating(Mockito.any())).thenReturn(draw);
        return list;
    }

    // Returns the message of the exception of the type that the body throws
    private static String message(Class<? extends Throwable> type, Executable body) {
        return assertThrows(type, body).getMessage();
    }

    // Checks the command count and the largest element count and the empty flag of the batch
    private static void assertCounters(MultiDrawBatch batch, int size, int maxElementCount) {
        assertEquals(size, batch.size(), "size");
        assertEquals(maxElementCount, batch.getIndexBufferSize(), "largest element count");
        assertEquals(size <= 0, batch.isEmpty(), "empty flag");
    }

    // Checks that the base constructor rejects every negative capacity and the int limit
    @Test
    void constructorRejectsCapacityOutsideRange() {
        for (int capacity : INVALID_CAPACITIES) {
            assertEquals(CAPACITY_MESSAGE + capacity, message(IllegalArgumentException.class, () -> new Probe(capacity)));
        }
    }

    // Checks that the base constructor adds one spare slot up to the int limit and starts empty
    @Test
    void constructorAddsOneSpareSlot() {
        int[] capacities = {0, 1, 2, 1023, MultiDrawBatch.MAX_COMMAND_COUNT, Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1};
        for (int capacity : capacities) {
            Probe probe = new Probe(capacity);
            assertEquals(capacity + 1, probe.capacity(), "capacity of " + capacity);
            assertCounters(probe, 0, 0);
        }
    }

    // Checks that the command count covers every facing of every region section and one spare slot
    @Test
    void maxCommandCountCoversEveryFacingOfEverySection() {
        assertEquals(ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE + 1, MultiDrawBatch.MAX_COMMAND_COUNT);
        assertEquals(7 * 256 + 1, MultiDrawBatch.MAX_COMMAND_COUNT);
    }

    // Checks that size and the largest element count return the stored fields at the int limits
    @Test
    void gettersReturnStoredCounters() {
        Probe probe = new Probe(4);
        int[] values = {0, 1, 7, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        for (int value : values) {
            probe.set(value, value);
            assertEquals(value, probe.size());
            assertEquals(value, probe.getIndexBufferSize());
            assertEquals(5, probe.capacity());
        }
    }

    // Checks the empty flag at zero and one and at the int limits
    @Test
    void isEmptyTreatsZeroAndNegativeSizesAsEmpty() {
        Probe probe = new Probe(0);
        probe.set(0, 9);
        assertTrue(probe.isEmpty());
        probe.set(1, 0);
        assertFalse(probe.isEmpty());
        probe.set(Integer.MAX_VALUE, 0);
        assertFalse(probe.isEmpty());
        probe.set(-1, 0);
        assertTrue(probe.isEmpty());
        probe.set(Integer.MIN_VALUE, 0);
        assertTrue(probe.isEmpty());
    }

    // Checks that clear resets both counters and keeps the capacity on fresh and full and repeated calls
    @Test
    void clearResetsCountersAndKeepsCapacity() {
        Probe probe = new Probe(Integer.MAX_VALUE - 1);
        probe.clear();
        assertCounters(probe, 0, 0);

        probe.set(Integer.MAX_VALUE, Integer.MAX_VALUE);
        probe.clear();
        assertCounters(probe, 0, 0);
        probe.clear();
        assertCounters(probe, 0, 0);

        probe.set(Integer.MIN_VALUE, -1);
        probe.clear();
        assertCounters(probe, 0, 0);
        assertEquals(Integer.MAX_VALUE, probe.capacity());
    }

    // Checks the modifiers of every member of the base class
    @Test
    void memberModifiersArePinned() throws ReflectiveOperationException {
        int classModifiers = MultiDrawBatch.class.getModifiers();
        assertTrue(Modifier.isPublic(classModifiers) && Modifier.isAbstract(classModifiers));

        Constructor<?>[] constructors = MultiDrawBatch.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isProtected(constructors[0].getModifiers()));

        for (String name : new String[]{"size", "capacity", "isEmpty", "getIndexBufferSize"}) {
            int modifiers = MultiDrawBatch.class.getDeclaredMethod(name).getModifiers();
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isFinal(modifiers), name);
        }

        int clear = MultiDrawBatch.class.getDeclaredMethod("clear").getModifiers();
        assertTrue(Modifier.isPublic(clear) && !Modifier.isFinal(clear) && !Modifier.isAbstract(clear));

        Method[] abstracts = {
                MultiDrawBatch.class.getDeclaredMethod("delete"),
                MultiDrawBatch.class.getDeclaredMethod("appendDrawCommand", int.class, int.class, long.class),
                MultiDrawBatch.class.getDeclaredMethod("mergeIntoLastCommand", int.class),
                MultiDrawBatch.class.getDeclaredMethod("upload", CommandList.class),
                MultiDrawBatch.class.getDeclaredMethod("execute", CommandList.class, GlTessellation.class, GlPrimitiveType.class)};
        for (Method method : abstracts) {
            assertTrue(Modifier.isPublic(method.getModifiers()) && Modifier.isAbstract(method.getModifiers()), method.getName());
        }

        Field maxCommands = MultiDrawBatch.class.getDeclaredField("MAX_COMMAND_COUNT");
        int constant = maxCommands.getModifiers();
        assertTrue(Modifier.isPublic(constant) && Modifier.isStatic(constant) && Modifier.isFinal(constant));

        for (String name : new String[]{"size", "maxElementCount"}) {
            int modifiers = MultiDrawBatch.class.getDeclaredField(name).getModifiers();
            assertTrue(Modifier.isProtected(modifiers) && !Modifier.isFinal(modifiers), name);
        }
        int capacity = MultiDrawBatch.class.getDeclaredField("capacity").getModifiers();
        assertTrue(Modifier.isPrivate(capacity) && Modifier.isFinal(capacity));
    }

    // Checks that each concrete constructor rejects every capacity that the base rejects with the same message
    @ParameterizedTest
    @EnumSource(Kind.class)
    void concreteConstructorRejectsCapacityOutsideRange(Kind kind) {
        for (int capacity : INVALID_CAPACITIES) {
            assertEquals(CAPACITY_MESSAGE + capacity, message(IllegalArgumentException.class, () -> kind.create(capacity)));
        }
    }

    // Checks that each concrete batch starts empty with one spare slot
    @ParameterizedTest
    @EnumSource(Kind.class)
    void concreteBatchStartsEmptyWithSpareSlot(Kind kind) {
        for (int capacity : new int[]{0, 1, MultiDrawBatch.MAX_COMMAND_COUNT}) {
            MultiDrawBatch batch = batch(kind, capacity);
            assertEquals(capacity + 1, batch.capacity());
            assertCounters(batch, 0, 0);
        }
    }

    // Checks that only commands with elements raise the size and the largest count
    @ParameterizedTest
    @EnumSource(Kind.class)
    void appendCountsOnlyCommandsWithElements(Kind kind) {
        MultiDrawBatch batch = batch(kind, 4);

        batch.appendDrawCommand(5, 0, 0L);
        assertCounters(batch, 0, 0);

        batch.appendDrawCommand(0, 3, 0L);
        batch.appendDrawCommand(-7, 9, 12L);
        batch.appendDrawCommand(Integer.MIN_VALUE, 4, 64L);
        assertCounters(batch, 3, 9);

        batch.appendDrawCommand(1, 0, 0L);
        assertCounters(batch, 3, 9);
    }

    // Checks that a zero capacity holds one command in its spare slot and rejects a second
    @ParameterizedTest
    @EnumSource(Kind.class)
    void zeroCapacityHoldsOneCommand(Kind kind) {
        MultiDrawBatch batch = batch(kind, 0);
        batch.appendDrawCommand(0, 6, 0L);
        assertCounters(batch, 1, 6);

        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(0, 1, 0L)));
        assertCounters(batch, 1, 6);
    }

    // Checks that every slot accepts a command and the next append of any count is refused with the counters intact
    @ParameterizedTest
    @EnumSource(Kind.class)
    void fullBatchRefusesTheNextCommand(Kind kind) {
        MultiDrawBatch batch = batch(kind, 2);
        for (int slot = 0; slot < batch.capacity(); slot++) {
            batch.appendDrawCommand(slot, slot + 1, 0L);
        }
        assertCounters(batch, 3, 3);

        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(0, 100, 0L)));
        assertCounters(batch, 3, 3);

        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(0, 0, 0L)));
        assertCounters(batch, 3, 3);
    }

    // Checks that negative counts are refused with the counters intact
    @ParameterizedTest
    @EnumSource(Kind.class)
    void negativeCountsAreRefused(Kind kind) {
        MultiDrawBatch batch = batch(kind, 2);
        batch.appendDrawCommand(0, 5, 0L);

        for (int count : new int[]{-1, Integer.MIN_VALUE}) {
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.appendDrawCommand(0, count, 0L)));
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(count)));
        }
        assertCounters(batch, 1, 5);
    }

    // Checks that a merge raises the largest count only when the merged count passes it
    @ParameterizedTest
    @EnumSource(Kind.class)
    void mergeRaisesLargestCountOfLastCommand(Kind kind) {
        MultiDrawBatch batch = batch(kind, 3);
        batch.appendDrawCommand(0, 2, 0L);
        batch.mergeIntoLastCommand(5);
        assertCounters(batch, 1, 7);

        batch.appendDrawCommand(10, 1, 0L);
        batch.mergeIntoLastCommand(2);
        assertCounters(batch, 2, 7);

        batch.mergeIntoLastCommand(0);
        assertCounters(batch, 2, 7);

        batch.mergeIntoLastCommand(5);
        assertCounters(batch, 2, 8);
    }

    // Checks that a merge into a fresh or cleared batch or after a discarded command is refused
    @ParameterizedTest
    @EnumSource(Kind.class)
    void mergeRefusesBatchWithoutCommands(Kind kind) {
        MultiDrawBatch batch = batch(kind, 1);
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));

        batch.appendDrawCommand(0, 0, 0L);
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(0)));

        batch.appendDrawCommand(0, 4, 0L);
        batch.clear();
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
        assertCounters(batch, 0, 0);
    }

    // Checks the int limit of the element count and the refusal of a merge that overflows it
    @ParameterizedTest
    @EnumSource(Kind.class)
    void elementCountReachesIntLimitWithoutOverflow(Kind kind) {
        MultiDrawBatch batch = batch(kind, 1);
        batch.appendDrawCommand(Integer.MAX_VALUE, Integer.MAX_VALUE, 0L);
        assertCounters(batch, 1, Integer.MAX_VALUE);

        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(1));
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(Integer.MAX_VALUE));
        assertCounters(batch, 1, Integer.MAX_VALUE);

        batch.appendDrawCommand(0, Integer.MAX_VALUE - 1, 0L);
        batch.mergeIntoLastCommand(1);
        assertCounters(batch, 2, Integer.MAX_VALUE);
    }

    // Checks that clear resets the counters and opens every slot again on repeated rounds
    @ParameterizedTest
    @EnumSource(Kind.class)
    void clearReopensEverySlot(Kind kind) {
        MultiDrawBatch batch = batch(kind, 3);
        for (int round = 0; round < 3; round++) {
            for (int slot = 0; slot < batch.capacity(); slot++) {
                batch.appendDrawCommand(slot, 10 * (round + 1) + slot, 0L);
            }
            assertCounters(batch, 4, 10 * (round + 1) + 3);

            batch.clear();
            batch.clear();
            assertCounters(batch, 0, 0);
            assertEquals(4, batch.capacity());
        }
    }

    // Checks that a deleted batch keeps its counters and refuses every command operation on repeated deletes
    @ParameterizedTest
    @EnumSource(Kind.class)
    void deletedBatchRefusesCommandsAndKeepsCounters(Kind kind) {
        MultiDrawBatch batch = batch(kind, 2);
        batch.appendDrawCommand(0, 8, 0L);
        free(batch);
        batch.delete();

        assertCounters(batch, 1, 8);
        assertEquals(3, batch.capacity());

        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.appendDrawCommand(0, 1, 0L)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.upload(Mockito.mock(CommandList.class))));
        assertEquals("Batch is deleted", message(IllegalStateException.class,
                () -> batch.execute(Mockito.mock(CommandList.class), Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES)));

        batch.clear();
        assertCounters(batch, 0, 0);
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.appendDrawCommand(0, 1, 0L)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(0)));
        assertCounters(batch, 0, 0);
    }

    // Checks that upload and execute reject every null argument before any GL work
    @ParameterizedTest
    @EnumSource(Kind.class)
    void uploadAndExecuteRejectNullArguments(Kind kind) {
        MultiDrawBatch batch = batch(kind, 1);
        batch.appendDrawCommand(0, 3, 0L);
        CommandList list = Mockito.mock(CommandList.class);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);

        assertThrows(NullPointerException.class, () -> batch.upload(null));
        assertThrows(NullPointerException.class, () -> batch.execute(null, tessellation, GlPrimitiveType.TRIANGLES));
        assertThrows(NullPointerException.class, () -> batch.execute(list, null, GlPrimitiveType.TRIANGLES));
        assertThrows(NullPointerException.class, () -> batch.execute(list, tessellation, null));

        Mockito.verifyNoInteractions(list, tessellation);
        assertCounters(batch, 1, 3);
    }

    // Checks that an uploaded batch issues one multidraw for all its commands on every execution
    @ParameterizedTest
    @EnumSource(Kind.class)
    void executeIssuesOneMultidrawForEveryCommand(Kind kind) {
        MultiDrawBatch batch = batch(kind, 4);
        batch.appendDrawCommand(0, 6, 0L);
        batch.appendDrawCommand(40, 3, 24L);

        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);

        batch.upload(list);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);

        InOrder order = Mockito.inOrder(list, draw);
        for (int run = 0; run < 2; run++) {
            order.verify(list).beginTessellating(tessellation);
            if (kind == Kind.DIRECT) {
                order.verify(draw).multiDrawElementsBaseVertex((DirectMultiDrawBatch) batch, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            } else {
                order.verify(draw).multiDrawElementsIndirect(0L, 2, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            }
            order.verify(draw).close();
        }
        assertCounters(batch, 2, 6);
    }

    // Checks that an empty batch uploads and executes one multidraw of no commands
    @ParameterizedTest
    @EnumSource(Kind.class)
    void emptyBatchExecutesWithNoCommands(Kind kind) {
        MultiDrawBatch batch = batch(kind, 0);
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);

        batch.upload(list);
        batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.LINES);

        if (kind == Kind.DIRECT) {
            Mockito.verify(draw).multiDrawElementsBaseVertex((DirectMultiDrawBatch) batch, GlPrimitiveType.LINES, GlIndexType.UNSIGNED_INT);
        } else {
            Mockito.verify(draw).multiDrawElementsIndirect(0L, 0, GlPrimitiveType.LINES, GlIndexType.UNSIGNED_INT);
        }
        assertCounters(batch, 0, 0);
    }
}
