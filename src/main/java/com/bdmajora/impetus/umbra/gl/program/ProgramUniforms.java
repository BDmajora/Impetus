package com.bdmajora.impetus.umbra.gl.program;

import net.minecraft.client.Minecraft;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import com.bdmajora.impetus.umbra.gl.uniform.FloatSupplier;
import com.bdmajora.impetus.umbra.gl.uniform.FloatUniform;
import com.bdmajora.impetus.umbra.gl.uniform.IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Matrix3Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.MatrixUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import com.bdmajora.impetus.umbra.gl.uniform.Vector2IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector2Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector3IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector3Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector4IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector4Uniform;
import org.joml.Vector2f;

import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Every uniform bound to one program plus the driver uploading them at Iris's cadence (DYNAMIC every bind, ONCE on first use, PER_TICK/PER_FRAME on change); cadence matters since previous-frame suppliers advance when sampled
public class ProgramUniforms {
    // The uniforms that change per rendered OBJECT rather than per phase, re-uploaded from the per-object hooks; deliberately tiny rather than the whole DYNAMIC set, see updatePerObject for why
    private static final Set<String> PER_OBJECT_UNIFORMS = new HashSet<>(Arrays.asList(
            "entityId", "blockEntityId", "currentRenderedItemId", "entityColor"));

    private final List<Uniform> dynamic;
    private final List<Uniform> once;
    private final List<Uniform> perTick;
    private final List<Uniform> perFrame;
    private final List<Uniform> perObject;
    private long lastTick = -1L;
    private int lastFrame = -1;
    private boolean firstUpdate = true;
    // Bumped before every DYNAMIC walk so suppliers that read GL state (MatrixUniforms' live modelview) can share one readback across the walk instead of querying per uniform; render thread only
    private static int dynamicPass;

    private ProgramUniforms(List<Uniform> dynamic, List<Uniform> once, List<Uniform> perTick, List<Uniform> perFrame,
                            List<Uniform> perObject) {
        this.dynamic = dynamic;
        this.once = once;
        this.perTick = perTick;
        this.perFrame = perFrame;
        this.perObject = perObject;
    }

    // Re-uploads only the uniforms that vary per object (material ids, hurt-flash colour); calling update() here walked the whole DYNAMIC list, and MatrixUniform uploads unconditionally with five suppliers each doing a glGetFloat(GL_MODELVIEW_MATRIX) query, tens of thousands of stalls per frame across a few thousand objects
    public void updatePerObject() {
        updateStage(this.perObject);
    }

    // World tick, or zero with no world
    private static long currentTick() {
        return Minecraft.getMinecraft().world == null ? 0L : Minecraft.getMinecraft().world.getTotalWorldTime();
    }

    // The current DYNAMIC walk's number; equal across every supplier of one update() call
    public static int dynamicPass() {
        return dynamicPass;
    }

    // Uploads one cadence bucket
    private static void updateStage(List<Uniform> uniforms) {
        for (int i = 0; i < uniforms.size(); i++) {
            uniforms.get(i).update();
        }
    }

    // Uploads whichever buckets are due this bind
    public void update() {
        long currentTick = currentTick();
        int currentFrame = SystemTimeUniforms.COUNTER.getFrameCounter();
        dynamicPass++;
        updateStage(this.dynamic);
        if (this.firstUpdate) {
            this.firstUpdate = false;
            updateStage(this.once);
            updateStage(this.perTick);
            updateStage(this.perFrame);
            this.lastTick = currentTick;
            this.lastFrame = currentFrame;
            return;
        }
        if (this.lastTick != currentTick) {
            this.lastTick = currentTick;
            updateStage(this.perTick);
        }
        if (this.lastFrame != currentFrame) {
            this.lastFrame = currentFrame;
            updateStage(this.perFrame);
        }
    }

    // Starts registration for one program
    public static Builder builder(String name, int program) {
        return new Builder(name, program);
    }

    public static class Builder implements UniformCollector {
        private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
        // GL uniform type enums (glGetActiveUniform), used for provided-vs-declared validation.
        private static final int GL_ACTIVE_UNIFORMS = 0x8B86;
        private static final int GL_FLOAT_T = 0x1406;
        private static final int GL_INT_T = 0x1404;
        private static final int GL_BOOL_T = 0x8B56;
        private static final int GL_FLOAT_VEC2_T = 0x8B50;
        private static final int GL_FLOAT_VEC3_T = 0x8B51;
        private static final int GL_FLOAT_VEC4_T = 0x8B52;
        private static final int GL_INT_VEC2_T = 0x8B53;
        private static final int GL_INT_VEC3_T = 0x8B54;
        private static final int GL_INT_VEC4_T = 0x8B55;
        private static final int GL_FLOAT_MAT3_T = 0x8B5B;
        private static final int GL_FLOAT_MAT4_T = 0x8B5C;
        private static final int GL_SAMPLER_1D_T = 0x8B5D;
        private static final int GL_SAMPLER_2D_T = 0x8B5E;
        private static final int GL_SAMPLER_3D_T = 0x8B5F;
        private static final int GL_SAMPLER_CUBE_T = 0x8B60;
        private static final int GL_SAMPLER_1D_SHADOW_T = 0x8B61;
        private static final int GL_SAMPLER_2D_SHADOW_T = 0x8B62;
        private static final int GL_UNSIGNED_INT_SAMPLER_2D_T = 0x8DD2;
        private static final int GL_UNSIGNED_INT_SAMPLER_3D_T = 0x8DD3;

