package com.bdmajora.coarctatio.mixin.client.model.bake;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.model.TRSRTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.vecmath.Vector4f;
import java.util.Optional;

// Item quads in the default format are written straight into their 28 ints instead of through UnpackedBakedQuad.Builder, which unpacks and repacks every element per vertex; a generated item model has a quad per visible pixel edge, so this is most of an item bake. remap = false, Forge class
@Mixin(value = ItemLayerModel.class, remap = false)
public abstract class ItemLayerModelQuadMixin {
    private static final Vector4f coarctatio$position = new Vector4f();

    @Inject(method = "buildQuad", at = @At("HEAD"), cancellable = true)
    private static void coarctatio$buildPacked(VertexFormat format, Optional<TRSRTransformation> transform, EnumFacing side, TextureAtlasSprite sprite, int tint,
                                               float x0, float y0, float z0, float u0, float v0,
                                               float x1, float y1, float z1, float u1, float v1,
                                               float x2, float y2, float z2, float u2, float v2,
                                               float x3, float y3, float z3, float u3, float v3,
                                               CallbackInfoReturnable<BakedQuad> cir) {
        if (format != DefaultVertexFormats.ITEM) {
            return;
        }
        TRSRTransformation transformation = transform.isPresent() && !transform.get().isIdentity() ? transform.get() : null;
        int[] data = new int[28];
        coarctatio$vertex(data, 0, x0, y0, z0, u0, v0, transformation);
        coarctatio$vertex(data, 1, x1, y1, z1, u1, v1, transformation);
        coarctatio$vertex(data, 2, x2, y2, z2, u2, v2, transformation);
        coarctatio$vertex(data, 3, x3, y3, z3, u3, v3, transformation);
        // The normal from the transformed corners, which is what the builder's transformer would have produced
        ForgeHooksClient.fillNormal(data, side);
        cir.setReturnValue(new BakedQuad(data, tint, side, sprite, true, DefaultVertexFormats.ITEM));
    }

    // Position, opaque white, uv; the normal slot is filled afterwards
    private static void coarctatio$vertex(int[] data, int vertex, float x, float y, float z, float u, float v, TRSRTransformation transformation) {
        int offset = vertex * 7;
        if (transformation != null) {
            synchronized (coarctatio$position) {
                coarctatio$position.set(x, y, z, 1.0f);
                transformation.transformPosition(coarctatio$position);
                x = coarctatio$position.x;
                y = coarctatio$position.y;
                z = coarctatio$position.z;
            }
        }
        data[offset] = Float.floatToRawIntBits(x);
        data[offset + 1] = Float.floatToRawIntBits(y);
        data[offset + 2] = Float.floatToRawIntBits(z);
        data[offset + 3] = -1;
        data[offset + 4] = Float.floatToRawIntBits(u);
        data[offset + 5] = Float.floatToRawIntBits(v);
    }
}
