package com.bdmajora.compat;

import com.bdmajora.impetus.mixin.ImpetusVintageMixinPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// Keeps every Impetus mixin clear of the VintageFix 0.7.2 and FermiumMixins 1.0.15 injections, using CleanMix's merge rule.
class ForeignMixinConflictTest {
    // Injection points CleanMix lets into a method another mixin overwrote whatever the priorities.
    private static final Set<String> ALWAYS_ALLOWED_POINTS = Set.of("HEAD", "RETURN", "TAIL");

    // Overwrites Impetus keeps on purpose over a foreign overwrite, keyed mixin|owner|method.
    private static final Map<String, String> ALLOWED = Map.of(
            "com.bdmajora.impetus.mixin.core.terrain.BiomeColorHelperMixin|net.minecraft.world.biome.BiomeColorHelper|getColorAtPos",
            "Impetus' biome blend radius option needs this body; it runs at 1200 so it replaces VintageFix' allocation tweak cleanly");

    // Mixins removed for VintageFix and FermiumMixins that must not come back.
    private static final List<String> REMOVED = List.of(
            "com.bdmajora.coarctatio.mixin.forge.OreDictionaryMixin",
            "com.bdmajora.coarctatio.mixin.forge.GameDataMixin",
            "com.bdmajora.coarctatio.mixin.forge.JarDiscovererMixin",
            "com.bdmajora.coarctatio.mixin.client.SearchTreeMixin",
            "com.bdmajora.coarctatio.mixin.client.resources.FallbackResourceManagerMixin",
            "com.bdmajora.coarctatio.mixin.client.resources.FileResourcePackMixin",
            "com.bdmajora.coarctatio.mixin.client.resources.DefaultResourcePackMixin",
            "com.bdmajora.coarctatio.mixin.client.texture.StitcherMixin",
            "com.bdmajora.coarctatio.mixin.client.texture.TextureMapMixin",
            "com.bdmajora.coarctatio.mixin.client.model.dynamic.BlockModelShapesDynamicMixin",
            "com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelManagerDynamicMixin",
            "com.bdmajora.coarctatio.mixin.client.model.bake.ItemLayerFaceDataMixin",
            "com.bdmajora.coarctatio.mixin.client.model.CoarctatioBakedQuadMixin",
            "com.bdmajora.coarctatio.mixin.client.model.SimpleBakedModelMixin",
            "com.bdmajora.coarctatio.mixin.client.model.ModelLoaderMixin",
            "com.bdmajora.coarctatio.mixin.client.model.multipart.ConditionAndMixin",
            "com.bdmajora.coarctatio.mixin.state.StateImplementationHashMixin",
            "com.bdmajora.coarctatio.mixin.state.PropertyEnumHashMixin",
            "com.bdmajora.coarctatio.mixin.state.BlockStateContainerMixin",
            "com.bdmajora.coarctatio.mixin.util.ModelResourceLocationMixin",
            "com.bdmajora.coarctatio.mixin.world.TemplateManagerMixin",
            "com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load.ChunkCacheMixin",
            "com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load.PathNavigateMixin",
            "com.bdmajora.equilibrium.mixin.entity.xp_orb_merging.EntityXPOrbMixin",
            "com.bdmajora.equilibrium.mixin.chunk.no_validation.ChunkMixin",
            "com.bdmajora.extras.mixin.async.WorldEntitySpawnerParallelMixin");

    // One injection of a foreign mod read from the snapshot.
    record Fact(String mod, String owner, String method, String kind, String at, String atTarget, int priority, String source) {
    }

    // One injection point of an Impetus mixin method.
    record Point(String value, String target) {
    }

    // One injector or overwrite of an Impetus mixin.
    record Ours(String mixin, String owner, String method, String kind, List<Point> points, int priority) {
    }

    private static List<Fact> foreign;
    private static List<Ours> ours;

    @BeforeAll
    static void read() throws Exception {
        foreign = readFacts();
        ours = scanImpetus();
    }

