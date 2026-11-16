package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Moving lights that every terrain shader adds on top of the meshed block light, so a moving torch never re-meshes anything.
public final class TerrainDynamicLights {
    // Uniform buffer binding point of the light block in every terrain program.
    public static final int BINDING = 14;
    // Lights a frame uploads, the nearest ones to the camera.
    public static final int MAX_LIGHTS = 64;
    // Name of the uniform block.
    public static final String BLOCK_NAME = "ImpetusDynamicLights";
    // Bytes of the std140 block: one vec4 per light, then an ivec4 whose x is the count.
    public static final int BYTES = MAX_LIGHTS * 16 + 16;
    // Doubles kept per published light: world x, y, z and luminance.
    public static final int STRIDE = 4;

    // Members of the block, shared by the engine include and the pack prologue.
    public static final String BLOCK_MEMBERS = String.join("\n",
            "    vec4 impetus_DynamicLights[" + MAX_LIGHTS + "];",
            "    ivec4 impetus_DynamicLightCount;",
            "");
    // Brightest level at a point relative to the render origin, as DynamicLightsEngine.lightLevel computes it for a block.
    public static final String LEVEL_FUNCTION = String.join("\n",
            "float impetus_dynamicLightLevel(vec3 position) {",
            "    float level = 0.0;",
            "    int count = min(impetus_DynamicLightCount.x, " + MAX_LIGHTS + ");",
            "    for (int i = 0; i < count; i++) {",
            "        vec4 light = impetus_DynamicLights[i];",
            "        vec3 delta = position - light.xyz;",
            "        float distanceSquared = dot(delta, delta);",
            "        if (distanceSquared <= 60.0625) {",
            "            level = max(level, (1.0 - sqrt(distanceSquared) / 7.75) * light.w);",
            "        }",
            "    }",
            "    return min(level, 15.0);",
            "}",
            "");
    // Declarations for a program whose block Java binds after linking, which is how the pack programs get it.
    public static final String UNBOUND_DECLARATIONS = "layout(std140) uniform " + BLOCK_NAME + " {\n" + BLOCK_MEMBERS + "};\n"
            + LEVEL_FUNCTION;

    // Published lights, STRIDE doubles each, in world coordinates.
    private static final double[] LIGHTS = new double[MAX_LIGHTS * STRIDE];
    // Number of published lights.
    private static int count;
    // Bumped on every publish, so an upload knows the lights moved.
    private static int generation;
    // Whether terrain shaders apply the lights, so meshes must not bake them in.
    private static boolean shaderApplied = true;

    // GL name of the uniform buffer of the raster path, zero until the first draw.
    private static int buffer;
    // Staging copy of the block.
    private static ByteBuffer staging;
    // Generation and origin of the last raster upload.
    private static int uploadedGeneration;
    private static double uploadedX;
    private static double uploadedY;
    private static double uploadedZ;

    // Prevents instantiation
    private TerrainDynamicLights() {
    }

    // Returns whether the terrain shaders apply moving lights, which is what lets meshing and rebuilds ignore them.
    public static boolean isShaderApplied() {
        return shaderApplied;
    }

    // Records whether the terrain programs can apply the lights; a legacy GLSL context cannot.
    public static void setShaderApplied(boolean applied) {
        shaderApplied = applied;
    }

    // Replaces the published lights with up to MAX_LIGHTS of the given world positions and luminances.
    public static void setLights(double[] data, int lightCount) {
        Objects.requireNonNull(data, "data");
        if (lightCount < 0 || (long) lightCount * STRIDE > data.length) {
            throw new IllegalArgumentException("Light count " + lightCount + " outside the data");
        }
        int kept = Math.min(lightCount, MAX_LIGHTS);
        System.arraycopy(data, 0, LIGHTS, 0, kept * STRIDE);
        count = kept;
        generation++;
    }

    // Returns the number of published lights.
    public static int lightCount() {
        return count;
    }

    // Writes the std140 block to an address with every light relative to an origin.
    public static void write(long ptr, double originX, double originY, double originZ) {
        int lights = count;
        for (int i = 0; i < MAX_LIGHTS; i++) {
            long slot = ptr + i * 16L;
            if (i < lights) {
                int offset = i * STRIDE;
                // Subtracted in double, so a light far from the world origin keeps its precision
                MemoryUtil.memPutFloat(slot, (float) (LIGHTS[offset] - originX));
                MemoryUtil.memPutFloat(slot + 4, (float) (LIGHTS[offset + 1] - originY));
                MemoryUtil.memPutFloat(slot + 8, (float) (LIGHTS[offset + 2] - originZ));
                MemoryUtil.memPutFloat(slot + 12, (float) LIGHTS[offset + 3]);
            } else {
                MemoryUtil.memPutLong(slot, 0L);
                MemoryUtil.memPutLong(slot + 8, 0L);
            }
        }
        long tail = ptr + MAX_LIGHTS * 16L;
        MemoryUtil.memPutInt(tail, lights);
        MemoryUtil.memPutInt(tail + 4, 0);
        MemoryUtil.memPutLong(tail + 8, 0L);
    }

    // Uploads the block for the raster programs when the lights or the origin changed, then binds it.
    public static void prepare(double originX, double originY, double originZ) {
        if (buffer == 0) {
            staging = MemoryUtil.memAlloc(BYTES);
            buffer = LWJGL.glGenBuffers();
            // Forces the first upload
            uploadedGeneration = generation - 1;
        }
        if (uploadedGeneration != generation || uploadedX != originX || uploadedY != originY || uploadedZ != originZ) {
            write(MemoryUtil.memAddress(staging), originX, originY, originZ);
            LWJGL.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
            // A fresh store each upload, so the driver never waits on draws still reading the last one
            LWJGL.glBufferData(GL31.GL_UNIFORM_BUFFER, staging, GL15.GL_DYNAMIC_DRAW);
            uploadedGeneration = generation;
            uploadedX = originX;
            uploadedY = originY;
            uploadedZ = originZ;
        }
        // Rebound every draw, since nothing else owns this binding point but a pack could still touch it
        LWJGL.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, buffer);
    }

    // Releases the raster buffer; the next draw makes a new one.
    public static void delete() {
        if (buffer != 0) {
            LWJGL.glDeleteBuffers(buffer);
            buffer = 0;
        }
        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }
    }
}
