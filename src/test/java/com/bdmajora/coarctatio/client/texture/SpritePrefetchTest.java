package com.bdmajora.coarctatio.client.texture;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.texture.CustomLoaderSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpritePrefetchTest {
    private static final ResourceLocation LOCATION = new ResourceLocation("minecraft:textures/blocks/stone.png");
    private static byte[] png;

    @BeforeAll
    static void bootstrap() throws IOException {
        Mc.bootstrap();
        Mc.textures();
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(3, 4, 0xFF00FF00);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        png = bytes.toByteArray();
    }

    @AfterEach
    void dropWhatWasPrefetched() {
        SpritePrefetch.clear();
    }

    // A sprite the prefetch can read, without going near Mockito: instrumenting the sprite class would undo the
    // constructor the harness adds to it
    private static TextureAtlasSprite sprite(String name) {
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", name);
        return sprite;
    }

    private static IResourceManager manager(IMetadataSection animation, boolean hasMetadata) throws IOException {
        IResourceManager manager = mock(IResourceManager.class);
        when(manager.getResource(any())).thenAnswer(invocation -> {
            IResource resource = mock(IResource.class);
            when(resource.getInputStream()).thenAnswer(stream -> new ByteArrayInputStream(png));
            when(resource.getResourcePackName()).thenReturn("test pack");
            when(resource.hasMetadata()).thenReturn(hasMetadata);
            when(resource.getMetadata("animation")).thenReturn(animation);
            when(resource.getMetadata("texture")).thenReturn(null);
            return resource;
        });
        return manager;
    }

    @Test
    void everySpriteIsDecodedBeforeTheAtlasLoopStarts() throws Exception {
        IMetadataSection animation = mock(IMetadataSection.class);
        IResourceManager manager = manager(animation, true);
        TextureAtlasSprite stone = sprite("minecraft:blocks/stone");
        TextureAtlasSprite ownLoader = new CustomLoaderSprite("minecraft:blocks/custom");
        List<TextureAtlasSprite> sprites = new ArrayList<>(Arrays.asList(stone, ownLoader));

        SpritePrefetch.run(manager, sprites, s -> LOCATION);
        // A sprite that loads itself is left to its own loader
        assertNull(SpritePrefetch.resource(ownLoader));

        SpritePrefetch.PrefetchedResource prefetched = (SpritePrefetch.PrefetchedResource) SpritePrefetch.resource(stone);
        assertNotNull(prefetched);
        assertEquals(LOCATION, prefetched.getResourceLocation());
        assertEquals("test pack", prefetched.getResourcePackName());
        assertTrue(prefetched.hasMetadata());
        assertEquals(16, prefetched.sizeInfo().pngWidth);
        assertEquals(16, prefetched.sizeInfo().pngHeight);
        // The animation section was read ahead of time; anything else reopens the real resource
        assertSame(animation, prefetched.getMetadata("animation"));
        assertNull(prefetched.getMetadata("texture"));

        // The decoded image is handed over once, and a second reader falls back to the file
        BufferedImage taken = prefetched.takeImage();
        assertNotNull(taken);
        assertEquals(0xFF00FF00, taken.getRGB(3, 4));
        assertNull(prefetched.takeImage());
        prefetched.close();

        SpritePrefetch.clear();
        assertNull(SpritePrefetch.resource(stone));
        assertNotNull(Mixins.construct(SpritePrefetch.class));
    }

    @Test
    void theStreamAPrefetchedResourceHandsOutReadsTheRealFile() throws Exception {
        IResourceManager manager = manager(null, false);
        TextureAtlasSprite stone = sprite("minecraft:blocks/stone");
        SpritePrefetch.run(manager, new ArrayList<>(Arrays.asList(stone)), s -> LOCATION);
        SpritePrefetch.PrefetchedResource prefetched = (SpritePrefetch.PrefetchedResource) SpritePrefetch.resource(stone);
        assertNotNull(prefetched);
        assertFalse(prefetched.hasMetadata());

        try (InputStream stream = prefetched.getInputStream()) {
            assertInstanceOf(SpritePrefetch.PrefetchedStream.class, stream);
            assertSame(prefetched, ((SpritePrefetch.PrefetchedStream) stream).resource());
            // The frame loader recognises the stream and takes the image; any other reader gets the bytes
            assertEquals(png[0] & 0xFF, stream.read());
            byte[] rest = new byte[7];
            assertEquals(7, stream.read(rest, 0, 7));
            assertEquals(png[1], rest[0]);
        }
        // Closing a stream that never opened the file is harmless
        prefetched.getInputStream().close();
    }

    @Test
    void aSpriteThatCannotBeReadIsLeftToTheVanillaLoop() throws Exception {
        IResourceManager failing = mock(IResourceManager.class);
        when(failing.getResource(any())).thenThrow(new IOException("no such texture"));
        TextureAtlasSprite stone = sprite("minecraft:blocks/stone");
        SpritePrefetch.run(failing, new ArrayList<>(Arrays.asList(stone)), s -> LOCATION);
        assertNull(SpritePrefetch.resource(stone));

        // So is one whose bytes are not an image at all
        IResourceManager garbage = mock(IResourceManager.class);
        when(garbage.getResource(any())).thenAnswer(invocation -> {
            IResource resource = mock(IResource.class);
            when(resource.getInputStream()).thenAnswer(stream -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
            return resource;
        });
        SpritePrefetch.run(garbage, new ArrayList<>(Arrays.asList(stone)), s -> LOCATION);
        assertNull(SpritePrefetch.resource(stone));
    }
}
