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
        assertTrue(applies(plugin, "state.BlockStateContainerMixin"));
        assertTrue(applies(plugin, "core.LockCodeMixin"));
        assertTrue(applies(plugin, "client.texture.StitcherMixin"));
        assertTrue(applies(plugin, "forge.OreDictionaryMixin"));
        assertTrue(applies(plugin, "client.model.part.BlockPartMixin"));
        assertTrue(applies(plugin, "world.TemplateManagerMixin"));
        assertTrue(applies(plugin, "forge.JarDiscovererMixin"));
        assertTrue(applies(plugin, "forge.ModCandidateMixin"));
        assertTrue(applies(plugin, "client.model.bake.ItemLayerModelQuadMixin"));
        // Off by default
        assertFalse(applies(plugin, "events.TickEventMixin"));
        assertFalse(applies(plugin, "client.model.ModelLoaderRegistryMissingMixin"));

        // The resource manager mixin carries two features, so either one keeps it
        config.stacklessResourceExceptions = false;
        config.resourceExistenceCache = true;
        assertTrue(applies(plugin, "client.resources.SimpleReloadableResourceManagerMixin"));
        config.resourceExistenceCache = false;
        assertFalse(applies(plugin, "client.resources.SimpleReloadableResourceManagerMixin"));

        // The model manager drives the pool lifecycle, which dynamic models take over
        config.poolQuadVertexData = true;
        config.dynamicModels = false;
        assertTrue(applies(plugin, "client.ModelManagerMixin"));
        config.dynamicModels = true;
        assertFalse(applies(plugin, "client.ModelManagerMixin"));
        // The dynamic-model mixins and the item prebake hang off that same switch
        assertTrue(applies(plugin, "client.model.dynamic.DynamicModelsSomethingMixin"));
        assertTrue(applies(plugin, "client.model.dynamic.RenderItemPrebakeMixin"));
        config.dynamicModelsPrebakeItems = false;
        assertFalse(applies(plugin, "client.model.dynamic.RenderItemPrebakeMixin"));
        config.resourceExistenceCache = true;
        assertFalse(applies(plugin, "client.model.dynamic.FileResourcePackAccessor"));
        // The model prefetch feeds the eager loader pass, which the dynamic reload replaces
        assertFalse(applies(plugin, "client.model.ModelLoaderPrefetchMixin"));
        config.dynamicModels = false;
        assertTrue(applies(plugin, "client.model.ModelLoaderPrefetchMixin"));
        assertTrue(applies(plugin, "client.model.ModelBakeryPrefetchMixin"));
        assertTrue(applies(plugin, "client.model.ModelBlockAnimationPrefetchMixin"));
        // It lists packs through the same accessors as the dynamic texture scan, so either feature keeps them
        assertTrue(applies(plugin, "client.model.dynamic.SimpleReloadableResourceManagerAccessor"));
        assertTrue(applies(plugin, "client.model.dynamic.LegacyV2AdapterAccessor"));
        config.resourceExistenceCache = false;
        assertTrue(applies(plugin, "client.model.dynamic.AbstractResourcePackAccessor"));
        config.parallelModelLoad = false;
        assertFalse(applies(plugin, "client.model.ModelBakeryPrefetchMixin"));
        assertFalse(applies(plugin, "client.model.dynamic.FallbackResourceManagerAccessor"));
        assertFalse(applies(plugin, "client.model.dynamic.FileResourcePackAccessor"));

        // Anything the switch table does not know is applied, with a warning
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

            // Defaults: the loader mixins are on, event recycling is off; no tracker means nothing can report a target loaded
            when(service.getClassTracker()).thenReturn(null);
            List<String> defaults = plugin.getMixins();
            assertEquals(List.of("forge.ASMDataMixin", "forge.ModCandidateMixin", "forge.JarDiscovererMixin"), defaults);

            config.recycleEvents = true;
            when(service.getClassTracker()).thenReturn(tracker);
            List<String> all = plugin.getMixins();
            assertTrue(all.contains("events.TickEventMixin"));
            assertTrue(all.contains("events.ForgeEventFactoryMixin"));
            assertTrue(all.contains("forge.ModCandidateMixin"));

            // A target another coremod already pulled in would fail the whole config, so only its own feature backs out
            when(tracker.isClassLoaded("net.minecraftforge.fml.common.discovery.ModCandidate")).thenReturn(true);
            when(tracker.isClassLoaded("net.minecraftforge.event.ForgeEventFactory")).thenReturn(true);
            assertEquals(List.of("forge.ASMDataMixin", "forge.JarDiscovererMixin"), plugin.getMixins());

            // With every guarded feature off the json's list stands alone
            config.recycleEvents = false;
            config.internLoaderStrings = false;
            config.modScanCache = false;
            assertNull(plugin.getMixins());
        }
    }
}
