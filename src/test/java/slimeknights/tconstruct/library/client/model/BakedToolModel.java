package slimeknights.tconstruct.library.client.model;

import net.minecraft.client.renderer.block.model.IBakedModel;

// Test stand-in for TConstruct's tool model, whose cache key names the parent model the per-parent cache is keyed by
public class BakedToolModel {
    public static class CacheKey {
        final IBakedModel parent;

        public CacheKey(IBakedModel parent) {
            this.parent = parent;
        }
    }
}
