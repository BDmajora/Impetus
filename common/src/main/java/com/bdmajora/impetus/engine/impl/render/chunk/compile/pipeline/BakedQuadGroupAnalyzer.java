package com.bdmajora.impetus.engine.impl.render.chunk.compile.pipeline;

import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;

import java.util.List;

public class BakedQuadGroupAnalyzer {
    // Whether the MC-138211 quad reorienting fix applies to this group; switchable because reorienting superimposed modded quads differently per layer causes z-fighting
    public static final int USE_REORIENTING = 0x1;
    public static final int USE_RENDER_PASS_OPTIMIZATION = 0x2;
    public static final int USE_ALL_THINGS = 0xFFFFFFFF;

    private int defaultRenderingFlags = 0;
    private int unassignedFaceRenderingFlags = 0;

    // Which light features a quad needs, from its flags
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

    // From the sprite, cached per sprite
    private static SpriteTransparencyLevel getQuadTransparencyLevel(BakedQuadView quad) {
        Object sprite = quad.impetus$getSprite();

        if ((quad.getFlags() & ModelQuadFlags.IS_PASS_OPTIMIZABLE) == 0 || sprite == null) {
            return SpriteTransparencyLevel.TRANSLUCENT;
        }

        return SpriteTransparencyLevel.Holder.getTransparencyLevel(sprite);
    }

    // Baseline for a block before per-quad analysis
    public void setDefaultRenderingFlags(int flags) {
        this.defaultRenderingFlags = flags;
        this.unassignedFaceRenderingFlags = flags;
    }

    // Unions the needs of every quad on a face
    public int getFlagsForRendering(ModelQuadFacing facing, List<? extends BakedQuadView> quads) {
        int quadRenderingFlags = facing == ModelQuadFacing.UNASSIGNED ? this.unassignedFaceRenderingFlags : this.defaultRenderingFlags;

        int quadsSize = quads.size();

        // By definition, singleton or empty lists of quads have a common config. Only check larger lists
        if (quadsSize >= 2) {
            // Disable reorienting if quads use different light configurations, otherwise layered quads may triangulate differently and z-fight
            int flagMask = -1;

            SpriteTransparencyLevel highestSeenLevel = SpriteTransparencyLevel.OPAQUE;

            // noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < quadsSize; i++) {
                var quad = BakedQuadView.of(quads.get(i));

                int newFlag = computeLightFlagMask(quad);
                if (flagMask == -1) {
                    flagMask = newFlag;
                } else if (newFlag != flagMask) {
                    // Disable reorienting
                    quadRenderingFlags &= ~USE_REORIENTING;
                }

                SpriteTransparencyLevel level = getQuadTransparencyLevel(quad);

                if (level.ordinal() < highestSeenLevel.ordinal()) {
                    // Downgrading will result in the quads being rendered in the wrong order, disable
                    quadRenderingFlags &= ~USE_RENDER_PASS_OPTIMIZATION;
                } else {
                    highestSeenLevel = level;
                }
            }
        }

        // Disable any flags in the null cullface that were disabled in other cullfaces
        this.unassignedFaceRenderingFlags &= quadRenderingFlags;

        return quadRenderingFlags;
    }

    // Downgrades to a cheaper pass when the quad's sprite is opaque
    public static Material chooseOptimalMaterial(int analyzerFlags, Material defaultMaterial, RenderPassConfiguration<?> renderPassConfiguration, BakedQuadView quad) {
        Object sprite = quad.impetus$getSprite();

        if (defaultMaterial == renderPassConfiguration.defaultSolidMaterial() || (analyzerFlags & USE_RENDER_PASS_OPTIMIZATION) == 0 || (quad.getFlags() & ModelQuadFlags.IS_PASS_OPTIMIZABLE) == 0 || sprite == null) {
            // No improvement possible
            return defaultMaterial;
        }

        return switch (SpriteTransparencyLevel.Holder.getTransparencyLevel(sprite)) {
            // Solid with no visual difference
            case OPAQUE -> renderPassConfiguration.defaultSolidMaterial();
            // Cutout_mipped with no visual difference, only when coming down from translucent
            case TRANSPARENT -> defaultMaterial == renderPassConfiguration.defaultTranslucentMaterial() ? renderPassConfiguration.defaultCutoutMippedMaterial() : defaultMaterial;
            default -> defaultMaterial;
        };
    }
}
