package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Clears the allocated shadow colour buffers that the pack wants cleared at the start of each pass
public final class ShadowColorClears {
    // Byte size of one integer clear colour with four 32 bit components
    private static final int INTEGER_COLOR_BYTES = 16;

    // Buffer index per clear in ascending order
    private final int[] indices;
    // Texture name per clear
    private final int[] textures;
    // Pixel format per clear
    private final int[] pixelFormats;
    // Integer clear colour per clear or null for a clear through the framebuffer
    private final ByteBuffer[] integerColors;
    // Float clear colours as four consecutive components per clear
    private final float[] colors;
    // Single entry draw buffer mask reused by every framebuffer clear
    private final int[] singleBuffer = new int[1];

    // Creates the clear list for the allocated buffers and reads the integer clear support once
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
            // A float clear colour is undefined for integer formats so those clear the texture directly
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

    // Returns how many buffers each pass clears
    public int size() {
        return this.indices.length;
    }

    // Clears each listed buffer to its colour and leaves the clear colour and draw buffers changed
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
