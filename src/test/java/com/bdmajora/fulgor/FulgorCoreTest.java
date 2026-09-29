package com.bdmajora.fulgor;

import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.api.SectionLightInfo;
import com.bdmajora.fulgor.async.AsyncLightStats;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.WorldLightManager;
import com.bdmajora.fulgor.gui.FulgorStatsCommand;
import com.bdmajora.fulgor.lighting.LightingEngine;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.WorldEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FulgorCoreTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(net.minecraft.launchwrapper.Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(FulgorConfig.class, "instance", null);
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
        Statics.set(Fulgor.class, "cachedBlockLightInfo", false);
    }

    @Test
    void theConfigReadsWritesAndClampsEveryKey() throws Exception {
        FulgorConfig config = FulgorConfig.get();
        assertSame(config, FulgorConfig.get());
        assertTrue(config.enabled);
        assertTrue(config.deferredLightUpdates);
        assertEquals(1 << 22, config.maxScheduledUpdates);
        assertEquals(1024, config.parallelMinPositions);
        assertTrue(config.parallelLightThreads() >= 1);
        config.parallelLightThreads = 6;
        assertEquals(6, config.parallelLightThreads());
        config.save();
        Path file = dir.resolve("config/impetus-fulgor.cfg");
        String written = Files.readString(file);
        assertTrue(written.contains("parallelLightThreads=6"));
        assertTrue(written.contains("asyncLightUpdates=true"));

        // Values out of range, unparseable or unrecognised fall back to the defaults
        Files.writeString(file, String.join("\n",
                "enabled=maybe",
                "maxScheduledUpdates=3",
                "parallelLightThreads=notanumber",
                "parallelMinChunks=9",
                "asyncLightUpdates=false",
                ""));
        Mixins.set(FulgorConfig.class, "instance", null);
        FulgorConfig reloaded = FulgorConfig.get();
        assertTrue(reloaded.enabled);
        assertEquals(1 << 22, reloaded.maxScheduledUpdates);
        assertEquals(0, reloaded.parallelLightThreads);
        assertEquals(9, reloaded.parallelMinChunks);
        assertFalse(reloaded.asyncLightUpdates);
    }

    @Test
    void compatibilityIsResolvedOnceAtStartup() {
        FulgorConfig.get().cacheBlockLightInfo = true;
        Fulgor.detectCompatibility();
        assertFalse(Fulgor.hasDynamicLights());
        assertFalse(Fulgor.hasFluidloggedApi());
        assertTrue(Fulgor.useCachedBlockLightInfo());
        // Either compatibility mod makes the per-block cache unusable
        Mc.forge("dynamiclights");
        Fulgor.detectCompatibility();
        assertTrue(Fulgor.hasDynamicLights());
        assertFalse(Fulgor.useCachedBlockLightInfo());
        Mc.forge("fluidlogged_api");
        Fulgor.detectCompatibility();
        assertTrue(Fulgor.hasFluidloggedApi());
        assertFalse(Fulgor.useCachedBlockLightInfo());
        Mc.forge();
        // The async engine only runs when both switches are on
        FulgorConfig.get().asyncLightUpdates = false;
        assertFalse(Fulgor.isAsync());
        FulgorConfig.get().asyncLightUpdates = true;
        assertTrue(Fulgor.isAsync());
        FulgorConfig.get().enabled = false;
        assertFalse(Fulgor.isAsync());
        FulgorConfig.get().enabled = true;
        Fulgor.detectCompatibility();
        Fulgor.onPostInit();
    }

    @Test
    void theStatisticsReportBothEngines() {
        FulgorConfig.get().asyncLightUpdates = false;
        Fulgor.recordScheduled();
        Fulgor.recordScheduled();
        Fulgor.recordDeduplicated();
        Fulgor.recordProcessed(4);
        List<String> deferred = Fulgor.statistics();
        assertEquals("Fulgor lighting statistics", deferred.get(0));
        assertTrue(deferred.get(1).contains("Updates scheduled:"));
        assertTrue(deferred.get(2).contains("50%"));
        assertTrue(Fulgor.debugOverlayLine().contains("deduped"));

        FulgorConfig.get().asyncLightUpdates = true;
        AsyncLightStats.BLOCK_CHANGES.add(1500);
        AsyncLightStats.INITIAL_LIGHTS.add(2_000_000);
        List<String> async = Fulgor.statistics();
        assertEquals("Fulgor lighting statistics (async engine)", async.get(0));
        assertTrue(async.stream().anyMatch(line -> line.contains("Block changes queued:  1500")));
        String overlay = Fulgor.debugOverlayLine();
        assertTrue(overlay.contains("1.5k"));
        assertTrue(overlay.contains("2.0M"));

        // The command prints the same report in two colours
        FulgorStatsCommand command = new FulgorStatsCommand();
        assertEquals("fulgor", command.getName());
        assertTrue(command.getUsage(null).startsWith("/fulgor"));
        assertEquals(0, command.getRequiredPermissionLevel());
        ICommandSender sender = mock(ICommandSender.class);
        command.execute(null, sender, new String[0]);
        ArgumentCaptor<ITextComponent> messages = ArgumentCaptor.forClass(ITextComponent.class);
        Mockito.verify(sender, Mockito.atLeast(2)).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().get(0).getUnformattedText().startsWith("§b"));
        assertTrue(messages.getAllValues().get(1).getUnformattedText().startsWith("§7"));
    }

    @Test
    void theClientTickDrivesWhicheverEngineIsInUse() {
        World deferred = Mc.mock(World.class, LightingEngineProvider.class);
        LightingEngine engine = mock(LightingEngine.class);
        when(((LightingEngineProvider) deferred).fulgor$getLightingEngine()).thenReturn(engine);
        Fulgor.processClientLightUpdates(deferred);
        Mockito.verify(engine).processLightUpdates();

        World async = Mc.mock(World.class, AsyncLitWorld.class);
        WorldLightManager manager = mock(WorldLightManager.class);
        when(((AsyncLitWorld) async).fulgor$getLightManager()).thenReturn(manager);
        Fulgor.processClientLightUpdates(async);
        Mockito.verify(manager).processClientUpdates();
        // A world with no manager yet, and a world neither engine owns, are both no-ops
        when(((AsyncLitWorld) async).fulgor$getLightManager()).thenReturn(null);
        Fulgor.processClientLightUpdates(async);
        Fulgor.processClientLightUpdates(mock(World.class));

        // Unloading a world takes its lane threads with it
        FulgorEvents events = new FulgorEvents();
        events.onWorldUnload(new WorldEvent.Unload(async));
        Mockito.verify((AsyncLitWorld) async).fulgor$shutdownLightManager();
        events.onWorldUnload(new WorldEvent.Unload(mock(World.class)));
    }

    @Test
    void theRenderBridgeDegradesWithTheConfig() {
        World deferred = Mc.mock(World.class, LightingEngineProvider.class);
        LightingEngine engine = mock(LightingEngine.class);
        when(((LightingEngineProvider) deferred).fulgor$getLightingEngine()).thenReturn(engine);
        FulgorRenderBridge.flushPendingLightUpdates(deferred);
        Mockito.verify(engine).processLightUpdates();
        FulgorRenderBridge.flushPendingLightUpdates(mock(World.class));

        assertTrue(FulgorRenderBridge.fixRenderLighting());
        FulgorConfig.get().fixRenderLighting = false;
        assertFalse(FulgorRenderBridge.fixRenderLighting());
        FulgorConfig.get().fixRenderLighting = true;
        FulgorConfig.get().enabled = false;
        assertFalse(FulgorRenderBridge.fixRenderLighting());
        FulgorConfig.get().enabled = true;

        // Emptiness is asked of the section's own narrow answer when Fulgor has widened the vanilla one
        ExtendedBlockStorage plain = new ExtendedBlockStorage(0, true);
        assertTrue(FulgorRenderBridge.isEmptyOfBlocks(plain));
        ExtendedBlockStorage widened = Mc.mock(ExtendedBlockStorage.class, SectionLightInfo.class);
        when(((SectionLightInfo) widened).fulgor$hasNoBlocks()).thenReturn(false);
        assertFalse(FulgorRenderBridge.isEmptyOfBlocks(widened));
    }
}
