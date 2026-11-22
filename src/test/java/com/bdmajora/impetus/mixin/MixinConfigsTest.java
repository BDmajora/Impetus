package com.bdmajora.impetus.mixin;

import com.bdmajora.coarctatio.mixin.CoarctatioMixinPlugin;
import com.bdmajora.extras.network.NetworkLimits;
import com.bdmajora.testing.Statics;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Cross-checks the mixin configs against the compiled classes: a listed class that is gone (a3fc5013f deleted ChunkPrimerMixin and
// had to unlist it by hand) fails the game at launch, and an unlisted @Mixin class is silently dead code.
class MixinConfigsTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    // The Impetus plugin lists its package by scanning, so its config names nothing
    private static final String SCANNED_PACKAGE = "com.bdmajora.impetus.mixin";

    @Test
    void theRegistrarAddsExactlyTheConfigsThatShip() throws Exception {
        Set<String> shipped = new TreeSet<>();
        try (Stream<Path> files = Files.list(resources())) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("mixins.") && name.endsWith(".json"))
                    .forEach(shipped::add);
        }
        assertEquals(shipped, new TreeSet<>(registered()));
        assertEquals(registered().size(), new HashSet<>(registered()).size(), "a config is registered twice");
    }

    @Test
    void everyListedMixinExistsIsAMixinAndIsListedOnce() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> config : configs().entrySet()) {
            JsonObject json = config.getValue();
            String pack = json.get("package").getAsString();
            Set<String> seen = new HashSet<>();
            for (String section : List.of("mixins", "client", "server")) {
                JsonArray list = json.has(section) ? json.getAsJsonArray(section) : new JsonArray();
                list.forEach(element -> {
                    String name = element.getAsString();
                    if (!seen.add(name)) {
                        problems.add(config.getKey() + ": " + name + " listed twice");
                    }
                    ClassNode node = classNode(pack + "." + name);
                    if (node == null) {
                        problems.add(config.getKey() + ": " + name + " has no class file");
                    } else if (!hasMixinAnnotation(node)) {
                        problems.add(config.getKey() + ": " + name + " is not a @Mixin");
                    }
                });
            }
            if (json.has("plugin")) {
                assertNotNull(classNode(json.get("plugin").getAsString()), config.getKey() + " plugin class is missing");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyMixinClassIsListedSomewhere() throws Exception {
        Set<String> listed = new HashSet<>();
        Map<String, String> packages = new TreeMap<>();
        for (Map.Entry<String, JsonObject> config : configs().entrySet()) {
            JsonObject json = config.getValue();
            String pack = json.get("package").getAsString();
            packages.put(pack, config.getKey());
            for (String section : List.of("mixins", "client", "server")) {
                if (json.has(section)) {
                    json.getAsJsonArray(section).forEach(element -> listed.add(pack + "." + element.getAsString()));
                }
            }
        }
        // Coarctatio adds its guarded groups from the plugin when their switch is on
        for (Object group : Statics.<List<?>>get(CoarctatioMixinPlugin.class, "GUARDED")) {
            Method mixins = group.getClass().getDeclaredMethod("mixins");
            mixins.setAccessible(true);
            for (Object name : (List<?>) mixins.invoke(group)) {
                listed.add("com.bdmajora.coarctatio.mixin." + name);
            }
        }
        List<String> orphans = new ArrayList<>();
        Path classes = Path.of(NetworkLimits.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".class"))::iterator) {
                String name = classes.relativize(file).toString().replace('/', '.').replace('\\', '.');
                name = name.substring(0, name.length() - ".class".length());
                if (name.startsWith(SCANNED_PACKAGE + ".") || !hasMixinAnnotation(read(Files.readAllBytes(file)))) {
                    continue;
                }
                String owner = null;
                for (String pack : packages.keySet()) {
                    if (name.startsWith(pack + ".")) {
                        owner = pack;
                    }
                }
                if (owner == null) {
                    orphans.add(name + " is outside every config package");
                } else if (!listed.contains(name)) {
                    orphans.add(name + " is not listed in " + packages.get(owner));
                }
            }
        }
        assertEquals(List.of(), orphans);
    }

    @Test
    void theScannedPackageHoldsOnlyItsOwnMixinsAndTheConfigListsNone() throws Exception {
        JsonObject impetus = configs().get("mixins.impetus.json");
        assertEquals(SCANNED_PACKAGE, impetus.get("package").getAsString());
        for (String section : List.of("mixins", "client", "server")) {
            assertTrue(!impetus.has(section) || impetus.getAsJsonArray(section).isEmpty(),
                    "a listed entry would be added twice next to the scan");
        }
        // No other config's package sits inside the scanned one, or its classes would be picked up twice
        for (Map.Entry<String, JsonObject> config : configs().entrySet()) {
            String pack = config.getValue().get("package").getAsString();
            assertFalse(!config.getKey().equals("mixins.impetus.json") && pack.startsWith(SCANNED_PACKAGE + "."), config.getKey());
        }
    }

    @Test
    void theRemovedPrimerCacheLeftNothingBehind() {
        // a3fc5013f dropped worldgen.primer_state_cache: no class, no listing, no switch
        assertEquals(null, classNode("com.bdmajora.equilibrium.mixin.worldgen.primer_state_cache.ChunkPrimerMixin"));
        assertEquals(null, com.bdmajora.equilibrium.config.EquilibriumOptions.get("mixin.worldgen.primer_state_cache"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> registered() throws Exception {
        Method method = Class.forName("com.bdmajora.impetus.core.ImpetusMixinRegistrar").getDeclaredMethod("mixinConfigs");
        method.setAccessible(true);
        return (List<String>) method.invoke(null);
    }

    private static Map<String, JsonObject> configs() throws Exception {
        Map<String, JsonObject> out = new TreeMap<>();
        for (String name : registered()) {
            try (InputStream in = Files.newInputStream(resources().resolve(name))) {
                out.put(name, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        return out;
    }

    // The folder the shipped resources sit in on the test classpath (the build's processed resources)
    private static Path resources() throws Exception {
        java.net.URL anchor = MixinConfigsTest.class.getResource("/mixins.impetus.json");
        assertNotNull(anchor, "mixins.impetus.json is not on the test classpath");
        return Path.of(anchor.toURI()).getParent();
    }

    private static ClassNode classNode(String className) {
        try (InputStream in = MixinConfigsTest.class.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class")) {
            return in == null ? null : read(in.readAllBytes());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static boolean hasMixinAnnotation(ClassNode node) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (list != null) {
                for (AnnotationNode annotation : list) {
                    if (MIXIN.equals(annotation.desc)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
