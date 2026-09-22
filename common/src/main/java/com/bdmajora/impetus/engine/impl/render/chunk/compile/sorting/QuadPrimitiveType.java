package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.util.sorting.MergeSort;
import org.jetbrains.annotations.Nullable;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import java.nio.ByteBuffer;
import java.util.BitSet;

public final class QuadPrimitiveType implements ChunkPrimitiveType {
    // generates index buffers that decompose quads into two triangles, for core profile rendering
    public static final QuadPrimitiveType TRIANGULATED = new QuadPrimitiveType(true);
    // generates index buffers suited for working with quad(-like) primitives directly
    public static final QuadPrimitiveType DIRECT = new QuadPrimitiveType(false);

    private final boolean triangulating;

    private static final int VERTICES_PER_PRIMITIVE = 4;

    private static final int FAKE_STATIC_CAMERA_OFFSET = 1000;

    public QuadPrimitiveType(boolean triangulating) {
        this.triangulating = triangulating;
    }

    // Six indices per quad
    @Override
    public int getIndexBufferElementsPerPrimitive() {
        return triangulating ? 6 : 4;
    }

    // Four
    @Override
    public int getVerticesPerPrimitive() {
        return VERTICES_PER_PRIMITIVE;
    }

    // Triangles once triangulated, else native quads
    @Override
    public GlPrimitiveType getGlPrimitiveType() {
        return triangulating ? GlPrimitiveType.TRIANGLES : GlPrimitiveType.QUADS;
    }

    // Sequential quad indices
    @Override
    public void generateSimpleIndexBuffer(ByteBuffer indexBuffer, int numPrimitives) {
        int minimumRequiredBufferSize = getIndexBufferSize(numPrimitives);
        if(indexBuffer.capacity() < minimumRequiredBufferSize) {
            throw new IllegalStateException("Given index buffer has length " + indexBuffer.capacity() + " but we need " + minimumRequiredBufferSize);
        }
        long ptr = LWJGL.memAddress(indexBuffer);

        int elementsPerPrimitive = this.getIndexBufferElementsPerPrimitive();
        boolean triangulating = this.triangulating;

        for (int primitiveIndex = 0; primitiveIndex < numPrimitives; primitiveIndex++) {
            writePrimitive(ptr + (long) primitiveIndex * elementsPerPrimitive * 4, primitiveIndex * VERTICES_PER_PRIMITIVE, triangulating);
        }
    }

    // One quad's indices at address ptr: a triangle pair (0 1 2, 2 3 0) or the four corners
    private static void writePrimitive(long ptr, int vertexOffset, boolean triangulating) {
        LWJGL.memPutInt(ptr, vertexOffset);
        LWJGL.memPutInt(ptr + 4, vertexOffset + 1);
        LWJGL.memPutInt(ptr + 8, vertexOffset + 2);

        if (triangulating) {
            LWJGL.memPutInt(ptr + 12, vertexOffset + 2);
            LWJGL.memPutInt(ptr + 16, vertexOffset + 3);
            LWJGL.memPutInt(ptr + 20, vertexOffset);
        } else {
            LWJGL.memPutInt(ptr + 12, vertexOffset + 3);
        }
    }

    // Indices in the given quad order
    private void generateIndexBuffer(ByteBuffer indexBuffer, int[] primitiveMapping) {
        int bufferSize = getIndexBufferSize(primitiveMapping.length);
        if(indexBuffer.capacity() != bufferSize) {
            throw new IllegalStateException("Given index buffer has length " + indexBuffer.capacity() + " but we expected " + bufferSize);
        }
        long ptr = LWJGL.memAddress(indexBuffer);

        int elementsPerPrimitive = this.getIndexBufferElementsPerPrimitive();
        boolean triangulating = this.triangulating;

        for (int primitiveIndex = 0; primitiveIndex < primitiveMapping.length; primitiveIndex++) {
            // Map to the desired primitive
            writePrimitive(ptr + (long) primitiveIndex * elementsPerPrimitive * 4, primitiveMapping[primitiveIndex] * VERTICES_PER_PRIMITIVE, triangulating);
        }
    }

