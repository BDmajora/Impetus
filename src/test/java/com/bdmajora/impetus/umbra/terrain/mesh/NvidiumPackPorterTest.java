package com.bdmajora.impetus.umbra.terrain.mesh;

import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPackLoader;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NvidiumPackPorterTest {
    static final String VERTEX = String.join("\n",
            "#version 120",
            "#include \"/lib/common.glsl\"",
            "varying vec2 texcoord;",
            "void main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy * SCALE; }");
    static final String FRAGMENT = String.join("\n",
            "#version 120",
            "varying vec2 texcoord;",
            "uniform sampler2D texture;",
            "void main() { gl_FragData[0] = texture2D(texture, texcoord); }");
    static final String COMMON = "#define SCALE 1.0 // [0.5 1.0 2.0]\n";

    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    // A pack with terrain, water and shadow programs, its include, and whatever else a test adds
    static Map<AbsolutePackPath, String> pack(Map<String, String> extra) {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(path("/lib/common.glsl"), COMMON);
        for (String program : new String[]{"gbuffers_terrain", "gbuffers_water", "shadow"}) {
            sources.put(path("/" + program + ".vsh"), VERTEX);
            sources.put(path("/" + program + ".fsh"), FRAGMENT);
        }
        extra.forEach((file, text) -> sources.put(path(file), text));
        return sources;
    }

    @Test
    void everyTerrainFamilyProgramGetsItsStagesBesideItInEveryFolder() {
        Map<AbsolutePackPath, String> sources = pack(Map.of(
                // The Nether ships only its own fragment stage, which pairs with the root's vertex stage as the loader pairs them
                "/world-1/gbuffers_terrain.fsh", FRAGMENT.replace("texcoord);", "texcoord) * 0.5;"),
                "/gbuffers_basic.vsh", VERTEX,
                "/gbuffers_basic.fsh", FRAGMENT,
                "/gbuffers_entities.vsh", VERTEX,
                "/gbuffers_entities.fsh", FRAGMENT));
        List<String> ported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<AbsolutePackPath, String> stages = NvidiumPackPorter.portSources(sources, ported, skipped);

        assertEquals(List.of("gbuffers_terrain", "gbuffers_basic", "gbuffers_water", "shadow", "world-1/gbuffers_terrain"), ported);
        assertTrue(skipped.isEmpty());
        assertTrue(stages.containsKey(path("/gbuffers_terrain.mesh")));
        assertTrue(stages.containsKey(path("/gbuffers_terrain.mesh.fsh")));
        assertTrue(stages.containsKey(path("/world-1/gbuffers_terrain.mesh.fsh")));
        assertTrue(stages.get(path("/world-1/gbuffers_terrain.mesh.fsh")).contains("* 0.5"));
        // Programs no mesh phase draws are left alone
        assertFalse(stages.containsKey(path("/gbuffers_entities.mesh")));
        // Includes are flattened, so the option's #define rides in the ported file and the option system edits it there too
        assertTrue(stages.get(path("/gbuffers_terrain.mesh")).contains("#define SCALE 1.0 // [0.5 1.0 2.0]"));
        assertTrue(NvidiumPackPorter.candidatePrograms().containsAll(List.of("gbuffers_terrain_cutout_mip", "gbuffers_textured_lit", "shadow_water")));
    }

    @Test
    void programsAPortCannotCarryAreReportedAndKeepOnlyTheirOpenGlStages() {
        Map<AbsolutePackPath, String> sources = pack(Map.of(
                "/gbuffers_water.gsh", "#version 150\nvoid main() {}",
                "/shadow.fsh", "#version 330\nin Block { vec2 uv; } fs_in;\nvoid main() {}"));
        List<String> ported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<AbsolutePackPath, String> stages = NvidiumPackPorter.portSources(sources, ported, skipped);
        assertTrue(ported.contains("gbuffers_terrain"));
        assertTrue(skipped.contains("gbuffers_water: geometry or tessellation stage"));
        assertTrue(skipped.stream().anyMatch(reason -> reason.startsWith("shadow: fragment input block Block")));
        assertFalse(stages.containsKey(path("/gbuffers_water.mesh")));
        assertFalse(stages.containsKey(path("/shadow.mesh")));
    }

    @Test
    void aPortLoadsAsAPackWhoseProgramsCarryTheirMeshStages() {
        Map<AbsolutePackPath, String> sources = pack(Map.of());
        sources.putAll(NvidiumPackPorter.portSources(sources, new ArrayList<>(), new ArrayList<>()));
        ShaderPack port = new ShaderPack(sources, Map.of("SCALE", "2.0"));
        ProgramSource terrain = port.getProgramSet().get(ProgramId.TerrainCutoutMip).orElseThrow();
        assertTrue(terrain.hasMeshStages());
        assertTrue(terrain.getMeshSource().orElseThrow().startsWith("#version 460"));
        // The option edit reached the ported copy of the define like every other file
        assertTrue(terrain.getMeshSource().orElseThrow().contains("#define SCALE 2.0"));
        assertTrue(terrain.getMeshFragmentSource().orElseThrow().contains("NVIDIUM_BRIDGE"));
        // An ordinary pack's programs have none
        assertFalse(new ShaderPack(pack(Map.of())).getProgramSet().get(ProgramId.TerrainCutoutMip).orElseThrow().hasMeshStages());
    }

    // Writes a pack folder: its shaders, a texture and a readme at the root
    private static Path writeFolderPack(Path root) throws IOException {
        for (Map.Entry<AbsolutePackPath, String> source : pack(Map.of()).entrySet()) {
            Path file = root.resolve("shaders" + source.getKey().getPathString());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
        Files.write(Files.createDirectories(root.resolve("shaders/textures")).resolve("noise.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        Files.writeString(root.resolve("readme.txt"), "credits");
        return root;
    }

    @Test
    void aFolderPackIsCopiedWholeWithItsPortAndAManifest(@TempDir Path temp) throws IOException {
        Path source = writeFolderPack(temp.resolve("Pack"));
        Path target = temp.resolve("nvidium_shaderpacks/Pack");

        NvidiumPackPorter.Result result = NvidiumPackPorter.port(source, target);
        assertEquals(3, result.ported().size());
        assertTrue(result.skipped().isEmpty());
        assertEquals("credits", Files.readString(target.resolve("readme.txt")));
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, Files.readAllBytes(target.resolve("shaders/textures/noise.png")));
        assertEquals(VERTEX, Files.readString(target.resolve("shaders/gbuffers_terrain.vsh")));
        assertTrue(Files.readString(target.resolve("shaders/gbuffers_terrain.mesh")).contains(MeshShaderTranslator.HEADER));
        String manifest = Files.readString(target.resolve(NvidiumPackPorter.MANIFEST));
        assertTrue(manifest.contains("source=Pack"));
        assertTrue(manifest.contains("format=" + MeshShaderTranslator.PORT_FORMAT));
        // The port loads with the ordinary loader
        assertTrue(ShaderPackLoader.loadFromDirectory(target).getProgramSet().get(ProgramId.Water).orElseThrow().hasMeshStages());

        // Porting again replaces the earlier port whole, leaving nothing stale behind
        Files.writeString(target.resolve("shaders/stale.glsl"), "old");
        Files.writeString(source.resolve("shaders/gbuffers_water.gsh"), "#version 150\nvoid main() {}");
        NvidiumPackPorter.Result again = NvidiumPackPorter.port(source, target);
        assertEquals(List.of("gbuffers_water: geometry or tessellation stage"), again.skipped());
        assertFalse(Files.exists(target.resolve("shaders/stale.glsl")));
        assertFalse(Files.exists(target.resolve("shaders/gbuffers_water.mesh")));
        assertTrue(Files.readString(target.resolve(NvidiumPackPorter.MANIFEST)).contains("skipped.0=gbuffers_water"));
    }

    @Test
    void ensureKeepsAPortUntilThePackOrTheFormatChanges(@TempDir Path temp) throws IOException {
        Path source = writeFolderPack(temp.resolve("Pack"));
        Files.writeString(source.resolve("shaders/gbuffers_water.gsh"), "#version 150\nvoid main() {}");
        Path target = temp.resolve("ports/Pack");

        NvidiumPackPorter.Result first = NvidiumPackPorter.ensure(source, target);
        assertFalse(first.reused());
        // Unchanged, the port is read back from its manifest and left as it is
        Files.writeString(target.resolve("shaders/marker.txt"), "kept");
        NvidiumPackPorter.Result again = NvidiumPackPorter.ensure(source, target);
        assertTrue(again.reused());
        assertEquals(first.ported(), again.ported());
        assertEquals(List.of("gbuffers_water: geometry or tessellation stage"), again.skipped());
        assertTrue(Files.exists(target.resolve("shaders/marker.txt")));

        // Any change to the pack's files makes a fresh port
        Files.writeString(source.resolve("shaders/gbuffers_terrain.fsh"), FRAGMENT + "\n");
        assertFalse(NvidiumPackPorter.ensure(source, target).reused());
        assertFalse(Files.exists(target.resolve("shaders/marker.txt")));
        // So does a port made by another format or from other templates, which a different fingerprint stands for
        Path manifest = target.resolve(NvidiumPackPorter.MANIFEST);
        Files.writeString(manifest, Files.readString(manifest).replaceAll("fingerprint=\\w+", "fingerprint=old"));
        assertFalse(NvidiumPackPorter.ensure(source, target).reused());
        assertEquals(NvidiumPackPorter.fingerprint(source), NvidiumPackPorter.fingerprint(source));
    }

    @Test
    void aZippedPackLosesItsWrappingFolderAndOnlyOwnPortsAreReplaced(@TempDir Path temp) throws IOException {
        Path zip = temp.resolve("Pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("Pack v1/"));
            out.closeEntry();
            for (Map.Entry<AbsolutePackPath, String> source : pack(Map.of()).entrySet()) {
                out.putNextEntry(new ZipEntry("Pack v1/shaders" + source.getKey().getPathString()));
                out.write(source.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("Pack v1/LICENSE"));
            out.write("MIT".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("__MACOSX/junk"));
            out.closeEntry();
        }
        Path target = temp.resolve("Pack");
        NvidiumPackPorter.port(zip, target);
        assertEquals("MIT", Files.readString(target.resolve("LICENSE")));
        assertTrue(Files.isRegularFile(target.resolve("shaders/shadow.mesh.fsh")));
        assertFalse(Files.exists(target.resolve("__MACOSX")));

        // A folder the porter did not make is never deleted, and a pack without shaders/ is refused
        Path foreign = Files.createDirectories(temp.resolve("Foreign"));
        assertThrows(IOException.class, () -> NvidiumPackPorter.port(zip, foreign));
        Path empty = Files.createDirectories(temp.resolve("Empty"));
        Files.writeString(empty.resolve("readme.txt"), "nothing");
        assertThrows(IOException.class, () -> NvidiumPackPorter.port(empty, temp.resolve("EmptyPort")));
        try (OutputStream out = Files.newOutputStream(temp.resolve("bare.zip")); ZipOutputStream zipped = new ZipOutputStream(out)) {
            zipped.putNextEntry(new ZipEntry("shaders/gbuffers_terrain.vsh"));
            zipped.write(VERTEX.getBytes(StandardCharsets.UTF_8));
            zipped.closeEntry();
        }
        NvidiumPackPorter.port(temp.resolve("bare.zip"), temp.resolve("bare"));
        assertTrue(Files.isRegularFile(temp.resolve("bare/shaders/gbuffers_terrain.vsh")));
        // A zip is fingerprinted as one file, so the port just made is current; one that carried nothing reads back empty
        NvidiumPackPorter.Result bare = NvidiumPackPorter.ensure(temp.resolve("bare.zip"), temp.resolve("bare"));
        assertTrue(bare.reused());
        assertTrue(bare.ported().isEmpty());
        assertTrue(bare.skipped().isEmpty());
    }
}