        // The GL type family a builder setter uploads with, compared against what the program declared; see declaredTypes for what a mismatch costs
        private enum ProvidedType {
            FLOAT, INT, VEC2, VEC2I, VEC3, VEC3I, VEC4, VEC4I, MAT3, MAT4
        }

        private static final class PendingUniform {
            final String uniformName;
            final ProvidedType provided;
            final UniformUpdateFrequency frequency;
            final Uniform uniform;
            // Scalar suppliers + location are retained so a FLOAT/INT mismatch against the declared type can be adapted through the other family instead of dropped; null/-1 for non-scalars
            final int location;
            final FloatSupplier floatSupplier;
            final IntSupplier intSupplier;

            PendingUniform(String uniformName, ProvidedType provided, UniformUpdateFrequency frequency, Uniform uniform,
                    int location, FloatSupplier floatSupplier, IntSupplier intSupplier) {
                this.uniformName = uniformName;
                this.provided = provided;
                this.frequency = frequency;
                this.uniform = uniform;
                this.location = location;
                this.floatSupplier = floatSupplier;
                this.intSupplier = intSupplier;
            }
        }

        private final String name;
        private final int program;
        // Keyed by uniform NAME so a later registration REPLACES an earlier one: addCommonUniforms runs before ActiveCustomUniforms.assignTo, so a pack-declared custom uniform wins over a built-in of the same name (Iris's rule, e.g. Sildur's framemod8); linked so the layout report keeps insertion order
        private final LinkedHashMap<String, PendingUniform> pending = new LinkedHashMap<>();

        private Builder(String name, int program) {
            this.name = name;
            this.program = program;
        }

        // Program name, for log lines
        public String getName() {
            return this.name;
        }

        // glGetUniformLocation; -1 means the program does not declare it
        private int location(CharSequence uniformName) {
            return LWJGL.glGetUniformLocation(this.program, uniformName);
        }

        // Queues a uniform until build resolves its location
        private void put(PendingUniform uniform) {
            this.pending.put(uniform.uniformName, uniform);
        }

        // Records a resolved uniform under its cadence, or drops it when the location is -1
        private void add(String uniformName, ProvidedType provided, UniformUpdateFrequency frequency, Uniform uniform) {
            put(new PendingUniform(uniformName, provided, frequency, uniform, -1, null, null));
        }

        // float
        public Builder uniform1f(UniformUpdateFrequency frequency, String uniformName, FloatSupplier value) {
            int location = location(uniformName);
            if (location != -1) {
                put(new PendingUniform(uniformName, ProvidedType.FLOAT, frequency,
                        new FloatUniform(location, value), location, value, null));
            }
            return this;
        }

