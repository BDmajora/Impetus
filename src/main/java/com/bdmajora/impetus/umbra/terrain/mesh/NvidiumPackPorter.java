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

// Ports a shader pack for the Nvidium backend ahead of time: the whole pack is copied into nvidium_shaderpacks/ and every terrain-family program gets its mesh-shader stages written beside its OpenGL ones, flattened and translated, so loading the port compiles them like any other pack file with no translation at all; ensure() keeps a port in step with its pack, so it is only redone when the pack or the port's own templates change
public final class NvidiumPackPorter {
    // Written at the port's root, outside shaders/, so the loader never reads it; also how a re-port knows the folder is its own to replace
    public static final String MANIFEST = "nvidium_port.txt";

    // Every template a ported mesh stage inlines; a change to any of them makes existing ports stale
    private static final String[] TEMPLATES = {"impetus:mesh/pack/scene.glsl", "impetus:mesh/cull.glsl", "impetus:mesh/pack/fetch.glsl",
            "impetus:mesh/pack/emit.glsl", "impetus:mesh/pack/opaque_main.glsl", "impetus:mesh/pack/translucent_main.glsl"};

    // The phases the mesh backend draws, whose fallback chains name every program a port may need
    private static final ProgramId[] ROLES = {ProgramId.TerrainCutoutMip, ProgramId.Water, ProgramId.ShadowCutout, ProgramId.ShadowWater};
    // Read as bytes and never as source, as ShaderPackLoader does
    private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "mcmeta", "dat", "bin", "raw");

    private NvidiumPackPorter() {
    }

    // What a port holds: the programs it translated and, for each it could not, why; reused when ensure() found it already up to date
    public record Result(List<String> ported, List<String> skipped, boolean reused) {
        public Result(List<String> ported, List<String> skipped) {
            this(ported, skipped, false);
        }
    }

    // Every program name a mesh phase may resolve to, nearest first
    static Set<String> candidatePrograms() {
        Set<String> names = new LinkedHashSet<>();
        for (ProgramId role : ROLES) {
            for (ProgramId id = role; id != null; id = id.getFallback()) {
                names.add(id.getSourceName());
            }
        }
        return names;
    }

    // The ported stage files for a pack's raw sources, keyed where they belong: beside each program, in each folder that ships one of its stages, paired the way ShaderPack's stage lookup pairs them (the folder first, then the root)
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

    // One program through the same bridge the OpenGL path builds, then into mesh form; the fragment stage only gets the modern-name rewrite here, since its own bridge depends on the draw buffers the options pick
    static MeshShaderTranslator.Port portProgram(String name, String vertex, String fragment) {
        String vsh = VanillaNameTransformer.transform(vertex);
        String fsh = VanillaNameTransformer.transform(fragment);
        boolean shadow = name.startsWith("shadow");
        String bridged = ModernPackTransformer.isModernSource(fsh)
                ? ImpetusTerrainTransformer.transformVertexShaderModern(vsh, shadow)
                : ImpetusTerrainTransformer.transformVertexShader(vsh, shadow);
        return MeshShaderTranslator.port(bridged, fsh);
    }

    // The port at target when it was made from source as it is now, by this port format and these templates; otherwise a fresh port
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

    // Copies the pack at source (folder or zip) into target and writes the ported stages; refuses a target it did not make, and a re-port replaces its own earlier one whole
    public static Result port(Path source, Path target) throws IOException {
        return port(source, target, fingerprint(source));
    }

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

    // The manifest: where the port came from, what it was made from, and what was and was not ported
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

    // The manifest a port at target carries, or null when there is none to read
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

    // What a port is made from: the port format, the inlined templates, and every file of the pack by path, size and modification time (a zip as one file)
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

    // The folders that can hold programs: the root ("") and every top-level folder of shaders/, as /name
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

    // A stage file in a folder, or at the root when the folder lacks it
    private static AbsolutePackPath locate(Map<AbsolutePackPath, String> sources, String folder, String name, String extension) {
        AbsolutePackPath inFolder = path(folder, name, extension);
        if (sources.containsKey(inFolder)) {
            return inFolder;
        }
        AbsolutePackPath atRoot = path("", name, extension);
        return sources.containsKey(atRoot) ? atRoot : null;
    }

    private static AbsolutePackPath path(String folder, String name, String extension) {
        return AbsolutePackPath.fromAbsolutePath(folder + "/" + name + "." + extension);
    }

    // Every file of a pack, keyed relative to its root with shaders/ on top: a folder as it is, a zip with any single wrapping folder stripped
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

    // The lower-case extension of a path, or "" for none
    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // Deletes a folder and everything in it, deepest first
    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
