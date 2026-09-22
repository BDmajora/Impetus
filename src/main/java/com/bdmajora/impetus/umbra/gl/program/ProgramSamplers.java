package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.sampler.SamplerBinding;
import com.bdmajora.impetus.umbra.gl.sampler.SamplerLimits;
import com.bdmajora.impetus.lwjgl.GL11;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The samplers one program uses, each on a unit allocated for that program alone; per-program because a pack can declare 49 names against 32 units, and overruns log and drop rather than throw
public final class ProgramSamplers {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final List<SamplerBinding> samplerBindings;
    private List<Uniform1iCall> initializer;

    private ProgramSamplers(List<SamplerBinding> samplerBindings, List<Uniform1iCall> initializer) {
        this.samplerBindings = samplerBindings;
        this.initializer = initializer;
    }

    // Starts registration, skipping units the engine already uses
    public static Builder builder(int program, Set<Integer> reservedTextureUnits) {
        return new Builder(program, reservedTextureUnits);
    }

    // Must be called with this program bound; issues deferred unit assignments on the first call, then rebinds every texture, restoring the selector EXACTLY ONCE after the run (see GlTextureUnits.bindTextureInRun, and Iris does the same)
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

    // Units consumed
    public int getActiveSamplers() {
        return this.samplerBindings.size();
    }

    public static final class Builder {
        private final int program;
        private final Set<Integer> reservedTextureUnits;
        private final List<SamplerBinding> samplers = new ArrayList<>();
        private final List<Uniform1iCall> calls = new ArrayList<>();
        private final int maxTextureUnits;
        private int nextUnit;
        private boolean exhaustedReported;

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

        // Whether this program declares that name as an ACTIVE uniform; a sampler the compiler optimised out reports false, correctly, since it needs no unit
        public boolean hasSampler(String name) {
            return LWJGL.glGetUniformLocation(this.program, name) != -1;
        }

        // Points a sampler at a unit this builder does not own (the block atlas and lightmap vanilla binds); the unit must already be reserved or the allocator hands it out again
        public void addExternalSampler(int textureUnit, String... names) {
            if (!this.reservedTextureUnits.contains(textureUnit)) {
                LOGGER.error("[Umbra] Sampler(s) {} point at unit {}, which is not reserved; the allocator may reuse it",
                        String.join("/", names), textureUnit);
            }
            for (String name : names) {
                int location = LWJGL.glGetUniformLocation(this.program, name);
                if (location != -1) {
                    this.calls.add(new Uniform1iCall(location, textureUnit));
                }
            }
        }

        // The GL_TEXTURE_2D case of the call below, which is most of them
        public boolean addDynamicSampler(IntSupplier texture, String... names) {
            return addDynamicSampler(GL11.GL_TEXTURE_2D, texture, names);
        }

        // Allocates ONE unit shared by all the given names (aliases like colortex0 and gcolor), only when the program declares at least one; returns whether a unit was consumed so callers can offer every sampler blindly
        public boolean addDynamicSampler(int textureTarget, IntSupplier texture, String... names) {
            boolean used = false;
            for (String name : names) {
                int location = LWJGL.glGetUniformLocation(this.program, name);
                if (location == -1) {
                    // Not declared by this program. Costs nothing — this is the point of per-program allocation.
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

        // Advances past units the engine owns
        private void skipReserved() {
            while (this.nextUnit < this.maxTextureUnits && this.reservedTextureUnits.contains(this.nextUnit)) {
                this.nextUnit++;
            }
        }

        // The first unit not yet handed out, for callers that report the resulting layout
        public int getNextUnit() {
            return this.nextUnit;
        }

        // Finalises the unit assignments
        public ProgramSamplers build() {
            return new ProgramSamplers(Collections.unmodifiableList(new ArrayList<>(this.samplers)),
                    new ArrayList<>(this.calls));
        }
    }
}
