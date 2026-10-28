package com.bdmajora.extras.client.itemfaces;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// Rebuilds the faces of a model (item models, baked block entities) that overlap on one plane, which vanilla leaves to depth rounding (a shimmer that moves with the camera): a same-facing stack is cut on one shared grid so every overlap is the same rectangle in each face over it, and GL_LEQUAL hands each such cell to the face drawn last, or to whichever of the two is solid there when alpha testing discards the other; back-to-back faces of a flat element move apart along their normals for the culling-off living entity layers
public final class CoplanarFaces {
    // Lists in vanilla's draw order: the six cull face lists by EnumFacing index, then the general list
    public static final int LISTS = 7;
    // Coordinates closer than this are one value: far under a model pixel, far over the float noise a zero-angle element rotation leaves
    static final float EDGE = 1.0E-5F;
    // Faces closer than this across their normal share a plane: a sixteenth of a model pixel, over the 0.01 and 0.001 pixel nudges packs use against z-fighting, which still lose to depth rounding a few blocks out
    static final float PLANE = 1.0F / 256.0F;
    // Gap opened between back-to-back faces, each moving along its own normal: holds them apart in a 24-bit depth buffer out to about twenty blocks
    static final float SEPARATION = 1.0F / 512.0F;
    // Most pieces one stack may become before it is left as the model drew it
    static final int MAX_PIECES = 1024;
    // In-plane axes per face axis, ordered so (axis, b, c) is right handed and a counter-clockwise loop in (b, c) faces +axis
    private static final int[] B = {1, 2, 0};
    private static final int[] C = {2, 0, 1};
    // Corner codes (bit 0 high b, bit 1 high c) in front-facing order, for faces toward +axis and toward -axis
    private static final int[] TOWARD_PLUS = {0, 1, 3, 2};
    private static final int[] TOWARD_MINUS = {0, 2, 3, 1};

    private CoplanarFaces() {
    }

    // The seven lists with each rebuilt stack's pieces in its last face's slot, or null when no stack needed rebuilding
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

    // Splits one axis's faces into runs no thicker than PLANE and resolves every run that holds more than one face
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

