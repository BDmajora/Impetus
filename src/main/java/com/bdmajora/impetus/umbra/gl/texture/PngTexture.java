package com.bdmajora.impetus.umbra.gl.texture;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureFilteringData;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Texture made from a PNG file.
public class PngTexture extends GlResource {
    // Width in pixels.
    private final int width;
    // Height in pixels.
    private final int height;

    // Creates a png texture from its data.
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

    // Returns the texture id.
    public int getTextureId() {
        return getGlId();
    }

    // Returns the width.
    public int getWidth() {
        return this.width;
    }

    // Returns the height.
    public int getHeight() {
        return this.height;
    }

    // Deletes the texture.
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
