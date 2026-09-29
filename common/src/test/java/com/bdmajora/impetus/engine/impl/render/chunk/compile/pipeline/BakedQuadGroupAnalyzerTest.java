package com.bdmajora.impetus.engine.impl.render.chunk.compile.pipeline;

import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffersTest;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.testing.Passes;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakedQuadGroupAnalyzerTest {
    private static BakedQuadView quad(boolean ao, boolean shade, SpriteTransparencyLevel level, boolean optimizable) {
        BakedQuadView quad = Mockito.mock(BakedQuadView.class);
        Mockito.when(quad.hasAmbientOcclusion()).thenReturn(ao);
        Mockito.when(quad.hasShade()).thenReturn(shade);
        Mockito.when(quad.getFlags()).thenReturn(optimizable ? ModelQuadFlags.IS_PASS_OPTIMIZABLE : 0);
        SpriteTransparencyLevel.Holder sprite = level == null ? null : () -> level;
        Mockito.when(quad.impetus$getSprite()).thenReturn(sprite);
        return quad;
    }

    @Test
    void mixedLightConfigsDisableReorientingAndDowngradesDisableOptimisation() {
        BakedQuadGroupAnalyzer analyzer = new BakedQuadGroupAnalyzer();
        analyzer.setDefaultRenderingFlags(BakedQuadGroupAnalyzer.USE_ALL_THINGS);
        assertEquals(BakedQuadGroupAnalyzer.USE_ALL_THINGS, analyzer.getFlagsForRendering(ModelQuadFacing.POS_X, List.of(quad(true, true, SpriteTransparencyLevel.OPAQUE, true))));
        int mixed = analyzer.getFlagsForRendering(ModelQuadFacing.POS_Y, List.of(
                quad(true, true, SpriteTransparencyLevel.OPAQUE, true),
                quad(false, true, SpriteTransparencyLevel.TRANSLUCENT, true),
                quad(true, false, SpriteTransparencyLevel.OPAQUE, true)));
        assertEquals(0, mixed & BakedQuadGroupAnalyzer.USE_REORIENTING);
        assertEquals(0, mixed & BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION);
        int unassigned = analyzer.getFlagsForRendering(ModelQuadFacing.UNASSIGNED, List.of());
        assertEquals(0, unassigned & BakedQuadGroupAnalyzer.USE_REORIENTING);
        analyzer.getFlagsForRendering(ModelQuadFacing.NEG_Z, List.of(quad(true, true, null, true), quad(true, true, null, false)));
    }

    @Test
    void optimalMaterialDowngradesByTransparency() {
        var config = ChunkBuildBuffersTest.CONFIG;
        BakedQuadView opaque = quad(true, true, SpriteTransparencyLevel.OPAQUE, true);
        BakedQuadView transparent = quad(true, true, SpriteTransparencyLevel.TRANSPARENT, true);
        BakedQuadView translucent = quad(true, true, SpriteTransparencyLevel.TRANSLUCENT, true);
        BakedQuadView bare = quad(true, true, null, true);
        BakedQuadView flagless = quad(true, true, SpriteTransparencyLevel.OPAQUE, false);
        int all = BakedQuadGroupAnalyzer.USE_ALL_THINGS;
        assertSame(Passes.SOLID_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.SOLID_MATERIAL, config, opaque));
        assertSame(Passes.TRANSLUCENT_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(0, Passes.TRANSLUCENT_MATERIAL, config, opaque));
        assertSame(Passes.TRANSLUCENT_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.TRANSLUCENT_MATERIAL, config, bare));
        assertSame(Passes.TRANSLUCENT_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.TRANSLUCENT_MATERIAL, config, flagless));
        assertSame(Passes.SOLID_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.TRANSLUCENT_MATERIAL, config, opaque));
        assertSame(Passes.CUTOUT_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.TRANSLUCENT_MATERIAL, config, transparent));
        assertSame(Passes.CUTOUT_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.CUTOUT_MATERIAL, config, transparent));
        assertSame(Passes.TRANSLUCENT_MATERIAL, BakedQuadGroupAnalyzer.chooseOptimalMaterial(all, Passes.TRANSLUCENT_MATERIAL, config, translucent));
    }
}