    // Reads the snapshot taken from the two mods' sources.
    static List<Fact> readFacts() throws IOException {
        List<Fact> facts = new ArrayList<>();
        try (InputStream in = ForeignMixinConflictTest.class.getResourceAsStream("foreign-mixins.tsv")) {
            assertNotNull(in, "foreign-mixins.tsv");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] c = line.split("\t", -1);
                assertEquals(9, c.length, line);
                facts.add(new Fact(c[0], owner(c[1]), method(c[2]), c[3], c[4], c[5].trim(), Integer.parseInt(c[6]), c[8]));
            }
        }
        return facts;
    }

    // Normalises a class name so source, binary and internal spellings compare equal.
    static String owner(String name) {
        return name.replace('/', '.').replace('$', '.');
    }

    // Normalises a method selector to its bare name.
    static String method(String selector) {
        String name = selector.trim();
        if (name.startsWith("L") && name.contains(";")) {
            name = name.substring(name.indexOf(';') + 1);
        }
        int paren = name.indexOf('(');
        if (paren >= 0) {
            name = name.substring(0, paren);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }
        return name.endsWith("*") && name.length() > 1 ? name.substring(0, name.length() - 1) : name;
    }

    // Returns whether two selectors name the same method, a lone star matching any.
    static boolean sameMethod(String a, String b) {
        return a.equals(b) || "*".equals(a) || "*".equals(b);
    }

    // Returns every conflict between the foreign injections and ours that CleanMix would reject or silently drop.
    static List<String> conflicts(List<Fact> facts, List<Ours> mine, Map<String, String> allowed) {
        List<String> found = new ArrayList<>();
        for (Ours o : mine) {
            for (Fact f : facts) {
                if (!f.owner().equals(o.owner()) || !sameMethod(f.method(), o.method())) {
                    continue;
                }
                String where = o.mixin() + " " + o.kind() + " " + o.owner() + "." + o.method() + " vs " + f.mod() + " " + f.kind() + " (" + f.source() + ")";
                boolean ourOverwrite = "Overwrite".equals(o.kind());
                boolean theirOverwrite = "Overwrite".equals(f.kind());
                if (ourOverwrite && theirOverwrite) {
                    if (!allowed.containsKey(o.mixin() + "|" + o.owner() + "|" + o.method())) {
                        found.add("both overwrite: " + where);
                    }
                } else if (theirOverwrite) {
                    for (Point p : o.points()) {
                        if (!ALWAYS_ALLOWED_POINTS.contains(p.value()) && o.priority() <= f.priority()) {
                            found.add("injects at " + p.value() + " into their overwrite: " + where);
                        }
                    }
                } else if (ourOverwrite) {
                    if (!ALWAYS_ALLOWED_POINTS.contains(f.at()) && f.priority() <= o.priority()) {
                        found.add("their " + f.at() + " injection cannot enter our overwrite: " + where);
                    }
                } else if ("Redirect".equals(o.kind()) && "Redirect".equals(f.kind())) {
                    for (Point p : o.points()) {
                        if (p.value().equals(f.at()) && p.target().equals(f.atTarget())) {
                            found.add("same redirect " + f.atTarget() + ": " + where);
                        }
                    }
                }
            }
        }
        return found;
    }

    // Reads every compiled Impetus mixin from the main class output.
    static List<Ours> scanImpetus() throws IOException, URISyntaxException {
        Path root = Paths.get(ImpetusVintageMixinPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Ours> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("com/bdmajora"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                found.addAll(parse(Files.readAllBytes(file)));
            }
        }
        return found;
    }

    // Returns the injectors of one class file, or nothing when it is not a mixin.
    static List<Ours> parse(byte[] bytes) {
        Map<String, Object> mixin = new HashMap<>();
        List<Map.Entry<String, Map<String, Object>>> methods = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return "Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor) ? collector(mixin) : null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        String kind = injectorKind(annotation);
                        if (kind == null) {
                            return null;
                        }
                        Map<String, Object> values = new HashMap<>();
                        values.put("@kind", kind);
                        values.put("@name", name);
                        methods.add(Map.entry(name, values));
                        return collector(values);
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        if (mixin.isEmpty() && methods.isEmpty()) {
            return List.of();
        }
        String className = new ClassReader(bytes).getClassName().replace('/', '.');
        List<String> owners = new ArrayList<>();
        for (Object type : list(mixin.get("value"))) {
            owners.add(owner(((Type) type).getClassName()));
        }
        for (Object target : list(mixin.get("targets"))) {
            owners.add(owner((String) target));
        }
        int priority = mixin.get("priority") instanceof Integer p ? p : 1000;
        List<Ours> found = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : methods) {
            Map<String, Object> values = entry.getValue();
            String kind = (String) values.get("@kind");
            List<String> selectors = new ArrayList<>();
            if ("Overwrite".equals(kind)) {
                selectors.add(entry.getKey());
            } else {
                for (Object selector : list(values.get("method"))) {
                    selectors.add(method((String) selector));
                }
            }
            List<Point> points = new ArrayList<>();
            for (Object at : list(values.get("at"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> a = (Map<String, Object>) at;
                points.add(new Point((String) a.getOrDefault("value", ""), String.valueOf(a.getOrDefault("target", "")).trim()));
            }
            if (points.isEmpty() && "ModifyConstant".equals(kind)) {
                points.add(new Point("CONSTANT", ""));
            }
            for (String target : owners) {
                for (String selector : selectors) {
                    found.add(new Ours(className, target, selector, kind, List.copyOf(points), priority));
                }
            }
        }
        return found;
    }

    // Returns the injector kind of an annotation descriptor or null for anything else.
    static String injectorKind(String descriptor) {
        if ("Lorg/spongepowered/asm/mixin/Overwrite;".equals(descriptor)) {
            return "Overwrite";
        }
        if (!descriptor.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !descriptor.startsWith("Lcom/llamalad7/mixinextras/injector/")) {
            return null;
        }
        String simple = descriptor.substring(descriptor.lastIndexOf('/') + 1, descriptor.length() - 1);
        return switch (simple) {
            case "Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant", "WrapOperation",
                 "WrapWithCondition", "ModifyExpressionValue", "ModifyReturnValue", "ModifyReceiver" -> simple;
            default -> null;
        };
    }

    // Returns a value as a list, wrapping a single value.
    static List<Object> list(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return List.of(value);
    }

    // Collects an annotation's values into the map, nested annotations as maps and arrays as lists.
    static AnnotationVisitor collector(Map<String, Object> into) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                into.put(name, value);
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                into.put(name, value);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                Map<String, Object> nested = new HashMap<>();
                into.put(name, nested);
                return collector(nested);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                List<Object> items = new ArrayList<>();
                into.put(name, items);
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String ignored, Object value) {
                        items.add(value);
                    }

                    @Override
                    public void visitEnum(String ignored, String descriptor, String value) {
                        items.add(value);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                        Map<String, Object> nested = new HashMap<>();
                        items.add(nested);
                        return collector(nested);
                    }
                };
            }
        };
    }

    private static Fact fact(String mod, String owner, String method, String kind, String at, String target, int priority) {
        return new Fact(mod, owner(owner), method(method), kind, at, target, priority, "synthetic");
    }

    private static Ours mine(String owner, String method, String kind, int priority, Point... points) {
        return new Ours("test.Mixin", owner(owner), method(method), kind, List.of(points), priority);
    }

    @Test
    void theSnapshotIsReadableAndHoldsTheInjectionsThatCrashedBefore() {
        assertTrue(foreign.size() > 400, "rows: " + foreign.size());
        assertTrue(foreign.stream().anyMatch(f -> f.mod().equals("VintageFix")));
        assertTrue(foreign.stream().anyMatch(f -> f.mod().equals("FermiumMixins")));
        // The redirect behind the 11-21 ore dictionary crash
        assertTrue(foreign.stream().anyMatch(f -> f.owner().equals("net.minecraftforge.oredict.OreDictionary")
                && f.method().equals("registerOreImpl") && f.kind().equals("Redirect") && f.at().equals("INVOKE")));
        // The two redirects the 11-21 VintageFix log reported as dropped
        assertTrue(foreign.stream().anyMatch(f -> f.owner().equals("net.minecraft.client.resources.FallbackResourceManager")
                && f.method().equals("*") && f.at().equals("NEW")));
        assertTrue(foreign.stream().anyMatch(f -> f.owner().equals("net.minecraftforge.fml.common.discovery.JarDiscoverer")
                && f.kind().equals("Redirect")));
        // Priorities other than the default are kept
        assertTrue(foreign.stream().anyMatch(f -> f.owner().equals("net.minecraft.world.ChunkCache")
                && f.method().equals("getLightForExt") && f.priority() == 500));
        assertTrue(foreign.stream().anyMatch(f -> f.owner().equals("net.minecraft.block.properties.PropertyEnum") && f.priority() == 800));
        // Inner classes are compared with dots
        assertTrue(foreign.stream().anyMatch(f -> f.owner().equals("net.minecraft.block.state.BlockStateContainer.StateImplementation")));
        assertTrue(foreign.stream().noneMatch(f -> f.owner().contains("$") || f.owner().contains("/")));
    }

    @Test
    void theScanReadsEveryImpetusMixinWithItsPriorityAndPoints() {
        assertTrue(ours.size() > 400, "injectors: " + ours.size());
        Ours glint = ours.stream().filter(o -> o.mixin().endsWith("BlockModelShapesMixin") && o.method().equals("getModelForState")).findFirst().orElseThrow();
        assertEquals("Inject", glint.kind());
        assertEquals(List.of(new Point("HEAD", "")), glint.points());
        Ours mipmaps = ours.stream().filter(o -> o.mixin().endsWith("mipmaps.TextureUtilMixin") && o.kind().equals("Overwrite")).findFirst().orElseThrow();
        assertEquals(900, mipmaps.priority());
        assertEquals("net.minecraft.client.renderer.texture.TextureUtil", mipmaps.owner());
        Ours biome = ours.stream().filter(o -> o.mixin().endsWith("terrain.BiomeColorHelperMixin")).findFirst().orElseThrow();
        assertEquals(1200, biome.priority());
        // Redirect targets survive the scan
        assertTrue(ours.stream().anyMatch(o -> o.kind().equals("Redirect") && o.points().stream().anyMatch(p -> !p.target().isEmpty())));
        // MixinExtras injectors are recognised too
        assertTrue(ours.stream().anyMatch(o -> o.kind().equals("WrapOperation")));
        // String targets resolve to the same spelling as class targets
        assertTrue(ours.stream().noneMatch(o -> o.owner().contains("/") || o.owner().contains("$")));
    }

    @Test
    void noImpetusMixinConflictsWithVintageFixOrFermiumMixins() {
        List<String> found = conflicts(foreign, ours, ALLOWED);
        assertEquals(List.of(), found, String.join("\n", found));
    }

    @Test
    void everyAllowedOverlapStillExistsAndIsTheOnlyThingItHides() {
        for (String key : ALLOWED.keySet()) {
            String[] parts = key.split("\\|");
            assertTrue(ours.stream().anyMatch(o -> o.mixin().equals(parts[0]) && o.owner().equals(parts[1]) && o.method().equals(parts[2])),
                    "stale allowance " + key);
            assertTrue(foreign.stream().anyMatch(f -> f.owner().equals(parts[1]) && f.method().equals(parts[2]) && f.kind().equals("Overwrite")),
                    "allowance with nothing to allow " + key);
            Ours kept = ours.stream().filter(o -> o.mixin().equals(parts[0])).findFirst().orElseThrow();
            int theirs = foreign.stream().filter(f -> f.owner().equals(parts[1]) && f.method().equals(parts[2])).mapToInt(Fact::priority).max().orElseThrow();
            // Ours must outrank theirs or Mixin skips ours instead
            assertTrue(kept.priority() > theirs, key);
        }
        // Without the allowance the same pair is reported
        assertFalse(conflicts(foreign, ours, Map.of()).isEmpty());
    }

    @Test
    void theRemovedMixinsStayRemoved() throws Exception {
        Set<String> listed = new HashSet<>();
        for (String config : List.of("mixins.coarctatio.json", "mixins.equilibrium.json", "mixins.extras.json", "mixins.fulgor.json",
                "mixins.impetus.json", "mixins.umbra.json", "mixins.dynamiclights.json", "mixins.linuxextras.json")) {
            try (InputStream in = ForeignMixinConflictTest.class.getResourceAsStream("/" + config)) {
                assertNotNull(in, config);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                String pkg = json.get("package").getAsString();
                for (String section : List.of("mixins", "client", "server")) {
                    if (json.has(section)) {
                        JsonArray array = json.getAsJsonArray(section);
                        array.forEach(element -> listed.add(pkg + "." + element.getAsString()));
                    }
                }
            }
        }
        for (String removed : REMOVED) {
            assertFalse(listed.contains(removed), removed + " is listed again");
            assertNull(ForeignMixinConflictTest.class.getResource("/" + removed.replace('.', '/') + ".class"), removed + " is back on the class path");
        }
        // Every listed mixin is a real class, so nothing listed was half removed
        for (String mixin : listed) {
            assertNotNull(ForeignMixinConflictTest.class.getResource("/" + mixin.replace('.', '/') + ".class"), mixin);
        }
    }

    @Test
    void theRuleReproducesTheOreDictionaryCrash() {
        Fact redirect = fact("FermiumMixins", "net/minecraftforge/oredict/OreDictionary", "registerOreImpl", "Redirect", "INVOKE",
                "Lnet/minecraftforge/fml/common/FMLLog;bigWarning(Ljava/lang/String;[Ljava/lang/Object;)V", 1000);
        Ours overwrite = mine("net.minecraftforge.oredict.OreDictionary", "registerOreImpl", "Overwrite", 1000);
        assertEquals(1, conflicts(List.of(redirect), List.of(overwrite), Map.of()).size());
        // A lower priority overwrite lets their injector in, as CleanMix does
        assertEquals(List.of(), conflicts(List.of(redirect), List.of(mine("net.minecraftforge.oredict.OreDictionary", "registerOreImpl", "Overwrite", 999)), Map.of()));
        // A higher one still breaks it
        assertEquals(1, conflicts(List.of(redirect), List.of(mine("net.minecraftforge.oredict.OreDictionary", "registerOreImpl", "Overwrite", 1001)), Map.of()).size());
    }

    @Test
    void headReturnAndTailAlwaysEnterAnOverwrite() {
        Fact overwrite = fact("VintageFix", "net.minecraft.client.renderer.block.model.ModelManager", "onResourceManagerReload", "Overwrite", "", "", 1000);
        for (String point : List.of("HEAD", "RETURN", "TAIL")) {
            assertEquals(List.of(), conflicts(List.of(overwrite),
                    List.of(mine("net.minecraft.client.renderer.block.model.ModelManager", "onResourceManagerReload", "Inject", 1000, new Point(point, ""))), Map.of()), point);
            // And the other way round
            Fact theirs = fact("VintageFix", "a.B", "m", "Inject", point, "", 1000);
            assertEquals(List.of(), conflicts(List.of(theirs), List.of(mine("a.B", "m", "Overwrite", 1000)), Map.of()), point);
        }
        // Every other point needs the higher priority
        for (String point : List.of("INVOKE", "FIELD", "NEW", "CONSTANT", "INVOKE_ASSIGN", "LOAD", "STORE", "JUMP")) {
            assertEquals(1, conflicts(List.of(overwrite),
                    List.of(mine("net.minecraft.client.renderer.block.model.ModelManager", "onResourceManagerReload", "Inject", 1000, new Point(point, "x"))), Map.of()).size(), point);
            assertEquals(List.of(), conflicts(List.of(overwrite),
                    List.of(mine("net.minecraft.client.renderer.block.model.ModelManager", "onResourceManagerReload", "Inject", 1001, new Point(point, "x"))), Map.of()), point);
        }
    }

    @Test
    void oneMethodWithSeveralPointsReportsEachBadPoint() {
        Fact overwrite = fact("VintageFix", "a.B", "m", "Overwrite", "", "", 1000);
        Ours both = mine("a.B", "m", "Inject", 1000, new Point("HEAD", ""), new Point("INVOKE", "x"), new Point("FIELD", "y"));
        assertEquals(2, conflicts(List.of(overwrite), List.of(both), Map.of()).size());
    }

    @Test
    void twoOverwritesConflictUnlessAllowedWhateverThePriorities() {
        Fact theirs = fact("VintageFix", "a.B", "m", "Overwrite", "", "", 500);
        for (int priority : new int[] {1, 500, 1000, 5000}) {
            assertEquals(1, conflicts(List.of(theirs), List.of(mine("a.B", "m", "Overwrite", priority)), Map.of()).size(), "priority " + priority);
        }
        assertEquals(List.of(), conflicts(List.of(theirs), List.of(mine("a.B", "m", "Overwrite", 1000)), Map.of("test.Mixin|a.B|m", "kept")));
        // The allowance is exact
        assertEquals(1, conflicts(List.of(theirs), List.of(mine("a.B", "m", "Overwrite", 1000)), Map.of("test.Mixin|a.B|other", "kept")).size());
    }

    @Test
    void redirectsCollideOnlyOnTheSameCall() {
        Fact theirs = fact("VintageFix", "a.B", "m", "Redirect", "INVOKE", "La/C;run()V", 1000);
        assertEquals(1, conflicts(List.of(theirs), List.of(mine("a.B", "m", "Redirect", 1000, new Point("INVOKE", "La/C;run()V"))), Map.of()).size());
        assertEquals(List.of(), conflicts(List.of(theirs), List.of(mine("a.B", "m", "Redirect", 1000, new Point("INVOKE", "La/C;walk()V"))), Map.of()));
        assertEquals(List.of(), conflicts(List.of(theirs), List.of(mine("a.B", "m", "Redirect", 1000, new Point("FIELD", "La/C;run()V"))), Map.of()));
        // Wrapping the same call chains with their redirect
        assertEquals(List.of(), conflicts(List.of(theirs), List.of(mine("a.B", "m", "WrapOperation", 1000, new Point("INVOKE", "La/C;run()V"))), Map.of()));
        // Two plain injectors never collide
        assertEquals(List.of(), conflicts(List.of(fact("VintageFix", "a.B", "m", "Inject", "INVOKE", "x", 1000)),
                List.of(mine("a.B", "m", "Inject", 1000, new Point("INVOKE", "x"))), Map.of()));
    }

    @Test
    void ownersAndSelectorsAreMatchedLooselyButExactly() {
        Fact wildcard = fact("VintageFix", "net/minecraft/client/resources/FallbackResourceManager", "*", "Redirect", "NEW",
                "(Ljava/lang/String;)Ljava/io/FileNotFoundException;", 1000);
        // A star in their selector reaches every method of the class
        assertEquals(1, conflicts(List.of(wildcard), List.of(mine("net.minecraft.client.resources.FallbackResourceManager", "getResource", "Redirect", 1000,
                new Point("NEW", "(Ljava/lang/String;)Ljava/io/FileNotFoundException;"))), Map.of()).size());
        // Another class is never matched
        assertEquals(List.of(), conflicts(List.of(wildcard), List.of(mine("net.minecraft.client.resources.FallbackResourceManagerX", "getResource", "Redirect", 1000,
                new Point("NEW", "(Ljava/lang/String;)Ljava/io/FileNotFoundException;"))), Map.of()));
        // Descriptors, owners and trailing stars in selectors are ignored
        assertEquals("getLightForExt", method("getLightForExt(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)I"));
        assertEquals("tick", method("Lnet/minecraft/server/MinecraftServer;tick()V"));
        assertEquals("<init>", method("<init>*"));
        assertEquals("field", method("field:I"));
        assertEquals("*", method("*"));
        assertEquals("plain", method("  plain  "));
        // Binary, internal and source spellings of an inner class agree
        assertEquals(owner("net.minecraft.block.state.BlockStateContainer.StateImplementation"), owner("net/minecraft/block/state/BlockStateContainer$StateImplementation"));
        assertTrue(sameMethod("*", "x") && sameMethod("x", "*") && sameMethod("x", "x"));
        assertFalse(sameMethod("x", "y"));
    }

    @Test
    void theParserIgnoresPlainClassesAndUnknownAnnotations() {
        assertNull(injectorKind("Lorg/spongepowered/asm/mixin/Shadow;"));
        assertNull(injectorKind("Lorg/spongepowered/asm/mixin/injection/At;"));
        assertNull(injectorKind("Lcom/llamalad7/mixinextras/sugar/Local;"));
        assertEquals("Overwrite", injectorKind("Lorg/spongepowered/asm/mixin/Overwrite;"));
        assertEquals("WrapOperation", injectorKind("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"));
        assertEquals("WrapWithCondition", injectorKind("Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;"));
        assertEquals("ModifyReturnValue", injectorKind("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"));
        assertEquals(List.of(), list(null));
        assertEquals(List.of("a"), list("a"));
        assertEquals(List.of("a", "b"), list(List.of("a", "b")));
        // A class that is no mixin yields nothing
        try (InputStream in = ForeignMixinConflictTest.class.getResourceAsStream("ForeignMixinConflictTest.class")) {
            assertEquals(List.of(), parse(in.readAllBytes()));
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void everyOverlapLeftIsOneTheRuleAccepts() {
        // Lists the remaining shared methods so a reviewer can see what still runs side by side
        Map<String, List<String>> shared = new LinkedHashMap<>();
        for (Ours o : ours) {
            for (Fact f : foreign) {
                if (f.owner().equals(o.owner()) && sameMethod(f.method(), o.method())) {
                    shared.computeIfAbsent(o.owner() + "." + o.method(), k -> new ArrayList<>()).add(o.kind() + "/" + f.mod() + " " + f.kind());
                }
            }
        }
        assertFalse(shared.isEmpty());
        // The hooks this change kept on purpose are among them
        assertTrue(shared.containsKey("net.minecraft.client.renderer.block.model.ModelManager.onResourceManagerReload"), shared.keySet().toString());
        assertTrue(shared.containsKey("net.minecraft.world.ChunkCache.getLightForExt"), shared.keySet().toString());
        // None of the removed overlaps remain
        assertFalse(shared.containsKey("net.minecraftforge.oredict.OreDictionary.registerOreImpl"));
        assertFalse(shared.keySet().stream().anyMatch(k -> k.startsWith("net.minecraft.client.resources.FallbackResourceManager.")));
        assertFalse(shared.keySet().stream().anyMatch(k -> k.startsWith("net.minecraftforge.fml.common.discovery.JarDiscoverer.")));
        assertFalse(shared.containsKey("net.minecraft.block.state.BlockStateContainer.StateImplementation.hashCode"));
        assertFalse(shared.containsKey("net.minecraft.world.chunk.Chunk.getBlockState"));
    }
}
