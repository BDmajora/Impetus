package com.bdmajora.impetus.umbra.gl.texture;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureFilteringData;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A GL texture from a pack's PNG bytes (Iris's NativeImageBackedCustomTexture); filtering and wrapping follow the .mcmeta sidecar exactly (NEAREST/REPEAT default, `blur` LINEAR, `clamp` CLAMP_TO_EDGE), decoded through ImageIO like vanilla
public class PngTexture extends GlResource {
    private final int width;
    private final int height;

    public PngTexture(CustomTextureData.PngData data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data.getContent()));
        if (image == null) {
            throw new IOException("Not a decodable image");
        }
        this.width = image.getWidth();
        this.height = image.getHeight();

        int[] argb = image.getRGB(0, 0, this.width, this.height, null, 0, this.width);
        ByteBuffer pixels = ByteBuffer.allocateDirect(this.width * this.height * 4).order(ByteOrder.nativeOrder());
        for (int pixel : argb) {
            pixels.put((byte) (pixel >> 16));
            pixels.put((byte) (pixel >> 8));
            pixels.put((byte) pixel);
            pixels.put((byte) (pixel >> 24));
        }
        pixels.flip();

        TextureFilteringData filtering = data.getFilteringData();
        int filter = filtering.shouldBlur() ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        int wrap = filtering.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;

        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(filter, wrap);
        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, this.width, this.height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // For binding as a custom sampler
    public int getTextureId() {
        return getGlId();
    }

    // Decoded width
    public int getWidth() {
        return this.width;
    }

    // Decoded height
    public int getHeight() {
        return this.height;
    }

    // Frees the texture
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
