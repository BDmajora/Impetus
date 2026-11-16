package com.bdmajora.extras.client.itemfaces;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// Splits overlapping flat faces of a model into pieces that no longer fight for the same depth.
public final class CoplanarFaces {
    // Number of face lists of a model.
    public static final int LISTS = 7;
    // Tolerance for the edges of a face.
    static final float EDGE = 1.0E-5F;
    // Tolerance for the distance between planes.
    static final float PLANE = 1.0F / 256.0F;
    // Distance that separates stacked faces.
    static final float SEPARATION = 1.0F / 512.0F;
    // Largest number of pieces for one stack.
    static final int MAX_PIECES = 1024;
    // Index of the first tangent axis for each axis.
    private static final int[] B = {1, 2, 0};
    // Index of the second tangent axis for each axis.
    private static final int[] C = {2, 0, 1};
    // Corner order of a face that looks toward the positive side.
    private static final int[] TOWARD_PLUS = {0, 1, 3, 2};
    // Corner order of a face that looks toward the negative side.
    private static final int[] TOWARD_MINUS = {0, 2, 3, 1};

    // Prevents instantiation
    private CoplanarFaces() {
    }

    // Returns the lists with overlapping faces replaced or null when nothing changed.
    public static List<List<BakedQuad>> resolve(List<List<BakedQuad>> lists) {
        int total = 0;
        for (List<BakedQuad> list : lists) {
            total += list.size();
        }
        Face[] byOrder = new Face[total];
        List<Face> faces = new ArrayList<>(total);
        int order = 0;
        for (List<BakedQuad> list : lists) {
            for (BakedQuad quad : list) {
                Face face = Face.of(quad, order);
                byOrder[order++] = face;
                if (face != null) {
                    faces.add(face);
                }
            }
        }

        boolean changed = false;
        for (int axis = 0; axis < 3; axis++) {
            changed |= resolveAxis(faces, axis);
        }
        if (!changed) {
            return null;
        }

        List<List<BakedQuad>> resolved = new ArrayList<>(lists.size());
        order = 0;
        for (List<BakedQuad> list : lists) {
            List<BakedQuad> out = new ArrayList<>(list.size());
            for (BakedQuad quad : list) {
                Face face = byOrder[order++];
                Stack stack = face == null ? null : face.stack;
                if (stack == null || stack.pieces == null) {
                    out.add(quad);
                } else if (stack.last == face) {
                    out.addAll(stack.pieces);
                }
            }
            resolved.add(out);
        }
        return resolved;
    }

    // Resolves the overlapping faces that look along the axis and returns whether any changed.
    private static boolean resolveAxis(List<Face> faces, int axis) {
        List<Face> onAxis = new ArrayList<>();
        for (Face face : faces) {
            if (face.axis == axis) {
                onAxis.add(face);
            }
        }
        onAxis.sort(Comparator.comparingDouble(face -> face.plane));

        boolean changed = false;
        int start = 0;
        while (start < onAxis.size()) {
            float base = onAxis.get(start).plane;
            int end = start + 1;
            while (end < onAxis.size() && onAxis.get(end).plane - base <= PLANE) {
                end++;
            }
            if (end - start > 1) {
                changed |= resolveRun(onAxis.subList(start, end));
            }
            start = end;
        }
        return changed;
    }

    // Resolves one run of faces on the same plane and returns whether any changed.
    private static boolean resolveRun(List<Face> run) {
        Face[] faces = run.toArray(new Face[0]);
        Arrays.sort(faces, Comparator.comparingDouble(face -> face.b0));
        int[] parent = new int[faces.length];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        List<Face> twins = new ArrayList<>();
        for (int i = 0; i < faces.length; i++) {
            Face a = faces[i];
            for (int j = i + 1; j < faces.length && faces[j].b0 < a.b1 - EDGE; j++) {
                Face b = faces[j];
                if (!a.overlaps(b)) {
                    continue;
                }
                if (a.sign == b.sign) {
                    parent[root(parent, j)] = root(parent, i);
                } else if (a.sameExtent(b)) {
                    twins.add(a);
                    twins.add(b);
                }
            }
        }

        List<Stack> stacks = new ArrayList<>();
        Stack[] byRoot = new Stack[faces.length];
        for (int i = 0; i < faces.length; i++) {
            int root = root(parent, i);
            if (byRoot[root] == null) {
                byRoot[root] = new Stack();
                stacks.add(byRoot[root]);
            }
            byRoot[root].add(faces[i]);
        }

        for (int i = 0; i < twins.size(); i += 2) {
            Stack one = twins.get(i).stack;
            Stack other = twins.get(i + 1).stack;
            boolean oneShared = one.members.size() > 1;
            if (oneShared == other.members.size() > 1) {
                one.push(SEPARATION / 2.0F);
                other.push(SEPARATION / 2.0F);
            } else {
                (oneShared ? other : one).push(SEPARATION);
            }
        }

        boolean changed = false;
        for (Stack stack : stacks) {
            changed |= stack.build();
        }
        return changed;
    }

