package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.features.FeatureFlags;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.NamespacedId;
import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import net.minecraft.util.BlockRenderLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackTest {
    @TempDir
    Path dir;

    @AfterEach
    void forgetPackState() {
        ShaderMacros.setPackLegacyPrograms(Collections.emptySet());
        CustomTextureTransformer.setActivePatches(null);
    }

    private static final String PROPERTIES = String.join("\n",
            "sliders = SHADOW_QUALITY",
            "profile.LOW = !BLOOM SHADOW_QUALITY=1 !program.composite1 profile.BASE",
            "profile.BASE = SUN_ANGLE:10",
            "profile.HIGH = BLOOM SHADOW_QUALITY=3 NOT_AN_OPTION",
            "iris.features.required = SSBO TESSELLATION_SHADERS",
            "iris.features.optional = CUSTOM_IMAGES MADE_UP",
            "supportsColorCorrection = true",
            "oldLighting = false",
            "#ifdef BLOOM",
            "bloom.gated = yes",
            "#endif",
            "size.buffer.colortex1 = SHADOW_QUALITY SHADOW_QUALITY",
            "texture.noise = tex/noise.png",
            "texture.composite.colortex5 = /tex/lut.png",
            "texture.composite.colortex6 = missing.png",
            "texture.gbuffers.gaux1 = minecraft:dynamic/lightmap_1",
            "texture.gbuffers.gaux2 = minecraft:dynamic/light_map_1",
            "texture.gbuffers.gaux3 = mymod:textures/a.png",
            "texture.gbuffers.gaux4 = a:b:c",
            "texture.deferred.colortex6 = tex/volume.dat TEXTURE_3D RGBA8 4 4 4 RGBA UNSIGNED_BYTE",
            "customTexture.raw2d = /tex/raw.dat TEXTURE_2D RGBA8 2 2 RGBA UNSIGNED_BYTE",
            "customTexture.bad3d = tex/volume.dat TEXTURE_3D RGBA8 4 4",
            "customTexture.bad2d = tex/raw.dat TEXTURE_2D RGBA8 2",
            "customTexture.cube = tex/raw.dat TEXTURE_CUBE RGBA8 2 2 RGBA UNSIGNED_BYTE",
            "customTexture.zero = tex/raw.dat TEXTURE_2D RGBA8 0 2 RGBA UNSIGNED_BYTE",
            "customTexture.nan = tex/raw.dat TEXTURE_2D RGBA8 x 2 RGBA UNSIGNED_BYTE",
            "customTexture.nometa = tex/plain.png",
            "customTexture.emptymeta = tex/empty.png",
            "customTexture.partialmeta = tex/partial.png");

    private static final String TERRAIN_VERTEX = String.join("\n",
            "#version 120",
            "#define BLOOM // Bloom toggle",
            "//#define SUN_GLOW",
            "#define SHADOW_QUALITY 1 // Shadow quality [1 2 3]",
            "#define SUN_ANGLE 10 // [0 10 20]",
            "#define WATER_WAVES 2 // [1 2 3]",
            "const int shadowMapResolution = 2048; // [1024 2048 4096]",
            "const bool shadowHardwareFiltering = true;",
            "#include \"lib/common.glsl\"",
            "#ifdef BLOOM",
            "#endif",
            "#ifdef SUN_GLOW",
            "#endif",
            "#ifdef shadowHardwareFiltering",
            "#endif",
            "void main() {}");

    private static final String WATER_FRAGMENT = String.join("\n",
            "#if !defined IS_IRIS && MC_VERSION < 99999999999",
            "#endif",
            "#if !defined(IS_IRIS) && MC_VERSION < 10000",
            "#endif",
            "#if !defined(IS_IRIS) && MC_VERSION < 11300",
            "#endif",
            "void main() {}");

    private static Map<String, String> textFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("shaders.properties", PROPERTIES);
        files.put("gbuffers_terrain.vsh", TERRAIN_VERTEX);
        files.put("gbuffers_terrain.fsh", "#version 120\n#include \"/lib/common.glsl\"\nvoid main() {}\n");
        files.put("gbuffers_basic.vsh", "void main() {}");
        files.put("gbuffers_basic.fsh", "void main() {}");
        files.put("gbuffers_water.vsh", "void main() {}");
        files.put("gbuffers_water.fsh", WATER_FRAGMENT);
        files.put("lib/common.glsl", "#include \"util.glsl\"\n#include \"missing.glsl\"\n#include \"missing.glsl\"\n#include nothing\nfloat common;\n");
        files.put("lib/util.glsl", "float util;");
        files.put("lib/legacy.glsl", "#if !defined(IS_IRIS) && MC_VERSION < 11300\n#endif");
        files.put("lib/defines.h", "#define H_VALUE 1 // [1 2]");
        files.put("composite.vsh", "// root composite");
        files.put("world0/composite.vsh", "// overworld composite");
        files.put("world0/composite.fsh", "void main() {}");
        files.put("composite1.vsh", "void main() {}");
        files.put("composite1.fsh", "void main() {}");
        files.put("composite2.fsh", "void main() {}");
        files.put("deferred.csh", "// deferred");
        files.put("deferred_a.csh", "// deferred a");
        files.put("deferred_b.csh", "// deferred b");
        files.put("deferred_d.csh", "// never read, _c is missing");
        files.put("shadowcomp_a.csh", "// shadowcomp a");
        files.put("shadow.vsh", "void main() {}");
        files.put("shadow.gsh", "// geometry");
        files.put("shadow.tcs", "// control");
        files.put("shadow.tes", "// evaluation");
        files.put("shadow.fsh", "void main() {}");
        files.put("final.vsh", "void main() {}");
        files.put("final.fsh", "void main() {}");
        files.put("block.properties", "block.10 = minecraft:stone grass\nlayer.translucent = minecraft:glass\n");
        files.put("item.properties", "item.5 = diamond_sword\n");
        files.put("entity.properties", "entity.7 = zombie\n");
        // Metadata for other mods stays out of the source map
        files.put("voxy.json", "#define OVERWORLD");
        files.put("README.md", "#define NOT_AN_OPTION");
        return files;
    }

    private static Map<String, byte[]> binaryFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("tex/noise.png", new byte[] {1, 2, 3});
        files.put("tex/noise.png.mcmeta", "{\"texture\":{\"blur\":true,\"clamp\":true}}".getBytes(StandardCharsets.UTF_8));
        files.put("tex/lut.png", new byte[] {4});
        files.put("tex/lut.png.mcmeta", "{".getBytes(StandardCharsets.UTF_8));
        files.put("tex/volume.dat", new byte[64]);
        files.put("tex/raw.dat", new byte[16]);
        files.put("tex/plain.png", new byte[] {5});
        files.put("tex/empty.png", new byte[] {6});
        files.put("tex/empty.png.mcmeta", "{\"animation\":{}}".getBytes(StandardCharsets.UTF_8));
        files.put("tex/partial.png", new byte[] {7});
        files.put("tex/partial.png.mcmeta", "{\"texture\":{}}".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private Path writePack() throws IOException {
        Path shaders = dir.resolve("pack/shaders");
        for (Map.Entry<String, String> file : textFiles().entrySet()) {
            Path target = shaders.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        for (Map.Entry<String, byte[]> file : binaryFiles().entrySet()) {
            Path target = shaders.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue());
        }
        return dir.resolve("pack");
    }

    private Path zipPack(String name, boolean withShaders) throws IOException {
        Path zip = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream stream = new ZipOutputStream(out)) {
            stream.putNextEntry(new ZipEntry("MyPack/"));
            stream.closeEntry();
            stream.putNextEntry(new ZipEntry("MyPack/pack.txt"));
            stream.write("not in shaders/".getBytes(StandardCharsets.UTF_8));
            stream.closeEntry();
            if (withShaders) {
                for (Map.Entry<String, String> file : textFiles().entrySet()) {
                    stream.putNextEntry(new ZipEntry("MyPack/shaders/" + file.getKey()));
                    stream.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    stream.closeEntry();
                }
                for (Map.Entry<String, byte[]> file : binaryFiles().entrySet()) {
                    stream.putNextEntry(new ZipEntry("MyPack\\shaders\\" + file.getKey()));
                    stream.write(file.getValue());
                    stream.closeEntry();
                }
            }
        }
        return zip;
    }

    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    @Test
    void aFolderPackLoadsWithItsOptionEditsApplied() throws IOException {
        Map<String, String> changed = Map.of("BLOOM", "false", "SUN_GLOW", "true", "WATER_WAVES", "3",
                "shadowMapResolution", "1024", "shadowHardwareFiltering", "false");
        ShaderPack pack = ShaderPackLoader.loadFromDirectory(writePack(), changed);

        // Options are discovered pack-wide; an unreferenced define is not an option
        OptionSet options = pack.getShaderPackOptions().getOptionSet();
        assertEquals(Set.of("BLOOM", "SUN_GLOW", "shadowHardwareFiltering"), options.getBooleanOptions().keySet());
        assertEquals(Set.of("SHADOW_QUALITY", "SUN_ANGLE", "WATER_WAVES", "shadowMapResolution", "H_VALUE"), options.getStringOptions().keySet());
        String edited = pack.getShaderPackOptions().getEditedSources().get(path("/gbuffers_terrain.vsh"));
        assertTrue(edited.contains("//#define BLOOM // Bloom toggle"), edited);
        assertTrue(edited.contains("\n#define SUN_GLOW\n"), edited);
        assertTrue(edited.contains("#define WATER_WAVES 3 // OptionAnnotatedSource: Changed option"), edited);
        assertTrue(edited.contains("const int shadowMapResolution = 1024;"), edited);
        assertTrue(edited.contains("const bool shadowHardwareFiltering = false;"), edited);

        // The option values feed the properties macros, not the GLSL environment
        Map<String, String> defines = pack.getShaderDefines();
        assertEquals("1", defines.get("SUN_GLOW"));
        assertFalse(defines.containsKey("BLOOM"));
        assertEquals("1", defines.get("SHADOW_QUALITY"));
        assertFalse(pack.getProperties().getRaw().containsKey("bloom.gated"));
        assertArrayEquals(new float[] {1, 1}, pack.getProperties().getBufferSizes().get(1));
        Map<String, String> environment = pack.getEnvironmentDefines();
        assertFalse(environment.containsKey("SUN_GLOW"));
        assertTrue(environment.containsKey("MC_OLD_HAND_LIGHT"));
        assertFalse(environment.containsKey("MC_OLD_LIGHTING"));
        assertTrue(environment.containsKey("COLOR_SPACE_SRGB"));

        // LOW is the profile these values match, and it switches composite1 off
        assertEquals(java.util.Optional.of(false), pack.getProperties().getProgramEnabled("composite1"));
        assertTrue(pack.hasFeature(FeatureFlags.SSBO));
        assertTrue(pack.hasFeature(FeatureFlags.CUSTOM_IMAGES));
        assertTrue(pack.hasFeature(FeatureFlags.TESSELLATION_SHADERS));

        // Other mods' metadata never reaches the option scan
        assertFalse(pack.getSources().containsKey(path("/voxy.json")));
        assertFalse(pack.getSources().containsKey(path("/README.md")));
        assertTrue(pack.getSources().containsKey(path("/lib/defines.h")));
        assertPrograms(pack);
        assertTextures(pack);

        // Only the program with an authored pre-Iris branch for this version compiles as legacy
        Map<String, String> macros = Map.of("IS_IRIS", "", "IRIS_VERSION", "10805", "MC_VERSION", "11202");
        assertFalse(ShaderMacros.forProgram(macros, "gbuffers_water").containsKey("IS_IRIS"));
        assertTrue(ShaderMacros.forProgram(macros, "gbuffers_terrain").containsKey("IS_IRIS"));
        assertEquals(1, CustomTextureTransformer.getActivePatches().size());

        assertTrue(pack.getIdMap().hasBlockProperties());
        assertEquals(Map.of(new NamespacedId("diamond_sword"), 5), pack.getIdMap().getItemIdMap());
        assertEquals(Map.of(new NamespacedId("zombie"), 7), pack.getIdMap().getEntityIdMap());
        assertEquals(Map.of(new NamespacedId("minecraft:glass"), BlockRenderLayer.TRANSLUCENT), pack.getIdMap().getBlockRenderLayerMap());
    }

    private static void assertPrograms(ShaderPack pack) {
        ProgramSet programs = pack.getProgramSet();
        assertSame(pack.getProperties(), programs.getProperties());
        ProgramSource terrain = programs.get(ProgramId.Terrain).orElseThrow();
        assertEquals("gbuffers_terrain", terrain.getName());
        String fragment = terrain.getFragmentSource().orElseThrow();
        assertTrue(fragment.contains("float util;") && fragment.contains("float common;"), fragment);
        assertTrue(fragment.contains("skipped unresolved #include \"missing.glsl\""), fragment);
        assertTrue(fragment.contains("#include nothing"), fragment);
        // A missing program walks its fallback chain, a direct lookup does not
        assertSame(terrain, programs.get(ProgramId.TerrainSolid).orElseThrow());
        assertEquals("gbuffers_basic", programs.get(ProgramId.Clouds).orElseThrow().getName());
        assertTrue(programs.getDirect(ProgramId.Clouds).isEmpty());
        assertSame(terrain, programs.getDirect(ProgramId.Terrain).orElseThrow());
        assertTrue(programs.get(ProgramId.DhShadow).isEmpty());

        // world0 wins over the pack root
        ProgramSource composite = programs.get(ProgramArrayId.Composite, 0).orElseThrow();
        assertEquals("// overworld composite", composite.getVertexSource().orElseThrow());
        assertTrue(programs.get(ProgramArrayId.Composite, 1).isPresent());
        // A fragment stage alone is not a program
        assertNotNull(programs.getArray(ProgramArrayId.Composite)[2]);
        assertFalse(programs.getArray(ProgramArrayId.Composite)[2].isValid());
        assertTrue(programs.get(ProgramArrayId.Composite, 2).isEmpty());
        assertTrue(programs.get(ProgramArrayId.Composite, 3).isEmpty());
        assertTrue(programs.get(ProgramArrayId.Composite, -1).isEmpty());
        assertTrue(programs.get(ProgramArrayId.Composite, 100).isEmpty());
        assertTrue(programs.get(ProgramArrayId.Begin, 0).isEmpty());
        assertNull(programs.getArray(ProgramArrayId.Begin));

        // Compute variants stop at the first gap
        ProgramSource deferred = programs.get(ProgramArrayId.Deferred, 0).orElseThrow();
        String[] computes = deferred.getComputeSources();
        assertEquals(ProgramSource.MAX_COMPUTE_VARIANTS, computes.length);
        assertEquals("// deferred", deferred.getComputeSource().orElseThrow());
        assertEquals("// deferred b", computes[2]);
        assertNull(computes[4]);
        assertFalse(deferred.hasRasterStages());
        ProgramSource shadowcomp = programs.get(ProgramArrayId.ShadowComposite, 0).orElseThrow();
        assertTrue(shadowcomp.getComputeSource().isEmpty());
        assertTrue(shadowcomp.hasComputeSource());

        ProgramSource shadow = programs.get(ProgramId.Shadow).orElseThrow();
        assertEquals("// geometry", shadow.getGeometrySource().orElseThrow());
        assertEquals("// control", shadow.getTessControlSource().orElseThrow());
        assertEquals("// evaluation", shadow.getTessEvalSource().orElseThrow());
        assertTrue(shadow.hasRasterStages());
        assertTrue(shadow.getComputeSource().isEmpty());
        assertFalse(shadow.hasComputeSource());

        Map<String, ProgramSource> declared = programs.collectDeclaredPrograms();
        assertTrue(declared.keySet().containsAll(List.of("gbuffers_terrain", "gbuffers_basic", "shadow", "final",
                "composite", "composite1", "deferred", "shadowcomp")));
        assertFalse(declared.containsKey("composite2"));
        assertEquals(declared.keySet().stream().sorted().toList(), programs.listDeclaredPrograms().stream().sorted().toList());
    }

    private static void assertTextures(ShaderPack pack) {
        CustomTextureData.PngData noise = (CustomTextureData.PngData) pack.getCustomNoiseTexture();
        assertArrayEquals(new byte[] {1, 2, 3}, noise.getContent());
        assertTrue(noise.getFilteringData().shouldBlur());
        assertTrue(noise.getFilteringData().shouldClamp());
        assertEquals("TextureFilteringData{blur=true, clamp=true}", noise.getFilteringData().toString());

        Map<String, CustomTextureData> composite = pack.getCustomTextureDataMap().get(TextureStage.COMPOSITE_AND_FINAL);
        // A malformed .mcmeta is ignored rather than failing the texture, a missing file drops its sampler
        assertFalse(((CustomTextureData.PngData) composite.get("colortex5")).getFilteringData().shouldBlur());
        assertFalse(composite.containsKey("colortex6"));

        Map<String, CustomTextureData> gbuffers = pack.getCustomTextureDataMap().get(TextureStage.GBUFFERS_AND_SHADOW);
        assertEquals(new CustomTextureData.LightmapMarker(), gbuffers.get("gaux1"));
        assertEquals(new CustomTextureData.LightmapMarker().hashCode(), gbuffers.get("gaux2").hashCode());
        assertNotEquals(gbuffers.get("gaux1"), null);
        assertNotEquals(gbuffers.get("gaux1"), gbuffers.get("gaux3"));
        CustomTextureData.ResourceData resource = (CustomTextureData.ResourceData) gbuffers.get("gaux3");
        assertEquals("mymod", resource.getNamespace());
        assertEquals("textures/a.png", resource.getLocation());
        assertEquals("b", ((CustomTextureData.ResourceData) gbuffers.get("gaux4")).getLocation());

        Map<String, CustomTextureData> custom = pack.getUmbraCustomTextureDataMap();
        CustomTextureData.RawData volume = (CustomTextureData.RawData) custom.get("customtex0");
        assertEquals("TEXTURE_3D", volume.getTextureType());
        assertEquals("RGBA8", volume.getInternalFormat());
        assertEquals(4, volume.getWidth());
        assertEquals(4, volume.getHeight());
        assertEquals(4, volume.getDepth());
        assertEquals("RGBA", volume.getPixelFormat());
        assertEquals("UNSIGNED_BYTE", volume.getPixelType());
        assertEquals(64, volume.getContent().length);
        CustomTextureData.RawData flat = (CustomTextureData.RawData) custom.get("raw2d");
        assertEquals(1, flat.getDepth());
        // Every malformed raw definition is dropped with a log line
        for (String dropped : new String[] {"bad3d", "bad2d", "cube", "zero", "nan"}) {
            assertFalse(custom.containsKey(dropped), dropped);
        }
        assertInstanceOf(CustomTextureData.PngData.class, custom.get("nometa"));
        assertFalse(((CustomTextureData.PngData) custom.get("emptymeta")).getFilteringData().shouldClamp());
        assertFalse(((CustomTextureData.PngData) custom.get("partialmeta")).getFilteringData().shouldBlur());
    }

    @Test
    void aZippedPackLoadsTheSame() throws IOException {
        ShaderPack pack = ShaderPackLoader.loadFromZip(zipPack("pack.zip", true));
        assertTrue(pack.getProgramSet().get(ProgramId.Terrain).isPresent());
        assertNotNull(pack.getCustomNoiseTexture());
        // With the defaults, BASE is the profile that matches, and it disables nothing
        assertEquals(java.util.Optional.empty(), pack.getProperties().getProgramEnabled("composite1"));
        assertEquals("yes", pack.getProperties().getRaw().get("bloom.gated"));
        assertFalse(pack.getSources().containsKey(path("/voxy.json")));

        ShaderPack folder = ShaderPackLoader.loadFromDirectory(writePack());
        assertEquals(pack.getSources().keySet(), folder.getSources().keySet());
    }

    @Test
    void aPackWithoutShadersIsRefused() throws IOException {
        Files.createDirectories(dir.resolve("empty"));
        assertThrows(IOException.class, () -> ShaderPackLoader.loadFromDirectory(dir.resolve("empty")));
        assertThrows(IOException.class, () -> ShaderPackLoader.loadFromZip(zipPack("empty.zip", false)));
    }

    @Test
    void aPackWithNothingButSourcesUsesEveryDefault() {
        ShaderPack pack = new ShaderPack(Map.of(path("/final.vsh"), "void main() {}", path("/final.fsh"), "void main() {}"));
        assertTrue(pack.getProperties().getRaw().isEmpty());
        Map<String, String> environment = pack.getEnvironmentDefines();
        assertTrue(environment.containsKey("MC_OLD_LIGHTING"));
        assertFalse(environment.containsKey("COLOR_SPACE_SRGB"));
        assertNull(pack.getCustomNoiseTexture());
        assertFalse(pack.getIdMap().hasBlockProperties());
        assertTrue(pack.getProgramSet().get(ProgramId.Final).isPresent());
        assertTrue(pack.getUmbraCustomTextureDataMap().isEmpty());
        assertTrue(pack.getCustomTextureDataMap().isEmpty());
    }

    @Test
    void brokenProfilesAreIgnoredRatherThanFailingThePack() {
        ShaderPack pack = new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "profile.A = profile.B\nprofile.B = profile.A\n"));
        assertEquals(java.util.Optional.empty(), pack.getProperties().getProgramEnabled("composite"));
        // No profile matches values nobody set, so nothing is disabled either
        ShaderPack unmatched = new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "profile.A = FOO=1\n",
                path("/a.fsh"), "#define FOO 2 // [1 2]"));
        assertEquals(java.util.Optional.empty(), unmatched.getProperties().getProgramEnabled("composite"));
    }
}
