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

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        // Anything the switch table does not know is applied, with a warning
        assertTrue(applies(plugin, "something.UnknownMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.World", "com.example.Foreign"));
    }

    @Test
    void theRecycledEventMixinsAreOnlyListedWhenTheyAreWanted() {
        CoarctatioMixinPlugin plugin = new CoarctatioMixinPlugin();
        plugin.onLoad("com.bdmajora.coarctatio.mixin");
        CoarctatioConfig config = Mixins.get(plugin, "config");

        // Off by default, and the list is then left to the json
        assertFalse(config.recycleEvents);
        assertNull(plugin.getMixins());

        config.recycleEvents = true;
        org.spongepowered.asm.service.IMixinService service =
                org.mockito.Mockito.mock(org.spongepowered.asm.service.IMixinService.class);
        org.spongepowered.asm.service.IClassTracker tracker =
                org.mockito.Mockito.mock(org.spongepowered.asm.service.IClassTracker.class);
        try (org.mockito.MockedStatic<org.spongepowered.asm.service.MixinService> services =
                     org.mockito.Mockito.mockStatic(org.spongepowered.asm.service.MixinService.class)) {
            services.when(org.spongepowered.asm.service.MixinService::getService).thenReturn(service);

            // No tracker at all means nothing can say a target was already loaded
            org.mockito.Mockito.when(service.getClassTracker()).thenReturn(null);
            assertTrue(plugin.getMixins().contains("events.TickEventMixin"));

            org.mockito.Mockito.when(service.getClassTracker()).thenReturn(tracker);
            List<String> mixins = plugin.getMixins();
            assertTrue(mixins.contains("events.TickEventMixin"));
            assertTrue(mixins.contains("events.ForgeEventFactoryMixin"));

            // A target another coremod already pulled in would fail the whole config, so the feature backs out
            org.mockito.Mockito.when(tracker.isClassLoaded("net.minecraftforge.event.ForgeEventFactory")).thenReturn(true);
            assertNull(plugin.getMixins());
        }
    }
}
