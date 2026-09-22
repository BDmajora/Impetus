package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.gl.image.ImageBinding;
import com.bdmajora.impetus.umbra.gl.image.ImageHolder;
import com.bdmajora.impetus.umbra.gl.image.ImageLimits;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The images one program uses, each on a unit allocated for that program only when it declares the uniform; glUniform1i calls wait for the first update since they write to the bound program
public class ProgramImages {
    private final List<ImageBinding> imageBindings;
    private List<Uniform1iCall> initializer;

    private ProgramImages(List<ImageBinding> imageBindings, List<Uniform1iCall> initializer) {
        this.imageBindings = imageBindings;
        this.initializer = initializer;
    }

    // Starts registration for one program
    public static Builder builder(int program) {
        return new Builder(program);
    }

    // Must be called with this program bound; issues the deferred unit assignments on the first call, then rebinds every image since a render-target image's texture changes on flip
    public void update() {
        if (this.initializer != null) {
            for (Uniform1iCall call : this.initializer) {
                LWJGL.glUniform1i(call.location(), call.value());
            }
            this.initializer = null;
        }

        for (ImageBinding binding : this.imageBindings) {
            binding.update();
        }
    }

    // Units consumed
    public int getActiveImages() {
        return this.imageBindings.size();
    }

    public static final class Builder implements ImageHolder {
        private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

        private final int program;
        private final List<ImageBinding> images = new ArrayList<>();
        private final List<Uniform1iCall> calls = new ArrayList<>();
        private final int maxImageUnits;
        private int nextImageUnit;

        private Builder(int program) {
            this.program = program;
            this.maxImageUnits = ImageLimits.get().getMaxImageUnits();
        }

        // Whether the program declared this image uniform
        @Override
        public boolean hasImage(String name) {
            return LWJGL.glGetUniformLocation(this.program, name) != -1;
        }

        // Allocates a unit only if the uniform resolves
        @Override
        public void addTextureImage(IntSupplier textureID, int internalFormat, String name) {
            int location = LWJGL.glGetUniformLocation(this.program, name);
            if (location == -1) {
                // This program does not use this image. Costs nothing — this is the point of per-program allocation.
                return;
            }

            if (this.nextImageUnit >= this.maxImageUnits) {
                // Umbra throws here; this port logs and drops, so a pack overrunning the limit loses one effect rather than the whole pipeline mid-frame
                LOGGER.error("[Umbra] No image units left for '{}' (driver reports {}); it will be unbound",
                        name, this.maxImageUnits);
                return;
            }

            this.images.add(new ImageBinding(this.nextImageUnit, internalFormat, textureID));
            this.calls.add(new Uniform1iCall(location, this.nextImageUnit));
            this.nextImageUnit++;
        }

        // Finalises; the glUniform1i calls wait for first update
        public ProgramImages build() {
            return new ProgramImages(Collections.unmodifiableList(new ArrayList<>(this.images)),
                    new ArrayList<>(this.calls));
        }
    }
}
