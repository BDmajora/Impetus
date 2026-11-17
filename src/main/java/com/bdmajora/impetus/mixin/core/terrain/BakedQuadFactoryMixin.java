package com.bdmajora.impetus.mixin.core.terrain;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes FaceBakery.
@Mixin(FaceBakery.class)
public class BakedQuadFactoryMixin {
    // Changes the return value of makeBakedQuad.
    @ModifyReturnValue(method = "makeBakedQuad(Lorg/lwjgl/util/vector/Vector3f;Lorg/lwjgl/util/vector/Vector3f;Lnet/minecraft/client/renderer/block/model/BlockPartFace;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/util/EnumFacing;Lnet/minecraftforge/common/model/ITransformation;Lnet/minecraft/client/renderer/block/model/BlockPartRotation;ZZ)Lnet/minecraft/client/renderer/block/model/BakedQuad;", at = @At("RETURN"))
    private BakedQuad setMaterialClassification(BakedQuad quad, @Local(ordinal = 0, argsOnly = true) BlockPartFace face, @Local(ordinal = 0, argsOnly = true) TextureAtlasSprite sprite) {
        handleMaterialClassifications(quad, sprite, face);
        return quad;
    }

    // Marks quads whose UVs cover the whole sprite.
    private static void handleMaterialClassifications(BakedQuad quad, TextureAtlasSprite sprite, BlockPartFace face) {
        if (sprite.getClass() == TextureAtlasSprite.class) {
            float minUV = Float.MAX_VALUE, maxUV = -Float.MAX_VALUE;
            float[] uvs = face.blockFaceUV.uvs;

            for (float uv : uvs) {
                minUV = Math.min(minUV, uv);
                maxUV = Math.max(maxUV, uv);
            }

            if (minUV >= 0 && maxUV <= 16) {
                BakedQuadView view = (BakedQuadView)(Object)quad;
                view.addFlags(ModelQuadFlags.IS_TRUSTED_SPRITE);
            }
        }
    }
}
