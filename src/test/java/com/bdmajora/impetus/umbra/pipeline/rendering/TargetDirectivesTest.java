package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class TargetDirectivesTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private PipelineGl gl;
    private UmbraRenderTargets targets;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(4, 4);
    }

    @AfterEach
    void close() {
        this.targets.destroy();
        this.gl.close();
    }

    private TargetDirectives apply(ShaderPack pack) {
        TargetDirectives directives = new TargetDirectives();
        directives.apply(new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines()), this.targets);
        return directives;
    }

    private InternalTextureFormat format(int index) {
        return this.targets.getOrCreate(index).getInternalFormat();
    }

    @Test
    void formatsComeFromAnyStageByEitherNameAndTheLastActiveDeclarationWins() {
        ShaderPack pack = PackSourcesTest.pack(
                "/gbuffers_textured.vsh", "const int gaux1Format = RGBA16F;\n" + VERTEX,
                "/gbuffers_textured.fsh", String.join("\n",
                        "const int colortex2Format = RGB16F;",
                        "const int colortex2Format = RGBA32F;",
                        "#ifdef NEVER",
                        "const int colortex2Format = R8;",
                        "#endif",
                        // Unknown names, unknown formats, wrong types and non-identifier values change nothing
                        "const int shadowcolor0Format = RGBA16F;",
                        "const int colortex3Format = BOGUS;",
                        "const float colortex5Format = 1.0;",
                        "const int colortex6Format = RGBA16F /* trailing */;",
                        "void main() {}"));
        TargetDirectives directives = apply(pack);
        assertEquals(InternalTextureFormat.RGBA32F, format(2));
        assertEquals(InternalTextureFormat.RGBA16F, format(4));
        assertEquals(InternalTextureFormat.RGBA, format(3));
        assertEquals(InternalTextureFormat.RGBA, format(5));
        assertEquals(InternalTextureFormat.RGBA, format(6));
        assertNotNull(directives);
    }

    @Test
    void aFormatForATargetThatAlreadyExistsIsReportedAndIgnored() {
        this.targets.getOrCreate(1);
        apply(PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh", "const int colortex1Format = RGBA16F;\nvoid main() {}"));
        assertEquals(InternalTextureFormat.RGBA, format(1));
    }

    @Test
    void theLegacyGaux4FormatAcceptsOnlyOptiFinesThreeAndAConstOverridesIt() {
        apply(PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh", "/* GAUX4FORMAT:RGBA8 */\n/*GAUX4FORMAT : RGB16 */\nvoid main() {}"));
        assertEquals(InternalTextureFormat.RGB16, format(7));
        this.targets.destroy();
        this.targets = new UmbraRenderTargets(4, 4);
        // The const directive is applied after every legacy one, so it wins regardless of where it sits
        apply(PackSourcesTest.pack("/composite.vsh", "const int colortex7Format = RGBA16F;\n" + VERTEX,
                "/composite.fsh", "/* GAUX4FORMAT:RGB32F */\nvoid main() {}"));
        assertEquals(InternalTextureFormat.RGBA16F, format(7));
    }

    @Test
    void clearsAndClearColoursFollowTheirDirectivesAndKeepTheirDefaultsOtherwise() {
        TargetDirectives defaults = new TargetDirectives();
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            assertTrue(defaults.clears(i));
        }
        assertNull(defaults.clearColor(0));
        assertArrayEquals(new float[]{1, 1, 1, 1}, defaults.clearColor(1));
        assertArrayEquals(new float[]{0, 0, 0, 0}, defaults.clearColor(15));
        TargetDirectives directives = apply(PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh", String.join("\n",
                "const bool colortex2Clear = false;",
                "const bool gaux1Clear=false;",
                "const int colortex3Clear = 0;",
                "const bool colortex6Clear = maybe;",
                "const vec4 colortex0ClearColor = vec4(1.0, 0.5, 0.25f, 1.0);",
                "const vec4 colortex2ClearColor = vec4(0.5);",
                "const vec4 colortex2ClearColor = vec4(a, b, c, d);",
                "const vec4 colortex7ClearColor = vec4(1.0, 2.0);",
                "const vec4 colortex8ClearColor = vec4(Infinity);",
                "const vec4 colortex9ClearColor = vec4(vec3(1.0), 1.0);",
                "const float colortex10ClearColor = 1.0;",
                "void main() {}")));
        assertFalse(directives.clears(2));
        assertFalse(directives.clears(4));
        assertTrue(directives.clears(3));
        assertTrue(directives.clears(6));
        assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, directives.clearColor(0));
        // A malformed later colour keeps the earlier one, as Umbra ignores what it cannot parse
        assertArrayEquals(new float[]{0.5f, 0.5f, 0.5f, 0.5f}, directives.clearColor(2));
        assertArrayEquals(new float[]{0, 0, 0, 0}, directives.clearColor(7));
        assertArrayEquals(new float[]{0, 0, 0, 0}, directives.clearColor(8));
        assertArrayEquals(new float[]{0, 0, 0, 0}, directives.clearColor(9));
        assertArrayEquals(new float[]{0, 0, 0, 0}, directives.clearColor(10));
    }

    @Test
    void vec4LiteralsParseOneOrFourFiniteComponents() {
        assertArrayEquals(new float[]{2, 2, 2, 2}, TargetDirectives.parseVec4(" 2.0f "));
        assertArrayEquals(new float[]{1, 2, 3, 4}, TargetDirectives.parseVec4("1, 2, 3, 4"));
        assertNull(TargetDirectives.parseVec4("1, 2, 3"));
        assertNull(TargetDirectives.parseVec4("1, 2, 3, 4, 5"));
        assertNull(TargetDirectives.parseVec4(""));
        assertNull(TargetDirectives.parseVec4("1, NaN, 3, 4"));
        assertNull(TargetDirectives.parseVec4("x"));
    }

    @Test
    void mipmapDirectivesAreReadPerProgramFromTheResolvedFragmentStage() {
        ShaderPack pack = PackSourcesTest.pack("/composite.vsh", "const bool colortex1MipmapEnabled = true;\n" + VERTEX,
                "/composite.fsh", String.join("\n",
                        "const bool colortex2MipmapEnabled = true;",
                        "const bool gaux1MipmapEnabled = true;",
                        "const bool gaux1MipmapEnabled = false;",
                        "#ifdef NEVER",
                        "const bool colortex3MipmapEnabled = true;",
                        "#endif",
                        "const bool bogusMipmapEnabled = true;",
                        "const bool colortex5MipmapEnabled = sometimes;",
                        "void main() {}"),
                "/composite1.vsh", VERTEX, "/composite1.fsh", "void main() {}");
        PackSources sources = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        ProgramSource composite = pack.getProgramSet().get(ProgramArrayId.Composite, 0).orElseThrow();
        BitSet expected = new BitSet();
        expected.set(2);
        assertEquals(expected, TargetDirectives.mipmappedBuffers(composite, sources));
        assertTrue(TargetDirectives.mipmappedBuffers(pack.getProgramSet().get(ProgramArrayId.Composite, 1).orElseThrow(), sources).isEmpty());
    }

    @Test
    void eachSuffixTakesOnlyItsOwnTypeAndTheFourthLegacyFormatIsHonoured() {
        TargetDirectives directives = apply(PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh", String.join("\n",
                "const bool colortex1Clear = true;",
                "const bool colortex2Format = true;",
                "const int colortex3ClearColor = 1;",
                "/* GAUX4FORMAT:RGBA32F */",
                "void main() {}")));
        assertTrue(directives.clears(1));
        assertEquals(InternalTextureFormat.RGBA, format(2));
        assertArrayEquals(new float[]{0, 0, 0, 0}, directives.clearColor(3));
        assertEquals(InternalTextureFormat.RGBA32F, format(7));
    }

    @Test
    void aProgramWithoutAFragmentStageMipmapsNothing() {
        ShaderPack pack = PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.csh", "void main() {}");
        PackSources sources = new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines());
        assertTrue(TargetDirectives.mipmappedBuffers(pack.getProgramSet().get(ProgramArrayId.Composite, 0).orElseThrow(), sources).isEmpty());
    }
}
