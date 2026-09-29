package com.bdmajora.coarctatio.mixin.client;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ClientMixinsTest {
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

    @Test
    void theCreativeSearchIndexIsBuiltOnTheFirstSearch() {
        // Mockito cannot instrument SearchTree, so the mixin gets a generated subclass whose shadow is a no-op
        SearchTreeMixin<String> tree = Mixins.concrete(SearchTreeMixin.class);
        // A re-parented mixin's field initialisers do not apply, so the starting state is set here
        Mixins.set(tree, "coarctatio$stale", true);
        // Startup and every reload ask for a rebuild; while nothing has searched, they are swallowed
        var eager = Mixins.ci();
        Mixins.call(tree, "coarctatio$deferRecalculate", eager);
        assertTrue(eager.isCancelled());

        // The first search clears the flag and asks for the build itself
        Mixins.call(tree, "coarctatio$buildOnDemand", "stone", Mixins.cir(List.of()));
        assertFalse(Mixins.<Boolean>get(tree, "coarctatio$stale"));

        // Once built, a rebuild goes through as usual
        var built = Mixins.ci();
        Mixins.call(tree, "coarctatio$deferRecalculate", built);
        assertFalse(built.isCancelled());
        // A second search does not rebuild
        Mixins.call(tree, "coarctatio$buildOnDemand", "stone", Mixins.cir(List.of()));

        // Adding an item re-arms the deferral
        Mixins.call(tree, "coarctatio$invalidate", "item", Mixins.ci());
        assertTrue(Mixins.<Boolean>get(tree, "coarctatio$stale"));
    }

    @Test
    void theSoundRegistryGetsACompactMapAndItsDebugWalksAreSkipped() {
        SoundRegistryMixin registry = Mixins.instance(SoundRegistryMixin.class);
        CallbackInfoReturnable<Map<ResourceLocation, SoundEventAccessor>> cir = Mixins.cir(new java.util.HashMap<>());
        Mixins.call(registry, "coarctatio$compactSoundMap", cir);
        assertTrue(cir.isCancelled());
        assertInstanceOf(Object2ObjectOpenHashMap.class, cir.getReturnValue());
        // clearMap reads the field back, so the two must be the same instance
        assertSame(cir.getReturnValue(), Mixins.get(registry, "soundRegistry"));

        SoundHandlerMixin handler = Mixins.instance(SoundHandlerMixin.class);
        Set<ResourceLocation> keys = Mixins.call(handler, "coarctatio$skipDebugWalks", mock(SoundRegistry.class));
        assertTrue(keys.isEmpty());
    }

    @Test
    void theBakeScopedPoolsBracketTheModelReload() {
        ModelManagerMixin manager = Mixins.instance(ModelManagerMixin.class);
        IResourceManager resources = mock(IResourceManager.class);

        Mixins.call(manager, "coarctatio$openPools", resources, Mixins.ci());
        int[] quad = {1, 2, 3};
        assertSame(quad, ModelCaches.QUADS.deduplicate(quad));
        assertSame(quad, ModelCaches.QUADS.deduplicate(new int[] {1, 2, 3}));

        Mixins.call(manager, "coarctatio$closePools", resources, Mixins.ci());
        // Closed, so the next pack's geometry does not keep this one's alive
        assertSame(quad, ModelCaches.QUADS.deduplicate(quad));
    }
}
