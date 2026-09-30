package com.bdmajora.coarctatio.mixin.client.texture;

import com.bdmajora.coarctatio.MemoryReport;
import com.bdmajora.coarctatio.client.texture.SpritePrefetch;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextureMixinsTest {
    private static final ResourceLocation LOCATION = new ResourceLocation("minecraft:textures/blocks/stone.png");
    private static byte[] png;

    @BeforeAll
    static void bootstrap() throws IOException {
        Mc.bootstrap();
        Mc.textures();
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        png = bytes.toByteArray();
    }

    @AfterEach
    void dropWhatWasPrefetched() {
        SpritePrefetch.clear();
    }

    private static TextureAtlasSprite sprite(String name, int width, int height) {
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", name);
        Mixins.set(sprite, "width", width);
        Mixins.set(sprite, "height", height);
        Mixins.set(sprite, "framesTextureData", new ArrayList<int[][]>());
        return sprite;
    }

    private static IResourceManager manager() throws IOException {
        IResourceManager manager = mock(IResourceManager.class);
        when(manager.getResource(any())).thenAnswer(invocation -> {
            IResource resource = mock(IResource.class);
            when(resource.getInputStream()).thenAnswer(stream -> new ByteArrayInputStream(png));
            when(resource.getResourcePackName()).thenReturn("test pack");
            return resource;
        });
        return manager;
    }

    @Test
    void theAtlasIsLaidOutByTheShelfPackerInVanillaSlots() {
        StitcherMixin stitcher = Mixins.instance(StitcherMixin.class);
        Set<Stitcher.Holder> holders = new LinkedHashSet<>();
        holders.add(new Stitcher.Holder(sprite("a", 16, 16), 0));
        holders.add(new Stitcher.Holder(sprite("b", 16, 32), 0));
        Mixins.set(stitcher, "setStitchHolders", holders);
        List<Stitcher.Slot> slots = new ArrayList<>();
        Mixins.set(stitcher, "stitchSlots", slots);
        Mixins.set(stitcher, "maxWidth", 1024);
        Mixins.set(stitcher, "maxHeight", 1024);

        var ci = Mixins.ci();
        Mixins.call(stitcher, "coarctatio$shelfStitch", ci);
        assertTrue(ci.isCancelled());
        // One exact-size slot per sprite, which is what every reader of the stitcher's output expects
        assertEquals(2, slots.size());
        for (Stitcher.Slot slot : slots) {
            assertNotNull(slot.getStitchHolder());
        }
        assertTrue(Mixins.<Integer>get(stitcher, "currentWidth") > 0);
        assertTrue(Mixins.<Integer>get(stitcher, "currentHeight") > 0);
    }

    @Test
    void staticSpritePixelsAreFreedOnceTheAtlasIsOnTheGpu() {
        TextureMapMixin map = Mixins.instance(TextureMapMixin.class);
        TextureAtlasSprite stone = sprite("minecraft:blocks/stone", 16, 16);
        Mixins.<List<int[][]>>get(stone, "framesTextureData").add(new int[][] {new int[256], null});
        TextureAtlasSprite animated = sprite("minecraft:blocks/water", 16, 16);
        Mixins.<List<int[][]>>get(animated, "framesTextureData").add(new int[][] {new int[256]});
        Mixins.set(animated, "animationMetadata",
                new net.minecraft.client.resources.data.AnimationMetadataSection(new ArrayList<>(), 16, 16, 1, false));

        Map<String, TextureAtlasSprite> uploaded = new LinkedHashMap<>();
        uploaded.put("stone", stone);
        uploaded.put("water", animated);
        uploaded.put("missing", null);
        Mixins.set(map, "mapUploadedSprites", uploaded);

        // Distant Horizons reads block pixels for its LOD colours whenever it likes, so with it installed nothing is freed
        long before = MemoryReport.totalBytes();
        Mc.forge("distanthorizons");
        Mixins.call(map, "coarctatio$releaseStaticSpriteData", mock(IResourceManager.class), Mixins.ci());
        assertEquals(1, stone.getFrameCount());
        assertEquals(before, MemoryReport.totalBytes());

        Mc.forge();
        Mixins.call(map, "coarctatio$releaseStaticSpriteData", mock(IResourceManager.class), Mixins.ci());
        // The static sprite's pixels are gone and counted; the animated one still needs its frames
        assertEquals(0, stone.getFrameCount());
        assertEquals(1, animated.getFrameCount());
        assertTrue(MemoryReport.totalBytes() > before);
    }

    @Test
    void theAtlasLoopReadsWhatThePrefetchAlreadyDecoded() throws Exception {
        TextureMapPrefetchMixin map = Mixins.instance(TextureMapPrefetchMixin.class);
        TextureAtlasSprite stone = sprite("minecraft:blocks/stone", 16, 16);
        Map<String, TextureAtlasSprite> registered = new HashMap<>();
        registered.put("minecraft:blocks/stone", stone);
        Mixins.set(map, "mapRegisteredSprites", registered);
        Mixins.stub(map, "getResourceLocation", invocation -> LOCATION);

        IResourceManager manager = manager();
        Mixins.call(map, "coarctatio$prefetchSprites", manager, Mixins.ci());
        IResource prefetched = SpritePrefetch.resource(stone);
        assertNotNull(prefetched);

        // Both resource lookups in the load loop and the frame read are answered from the prefetch
        Mc.Recorded<IResource> lookup = Mc.operation(mock(IResource.class));
        assertSame(prefetched, Mixins.call(map, "coarctatio$prefetchedForLoad", manager, LOCATION, lookup, stone));
        assertEquals(0, lookup.count());
        assertSame(prefetched, Mixins.call(map, "coarctatio$prefetchedForFrames", manager, LOCATION, lookup, stone));

        // The header was parsed during the prefetch, so the vanilla call is skipped
        Mc.Recorded<PngSizeInfo> header = Mc.operation(null);
        PngSizeInfo size = Mixins.call(map, "coarctatio$prefetchedSize", prefetched, header);
        assertEquals(16, size.pngWidth);
        assertEquals(0, header.count());

        // A sprite the prefetch skipped, and a resource that is not a prefetched one, both take the vanilla path
        TextureAtlasSprite other = sprite("minecraft:blocks/dirt", 16, 16);
        IResource vanilla = mock(IResource.class);
        Mc.Recorded<IResource> fallback = Mc.operation(vanilla);
        assertSame(vanilla, Mixins.call(map, "coarctatio$prefetchedForLoad", manager, LOCATION, fallback, other));
        assertEquals(1, fallback.count());
        Mc.Recorded<PngSizeInfo> realHeader = Mc.operation(null);
        Mixins.call(map, "coarctatio$prefetchedSize", vanilla, realHeader);
        assertEquals(1, realHeader.count());

        Mixins.call(map, "coarctatio$dropPrefetch", manager, Mixins.ci());
        assertNull(SpritePrefetch.resource(stone));
    }

    @Test
    void theSpriteTakesTheDecodedImageInsteadOfRunningThePngDecoderAgain() throws Exception {
        TextureAtlasSpritePrefetchMixin sprite = Mixins.instance(TextureAtlasSpritePrefetchMixin.class);
        TextureAtlasSprite stone = sprite("minecraft:blocks/stone", 16, 16);
        SpritePrefetch.run(manager(), new ArrayList<>(Arrays.asList(stone)), s -> LOCATION);
        SpritePrefetch.PrefetchedResource prefetched = (SpritePrefetch.PrefetchedResource) SpritePrefetch.resource(stone);
        assertNotNull(prefetched);

        InputStream stream = prefetched.getInputStream();
        BufferedImage decoded = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Mc.Recorded<BufferedImage> decoder = Mc.operation(decoded);
        BufferedImage taken = Mixins.call(sprite, "coarctatio$takePrefetchedImage", stream, decoder);
        assertNotNull(taken);
        assertNotSame(decoded, taken);
        assertEquals(0, decoder.count());

        // The image is handed over once; a second read decodes as usual, and so does any other stream
        assertSame(decoded, Mixins.call(sprite, "coarctatio$takePrefetchedImage", stream, Mc.operation(decoded)));
        assertSame(decoded, Mixins.call(sprite, "coarctatio$takePrefetchedImage",
                new ByteArrayInputStream(png), Mc.operation(decoded)));
    }
}