    // Returns the root of the group of the index.
    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    // Returns the offset of the texture coordinates of the format.
    static int uvOffset(VertexFormat format) {
        if (format == null || format.getElementCount() == 0) {
            return -1;
        }
        VertexFormatElement position = format.getElement(0);
        if (!position.isPositionElement() || position.getType() != VertexFormatElement.EnumType.FLOAT || position.getElementCount() != 3) {
            return -1;
        }
        for (int i = 1; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            if (element.getUsage() == VertexFormatElement.EnumUsage.UV && element.getIndex() == 0) {
                boolean floats = element.getType() == VertexFormatElement.EnumType.FLOAT && element.getElementCount() == 2;
                return floats && format.getOffset(i) % 4 == 0 ? format.getOffset(i) / 4 : -1;
            }
        }
        return -1;
    }

    // Returns the sorted cut positions of the members.
    private static float[] cuts(List<Face> members, boolean onB) {
        float[] values = new float[members.size() * 2];
        int n = 0;
        for (Face face : members) {
            values[n++] = onB ? face.b0 : face.c0;
            values[n++] = onB ? face.b1 : face.c1;
        }
        return merge(values);
    }

    // Returns the values without near duplicates.
    private static float[] merge(float[] values) {
        Arrays.sort(values);
        int count = 0;
        for (float value : values) {
            if (count == 0 || value - values[count - 1] > EDGE) {
                values[count++] = value;
            }
        }
        return Arrays.copyOf(values, count);
    }

    // Returns the position of the value among the cuts.
    private static int index(float[] cuts, float value) {
        int at = Arrays.binarySearch(cuts, value);
        return at >= 0 ? at : -at - 2;
    }

    // Faces that cover the same area.
    private static final class Stack {
        // Faces of the stack.
        final List<Face> members = new ArrayList<>(2);
        float offset;
        Face last;
        List<BakedQuad> pieces;

        // Adds a face to the stack.
        void add(Face face) {
            face.stack = this;
            this.members.add(face);
            if (this.last == null || face.order > this.last.order) {
                this.last = face;
            }
        }

        // Moves the faces apart by the distance.
        void push(float distance) {
            this.offset = Math.max(this.offset, distance);
        }

        // Cuts the faces into pieces and returns whether it succeeded.
        boolean build() {
            if (this.members.size() == 1 && this.offset == 0.0F) {
                return false;
            }
            float[] bCuts = cuts(this.members, true);
            float[] cCuts = cuts(this.members, false);
            int count = 0;
            for (Face face : this.members) {
                face.b0 = bCuts[index(bCuts, face.b0)];
                face.b1 = bCuts[index(bCuts, face.b1)];
                face.c0 = cCuts[index(cCuts, face.c0)];
                face.c1 = cCuts[index(cCuts, face.c1)];
                count += (index(bCuts, face.b1) - index(bCuts, face.b0)) * (index(cCuts, face.c1) - index(cCuts, face.c0));
            }
            if (count > MAX_PIECES) {
                return false;
            }

            int sign = this.members.get(0).sign;
            Face largest = this.members.get(0);
            for (Face face : this.members) {
                if (face.area() > largest.area() || face.area() == largest.area() && face.order < largest.order) {
                    largest = face;
                }
            }
            float plane = largest.plane + sign * this.offset;

            float[] fronts = new float[this.members.size()];
            for (int i = 0; i < fronts.length; i++) {
                fronts[i] = sign * this.members.get(i).plane;
            }
            float[] levels = merge(fronts);
            for (Face face : this.members) {
                face.level = index(levels, sign * face.plane);
                face.depth = 0;
                for (Face other : this.members) {
                    if (other != face && other.contains(face)) {
                        face.depth++;
                    }
                }
            }
            List<Face> ordered = new ArrayList<>(this.members);
            ordered.sort(Comparator.<Face>comparingInt(face -> face.level)
                    .thenComparingInt(face -> face.depth)
                    .thenComparingInt(face -> face.order));

            List<BakedQuad> out = new ArrayList<>(count);
            for (Face face : ordered) {
                int b0 = index(bCuts, face.b0);
                int b1 = index(bCuts, face.b1);
                int c0 = index(cCuts, face.c0);
                int c1 = index(cCuts, face.c1);
                for (int i = b0; i < b1; i++) {
                    for (int j = c0; j < c1; j++) {
                        out.add(face.piece(plane, bCuts[i], bCuts[i + 1], cCuts[j], cCuts[j + 1]));
                    }
                }
            }
            this.pieces = out;
            return true;
        }
    }

