package com.bdmajora.impetus.engine.impl.render.chunk.compile.pipeline;

import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;

import java.util.List;

// Decides which rendering optimizations apply to the quads of a block.
public class BakedQuadGroupAnalyzer {
    // Flag that allows reorienting the quads of a group.
    public static final int USE_REORIENTING = 0x1;
    // Flag that allows moving quads to cheaper render passes.
    public static final int USE_RENDER_PASS_OPTIMIZATION = 0x2;
    // Every flag.
    public static final int USE_ALL_THINGS = 0xFFFFFFFF;

    // Flags of every face.
    private int defaultRenderingFlags = 0;
    // Flags of the unassigned face.
    private int unassignedFaceRenderingFlags = 0;

    // Returns a mask of the light features of a quad.
    private static int computeLightFlagMask(BakedQuadView quad) {
        int flag = 0;

        if (quad.hasAmbientOcclusion()) {
            flag |= 1;
        }

        if (quad.hasShade()) {
            flag |= 2;
        }

        return flag;
    }

    // Returns the transparency level of the sprite of a quad.
    private static SpriteTransparencyLevel getQuadTransparencyLevel(BakedQuadView quad) {
        Object sprite = quad.impetus$getSprite();

        if ((quad.getFlags() & ModelQuadFlags.IS_PASS_OPTIMIZABLE) == 0 || sprite == null) {
            return SpriteTransparencyLevel.TRANSLUCENT;
        }

        return SpriteTransparencyLevel.Holder.getTransparencyLevel(sprite);
    }

    // Stores the flags every face starts with.
    public void setDefaultRenderingFlags(int flags) {
        this.defaultRenderingFlags = flags;
        this.unassignedFaceRenderingFlags = flags;
    }

    // Returns the rendering flags that every quad of a face allows.
    public int getFlagsForRendering(ModelQuadFacing facing, List<? extends BakedQuadView> quads) {
        int quadRenderingFlags = facing == ModelQuadFacing.UNASSIGNED ? this.unassignedFaceRenderingFlags : this.defaultRenderingFlags;

        int quadsSize = quads.size();

        // Checks only lists of two or more quads
        if (quadsSize >= 2) {
            // Compares the light features of the quads
            int flagMask = -1;

            SpriteTransparencyLevel highestSeenLevel = SpriteTransparencyLevel.OPAQUE;

            // noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < quadsSize; i++) {
                var quad = BakedQuadView.of(quads.get(i));

                int newFlag = computeLightFlagMask(quad);
                if (flagMask == -1) {
                    flagMask = newFlag;
                } else if (newFlag != flagMask) {
                    // Disables reorienting
                    quadRenderingFlags &= ~USE_REORIENTING;
                }

                SpriteTransparencyLevel level = getQuadTransparencyLevel(quad);

                if (level.ordinal() < highestSeenLevel.ordinal()) {
                    // Disables the pass optimization
                    quadRenderingFlags &= ~USE_RENDER_PASS_OPTIMIZATION;
                } else {
                    highestSeenLevel = level;
                }
            }
        }

        // Applies the flags to the unassigned face
        this.unassignedFaceRenderingFlags &= quadRenderingFlags;

        return quadRenderingFlags;
    }

    // Returns the cheapest material that looks the same for a quad.
    public static Material chooseOptimalMaterial(int analyzerFlags, Material defaultMaterial, RenderPassConfiguration<?> renderPassConfiguration, BakedQuadView quad) {
        Object sprite = quad.impetus$getSprite();

        if (defaultMaterial == renderPassConfiguration.defaultSolidMaterial() || (analyzerFlags & USE_RENDER_PASS_OPTIMIZATION) == 0 || (quad.getFlags() & ModelQuadFlags.IS_PASS_OPTIMIZABLE) == 0 || sprite == null) {
            // Keeps the material
            return defaultMaterial;
        }

        return switch (SpriteTransparencyLevel.Holder.getTransparencyLevel(sprite)) {
            // Uses the solid material
            case OPAQUE -> renderPassConfiguration.defaultSolidMaterial();
            // Uses the cutout material for translucent quads
            case TRANSPARENT -> defaultMaterial == renderPassConfiguration.defaultTranslucentMaterial() ? renderPassConfiguration.defaultCutoutMippedMaterial() : defaultMaterial;
            default -> defaultMaterial;
        };
    }
}
