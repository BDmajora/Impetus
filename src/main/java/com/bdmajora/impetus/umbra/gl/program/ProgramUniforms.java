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

// Uniforms of one program grouped by how often they upload.
public class ProgramUniforms {
    // Names of the uniforms that also upload for each object.
    private static final Set<String> PER_OBJECT_UNIFORMS = new HashSet<>(Arrays.asList(
            "entityId", "blockEntityId", "currentRenderedItemId", "entityColor"));

    // Uniforms that upload on every update.
    private final List<Uniform> dynamic;
    // Uniforms that upload on the first update.
    private final List<Uniform> once;
    // Uniforms that upload once per world tick.
    private final List<Uniform> perTick;
    // Uniforms that upload once per frame.
    private final List<Uniform> perFrame;
    // Uniforms that upload for each object.
    private final List<Uniform> perObject;
    // World tick of the last tick upload.
    private long lastTick = -1L;
    // Frame of the last frame upload.
    private int lastFrame = -1;
    // First update state of the program.
    private boolean firstUpdate = true;
    // Counter of updates across every program.
    private static int dynamicPass;

    // Creates the uniform groups.
    private ProgramUniforms(List<Uniform> dynamic, List<Uniform> once, List<Uniform> perTick, List<Uniform> perFrame,
                            List<Uniform> perObject) {
        this.dynamic = dynamic;
        this.once = once;
        this.perTick = perTick;
        this.perFrame = perFrame;
        this.perObject = perObject;
    }

    // Uploads the per object uniforms.
    public void updatePerObject() {
        updateStage(this.perObject);
    }

    // Returns the world tick or zero without a world.
    private static long currentTick() {
        return Minecraft.getMinecraft().world == null ? 0L : Minecraft.getMinecraft().world.getTotalWorldTime();
    }

    // Returns the update counter across every program.
    public static int dynamicPass() {
        return dynamicPass;
    }

    // Uploads every uniform of a group.
    private static void updateStage(List<Uniform> uniforms) {
        for (int i = 0; i < uniforms.size(); i++) {
            uniforms.get(i).update();
        }
    }

    // Uploads the dynamic uniforms and the tick and frame groups that changed.
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

    // Creates a builder for a program.
    public static Builder builder(String name, int program) {
        return new Builder(name, program);
    }

    // Collector of the uniforms a program uses.
    public static class Builder implements UniformCollector {
        // Logger of type mismatches.
        private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
        // GL enum of the active uniform count.
        private static final int GL_ACTIVE_UNIFORMS = 0x8B86;
        // GL type of a float.
        private static final int GL_FLOAT_T = 0x1406;
        // GL type of an int.
        private static final int GL_INT_T = 0x1404;
        // GL type of a bool.
        private static final int GL_BOOL_T = 0x8B56;
        // GL type of a float vec2.
        private static final int GL_FLOAT_VEC2_T = 0x8B50;
        // GL type of a float vec3.
        private static final int GL_FLOAT_VEC3_T = 0x8B51;
        // GL type of a float vec4.
        private static final int GL_FLOAT_VEC4_T = 0x8B52;
        // GL type of an int vec2.
        private static final int GL_INT_VEC2_T = 0x8B53;
        // GL type of an int vec3.
        private static final int GL_INT_VEC3_T = 0x8B54;
        // GL type of an int vec4.
        private static final int GL_INT_VEC4_T = 0x8B55;
        // GL type of a 3 by 3 matrix.
        private static final int GL_FLOAT_MAT3_T = 0x8B5B;
        // GL type of a 4 by 4 matrix.
        private static final int GL_FLOAT_MAT4_T = 0x8B5C;
        // GL type of a 1D sampler.
        private static final int GL_SAMPLER_1D_T = 0x8B5D;
        // GL type of a 2D sampler.
        private static final int GL_SAMPLER_2D_T = 0x8B5E;
        // GL type of a 3D sampler.
        private static final int GL_SAMPLER_3D_T = 0x8B5F;
        // GL type of a cube sampler.
        private static final int GL_SAMPLER_CUBE_T = 0x8B60;
        // GL type of a 1D shadow sampler.
        private static final int GL_SAMPLER_1D_SHADOW_T = 0x8B61;
        // GL type of a 2D shadow sampler.
        private static final int GL_SAMPLER_2D_SHADOW_T = 0x8B62;
        // GL type of an unsigned 2D sampler.
        private static final int GL_UNSIGNED_INT_SAMPLER_2D_T = 0x8DD2;
        // GL type of an unsigned 3D sampler.
        private static final int GL_UNSIGNED_INT_SAMPLER_3D_T = 0x8DD3;

