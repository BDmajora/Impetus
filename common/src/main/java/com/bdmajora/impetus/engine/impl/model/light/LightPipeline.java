package com.bdmajora.impetus.engine.impl.model.light;

import com.bdmajora.impetus.engine.impl.model.light.data.QuadLightData;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import org.jetbrains.annotations.NotNull;

// Lights model quads anywhere in the world.
public interface LightPipeline {
    // Lights one quad at model position x y z into out.
    void calculate(ModelQuadView quad, int x, int y, int z, QuadLightData out, @NotNull ModelQuadFacing cullFace,
                   @NotNull ModelQuadFacing lightFace, boolean shade, boolean applyAoDepthBlending);

    // resets any cached data held by this pipeline
    default void reset() {

    }
}
