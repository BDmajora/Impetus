package com.bdmajora.impetus.umbra.shaderpack.texture;

// Source of a custom texture of a shader pack.
public abstract class CustomTextureData {
    // Prevents instantiation outside the nested kinds
    private CustomTextureData() {
    }

    // Encoded image file with its filtering.
    public static final class PngData extends CustomTextureData {
        // Filtering of the texture.
        private final TextureFilteringData filteringData;
        // Bytes of the image file.
        private final byte[] content;

        // Creates image data.
        public PngData(TextureFilteringData filteringData, byte[] content) {
            this.filteringData = filteringData;
            this.content = content;
        }

        // Returns the filtering of the texture.
        public TextureFilteringData getFilteringData() {
            return this.filteringData;
        }

        // Returns the bytes of the image file.
        public byte[] getContent() {
            return this.content;
        }
    }

    // Marker of the game lightmap.
    public static final class LightmapMarker extends CustomTextureData {
        // Returns whether the other object is also a lightmap marker.
        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == this.getClass();
        }

        // Returns the same hash for every marker.
        @Override
        public int hashCode() {
            return 33;
        }
    }

    // Texture loaded by the game from a resource location.
    public static final class ResourceData extends CustomTextureData {
        // Namespace of the location.
        private final String namespace;
        // Path of the location.
        private final String location;

        // Creates resource data.
        public ResourceData(String namespace, String location) {
            this.namespace = namespace;
            this.location = location;
        }

        // Returns the namespace of the location.
        public String getNamespace() {
            return this.namespace;
        }

        // Returns the path of the location.
        public String getLocation() {
            return this.location;
        }
    }

    // Raw pixel data with its layout.
    public static final class RawData extends CustomTextureData {
        // Name of the texture target.
        private final String textureType;
        // Name of the internal format.
        private final String internalFormat;
        // Width in pixels.
        private final int width;
        // Height in pixels.
        private final int height;
        // Depth in pixels.
        private final int depth;
        // Name of the pixel format.
        private final String pixelFormat;
        // Name of the pixel type.
        private final String pixelType;
        // Bytes of the pixels.
        private final byte[] content;

        // Creates raw data.
        public RawData(String textureType, String internalFormat, int width, int height, int depth,
                       String pixelFormat, String pixelType, byte[] content) {
            this.textureType = textureType;
            this.internalFormat = internalFormat;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.pixelFormat = pixelFormat;
            this.pixelType = pixelType;
            this.content = content;
        }

        // Returns the name of the texture target.
        public String getTextureType() {
            return this.textureType;
        }

        // Returns the name of the internal format.
        public String getInternalFormat() {
            return this.internalFormat;
        }

        // Returns the width in pixels.
        public int getWidth() {
            return this.width;
        }

        // Returns the height in pixels.
        public int getHeight() {
            return this.height;
        }

        // Returns the depth in pixels.
        public int getDepth() {
            return this.depth;
        }

        // Returns the name of the pixel format.
        public String getPixelFormat() {
            return this.pixelFormat;
        }

        // Returns the name of the pixel type.
        public String getPixelType() {
            return this.pixelType;
        }

        // Returns the bytes of the pixels.
        public byte[] getContent() {
            return this.content;
        }
    }
}
