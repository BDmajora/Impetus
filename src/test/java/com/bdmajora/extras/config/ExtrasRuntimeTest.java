package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.ThreadTuning;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.booster.FastMath;
import com.bdmajora.extras.client.booster.FastRandom;
import com.bdmajora.extras.client.booster.StreamingUploader;
import com.bdmajora.extras.client.particle.ParticleTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkBuilder;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the copy of settings into the subsystems that hold them
class ExtrasRuntimeTest {
    // Launch switch of parallel ticking before each case
    private boolean installed;

    // Boots the game classes the thread tuning reads
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    // Remembers the launch switch of parallel ticking
    @BeforeEach
    void rememberLaunchSwitch() {
        installed = ParallelProcessor.INSTALLED;
    }

    // Puts every subsystem back to the defaults and the launch switch back to its value
    @AfterEach
    void restoreDefaults() {
        ExtrasRuntime.apply(new ExtrasConfig());
        ParallelProcessor.INSTALLED = installed;
    }

    // Checks that apply copies every live setting into its subsystem
    @Test
    void applyCopiesEveryLiveSetting() {
        ExtrasConfig options = new ExtrasConfig();
        options.gpuBooster.enabled = true;
        options.gpuBooster.fastMath = false;
        options.particle.parallelTick = false;
        options.particle.parallelModded = true;
        options.particle.lightCache = false;
        options.particle.cullOffscreen = false;
        options.particle.collisionCache = false;
        options.bakedEntities.signs = false;
        options.threads.removeRenderYield = true;
        options.threads.chunkBuilderPriority = Thread.MAX_PRIORITY;
        options.async.moddedEntities = true;
        ExtrasRuntime.apply(options);

        assertFalse(FastMath.enabled);
        assertTrue(FastRandom.enabled);
        assertTrue(StreamingUploader.enabled);
        assertTrue(StreamingUploader.formatCache);
        assertFalse(ParticleTicker.enabled);
        assertTrue(ParticleTicker.moddedParticles);
        assertFalse(ParticleTicker.lightCache);
        assertFalse(ParticleTicker.cullOffscreen);
        assertFalse(ParticleTicker.collisionCache);
        assertTrue(BakedEntities.chests);
        assertFalse(BakedEntities.signs);
        assertTrue(ThreadTuning.removeRenderYield);
        assertEquals(Thread.MAX_PRIORITY, ChunkBuilder.WORKER_PRIORITY);
        assertTrue(ParallelProcessor.moddedEntities);
    }

    // Checks that the live parallel switches follow the launch switch that install sets
    @Test
    void installSetsTheLaunchSwitchThatGatesTheLiveSwitches() {
        ExtrasConfig options = new ExtrasConfig();
        options.async.enabled = false;
        ExtrasRuntime.install(options);
        ExtrasRuntime.apply(new ExtrasConfig());
        assertFalse(ParallelProcessor.INSTALLED);
        assertFalse(ParallelProcessor.entities);

        ExtrasRuntime.install(new ExtrasConfig());
        assertTrue(ParallelProcessor.INSTALLED);
        assertFalse(ParallelProcessor.entities);
        ExtrasRuntime.apply(new ExtrasConfig());
        assertTrue(ParallelProcessor.entities);
        assertTrue(ParallelProcessor.randomTicks);
        assertTrue(ParallelProcessor.spawning);
    }

    // Checks that repeated applies of the same config leave the same state
    @Test
    void repeatedAppliesAreStable() {
        ExtrasConfig options = new ExtrasConfig();
        options.particle.parallelTick = false;
        ExtrasRuntime.apply(options);
        ExtrasRuntime.apply(options);
        assertFalse(ParticleTicker.enabled);
        ExtrasRuntime.apply(new ExtrasConfig());
        assertTrue(ParticleTicker.enabled);
    }

    // Checks that a null config is rejected
    @Test
    void aNullConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> ExtrasRuntime.install(null));
        assertThrows(NullPointerException.class, () -> ExtrasRuntime.apply(null));
    }

    // Checks that the private constructor runs
    @Test
    void theConstructorRuns() {
        assertNotNull(Mixins.construct(ExtrasRuntime.class));
    }
}
