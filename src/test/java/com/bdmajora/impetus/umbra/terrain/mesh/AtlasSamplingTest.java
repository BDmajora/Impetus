package com.bdmajora.impetus.umbra.terrain.mesh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtlasSamplingTest {
    @Test
    void implicitLookupsGainOrExtendABias() {
        assertEquals("texture2D(texture, uv, UMBRA_ATLAS_BIAS)", AtlasSampling.rewrite("texture2D(texture, uv)"));
        assertEquals("texture(gtexture, uv, (bias) + UMBRA_ATLAS_BIAS)", AtlasSampling.rewrite("texture(gtexture, uv, bias)"));
        assertEquals("textureOffset(tex, uv, ivec2(1, 0), UMBRA_ATLAS_BIAS)", AtlasSampling.rewrite("textureOffset(tex, uv, ivec2(1, 0))"));
        assertEquals("texture2DProj( gcolor , p, UMBRA_ATLAS_BIAS )", AtlasSampling.rewrite("texture2DProj( gcolor , p )"));
        // A call with more arguments than any overload is left for the compiler to reject
        assertEquals("texture(tex, uv, a, b)", AtlasSampling.rewrite("texture(tex, uv, a, b)"));
    }

    @Test
    void explicitLevelsAndGradientsScaleToTheBaseLevel() {
        assertEquals("textureLod(tex, uv, (lod + 1.0) * umbra_Mipped)", AtlasSampling.rewrite("textureLod(tex, uv, lod + 1.0)"));
        assertEquals("texture2DGradARB(texture, uv, (dFdx(uv)) * umbra_Mipped, (dFdy(uv)) * umbra_Mipped)",
                AtlasSampling.rewrite("texture2DGradARB(texture, uv, dFdx(uv), dFdy(uv))"));
        assertEquals("textureGradOffset(tex, uv, (a) * umbra_Mipped, (b) * umbra_Mipped, ivec2(0))",
                AtlasSampling.rewrite("textureGradOffset(tex, uv, a, b, ivec2(0))"));
        // A gradient call cut short only gets the arguments it has
        assertEquals("textureGrad(tex, uv, (a) * umbra_Mipped)", AtlasSampling.rewrite("textureGrad(tex, uv, a)"));
    }

    @Test
    void onlyTheAtlasIsTouchedNestingIncluded() {
        String other = "texture(noisetex, uv) + texelFetch(tex, ivec2(0), 0) + textureSize(tex, 0).x";
        assertEquals(other, AtlasSampling.rewrite(other));
        assertFalse(AtlasSampling.samplesAtlas(other));
        assertFalse(AtlasSampling.samplesAtlas("uniform sampler2D texture; float x = texture;"));

        // A lookup inside another's arguments is rewritten too, and indexing brackets do not end an argument
        assertEquals("textureLod(tex, uv[0], (texture(tex, uv[1], UMBRA_ATLAS_BIAS).r) * umbra_Mipped)",
                AtlasSampling.rewrite("textureLod(tex, uv[0], texture(tex, uv[1]).r)"));
        assertTrue(AtlasSampling.samplesAtlas("vec4 c = texture2D(texture, texcoord);"));
    }

    @Test
    void commentsAndBrokenCallsAreSkipped() {
        String commented = "// texture2D(tex, uv)\n/* texture(tex, ( */ texture2D(tex, uv);";
        assertEquals("// texture2D(tex, uv)\n/* texture(tex, ( */ texture2D(tex, uv, UMBRA_ATLAS_BIAS);", AtlasSampling.rewrite(commented));
        assertEquals("texture2D(tex, uv", AtlasSampling.rewrite("texture2D(tex, uv"));
        assertEquals("texture2D()", AtlasSampling.rewrite("texture2D()"));
        assertEquals("texture2D(, uv)", AtlasSampling.rewrite("texture2D(, uv)"));
    }
}