    // Joins a run's overlapping same-facing faces into stacks, pushes its back-to-back twins apart and rebuilds the stacks that need it
    private static boolean resolveRun(List<Face> run) {
        Face[] faces = run.toArray(new Face[0]);
        // Sorted on the low b edge, so a face can only meet the faces that start before it ends
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

        // A stacked twin has to stay on its stack's plane, so a lone partner takes the whole gap
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

    // Union-find root with path halving
    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    // Int offset of the first texture coordinate pair, or -1 unless positions lead as three floats and those coordinates are two floats
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

    // The distinct values a stack's edges take on one in-plane axis
    private static float[] cuts(List<Face> members, boolean onB) {
        float[] values = new float[members.size() * 2];
        int n = 0;
        for (Face face : members) {
            values[n++] = onB ? face.b0 : face.c0;
            values[n++] = onB ? face.b1 : face.c1;
        }
        return merge(values);
    }

    // Sorted distinct values, each run of values within EDGE of its lowest merged into that lowest; sorts the array it is given
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

    // Index of the cut a merged value went into: the cut itself, else the one just below, since merge keeps a run's lowest value
    private static int index(float[] cuts, float value) {
        int at = Arrays.binarySearch(cuts, value);
        return at >= 0 ? at : -at - 2;
    }

    // Same-facing faces of one plane joined by overlap, with what they turn into
    private static final class Stack {
        final List<Face> members = new ArrayList<>(2);
        // Distance the whole stack moves along its normal, away from a back-to-back twin
        float offset;
        // The member latest in draw order, whose slot takes every piece
        Face last;
        // The members' pieces in draw order, or null while the stack is left as it was
        List<BakedQuad> pieces;

        void add(Face face) {
            face.stack = this;
            this.members.add(face);
            if (this.last == null || face.order > this.last.order) {
                this.last = face;
            }
        }

        void push(float distance) {
            this.offset = Math.max(this.offset, distance);
        }

        // Cuts every member on the shared grid and orders the pieces so the face on top draws last; false leaves the stack as it was
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

            // Members a few hundredths of a pixel apart meet on the largest one's plane, the body a detail was laid on
            int sign = this.members.get(0).sign;
            Face largest = this.members.get(0);
            for (Face face : this.members) {
                if (face.area() > largest.area() || face.area() == largest.area() && face.order < largest.order) {
                    largest = face;
                }
            }
            float plane = largest.plane + sign * this.offset;

            // On top draws last: the member furthest along the normal, then one inside another's bounds (a detail on a body), then the later one, as an exact depth tie would
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

    // One quad that is an axis-aligned rectangle, read once from its vertex data
    private static final class Face {
        final BakedQuad quad;
        // Position in the model's draw order
        final int order;
        // Axis the face is flat on, and +1 or -1 for the side its front looks toward
        final int axis;
        final int sign;
        // Coordinate on that axis
        final float plane;
        // Bounds on the in-plane axes, snapped onto the stack's grid before cutting
        float b0;
        float b1;
        float c0;
        float c1;
        // Vertex index and texture coordinates at each corner code
        final int[] corners;
        final float[] u;
        final float[] v;
        // Ints per vertex and the int offset of the texture coordinates inside one
        final int stride;
        final int uv;
        // The stack the face joined, null while its plane holds no other face
        Stack stack;
        // Draw order keys inside the stack: plane level along the normal, then how many members contain it
        int level;
        int depth;

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

        // A plain BakedQuad laid out as an axis-aligned rectangle, or null for anything else (rotated elements, other quad classes, unknown formats)
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
                    // Flat on two axes is a line, which has nothing to fight over
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
            // Narrower than this, a corner could read as both of its edges
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

            // Each step has to reach a neighbouring corner, or the quad folds over itself; the loop's turn says which way the front looks
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

        private static float coordinate(int[] data, int stride, int vertex, int axis) {
            return Float.intBitsToFloat(data[vertex * stride + axis]);
        }

        private static boolean flat(int[] data, int stride, int axis) {
            float first = coordinate(data, stride, 0, axis);
            for (int i = 1; i < 4; i++) {
                if (!(Math.abs(coordinate(data, stride, i, axis) - first) <= EDGE)) {
                    return false;
                }
            }
            return true;
        }

        // 0 on the low edge, 1 on the high edge, -1 between them
        private static int side(float value, float low, float high) {
            if (value - low <= EDGE) {
                return 0;
            }
            return high - value <= EDGE ? 1 : -1;
        }

        float area() {
            return (this.b1 - this.b0) * (this.c1 - this.c0);
        }

        boolean overlaps(Face other) {
            return Math.min(this.b1, other.b1) - Math.max(this.b0, other.b0) > EDGE
                    && Math.min(this.c1, other.c1) - Math.max(this.c0, other.c0) > EDGE;
        }

        boolean sameExtent(Face other) {
            return Math.abs(this.b0 - other.b0) <= EDGE && Math.abs(this.b1 - other.b1) <= EDGE
                    && Math.abs(this.c0 - other.c0) <= EDGE && Math.abs(this.c1 - other.c1) <= EDGE;
        }

        // Whether the other face lies inside this one's bounds without matching them
        boolean contains(Face other) {
            return this.b0 <= other.b0 + EDGE && other.b1 <= this.b1 + EDGE
                    && this.c0 <= other.c0 + EDGE && other.c1 <= this.c1 + EDGE && !sameExtent(other);
        }

        // This face over one grid cell on the given plane: every other attribute comes from the original vertex at the same corner code, texture coordinates are interpolated
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

        // Exact at the corners, where the weights are 0 and 1 and a corner gives back its own float
        private static float bilinear(float[] corner, double s, double t) {
            return (float) ((1.0D - s) * (1.0D - t) * corner[0] + s * (1.0D - t) * corner[1]
                    + (1.0D - s) * t * corner[2] + s * t * corner[3]);
        }
    }
}
