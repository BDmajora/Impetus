package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Clears of the shadow color textures that the pack marks for clearing.
public final class ShadowColorClears {
    // Bytes of an integer clear color.
    private static final int INTEGER_COLOR_BYTES = 16;

    // Shadow color indices that clear.
    private final int[] indices;
    // GL names of the cleared textures.
    private final int[] textures;
    // Pixel format of each cleared texture.
    private final int[] pixelFormats;
    // Integer clear color of each texture or null.
    private final ByteBuffer[] integerColors;
    // Float clear colors in groups of four.
    private final float[] colors;
    // Scratch of a single draw buffer.
    private final int[] singleBuffer = new int[1];

    // Collects the clears of the allocated shadow color textures.
    public ShadowColorClears(ShadowColorSettings settings, int[] allocated, int[] textures, boolean integerClearSupported) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(allocated, "allocated");
        Objects.requireNonNull(textures, "textures");
        int count = 0;
        for (int index : allocated) {
            if (settings.clear(index)) {
                count++;
            }
        }
        this.indices = new int[count];
        this.textures = new int[count];
        this.pixelFormats = new int[count];
        this.integerColors = new ByteBuffer[count];
        this.colors = new float[count * 4];
        int entry = 0;
        for (int index : allocated) {
            if (!settings.clear(index)) {
                continue;
            }
            float[] color = settings.clearColor(index);
            InternalTextureFormat format = settings.format(index);
            this.indices[entry] = index;
            this.textures[entry] = textures[index];
            this.pixelFormats[entry] = format.getPixelFormat();
            System.arraycopy(color, 0, this.colors, entry * 4, 4);
            if (format.isInteger() && integerClearSupported) {
                ByteBuffer data = ByteBuffer.allocateDirect(INTEGER_COLOR_BYTES).order(ByteOrder.nativeOrder());
                for (int component = 0; component < 4; component++) {
                    data.putInt(component * 4, (int) color[component]);
                }
                this.integerColors[entry] = data;
            }
            entry++;
        }
    }

    // Returns the number of cleared textures.
    public int size() {
        return this.indices.length;
    }

    // Clears every texture with a texture clear or a framebuffer clear.
    public void run(UmbraFramebuffer framebuffer) {
        for (int entry = 0; entry < this.indices.length; entry++) {
            ByteBuffer data = this.integerColors[entry];
            if (data != null) {
                LWJGL.glClearTexImage(this.textures[entry], 0, this.pixelFormats[entry], GL11.GL_INT, data);
                continue;
            }
            this.singleBuffer[0] = this.indices[entry];
            framebuffer.drawBuffers(this.singleBuffer);
            int base = entry * 4;
            GlStateManager.clearColor(this.colors[base], this.colors[base + 1], this.colors[base + 2], this.colors[base + 3]);
            LWJGL.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }
}
