package com.bdmajora.impetus.engine.impl.gl.profiling;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import lombok.Getter;

import java.io.Closeable;

// Timer that measures GPU time between pairs of timestamp queries and reads each pair back a few frames later
public class TimerQueryManager implements Closeable {
    // Number of queued pairs required before the oldest pair is read back
    private static final int QUERY_FRAME_LAG_COUNT = 3;
    // Number of pairs held in flight and a power of two
    private static final int MAX_IN_FLIGHT = 8;
    // Mask that wraps a slot index into the ring
    private static final int SLOT_MASK = MAX_IN_FLIGHT - 1;
    // Starting length of the shared pool and a power of two
    private static final int INITIAL_POOL_CAPACITY = 16;

    // Ring of query names released by every manager and handed out again oldest first
    private static int[] queryPool = new int[INITIAL_POOL_CAPACITY];
    // Index of the oldest pooled name
    private static int poolHead;
    // Number of pooled names
    private static int poolSize;

    // Ring of pairs with each start name at an even index and its end name at the next odd index
    private final int[] pairs = new int[MAX_IN_FLIGHT << 1];
    // Slot of the oldest pair in flight
    private int head;
    // Number of pairs in flight
    private int count;
    // Name of the start query of the open pair
    private int startQueryId;
    // Flag set between a start timestamp and its end timestamp
    private boolean started;

    // Nanoseconds between the two timestamps of the last pair read back
    @Getter
    private long lastTime;

    // Returns the oldest pooled query name or generates a new one
    private static int allocateQuery() {
        if (poolSize == 0) {
            return LWJGL.glGenQueries();
        }

        int id = queryPool[poolHead];
        poolHead = (poolHead + 1) & (queryPool.length - 1);
        poolSize--;
        return id;
    }

    // Appends a query name to the shared pool
    private static void releaseQuery(int id) {
        if (poolSize == queryPool.length) {
            growPool();
        }

        queryPool[(poolHead + poolSize) & (queryPool.length - 1)] = id;
        poolSize++;
    }

    // Doubles the shared pool and moves its names to the front oldest first
    private static void growPool() {
        int[] grown = new int[queryPool.length << 1];
        int wrapped = queryPool.length - poolHead;

        System.arraycopy(queryPool, poolHead, grown, 0, wrapped);
        System.arraycopy(queryPool, 0, grown, wrapped, poolHead);
        queryPool = grown;
        poolHead = 0;
    }

    // Allocates a query name and writes a timestamp to it
    private static int issueTimestamp() {
        int id = allocateQuery();

        try {
            LWJGL.glQueryCounter(id, GL33.GL_TIMESTAMP);
        } catch (Throwable failed) {
            // Returns the name to the pool before the failure propagates
            releaseQuery(id);
            throw failed;
        }

        return id;
    }

    // Returns both names of the pair in the given slot to the shared pool
    private void releasePair(int slot) {
        releaseQuery(pairs[slot << 1]);
        releaseQuery(pairs[(slot << 1) | 1]);
    }

    // Issues the start timestamp of a new pair
    public void startProfiling() {
        if (started) {
            throw new IllegalStateException("Query already started but not ended");
        }

        startQueryId = issueTimestamp();
        started = true;
    }

    // Issues the end timestamp and queues the open pair
    public void finishProfiling() {
        if (!started) {
            throw new IllegalStateException("Trying to end query that hasn't started yet");
        }

        int endQueryId = issueTimestamp();
        int slot = (head + count) & SLOT_MASK;

        // Drops the oldest pair unread when the ring is full
        if (count == MAX_IN_FLIGHT) {
            releasePair(slot);
            head = (head + 1) & SLOT_MASK;
        } else {
            count++;
        }

        pairs[slot << 1] = startQueryId;
        pairs[(slot << 1) | 1] = endQueryId;
        started = false;
    }

    // Reads back the oldest pair once enough pairs are queued and its end result is available and returns whether a reading landed
    public boolean updateTime() {
        if (count < QUERY_FRAME_LAG_COUNT) {
            return false;
        }

        int startQuery = pairs[head << 1];
        int endQuery = pairs[(head << 1) | 1];

        // Checks availability on the end query alone
        if (LWJGL.glGetQueryObjecti(endQuery, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
            return false;
        }

        long startTime = LWJGL.glGetQueryObjectui64(startQuery, GL15.GL_QUERY_RESULT);
        long endTime = LWJGL.glGetQueryObjectui64(endQuery, GL15.GL_QUERY_RESULT);

        releaseQuery(startQuery);
        releaseQuery(endQuery);
        head = (head + 1) & SLOT_MASK;
        count--;
        lastTime = endTime - startTime;
        return true;
    }

    // Returns every held query name to the shared pool and leaves the manager idle
    @Override
    public void close() {
        for (; count > 0; count--) {
            releasePair(head);
            head = (head + 1) & SLOT_MASK;
        }

        if (started) {
            releaseQuery(startQueryId);
            started = false;
        }
    }
}
