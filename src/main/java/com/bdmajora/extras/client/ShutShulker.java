package com.bdmajora.extras.client;

import com.bdmajora.extras.client.bakedentities.ModelBoxAccess;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.BufferBuilder;

import java.util.ArrayList;
import java.util.List;

// Splits the shulker model so that the closed box has no overlapping faces.
public final class ShutShulker {
    // Prevents instantiation
    private ShutShulker() {
    }

    // Returns a renderer whose faces are cut at the edges of the other part or the same part when no cut applies.
    public static ModelRenderer split(ModelRenderer part, ModelRenderer other) {
        if (!plainBox(part) || !plainBox(other)) {
            return part;
        }
        ModelBox mine = part.cubeList.get(0);
        ModelBox theirs = other.cubeList.get(0);
        float dx = other.rotationPointX - part.rotationPointX;
        float dy = other.rotationPointY - part.rotationPointY;
        float dz = other.rotationPointZ - part.rotationPointZ;
        if (theirs.posX1 + dx != mine.posX1 || theirs.posX2 + dx != mine.posX2
                || theirs.posZ1 + dz != mine.posZ1 || theirs.posZ2 + dz != mine.posZ2) {
            return part;
        }
        List<Float> cuts = new ArrayList<>(2);
        for (float edge : new float[] {theirs.posY1 + dy, theirs.posY2 + dy}) {
            if (edge > mine.posY1 && edge < mine.posY2) {
                cuts.add(edge);
            }
        }
        if (cuts.isEmpty()) {
            return part;
        }
        List<TexturedQuad> pieces = new ArrayList<>(8);
        for (TexturedQuad quad : ((ModelBoxAccess) mine).impetus$getQuads()) {
            pieces.addAll(slice(quad, cuts));
        }
        ModelRenderer shut = new ModelRenderer(new ModelBase() { }, 0, 0);
        shut.setRotationPoint(part.rotationPointX, part.rotationPointY, part.rotationPointZ);
        // Creates a box from the quads.
        shut.cubeList.add(new QuadBox(shut, mine, pieces));
        return shut;
    }

    // Returns whether the part is one box without rotation or children.
    private static boolean plainBox(ModelRenderer part) {
        return part.cubeList != null && part.cubeList.size() == 1 && part.childModels == null
                && part.rotateAngleX == 0.0F && part.rotateAngleY == 0.0F && part.rotateAngleZ == 0.0F
                && part.offsetX == 0.0F && part.offsetY == 0.0F && part.offsetZ == 0.0F;
    }

    // Returns the quad cut at the heights.
    static List<TexturedQuad> slice(TexturedQuad quad, List<Float> cuts) {
        PositionTextureVertex[] corners = quad.vertexPositions;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (PositionTextureVertex corner : corners) {
            top = Math.min(top, (float) corner.vector3D.y);
            bottom = Math.max(bottom, (float) corner.vector3D.y);
        }
        List<Float> levels = new ArrayList<>(cuts.size() + 2);
        levels.add(top);
        for (float cut : cuts) {
            if (cut > top && cut < bottom) {
                levels.add(cut);
            }
        }
        levels.add(bottom);
        levels.sort(null);
        if (levels.size() == 2) {
            return List.of(quad);
        }
        PositionTextureVertex[] partners = new PositionTextureVertex[corners.length];
        for (int i = 0; i < corners.length; i++) {
            partners[i] = partner(corners, corners[i]);
            if (partners[i] == null) {
                return List.of(quad);
            }
        }
        List<TexturedQuad> slices = new ArrayList<>(levels.size() - 1);
        for (int s = 0; s + 1 < levels.size(); s++) {
            PositionTextureVertex[] kept = new PositionTextureVertex[corners.length];
            for (int i = 0; i < corners.length; i++) {
                boolean upper = corners[i].vector3D.y < partners[i].vector3D.y;
                kept[i] = at(corners[i], partners[i], upper ? levels.get(s) : levels.get(s + 1));
            }
            slices.add(new TexturedQuad(kept));
        }
        return slices;
    }

    // Returns the corner above or below the corner or null.
    private static PositionTextureVertex partner(PositionTextureVertex[] corners, PositionTextureVertex corner) {
        for (PositionTextureVertex other : corners) {
            if (other.vector3D.x == corner.vector3D.x && other.vector3D.z == corner.vector3D.z && other.vector3D.y != corner.vector3D.y) {
                return other;
            }
        }
        return null;
    }

    // Returns the point of the edge at the height.
    private static PositionTextureVertex at(PositionTextureVertex corner, PositionTextureVertex partner, float y) {
        if ((float) corner.vector3D.y == y) {
            return corner;
        }
        float t = (float) ((y - corner.vector3D.y) / (partner.vector3D.y - corner.vector3D.y));
        return new PositionTextureVertex((float) corner.vector3D.x, y, (float) corner.vector3D.z,
                corner.texturePositionX + (partner.texturePositionX - corner.texturePositionX) * t,
                corner.texturePositionY + (partner.texturePositionY - corner.texturePositionY) * t);
    }

    // Box that draws prepared quads.
    static final class QuadBox extends ModelBox {
        // Quads to draw.
        private final List<TexturedQuad> quads;

        // Returns the quads.
        QuadBox(ModelRenderer renderer, ModelBox shape, List<TexturedQuad> quads) {
            super(renderer, 0, 0, shape.posX1, shape.posY1, shape.posZ1, Math.round(shape.posX2 - shape.posX1),
                    Math.round(shape.posY2 - shape.posY1), Math.round(shape.posZ2 - shape.posZ1), 0.0F);
            this.quads = quads;
        }

        List<TexturedQuad> quads() {
            return this.quads;
        }

        // Draws every quad.
        @Override
        public void render(BufferBuilder buffer, float scale) {
            for (TexturedQuad quad : this.quads) {
                quad.draw(buffer, scale);
            }
        }
    }
}
