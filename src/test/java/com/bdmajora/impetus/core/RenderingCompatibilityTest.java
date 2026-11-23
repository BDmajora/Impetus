package com.bdmajora.impetus.core;

import com.bdmajora.testing.Mc;
import com.cleanroommc.common.CleanroomEnvironment;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.renderer.block.model.BuiltInModel;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.EnvironmentStateTweaker;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.Proxy;
import top.outlands.foundation.boot.TransformerHolder;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RenderingCompatibilityTest {
    private static final String FRUSTUM = "mixins/bugfixes/mixins.world.culling.frustum.json";
    private static final String OTHER_CONFIG = "mixins/bugfixes/mixins.world.culling.occlusion.json";
    private static final String IMPETUS_RENDERING = "mixins.impetus-rendering-test.json";

    @Test
    void vanillaFrustumPatchAndMissingSaveBlocksAreHandledTogether(@TempDir Path work) throws Exception {
        Path configJar = work.resolve("rendering-test.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(configJar))) {
            String foreignConfig = """
                    {"minVersion":"0.8","package":"mod.acgaming.universaltweaks"}
                    """;
            writeConfig(jar, FRUSTUM, foreignConfig);
            writeConfig(jar, OTHER_CONFIG, foreignConfig);
            writeConfig(jar, IMPETUS_RENDERING, """
                    {"required":true,"minVersion":"0.8","compatibilityLevel":"JAVA_25",
                     "package":"com.bdmajora.impetus.mixin",
                     "mixins":["core.terrain.BlockStateMapperMixin","core.terrain.RenderGlobalMixin"]}
                    """);
        }
        String classpath = configJar + File.pathSeparator + System.getProperty("impetus.test.classpath",
                System.getProperty("java.class.path"));
        Path log = work.resolve("probe.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED",
                "-cp", classpath, RenderingCompatibilityTest.class.getName())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), () -> "Rendering probe timed out. See " + log);
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    private static void writeConfig(JarOutputStream jar, String name, String json) throws Exception {
        jar.putNextEntry(new JarEntry(name));
        jar.write(json.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }

    public static void main(String[] args) throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("forgeLaunchArgs", new HashMap<String, String>());
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.minecraftHome = new File(".");
        Launch.classLoader = new LaunchClassLoader(urls.toArray(URL[]::new));
        TransformerHolder.transformers = new ArrayList<>();
        CleanroomEnvironment.setSide(Side.CLIENT);
        MixinBootstrap.init();
        Proxy mixin = new Proxy();
        MixinExtrasBootstrap.init();
        new EnvironmentStateTweaker().getLaunchArguments();

        Mixins.addConfiguration(FRUSTUM);
        assertTrue(isQueued(FRUSTUM));
        Mixins.addConfiguration(OTHER_CONFIG);
        ImpetusMixinRegistrar.suppressVanillaFrustumFix();
        assertFalse(isQueued(FRUSTUM));
        assertNull(Config.create(FRUSTUM, MixinEnvironment.getDefaultEnvironment(), null));
        assertTrue(isQueued(OTHER_CONFIG));

        Mixins.addConfiguration(IMPETUS_RENDERING);
        var renderGlobal = new ClassNode();
        new ClassReader(transform(mixin, "net.minecraft.client.renderer.RenderGlobal")).accept(renderGlobal, 0);
        var setupTerrain = renderGlobal.methods.stream().filter(method -> method.name.equals("setupTerrain"))
                .findFirst().orElseThrow();
        assertTrue(Arrays.stream(setupTerrain.instructions.toArray()).anyMatch(instruction ->
                instruction instanceof MethodInsnNode call && call.name.equals("setupTerrain")
                        && call.owner.equals("com/bdmajora/impetus/impl/render/terrain/ImpetusWorldRenderer")));

        verifyModelLookup(mixin);
    }

    private static void verifyModelLookup(Proxy mixin) throws Exception {
        String mapperName = BlockStateMapper.class.getName();
        byte[] transformed = transform(mixin, mapperName);
        var mapperClass = new ClassLoader(RenderingCompatibilityTest.class.getClassLoader()) {
            Class<?> defineMapper() {
                return defineClass(mapperName, transformed, 0, transformed.length);
            }
        }.defineMapper();
        Mc.bootstrap();
        var dummyType = Class.forName("net.minecraftforge.registries.GameData$BlockCallbacks");
        var factory = dummyType.getDeclaredField("INSTANCE");
        factory.setAccessible(true);
        var createDummy = dummyType.getDeclaredMethod("createDummy", ResourceLocation.class);
        createDummy.setAccessible(true);
        var missingId = new ResourceLocation("wings", "amethyst_ore");
        var dummy = (Block) createDummy.invoke(factory.get(null), missingId);
        ForgeRegistries.BLOCKS.register(dummy);
        assertSame(dummy, ForgeRegistries.BLOCKS.getValue(missingId));
        assertEquals(EnumBlockRenderType.INVISIBLE, dummy.getDefaultState().getRenderType());

        var mapper = mapperClass.getConstructor().newInstance();
        var getVariants = mapperClass.getMethod("getVariants", Block.class);
        var getLocations = mapperClass.getMethod("getBlockstateLocations", Block.class);
        var register = mapperClass.getMethod("registerBlockStateMapper", Block.class, IStateMapper.class);
        var modelId = new ModelResourceLocation(missingId, "normal");
        // A custom state mapper on a real block, including a modded air subclass, still works.
        var realBlock = new BlockAir() {};
        register.invoke(mapper, realBlock, (IStateMapper)
                block -> Map.of(block.getDefaultState(), modelId));
        assertEquals(Set.of(missingId), getLocations.invoke(mapper, realBlock));
        assertEquals(Set.of(), getLocations.invoke(mapper, dummy));
        assertEquals(Map.of(), getVariants.invoke(mapper, dummy));

        // VintageFix's lazy lookup asks the state mapper for a location, then calls ModelManager.getModel.
        var manager = new ModelManager(null);
        var baked = new BuiltInModel(ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
        var missing = new BuiltInModel(ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
        var registry = new RegistrySimple<ModelResourceLocation, IBakedModel>() {
            int lookups;

            @Override
            public IBakedModel getObject(ModelResourceLocation location) {
                assertEquals(modelId, location);
                lookups++;
                return baked;
            }
        };
        var registryField = ModelManager.class.getDeclaredField("modelRegistry");
        registryField.setAccessible(true);
        registryField.set(manager, registry);
        var missingField = ModelManager.class.getDeclaredField("defaultModel");
        missingField.setAccessible(true);
        missingField.set(manager, missing);
        for (Block block : new Block[] {dummy, realBlock, dummy}) {
            var variants = (Map<?, ?>) getVariants.invoke(mapper, block);
            assertSame(block == dummy ? missing : baked,
                    manager.getModel((ModelResourceLocation) variants.get(block.getDefaultState())));
        }
        assertEquals(1, registry.lookups);
    }

    private static boolean isQueued(String name) {
        return Mixins.getConfigs().stream().anyMatch(config -> name.equals(config.getName()));
    }

    private static byte[] transform(Proxy mixin, String className) throws Exception {
        try (var original = RenderingCompatibilityTest.class.getResourceAsStream("/" + className.replace('.', '/') + ".class")) {
            assertNotNull(original, className);
            return mixin.transform(className, className, original.readAllBytes());
        }
    }
}
