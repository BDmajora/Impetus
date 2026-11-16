package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.sampler.SamplerBinding;
import com.bdmajora.impetus.umbra.gl.sampler.SamplerLimits;
import org.lwjgl.opengl.GL11;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Texture units bound to the samplers of one program.
public final class ProgramSamplers {
    // Logger of unit allocation problems.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Bindings in unit order.
    private final List<SamplerBinding> samplerBindings;
    // Uniform writes of the sampler units or null after the first update.
    private List<Uniform1iCall> initializer;

    // Creates the samplers of a program.
    private ProgramSamplers(List<SamplerBinding> samplerBindings, List<Uniform1iCall> initializer) {
        this.samplerBindings = samplerBindings;
        this.initializer = initializer;
    }

    // Creates a builder for a program that skips the reserved units.
    public static Builder builder(int program, Set<Integer> reservedTextureUnits) {
        return new Builder(program, reservedTextureUnits);
    }

    // Writes the unit uniforms once and binds every texture.
    public void update() {
        if (this.initializer != null) {
            for (Uniform1iCall call : this.initializer) {
                LWJGL.glUniform1i(call.location(), call.value());
            }
            this.initializer = null;
        }

        if (this.samplerBindings.isEmpty()) {
            return;
        }

        try {
            for (SamplerBinding binding : this.samplerBindings) {
                binding.update();
            }
        } finally {
            GlTextureUnits.releaseScratch();
        }
    }

    // Returns the number of bound textures.
    public int getActiveSamplers() {
        return this.samplerBindings.size();
    }

    // Collector of the samplers a program uses.
    public static final class Builder {
        // GL name of the program.
        private final int program;
        // Units that dynamic samplers never take.
        private final Set<Integer> reservedTextureUnits;
        // Bindings in unit order.
        private final List<SamplerBinding> samplers = new ArrayList<>();
        // Uniform writes of the units.
        private final List<Uniform1iCall> calls = new ArrayList<>();
        // Number of texture units the driver allows.
        private final int maxTextureUnits;
        // Next free unit.
        private int nextUnit;
        // Reported state of running out of units.
        private boolean exhaustedReported;

        // Creates a builder and reports reserved units past the driver limit.
        private Builder(int program, Set<Integer> reservedTextureUnits) {
            this.program = program;
            this.reservedTextureUnits = new LinkedHashSet<>(reservedTextureUnits);
            this.maxTextureUnits = SamplerLimits.get().getMaxTextureUnits();

            for (int unit : this.reservedTextureUnits) {
                if (unit >= this.maxTextureUnits) {
                    LOGGER.error("[Umbra] Texture unit {} is reserved but this driver only has {}; samplers allocated "
                            + "around it will be wrong", unit, this.maxTextureUnits);
                }
            }
            skipReserved();
        }

        // Returns whether the program declares a sampler of a name.
        public boolean hasSampler(String name) {
            return LWJGL.glGetUniformLocation(this.program, name) != -1;
        }

        // Points samplers at a unit bound elsewhere and reports a unit that is not reserved.
        public void addExternalSampler(int textureUnit, String... names) {
            if (!this.reservedTextureUnits.contains(textureUnit)) {
                LOGGER.error("[Umbra] Samplers {} point at unit {}, which is not reserved; the allocator may reuse it",
                        String.join("/", names), textureUnit);
            }
            for (String name : names) {
                int location = LWJGL.glGetUniformLocation(this.program, name);
                if (location != -1) {
                    this.calls.add(new Uniform1iCall(location, textureUnit));
                }
            }
        }

        // Binds a 2D texture to the next unit for every declared name and returns whether one was declared.
        public boolean addDynamicSampler(IntSupplier texture, String... names) {
            return addDynamicSampler(GL11.GL_TEXTURE_2D, texture, names);
        }

        // Binds a texture to the next unit for every declared name and returns whether one was declared.
        public boolean addDynamicSampler(int textureTarget, IntSupplier texture, String... names) {
            boolean used = false;
            for (String name : names) {
                int location = LWJGL.glGetUniformLocation(this.program, name);
                if (location == -1) {
                    continue;
                }
                if (this.nextUnit >= this.maxTextureUnits) {
                    if (!this.exhaustedReported) {
                        LOGGER.error("[Umbra] Program ran out of texture units at '{}' (driver reports {}); this and "
                                + "any later sampler will read from unit 0", name, this.maxTextureUnits);
                        this.exhaustedReported = true;
                    }
                    return used;
                }
                this.calls.add(new Uniform1iCall(location, this.nextUnit));
                used = true;
            }

            if (!used) {
                return false;
            }

            this.samplers.add(new SamplerBinding(this.nextUnit, textureTarget, texture));
            this.nextUnit++;
            skipReserved();
            return true;
        }

        // Moves the next unit past reserved units.
        private void skipReserved() {
            while (this.nextUnit < this.maxTextureUnits && this.reservedTextureUnits.contains(this.nextUnit)) {
                this.nextUnit++;
            }
        }

        // Returns the next free unit.
        public int getNextUnit() {
            return this.nextUnit;
        }

        // Creates the samplers of the program.
        public ProgramSamplers build() {
            return new ProgramSamplers(Collections.unmodifiableList(new ArrayList<>(this.samplers)),
                    new ArrayList<>(this.calls));
        }
    }
}
