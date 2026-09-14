package com.bdmajora.extras.client.bakedentities;

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

    public EntityModelBaker(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    public List<BakedQuad> quads() {
        return this.quads;
    }

    // ModelRenderer.render with GL calls replaced by matrix ops: offset, then rotation point, then Z/Y/X rotations, boxes, children
    public void bake(ModelRenderer part, Matrix4f parent) {
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
        int component = axis == EnumFacing.Axis.X ? 0 : axis == EnumFacing.Axis.Y ? 1 : 2;
        for (BakedQuad quad : source) {
            int[] data = quad.getVertexData();
            float[][] verts = new float[4][5];
            for (int i = 0; i < 4; i++) {
                int offset = i * 7;
                verts[i][0] = Float.intBitsToFloat(data[offset]);
                verts[i][1] = Float.intBitsToFloat(data[offset + 1]);
                verts[i][2] = Float.intBitsToFloat(data[offset + 2]);
                verts[i][3] = Float.intBitsToFloat(data[offset + 4]);
                verts[i][4] = Float.intBitsToFloat(data[offset + 5]);
            }
            List<float[]> polygon = new ArrayList<>(5);
            for (int i = 0; i < 4; i++) {
                float[] current = verts[i];
                float[] previous = verts[(i + 3) % 4];
                boolean currentIn = inside(current[component], position, keepAbove);
                boolean previousIn = inside(previous[component], position, keepAbove);
                if (currentIn) {
                    if (!previousIn) {
                        polygon.add(intersect(previous, current, component, position));
                    }
                    polygon.add(current);
                } else if (previousIn) {
                    polygon.add(intersect(previous, current, component, position));
                }
            }
            // Duplicate vertices arise when an edge lies exactly on the plane; dropping them leaves the true rectangle
            List<float[]> cleaned = new ArrayList<>(polygon.size());
            for (float[] vertex : polygon) {
                float[] last = cleaned.isEmpty() ? null : cleaned.get(cleaned.size() - 1);
                if (last == null || !same(last, vertex)) {
                    cleaned.add(vertex);
                }
            }
            if (cleaned.size() > 1 && same(cleaned.get(0), cleaned.get(cleaned.size() - 1))) {
                cleaned.remove(cleaned.size() - 1);
            }
            if (cleaned.size() != 4) {
                continue;
            }
            float[] xs = new float[4];
            float[] ys = new float[4];
            float[] zs = new float[4];
            float[] us = new float[4];
            float[] vs = new float[4];
            for (int i = 0; i < 4; i++) {
                float[] vertex = cleaned.get(i);
                xs[i] = vertex[0] - (component == 0 ? shift : 0.0F);
                ys[i] = vertex[1] - (component == 1 ? shift : 0.0F);
                zs[i] = vertex[2] - (component == 2 ? shift : 0.0F);
                us[i] = vertex[3];
                vs[i] = vertex[4];
            }
            result.add(buildAtlas(xs, ys, zs, us, vs, quad.getSprite()));
        }
        return result;
    }

    private static boolean inside(float value, float position, boolean keepAbove) {
        return keepAbove ? value >= position - 1.0E-4F : value <= position + 1.0E-4F;
    }

    private static boolean same(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 1.0E-5F && Math.abs(a[1] - b[1]) < 1.0E-5F && Math.abs(a[2] - b[2]) < 1.0E-5F;
    }

    // Linear interpolation of position and atlas UV to the plane crossing
    private static float[] intersect(float[] from, float[] to, int component, float position) {
        float span = to[component] - from[component];
        float t = span == 0.0F ? 0.0F : (position - from[component]) / span;
        float[] out = new float[5];
        for (int i = 0; i < 5; i++) {
            out[i] = from[i] + (to[i] - from[i]) * t;
        }
        return out;
    }

    // As build, but the UVs are already atlas coordinates
    private static BakedQuad buildAtlas(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, TextureAtlasSprite sprite) {
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
            data[offset + 4] = Float.floatToRawIntBits(us[i]);
            data[offset + 5] = Float.floatToRawIntBits(vs[i]);
            data[offset + 6] = 0;
        }
        return new BakedQuad(data, -1, face, sprite, true, DefaultVertexFormats.BLOCK);
    }
}
