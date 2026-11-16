package com.bdmajora.impetus.impl.render.clouds;

import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;

// Cloud renderer that builds cloud cells from the cloud texture and emits only visible faces.
public final class SodiumCloudRenderer {
    // Logger of texture read failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Clouds");
    // Location of the cloud texture.
    private static final ResourceLocation CLOUDS_TEXTURES = new ResourceLocation("textures/environment/clouds.png");

    // Flag of an opaque cell.
    private static final int OPAQUE = 1;
    // Flag of a cell with a clear north neighbor.
    private static final int NORTH_OPEN = 1 << 1;
    // Flag of a cell with a clear south neighbor.
    private static final int SOUTH_OPEN = 1 << 2;
    // Flag of a cell with a clear west neighbor.
    private static final int WEST_OPEN = 1 << 3;
    // Flag of a cell with a clear east neighbor.
    private static final int EAST_OPEN = 1 << 4;

    // Width of a cell in blocks.
    public static final float CELL_SIZE = 12.0F;
    // Height of the cloud layer in blocks.
    private static final float THICKNESS = 4.0F;
    // Offset of the top and far faces against z fighting.
    private static final float INSET = 9.765625E-4F;
    // Alpha of every cloud vertex.
    private static final float ALPHA = 0.8F;

    // Shade of the bottom faces.
    private static final float TINT_BOTTOM = 0.7F;
    // Shade of the faces along x.
    private static final float TINT_X = 0.9F;
    // Shade of the faces along z.
    private static final float TINT_Z = 0.8F;

    // Camera position below the clouds.
    private static final int BELOW = 0;
    // Camera position inside the clouds.
    private static final int INSIDE = 1;
    // Camera position above the clouds.
    private static final int ABOVE = 2;

    // Tolerance of the camera position test in blocks.
    private static final float ORIENTATION_EPSILON = 0.125F;

    // Cells of the current texture or null.
    private static CloudCells cachedCells;
    // Texture reload count of the cached cells.
    private static int cachedTextureReloadCount = -1;

    // Prevents instantiation
    private SodiumCloudRenderer() {
    }

    // Returns whether the cloud cells could be read.
    public static boolean isReady(Minecraft mc) {
        return getCells(mc) != null;
    }

