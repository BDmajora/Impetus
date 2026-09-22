package com.bdmajora.impetus.engine.impl.gl.profiling;

import com.bdmajora.impetus.lwjgl.GL15;
import com.bdmajora.impetus.lwjgl.GL32;
import com.bdmajora.impetus.lwjgl.GL33;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import lombok.Getter;

import java.io.Closeable;

public class TimerQueryManager implements Closeable {
    private static final int INVALID_ID = -1;
    // Frames to wait before reading a timer query back; same-frame reads force a full CPU/GPU sync, and three frames is past any driver's queue depth
    private static final int QUERY_FRAME_LAG_COUNT = 3;
    // Past this many pairs in flight the oldest are dropped unread: a GPU that far behind (a heavy pack with vsync off) would otherwise have every read block on it
    private static final int MAX_IN_FLIGHT = 8;

    // A start and end timestamp query pair awaiting results
    private record InFlightQuery(int startTime, int endTime) {
        // Whether both results can be read without waiting; the end timestamp is written last, so it alone answers for the pair
        boolean isAvailable() {
            return LWJGL.glGetQueryObjecti(this.endTime, GL15.GL_QUERY_RESULT_AVAILABLE) != 0;
        }

        long getTimeDelta() {
            long startTime = LWJGL.glGetQueryObjectui64(this.startTime, GL32.GL_QUERY_RESULT);
            long endTime = LWJGL.glGetQueryObjectui64(this.endTime, GL32.GL_QUERY_RESULT);
            return endTime - startTime;
        }

        void delete() {
            releaseQuery(startTime);
            releaseQuery(endTime);
        }
    }

    private final ObjectArrayFIFOQueue<InFlightQuery> inFlightQueries = new ObjectArrayFIFOQueue<>();
    private int startQueryId = INVALID_ID;

    private static final IntArrayFIFOQueue QUERY_POOL = new IntArrayFIFOQueue();

    @Getter
    private long lastTime;

    // From the pool, or a fresh glGenQueries
    private static int allocateQuery() {
        if (!QUERY_POOL.isEmpty()) {
            return QUERY_POOL.dequeueInt();
        } else {
            return LWJGL.glGenQueries();
        }
    }

    // Back to the pool
    private static void releaseQuery(int id) {
        QUERY_POOL.enqueue(id);
    }

    // Issues the start timestamp
    public void startProfiling() {
        if (startQueryId != INVALID_ID) {
            throw new IllegalStateException("Query already started but not ended");
        }
        int id = allocateQuery();
        LWJGL.glQueryCounter(id, GL33.GL_TIMESTAMP);
        startQueryId = id;
    }

    // Issues the end timestamp and queues the pair
    public void finishProfiling() {
        if (startQueryId == INVALID_ID) {
            throw new IllegalStateException("Trying to end query that hasn't started yet");
        }
        int id = allocateQuery();
        LWJGL.glQueryCounter(id, GL33.GL_TIMESTAMP);
        inFlightQueries.enqueue(new InFlightQuery(startQueryId, id));
        startQueryId = INVALID_ID;
    }

    // Reads back the oldest pair once it is old enough AND its result is in; GL_QUERY_RESULT on a pair the GPU has not reached blocks the render thread until it has, which with F3 open was a stall every frame the GPU ran more than the lag behind
    public void updateTime() {
        while (inFlightQueries.size() > MAX_IN_FLIGHT) {
            inFlightQueries.dequeue().delete();
        }
        if (inFlightQueries.size() < QUERY_FRAME_LAG_COUNT) {
            return;
        }
        var query = inFlightQueries.first();
        if (!query.isAvailable()) {
            return;
        }
        inFlightQueries.dequeue();
        lastTime = query.getTimeDelta();
        query.delete();
    }

    // Returns every query to the shared pool; the GL names themselves live as long as the context
    @Override
    public void close() {
        while (!inFlightQueries.isEmpty()) {
            inFlightQueries.dequeue().delete();
        }
        if (startQueryId != INVALID_ID) {
            releaseQuery(startQueryId);
            startQueryId = INVALID_ID;
        }
    }
}
