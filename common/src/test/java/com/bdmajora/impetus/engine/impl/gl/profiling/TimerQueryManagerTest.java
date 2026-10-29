package com.bdmajora.impetus.engine.impl.gl.profiling;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.IntToLongFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;

// Tests of the timestamp query pairs and of the query name pool that every manager shares
class TimerQueryManagerTest {
    // Mocked GL service that answers as a fake GPU
    private final LWJGLService gl = TestGl.gl();
    // Issue order of the timestamp last written to each query name
    private final Map<Integer, Integer> issued = new HashMap<>();
    // Number of query names generated so far
    private int generated;
    // Number of timestamps issued so far
    private int issuedCount;
    // Number of issued timestamps the fake GPU has completed
    private int completed = Integer.MAX_VALUE;
    // Timestamp value the fake GPU writes for each issue order
    private IntToLongFunction clock = order -> (long) order * order;

    // Restores the shared pool and wires the fake GPU into the GL mock
    @BeforeEach
    void installFakeGpu() {
        resetPool();
        Mockito.doAnswer(this::generate).when(gl).glGenQueries();
        Mockito.doAnswer(this::issue).when(gl).glQueryCounter(anyInt(), anyInt());
        Mockito.doAnswer(this::available).when(gl).glGetQueryObjecti(anyInt(), anyInt());
        Mockito.doAnswer(this::result).when(gl).glGetQueryObjectui64(anyInt(), anyInt());
        Mockito.clearInvocations(gl);
    }

    // Restores the shared pool for the tests that run next
    @AfterEach
    void restorePool() {
        resetPool();
    }

    // Returns the next fresh query name
    private Object generate(InvocationOnMock invocation) {
        return ++generated;
    }

    // Records the issue order of a timestamp written to a query name
    private Object issue(InvocationOnMock invocation) {
        int name = invocation.getArgument(0);
        int target = invocation.getArgument(1);
        assertEquals(GL33.GL_TIMESTAMP, target);
        issued.put(name, ++issuedCount);
        return null;
    }

    // Returns 1 when the last timestamp written to the name has completed and 0 otherwise
    private Object available(InvocationOnMock invocation) {
        int name = invocation.getArgument(0);
        int pname = invocation.getArgument(1);
        assertEquals(GL15.GL_QUERY_RESULT_AVAILABLE, pname);
        return isComplete(name) ? 1 : 0;
    }

    // Returns the timestamp of a completed query and fails a read that would block
    private Object result(InvocationOnMock invocation) {
        int name = invocation.getArgument(0);
        int pname = invocation.getArgument(1);
        assertEquals(GL15.GL_QUERY_RESULT, pname);
        assertTrue(isComplete(name), "Read of a pending query result");
        return clock.applyAsLong(issued.get(name));
    }

    // Checks whether the last timestamp written to the name has completed
    private boolean isComplete(int name) {
        Integer order = issued.get(name);
        return order != null && order <= completed;
    }

    // Restores the shared pool to its state at class initialisation
    private static void resetPool() {
        int capacity = Statics.get(TimerQueryManager.class, "INITIAL_POOL_CAPACITY");
        Statics.set(TimerQueryManager.class, "queryPool", new int[capacity]);
        Statics.set(TimerQueryManager.class, "poolHead", 0);
        Statics.set(TimerQueryManager.class, "poolSize", 0);
    }

    // Returns the number of pooled names
    private static int poolSize() {
        return Statics.get(TimerQueryManager.class, "poolSize");
    }

    // Returns the pooled names oldest first
    private static int[] pooled() {
        int[] ring = Statics.get(TimerQueryManager.class, "queryPool");
        int head = Statics.get(TimerQueryManager.class, "poolHead");
        int[] names = new int[poolSize()];

        for (int i = 0; i < names.length; i++) {
            names[i] = ring[(head + i) & (ring.length - 1)];
        }

        return names;
    }

    // Starts and finishes the given number of pairs
    private static void profile(TimerQueryManager manager, int pairs) {
        for (int i = 0; i < pairs; i++) {
            manager.startProfiling();
            manager.finishProfiling();
        }
    }

    // Returns the time the given pair records when every timestamp so far was issued in pair order
    private static long delta(int pair) {
        return 4L * pair - 1;
    }