    // One flat face of a model.
    private static final class Face {
        // Quad of the face.
        final BakedQuad quad;
        // Position of the face in the model.
        final int order;
        // Axis that the face looks along.
        final int axis;
        // Direction of the face along its axis.
        final int sign;
        // Position of the face along its axis.
        final float plane;
        float b0;
        float b1;
        float c0;
        float c1;
        // Corner order of the face.
        final int[] corners;
        // Texture u of the corners.
        final float[] u;
        // Texture v of the corners.
        final float[] v;
        // Number of ints of one vertex.
        final int stride;
        // Offset of the texture coordinates.
        final int uv;
        Stack stack;
        int level;
        int depth;

        // Creates a face.
        private Face(BakedQuad quad, int order, int axis, int sign, float plane, float[] bounds, int[] corners, int stride, int uv) {
            this.quad = quad;
            this.order = order;
            this.axis = axis;
            this.sign = sign;
            this.plane = plane;
            this.b0 = bounds[0];
            this.b1 = bounds[1];
            this.c0 = bounds[2];
            this.c1 = bounds[3];
            this.corners = corners;
            this.stride = stride;
            this.uv = uv;
            int[] data = quad.getVertexData();
            this.u = new float[4];
            this.v = new float[4];
            for (int code = 0; code < 4; code++) {
                this.u[code] = Float.intBitsToFloat(data[corners[code] * stride + uv]);
                this.v[code] = Float.intBitsToFloat(data[corners[code] * stride + uv + 1]);
            }
        }

        // Returns the face of the quad or null when it is not flat.
        static Face of(BakedQuad quad, int order) {
            if (quad.getClass() != BakedQuad.class) {
                return null;
            }
            VertexFormat format = quad.getFormat();
            int uv = uvOffset(format);
            if (uv < 0) {
                return null;
            }
            int stride = format.getIntegerSize();
            int[] data = quad.getVertexData();
            if (data.length != stride * 4) {
                return null;
            }

            int axis = -1;
            for (int a = 0; a < 3; a++) {
                if (flat(data, stride, a)) {
                    if (axis >= 0) {
                        return null;
                    }
                    axis = a;
                }
            }
            if (axis < 0) {
                return null;
            }

            int b = B[axis];
            int c = C[axis];
            float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int i = 0; i < 4; i++) {
                float onB = coordinate(data, stride, i, b);
                float onC = coordinate(data, stride, i, c);
                bounds[0] = Math.min(bounds[0], onB);
                bounds[1] = Math.max(bounds[1], onB);
                bounds[2] = Math.min(bounds[2], onC);
                bounds[3] = Math.max(bounds[3], onC);
            }
            if (!(bounds[1] - bounds[0] > 2.0F * EDGE) || !(bounds[3] - bounds[2] > 2.0F * EDGE)) {
                return null;
            }

            int[] codes = new int[4];
            int[] corners = {-1, -1, -1, -1};
            for (int i = 0; i < 4; i++) {
                int high = side(coordinate(data, stride, i, b), bounds[0], bounds[1]);
                int up = side(coordinate(data, stride, i, c), bounds[2], bounds[3]);
                if (high < 0 || up < 0) {
                    return null;
                }
                int code = high | up << 1;
                if (corners[code] >= 0) {
                    return null;
                }
                corners[code] = i;
                codes[i] = code;
            }

