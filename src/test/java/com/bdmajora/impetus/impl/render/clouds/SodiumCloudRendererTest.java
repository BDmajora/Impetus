package com.bdmajora.impetus.impl.render.clouds;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SodiumCloudRendererTest {
    private static final ResourceLocation CLOUDS = new ResourceLocation("textures/environment/clouds.png");

    private Minecraft client;
    private IResource resource;
    private boolean openGl14;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void freshTexture() throws IOException {
        client = Mc.client();
        resource = mock(IResource.class);
        when(client.getResourceManager().getResource(CLOUDS)).thenReturn(resource);
        forget();
        // Blending goes through GL11 unless an earlier test left OpenGL 1.4 switched on
        openGl14 = Statics.get(OpenGlHelper.class, "openGL14");
        Statics.set(OpenGlHelper.class, "openGL14", false);
    }

    @AfterEach
    void restore() {
        forget();
        Statics.set(OpenGlHelper.class, "openGL14", openGl14);
    }

    // Drops the parsed texture so the next call reads it again
    private static void forget() {
        Mixins.set(SodiumCloudRenderer.class, "cachedTextureReloadCount", -1);
        Mixins.set(SodiumCloudRenderer.class, "cachedCells", null);
    }

    // A 4x4 cloud map with an L of three cloud cells in its middle; gaps are stored with alpha 1, as vanilla's are
    private static byte[] clouds() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, z, 0x01FFFFFF);
            }
        }
        image.setRGB(1, 1, 0xFFFFFFFF);
        image.setRGB(2, 1, 0xFFFFFFFF);
        image.setRGB(1, 2, 0xFFFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void anUnreadableTextureLeavesTheCloudsToVanilla() throws IOException {
        WorldClient world = mock(WorldClient.class);
        TextureManager textures = mock(TextureManager.class);
        when(client.getResourceManager().getResource(CLOUDS)).thenThrow(new FileNotFoundException("clouds.png"));
        assertFalse(SodiumCloudRenderer.isReady(client));
        assertFalse(SodiumCloudRenderer.render(client, world, textures, 0, 0F, 2, 0, 100, 0, true, 8, 128F, SodiumCloudRenderer.CELL_SIZE));
        verify(textures, Mockito.never()).bindTexture(CLOUDS);
        // Bytes that are not an image are as good as no texture
        forget();
        IReloadableResourceManager resources = (IReloadableResourceManager) client.getResourceManager();
        Mockito.doReturn(resource).when(resources).getResource(CLOUDS);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        assertFalse(SodiumCloudRenderer.isReady(client));
    }

    @Test
    void theLayerIsMeshedForEveryCameraPosition() throws IOException {
        byte[] png = clouds();
        when(resource.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(png));
        assertTrue(SodiumCloudRenderer.isReady(client));
        WorldClient world = mock(WorldClient.class);
        when(world.getCloudColour(0.5F)).thenReturn(new Vec3d(1.0, 0.9, 0.8));
        TextureManager textures = mock(TextureManager.class);
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class);
             MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class);
             MockedStatic<GL14> gl14 = Mockito.mockStatic(GL14.class)) {
            // The camera sits in the cloud cell at (1, 1): 18 / 12 = 1.5 across and 12 / 12 + 0.33 down
            float cell = SodiumCloudRenderer.CELL_SIZE;
            // Fast clouds, seen from below and then above, in both eyes of anaglyph and the ordinary pass
            assertTrue(SodiumCloudRenderer.render(client, world, textures, 0, 0.5F, 2, 18, 100, 12, false, 3, 128F, cell));
            assertTrue(SodiumCloudRenderer.render(client, world, textures, 0, 0.5F, 0, 18, 200, 12, false, 3, 128F, cell));
            // Fancy clouds from below, from above and from inside the layer, where the nearby cells turn inside out
            assertTrue(SodiumCloudRenderer.render(client, world, textures, 0, 0.5F, 2, 18, 100, 12, true, 3, 128F, cell));
            assertTrue(SodiumCloudRenderer.render(client, world, textures, 0, 0.5F, 1, 18, 200, 12, true, 3, 128F, cell));
            assertTrue(SodiumCloudRenderer.render(client, world, textures, 0, 0.5F, 2, 18, 130, 12, true, 3, 128F, cell));
            // A radius of zero still draws the cell around the camera, and a far-out camera wraps back into the texture
            assertTrue(SodiumCloudRenderer.render(client, world, textures, 40000, 0.5F, 2, 5_000_000, 130, -5_000_000, true, 0, 128F, cell * 2));
            verify(textures, Mockito.times(6)).bindTexture(CLOUDS);
            gl11.verify(() -> GL11.glDrawArrays(eq(GL11.GL_QUADS), eq(0), anyInt()), Mockito.atLeast(5));
        }
        assertEquals(0, Tessellator.getInstance().getBuffer().getVertexCount());
    }
}
