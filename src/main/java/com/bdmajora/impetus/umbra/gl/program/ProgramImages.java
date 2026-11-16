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

// Image units bound to one program.
public class ProgramImages {
    // Images in unit order.
    private final List<ImageBinding> imageBindings;
    // Uniform writes of the image units or null after the first update.
    private List<Uniform1iCall> initializer;

    // Creates the images of a program.
    private ProgramImages(List<ImageBinding> imageBindings, List<Uniform1iCall> initializer) {
        this.imageBindings = imageBindings;
        this.initializer = initializer;
    }

    // Creates a builder for a program.
    public static Builder builder(int program) {
        return new Builder(program);
    }

    // Writes the unit uniforms once and binds every image.
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

    // Returns the number of bound images.
    public int getActiveImages() {
        return this.imageBindings.size();
    }

    // Collector of the images a program uses.
    public static final class Builder implements ImageHolder {
        // Logger of exhausted image units.
        private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

        // GL name of the program.
        private final int program;
        // Images in unit order.
        private final List<ImageBinding> images = new ArrayList<>();
        // Uniform writes of the units.
        private final List<Uniform1iCall> calls = new ArrayList<>();
        // Number of image units the driver allows.
        private final int maxImageUnits;
        // Next free image unit.
        private int nextImageUnit;

        // Creates a builder for a program.
        private Builder(int program) {
            this.program = program;
            this.maxImageUnits = ImageLimits.get().getMaxImageUnits();
        }

        // Returns whether the program declares an image of a name.
        @Override
        public boolean hasImage(String name) {
            return LWJGL.glGetUniformLocation(this.program, name) != -1;
        }

        // Binds a texture image to the next unit when the program declares it and a unit is left.
        @Override
        public void addTextureImage(IntSupplier textureID, int internalFormat, String name) {
            int location = LWJGL.glGetUniformLocation(this.program, name);
            if (location == -1) {
                return;
            }

            if (this.nextImageUnit >= this.maxImageUnits) {
                LOGGER.error("[Umbra] No image units left for '{}' (driver reports {}); it will be unbound",
                        name, this.maxImageUnits);
                return;
            }

            this.images.add(new ImageBinding(this.nextImageUnit, internalFormat, textureID));
            this.calls.add(new Uniform1iCall(location, this.nextImageUnit));
            this.nextImageUnit++;
        }

        // Creates the images of the program.
        public ProgramImages build() {
            return new ProgramImages(Collections.unmodifiableList(new ArrayList<>(this.images)),
                    new ArrayList<>(this.calls));
        }
    }
}
