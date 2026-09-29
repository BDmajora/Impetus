package com.bdmajora.impetus.core;

import com.bdmajora.impetus.booter.BooterBootstrap;
import com.bdmajora.impetus.booter.BooterCore;
import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformerHandle;
import com.bdmajora.testing.LaunchEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CoreTest {
    // A class with one field of the given type
    private static byte[] classWithField(String name, String fieldDescriptor) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "field", fieldDescriptor, null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void lwjglReferencesInImpetusClassesMoveToLwjgl3() {
        ImpetusLWJGLRelocationTransformer transformer = new ImpetusLWJGLRelocationTransformer();
        assertNull(transformer.transform("a", "com.bdmajora.impetus.A", null));
        byte[] foreign = classWithField("net/minecraft/A", "Lorg/lwjgl/opengl/GL11;");
        assertSame(foreign, transformer.transform("a", "net.minecraft.A", foreign));
        // No LWJGL reference means ASM is never involved
        byte[] plain = classWithField("com/bdmajora/impetus/Plain", "Ljava/lang/String;");
        assertSame(plain, transformer.transform("a", "com.bdmajora.impetus.Plain", plain));
        byte[] lwjgl = classWithField("com/bdmajora/impetus/Drawing", "Lorg/lwjgl/opengl/GL11;");
        byte[] moved = transformer.transform("a", "com.bdmajora.impetus.Drawing", lwjgl);
        ClassNode node = new ClassNode();
        new ClassReader(moved).accept(node, 0);
        assertEquals("Lorg/lwjgl3/opengl/GL11;", node.fields.get(0).desc);
        // Bytes that only look like a class are handed back untouched
        byte[] garbage = "not a class but mentions org/lwjgl/ anyway".getBytes(StandardCharsets.UTF_8);
        assertSame(garbage, transformer.transform("a", "com.bdmajora.impetus.Garbage", garbage));
        // The last possible window is still scanned
        byte[] tail = "org/lwjgl/".getBytes(StandardCharsets.UTF_8);
        assertSame(tail, transformer.transform("a", "com.bdmajora.impetus.Tail", tail));
    }

    @Test
    void theRegistrarQueuesEveryConfigAndSuppressesSupersededLighting(@TempDir Path home, @TempDir Path classpath) throws IOException {
        Files.writeString(classpath.resolve("mixins.phosphor.json"), "{}");
        // A launch with a Mixin service in place, whose loader sees Phosphor's config but not Alfheim's
        LaunchEnvironment.install(home, classpath.toUri().toURL());
        try (MockedStatic<Mixins> mixins = Mockito.mockStatic(Mixins.class);
             MockedStatic<Config> configs = Mockito.mockStatic(Config.class)) {
            ImpetusMixinRegistrar.apply();
            for (String config : List.of("mixins.impetus.json", "mixins.umbra.json", "mixins.coarctatio.json", "mixins.fulgor.json",
                    "mixins.equilibrium.json", "mixins.extras.json", "mixins.dynamiclights.json")) {
                mixins.verify(() -> Mixins.addConfiguration(config));
            }
            configs.verify(() -> Config.blacklist("mixins.phosphor.json"));
            configs.verify(() -> Config.blacklist("mixins.alfheim.json"), never());
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theLoadingPluginBringsUpMixinOnlyWhenItOwnsIt() {
        try (MockedStatic<BooterBootstrap> bootstrap = Mockito.mockStatic(BooterBootstrap.class);
             MockedStatic<BooterCore> core = Mockito.mockStatic(BooterCore.class);
             MockedStatic<ImpetusMixinRegistrar> registrar = Mockito.mockStatic(ImpetusMixinRegistrar.class)) {
            bootstrap.when(BooterBootstrap::initialize).thenReturn(BooterBootstrap.MIXIN_OWNED);
            bootstrap.when(BooterBootstrap::state).thenReturn(BooterBootstrap.MIXIN_OWNED);
            ImpetusLoadingPlugin plugin = new ImpetusLoadingPlugin();
            core.verify(BooterCore::initialize);
            List<Object> coremods = new ArrayList<>();
            plugin.injectData(Map.of("coremodList", coremods));
            core.verify(() -> BooterCore.injectData(coremods));
            registrar.verify(ImpetusMixinRegistrar::apply);
            assertThrows(RuntimeException.class, () -> plugin.injectData(Map.of("coremodList", "not a list")));

            // Under another booter Impetus only registers its configs
            bootstrap.when(BooterBootstrap::initialize).thenReturn(BooterBootstrap.DEFERRED);
            bootstrap.when(BooterBootstrap::state).thenReturn(BooterBootstrap.DEFERRED);
            ImpetusLoadingPlugin deferred = new ImpetusLoadingPlugin();
            deferred.injectData(Map.of());
            core.verify(BooterCore::initialize);
            registrar.verify(ImpetusMixinRegistrar::apply, Mockito.times(2));

            assertEquals(0, plugin.getASMTransformerClass().length);
            assertNull(plugin.getModContainerClass());
            assertNull(plugin.getSetupClass());
            assertNull(plugin.getAccessTransformerClass());
        }
    }

    @Test
    void lwjgl3ifyIsToldToLeaveImpetusAlone() {
        new ImpetusLwjgl3ifyCompat();
        RfbClassTransformerHandle other = mock(RfbClassTransformerHandle.class);
        when(other.id()).thenReturn("mixin:mixin");
        RfbClassTransformerHandle redirect = mock(RfbClassTransformerHandle.class);
        when(redirect.id()).thenReturn("lwjgl3ify:redirect");
        List<String> exclusions = new ArrayList<>();
        when(redirect.exclusions()).thenReturn(exclusions);
        try (MockedStatic<SharedConfig> shared = Mockito.mockStatic(SharedConfig.class)) {
            shared.when(SharedConfig::getRfbTransformers).thenReturn(List.of(other, redirect));
            ImpetusLwjgl3ifyCompat.apply();
            assertEquals(List.of("com.bdmajora.impetus.engine", "com.bdmajora.impetus"), exclusions);
            // Without lwjgl3ify nothing is touched
            shared.when(SharedConfig::getRfbTransformers).thenReturn(List.of(other));
            ImpetusLwjgl3ifyCompat.apply();
            assertEquals(2, exclusions.size());
        }
    }
}