            int turn = 0;
            for (int i = 0; i < 4; i++) {
                int from = codes[i];
                int to = codes[(i + 1) & 3];
                int step = from ^ to;
                if (step != 1 && step != 2) {
                    return null;
                }
                turn += (from & 1) * (to >> 1) - (to & 1) * (from >> 1);
            }
            return new Face(quad, order, axis, turn > 0 ? 1 : -1, coordinate(data, stride, 0, axis), bounds, corners, stride, uv);
        }

        // Returns the coordinate of a vertex.
        private static float coordinate(int[] data, int stride, int vertex, int axis) {
            return Float.intBitsToFloat(data[vertex * stride + axis]);
        }

        // Returns whether every vertex shares the same coordinate on the axis.
        private static boolean flat(int[] data, int stride, int axis) {
            float first = coordinate(data, stride, 0, axis);
            for (int i = 1; i < 4; i++) {
                if (!(Math.abs(coordinate(data, stride, i, axis) - first) <= EDGE)) {
                    return false;
                }
            }
            return true;
        }

        // Returns which side of the range the value lies on.
        private static int side(float value, float low, float high) {
            if (value - low <= EDGE) {
                return 0;
            }
            return high - value <= EDGE ? 1 : -1;
        }

        // Returns the area of the face.
        float area() {
            return (this.b1 - this.b0) * (this.c1 - this.c0);
        }

        // Returns whether the face overlaps the other face.
        boolean overlaps(Face other) {
            return Math.min(this.b1, other.b1) - Math.max(this.b0, other.b0) > EDGE
                    && Math.min(this.c1, other.c1) - Math.max(this.c0, other.c0) > EDGE;
        }

        // Returns whether the face covers the same extent as the other face.
        boolean sameExtent(Face other) {
            return Math.abs(this.b0 - other.b0) <= EDGE && Math.abs(this.b1 - other.b1) <= EDGE
                    && Math.abs(this.c0 - other.c0) <= EDGE && Math.abs(this.c1 - other.c1) <= EDGE;
        }

        // Returns whether the face contains the other face.
        boolean contains(Face other) {
            return this.b0 <= other.b0 + EDGE && other.b1 <= this.b1 + EDGE
                    && this.c0 <= other.c0 + EDGE && other.c1 <= this.c1 + EDGE && !sameExtent(other);
        }

        // Returns the part of the face inside the rectangle.
        BakedQuad piece(float plane, float bLow, float bHigh, float cLow, float cHigh) {
            int[] source = this.quad.getVertexData();
            int[] data = new int[this.stride * 4];
            int[] loop = this.sign > 0 ? TOWARD_PLUS : TOWARD_MINUS;
            double width = (double) this.b1 - this.b0;
            double height = (double) this.c1 - this.c0;
            for (int k = 0; k < 4; k++) {
                int code = loop[k];
                int to = k * this.stride;
                System.arraycopy(source, this.corners[code] * this.stride, data, to, this.stride);
                float onB = (code & 1) != 0 ? bHigh : bLow;
                float onC = (code & 2) != 0 ? cHigh : cLow;
                data[to + this.axis] = Float.floatToRawIntBits(plane);
                data[to + B[this.axis]] = Float.floatToRawIntBits(onB);
                data[to + C[this.axis]] = Float.floatToRawIntBits(onC);
                double s = ((double) onB - this.b0) / width;
                double t = ((double) onC - this.c0) / height;
                data[to + this.uv] = Float.floatToRawIntBits(bilinear(this.u, s, t));
                data[to + this.uv + 1] = Float.floatToRawIntBits(bilinear(this.v, s, t));
            }
            return new BakedQuad(data, this.quad.getTintIndex(), this.quad.getFace(), this.quad.getSprite(),
                    this.quad.shouldApplyDiffuseLighting(), this.quad.getFormat());
        }

        // Returns the interpolated value between four corners.
        private static float bilinear(float[] corner, double s, double t) {
            return (float) ((1.0D - s) * (1.0D - t) * corner[0] + s * (1.0D - t) * corner[1]
                    + (1.0D - s) * t * corner[2] + s * t * corner[3]);
        }
    }
}
