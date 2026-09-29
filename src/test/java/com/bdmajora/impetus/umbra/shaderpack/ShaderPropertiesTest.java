package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTexturePatch;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPropertiesTest {
    // Menu layout is read from the file as shipped
    private static final String ORIGINAL = String.join("\n",
            "# a comment",
            "! another comment",
            "not a directive",
            "= orphan value",
            "sliders = SHADOW_QUALITY SUN_ANGLE",
            "screen = <profile> <empty> SHADOW_QUALITY [LIGHTING]",
            "screen.LIGHTING = BLOOM   SUN_ANGLE",
            "screen.columns = 2",
            "screen.LIGHTING.columns = 3",
            "screen.WIDE.columns = many",
            "profile.LOW = !BLOOM SHADOW_QUALITY=1",
            "profile.HIGH = BLOOM SHADOW_QUALITY=3");

    // Pipeline directives come from the preprocessed text
    private static final String PREPROCESSED = String.join("\r\n",
            "shadowMapResolution = 2048",
            "shadowDistance = far",
            "fallbackTex = 3",
            "shadow.enabled = true",
            "dhShadow.enabled = false",
            "clouds = Fast",
            "dhClouds = OFF",
            "sun = false",
            "moon = TRUE",
            "stars = on",
            "sky = BLOOM",
            "vignette = (",
            "underwaterOverlay = true",
            "weather = false",
            "beacon.beam.depth = true",
            "rain.depth = false",
            "separateAo = true",
            "oldLighting = false",
            "oldHandLight = true",
            "dynamicHandLight = true",
            "frustum.culling = false",
            "occlusion.culling = true",
            "backFace.solid = false",
            "shadowTerrain = true",
            "shadowTranslucent = false",
            "shadowEntities = true",
            "shadowBlockEntities = false",
            "shadowLightBlockEntities = true",
            "shadowPlayer = false",
            "shadow.culling = Reversed",
            "voxelizeLightBlocks = true",
            "separateEntityDraws = false",
            "prepareBeforeShadow = true",
            "allowConcurrentCompute = false",
            "supportsColorCorrection = true",
            "breaksAnisotropy = false",
            "skipAllRendering = false",
            "particles.ordering = After",
            "alphaTest.gbuffers_water = off",
            "blend.composite2 = SRC_ALPHA ONE_MINUS_SRC_ALPHA",
            "program.composite4.enabled = false",
            "program.world0/deferred.enabled = true",
            "program.composite5..enabled = false",
            "texture.noise = tex/noise.png",
            "texture.composite.colortex5 = tex/lut.png",
            "texture.gbuffers.gaux1.2 = minecraft:textures/misc/x.png",
            "texture.deferred.colortex6 = tex/volume.dat TEXTURE_3D RGBA8 4 4 4 RGBA UNSIGNED_BYTE",
            "texture.deferred.colortex7 = tex/line.dat TEXTURE_1D RGBA8 4 RGBA UNSIGNED_BYTE",
            "texture.prepare.colortex8 = tex/rect.dat texture_rectangle RGBA8 4 4 RGBA UNSIGNED_BYTE",
            "texture.shadowcomp.colortex9 = a b c",
            "texture.bogus.colortex1 = x.png",
            "texture.nodot = x",
            "texture.gbuffers. = x",
            "customTexture.lut = tex/lut.png ",
            "customTexture. = x",
            "size.buffer.colortex1 = RES RES",
            "size.buffer.colortex2 = 0.5 0.5",
            "size.buffer.gaux1 = 512 0.5",
            "size.buffer.nottarget = 1 1",
            "size.buffer.colortex3 = 1",
            "size.buffer.colortex6 = 0 1",
            "size.buffer.colortex7 = UNDEFINED 1",
            "size.buffer.colortex8 = -1 .5",
            "size.buffer.colortexX = 1 1",
            "image.voxels = voxel_sampler RGBA RGBA8 UNSIGNED_BYTE false false 32 32 32",
            "image.screen = screen_sampler RGBA RGBA16F HALF_FLOAT true true 0.5 0.5",
            "image.flat = flat_sampler RGBA RGBA8 UNSIGNED_BYTE true false 16 16",
            "image.bad = x",
            "image.badnum = s RGBA RGBA8 UNSIGNED_BYTE false false a b",
            "flip.composite.colortex1 = true",
            "flip.composite = true",
            "flip.composite. = true",
            "flip.composite.colortex2 = maybe",
            "flip.composite.nottarget = false",
            "flip.deferred_pre.gaux2 = false",
            "scale.composite1 = 0.5",
            "scale.composite2 = 0.5 0.25 0.25",
            "scale.composite3 = 0.5 0.25",
            "scale.composite4 = x",
            "scale. = 1",
            "uniform.float.myUniform = frameTimeCounter * 2.0",
            "variable.float.myVariable = 1.0",
            "uniform.nodot = 1");

    private static ShaderProperties parsed() {
        return ShaderProperties.parse(ORIGINAL, PREPROCESSED, Map.of("RES", " 512 ", "BLOOM", "1"), Set.of());
    }

    @Test
    void theMenuLayoutComesFromTheShippedFile() {
        ShaderProperties properties = parsed();
        assertEquals(List.of("SHADOW_QUALITY", "SUN_ANGLE"), properties.getSliderOptions());
        assertEquals(Optional.of(List.of("<profile>", "<empty>", "SHADOW_QUALITY", "[LIGHTING]")), properties.getMainScreenOptions());
        assertEquals(Map.of("LIGHTING", List.of("BLOOM", "SUN_ANGLE")), properties.getSubScreenOptions());
        assertEquals(Optional.of(2), properties.getMainScreenColumnCount());
        // A column count that is not a number is dropped
        assertEquals(Map.of("LIGHTING", 3), properties.getSubScreenColumnCount());
        assertEquals(List.of("LOW", "HIGH"), List.copyOf(properties.getProfiles().keySet()));
        assertEquals(List.of("!BLOOM", "SHADOW_QUALITY=1"), properties.getProfiles().get("LOW"));
        // None of it leaks into the pipeline view
        assertFalse(properties.getRaw().containsKey("sliders"));
        assertSame(properties.getRaw(), properties.asMap());
    }

    @Test
    void eachTypedDirectiveReadsItsKey() {
        ShaderProperties p = parsed();
        assertEquals(OptionalInt.of(2048), p.getShadowMapResolution());
        assertEquals(OptionalInt.empty(), p.getShadowDistance());
        assertEquals(OptionalInt.of(3), p.getFallbackTex());
        assertEquals(Optional.of(true), p.getShadowEnabled());
        assertEquals(Optional.of(false), p.getDhShadowEnabled());
        assertEquals(Optional.of("fast"), p.getCloudMode());
        assertEquals(Optional.of("off"), p.getDhCloudMode());
        assertEquals(Optional.of(false), p.getRenderSun());
        assertEquals(Optional.of(true), p.getRenderMoon());
        // Anything but true/false is evaluated as an expression over the defines
        assertEquals(Optional.of(false), p.getRenderStars());
        assertEquals(Optional.of(true), p.getRenderSky());
        assertEquals(Optional.empty(), p.getRenderVignette());
        assertEquals(Optional.of(true), p.getRenderUnderwaterOverlay());
        assertEquals(Optional.of(false), p.getRenderWeather());
        assertEquals(Optional.of(true), p.getBeaconBeamDepth());
        assertEquals(Optional.of(false), p.getRainDepth());
        assertEquals(Optional.of(true), p.getSeparateAo());
        assertEquals(Optional.of(false), p.getOldLighting());
        assertEquals(Optional.of(true), p.getOldHandLight());
        assertEquals(Optional.of(true), p.getDynamicHandLight());
        assertEquals(Optional.of(false), p.getFrustumCulling());
        assertEquals(Optional.of(true), p.getOcclusionCulling());
        assertEquals(Optional.of(false), p.getBackFaceCulling("solid"));
        assertEquals(Optional.empty(), p.getBackFaceCulling("translucent"));
        assertEquals(Optional.of(true), p.getShadowTerrain());
        assertEquals(Optional.of(false), p.getShadowTranslucent());
        assertEquals(Optional.of(true), p.getShadowEntities());
        assertEquals(Optional.of(false), p.getShadowBlockEntities());
        assertEquals(Optional.of(true), p.getShadowLightBlockEntities());
        assertEquals(Optional.of(false), p.getShadowPlayer());
        assertEquals(Optional.of("reversed"), p.getShadowCulling());
        assertEquals(Optional.of(true), p.getVoxelizeLightBlocks());
        assertEquals(Optional.of(false), p.getSeparateEntityDraws());
        assertEquals(Optional.of(true), p.getPrepareBeforeShadow());
        assertEquals(Optional.of(false), p.getAllowConcurrentCompute());
        assertEquals(Optional.of(true), p.getSupportsColorCorrection());
        assertEquals(Optional.of(false), p.getBreaksAnisotropy());
        assertEquals(Optional.of(false), p.getSkipAllRendering());
        assertEquals(Optional.of("after"), p.getParticleOrdering());
        assertEquals(Optional.of("off"), p.getAlphaTestOverride("gbuffers_water"));
        assertEquals(Optional.of("SRC_ALPHA ONE_MINUS_SRC_ALPHA"), p.getBlendModeOverride("composite2"));
        assertEquals(Optional.of("2048"), p.get("shadowMapResolution"));
        assertEquals(Optional.empty(), p.get("missing"));
    }

    @Test
    void particleOrderingFallsBackToTheLegacySwitch() {
        assertEquals(Optional.of("before"), ShaderProperties.parse("particles.before.deferred = true").getParticleOrdering());
        assertEquals(Optional.empty(), ShaderProperties.parse("particles.before.deferred = false").getParticleOrdering());
        assertEquals(Optional.empty(), ShaderProperties.empty().getParticleOrdering());
        assertEquals(Optional.empty(), ShaderProperties.parse("", "").getMainScreenOptions());
    }

    @Test
    void programsCanBeSwitchedOffByDirectiveOrProfile() {
        ShaderProperties p = parsed();
        assertEquals(Optional.of(false), p.getProgramEnabled("composite4"));
        assertEquals(Optional.of(true), p.getProgramEnabled("deferred"));
        // The doubled-dot spelling some packs use
        assertEquals(Optional.of(false), p.getProgramEnabled("composite5"));
        assertEquals(Optional.empty(), p.getProgramEnabled("composite6"));
        ShaderProperties profiled = p.withProfileDisabledPrograms(Set.of("composite6", "world0/composite7"));
        assertEquals(Optional.of(false), profiled.getProgramEnabled("composite6"));
        assertEquals(Optional.of(false), profiled.getProgramEnabled("composite7"));
        assertEquals(Optional.empty(), profiled.getProgramEnabled("composite8"));
    }

    @Test
    void texturesAreScopedByStageAndRawOnesGetAMintedSampler() {
        ShaderProperties p = parsed();
        assertEquals(Optional.of("tex/noise.png"), p.getNoiseTexturePath());
        assertEquals(Map.of("colortex5", "tex/lut.png"), p.getCustomTextures().get(TextureStage.COMPOSITE_AND_FINAL));
        // The mip-level suffix is dropped
        assertEquals(Map.of("gaux1", "minecraft:textures/misc/x.png"), p.getCustomTextures().get(TextureStage.GBUFFERS_AND_SHADOW));
        assertFalse(p.getCustomTextures().containsKey(TextureStage.DEFERRED));

        List<CustomTexturePatch> patches = p.getCustomTexturePatches();
        assertEquals(3, patches.size());
        assertEquals("colortex6", patches.get(0).getSamplerName());
        assertEquals(TextureStage.DEFERRED, patches.get(0).getStage());
        assertEquals("TEXTURE_3D", patches.get(0).getTextureType());
        assertEquals("customtex0", patches.get(0).getNewSamplerName());
        assertEquals("TEXTURE_1D", patches.get(1).getTextureType());
        assertEquals("TEXTURE_RECTANGLE", patches.get(2).getTextureType());
        assertEquals("colortex8 (TEXTURE_RECTANGLE, PREPARE) -> customtex2", patches.get(2).toString());

        Map<String, String> custom = p.getUmbraCustomTextures();
        assertEquals("tex/lut.png", custom.get("lut"));
        assertEquals("tex/volume.dat TEXTURE_3D RGBA8 4 4 4 RGBA UNSIGNED_BYTE", custom.get("customtex0"));
        assertEquals(4, custom.size());
    }

    @Test
    void bufferSizesAreTexelsOrFractionsPerAxis() {
        ShaderProperties p = parsed();
        Map<Integer, float[]> sizes = p.getBufferSizes();
        assertEquals(Set.of(1, 2, 4), sizes.keySet());
        // An option name resolves to its value
        assertArrayEquals(new float[] {512, 512}, sizes.get(1));
        assertArrayEquals(new boolean[] {false, false}, p.getBufferSizeRelative(1));
        assertArrayEquals(new boolean[] {true, true}, p.getBufferSizeRelative(2));
        assertArrayEquals(new boolean[] {false, true}, p.getBufferSizeRelative(4));
        assertArrayEquals(new boolean[] {false, false}, p.getBufferSizeRelative(9));
    }

    @Test
    void imagesFlipsAndScalesParse() {
        ShaderProperties p = parsed();
        List<CustomImageDefinition> images = p.getUmbraCustomImages();
        assertEquals(3, images.size());
        CustomImageDefinition voxels = images.get(0);
        assertEquals("voxels", voxels.name);
        assertEquals("voxel_sampler", voxels.samplerName);
        assertEquals("RGBA", voxels.format);
        assertEquals("RGBA8", voxels.internalFormat);
        assertEquals("UNSIGNED_BYTE", voxels.pixelType);
        assertFalse(voxels.clear);
        assertFalse(voxels.relative);
        assertEquals(32, voxels.sizeZ);
        CustomImageDefinition screen = images.get(1);
        assertTrue(screen.relative);
        assertTrue(screen.clear);
        assertEquals(0.5F, screen.relativeX);
        assertEquals(0.5F, screen.relativeY);
        assertEquals(0, images.get(2).sizeZ);
        assertEquals(16, images.get(2).sizeX);

        assertEquals(Map.of(1, true), p.getExplicitFlips("composite"));
        assertEquals(Map.of(5, false), p.getExplicitFlips("deferred_pre"));
        assertTrue(p.getExplicitFlips("final").isEmpty());

        assertArrayEquals(new float[] {0.5F, 0, 0}, p.getViewportScale("composite1"));
        assertArrayEquals(new float[] {0.5F, 0.25F, 0.25F}, p.getViewportScale("composite2"));
        assertNull(p.getViewportScale("composite3"));
        assertNull(p.getViewportScale("composite4"));

        assertFalse(p.getCustomUniforms().isEmpty());
        assertTrue(ShaderProperties.empty().getCustomUniforms().isEmpty());
    }
}