        // vec2
        public Builder uniform2f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2f> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC2, frequency, new Vector2Uniform(location, value));
            }
            return this;
        }

        // int
        public Builder uniform1i(UniformUpdateFrequency frequency, String uniformName, IntSupplier value) {
            int location = location(uniformName);
            if (location != -1) {
                put(new PendingUniform(uniformName, ProvidedType.INT, frequency,
                        new IntUniform(location, value), location, null, value));
            }
            return this;
        }

        // ivec2
        public Builder uniform2i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2i> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC2I, frequency, new Vector2IntUniform(location, value));
            }
            return this;
        }

        // vec3
        public Builder uniform3f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3f> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC3, frequency, new Vector3Uniform(location, value));
            }
            return this;
        }

        // ivec3
        public Builder uniform3i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3i> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC3I, frequency, new Vector3IntUniform(location, value));
            }
            return this;
        }

        // vec4
        public Builder uniform4f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4f> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC4, frequency, new Vector4Uniform(location, value));
            }
            return this;
        }

        // ivec4
        public Builder uniform4i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4i> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC4I, frequency, new Vector4IntUniform(location, value));
            }
            return this;
        }

        // mat3
        public Builder uniformMatrix3(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix3fc> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.MAT3, frequency, new Matrix3Uniform(location, value));
            }
            return this;
        }

        // mat4
        public Builder uniformMatrix(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix4fc> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.MAT4, frequency, new MatrixUniform(location, value));
            }
            return this;
        }

        // The provider family a declared GL type needs, or null for types this port cannot supply; matches Iris's mapping
        private static ProvidedType expectedType(int glType) {
            return switch (glType) {
                case GL_FLOAT_T -> ProvidedType.FLOAT;
                case GL_INT_T, GL_BOOL_T, GL_SAMPLER_1D_T, GL_SAMPLER_2D_T, GL_SAMPLER_3D_T, GL_SAMPLER_CUBE_T,
                     GL_SAMPLER_1D_SHADOW_T, GL_SAMPLER_2D_SHADOW_T, GL_UNSIGNED_INT_SAMPLER_2D_T,
                     GL_UNSIGNED_INT_SAMPLER_3D_T -> ProvidedType.INT;
                case GL_FLOAT_VEC2_T -> ProvidedType.VEC2;
                case GL_INT_VEC2_T -> ProvidedType.VEC2I;
                case GL_FLOAT_VEC3_T -> ProvidedType.VEC3;
                case GL_INT_VEC3_T -> ProvidedType.VEC3I;
                case GL_FLOAT_VEC4_T -> ProvidedType.VEC4;
                case GL_INT_VEC4_T -> ProvidedType.VEC4I;
                case GL_FLOAT_MAT3_T -> ProvidedType.MAT3;
                case GL_FLOAT_MAT4_T -> ProvidedType.MAT4;
                default -> null;
            };
        }

        // Reads every ACTIVE uniform's declared type so a mismatched provider is disabled with a log line rather than raising GL_INVALID_OPERATION on EVERY upload (the "1282 @ Post render" spam); packs disagree on int vs float for worldTime, isEyeInWater etc. Same as Iris's buildUniforms
        private Map<String, ProvidedType> declaredTypes() {
            Map<String, ProvidedType> declared = new HashMap<>();
            int activeUniforms = LWJGL.glGetProgrami(this.program, GL_ACTIVE_UNIFORMS);
            IntBuffer sizeType = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asIntBuffer();
            for (int index = 0; index < activeUniforms; index++) {
                String uniformName = LWJGL.glGetActiveUniform(this.program, index, 256, sizeType);
                if (uniformName == null || uniformName.isEmpty()) {
                    continue;
                }
                if (uniformName.endsWith("[0]")) {
                    uniformName = uniformName.substring(0, uniformName.length() - 3);
                }
                declared.put(uniformName, expectedType(sizeType.get(1)));
            }
            return declared;
        }

        // Resolves every pending location and returns the finished set
        public ProgramUniforms buildUniforms() {
            Map<String, ProvidedType> declared = declaredTypes();
            List<Uniform> dynamic = new ArrayList<>();
            List<Uniform> once = new ArrayList<>();
            List<Uniform> perTick = new ArrayList<>();
            List<Uniform> perFrame = new ArrayList<>();
            List<Uniform> perObject = new ArrayList<>();
            for (PendingUniform entry : this.pending.values()) {
                Uniform uniform = entry.uniform;
                ProvidedType declaredType = declared.get(entry.uniformName);
                if (declared.containsKey(entry.uniformName) && declaredType != entry.provided) {
                    // Packs disagree on whether OptiFine scalars (framemod8, worldTime, isEyeInWater) are int or float; rather than drop the uniform and leave GLSL's default 0 (Sildur's `uniform int framemod8` breaking TAA jitter), re-upload through the declared family
                    if (entry.provided == ProvidedType.FLOAT && declaredType == ProvidedType.INT
                            && entry.floatSupplier != null) {
                        FloatSupplier fs = entry.floatSupplier;
                        uniform = new IntUniform(entry.location, () -> Math.round(fs.getAsFloat()));
                    } else if (entry.provided == ProvidedType.INT && declaredType == ProvidedType.FLOAT
                            && entry.intSupplier != null) {
                        IntSupplier is = entry.intSupplier;
                        uniform = new FloatUniform(entry.location, () -> (float) is.getAsInt());
                    } else {
                        LOGGER.warn("[{}] Wrong uniform type for {}: providing {} but the program declares a different"
                                        + " type. Disabling that uniform.",
                                this.name, entry.uniformName, entry.provided);
                        continue;
                    }
                }
                switch (entry.frequency) {
                    case DYNAMIC -> dynamic.add(uniform);
                    case ONCE -> once.add(uniform);
                    case PER_TICK -> perTick.add(uniform);
                    default -> perFrame.add(uniform);
                }
                // Also indexed separately while staying in its frequency list: the per-object hooks re-upload just these between draws, and the phase-level update still covers them
                if (PER_OBJECT_UNIFORMS.contains(entry.uniformName)) {
                    perObject.add(uniform);
                }
            }
            return new ProgramUniforms(dynamic, once, perTick, perFrame, perObject);
        }
    }
}
