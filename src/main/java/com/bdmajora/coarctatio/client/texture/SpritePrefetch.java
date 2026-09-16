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

// StellarCore's parallel texture load: every registered sprite's PNG is opened and decoded across the common pool before the atlas loop starts, and the loop's resource lookups are answered from here instead of decoding one sprite at a time on the client thread
public final class SpritePrefetch {
    private static final Map<TextureAtlasSprite, PrefetchedResource> PREFETCHED = new ConcurrentHashMap<>();

    private SpritePrefetch() {
    }

    // Decodes every sprite that has no custom loader; a sprite that fails here simply falls back to the vanilla path, which will log the real problem
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
                // Left to the vanilla loop, whose missing-texture tracking reports it properly
            }
        });
        Coarctatio.LOGGER.debug("Prefetched {} of {} sprites in {} ms", PREFETCHED.size(), sprites.size(), (System.nanoTime() - start) / 1_000_000L);
    }

    // The prefetched resource for a sprite, or null to take the vanilla path
    @Nullable
    public static IResource resource(TextureAtlasSprite sprite) {
        return PREFETCHED.get(sprite);
    }

    public static void clear() {
        PREFETCHED.clear();
    }

    // A resource whose PNG header, animation metadata and decoded image were read ahead of time; anything that insists on the raw stream gets the real resource reopened underneath
    public static final class PrefetchedResource implements IResource {
        private final IResourceManager resourceManager;
        private final ResourceLocation location;
        private final String packName;
        private final PngSizeInfo sizeInfo;
        private final IMetadataSection animation;
        private final boolean hasMetadata;
        private BufferedImage image;

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

        @Nullable
        static PrefetchedResource load(IResourceManager resourceManager, ResourceLocation location) throws IOException {
            IResource resource = resourceManager.getResource(location);
            try {
                BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());
                if (image == null) {
                    return null;
                }
                // The header read is repeated rather than derived from the image so the size the sprite is registered with is exactly what vanilla would have read
                IResource header = resourceManager.getResource(location);
                PngSizeInfo sizeInfo = PngSizeInfo.makeFromResource(header);
                IMetadataSection animation = resource.getMetadata("animation");
                return new PrefetchedResource(resourceManager, location, resource.getResourcePackName(), sizeInfo, animation,
                        resource.hasMetadata(), image);
            } finally {
                IOUtils.closeQuietly(resource);
            }
        }

        public PngSizeInfo sizeInfo() {
            return this.sizeInfo;
        }

        // Hands the decoded image over once; a second read reopens the file like any other stream user
        @Nullable
        public BufferedImage takeImage() {
            BufferedImage taken = this.image;
            this.image = null;
            return taken;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return this.location;
        }

        @Override
        public InputStream getInputStream() {
            return new PrefetchedStream(this);
        }

        @Override
        public boolean hasMetadata() {
            return this.hasMetadata;
        }

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

        @Override
        public String getResourcePackName() {
            return this.packName;
        }

        @Override
        public void close() {
        }
    }

    // The stream a prefetched resource hands out: the sprite's frame loader recognises it and takes the decoded image, and any other reader falls through to the real file
    public static final class PrefetchedStream extends InputStream {
        private final PrefetchedResource resource;
        private InputStream fallback;

        PrefetchedStream(PrefetchedResource resource) {
            this.resource = resource;
        }

        public PrefetchedResource resource() {
            return this.resource;
        }

        private InputStream fallback() throws IOException {
            if (this.fallback == null) {
                this.fallback = this.resource.resourceManager.getResource(this.resource.location).getInputStream();
            }
            return this.fallback;
        }

        @Override
        public int read() throws IOException {
            return fallback().read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return fallback().read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (this.fallback != null) {
                this.fallback.close();
            }
        }
    }
}
