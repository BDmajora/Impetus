package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig.AsyncSettings;
import com.bdmajora.extras.ExtrasConfig.ClientTickSettings;
import com.bdmajora.extras.ExtrasConfig.LoadingSettings;
import com.bdmajora.extras.ExtrasConfig.NetworkSettings;
import com.bdmajora.extras.ExtrasConfig.ThreadSettings;

// Settings of parallel ticking and the network and thread scheduling and client ticks and loading
final class SystemSchema {
    // Parallel server ticking switches and worker count and serial entity list
    static final SettingGroup<AsyncSettings> ASYNC = SettingGroup.builder("parallel_ticking", c -> c.async, AsyncSettings::new)
            .bool("enabled", "Master switch for parallel server ticking (entities, random ticks, mob spawning across the worker pool); takes effect on the next launch",
                    s -> s.enabled, (s, v) -> s.enabled = v)
            .bool("entities", "Tick vanilla entities in parallel across the worker pool", s -> s.entities, (s, v) -> s.entities = v)
            .bool("moddedEntities", "Also tick modded entities in parallel; off keeps them on the main thread",
                    s -> s.moddedEntities, (s, v) -> s.moddedEntities = v)
            .bool("randomTicks", "Run block random ticks (crop growth, fire spread, ...) in parallel",
                    s -> s.randomTicks, (s, v) -> s.randomTicks = v)
            .bool("spawning", "Evaluate natural mob spawn attempts in parallel per chunk", s -> s.spawning, (s, v) -> s.spawning = v)
            .integer("threads", 0, AsyncSettings.THREADS_MAX,
                    "Worker threads for parallel ticking (0 = automatic, about three quarters of the cores)",
                    s -> s.threads, (s, v) -> s.threads = v)
            .list("synchronizedEntities", "Entity ids (or namespace:*) that always tick on the main thread",
                    s -> s.synchronizedEntities, (s, v) -> s.synchronizedEntities = v)
            .build();

    // Wire limit and timeout and Krypton switches
    static final SettingGroup<NetworkSettings> NETWORK = SettingGroup.builder("network", c -> c.network, NetworkSettings::new)
            .bool("largePackets", "Lift vanilla's packet, payload, NBT, string and chunk data size caps (2 MB frames, 32 KB strings, 1 MB payloads)",
                    s -> s.largePackets, (s, v) -> s.largePackets = v)
            .bool("flushConsolidation", "Queue packets sent during a server tick and flush each connection once at the end of the tick instead of per packet",
                    s -> s.flushConsolidation, (s, v) -> s.flushConsolidation = v)
            .bool("fastVarInts", "Table-driven varint sizing and single-write varint encoding",
                    s -> s.fastVarInts, (s, v) -> s.fastVarInts = v)
            .bool("pooledCompression", "Compress and decompress packets without copying them into fresh arrays",
                    s -> s.pooledCompression, (s, v) -> s.pooledCompression = v)
            .integer("readTimeoutSeconds", NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a connection may go silent before it is dropped (vanilla 30)",
                    s -> s.readTimeoutSeconds, (s, v) -> s.readTimeoutSeconds = v)
            .integer("loginTimeoutSeconds", NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a client may take to finish logging in (vanilla 30)",
                    s -> s.loginTimeoutSeconds, (s, v) -> s.loginTimeoutSeconds = v)
            .integer("keepAliveTimeoutSeconds", NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a client may take to answer a keep-alive (vanilla 15)",
                    s -> s.keepAliveTimeoutSeconds, (s, v) -> s.keepAliveTimeoutSeconds = v)
            .build();

    // Thread priorities and the render loop yield switch
    static final SettingGroup<ThreadSettings> THREADS = SettingGroup.builder("thread_scheduling", c -> c.threads, ThreadSettings::new)
            .bool("removeRenderYield", "Skip the Thread.yield() the render loop makes once per frame",
                    s -> s.removeRenderYield, (s, v) -> s.removeRenderYield = v)
            .integer("renderThreadPriority", ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the client (render) thread, 1-10 (5 = unchanged)",
                    s -> s.renderThreadPriority, (s, v) -> s.renderThreadPriority = v)
            .integer("serverThreadPriority", ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the integrated server thread, 1-10 (5 = unchanged)",
                    s -> s.serverThreadPriority, (s, v) -> s.serverThreadPriority = v)
            .integer("chunkBuilderPriority", ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the chunk builder threads, 1-10 (3 = unchanged)",
                    s -> s.chunkBuilderPriority, (s, v) -> s.chunkBuilderPriority = v)
            .build();

    // Lightmap caching switch
    static final SettingGroup<ClientTickSettings> CLIENT_TICK = SettingGroup.builder("client_tick", c -> c.clientTick, ClientTickSettings::new)
            .bool("lightmapCaching", "Skip rebuilding and uploading the lightmap while nothing but the torch flicker has changed",
                    s -> s.lightmapCaching, (s, v) -> s.lightmapCaching = v)
            .build();

    // World load and screenshot and atlas switches
    static final SettingGroup<LoadingSettings> LOADING = SettingGroup.builder("loading", c -> c.loading, LoadingSettings::new)
            .bool("skipWorldLoadGc", "Skip the forced full garbage collection on world load and unload",
                    s -> s.skipWorldLoadGc, (s, v) -> s.skipWorldLoadGc = v)
            .bool("smoothDimensionChange", "Skip the 'Loading terrain' screen on dimension change and respawn",
                    s -> s.smoothDimensionChange, (s, v) -> s.smoothDimensionChange = v)
            .bool("releaseScreenshotBuffers", "Free the screenshot readback buffers after each screenshot instead of keeping them forever",
                    s -> s.releaseScreenshotBuffers, (s, v) -> s.releaseScreenshotBuffers = v)
            .bool("asyncScreenshots", "Encode and write screenshots on a background thread",
                    s -> s.asyncScreenshots, (s, v) -> s.asyncScreenshots = v)
            .bool("driverAtlasLimit", "Ask the driver for its maximum texture size instead of probing proxy uploads from 16384 down",
                    s -> s.driverAtlasLimit, (s, v) -> s.driverAtlasLimit = v)
            .build();

    // Prevents instantiation
    private SystemSchema() {
    }
}
