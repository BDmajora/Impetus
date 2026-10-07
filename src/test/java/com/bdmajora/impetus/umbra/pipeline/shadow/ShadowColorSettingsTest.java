package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShadowColorSettingsTest {
    @Test
    void eachBufferReadsItsOwnDirectives() {
        ConstDirectives consts = new ConstDirectives(String.join("\n",
                "const bool generateShadowColorMipmap = true;",
                "const int shadowcolor1Format = RGBA16F;",
                "const int shadowcolor2Format = R32UI;",
                "const int shadowcolor3Format = NOT_A_FORMAT;",
                "const bool shadowcolor1Clear = false;",
                "const vec4 shadowcolor1ClearColor = vec4(0.25);",
                "const bool shadowcolor0Mipmap = false;",
                "const bool shadowColor3Mipmap = false;",
                "const bool shadowcolor0Nearest = true;",
                "const bool shadowColor4Nearest = true;",
                "const bool shadowColor5MinMagNearest = true;"));
        ShadowColorSettings settings = ShadowColorSettings.parse(consts, ShadowColorSettings.IRIS_BUFFERS);
        assertEquals(8, settings.count());

        assertEquals(InternalTextureFormat.RGBA, settings.format(0));
        assertEquals(InternalTextureFormat.RGBA16F, settings.format(1));
        assertEquals(InternalTextureFormat.RGBA, settings.format(3));
        assertTrue(settings.clear(0));
        assertFalse(settings.clear(1));
        assertArrayEquals(new float[] {0.25f, 0.25f, 0.25f, 0.25f}, settings.clearColor(1));
        assertArrayEquals(new float[] {1, 1, 1, 1}, settings.clearColor(2));
        // The shared switch first, then the per-buffer spellings override it
        assertFalse(settings.mipmap(0));
        assertTrue(settings.mipmap(1));
        assertFalse(settings.mipmap(3));
        assertTrue(settings.nearest(0));
        assertFalse(settings.nearest(1));
        // Integer formats cannot be filtered at all
        assertTrue(settings.nearest(2));
        assertTrue(settings.nearest(4));
        assertTrue(settings.nearest(5));

        ShadowColorSettings defaults = ShadowColorSettings.parse(new ConstDirectives(""), ShadowColorSettings.OPTIFINE_BUFFERS);
        assertEquals(2, defaults.count());
        assertFalse(defaults.mipmap(1));
        assertEquals(InternalTextureFormat.RGBA, defaults.format(1));
    }
}
