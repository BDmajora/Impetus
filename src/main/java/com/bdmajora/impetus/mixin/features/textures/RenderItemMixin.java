package com.bdmajora.impetus.mixin.features.textures;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.bdmajora.impetus.impl.render.terrain.sprite.SpriteUtil;

// Mixin that changes RenderItem.
@Mixin(RenderItem.class)
public class RenderItemMixin {
    // Changes an expression value in renderQuads.
    @ModifyExpressionValue(method = "renderQuads", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", remap = false))
    private Object markSpriteActive(Object o) {
        if (o instanceof BakedQuad quad) {
            var sprite = quad.getSprite();
            if (sprite != null) {
                SpriteUtil.markSpriteActive(sprite);
            }
        }
        return o;
    }
}
