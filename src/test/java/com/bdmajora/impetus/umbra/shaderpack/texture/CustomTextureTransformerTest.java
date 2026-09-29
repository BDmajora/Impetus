package com.bdmajora.impetus.umbra.shaderpack.texture;

import com.bdmajora.impetus.umbra.gl.blending.BlendMode;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPackLoader;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomTextureTransformerTest {
    @AfterEach
    void clearPatches() {
        CustomTextureTransformer.setActivePatches(null);
    }

    @Test
    void onlyAMatchingSamplerTypeIsRenamed() {
        List<CustomTexturePatch> patches = List.of(
                new CustomTexturePatch("colortex6", TextureStage.DEFERRED, "TEXTURE_3D", "customtex0"),
                new CustomTexturePatch("colortex7", TextureStage.DEFERRED, "TEXTURE_2D", "customtex1"),
                new CustomTexturePatch("colortex8", TextureStage.DEFERRED, "TEXTURE_1D", "customtex2"),
                new CustomTexturePatch("colortex9", TextureStage.DEFERRED, "TEXTURE_RECTANGLE", "customtex3"),
                new CustomTexturePatch("colortex10", TextureStage.DEFERRED, "TEXTURE_CUBE_MAP", "customtex4"),
                new CustomTexturePatch("colortex11", TextureStage.DEFERRED, "TEXTURE_2D", "customtex5"),
                new CustomTexturePatch("colortex6", TextureStage.COMPOSITE_AND_FINAL, "TEXTURE_3D", "customtex6"));
        String source = String.join("\n",
                "// uniform sampler2D colortex11;",
                "uniform float colortex5;",
                "uniform sampler3D colortex6[2];",
                "uniform usampler2D other, colortex7;",
                "uniform sampler3D colortex8;",
                "uniform isampler2DRect colortex9;",
                "uniform samplerCube colortex10;",
                "vec4 a = texture(colortex6[0], p) + texture(colortex7, uv) + texture(colortex8, p);",
                "vec4 b = texture(colortex9, uv) + texture(colortex10, v) + texture(colortex11, uv);");
        String deferred = CustomTextureTransformer.transform("deferred", source, TextureStage.DEFERRED, patches);
        assertTrue(deferred.contains("uniform sampler3D customtex0[2];"), deferred);
        assertTrue(deferred.contains("uniform usampler2D other, customtex1;"), deferred);
        // A 1D texture does not feed a sampler3D, nor an unknown target anything
        assertTrue(deferred.contains("uniform sampler3D colortex8;"), deferred);
        assertTrue(deferred.contains("uniform isampler2DRect customtex3;"), deferred);
        assertTrue(deferred.contains("uniform samplerCube colortex10;"), deferred);
        // A declaration inside a comment does not count
        assertTrue(deferred.contains("texture(colortex11, uv)"), deferred);
        assertTrue(deferred.contains("texture(customtex0[0], p)"), deferred);
        // Other stages are untouched, and so is a program with nothing declared
        assertEquals(source, CustomTextureTransformer.transform("gbuffers", source, TextureStage.GBUFFERS_AND_SHADOW, patches));
        assertEquals("void main() {}", CustomTextureTransformer.transform("x", "void main() {}", TextureStage.DEFERRED, patches));
        assertNull(CustomTextureTransformer.transform("x", null, TextureStage.DEFERRED, patches));

        // The three-argument form uses the pack's installed patches
        assertEquals(source, CustomTextureTransformer.transform("deferred", source, TextureStage.DEFERRED));
        CustomTextureTransformer.setActivePatches(patches);
        assertEquals(patches, CustomTextureTransformer.getActivePatches());
        assertEquals(deferred, CustomTextureTransformer.transform("deferred", source, TextureStage.DEFERRED));
    }

    @Test
    void everyStageNameParses() {
        assertEquals(TextureStage.SETUP, TextureStage.parse("setup").orElseThrow());
        assertEquals(TextureStage.BEGIN, TextureStage.parse("begin").orElseThrow());
        assertEquals(TextureStage.SHADOWCOMP, TextureStage.parse("shadowcomp").orElseThrow());
        assertEquals(TextureStage.PREPARE, TextureStage.parse("prepare").orElseThrow());
        assertTrue(TextureStage.parse("final").isEmpty());
        assertNull(CustomImageDefinition.parse("empty", ""));
    }

    @Test
    void programIdsKnowTheirNamesAndDefaults() {
        assertEquals(ProgramId.Terrain, ProgramId.bySourceName("GBUFFERS_TERRAIN"));
        assertNull(ProgramId.bySourceName("gbuffers_unknown"));
        BlendMode eyes = ProgramId.SpiderEyes.getDefaultBlendMode();
        assertNotNull(eyes);
        assertNull(ProgramId.Terrain.getDefaultBlendMode());
        assertEquals("composite", ProgramArrayId.Composite.getBaseName());
        assertEquals("composite3", ProgramArrayId.Composite.getSourceName(3));
        assertThrows(IndexOutOfBoundsException.class, () -> ProgramArrayId.Composite.getSourceName(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ProgramArrayId.Composite.getSourceName(100));
    }

    @Test
    void theZipReadersStreamCannotCloseTheZip() throws Exception {
        Class<?> type = Class.forName(ShaderPackLoader.class.getName() + "$UncloseableStream");
        Constructor<?> constructor = type.getDeclaredConstructor(InputStream.class);
        constructor.setAccessible(true);
        ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[] {9, 8});
        try (InputStream stream = (InputStream) constructor.newInstance(bytes)) {
            assertEquals(9, stream.read());
        }
        // Closing did nothing to the stream underneath
        assertEquals(8, bytes.read());
    }
}