        // Value types a uniform supplier provides.
        private enum ProvidedType {
            // Scalar and vector and matrix kinds.
            FLOAT, INT, VEC2, VEC2I, VEC3, VEC3I, VEC4, VEC4I, MAT3, MAT4
        }

        // Uniform found in the program and not yet grouped.
        private static final class PendingUniform {
            // Name in the program.
            final String uniformName;
            // Type the supplier provides.
            final ProvidedType provided;
            // Upload frequency.
            final UniformUpdateFrequency frequency;
            // Uniform that uploads the value.
            final Uniform uniform;
            // Location in the program or minus one.
            final int location;
            // Float supplier or null.
            final FloatSupplier floatSupplier;
            // Int supplier or null.
            final IntSupplier intSupplier;

            // Creates a pending uniform.
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

        // Name of the program.
        private final String name;
        // GL name of the program.
        private final int program;
        // Pending uniforms by name in registration order.
        private final LinkedHashMap<String, PendingUniform> pending = new LinkedHashMap<>();

        // Creates a builder for a program.
        private Builder(String name, int program) {
            this.name = name;
            this.program = program;
        }

        // Returns the name of the program.
        public String getName() {
            return this.name;
        }

        // Returns the location of a uniform or minus one.
        private int location(CharSequence uniformName) {
            return LWJGL.glGetUniformLocation(this.program, uniformName);
        }

        // Replaces the pending uniform of the same name.
        private void put(PendingUniform uniform) {
            this.pending.put(uniform.uniformName, uniform);
        }

        // Stores a pending uniform without a scalar supplier.
        private void add(String uniformName, ProvidedType provided, UniformUpdateFrequency frequency, Uniform uniform) {
            put(new PendingUniform(uniformName, provided, frequency, uniform, -1, null, null));
        }

        // Registers a float uniform the program uses.
        public Builder uniform1f(UniformUpdateFrequency frequency, String uniformName, FloatSupplier value) {
            int location = location(uniformName);
            if (location != -1) {
                put(new PendingUniform(uniformName, ProvidedType.FLOAT, frequency,
                        new FloatUniform(location, value), location, value, null));
            }
            return this;
        }

        // Registers a float vec2 uniform the program uses.
        public Builder uniform2f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2f> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC2, frequency, new Vector2Uniform(location, value));
            }
            return this;
        }

        // Registers an int uniform the program uses.
        public Builder uniform1i(UniformUpdateFrequency frequency, String uniformName, IntSupplier value) {
            int location = location(uniformName);
            if (location != -1) {
                put(new PendingUniform(uniformName, ProvidedType.INT, frequency,
                        new IntUniform(location, value), location, null, value));
            }
            return this;
        }

        // Registers an int vec2 uniform the program uses.
        public Builder uniform2i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2i> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC2I, frequency, new Vector2IntUniform(location, value));
            }
            return this;
        }

        // Registers a float vec3 uniform the program uses.
        public Builder uniform3f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3f> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC3, frequency, new Vector3Uniform(location, value));
            }
            return this;
        }

        // Registers an int vec3 uniform the program uses.
        public Builder uniform3i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3i> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC3I, frequency, new Vector3IntUniform(location, value));
            }
            return this;
        }

        // Registers a float vec4 uniform the program uses.
        public Builder uniform4f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4f> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC4, frequency, new Vector4Uniform(location, value));
            }
            return this;
        }

        // Registers an int vec4 uniform the program uses.
        public Builder uniform4i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4i> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.VEC4I, frequency, new Vector4IntUniform(location, value));
            }
            return this;
        }

        // Registers a 3 by 3 matrix uniform the program uses.
        public Builder uniformMatrix3(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix3fc> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.MAT3, frequency, new Matrix3Uniform(location, value));
            }
            return this;
        }

        // Registers a 4 by 4 matrix uniform the program uses.
        public Builder uniformMatrix(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix4fc> value) {
            int location = location(uniformName);
            if (location != -1) {
                add(uniformName, ProvidedType.MAT4, frequency, new MatrixUniform(location, value));
            }
            return this;
        }

        // Returns the supplier type of a GL type or null for an unlisted one.
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

        // Returns the declared supplier type of every active uniform.
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

        // Groups the pending uniforms and converts or disables those whose declared type differs.
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
                // Checks only declarations of a known GL type
                if (declaredType != null && declaredType != entry.provided) {
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
                if (PER_OBJECT_UNIFORMS.contains(entry.uniformName)) {
                    perObject.add(uniform);
                }
            }
            return new ProgramUniforms(dynamic, once, perTick, perFrame, perObject);
        }
    }
}
