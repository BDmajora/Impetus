package com.bdmajora.impetus.umbra.uniforms;

import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.time.LocalDateTime;

// Frame time and counter uniforms.
public final class SystemTimeUniforms {
    // Shared frame timer.
    public static final Timer COUNTER = new Timer();

    // Prevents instantiation
    private SystemTimeUniforms() {
    }

    // Adds the frame time uniforms.
    public static void addSystemTimeUniforms(UniformCollector uniforms) {
        uniforms
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTimeCounter", COUNTER::getFrameTimeCounter)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTime", COUNTER::getLastFrameTime)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "frameCounter", COUNTER::getFrameCounter)
                .uniform3i(UniformUpdateFrequency.PER_TICK, "currentDate", SystemTimeUniforms::getCurrentDate)
                .uniform3i(UniformUpdateFrequency.PER_TICK, "currentTime", SystemTimeUniforms::getCurrentTime)
                .uniform2i(UniformUpdateFrequency.PER_TICK, "currentYearTime", SystemTimeUniforms::getCurrentYearTime);
    }

    // Returns the current date.
    private static Vector3i getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        return new Vector3i(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }

    // Returns the current time.
    private static Vector3i getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return new Vector3i(now.getHour(), now.getMinute(), now.getSecond());
    }

    // Returns the current year time.
    private static Vector2i getCurrentYearTime() {
        LocalDateTime now = LocalDateTime.now();
        int elapsed = ((now.getDayOfYear() - 1) * 86400)
                + (now.getHour() * 3600)
                + (now.getMinute() * 60)
                + now.getSecond();
        return new Vector2i(elapsed, now.toLocalDate().lengthOfYear() * 86400 - elapsed);
    }

    // Timer of frame time and count.
    public static final class Timer {
        // Value at which the frame counter wraps.
        private static final int FRAME_COUNTER_WRAP = 720720;
        // Seconds at which the frame time counter wraps.
        private static final float FRAME_TIME_WRAP_SECONDS = 3600.0f;

        // Frame counter.
        private int frameCounter;
        // Frame time counter.
        private float frameTimeCounter;
        // Last frame time.
        private float lastFrameTime;
        // Time of the last frame or minus one.
        private long lastFrameTimeNanos = -1L;

        // Steps the counters to a time.
        public void beginFrame(long nowNanos) {
            if (this.lastFrameTimeNanos >= 0) {
                float deltaSeconds = (nowNanos - this.lastFrameTimeNanos) / 1_000_000_000.0f;
                this.lastFrameTime = deltaSeconds;
                this.frameTimeCounter += deltaSeconds;
                if (this.frameTimeCounter > FRAME_TIME_WRAP_SECONDS) {
                    this.frameTimeCounter -= FRAME_TIME_WRAP_SECONDS;
                }
            }
            this.lastFrameTimeNanos = nowNanos;
            this.frameCounter = (this.frameCounter + 1) % FRAME_COUNTER_WRAP;
        }

        // Returns the last frame time.
        public float getLastFrameTime() {
            return this.lastFrameTime;
        }

        // Returns the frame time counter.
        public float getFrameTimeCounter() {
            return this.frameTimeCounter;
        }

        // Returns the frame counter.
        public int getFrameCounter() {
            return this.frameCounter;
        }

        // Restarts the timer.
        public void reset() {
            this.frameCounter = 0;
            this.frameTimeCounter = 0.0f;
            this.lastFrameTimeNanos = -1L;
        }
    }
}
