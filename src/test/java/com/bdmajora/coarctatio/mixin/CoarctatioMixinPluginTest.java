package com.bdmajora.coarctatio.mixin;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CoarctatioMixinPluginTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    private static boolean applies(CoarctatioMixinPlugin plugin, String name) {
        return plugin.shouldApplyMixin("net.minecraft.world.World", "com.bdmajora.coarctatio.mixin." + name);
    }

    @Test
    void everyMixinIsGatedOnItsOwnSwitch() {
        CoarctatioMixinPlugin plugin = new CoarctatioMixinPlugin();
        plugin.onLoad("com.bdmajora.coarctatio.mixin");
        CoarctatioConfig config = Mixins.get(plugin, "config");
        assertSame(CoarctatioConfig.get(), config);

        assertTrue(applies(plugin, "util.ResourceLocationMixin"));
        config.deduplicateResourceLocations = false;
        assertFalse(applies(plugin, "util.ResourceLocationMixin"));

        assertTrue(applies(plugin, "nbt.NBTTagCompoundMixin"));
        assertTrue(applies(plugin, "nbt.NBTTagListPoolMixin"));
        assertTrue(applies(plugin, "core.LockCodeMixin"));
        assertTrue(applies(plugin, "item.ItemStackCapabilityMixin"));
        assertTrue(applies(plugin, "world.AnvilChunkLoaderMixin"));
        assertTrue(applies(plugin, "world.ChunkMixin"));
        assertTrue(applies(plugin, "client.SoundHandlerMixin"));
        assertTrue(applies(plugin, "forge.ModCandidateMixin"));
        // The part pools and the reload hook that opens them share one switch
        assertTrue(applies(plugin, "client.model.part.BlockPartMixin"));
        assertTrue(applies(plugin, "client.ModelManagerMixin"));
        config.canonicalizeModelParts = false;
        assertFalse(applies(plugin, "client.ModelManagerMixin"));
        assertFalse(applies(plugin, "client.model.part.ModelBlockMixin"));
        // Off by default
        assertFalse(applies(plugin, "events.TickEventMixin"));
        assertFalse(applies(plugin, "client.model.ModelLoaderRegistryMissingMixin"));

        // Checks an unknown mixin.
        assertTrue(applies(plugin, "something.UnknownMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.World", "com.example.Foreign"));
    }

    @Test
    void guardedMixinsAreListedWhenWantedAndBackOutAloneWhenTheirTargetIsAlreadyLoaded() {
        CoarctatioMixinPlugin plugin = new CoarctatioMixinPlugin();
        plugin.onLoad("com.bdmajora.coarctatio.mixin");
        CoarctatioConfig config = Mixins.get(plugin, "config");
        IMixinService service = mock(IMixinService.class);
        IClassTracker tracker = mock(IClassTracker.class);
        try (MockedStatic<MixinService> services = mockStatic(MixinService.class)) {
            services.when(MixinService::getService).thenReturn(service);

            // Checks the defaults.
            when(service.getClassTracker()).thenReturn(null);
            List<String> defaults = plugin.getMixins();
            assertEquals(List.of("forge.ASMDataMixin", "forge.ModCandidateMixin"), defaults);

            config.recycleEvents = true;
            when(service.getClassTracker()).thenReturn(tracker);
            List<String> all = plugin.getMixins();
            assertTrue(all.contains("events.TickEventMixin"));
            assertTrue(all.contains("events.ForgeEventFactoryMixin"));
            assertTrue(all.contains("forge.ModCandidateMixin"));

            // Checks a target loaded early.
            when(tracker.isClassLoaded("net.minecraftforge.fml.common.discovery.ModCandidate")).thenReturn(true);
            when(tracker.isClassLoaded("net.minecraftforge.event.ForgeEventFactory")).thenReturn(true);
            assertEquals(List.of("forge.ASMDataMixin"), plugin.getMixins());

            // With every guarded feature off the json's list stands alone
            config.recycleEvents = false;
            config.internLoaderStrings = false;
            assertNull(plugin.getMixins());
        }
    }
}
