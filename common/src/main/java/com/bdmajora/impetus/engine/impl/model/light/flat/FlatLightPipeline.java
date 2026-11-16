package com.bdmajora.impetus.engine.impl.model.light.flat;

import lombok.RequiredArgsConstructor;
import com.bdmajora.impetus.engine.impl.model.light.DiffuseProvider;
import com.bdmajora.impetus.engine.impl.model.light.LightPipeline;
import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.light.data.QuadLightData;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.api.util.NormI8;

import java.util.Arrays;

import static com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess.*;

// Lighter that uses the light value of the block in front of a face.
@RequiredArgsConstructor
public class FlatLightPipeline implements LightPipeline {
    // Light cache that supplies the light values.
    @lombok.NonNull
    private final LightDataAccess lightCache;
    // Source of the diffuse shading.
    @lombok.NonNull
    private final DiffuseProvider diffuseProvider;

    // Quad normal shading state of the lighter.
    private final boolean useQuadNormalsForShading;

    // Calculates one light value and one brightness for the whole quad.
    @Override
    public void calculate(ModelQuadView quad, int x, int y, int z, QuadLightData out, ModelQuadFacing cullFace, ModelQuadFacing lightFace, boolean shade, boolean applyAoDepthBlending) {
        int lightmap;

        if (!lightFace.isDirection()) {
            throw new IllegalStateException("Flat lighting needs an assigned light face");
        }

        int flags = quad.getFlags();
        // Light word of the block itself
        int word = this.lightCache.get(x, y, z);

        // Prefers the cull face
        if (cullFace.isDirection()) {
            lightmap = getOffsetLightmap(word, x, y, z, cullFace);
        } else {
            // Treats a parallel face of a full cube as aligned
            if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && unpackFC(word))) {
                lightmap = getOffsetLightmap(word, x, y, z, lightFace);
            } else {
                lightmap = getEmissiveLightmap(word);
            }
        }

        Arrays.fill(out.lm, lightmap);

        if ((flags & ModelQuadFlags.IS_VANILLA_SHADED) != 0 || !this.useQuadNormalsForShading) {
            Arrays.fill(out.br, this.diffuseProvider.getDiffuse(lightFace, shade));
        } else {
            this.applySidedBrightnessFromNormals(quad, out, shade);
        }
    }

    // Fills the brightness from the normal of the quad.
    public void applySidedBrightnessFromNormals(ModelQuadView quad, QuadLightData out, boolean shade) {
        int normal = quad.getModFaceNormal();
        Arrays.fill(out.br, this.diffuseProvider.getDiffuse(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal), shade));
    }

    // Returns the lightmap of the neighbor with the luminance of the origin.
    private int getOffsetLightmap(int word, int x, int y, int z, ModelQuadFacing face) {
        // Returns full brightness for an emitter
        if (unpackEM(word)) {
            return LightDataAccess.FULL_BRIGHT;
        }

        // Combines neighbor light with origin luminance
        int adjWord = this.lightCache.get(x, y, z, face);
        return LightDataAccess.pack(Math.max(unpackBL(adjWord), unpackLU(word)), unpackSL(adjWord));
    }
}
