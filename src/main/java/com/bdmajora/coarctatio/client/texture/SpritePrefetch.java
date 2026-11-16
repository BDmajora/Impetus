package com.bdmajora.coarctatio.client.texture;

import com.bdmajora.coarctatio.Coarctatio;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Reads sprite images on worker threads before the atlas needs them.
public final class SpritePrefetch {
    // Prefetched resources by sprite.
    private static final Map<TextureAtlasSprite, PrefetchedResource> PREFETCHED = new ConcurrentHashMap<>();

    // Prevents instantiation
    private SpritePrefetch() {
    }

    // Reads the image of every sprite in parallel and stores the results.
    public static void run(IResourceManager resourceManager, Collection<TextureAtlasSprite> sprites,
                           Function<TextureAtlasSprite, ResourceLocation> locations) {
        PREFETCHED.clear();
        long start = System.nanoTime();
        sprites.parallelStream().forEach(sprite -> {
            ResourceLocation location = locations.apply(sprite);
            try {
                if (sprite.hasCustomLoader(resourceManager, location)) {
                    return;
                }
                PrefetchedResource prefetched = PrefetchedResource.load(resourceManager, location);
                if (prefetched != null) {
                    PREFETCHED.put(sprite, prefetched);
                }
            } catch (Throwable t) {
            }
        });
        Coarctatio.LOGGER.debug("Prefetched {} of {} sprites in {} ms", PREFETCHED.size(), sprites.size(), (System.nanoTime() - start) / 1_000_000L);
    }

    // Returns the prefetched resource of the sprite or null.
    @Nullable
    public static IResource resource(TextureAtlasSprite sprite) {
        return PREFETCHED.get(sprite);
    }

    // Discards every prefetched resource.
    public static void clear() {
        PREFETCHED.clear();
    }

    // Resource that already holds a decoded image.
    public static final class PrefetchedResource implements IResource {
        // Manager that reads the original file.
        private final IResourceManager resourceManager;
        // Location of the file.
        private final ResourceLocation location;
        // Name of the pack that holds the file.
        private final String packName;
        // Size read from the file header.
        private final PngSizeInfo sizeInfo;
        // Animation metadata of the file.
        private final IMetadataSection animation;
        // Whether the file has metadata.
        private final boolean hasMetadata;
        // Decoded image or null once taken.
        private BufferedImage image;

        // Creates a resource from its parts.
        private PrefetchedResource(IResourceManager resourceManager, ResourceLocation location, String packName,
                                   PngSizeInfo sizeInfo, IMetadataSection animation, boolean hasMetadata, BufferedImage image) {
            this.resourceManager = resourceManager;
            this.location = location;
            this.packName = packName;
            this.sizeInfo = sizeInfo;
            this.animation = animation;
            this.hasMetadata = hasMetadata;
            this.image = image;
        }

        // Returns the resource read from the file or null when it has no image.
        @Nullable
        static PrefetchedResource load(IResourceManager resourceManager, ResourceLocation location) throws IOException {
            IResource resource = resourceManager.getResource(location);
            try {
                BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());
                if (image == null) {
                    return null;
                }
                IResource header = resourceManager.getResource(location);
                PngSizeInfo sizeInfo;
                try {
                    sizeInfo = PngSizeInfo.makeFromResource(header);
                } finally {
                    IOUtils.closeQuietly(header);
                }
                IMetadataSection animation = resource.getMetadata("animation");
                return new PrefetchedResource(resourceManager, location, resource.getResourcePackName(), sizeInfo, animation,
                        resource.hasMetadata(), image);
            } finally {
                IOUtils.closeQuietly(resource);
            }
        }

        // Returns the size read from the file header.
        public PngSizeInfo sizeInfo() {
            return this.sizeInfo;
        }

        // Returns the decoded image and forgets it.
        @Nullable
        public BufferedImage takeImage() {
            BufferedImage taken = this.image;
            this.image = null;
            return taken;
        }

        // Returns the location of the file.
        @Override
        public ResourceLocation getResourceLocation() {
            return this.location;
        }

        // Returns a stream that reads the original file on demand.
        @Override
        public InputStream getInputStream() {
            // Creates a stream for the resource.
            return new PrefetchedStream(this);
        }

        // Returns whether the file has metadata.
        @Override
        public boolean hasMetadata() {
            return this.hasMetadata;
        }

        // Returns the metadata section or null when it cannot be read.
        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T extends IMetadataSection> T getMetadata(String sectionName) {
            if ("animation".equals(sectionName)) {
                return (T) this.animation;
            }
            try {
                IResource real = this.resourceManager.getResource(this.location);
                try {
                    return real.getMetadata(sectionName);
                } finally {
                    IOUtils.closeQuietly(real);
                }
            } catch (IOException e) {
                return null;
            }
        }

        // Returns the name of the pack.
        @Override
        public String getResourcePackName() {
            return this.packName;
        }

        // Releases nothing.
        @Override
        public void close() {
        }
    }

    // Stream that opens the original file on first use.
    public static final class PrefetchedStream extends InputStream {
        // Resource that the stream belongs to.
        private final PrefetchedResource resource;
        // Stream of the original file or null before first use.
        private InputStream fallback;

        PrefetchedStream(PrefetchedResource resource) {
            this.resource = resource;
        }

        // Returns the resource that the stream belongs to.
        public PrefetchedResource resource() {
            return this.resource;
        }

        // Returns the stream of the original file and opens it on first use.
        private InputStream fallback() throws IOException {
            if (this.fallback == null) {
                this.fallback = this.resource.resourceManager.getResource(this.resource.location).getInputStream();
            }
            return this.fallback;
        }

        // Reads one byte from the original file.
        @Override
        public int read() throws IOException {
            return fallback().read();
        }

        // Reads bytes from the original file.
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return fallback().read(b, off, len);
        }

        // Closes the original stream when it was opened.
        @Override
        public void close() throws IOException {
            if (this.fallback != null) {
                this.fallback.close();
            }
        }
    }
}
