package com.bdmajora.impetus.umbra.terrain.mesh;

import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.include.IncludeProcessor;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.terrain.ImpetusTerrainTransformer;
import com.bdmajora.impetus.umbra.terrain.ModernPackTransformer;
import com.bdmajora.impetus.umbra.terrain.VanillaNameTransformer;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.UnsupportedProgramException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Porter of shader packs to mesh shader terrain.
public final class NvidiumPackPorter {
    // Name of the file that records a port.
    public static final String MANIFEST = "nvidium_port.txt";

    // Engine templates included by ported programs.
    private static final String[] TEMPLATES = {"impetus:mesh/pack/scene.glsl", "impetus:mesh/cull.glsl", "impetus:mesh/pack/fetch.glsl",
            "impetus:mesh/pack/emit.glsl", "impetus:mesh/pack/opaque_main.glsl", "impetus:mesh/pack/translucent_main.glsl"};

    // Programs that get a mesh stage.
    private static final ProgramId[] ROLES = {ProgramId.TerrainCutoutMip, ProgramId.Water, ProgramId.ShadowCutout, ProgramId.ShadowWater};
    // Binary extensions.
    private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "mcmeta", "dat", "bin", "raw");

    // Prevents instantiation
    private NvidiumPackPorter() {
    }

    // Data of the result.
    public record Result(List<String> ported, List<String> skipped, boolean reused) {
        // Creates a result from its ported and skipped.
        public Result(List<String> ported, List<String> skipped) {
            this(ported, skipped, false);
        }
    }

    // Returns the source names of the ported programs.
    static Set<String> candidatePrograms() {
        Set<String> names = new LinkedHashSet<>();
        for (ProgramId role : ROLES) {
            for (ProgramId id = role; id != null; id = id.getFallback()) {
                names.add(id.getSourceName());
            }
        }
        return names;
    }

    // Returns the sources with mesh stages added.
    public static Map<AbsolutePackPath, String> portSources(Map<AbsolutePackPath, String> sources, List<String> ported, List<String> skipped) {
        IncludeProcessor includes = new IncludeProcessor(sources);
        Map<AbsolutePackPath, String> output = new TreeMap<>(Comparator.comparing(AbsolutePackPath::getPathString));

        for (String folder : folders(sources)) {
            for (String name : candidatePrograms()) {
                String label = folder.isEmpty() ? name : folder.substring(1) + "/" + name;
                if (!sources.containsKey(path(folder, name, "vsh")) && !sources.containsKey(path(folder, name, "fsh"))) {
                    continue;
                }
                AbsolutePackPath vertex = locate(sources, folder, name, "vsh");
                AbsolutePackPath fragment = locate(sources, folder, name, "fsh");
                if (vertex == null || fragment == null) {
                    continue;
                }
                if (locate(sources, folder, name, "gsh") != null || locate(sources, folder, name, "tcs") != null
                        || locate(sources, folder, name, "tes") != null) {
                    skipped.add(label + ": geometry or tessellation stage");
                    continue;
                }
                try {
                    MeshShaderTranslator.Port port = portProgram(name,
                            String.join("\n", includes.process(vertex)), String.join("\n", includes.process(fragment)));
                    output.put(path(folder, name, ShaderPack.MESH_EXTENSION), port.mesh());
                    output.put(path(folder, name, ShaderPack.MESH_FRAGMENT_EXTENSION), port.fragment());
                    ported.add(label);
                } catch (UnsupportedProgramException | IllegalStateException e) {
                    skipped.add(label + ": " + e.getMessage());
                }
            }
        }
        return output;
    }

    // Translates one program to a mesh stage.
    static MeshShaderTranslator.Port portProgram(String name, String vertex, String fragment) {
        String vsh = VanillaNameTransformer.transform(vertex);
        String fsh = VanillaNameTransformer.transform(fragment);
        boolean shadow = name.startsWith("shadow");
        String bridged = ModernPackTransformer.isModernSource(fsh)
                ? ImpetusTerrainTransformer.transformVertexShaderModern(vsh, shadow)
                : ImpetusTerrainTransformer.transformVertexShader(vsh, shadow);
        return MeshShaderTranslator.port(bridged, fsh);
    }

    // Ports a pack unless its recorded port is current.
    public static Result ensure(Path source, Path target) throws IOException {
        String fingerprint = fingerprint(source);
        Properties manifest = readManifest(target);
        if (manifest != null && fingerprint.equals(manifest.getProperty("fingerprint"))) {
            List<String> ported = new ArrayList<>();
            for (String name : manifest.getProperty("ported", "").split(",")) {
                if (!name.isBlank()) {
                    ported.add(name.trim());
                }
            }
            List<String> skipped = new ArrayList<>();
            for (int i = 0; manifest.containsKey("skipped." + i); i++) {
                skipped.add(manifest.getProperty("skipped." + i));
            }
            return new Result(ported, skipped, true);
        }
        return port(source, target, fingerprint);
    }

    // Ports a pack into a target folder.
    public static Result port(Path source, Path target) throws IOException {
        return port(source, target, fingerprint(source));
    }

    // Ports a pack into a target folder with a fingerprint.
    private static Result port(Path source, Path target, String fingerprint) throws IOException {
        Map<String, byte[]> files = readPack(source);
        if (files.keySet().stream().noneMatch(file -> file.startsWith("shaders/"))) {
            throw new IOException("No shaders/ folder in " + source.getFileName());
        }
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target.resolve(MANIFEST))) {
                throw new IOException(target.getFileName() + " already exists and was not made by a port; move it aside first");
            }
            deleteTree(target);
        }

        Map<AbsolutePackPath, String> sources = new HashMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().startsWith("shaders/") && !BINARY_EXTENSIONS.contains(extension(file.getKey()))) {
                sources.put(AbsolutePackPath.fromAbsolutePath(file.getKey().substring("shaders".length())),
                        new String(file.getValue(), StandardCharsets.UTF_8));
            }
        }

        List<String> ported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<AbsolutePackPath, String> stages = portSources(sources, ported, skipped);

        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path destination = target.resolve(file.getKey());
            Files.createDirectories(destination.getParent());
            Files.write(destination, file.getValue());
        }
        for (Map.Entry<AbsolutePackPath, String> stage : stages.entrySet()) {
            Path destination = target.resolve("shaders" + stage.getKey().getPathString());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, stage.getValue(), StandardCharsets.UTF_8);
        }

        Files.writeString(target.resolve(MANIFEST), manifest(source, fingerprint, ported, skipped), StandardCharsets.UTF_8);
        return new Result(ported, skipped);
    }

    // Returns the manifest text of a port.
    private static String manifest(Path source, String fingerprint, List<String> ported, List<String> skipped) {
        StringBuilder out = new StringBuilder("# Nvidium port of a shader pack, written by Impetus and remade whenever the pack changes; delete this folder to force a fresh one\n");
        out.append("source=").append(source.getFileName()).append('\n');
        out.append("format=").append(MeshShaderTranslator.PORT_FORMAT).append('\n');
        out.append("fingerprint=").append(fingerprint).append('\n');
        out.append("date=").append(Instant.now()).append('\n');
        out.append("ported=").append(String.join(", ", ported)).append('\n');
        for (int i = 0; i < skipped.size(); i++) {
            out.append("skipped.").append(i).append('=').append(skipped.get(i)).append('\n');
        }
        return out.toString();
    }

    // Reads the manifest of a port or returns null.
    private static Properties readManifest(Path target) throws IOException {
        Path file = target.resolve(MANIFEST);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties manifest = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        return manifest;
    }

    // Returns a hash of the pack files.
    static String fingerprint(Path source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        digest.update(("format " + MeshShaderTranslator.PORT_FORMAT + "\n").getBytes(StandardCharsets.UTF_8));
        for (String template : TEMPLATES) {
            digest.update(String.valueOf(ShaderLoader.getShaderSource(template)).getBytes(StandardCharsets.UTF_8));
        }
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(source)) {
            try (Stream<Path> walk = Files.walk(source)) {
                walk.filter(Files::isRegularFile).sorted().forEach(files::add);
            }
        } else {
            files.add(source);
        }
        for (Path file : files) {
            String entry = source.relativize(file).toString().replace('\\', '/') + '\0' + Files.size(file) + '\0'
                    + Files.getLastModifiedTime(file).toMillis() + '\n';
            digest.update(entry.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // Returns the program folders of a pack.
    private static Set<String> folders(Map<AbsolutePackPath, String> sources) {
        Set<String> folders = new TreeSet<>();
        folders.add("");
        for (AbsolutePackPath path : sources.keySet()) {
            String file = path.getPathString();
            int slash = file.indexOf('/', 1);
            if (slash > 1) {
                folders.add(file.substring(0, slash));
            }
        }
        return folders;
    }

    // Returns the path of a stage or null.
    private static AbsolutePackPath locate(Map<AbsolutePackPath, String> sources, String folder, String name, String extension) {
        AbsolutePackPath inFolder = path(folder, name, extension);
        if (sources.containsKey(inFolder)) {
            return inFolder;
        }
        AbsolutePackPath atRoot = path("", name, extension);
        return sources.containsKey(atRoot) ? atRoot : null;
    }

    // Returns the path of a file in a folder.
    private static AbsolutePackPath path(String folder, String name, String extension) {
        return AbsolutePackPath.fromAbsolutePath(folder + "/" + name + "." + extension);
    }

    // Reads every file of a pack folder or zip.
    static Map<String, byte[]> readPack(Path source) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    files.put(source.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                    return FileVisitResult.CONTINUE;
                }
            });
            return files;
        }

        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName().replace('\\', '/'), zip.readAllBytes());
                }
            }
        }
        String prefix = "";
        for (String name : entries.keySet()) {
            int shaders = name.indexOf("shaders/");
            if (shaders >= 0 && (shaders == 0 || name.charAt(shaders - 1) == '/')) {
                prefix = name.substring(0, shaders);
                break;
            }
        }
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                files.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return files;
    }

    // Returns the extension of a path.
    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // Deletes a folder and its contents.
    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
