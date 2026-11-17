package slimeknights.tconstruct.library.client.model;

import net.minecraft.client.renderer.block.model.IBakedModel;

// Test stand-in for TConstruct's tool model.
public class BakedToolModel {
    public static class CacheKey {
        final IBakedModel parent;

        public CacheKey(IBakedModel parent) {
            this.parent = parent;
        }
    }
}
