package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ShadowSettingsTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private static final Pattern DEFINE = Pattern.compile("(?m)^\\s*#define\\s+SHADOWRES\\s+(\\d+)");
    private static final Pattern FLOAT_DEFINE = Pattern.compile("(?m)^\\s*#define\\s+SHADOWHPL\\s+([0-9.]+)");
    private PipelineGl gl;
    private ShadowSamplers samplers;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.samplers = new ShadowSamplers(false);
    }

    @AfterEach
    void close() {
        CelestialUniforms.setSunPathRotation(0.0f);
        WorldRenderingSettings.setVoxelRenderDistanceChunks(0);
        this.samplers.destroy();
        this.gl.close();
    }

    private UmbraShadowRenderer create(ShaderPack pack) {
        return ShadowSettings.createRenderer(pack, new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines()),
                this.samplers, pack.getEnvironmentDefines(), Map.of(), () -> { });
    }

    @Test
    void aResolutionMustBePositiveAndFitTheGpu() {
        assertEquals(2048, ShadowSettings.validResolution(2048));
        assertEquals(ShadowSettings.DEFAULT_RESOLUTION, ShadowSettings.validResolution(0));
        assertEquals(ShadowSettings.DEFAULT_RESOLUTION, ShadowSettings.validResolution(-5));
        // An unknown limit (a failed query) clamps nothing
        assertEquals(1 << 20, ShadowSettings.validResolution(1 << 20));
        when(TestGl.gl().glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)).thenReturn(16384);
        assertEquals(16384, ShadowSettings.validResolution(65536));
        assertEquals(16384, ShadowSettings.validResolution(16384));
    }

    @Test
    void legacyDefinesReadTheFirstStageThatDeclaresThemAndSurviveGarbage() {
        assertEquals(512, ShadowSettings.defineInt(List.of("void a() {}", "#define SHADOWRES 512", "#define SHADOWRES 4096"), DEFINE, 1024));
        // At the very start of a stage, which the old concatenation (no separator) hid behind the previous stage's last line
        assertEquals(256, ShadowSettings.defineInt(List.of("int x = 1;", "#define SHADOWRES 256\n"), DEFINE, 1024));
        // Too long for an int keeps the fallback instead of failing the whole pipeline
        assertEquals(1024, ShadowSettings.defineInt(List.of("#define SHADOWRES 99999999999"), DEFINE, 1024));
        assertEquals(1024, ShadowSettings.defineInt(List.of(), DEFINE, 1024));
        assertEquals(90.5f, ShadowSettings.defineFloat(List.of("#define SHADOWHPL 90.5"), FLOAT_DEFINE, 160.0f));
        assertEquals(160.0f, ShadowSettings.defineFloat(List.of("#define SHADOWHPL 1.2.3"), FLOAT_DEFINE, 160.0f));
        assertNull(ShadowSettings.defineFloat(List.of("nothing"), FLOAT_DEFINE, null));
    }

    @Test
    void higherShadowcolorGivesIrisEightBuffersAndTheUsedSetComesFromNamesAndWriters() {
        ShaderPack optifine = PackSourcesTest.pack("/shaders.properties", "");
        assertEquals(2, ShadowSettings.colorBufferCount(optifine));
        ShaderPack pack = PackSourcesTest.pack("/shaders.properties", "iris.features.optional = HIGHER_SHADOWCOLOR",
                "/shadow.vsh", VERTEX, "/shadow.fsh", "/* DRAWBUFFERS:15 */\nvoid main() {}",
                "/shadowcomp.vsh", VERTEX, "/shadowcomp.fsh", "/* DRAWBUFFERS:3 */\nvoid main() {}",
                "/composite.vsh", VERTEX, "/composite.fsh", "uniform sampler2D shadowcolor6;\nvoid main() {}");
        assertEquals(8, ShadowSettings.colorBufferCount(pack));
        BitSet used = ShadowSettings.usedColorBuffers(pack, new PackSources(pack.getProgramSet(), pack.getEnvironmentDefines()));
        assertEquals(PassFactoryTest.bits(1, 3, 5, 6), used);
    }

    @Test
    void withoutAShadowProgramThereIsNoRendererButTheSunPathStillTilts() {
        ShaderPack pack = PackSourcesTest.pack("/composite.vsh", VERTEX,
                "/composite.fsh", "#ifdef NEVER\nconst float sunPathRotation = 40.0;\n#else\nconst float sunPathRotation = 25.0;\n#endif\nvoid main() {}");
        assertNull(create(pack));
        assertEquals(25.0f, CelestialUniforms.getSunPathRotation());
    }

    @Test
    void shadersPropertiesOverrideTheGlslDirectivesAndAreValidatedToo() {
        ShaderPack pack = PackSourcesTest.pack("/shaders.properties", "shadowMapResolution = 0\nshadowDistance = 48",
                "/shadow.vsh", "#define SHADOWRES 512\n" + VERTEX,
                "/shadow.fsh", "const float voxelDistance = 64.0;\nvoid main() {}");
        UmbraShadowRenderer renderer = create(pack);
        assertNotNull(renderer);
        try {
            // A 0 from shaders.properties used to skip the check and fail allocation, disabling shadows
            assertEquals(ShadowSettings.DEFAULT_RESOLUTION, renderer.getResolution());
            assertEquals(4, WorldRenderingSettings.getVoxelRenderDistanceChunks());
        } finally {
            renderer.destroy();
        }
        ShaderPack legacy = PackSourcesTest.pack("/shadow.vsh", "#define SHADOWRES 512\n" + VERTEX,
                "/shadow.fsh", "const float shadowDistance = 32.0;\nvoid main() {}");
        UmbraShadowRenderer fromDefine = create(legacy);
        try {
            assertEquals(512, fromDefine.getResolution());
            // The voxel radius follows the shadow distance when the pack declares none
            assertEquals(2, WorldRenderingSettings.getVoxelRenderDistanceChunks());
        } finally {
            fromDefine.destroy();
        }
    }

    @Test
    void aRendererThatFailsToBuildDisablesShadowsInsteadOfThePipeline() {
        // A driver offering no colour attachments refuses shadowcolor0
        this.gl.limits(48, 8, 0, 8);
        ShaderPack pack = PackSourcesTest.pack("/shadow.vsh", VERTEX, "/shadow.fsh", "void main() {}");
        assertNull(create(pack));
    }

    @Test
    void theFovComesFromTheConstThenTheLegacyDefineAndImagesMeanThePackVoxelizes() {
        ShaderPack constFov = PackSourcesTest.pack("/shadow.vsh", VERTEX,
                "/shadow.fsh", "const float shadowMapFov = 70.0;\nvoid main() {}");
        UmbraShadowRenderer fromConst = create(constFov);
        try {
            assertEquals(70.0f, com.bdmajora.testing.Mixins.<Float>get(fromConst, "shadowMapFov"));
            assertFalse(com.bdmajora.testing.Mixins.<Boolean>get(fromConst, "packVoxelizes"));
        } finally {
            fromConst.destroy();
        }
        ShaderPack legacy = PackSourcesTest.pack("/shaders.properties", "image.voxels = voxel_sampler rgba_integer rgba8ui unsigned_int true false 16 16 16",
                "/shadow.vsh", "#define SHADOWFOV 90.0\n" + VERTEX, "/shadow.fsh", "void main() {}");
        UmbraShadowRenderer fromDefine = create(legacy);
        try {
            assertEquals(90.0f, com.bdmajora.testing.Mixins.<Float>get(fromDefine, "shadowMapFov"));
            assertTrue(com.bdmajora.testing.Mixins.<Boolean>get(fromDefine, "packVoxelizes"));
        } finally {
            fromDefine.destroy();
        }
    }

    @Test
    void distantHorizonsShadowsWidenTheUndeclaredDepthRangeToTheLodDistance() throws Exception {
        java.lang.invoke.MethodHandle enabled = com.bdmajora.testing.Statics.get(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "isRenderingEnabled");
        java.lang.invoke.MethodHandle distance = com.bdmajora.testing.Statics.get(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "getRenderDistance");
        boolean present = com.bdmajora.testing.Statics.get(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "dhPresent");
        com.bdmajora.testing.Statics.set(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "isRenderingEnabled", java.lang.invoke.MethodHandles.constant(boolean.class, true));
        com.bdmajora.testing.Statics.set(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "getRenderDistance", java.lang.invoke.MethodHandles.constant(int.class, 512));
        com.bdmajora.testing.Statics.set(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "dhPresent", true);
        try {
            String[] programs = {"/shadow.vsh", VERTEX, "/shadow.fsh", "void main() {}", "/dh_shadow.vsh", VERTEX, "/dh_shadow.fsh", "void main() {}"};
            UmbraShadowRenderer lod = create(PackSourcesTest.pack(programs));
            try {
                assertEquals(-512.0f, com.bdmajora.testing.Mixins.<Float>get(lod, "nearPlane"));
                assertEquals(512.0f, com.bdmajora.testing.Mixins.<Float>get(lod, "farPlane"));
            } finally {
                lod.destroy();
            }
            String[] off = new String[programs.length + 2];
            off[0] = "/shaders.properties";
            off[1] = "dhShadow.enabled = false";
            System.arraycopy(programs, 0, off, 2, programs.length);
            UmbraShadowRenderer plain = create(PackSourcesTest.pack(off));
            try {
                assertEquals(UmbraShadowRenderer.DEFAULT_NEAR_PLANE, com.bdmajora.testing.Mixins.<Float>get(plain, "nearPlane"));
            } finally {
                plain.destroy();
            }
            UmbraShadowRenderer noDhProgram = create(PackSourcesTest.pack("/shadow.vsh", VERTEX, "/shadow.fsh", "void main() {}"));
            try {
                assertEquals(UmbraShadowRenderer.DEFAULT_FAR_PLANE, com.bdmajora.testing.Mixins.<Float>get(noDhProgram, "farPlane"));
            } finally {
                noDhProgram.destroy();
            }
        } finally {
            com.bdmajora.testing.Statics.set(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "dhPresent", present);
            com.bdmajora.testing.Statics.set(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "isRenderingEnabled", enabled);
            com.bdmajora.testing.Statics.set(com.bdmajora.impetus.umbra.compat.dh.DhCompat.class, "getRenderDistance", distance);
        }
    }
}
