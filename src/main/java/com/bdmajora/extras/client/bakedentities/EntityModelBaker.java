package com.bdmajora.extras.client.bakedentities;

import com.bdmajora.extras.client.itemfaces.CoplanarFaces;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

// Turns a ModelRenderer tree into block-model quads by replaying the same transforms its display list would run under, so the geometry, UVs and winding are exactly what the block entity renderer draws; the matrix passed in is the renderer's own setup (translate, flip, rotate) in block units
public final class EntityModelBaker {
    private static final float MODEL_SCALE = 0.0625F;

    private final List<BakedQuad> quads = new ArrayList<>();
    private final TextureAtlasSprite sprite;
    // The quads with coplanar faces resolved, worked out on the first ask after the last bake
    private List<BakedQuad> resolved;

    public EntityModelBaker(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    // The baked quads, with faces that share a plane (the shulker lid's lower rows over the base's teeth, the chest base over the lid's bottom row) cut so the shared part is one identical quad in each, drawn in the renderer's order; a chunk mesh keeps no GL_LEQUAL tie of its own, so this is what keeps the pair from z-fighting
    public List<BakedQuad> quads() {
        if (this.resolved == null) {
            List<List<BakedQuad>> lists = new ArrayList<>(CoplanarFaces.LISTS);
            for (int i = 1; i < CoplanarFaces.LISTS; i++) {
                lists.add(List.of());
            }
            lists.add(this.quads);
            List<List<BakedQuad>> resolved = CoplanarFaces.resolve(lists);
            this.resolved = resolved == null ? this.quads : resolved.get(CoplanarFaces.LISTS - 1);
        }
        return this.resolved;
    }

    // ModelRenderer.render with GL calls replaced by matrix ops: offset, then rotation point, then Z/Y/X rotations, boxes, children
    public void bake(ModelRenderer part, Matrix4f parent) {
        this.resolved = null;
        if (part.isHidden || !part.showModel) {
            return;
        }
        Matrix4f matrix = new Matrix4f(parent);
        matrix.translate(part.offsetX, part.offsetY, part.offsetZ);
        matrix.translate(part.rotationPointX * MODEL_SCALE, part.rotationPointY * MODEL_SCALE, part.rotationPointZ * MODEL_SCALE);
        if (part.rotateAngleZ != 0.0F) {
            matrix.rotateZ(part.rotateAngleZ);
        }
        if (part.rotateAngleY != 0.0F) {
            matrix.rotateY(part.rotateAngleY);
        }
        if (part.rotateAngleX != 0.0F) {
            matrix.rotateX(part.rotateAngleX);
        }
        if (part.cubeList != null) {
            for (ModelBox box : part.cubeList) {
                for (TexturedQuad quad : ((ModelBoxAccess) box).impetus$getQuads()) {
                    emit(quad, matrix);
                }
            }
        }
        if (part.childModels != null) {
            for (ModelRenderer child : part.childModels) {
                bake(child, matrix);
            }
        }
    }

    // One TexturedQuad to one BakedQuad in the BLOCK format; the face is derived from the transformed normal the same way TexturedQuad.draw computes it
    private void emit(TexturedQuad quad, Matrix4f matrix) {
        PositionTextureVertex[] source = quad.vertexPositions;
        if (source.length != 4) {
            return;
        }
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        float[] us = new float[4];
        float[] vs = new float[4];
        Vector4f point = new Vector4f();
        for (int i = 0; i < 4; i++) {
            PositionTextureVertex vertex = source[i];
            point.set((float) vertex.vector3D.x * MODEL_SCALE, (float) vertex.vector3D.y * MODEL_SCALE, (float) vertex.vector3D.z * MODEL_SCALE, 1.0F);
            matrix.transform(point);
            xs[i] = point.x;
            ys[i] = point.y;
            zs[i] = point.z;
            us[i] = vertex.texturePositionX;
            vs[i] = vertex.texturePositionY;
        }
        this.quads.add(build(xs, ys, zs, us, vs, this.sprite));
    }

    // Normal from (v2 - v1) x (v0 - v1), the outward direction of the quad's winding, then packed vertex data with white colour and no lightmap like FaceBakery
    static BakedQuad build(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, TextureAtlasSprite sprite) {
        Vector3f a = new Vector3f(xs[2] - xs[1], ys[2] - ys[1], zs[2] - zs[1]);
        Vector3f b = new Vector3f(xs[0] - xs[1], ys[0] - ys[1], zs[0] - zs[1]);
        Vector3f normal = a.cross(b);
        EnumFacing face = EnumFacing.getFacingFromVector(normal.x, normal.y, normal.z);

        int[] data = new int[28];
        for (int i = 0; i < 4; i++) {
            int offset = i * 7;
            data[offset] = Float.floatToRawIntBits(xs[i]);
            data[offset + 1] = Float.floatToRawIntBits(ys[i]);
            data[offset + 2] = Float.floatToRawIntBits(zs[i]);
            data[offset + 3] = -1;
            data[offset + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(us[i] * 16.0D));
            data[offset + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(vs[i] * 16.0D));
            data[offset + 6] = 0;
        }
        return new BakedQuad(data, -1, face, sprite, true, DefaultVertexFormats.BLOCK);
    }

    // Splits every quad at the plane axis = position, keeping the side the sign selects and shifting it by -shift on that axis; used to hand each half of a double chest the part that lies in its own block
    public static List<BakedQuad> clip(List<BakedQuad> source, EnumFacing.Axis axis, float position, boolean keepAbove, float shift) {
        List<BakedQuad> result = new ArrayList<>(source.size());
        int component = component(axis);
        for (BakedQuad quad : source) {
            List<float[]> polygon = cut(corners(quad), component, position, keepAbove);
            if (polygon.size() != 4) {
                continue;
            }
            // The corners are this quad's own copies, so the shift can land in place
            for (float[] corner : polygon) {
                corner[component] -= shift;
            }
            result.add(buildAtlas(polygon, quad.getSprite()));
        }
        return result;
    }

    private static int component(EnumFacing.Axis axis) {
        return axis == EnumFacing.Axis.X ? 0 : axis == EnumFacing.Axis.Y ? 1 : 2;
    }

    // Position and atlas UV of each corner, read back out of the packed BLOCK-format data
    private static List<float[]> corners(BakedQuad quad) {
        int[] data = quad.getVertexData();
        List<float[]> corners = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int offset = i * 7;
            corners.add(new float[] {Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2]),
                    Float.intBitsToFloat(data[offset + 4]), Float.intBitsToFloat(data[offset + 5])});
        }
        return corners;
    }

    // One Sutherland-Hodgman pass keeping the side of the plane the sign selects
    private static List<float[]> cut(List<float[]> polygon, int component, float position, boolean keepAbove) {
        int count = polygon.size();
        List<float[]> kept = new ArrayList<>(count + 1);
        for (int i = 0; i < count; i++) {
            float[] current = polygon.get(i);
            float[] previous = polygon.get((i + count - 1) % count);
            boolean currentIn = inside(current[component], position, keepAbove);
            boolean previousIn = inside(previous[component], position, keepAbove);
            if (currentIn) {
                if (!previousIn) {
                    kept.add(intersect(previous, current, component, position));
                }
                kept.add(current);
            } else if (previousIn) {
                kept.add(intersect(previous, current, component, position));
            }
        }
        // Duplicate vertices arise when an edge lies exactly on the plane; dropping them leaves the true rectangle
        List<float[]> cleaned = new ArrayList<>(kept.size());
        for (float[] vertex : kept) {
            float[] last = cleaned.isEmpty() ? null : cleaned.get(cleaned.size() - 1);
            if (last == null || !same(last, vertex)) {
                cleaned.add(vertex);
            }
        }
        if (cleaned.size() > 1 && same(cleaned.get(0), cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }
        return cleaned;
    }

    private static boolean inside(float value, float position, boolean keepAbove) {
        return keepAbove ? value >= position - 1.0E-4F : value <= position + 1.0E-4F;
    }

    private static boolean same(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 1.0E-5F && Math.abs(a[1] - b[1]) < 1.0E-5F && Math.abs(a[2] - b[2]) < 1.0E-5F;
    }

    // Linear interpolation of position and atlas UV to the plane crossing, pinned exactly onto the plane so a cut edge meets the face that caused it with no seam
    private static float[] intersect(float[] from, float[] to, int component, float position) {
        float span = to[component] - from[component];
        float t = span == 0.0F ? 0.0F : (position - from[component]) / span;
        float[] out = new float[5];
        for (int i = 0; i < 5; i++) {
            out[i] = from[i] + (to[i] - from[i]) * t;
        }
        out[component] = position;
        return out;
    }

    // As build, but the corners already carry atlas UVs
    private static BakedQuad buildAtlas(List<float[]> corners, TextureAtlasSprite sprite) {
        float[] first = corners.get(0);
        float[] second = corners.get(1);
        float[] third = corners.get(2);
        Vector3f normal = new Vector3f(third[0] - second[0], third[1] - second[1], third[2] - second[2])
                .cross(first[0] - second[0], first[1] - second[1], first[2] - second[2]);
        EnumFacing face = EnumFacing.getFacingFromVector(normal.x, normal.y, normal.z);
        int[] data = new int[28];
        for (int i = 0; i < 4; i++) {
            float[] corner = corners.get(i);
            int offset = i * 7;
            data[offset] = Float.floatToRawIntBits(corner[0]);
            data[offset + 1] = Float.floatToRawIntBits(corner[1]);
            data[offset + 2] = Float.floatToRawIntBits(corner[2]);
            data[offset + 3] = -1;
            data[offset + 4] = Float.floatToRawIntBits(corner[3]);
            data[offset + 5] = Float.floatToRawIntBits(corner[4]);
            data[offset + 6] = 0;
        }
        return new BakedQuad(data, -1, face, sprite, true, DefaultVertexFormats.BLOCK);
    }
}
