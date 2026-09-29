package com.bdmajora.impetus.booter;

import com.bdmajora.impetus.booter.fix.forge.EagerlyLoadEventClassTransformer;
import com.bdmajora.impetus.booter.fix.mixinextras.MixinExtrasFixer;
import com.bdmajora.impetus.booter.mixin.SimpleMixinPlugin;
import com.bdmajora.impetus.booter.service.ClassLoadTracer;
import com.bdmajora.impetus.booter.service.CoremodsRescuer;
import com.bdmajora.impetus.booter.service.MixinBooterService;
import com.bdmajora.impetus.booter.service.ModDiscoverer;
import com.bdmajora.impetus.booter.util.Environment;
import com.bdmajora.impetus.booter.util.PropertiesConfig;
import com.bdmajora.impetus.booter.util.Srg2NotchRemapper;
import com.bdmajora.testing.LaunchEnvironment;
import com.bdmajora.testing.Statics;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BooterTest {
    // Where MixinBooterConfig reads its file, relative to the working directory like the game's
    private static final File BOOTER_CONFIG = new File("config", Tags.MOD_ID + ".cfg");

    @TempDir
    Path home;

    private LaunchClassLoader loader;
    private File previousFmlHome;

    // FML's wrapper around a coremod instance
    public static final class Wrapper {
        public Object coreModInstance;

        Wrapper(Object coreModInstance) {
            this.coreModInstance = coreModInstance;
        }
    }

    // A wrapper of some other shape, which the reflective read cannot handle
    public static final class OtherWrapper {
        public Object coreModInstance = new Object();
    }

    // A coremod that both hijacks another mod's config and queues its own, skipping one
    static final class Hijacker implements IMixinConfigHijacker, IEarlyMixinLoader {
        final List<String> queued = new ArrayList<>();

        @Override
        public Set<String> getHijackedMixinConfigs() {
            return Set.of("hijacked.mixins.json");
        }

        @Override
        public List<String> getMixinConfigs() {
            return List.of("early.mixins.json", "skipped.mixins.json");
        }

        @Override
        public boolean shouldMixinConfigQueue(String mixinConfig) {
            return !mixinConfig.startsWith("skipped");
        }

        @Override
        public void onMixinConfigQueued(String mixinConfig) {
            this.queued.add(mixinConfig);
        }
    }

    // One relying on every default
    static final class DefaultLoader implements IEarlyMixinLoader {
        @Override
        public List<String> getMixinConfigs() {
            return List.of("default.mixins.json");
        }
    }

    static final class FailingLoader implements IEarlyMixinLoader {
        @Override
        public List<String> getMixinConfigs() {
            throw new IllegalStateException("broken loader");
        }
    }

    @BeforeEach
    void boot() {
        loader = LaunchEnvironment.install(home);
        previousFmlHome = Statics.get(FMLInjectionData.class, "minecraftHome");
        Statics.set(FMLInjectionData.class, "minecraftHome", home.toFile());
        Statics.set(BooterBootstrap.class, "state", -1);
        resetDiscoverer();
    }

    @AfterEach
    void shutDown() throws IOException {
        resetDiscoverer();
        Statics.set(BooterBootstrap.class, "state", -1);
        Statics.set(FMLInjectionData.class, "minecraftHome", previousFmlHome);
        Files.deleteIfExists(BOOTER_CONFIG.toPath());
        LaunchEnvironment.restore();
    }

    private static void resetDiscoverer() {
        Statics.set(ModDiscoverer.class, "discovered", false);
        for (String name : List.of("modIdToFiles", "fileToModIds")) {
            Statics.<Multimap<?, ?>>get(ModDiscoverer.class, name).clear();
        }
    }

    @Test
    void theContextAndLoaderDefaultsAnswerForCallers() {
        Context context = new Context("a.mixins.json", Set.of("alpha"));
        assertEquals(Context.ModLoader.FORGE, context.modLoader());
        assertFalse(context.inDev());
        assertEquals("a.mixins.json", context.mixinConfig());
        assertTrue(context.isModPresent("alpha"));
        assertFalse(context.isModPresent("beta"));
        DefaultLoader loader = new DefaultLoader();
        assertTrue(loader.shouldMixinConfigQueue(context));
        loader.onMixinConfigQueued(context);
        Hijacker hijacker = new Hijacker();
        assertEquals(Set.of("hijacked.mixins.json"), hijacker.getHijackedMixinConfigs(context));
        // Environment facts come from the launch: the primary tweaker's side, Forge's Minecraft version, and no GradleStart here
        assertNotNull(new Environment());
        assertEquals("CLIENT", Environment.side());
        assertEquals("1.12.2", Environment.minecraftVersion());
        assertFalse(Environment.inDev());
    }

    @Test
    void theBootstrapStandsDownForAnyOtherMixin() throws Exception {
        assertEquals(-1, BooterBootstrap.state());
        // Another service chosen before us wins, and the answer is cached
        System.setProperty("mixin.service", "other.MixinService");
        assertEquals(BooterBootstrap.DEFERRED, BooterBootstrap.initialize());
        System.clearProperty("mixin.service");
        assertEquals(BooterBootstrap.DEFERRED, BooterBootstrap.initialize());
        assertEquals(BooterBootstrap.DEFERRED, BooterBootstrap.state());

        // A real MixinBooter on the launch classpath, found as a resource so it is never initialised by looking
        Statics.set(BooterBootstrap.class, "state", -1);
        System.setProperty("mixin.service", MixinBooterService.class.getName());
        Path foreign = home.resolve("foreign");
        Files.createDirectories(foreign.resolve("zone/rong/mixinbooter"));
        Files.write(foreign.resolve("zone/rong/mixinbooter/MixinBooterPlugin.class"), new byte[0]);
        loader.addURL(foreign.toUri().toURL());
        assertEquals(BooterBootstrap.DEFERRED, BooterBootstrap.initialize());

        // Mixin already present without a booter, as in the development runtime
        Statics.set(BooterBootstrap.class, "state", -1);
        System.setProperty("mixin.service", "");
        LaunchEnvironment.restore();
        loader = LaunchEnvironment.install(home);
        assertEquals(BooterBootstrap.DEFERRED, BooterBootstrap.initialize());
        assertNotNull(Statics.call(BooterBootstrap.class, "resource", "org/spongepowered/asm/launch/MixinBootstrap.class"));

        // The steps of supplying Mixin ourselves: the bundled libraries are only present in a built jar
        assertFalse((boolean) Statics.call(BooterBootstrap.class, "extractAndAttachLibs"));
        URL self = Statics.call(BooterBootstrap.class, "selfJarUrl");
        assertNotNull(self);
        Statics.call(BooterBootstrap.class, "injectSelfIntoAppClassLoader");
        Statics.call(BooterBootstrap.class, "addToAppClassLoader", self);
        assertEquals(home.toFile(), Statics.call(BooterBootstrap.class, "gameDir"));
        Launch.minecraftHome = null;
        assertEquals(new File("."), Statics.call(BooterBootstrap.class, "gameDir"));
        File copied = home.resolve("copied.jar").toFile();
        Statics.call(BooterBootstrap.class, "copy", new ByteArrayInputStream(new byte[] {1, 2}), copied);
        Statics.call(BooterBootstrap.class, "copy", new ByteArrayInputStream(new byte[] {3}), copied);
        assertArrayEquals(new byte[] {3}, Files.readAllBytes(copied.toPath()));
        Statics.call(BooterBootstrap.class, "closeQuietly", (Object) null);
        Statics.call(BooterBootstrap.class, "closeQuietly", (Closeable) () -> {
            throw new IOException("already closed");
        });
        Statics.call(BooterBootstrap.class, "log", "a message");
    }

    @Test
    void theUsersConfigTunesMixinBeforeItBoots() throws IOException {
        // Defaults: nothing blacklisted, the audit trail on, no debug switches
        MixinBooterConfig.load();
        assertTrue(BOOTER_CONFIG.isFile());
        assertNull(System.getProperty(MixinBooterService.AUDIT_PROPERTY));
        assertNull(System.getProperty("mixin.debug.verbose"));

        Files.writeString(BOOTER_CONFIG.toPath(), String.join("\n",
                "general {",
                "    B:auditTrail=false",
                "    S:blacklistedConfigs <",
                "        broken.mixins.json",
                "     >",
                "}",
                "debug {",
                "    B:checkInterfaces=true",
                "    B:export=false",
                "    B:verbose=true",
                "    S:watchedClasses <",
                "        demo.Watched",
                "     >",
                "}"), StandardCharsets.UTF_8);
        // A switch already given on the command line is left as it was, and watched classes join any given there
        System.setProperty("mixin.checks.interfaces", "false");
        System.setProperty(ClassLoadTracer.WATCH_PROPERTY, " existing.One ");
        MixinBooterConfig.load();
        assertEquals("false", System.getProperty(MixinBooterService.AUDIT_PROPERTY));
        assertEquals("true", System.getProperty("mixin.debug.verbose"));
        assertNull(System.getProperty("mixin.debug.export"));
        assertEquals("false", System.getProperty("mixin.checks.interfaces"));
        assertEquals("existing.One,demo.Watched", System.getProperty(ClassLoadTracer.WATCH_PROPERTY));
    }

    @Test
    void theCoreBootsMixinAndQueuesTheEarlyLoaders() {
        List<?> tweaks = (List<?>) Launch.blackboard.get("TweakClasses");
        try (MockedStatic<MixinBootstrap> bootstrap = Mockito.mockStatic(MixinBootstrap.class);
             MockedStatic<MixinExtrasBootstrap> extras = Mockito.mockStatic(MixinExtrasBootstrap.class);
             MockedStatic<org.spongepowered.asm.mixin.Mixins> configs = Mockito.mockStatic(org.spongepowered.asm.mixin.Mixins.class)) {
            BooterCore.initialize();
            bootstrap.verify(MixinBootstrap::init);
            extras.verify(MixinExtrasBootstrap::init);
            assertEquals(MixinBooterService.class.getName(), System.getProperty("mixin.service"));
            assertEquals("com.bdmajora.impetus.booter.service.MixinServiceBootstrap", System.getProperty("mixin.bootstrapService"));
            // The rescuer runs before Forge's own tweakers, and class loads can be traced
            assertEquals(CoremodsRescuer.class.getName(), tweaks.get(0));
            assertTrue(loader.getTransformers().stream().anyMatch(transformer -> transformer instanceof ClassLoadTracer));

            // Hijackers blacklist first, then each early loader's configs are queued unless it declines one
            MixinPlatformManager platform = mock(MixinPlatformManager.class);
            bootstrap.when(MixinBootstrap::getPlatform).thenReturn(platform);
            Hijacker hijacker = new Hijacker();
            BooterCore.injectData(List.of(new Wrapper(hijacker), new Wrapper(new DefaultLoader()),
                    new Wrapper(new FailingLoader()), new Wrapper("not a plugin"), new OtherWrapper()));
            configs.verify(() -> org.spongepowered.asm.mixin.Mixins.addConfiguration("early.mixins.json"));
            configs.verify(() -> org.spongepowered.asm.mixin.Mixins.addConfiguration("default.mixins.json"));
            configs.verify(() -> org.spongepowered.asm.mixin.Mixins.addConfiguration("skipped.mixins.json"), never());
            assertEquals(List.of("early.mixins.json"), hijacker.queued);
            verify(platform).inject();
        }
    }

    @Test
    void theSharedPropertiesFileParsesLeniently() throws IOException {
        Logger logger = mock(Logger.class);
        PropertiesConfig config = new PropertiesConfig(logger, "switches.cfg", "Switches");
        // Nothing to write before a load has resolved the file
        config.save(Map.of("a", "b"));
        Properties defaults = config.load();
        assertTrue(defaults.isEmpty());
        Map<String, String> values = PropertiesConfig.values();
        values.put("flag", "true");
        values.put("fuzzy", "maybe");
        values.put("count", "5");
        values.put("huge", "99");
        values.put("word", "five");
        values.put("items", " a, ,b ,");
        config.save(values);
        assertEquals(home.resolve("config/switches.cfg").toFile(), PropertiesConfig.configFile(logger, "switches.cfg"));
        config.load();
        assertTrue(config.bool("flag", false));
        assertTrue(config.bool("fuzzy", true));
        assertFalse(config.bool("absent", false));
        assertEquals(5, config.integer("count", 1, 0, 10));
        assertEquals(1, config.integer("huge", 1, 0, 10));
        assertEquals(1, config.integer("word", 1, 0, 10));
        assertEquals(1, config.integer("absent", 1, 0, 10));
        assertArrayEquals(new String[] {"a", "b"}, config.list("items", ""));
        assertArrayEquals(new String[] {"x", "y"}, config.list("absent", "x,y"));

        // An unreadable file falls back to defaults, and a config directory that cannot exist is reported
        Files.createDirectories(home.resolve("config/unreadable.cfg"));
        assertTrue(new PropertiesConfig(logger, "unreadable.cfg", "").load().isEmpty());
        Path blocked = home.resolve("blocked");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("config"), "a file where the directory belongs");
        Launch.minecraftHome = blocked.toFile();
        PropertiesConfig stranded = new PropertiesConfig(logger, "switches.cfg", "");
        stranded.load();
        stranded.save(values);
        verify(logger, Mockito.atLeastOnce()).warn(Mockito.anyString(), Mockito.any(Object.class));
        verify(logger).warn(Mockito.eq("Could not write {}"), Mockito.any(Object.class), Mockito.any(IOException.class));
    }

    @Test
    void theRemapperGoesThroughFmlsDeobfuscator() {
        Srg2NotchRemapper remapper = new Srg2NotchRemapper();
        assertEquals("net/minecraft/client/Minecraft", remapper.map("net/minecraft/client/Minecraft"));
        assertEquals("func_71410_x", remapper.mapMethodName("net/minecraft/client/Minecraft", "func_71410_x", "()V"));
        assertEquals("field_71474_y", remapper.mapFieldName("net/minecraft/client/Minecraft", "field_71474_y", "I"));
        assertEquals("net/minecraft/client/Minecraft", remapper.unmap("net/minecraft/client/Minecraft"));
    }

    @Test
    void theSharedMixinPluginAppliesEverything() {
        SimpleMixinPlugin plugin = new SimpleMixinPlugin() {
        };
        plugin.onLoad("com.example.mixin");
        assertNull(plugin.getRefMapperConfig());
        assertTrue(plugin.shouldApplyMixin("a.Target", "a.Mixin"));
        plugin.acceptTargets(Set.of(), Set.of());
        assertNull(plugin.getMixins());
        plugin.preApply("a.Target", new ClassNode(), "a.Mixin", null);
        plugin.postApply("a.Target", new ClassNode(), "a.Mixin", null);
    }

    // A class with a no-op method ahead of its constructor, and a method building an ASM Handle the way ASM 5.0 cannot
    private static byte[] handleUser() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "demo/Bus", null, "java/lang/Object", null);
        MethodVisitor post = writer.visitMethod(Opcodes.ACC_PUBLIC, "post", "()V", null, null);
        post.visitCode();
        post.visitTypeInsn(Opcodes.NEW, "org/objectweb/asm/Handle");
        post.visitInsn(Opcodes.DUP);
        post.visitInsn(Opcodes.ICONST_5);
        post.visitLdcInsn("owner");
        post.visitLdcInsn("name");
        post.visitLdcInsn("()V");
        post.visitInsn(Opcodes.ICONST_0);
        post.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/objectweb/asm/Handle", "<init>",
                "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V", false);
        post.visitInsn(Opcodes.POP);
        post.visitInsn(Opcodes.RETURN);
        post.visitMaxs(0, 0);
        post.visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static MethodNode method(byte[] bytes, String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theEventBusLoadsTheEventClassBeforeSubscribing() {
        EagerlyLoadEventClassTransformer transformer = new EagerlyLoadEventClassTransformer();
        byte[] bytes = handleUser();
        assertSame(bytes, transformer.transform("demo.Bus", "demo.Bus", bytes));
        byte[] eager = transformer.transform("$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer", "x", bytes);
        boolean constructsEvent = false;
        for (AbstractInsnNode instruction : method(eager, "<init>").instructions.toArray()) {
            constructsEvent |= instruction instanceof TypeInsnNode type && type.desc.equals("net/minecraftforge/fml/common/eventhandler/Event");
        }
        assertTrue(constructsEvent);
    }

    @Test
    void mixinExtrasHandlesAreBuiltTheWayOldAsmCan() {
        MixinExtrasFixer fixer = new MixinExtrasFixer();
        byte[] bytes = handleUser();
        assertSame(bytes, fixer.transform("demo.Bus", "demo.Bus", bytes));
        for (String name : List.of("com.llamalad7.mixinextras.utils.ASMUtils", "com.llamalad7.mixinextras.utils.OperationUtils",
                "com.llamalad7.mixinextras.utils.TypeUtils", "com.llamalad7.mixinextras.expression.impl.utils.ExpressionASMUtils")) {
            List<String> handles = new ArrayList<>();
            for (AbstractInsnNode instruction : method(fixer.transform(name, name, bytes), "post").instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("org/objectweb/asm/Handle")) {
                    handles.add(call.desc);
                }
            }
            // The interface flag is popped and the four-argument constructor called instead
            assertEquals(List.of("(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"), handles, name);
        }
    }
}
