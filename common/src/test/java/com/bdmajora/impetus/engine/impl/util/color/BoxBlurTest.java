package com.bdmajora.impetus.engine.impl.util.color;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoxBlurTest {
    @Test
    void uniformBuffersAreLeftUntouched() {
        BoxBlur.ColorBuffer buf = new BoxBlur.ColorBuffer(4, 4);
        BoxBlur.ColorBuffer tmp = new BoxBlur.ColorBuffer(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                buf.set(x, y, 0xFF123456);
            }
        }
        BoxBlur.blur(buf, tmp, 1);
        assertEquals(0xFF123456, buf.get(3, 3));
        assertEquals(0, tmp.get(0, 0));
    }

    @Test
    void blurSpreadsASinglePixel() {
        BoxBlur.ColorBuffer buf = new BoxBlur.ColorBuffer(5, 5);
        BoxBlur.ColorBuffer tmp = new BoxBlur.ColorBuffer(5, 5);
        buf.set(2, 2, ColorARGB.pack(255, 0, 0));
        BoxBlur.blur(buf, tmp, 1);
        int centre = buf.get(2, 2);
        int corner = buf.get(0, 0);
        assertTrue(ColorARGB.unpackRed(centre) > 0);
        assertTrue(ColorARGB.unpackRed(buf.get(1, 2)) > 0);
        assertEquals(0, ColorARGB.unpackRed(corner));
        assertEquals(0xFF, ColorARGB.unpackAlpha(centre));
        assertEquals(6, BoxBlur.ColorBuffer.getIndex(1, 1, 5));
    }

    @Test
    void mismatchedBuffersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BoxBlur.blur(new BoxBlur.ColorBuffer(2, 2), new BoxBlur.ColorBuffer(3, 2), 1));
        int avg = BoxBlur.averageRGB(300, 600, 900, (int) Math.ceil((1L << 24) / 3.0));
        assertEquals(100, ColorARGB.unpackRed(avg));
        assertEquals(200, ColorARGB.unpackGreen(avg));
        assertEquals(255 & 300, ColorARGB.unpackBlue(avg));
        new BoxBlur();
    }
}
