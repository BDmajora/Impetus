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

// A shut shulker box drawn so lid and base meet without shimmer: over the lid's lower four rows their sides share a plane, and the sheet makes the lid see-through there exactly where the base's teeth are solid, so the band only looks whole when both quads pick the same texel at every pixel; cutting each part at the other's edges makes those rows one identical quad in both, every texel kept
public final class ShutShulker {
    private ShutShulker() {
    }

    // The part with each side cut where the other part's top or bottom edge crosses it, or the part itself unless both are plain unturned single boxes on one footprint
    public static ModelRenderer split(ModelRenderer part, ModelRenderer other) {
        if (!plainBox(part) || !plainBox(other)) {
            return part;
        }
        ModelBox mine = part.cubeList.get(0);
        ModelBox theirs = other.cubeList.get(0);
        // The other box in this part's frame; only a matching footprint puts its sides on this part's planes
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
        shut.cubeList.add(new QuadBox(shut, mine, pieces));
        return shut;
    }

    // One box, no children, no turn and no offset, so the box bounds are the geometry in the part's own frame
    private static boolean plainBox(ModelRenderer part) {
        return part.cubeList != null && part.cubeList.size() == 1 && part.childModels == null
                && part.rotateAngleX == 0.0F && part.rotateAngleY == 0.0F && part.rotateAngleZ == 0.0F
                && part.offsetX == 0.0F && part.offsetY == 0.0F && part.offsetZ == 0.0F;
    }

    // The quad's slices between its own top and bottom and the cuts inside them, each in the quad's corner order with texture coordinates slid along the vertical edges; a flat face, or one without straight vertical edges, comes back whole
    static List<TexturedQuad> slice(TexturedQuad quad, List<Float> cuts) {
        PositionTextureVertex[] corners = quad.vertexPositions;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (PositionTextureVertex corner : corners) {
            top = Math.min(top, (float) corner.vector3D.y);
            bottom = Math.max(bottom, (float) corner.vector3D.y);
        }
        // Model y grows downward, so the levels run from the top edge down
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

    // The corner at the other end of this one's vertical edge: same x and z, another height
    private static PositionTextureVertex partner(PositionTextureVertex[] corners, PositionTextureVertex corner) {
        for (PositionTextureVertex other : corners) {
            if (other.vector3D.x == corner.vector3D.x && other.vector3D.z == corner.vector3D.z && other.vector3D.y != corner.vector3D.y) {
                return other;
            }
        }
        return null;
    }

    // The point at height y on the edge from this corner to its partner; the corner itself when it already sits there, so shared rows keep the exact original coordinates
    private static PositionTextureVertex at(PositionTextureVertex corner, PositionTextureVertex partner, float y) {
        if ((float) corner.vector3D.y == y) {
            return corner;
        }
        float t = (float) ((y - corner.vector3D.y) / (partner.vector3D.y - corner.vector3D.y));
        return new PositionTextureVertex((float) corner.vector3D.x, y, (float) corner.vector3D.z,
                corner.texturePositionX + (partner.texturePositionX - corner.texturePositionX) * t,
                corner.texturePositionY + (partner.texturePositionY - corner.texturePositionY) * t);
    }

    // A box drawing the given slices in place of the six faces it was built with
    static final class QuadBox extends ModelBox {
        private final List<TexturedQuad> quads;

        QuadBox(ModelRenderer renderer, ModelBox shape, List<TexturedQuad> quads) {
            super(renderer, 0, 0, shape.posX1, shape.posY1, shape.posZ1, Math.round(shape.posX2 - shape.posX1),
                    Math.round(shape.posY2 - shape.posY1), Math.round(shape.posZ2 - shape.posZ1), 0.0F);
            this.quads = quads;
        }

        List<TexturedQuad> quads() {
            return this.quads;
        }

        @Override
        public void render(BufferBuilder buffer, float scale) {
            for (TexturedQuad quad : this.quads) {
                quad.draw(buffer, scale);
            }
        }
    }
}