    // Checks that a new manager holds no reading and makes no GL call
    @Test
    void newManagerIsIdle() {
        TimerQueryManager manager = new TimerQueryManager();

        assertEquals(0L, manager.getLastTime());
        assertFalse(manager.updateTime());
        manager.close();

        assertEquals(0L, manager.getLastTime());
        assertEquals(0, poolSize());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that a start generates a name and writes one timestamp to it
    @Test
    void startGeneratesANameAndWritesATimestamp() {
        new TimerQueryManager().startProfiling();

        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glGenQueries();
        order.verify(gl).glQueryCounter(1, GL33.GL_TIMESTAMP);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that a second start before the end is refused and allocates nothing
    @Test
    void startTwiceIsRefused() {
        TimerQueryManager manager = new TimerQueryManager();
        manager.startProfiling();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::startProfiling);

        assertEquals("Query already started but not ended", thrown.getMessage());
        Mockito.verify(gl).glGenQueries();
        Mockito.verify(gl).glQueryCounter(anyInt(), anyInt());
        manager.finishProfiling();
        manager.close();
        assertArrayEquals(new int[] {1, 2}, pooled());
    }

    // Checks that pooled names from another manager are reused oldest first before a name is generated
    @Test
    void startReusesPooledNamesOldestFirst() {
        TimerQueryManager first = new TimerQueryManager();
        profile(first, 3);
        assertTrue(first.updateTime());
        Mockito.clearInvocations(gl);

        TimerQueryManager second = new TimerQueryManager();
        second.startProfiling();
        second.finishProfiling();
        second.startProfiling();

        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glQueryCounter(1, GL33.GL_TIMESTAMP);
        order.verify(gl).glQueryCounter(2, GL33.GL_TIMESTAMP);
        order.verify(gl).glGenQueries();
        order.verify(gl).glQueryCounter(7, GL33.GL_TIMESTAMP);
        Mockito.verifyNoMoreInteractions(gl);
        assertEquals(0, poolSize());
    }

    // Checks that the pool keeps release order when it grows with its oldest name past the end of its array
    @Test
    void poolKeepsReleaseOrderAcrossWrappedGrowth() {
        Statics.set(TimerQueryManager.class, "queryPool", new int[4]);
        TimerQueryManager holder = new TimerQueryManager();
        profile(holder, 8);
        TimerQueryManager closed = new TimerQueryManager();
        profile(closed, 2);
        closed.close();
        TimerQueryManager taker = new TimerQueryManager();
        profile(taker, 1);
        assertArrayEquals(new int[] {19, 20}, pooled());

        holder.close();

        assertArrayEquals(new int[] {19, 20, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, pooled());
        assertEquals(32, Statics.<int[]>get(TimerQueryManager.class, "queryPool").length);
        taker.startProfiling();
        assertArrayEquals(new int[] {20, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, pooled());
        Mockito.verify(gl, Mockito.times(20)).glGenQueries();
        Mockito.verify(gl, Mockito.times(2)).glQueryCounter(19, GL33.GL_TIMESTAMP);
    }

    // Checks that a failed start timestamp returns the name to the pool and leaves the manager idle
    @Test
    void failedStartReturnsTheName() {
        TimerQueryManager manager = new TimerQueryManager();
        RuntimeException lost = new RuntimeException("lost");
        Mockito.doThrow(lost).doAnswer(this::issue).when(gl).glQueryCounter(anyInt(), anyInt());

        assertSame(lost, assertThrows(RuntimeException.class, manager::startProfiling));

        assertArrayEquals(new int[] {1}, pooled());
        assertThrows(IllegalStateException.class, manager::finishProfiling);
        manager.startProfiling();
        Mockito.verify(gl).glGenQueries();
        Mockito.verify(gl, Mockito.times(2)).glQueryCounter(1, GL33.GL_TIMESTAMP);
    }

    // Checks that a failed name generation reaches the caller and leaves the manager idle
    @Test
    void failedGenerationLeavesTheManagerIdle() {
        TimerQueryManager manager = new TimerQueryManager();
        RuntimeException lost = new RuntimeException("lost");
        Mockito.doThrow(lost).doAnswer(this::generate).when(gl).glGenQueries();

        assertSame(lost, assertThrows(RuntimeException.class, manager::startProfiling));

        Mockito.verify(gl, Mockito.never()).glQueryCounter(anyInt(), anyInt());
        assertEquals(0, poolSize());
        assertThrows(IllegalStateException.class, manager::finishProfiling);
        profile(manager, 1);
        manager.close();
        assertArrayEquals(new int[] {1, 2}, pooled());
    }

    // Checks that names at the int limits pass through the pairs and the pool unchanged
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -2, -1, 0, Integer.MAX_VALUE})
    void namesAtTheIntLimitsPassThroughUnchanged(int name) {
        generated = 100;
        Mockito.doReturn(name).doAnswer(this::generate).when(gl).glGenQueries();
        TimerQueryManager manager = new TimerQueryManager();

        profile(manager, 3);

        assertTrue(manager.updateTime());
        assertEquals(delta(1), manager.getLastTime());
        Mockito.verify(gl).glQueryCounter(name, GL33.GL_TIMESTAMP);
        Mockito.verify(gl).glGetQueryObjecti(101, GL15.GL_QUERY_RESULT_AVAILABLE);
        Mockito.verify(gl).glGetQueryObjectui64(name, GL15.GL_QUERY_RESULT);
        assertArrayEquals(new int[] {name, 101}, pooled());
    }

    // Checks that an end without a start is refused and makes no GL call
    @Test
    void finishWithoutStartIsRefused() {
        TimerQueryManager manager = new TimerQueryManager();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::finishProfiling);

        assertEquals("Trying to end query that hasn't started yet", thrown.getMessage());
        Mockito.verifyNoInteractions(gl);
    }

