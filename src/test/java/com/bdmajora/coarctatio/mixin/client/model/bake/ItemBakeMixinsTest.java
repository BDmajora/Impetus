package com.bdmajora.coarctatio.mixin.client.model.bake;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.model.TRSRTransformation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.vecmath.Matrix4f;
import java.util.EnumMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemBakeMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void theEdgeBitmapsAreOneFlatArrayRatherThanFourBitSets() {
        ItemLayerFaceDataMixin faces = Mixins.concrete(ItemLayerFaceDataMixin.class);
        Mixins.set(faces, "vMax", 16);
        // The constructor's EnumMap.put is skipped, since nothing reads the BitSets any more
        assertNull(Mixins.call(faces, "coarctatio$skipBitSets", new EnumMap<EnumFacing, Object>(EnumFacing.class),
                EnumFacing.UP, new Object()));
        Mixins.call(faces, "coarctatio$allocate", 16, 16, Mixins.ci());

        faces.set(EnumFacing.WEST, 3, 4);
        assertTrue(faces.get(EnumFacing.WEST, 3, 4));
        // Each face has its own words, so the same pixel on another face is untouched
        assertFalse(faces.get(EnumFacing.EAST, 3, 4));
        assertFalse(faces.get(EnumFacing.WEST, 4, 4));
        for (EnumFacing facing : new EnumFacing[] {EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP, EnumFacing.DOWN}) {
            faces.set(facing, 0, 0);
            assertTrue(faces.get(facing, 0, 0));
        }
        // The model only ever produces those four
        assertThrows(IllegalArgumentException.class, () -> faces.set(EnumFacing.NORTH, 0, 0));
    }

    @Test
    void anItemQuadIsWrittenStraightIntoItsVertexData() {
        assertNotNull(Mixins.instance(ItemLayerModelQuadMixin.class));
        CallbackInfoReturnable<BakedQuad> cir = Mixins.cir();
        Mixins.call(ItemLayerModelQuadMixin.class, "coarctatio$buildPacked",
                DefaultVertexFormats.ITEM, Optional.empty(), EnumFacing.SOUTH, (TextureAtlasSprite) null, 0,
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 1f,
                1f, 1f, 0f, 1f, 1f,
                1f, 0f, 0f, 1f, 0f,
                cir);
        assertTrue(cir.isCancelled());
        BakedQuad quad = cir.getReturnValue();
        assertEquals(28, quad.getVertexData().length);
        assertEquals(EnumFacing.SOUTH, quad.getFace());
        // Position, opaque white, uv per vertex
        assertEquals(0f, Float.intBitsToFloat(quad.getVertexData()[0]));
        assertEquals(-1, quad.getVertexData()[3]);
        assertEquals(1f, Float.intBitsToFloat(quad.getVertexData()[7 + 1]));

        // A transformation is applied to every corner on the way in
        Matrix4f shift = new Matrix4f();
        shift.setIdentity();
        shift.setTranslation(new javax.vecmath.Vector3f(1f, 0f, 0f));
        CallbackInfoReturnable<BakedQuad> moved = Mixins.cir();
        Mixins.call(ItemLayerModelQuadMixin.class, "coarctatio$buildPacked",
                DefaultVertexFormats.ITEM, Optional.of(new TRSRTransformation(shift)), EnumFacing.SOUTH, (TextureAtlasSprite) null, 0,
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 1f,
                1f, 1f, 0f, 1f, 1f,
                1f, 0f, 0f, 1f, 0f,
                moved);
        assertEquals(1f, Float.intBitsToFloat(moved.getReturnValue().getVertexData()[0]));

        // Any other format is left to Forge's unpacking builder
        CallbackInfoReturnable<BakedQuad> other = Mixins.cir();
        Mixins.call(ItemLayerModelQuadMixin.class, "coarctatio$buildPacked",
                DefaultVertexFormats.BLOCK, Optional.empty(), EnumFacing.SOUTH, (TextureAtlasSprite) null, 0,
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 1f,
                1f, 1f, 0f, 1f, 1f,
                1f, 0f, 0f, 1f, 0f,
                other);
        assertFalse(other.isCancelled());
    }

    @Test
    void aThreeComponentPositionUnpacksWithWSet() {
        LightUtilUnpackMixin unpack = Mixins.concrete(LightUtilUnpackMixin.class);
        assertNotNull(unpack);

        VertexFormat format = DefaultVertexFormats.POSITION_TEX;
        float[] to = new float[4];
        Mixins.call(LightUtilUnpackMixin.class, "coarctatio$positionW", new int[28], to, format, 0, 0, Mixins.ci());
        // The builder path carried 1 here, and consumers transforming by the full matrix depend on it
        assertEquals(1.0f, to[3]);

        // A shorter destination, or an element that is not a three-component position, is left alone
        float[] shorter = new float[3];
        Mixins.call(LightUtilUnpackMixin.class, "coarctatio$positionW", new int[28], shorter, format, 0, 0, Mixins.ci());
        assertEquals(0f, shorter[2]);
        float[] uv = new float[4];
        Mixins.call(LightUtilUnpackMixin.class, "coarctatio$positionW", new int[28], uv, format, 0, 1, Mixins.ci());
        assertEquals(0f, uv[3]);
    }

    @Test
    void whetherATransformationIsTheIdentityIsAnsweredOnce() {
        TRSRTransformationIdentityMixin transformation = Mixins.instance(TRSRTransformationIdentityMixin.class);
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        Mixins.set(transformation, "matrix", identity);
        Mixins.call(transformation, "coarctatio$cacheIdentity", Mixins.ci());
        assertTrue(transformation.isIdentity());

        Matrix4f shifted = new Matrix4f();
        shifted.setIdentity();
        shifted.setTranslation(new javax.vecmath.Vector3f(1f, 2f, 3f));
        Mixins.set(transformation, "matrix", shifted);
        Mixins.call(transformation, "coarctatio$cacheIdentity", Mixins.ci());
        assertFalse(transformation.isIdentity());
    }
}