    // Draws the clouds around the camera and returns false when the cells are missing.
    public static boolean render(Minecraft mc, WorldClient world, TextureManager textureManager, int cloudTickCounter,
            float partialTicks, int pass, double cameraX, double cameraY, double cameraZ, boolean fancy,
            int radiusCells, float cloudHeight, float cellSize) {
        CloudCells cells = getCells(mc);
        if (cells == null) {
            return false;
        }

        double cloudTime = (double) ((float) cloudTickCounter + partialTicks);
        double gridX = (cameraX + cloudTime * 0.029999999329447746D) / cellSize;
        double gridZ = cameraZ / cellSize + 0.33000001311302185D;
        gridX -= (double) (MathHelper.floor(gridX / 2048.0D) * 2048);
        gridZ -= (double) (MathHelper.floor(gridZ / 2048.0D) * 2048);

        int cameraCellX = MathHelper.floor(gridX);
        int cameraCellZ = MathHelper.floor(gridZ);
        float subCellX = (float) (gridX - (double) cameraCellX);
        float subCellZ = (float) (gridZ - (double) cameraCellZ);

        float bottomY = cloudHeight - (float) cameraY + 0.33F;
        int cameraSide;
        if (bottomY >= -ORIENTATION_EPSILON) {
            cameraSide = BELOW;
        } else if (bottomY + THICKNESS <= ORIENTATION_EPSILON) {
            cameraSide = ABOVE;
        } else {
            cameraSide = INSIDE;
        }

        Vec3d cloudColour = world.getCloudColour(partialTicks);
        float red = (float) cloudColour.x;
        float green = (float) cloudColour.y;
        float blue = (float) cloudColour.z;

        if (pass != 2) {
            float grey = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float warm = (red * 30.0F + green * 70.0F) / 100.0F;
            float cool = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = grey;
            green = warm;
            blue = cool;
        }

        textureManager.bindTexture(CLOUDS_TEXTURES);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        GlStateManager.pushMatrix();
        // Finishes the buffer and restores the GL state on every path
        try {
            GlStateManager.scale(cellSize, 1.0F, cellSize);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);

            try {
                int radius = Math.max(1, radiusCells);
                int radiusSquared = radius * radius;

                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                        if (offsetX * offsetX + offsetZ * offsetZ > radiusSquared) {
                            continue;
                        }

                        int cellX = cameraCellX + offsetX;
                        int cellZ = cameraCellZ + offsetZ;
                        int flags = cells.get(cellX, cellZ);
                        if ((flags & OPAQUE) == 0) {
                            continue;
                        }

                        if (!fancy) {
                            emitFastCell(buffer, cells, cellX, cellZ, offsetX, offsetZ, subCellX, subCellZ, bottomY,
                                    cameraSide, red, green, blue);
                            continue;
                        }

                        boolean interior = cameraSide == INSIDE && Math.abs(offsetX) + Math.abs(offsetZ) <= 1;
                        emitCell(buffer, cells, cellX, cellZ, offsetX, offsetZ, subCellX, subCellZ, bottomY, cameraSide,
                                flags, interior, red, green, blue);
                    }
                }
            } finally {
                tessellator.draw();
            }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.enableCull();
        }
        return true;
    }

    // Emits the single flat face of a cell for fast clouds.
    private static void emitFastCell(BufferBuilder buffer, CloudCells cells, int cellX, int cellZ, int offsetX,
            int offsetZ, float subCellX, float subCellZ, float bottomY, int cameraSide, float red, float green,
            float blue) {
        float x0 = (float) offsetX - subCellX;
        float x1 = x0 + 1.0F;
        float z0 = (float) offsetZ - subCellZ;
        float z1 = z0 + 1.0F;
        float u0 = cells.u(cellX);
        float u1 = cells.u(cellX + 1);
        float v0 = cells.v(cellZ);
        float v1 = cells.v(cellZ + 1);
        float normalY = cameraSide == ABOVE ? 1.0F : -1.0F;

        vertex(buffer, x0, bottomY, z1, u0, v1, red, green, blue, 0.0F, normalY, 0.0F);
        vertex(buffer, x1, bottomY, z1, u1, v1, red, green, blue, 0.0F, normalY, 0.0F);
        vertex(buffer, x1, bottomY, z0, u1, v0, red, green, blue, 0.0F, normalY, 0.0F);
        vertex(buffer, x0, bottomY, z0, u0, v0, red, green, blue, 0.0F, normalY, 0.0F);
    }

    // Emits the visible faces of a cell for fancy clouds.
    private static void emitCell(BufferBuilder buffer, CloudCells cells, int cellX, int cellZ, int offsetX,
            int offsetZ, float subCellX, float subCellZ, float bottomY, int cameraSide, int flags, boolean interior,
            float red, float green, float blue) {
        float x0 = (float) offsetX - subCellX;
        float x1 = x0 + 1.0F;
        float z0 = (float) offsetZ - subCellZ;
        float z1 = z0 + 1.0F;
        float y0 = bottomY;
        float y1 = bottomY + THICKNESS;
        float u0 = cells.u(cellX);
        float u1 = cells.u(cellX + 1);
        float uc = cells.uCentre(cellX);
        float v0 = cells.v(cellZ);
        float v1 = cells.v(cellZ + 1);
        float vc = cells.vCentre(cellZ);
        float facing = interior ? -1.0F : 1.0F;

        if (interior || cameraSide != BELOW) {
            horizontal(buffer, x0, x1, y1 - INSET, z0, z1, u0, u1, v0, v1, red, green, blue, facing);
        }

        if (interior || cameraSide != ABOVE) {
            horizontal(buffer, x0, x1, y0, z0, z1, u0, u1, v0, v1,
                    red * TINT_BOTTOM, green * TINT_BOTTOM, blue * TINT_BOTTOM, -facing);
        }

        if (interior || ((flags & NORTH_OPEN) != 0 && offsetZ > 0)) {
            zWall(buffer, x0, x1, y0, y1, z0, u0, u1, vc, red, green, blue, -facing);
        }

        if (interior || ((flags & SOUTH_OPEN) != 0 && offsetZ < 0)) {
            zWall(buffer, x0, x1, y0, y1, z1 - INSET, u0, u1, vc, red, green, blue, facing);
        }

        if (interior || ((flags & WEST_OPEN) != 0 && offsetX > 0)) {
            xWall(buffer, x0, y0, y1, z0, z1, uc, v0, v1, red, green, blue, -facing);
        }

        if (interior || ((flags & EAST_OPEN) != 0 && offsetX < 0)) {
            xWall(buffer, x1 - INSET, y0, y1, z0, z1, uc, v0, v1, red, green, blue, facing);
        }
    }

    // Emits a horizontal face.
    private static void horizontal(BufferBuilder buffer, float x0, float x1, float y, float z0, float z1, float u0,
            float u1, float v0, float v1, float red, float green, float blue, float normalY) {
        vertex(buffer, x0, y, z1, u0, v1, red, green, blue, 0.0F, normalY, 0.0F);
        vertex(buffer, x1, y, z1, u1, v1, red, green, blue, 0.0F, normalY, 0.0F);
        vertex(buffer, x1, y, z0, u1, v0, red, green, blue, 0.0F, normalY, 0.0F);
        vertex(buffer, x0, y, z0, u0, v0, red, green, blue, 0.0F, normalY, 0.0F);
    }

    // Emits a wall that faces along z.
    private static void zWall(BufferBuilder buffer, float x0, float x1, float y0, float y1, float z, float u0,
            float u1, float vc, float red, float green, float blue, float normalZ) {
        float r = red * TINT_Z;
        float g = green * TINT_Z;
        float b = blue * TINT_Z;
        vertex(buffer, x0, y1, z, u0, vc, r, g, b, 0.0F, 0.0F, normalZ);
        vertex(buffer, x1, y1, z, u1, vc, r, g, b, 0.0F, 0.0F, normalZ);
        vertex(buffer, x1, y0, z, u1, vc, r, g, b, 0.0F, 0.0F, normalZ);
        vertex(buffer, x0, y0, z, u0, vc, r, g, b, 0.0F, 0.0F, normalZ);
    }

    // Emits a wall that faces along x.
    private static void xWall(BufferBuilder buffer, float x, float y0, float y1, float z0, float z1, float uc,
            float v0, float v1, float red, float green, float blue, float normalX) {
        float r = red * TINT_X;
        float g = green * TINT_X;
        float b = blue * TINT_X;
        vertex(buffer, x, y0, z1, uc, v1, r, g, b, normalX, 0.0F, 0.0F);
        vertex(buffer, x, y1, z1, uc, v1, r, g, b, normalX, 0.0F, 0.0F);
        vertex(buffer, x, y1, z0, uc, v0, r, g, b, normalX, 0.0F, 0.0F);
        vertex(buffer, x, y0, z0, uc, v0, r, g, b, normalX, 0.0F, 0.0F);
    }

    // Emits one vertex.
    private static void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v, float red,
            float green, float blue, float normalX, float normalY, float normalZ) {
        buffer.pos((double) x, (double) y, (double) z)
                .tex((double) u, (double) v)
                .color(red, green, blue, ALPHA)
                .normal(normalX, normalY, normalZ)
                .endVertex();
    }

    // Returns the cells of the current cloud texture and rereads them after a texture reload.
    private static CloudCells getCells(Minecraft mc) {
        int textureReloadCount = CapturedRenderingState.INSTANCE.getTextureReloadCount();
        if (cachedTextureReloadCount == textureReloadCount) {
            return cachedCells;
        }

        cachedTextureReloadCount = textureReloadCount;
        cachedCells = null;

        try (IResource resource = mc.getResourceManager().getResource(CLOUDS_TEXTURES)) {
            BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IOException("cloud texture is empty");
            }
            cachedCells = new CloudCells(image);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; falling back to vanilla cloud geometry", CLOUDS_TEXTURES, e);
        }

        return cachedCells;
    }

    // Opacity and open sides of every pixel of the cloud texture.
    private static final class CloudCells {
        // Flags of each cell.
        private final byte[] flags;
        // Width of the texture in cells.
        private final int width;
        // Height of the texture in cells.
        private final int height;

        // Reads the cells of an image and rejects one too large to index.
        private CloudCells(BufferedImage image) {
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.flags = new byte[Math.multiplyExact(this.width, this.height)];

            int[] pixels = new int[this.flags.length];
            image.getRGB(0, 0, this.width, this.height, pixels, 0, this.width);

            for (int z = 0; z < this.height; z++) {
                for (int x = 0; x < this.width; x++) {
                    if (!isOpaque(pixels, x, z)) {
                        continue;
                    }

                    int cell = OPAQUE;
                    if (!isOpaque(pixels, x, z - 1)) {
                        cell |= NORTH_OPEN;
                    }
                    if (!isOpaque(pixels, x, z + 1)) {
                        cell |= SOUTH_OPEN;
                    }
                    if (!isOpaque(pixels, x - 1, z)) {
                        cell |= WEST_OPEN;
                    }
                    if (!isOpaque(pixels, x + 1, z)) {
                        cell |= EAST_OPEN;
                    }
                    this.flags[index(x, z)] = (byte) cell;
                }
            }
        }

        // Returns the flags of a cell that wraps around the texture.
        private int get(int x, int z) {
            return this.flags[index(x, z)];
        }

        // Returns the u of the edge of a cell.
        private float u(int cellX) {
            return (float) cellX / (float) this.width;
        }

        // Returns the v of the edge of a cell.
        private float v(int cellZ) {
            return (float) cellZ / (float) this.height;
        }

        // Returns the u of the center of a cell.
        private float uCentre(int cellX) {
            return ((float) cellX + 0.5F) / (float) this.width;
        }

        // Returns the v of the center of a cell.
        private float vCentre(int cellZ) {
            return ((float) cellZ + 0.5F) / (float) this.height;
        }

        // Returns whether a pixel is opaque enough to form a cell.
        private boolean isOpaque(int[] pixels, int x, int z) {
            return ((pixels[index(x, z)] >>> 24) & 255) >= 10;
        }

        // Returns the index of a cell that wraps around the texture.
        private int index(int x, int z) {
            return Math.floorMod(z, this.height) * this.width + Math.floorMod(x, this.width);
        }
    }
}