    // Checks that a second end after one start is refused and allocates nothing
    @Test
    void finishTwiceIsRefused() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 1);

        assertThrows(IllegalStateException.class, manager::finishProfiling);

        Mockito.verify(gl, Mockito.times(2)).glGenQueries();
        Mockito.verify(gl, Mockito.times(2)).glQueryCounter(anyInt(), anyInt());
    }

    // Checks that the end timestamp goes to a second name after the start timestamp
    @Test
    void finishWritesTheEndTimestampAfterTheStart() {
        profile(new TimerQueryManager(), 1);

        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glQueryCounter(1, GL33.GL_TIMESTAMP);
        order.verify(gl).glQueryCounter(2, GL33.GL_TIMESTAMP);
        assertEquals(0, poolSize());
    }

    // Checks that a failed end timestamp returns the end name to the pool and keeps the pair open
    @Test
    void failedFinishKeepsThePairOpen() {
        TimerQueryManager manager = new TimerQueryManager();
        manager.startProfiling();
        RuntimeException lost = new RuntimeException("lost");
        Mockito.doThrow(lost).doAnswer(this::issue).when(gl).glQueryCounter(anyInt(), anyInt());

        assertSame(lost, assertThrows(RuntimeException.class, manager::finishProfiling));

        assertArrayEquals(new int[] {2}, pooled());
        assertThrows(IllegalStateException.class, manager::startProfiling);
        manager.finishProfiling();
        profile(manager, 2);
        assertTrue(manager.updateTime());
        assertEquals(delta(1), manager.getLastTime());
        Mockito.verify(gl, Mockito.times(6)).glGenQueries();
    }

    // Checks that eight pairs stay queued and a ninth drops the oldest pair unread
    @Test
    void finishDropsTheOldestPairPastEight() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 8);
        assertEquals(0, poolSize());

        profile(manager, 1);

        assertArrayEquals(new int[] {1, 2}, pooled());
        Mockito.verify(gl, Mockito.never()).glGetQueryObjecti(anyInt(), anyInt());
        assertTrue(manager.updateTime());
        assertEquals(delta(2), manager.getLastTime());
    }

    // Checks that endless pairs without an update cycle through a fixed set of names
    @Test
    void endlessPairsWithoutUpdatesReuseAFixedSetOfNames() {
        TimerQueryManager manager = new TimerQueryManager();

        profile(manager, 1000);

        Mockito.verify(gl, Mockito.times(18)).glGenQueries();
        assertEquals(2, poolSize());
        assertTrue(manager.updateTime());
        assertEquals(delta(993), manager.getLastTime());
    }

    // Checks that fewer than three pairs give no reading and read no query
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void updateNeedsThreePairs(int pairs) {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, pairs);

        assertFalse(manager.updateTime());

        assertEquals(0L, manager.getLastTime());
        Mockito.verify(gl, Mockito.never()).glGetQueryObjecti(anyInt(), anyInt());
        Mockito.verify(gl, Mockito.never()).glGetQueryObjectui64(anyInt(), anyInt());
    }

    // Checks that a pending oldest pair stays queued and only its end query is checked
    @Test
    void updateLeavesAPendingPairQueued() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        completed = 1;

        assertFalse(manager.updateTime());
        assertFalse(manager.updateTime());

        Mockito.verify(gl, Mockito.times(2)).glGetQueryObjecti(2, GL15.GL_QUERY_RESULT_AVAILABLE);
        Mockito.verify(gl, Mockito.never()).glGetQueryObjecti(Mockito.intThat(n -> n != 2), anyInt());
        Mockito.verify(gl, Mockito.never()).glGetQueryObjectui64(anyInt(), anyInt());
        assertEquals(0L, manager.getLastTime());
        completed = 2;
        assertTrue(manager.updateTime());
        assertEquals(delta(1), manager.getLastTime());
    }

    // Checks that a reading reads the oldest start then its end and returns both names to the pool
    @Test
    void updateReadsTheOldestPairAndRecyclesItsNames() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        Mockito.clearInvocations(gl);

        assertTrue(manager.updateTime());

        assertEquals(delta(1), manager.getLastTime());
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glGetQueryObjecti(2, GL15.GL_QUERY_RESULT_AVAILABLE);
        order.verify(gl).glGetQueryObjectui64(1, GL15.GL_QUERY_RESULT);
        order.verify(gl).glGetQueryObjectui64(2, GL15.GL_QUERY_RESULT);
        Mockito.verifyNoMoreInteractions(gl);
        assertArrayEquals(new int[] {1, 2}, pooled());
    }

    // Checks that each update reads one pair until fewer than three remain
    @Test
    void updateReadsOnePairPerCall() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 5);

        assertTrue(manager.updateTime());
        assertEquals(delta(1), manager.getLastTime());
        assertTrue(manager.updateTime());
        assertEquals(delta(2), manager.getLastTime());
        assertTrue(manager.updateTime());
        assertEquals(delta(3), manager.getLastTime());
        assertFalse(manager.updateTime());

        assertEquals(delta(3), manager.getLastTime());
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6}, pooled());
    }

    // Checks that an update neither reads nor releases the open start
    @Test
    void updateIgnoresTheOpenPair() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        manager.startProfiling();

        assertTrue(manager.updateTime());

        Mockito.verify(gl, Mockito.never()).glGetQueryObjecti(Mockito.eq(7), anyInt());
        Mockito.verify(gl, Mockito.never()).glGetQueryObjectui64(Mockito.eq(7), anyInt());
        assertArrayEquals(new int[] {1, 2}, pooled());
        manager.finishProfiling();
        assertArrayEquals(new int[] {2}, pooled());
        assertTrue(manager.updateTime());
        assertEquals(delta(2), manager.getLastTime());
    }

    // Checks that a failed result read keeps the pair queued and the last reading unchanged
    @Test
    void failedReadKeepsThePair() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        RuntimeException lost = new RuntimeException("lost");
        Mockito.doAnswer(this::result).doThrow(lost).doAnswer(this::result).when(gl).glGetQueryObjectui64(anyInt(), anyInt());

        assertSame(lost, assertThrows(RuntimeException.class, manager::updateTime));

        assertEquals(0L, manager.getLastTime());
        assertEquals(0, poolSize());
        assertTrue(manager.updateTime());
        assertEquals(delta(1), manager.getLastTime());
        assertArrayEquals(new int[] {1, 2}, pooled());
    }

    // Checks that a failed availability check keeps the pair queued
    @Test
    void failedAvailabilityCheckKeepsThePair() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        RuntimeException lost = new RuntimeException("lost");
        Mockito.doThrow(lost).doAnswer(this::available).when(gl).glGetQueryObjecti(anyInt(), anyInt());

        assertSame(lost, assertThrows(RuntimeException.class, manager::updateTime));

        Mockito.verify(gl, Mockito.never()).glGetQueryObjectui64(anyInt(), anyInt());
        assertEquals(0, poolSize());
        assertTrue(manager.updateTime());
        assertEquals(delta(1), manager.getLastTime());
    }

    // Checks that any nonzero availability answer counts as available
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 1, 2, Integer.MAX_VALUE})
    void anyNonZeroAvailabilityAnswerReads(int answer) {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        Mockito.doReturn(answer).when(gl).glGetQueryObjecti(anyInt(), anyInt());

        assertTrue(manager.updateTime());

        assertEquals(delta(1), manager.getLastTime());
    }

    // Checks that the time is the end timestamp minus the start timestamp in wrapping 64 bit arithmetic
    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "0, 9223372036854775807, 9223372036854775807",
            "9223372036854775807, -9223372036854775808, 1",
            "-1, 0, 1",
            "-2, -1, 1",
            "-9223372036854775808, 9223372036854775807, -1",
            "10, 4, -6"
    })
    void timeIsTheWrappedTimestampDifference(long start, long end, long expected) {
        clock = order -> order == 1 ? start : end;
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);

        assertTrue(manager.updateTime());

        assertEquals(expected, manager.getLastTime());
    }

    // Checks that close returns every pair oldest first and then the open start without a GL call
    @Test
    void closeReturnsEveryNameOldestFirst() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        manager.startProfiling();
        Mockito.clearInvocations(gl);

        manager.close();

        Mockito.verifyNoInteractions(gl);
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 7}, pooled());
        assertThrows(IllegalStateException.class, manager::finishProfiling);
        assertFalse(manager.updateTime());
    }

    // Checks that close after the ring wrapped returns each live name once and oldest first
    @Test
    void closeAfterTheRingWrapped() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 11);
        assertTrue(manager.updateTime());
        assertEquals(delta(4), manager.getLastTime());

        manager.close();

        assertArrayEquals(new int[] {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 1, 2, 3, 4}, pooled());
    }

    // Checks that a second close returns nothing again
    @Test
    void closeTwiceReturnsEachNameOnce() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 1);
        manager.startProfiling();

        manager.close();
        manager.close();

        assertArrayEquals(new int[] {1, 2, 3}, pooled());
    }

    // Checks that a closed manager starts over and keeps its last reading
    @Test
    void closedManagerIsReusable() {
        TimerQueryManager manager = new TimerQueryManager();
        profile(manager, 3);
        assertTrue(manager.updateTime());
        manager.startProfiling();

        manager.close();

        assertEquals(delta(1), manager.getLastTime());
        assertFalse(manager.updateTime());
        profile(manager, 3);
        assertTrue(manager.updateTime());
        assertEquals(clock.applyAsLong(9) - clock.applyAsLong(8), manager.getLastTime());
        Mockito.verify(gl, Mockito.times(6)).glGenQueries();
    }

    // Checks random operation sequences against a reference that queues every pair and trims only on update
    @Test
    void matchesTheReferenceOverRandomSequences() {
        Random random = new Random(0x71AE5L);
        TimerQueryManager manager = new TimerQueryManager();
        Reference reference = new Reference();
        completed = 0;

        for (int step = 0; step < 20_000; step++) {
            int operation = random.nextInt(10);

            if (operation < 3) {
                assertSameOutcome(manager::startProfiling, reference::start);
            } else if (operation < 6) {
                assertSameOutcome(manager::finishProfiling, reference::finish);
            } else if (operation < 8) {
                assertEquals(reference.update(), manager.updateTime());
            } else if (operation == 8) {
                completed = Math.max(completed, issuedCount - random.nextInt(12));
            } else if (random.nextInt(8) == 0) {
                manager.close();
                reference.close();
            }

            assertEquals(reference.lastTime, manager.getLastTime());

            if (step % 1000 == 0) {
                Mockito.clearInvocations(gl);
            }
        }

        manager.close();
        int[] names = pooled();
        assertEquals(generated, names.length);
        assertEquals(generated, Arrays.stream(names).distinct().count());
        assertTrue(generated <= 18, "Names generated " + generated);
    }

    // Runs an operation on the manager and then on the reference and checks both fail alike or both complete
    private static void assertSameOutcome(Runnable actual, Runnable expected) {
        assertEquals(outcome(actual), outcome(expected));
    }

    // Returns the class and message of the exception an operation throws or null when it completes
    private static String outcome(Runnable operation) {
        try {
            operation.run();
            return null;
        } catch (RuntimeException thrown) {
            return thrown.getClass().getName() + " " + thrown.getMessage();
        }
    }

    // Reference of the reading rules that queues every pair and trims to eight pairs only on update
    private final class Reference {
        // Issue orders of each queued pair with the start first
        private final ArrayDeque<int[]> queue = new ArrayDeque<>();
        // Issue order of the open start timestamp
        private int start;
        // Flag set while a pair is open
        private boolean open;
        // Time of the last pair read back
        private long lastTime;

        // Opens a pair at the issue order the manager last wrote
        void start() {
            if (open) {
                throw new IllegalStateException("Query already started but not ended");
            }

            start = issuedCount;
            open = true;
        }

        // Queues the open pair with the issue order the manager last wrote
        void finish() {
            if (!open) {
                throw new IllegalStateException("Trying to end query that hasn't started yet");
            }

            queue.add(new int[] {start, issuedCount});
            open = false;
        }

        // Trims to eight pairs and reads the oldest pair once three are queued and its end has completed
        boolean update() {
            while (queue.size() > 8) {
                queue.poll();
            }

            if (queue.size() < 3 || queue.peek()[1] > completed) {
                return false;
            }

            int[] pair = queue.poll();
            lastTime = clock.applyAsLong(pair[1]) - clock.applyAsLong(pair[0]);
            return true;
        }

        // Forgets every pair
        void close() {
            queue.clear();
            open = false;
        }
    }
}
