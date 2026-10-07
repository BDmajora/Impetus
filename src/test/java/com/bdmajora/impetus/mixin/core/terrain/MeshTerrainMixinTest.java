package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainRenderer;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeshTerrainMixinTest {
    @AfterEach
    void forget() {
        Statics.set(MeshTerrainRenderer.class, "activeConfig", null);
    }

    @Test
    void theFarPlaneReachesTheKeepDistance() {
        EntityRendererFarPlaneMixin renderer = Mixins.instance(EntityRendererFarPlaneMixin.class);
        Mixins.set(renderer, "farPlaneDistance", 128.0F);
        Mixins.call(renderer, "impetus$extendFarPlane", 0.0F, 2, Mixins.ci());
        assertEquals(128.0F, (float) Mixins.<Float>get(renderer, "farPlaneDistance"));

        Statics.set(MeshTerrainRenderer.class, "activeConfig",
                new MeshTerrainConfig(true, MeshTranslucencySorting.QUADS, MeshStatistics.NONE, true, 2048, 48, 8));
        Mixins.call(renderer, "impetus$extendFarPlane", 0.0F, 2, Mixins.ci());
        assertEquals(768.0F, (float) Mixins.<Float>get(renderer, "farPlaneDistance"));
    }
}
