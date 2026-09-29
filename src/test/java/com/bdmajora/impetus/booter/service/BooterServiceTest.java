package com.bdmajora.impetus.booter.service;

import com.bdmajora.impetus.booter.Tags;
import com.bdmajora.testing.LaunchEnvironment;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.Multimap;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.obfuscation.RemapperChain;
import org.spongepowered.asm.obfuscation.mapping.remap.CleanroomRemapper;
import org.spongepowered.asm.service.ILegacyClassTransformer;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.mojang.LegacyTransformerHandle;
import org.spongepowered.asm.util.Constants.ManifestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BooterServiceTest {
    @TempDir
    Path home;

    private LaunchClassLoader loader;

    // A coremod shipped beside a TweakClass, which queues more tweakers as it constructs
    public static final class RescuedCoremod implements IFMLLoadingPlugin {
        @SuppressWarnings("unchecked")
        public RescuedCoremod() {
            List<String> tweaks = (List<String>) Launch.blackboard.get("TweakClasses");
            tweaks.add("rescued.First");
            tweaks.add(0, "rescued.Second");
            tweaks.addAll(List.of("rescued.Third"));
            tweaks.addAll(0, List.of("rescued.Fourth"));
        }

        @Override
        public String[] getASMTransformerClass() {
            return new String[0];
        }

        @Override
        public String getModContainerClass() {
            return null;
        }

        @Override
        public String getSetupClass() {
            return null;
        }

        @Override
        public void injectData(Map<String, Object> data) {
        }

        @Override
        public String getAccessTransformerClass() {
            return null;
        }
    }

    // A transformer that is already Mixin-aware, counting what it is handed and optionally re-entering
    public static final class CountingTransformer implements IClassTransformer, ILegacyClassTransformer {
        static int calls;
        static boolean reenter;

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return basicClass;
        }

        @Override
        public String getName() {
            return getClass().getName();
        }

        @Override
        public boolean isDelegationExcluded() {
            return false;
        }

        @Override
        public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
            calls++;
            if (reenter) {
                MixinService.getService().getReEntranceLock().set();
            }
            return basicClass;
        }
    }

    // One that asks to be left out of Mixin's delegation
    public static final class ExcludedTransformer implements IClassTransformer, ILegacyClassTransformer {
        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return basicClass;
        }

        @Override
        public String getName() {
            return getClass().getName();
        }

        @Override
        public boolean isDelegationExcluded() {
            return true;
        }

        @Override
        public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
            throw new AssertionError("excluded from delegation");
        }
    }

    // A plain LaunchWrapper transformer, which Mixin wraps
    public static final class PlainTransformer implements IClassTransformer {
        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return basicClass;
        }
    }

    // FML's deobfuscation transformer stands behind the class name mapping
    public static final class NameTransformer implements IClassTransformer, IClassNameTransformer {
        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return basicClass;
        }

        @Override
        public String unmapClassName(String name) {
            return name;
        }

        @Override
        public String remapClassName(String name) {
            return name;
        }
    }

    // Stands in for FMLLog's logger field
    static final class LoggerHolder {
        static Logger log;
        static final Logger FIXED = mock(Logger.class);
    }

    @BeforeEach
    void boot() {
        loader = LaunchEnvironment.install(home);
        resetDiscoverer();
        CountingTransformer.calls = 0;
        CountingTransformer.reenter = false;
    }

    @AfterEach
    void shutDown() {
        resetDiscoverer();
        Statics.set(ClassLoadTracer.class, "watched", null);
        Statics.set(InitPhaseTrigger.class, "installed", null);
        LaunchEnvironment.restore();
    }

    private static void resetDiscoverer() {
        Statics.set(ModDiscoverer.class, "discovered", false);
        for (String name : List.of("modIdToFiles", "fileToModIds")) {
            Statics.<Multimap<?, ?>>get(ModDiscoverer.class, name).clear();
        }
        for (String name : List.of("manifestMixinJars", "manifestMixinModDirJars", "forceLoadAsModFiles", "forceReparseableFiles")) {
            Statics.<Set<?>>get(ModDiscoverer.class, name).clear();
        }
        Statics.<Map<?, ?>>get(ModDiscoverer.class, "droppedCoremods").clear();
        Statics.<List<?>>get(ModDiscoverer.class, "rescuedTweakClasses").clear();
    }

    // A jar in the given directory with the given main manifest attributes and entries, in order
    private static Path jar(Path directory, String name, Map<String, String> attributes, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(directory);
        Path jar = directory.resolve(name);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.forEach((key, value) -> manifest.getMainAttributes().putValue(key, value));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    private static Map<String, byte[]> mcmodInfo(String json) {
        return Map.of("mcmod.info", json.getBytes(StandardCharsets.UTF_8));
    }

    // A class carrying another annotation, then @Mod with its modid after another element
    private static byte[] modClass(String internalName, String modId) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writer.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
        if (modId != null) {
            AnnotationVisitor mod = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
            mod.visit("name", "Named");
            mod.visit("modid", modId);
            mod.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bytes(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return in.readAllBytes();
        }
    }

    @Test
    void theServiceIsTheBootersOwn() {
        MixinServiceBootstrap bootstrap = new MixinServiceBootstrap();
        assertEquals(Tags.MOD_NAME, bootstrap.getName());
        assertEquals(MixinBooterService.class.getName(), bootstrap.getServiceClassName());
        bootstrap.bootstrap();

        MixinBooterService service = (MixinBooterService) MixinService.getService();
        assertEquals(Tags.MOD_NAME, service.getName());
        assertFalse((boolean) Mixins.call(service, "isDevelopment"));
        assertEquals("CLIENT", service.getSideName());
        assertSame(MixinBooterService.auditFile(), Mixins.call(service, "createAuditLog"));
        assertInstanceOf(ClassProvider.class, service.getClassProvider());
        assertInstanceOf(BytecodeProvider.class, service.getBytecodeProvider());
        assertInstanceOf(TransformerProvider.class, service.getTransformerProvider());
        assertInstanceOf(ClassLoaderUtil.class, service.getClassTracker());
        Mixins.call(service, "onRefresh");
        // INIT is advanced through whatever transitioner Mixin handed over, and not at all before it did
        service.gotoInitPhase();
        List<MixinEnvironment.Phase> phases = new ArrayList<>();
        service.acceptPhaseTransitioner(phases::add);
        service.gotoInitPhase();
        assertEquals(List.of(MixinEnvironment.Phase.INIT), phases);
        // Mixin's proxy transformer is registered (here it cannot load, which LaunchWrapper only logs)
        service.beginPhase();
        // A jar path maps to the mod that owns it; anything else has no source id
        assertNull(Mixins.call(service, "resolveSourceId", URI.create("https://example.com/mod.jar")));
        assertNull(Mixins.call(service, "resolveSourceId", URI.create("file:/mods/fragment.jar#part")));
        assertNull(Mixins.call(service, "resolveSourceId", home.resolve("unknown.jar").toUri()));
        IContainerHandle primary = service.getPrimaryContainer();
        assertNotNull(primary);
        // Init runs once, registering the environment tweaker and the SRG to notch remapper; Mixin's default environment
        // only exists after MixinBootstrap.init, so it is stood in for rather than booted in the test JVM
        try (MockedStatic<MixinEnvironment> environment = Mockito.mockStatic(MixinEnvironment.class)) {
            MixinEnvironment defaults = mock(MixinEnvironment.class);
            RemapperChain remappers = mock(RemapperChain.class);
            environment.when(MixinEnvironment::getDefaultEnvironment).thenReturn(defaults);
            Mockito.when(defaults.getRemappers()).thenReturn(remappers);
            service.init();
            service.init();
            verify(remappers).add(Mockito.any(CleanroomRemapper.class));
        }
        assertTrue(((List<?>) Launch.blackboard.get("TweakClasses")).contains("org.spongepowered.asm.mixin.EnvironmentStateTweaker"));
        assertTrue(service.getMixinContainers().isEmpty());
    }

    @Test
    void classesAreFoundThroughTheLaunchClassLoader() throws Exception {
        ClassProvider classes = new ClassProvider();
        assertEquals(0, classes.getClassPath().length);
        assertSame(Tags.class, classes.findClass(Tags.class.getName()));
        assertSame(Tags.class, classes.findClass(Tags.class.getName(), false));
        assertSame(String.class, classes.findAgentClass("java.lang.String", false));

        ClassLoaderUtil tracker = new ClassLoaderUtil();
        assertFalse(tracker.isClassLoaded("demo.Cached"));
        Mixins.<Map<String, Class<?>>>get(loader, "cachedClasses").put("demo.Cached", String.class);
        assertTrue(tracker.isClassLoaded("demo.Cached"));
        // LaunchWrapper's own exclusions are reported back to Mixin
        assertEquals("PACKAGE_CLASSLOADER_EXCLUSION", tracker.getClassRestrictions("org.lwjgl.opengl.GL11"));
        loader.addTransformerExclusion("demo.");
        assertEquals("PACKAGE_TRANSFORMER_EXCLUSION", tracker.getClassRestrictions("demo.Thing"));
        loader.addTransformerExclusion("org.lwjgl.");
        assertEquals("PACKAGE_CLASSLOADER_EXCLUSION,PACKAGE_TRANSFORMER_EXCLUSION", tracker.getClassRestrictions("org.lwjgl.opengl.GL11"));
        assertEquals("", tracker.getClassRestrictions("free.Thing"));
        assertTrue(tracker.isClassExcluded("renamed.Thing", "demo.Thing"));
        assertFalse(tracker.isClassExcluded("free.Thing", null));
        tracker.registerInvalidClass("demo.Broken");
        assertTrue(Mixins.<Set<String>>get(loader, "invalidClasses").contains("demo.Broken"));
        // A LaunchClassLoader missing its exclusion sets is treated as excluding nothing
        Mixins.set(tracker, "classLoaderExceptions", null);
        Mixins.set(tracker, "transformerExceptions", null);
        assertEquals("", tracker.getClassRestrictions("org.lwjgl.opengl.GL11"));
    }

    @Test
    void mixinDelegatesToEveryTransformerItMaySafelyRun() throws Exception {
        loader.registerTransformer(CountingTransformer.class.getName());
        loader.registerTransformer(ExcludedTransformer.class.getName());
        loader.registerTransformer(PlainTransformer.class.getName());
        loader.registerTransformer(NameTransformer.class.getName());
        loader.registerTransformer("net.minecraftforge.fml.common.asm.transformers.TerminalTransformer");
        TransformerProvider transformers = new TransformerProvider();
        Collection<ITransformer> all = transformers.getTransformers();
        assertEquals(5, all.size());
        assertTrue(all.stream().anyMatch(t -> t instanceof CountingTransformer));
        // The two Mixin-aware ones are used as they are, the rest wrapped
        assertEquals(3, all.stream().filter(t -> t instanceof LegacyTransformerHandle).count());
        // Forge's re-entrant transformers and those asking to be left out are not delegated to
        List<ITransformer> delegated = transformers.getDelegatedTransformers();
        assertEquals(3, delegated.size());
        assertTrue(delegated.stream().noneMatch(t -> t.getName().contains("TerminalTransformer") || t instanceof ExcludedTransformer));
        // The list is rebuilt when LaunchWrapper's changes, when told to refresh, or when a transformer is excluded
        assertSame(transformers.getDelegatedLegacyTransformers(), transformers.getDelegatedLegacyTransformers());
        transformers.refreshDelegatedTransformers();
        transformers.addTransformerExclusion(PlainTransformer.class.getName());
        assertEquals(2, transformers.getDelegatedTransformers().size());

        BytecodeProvider bytecode = new BytecodeProvider(transformers, MixinService.getService().getReEntranceLock(), new ClassLoaderUtil());
        // A class LaunchWrapper transforms (the booter's own are handed to the parent loader untransformed)
        String assertions = "org/junit/jupiter/api/Assertions";
        ClassNode node = bytecode.getClassNode(assertions);
        assertEquals(assertions, node.name);
        assertEquals(1, CountingTransformer.calls);
        bytecode.getClassNode(assertions, false);
        assertEquals(1, CountingTransformer.calls);
        // LaunchWrapper's own copy of the bytes wins over the application loader's: Tags' bytes stand in for demo.Copied
        Path classes = home.resolve("classes");
        Files.createDirectories(classes.resolve("demo"));
        Files.write(classes.resolve("demo/Copied.class"), bytes(Tags.class));
        loader.addURL(classes.toUri().toURL());
        assertEquals(Tags.class.getName().replace('.', '/'), bytecode.getClassNode("demo/Copied", true).name);
        assertEquals(2, CountingTransformer.calls);
        // Classes LaunchWrapper keeps away from its transformers are read untransformed
        bytecode.getClassNode("org/lwjgl/opengl/GL11", true, 0);
        assertEquals(2, CountingTransformer.calls);
        assertThrows(ClassNotFoundException.class, () -> bytecode.getClassNode("demo/Missing"));
        // A transformer that re-enters Mixin while it runs is dropped from delegation
        CountingTransformer.reenter = true;
        bytecode.getClassNode(assertions);
        assertEquals(3, CountingTransformer.calls);
        bytecode.getClassNode(assertions);
        assertEquals(3, CountingTransformer.calls);
    }

    @Test
    void theTracerLogsWhereAWatchedClassLoaded() {
        ClassLoadTracer tracer = new ClassLoadTracer();
        byte[] bytes = {1, 2, 3};
        // Unwatched, and a blank property, cost nothing
        assertSame(bytes, tracer.transform("a", "a", bytes));
        System.setProperty(ClassLoadTracer.WATCH_PROPERTY, "  ");
        assertSame(bytes, tracer.transform("a", "a", bytes));
        System.setProperty(ClassLoadTracer.WATCH_PROPERTY, " demo.Watched , ,other.Named ");
        assertSame(bytes, tracer.transform("x", "demo.Watched", bytes));
        assertSame(bytes, tracer.transform("x", "demo.Watched", bytes));
        assertSame(bytes, tracer.transform("other.Named", "renamed.Thing", bytes));
        assertSame(bytes, tracer.transform("free", "free", bytes));
        assertEquals(Set.of("demo.Watched", "other.Named"), Mixins.get(tracer, "traced"));
        // * watches everything, by its transformed name where there is one
        Statics.set(ClassLoadTracer.class, "watched", null);
        System.setProperty(ClassLoadTracer.WATCH_PROPERTY, ClassLoadTracer.WATCH_ALL);
        ClassLoadTracer all = new ClassLoadTracer();
        all.transform("raw.Name", null, bytes);
        all.transform("raw.Other", "mapped.Other", bytes);
        assertEquals(Set.of("raw.Name", "mapped.Other"), Mixins.get(all, "traced"));

        // The logged stack starts past the class loading machinery and stops at the launcher
        StackTraceElement[] stack = {
                frame("t", "a"), frame("t", "b"), frame("t", "c"), frame("t", "d"), frame("t", "e"),
                frame("java.lang.ClassLoader", "loadClass"),
                frame("java.security.SecureClassLoader", "defineClass"),
                frame("sun.reflect.NativeMethodAccessorImpl", "invoke0"),
                frame("sun.reflect.DelegatingMethodAccessorImpl", "invoke"),
                frame("net.minecraft.launchwrapper.LaunchClassLoader", "findClass"),
                frame("net.minecraft.launchwrapper.LaunchClassLoader", "loadClass"),
                frame("net.minecraft.launchwrapper.LaunchClassLoader", "getClassBytes"),
                frame("demo.Caller", "run"),
                frame("net.minecraft.launchwrapper.Launch", "main"),
                frame("net.minecraft.launchwrapper.Launch", "launch"),
                frame("after.Entry", "x")};
        StackTraceElement[] kept = Statics.call(ClassLoadTracer.class, "trim", (Object) stack);
        assertArrayEquals(new StackTraceElement[] {stack[11], stack[12], stack[13]}, kept);
        StackTraceElement[] client = {frame("t", "a"), frame("t", "b"), frame("t", "c"), frame("t", "d"), frame("t", "e"),
                frame("demo.Caller", "run"), frame("net.minecraft.client.main.Main", "main"), frame("after", "x")};
        assertArrayEquals(new StackTraceElement[] {client[5]}, Statics.call(ClassLoadTracer.class, "trim", (Object) client));
        assertEquals(0, ((StackTraceElement[]) Statics.call(ClassLoadTracer.class, "trim", (Object) new StackTraceElement[] {frame("t", "a")})).length);
    }

    private static StackTraceElement frame(String type, String method) {
        return new StackTraceElement(type, method, null, -1);
    }

    @Test
    void theInitTriggerProxiesFmlsLoggerUntilInitIsReached() throws Throwable {
        // This JVM has no Field.modifiers, so the hook on FMLLog cannot install and INIT waits for the DEFAULT transition
        InitPhaseTrigger.install();
        assertNull(Statics.get(InitPhaseTrigger.class, "installed"));
        InitPhaseTrigger.uninstall();

        Field field = LoggerHolder.class.getDeclaredField("log");
        Logger delegate = mock(Logger.class);
        InitPhaseTrigger trigger = Mixins.construct(InitPhaseTrigger.class, field, delegate);
        Logger proxy = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] {Logger.class}, trigger);
        MixinBooterService service = (MixinBooterService) MixinService.getService();
        List<MixinEnvironment.Phase> phases = new ArrayList<>();
        service.acceptPhaseTransitioner(phases::add);
        // Every call is forwarded, and FML's "Validating minecraft" debug line advances Mixin to INIT once
        proxy.info("hello");
        proxy.info("Validating minecraft");
        proxy.debug("Validating minecraft");
        proxy.debug("Validating minecraft");
        proxy.debug("other");
        assertEquals(List.of(MixinEnvironment.Phase.INIT), phases);
        verify(delegate).info("hello");
        verify(delegate, org.mockito.Mockito.times(2)).debug("Validating minecraft");
        doThrow(new IllegalStateException("boom")).when(delegate).warn("boom");
        assertThrows(IllegalStateException.class, () -> proxy.warn("boom"));
        assertNotNull(proxy.toString());

        // Uninstalling puts the real logger back, and says so when INIT was never seen on it
        Statics.set(InitPhaseTrigger.class, "installed", trigger);
        InitPhaseTrigger.uninstall();
        assertSame(delegate, LoggerHolder.log);
        assertNull(Statics.get(InitPhaseTrigger.class, "installed"));
        InitPhaseTrigger unseen = Mixins.construct(InitPhaseTrigger.class, LoggerHolder.class.getDeclaredField("FIXED"), delegate);
        Statics.set(InitPhaseTrigger.class, "installed", unseen);
        InitPhaseTrigger.uninstall();
        assertNotSame(delegate, LoggerHolder.FIXED);
    }

    @Test
    void theRescuerTweakerDoesItsWorkAsItIsBuilt() {
        CoremodsRescuer rescuer = new CoremodsRescuer();
        rescuer.acceptOptions(List.of(), home.toFile(), home.toFile(), "profile");
        rescuer.injectIntoClassLoader(loader);
        assertEquals("", rescuer.getLaunchTarget());
        assertEquals(0, rescuer.getLaunchArguments().length);
    }

    @Test
    void everyKindOfModJarIsDiscovered() throws Exception {
        Path mods = home.resolve("mods");
        Path alpha = jar(mods, "alpha.jar", Map.of(ManifestAttributes.MIXINCONFIGS, "mixins.alpha.json"),
                mcmodInfo("[{\"modid\":\"alpha\"}]"));
        Path beta = jar(mods, "beta.jar", Map.of(), new LinkedHashMap<>(Map.of(
                "mcmod.info", "{\"modList\":[{\"modid\":\"beta\"},{\"modid\":\"gamma\"},3]}".getBytes(StandardCharsets.UTF_8),
                "org/spongepowered/asm/launch/MixinBootstrap.class", new byte[0])));
        Map<String, byte[]> deltaEntries = new LinkedHashMap<>();
        deltaEntries.put("META-INF/", new byte[0]);
        deltaEntries.put("readme.txt", new byte[] {1});
        deltaEntries.put("demo/Broken.class", new byte[] {1, 2, 3});
        deltaEntries.put("demo/Plain.class", modClass("demo/Plain", null));
        deltaEntries.put("demo/DeltaMod.class", modClass("demo/DeltaMod", "delta"));
        deltaEntries.put("demo/Later.class", modClass("demo/Later", "later"));
        Path delta = jar(mods, "delta.jar", Map.of(ManifestAttributes.MIXINCONNECTOR, "demo.Connector"), deltaEntries);
        jar(mods, "optifine.jar", Map.of(ManifestAttributes.TWEAKER, "optifine.OptiFineForgeTweaker"), Map.of());
        jar(mods, "moddirector.jar", Map.of(ManifestAttributes.TWEAKER, "net.jan.moddirector.launchwrapper.ModDirectorTweaker"), Map.of());
        jar(mods, "paintings.jar", Map.of("FMLCorePlugin", "git.jbredwards.jsonpaintings.mod.asm.ASMHandler"), Map.of());
        jar(mods, "cleanroom.jar", Map.of(ManifestAttributes.MODTYPE, ManifestAttributes.CLEANROOMMODTYPE,
                ManifestAttributes.MIXINCONFIGS, "mixins.cleanroom.json"), mcmodInfo("[{\"modid\":\"skipped\"}]"));
        jar(mods, "forced.jar", Map.of("ForceLoadAsMod", "true", "FMLCorePluginContainsFMLMod", "true"),
                mcmodInfo("[{\"modid\":\"forced\"}]"));
        jar(mods, "loose.jar", Map.of("ForceLoadAsMod", "TRUE"), mcmodInfo("[{\"modid\":\"loose\"}]"));
        Path replay = jar(mods, "replay.jar", Map.of("FMLCorePlugin", RescuedCoremod.class.getName(),
                ManifestAttributes.TWEAKER, "org.spongepowered.asm.launch.MixinTweaker"), mcmodInfo("[{\"modid\":\"replay\"}]"));
        jar(mods, "replay-copy.jar", Map.of("FMLCorePlugin", RescuedCoremod.class.getName(),
                ManifestAttributes.TWEAKER, "com.replaymod.core.tweaker.ReplayModTweaker"), Map.of());
        jar(mods, "missing-coremod.jar", Map.of("FMLCorePlugin", "demo.NoSuchCoremod",
                ManifestAttributes.TWEAKER, "org.spongepowered.asm.launch.MixinTweaker"), Map.of());
        jar(mods, "own-tweaker.jar", Map.of("FMLCorePlugin", "demo.Ignored", ManifestAttributes.TWEAKER, "demo.Tweaker"), Map.of());
        jar(mods, "badinfo.jar", Map.of(), mcmodInfo("not json {"));
        Files.writeString(mods.resolve("notes.txt"), "not a mod");
        Files.writeString(mods.resolve("corrupt.jar"), "not a zip");
        // A jar only on the development classpath, and a classpath directory, beside the mods folder
        Path dev = jar(home.resolve("dev"), "dev.jar", Map.of(ManifestAttributes.MIXINCONFIGS, "mixins.dev.json"),
                mcmodInfo("[{\"modid\":\"dev\"}]"));
        loader.addURL(dev.toUri().toURL());
        loader.addURL(home.resolve("dev").toUri().toURL());
        loader.addURL(alpha.toUri().toURL());

        ModDiscoverer.discover();
        ModDiscoverer.discover();
        for (String id : List.of("alpha", "beta", "gamma", "delta", "optifine", "moddirector", "jsonpaintings", "forced", "loose", "replay", "dev")) {
            assertTrue(ModDiscoverer.isModPresent(id), id);
        }
        assertFalse(ModDiscoverer.isModPresent("skipped"));
        assertFalse(ModDiscoverer.isModPresent("later"));
        assertTrue(ModDiscoverer.getPresentMods().containsAll(List.of("alpha", "dev")));
        assertEquals(Set.of(alpha.toFile().getAbsoluteFile()), ModDiscoverer.getModSources("alpha"));
        assertEquals("alpha", ModDiscoverer.getModFromSource(alpha.toFile()));
        assertEquals(Set.of("beta", "gamma"), ModDiscoverer.getModsFromSource(beta.toFile()));
        assertEquals("delta", ModDiscoverer.getModFromSource(delta.toFile()));
        assertNull(ModDiscoverer.getModFromSource(home.resolve("nothing.jar").toFile()));
        assertTrue(ModDiscoverer.manifestMixinJars().containsAll(List.of(alpha.toFile(), delta.toFile(), dev.toFile())));
        assertTrue(ModDiscoverer.isModDirMixinJar(alpha.toFile()));
        assertFalse(ModDiscoverer.isModDirMixinJar(dev.toFile()));
        List<java.io.File> listed = new ArrayList<>();
        Statics.call(ModDiscoverer.class, "addDirectoryContents", mods.toFile(), listed);
        Statics.call(ModDiscoverer.class, "addDirectoryContents", home.resolve("absent").toFile(), listed);
        assertTrue(listed.contains(alpha.toFile()));

        // ForceLoadAsMod undoes Forge ignoring cascading-tweaker jars, and a contained-mod jar is reparsed
        List<String> ignored = CoreModManager.getIgnoredMods();
        List<String> reparseable = CoreModManager.getReparseableCoremods();
        ignored.add("forced.jar");
        try {
            ModDiscoverer.applyForceLoadAsMod();
            ModDiscoverer.applyForceLoadAsMod();
            assertFalse(ignored.contains("forced.jar"));
            assertTrue(reparseable.contains("forced.jar"));
            assertFalse(reparseable.contains("loose.jar"));

            // The service hands Mixin every manifest jar, adding the ones Forge will not to LaunchWrapper
            ignored.add("delta.jar");
            MixinBooterService service = (MixinBooterService) MixinService.getService();
            assertEquals(3, service.getMixinContainers().size());
            assertTrue(reparseable.contains("alpha.jar"));
            assertFalse(reparseable.contains("dev.jar"));
            assertEquals("replay", Mixins.call(service, "resolveSourceId", replay.toUri()));
        } finally {
            ignored.removeAll(List.of("forced.jar", "delta.jar"));
            reparseable.removeAll(List.of("forced.jar", "alpha.jar"));
        }

        // Coremods Forge skips because they ship beside a tweaker are loaded by hand, and the tweakers they add are replayed
        @SuppressWarnings("unchecked")
        List<String> tweaks = (List<String>) Launch.blackboard.get("TweakClasses");
        tweaks.add("rescued.Second");
        // FML only creates its plugin list once its own coremod discovery runs
        List<?> plugins = Statics.get(CoreModManager.class, "loadPlugins");
        Statics.set(CoreModManager.class, "loadPlugins", new ArrayList<>());
        try {
            ModDiscoverer.rescueDroppedCoremods();
            assertSame(tweaks, Launch.blackboard.get("TweakClasses"));
            assertEquals(List.of("rescued.Second"), tweaks);
            ModDiscoverer.flushRescuedTweakClasses();
            assertEquals(List.of("rescued.Second", "rescued.First", "rescued.Third", "rescued.Fourth"), tweaks);
            ModDiscoverer.flushRescuedTweakClasses();
            // Rescuing again finds the coremod already loaded, and with no tweak list there is nothing to replay into
            Launch.blackboard.remove("TweakClasses");
            ModDiscoverer.rescueDroppedCoremods();
            Statics.<List<String>>get(ModDiscoverer.class, "rescuedTweakClasses").add("rescued.Late");
            ModDiscoverer.flushRescuedTweakClasses();
            assertTrue(Statics.<List<?>>get(ModDiscoverer.class, "rescuedTweakClasses").isEmpty());
        } finally {
            Statics.set(CoreModManager.class, "loadPlugins", plugins);
        }
    }
}