    private static void buildStaticDistanceArray(float[] centers, float[] distanceArray, float x, float y, float z,
                                                 float normX, float normY, float normZ, int quadCount, BitSet normalSigns) {
        for (int quadIdx = 0; quadIdx < quadCount; ++quadIdx) {
            int centerIdx = quadIdx * 3;

            // Distance is the projection of camera->quad-centre onto the shared normal, sign-flipped for backwards-facing quads in the same plane

            float qX = centers[centerIdx + 0] - x;
            float qY = centers[centerIdx + 1] - y;
            float qZ = centers[centerIdx + 2] - z;

            distanceArray[quadIdx] = (normX * qX + normY * qY + normZ * qZ) * (normalSigns.get(quadIdx) ? 1 : -1);
        }
    }

    private static void buildDynamicDistanceArray(float[] centers, float[] distanceArray, int quadCount, float x,
                                                  float y, float z) {
        // Sort using distance to camera directly
        for (int quadIdx = 0; quadIdx < quadCount; ++quadIdx) {
            int centerIdx = quadIdx * 3;

            float qX = centers[centerIdx + 0] - x;
            float qY = centers[centerIdx + 1] - y;
            float qZ = centers[centerIdx + 2] - z;
            distanceArray[quadIdx] = qX * qX + qY * qY + qZ * qZ;
        }
    }

    // Back-to-front indices from the camera, using the analyzer's plane data
    @Override
    public void generateSortedIndexBuffer(ByteBuffer indexBuffer, int quadCount, @Nullable TranslucentQuadAnalyzer.SortState chunkData, float x, float y, float z) {
        if (chunkData == null || chunkData.level() == TranslucentQuadAnalyzer.Level.NONE || chunkData.centersLength() < 3) {
            generateSimpleIndexBuffer(indexBuffer, quadCount);
            return;
        }

        if (quadCount != (chunkData.centersLength() / 3)) {
            throw new IllegalStateException(String.format("Mismatched SortState (%d) vs given quad count (%d)", chunkData.centersLength() / 3, quadCount));
        }

        float[] centers = chunkData.centers();
        boolean isStatic = chunkData.level() == TranslucentQuadAnalyzer.Level.STATIC;

        // Quad splitting (BSP exact ordering) can be disabled in Performance options, falling back to per-quad centroid distance sorting
        if (!isStatic && ImpetusRuntimeOptions.quadSplittingEnabled) {
            int[] bspOrder = BspTranslucencySorter.sort(centers, chunkData.normals(), quadCount, x, y, z);
            if (bspOrder != null) {
                generateIndexBuffer(indexBuffer, bspOrder);
                return;
            }
        }

        int[] indicesArray = new int[quadCount];
        float[] distanceArray = new float[quadCount];
        for (int quadIdx = 0; quadIdx < quadCount; ++quadIdx) {
            indicesArray[quadIdx] = quadIdx;
        }

        if (isStatic) {
            buildStaticDistanceArray(centers, distanceArray,
                    centers[0] + chunkData.sharedNormal().x * FAKE_STATIC_CAMERA_OFFSET,
                    centers[1] + chunkData.sharedNormal().y * FAKE_STATIC_CAMERA_OFFSET,
                    centers[2] + chunkData.sharedNormal().z * FAKE_STATIC_CAMERA_OFFSET,
                    chunkData.sharedNormal().x,
                    chunkData.sharedNormal().y,
                    chunkData.sharedNormal().z,
                    quadCount,
                    chunkData.normalSigns());
        } else {
            buildDynamicDistanceArray(centers, distanceArray, quadCount, x, y, z);
        }

        MergeSort.mergeSort(indicesArray, distanceArray);

        generateIndexBuffer(indexBuffer, indicesArray);
    }
}
