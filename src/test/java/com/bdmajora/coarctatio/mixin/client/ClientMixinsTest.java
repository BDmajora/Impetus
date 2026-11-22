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
    void theSoundRegistryGetsACompactMapAndItsDebugWalksAreSkipped() {
        SoundRegistryMixin registry = Mixins.instance(SoundRegistryMixin.class);
        CallbackInfoReturnable<Map<ResourceLocation, SoundEventAccessor>> cir = Mixins.cir(new java.util.HashMap<>());
        Mixins.call(registry, "coarctatio$compactSoundMap", cir);
        assertTrue(cir.isCancelled());
        assertInstanceOf(Object2ObjectOpenHashMap.class, cir.getReturnValue());
        // Checks the same instance.
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
        String name = new String("minecraft:blocks/stone");
        assertSame(name, ModelCaches.MODEL_TEXTURES.deduplicate(name));
        assertSame(name, ModelCaches.MODEL_TEXTURES.deduplicate(new String("minecraft:blocks/stone")));

        Mixins.call(manager, "coarctatio$closePools", resources, Mixins.ci());
        // Checks the pool is closed.
        String other = new String("minecraft:blocks/dirt");
        assertSame(other, ModelCaches.MODEL_TEXTURES.deduplicate(other));
    }
}
