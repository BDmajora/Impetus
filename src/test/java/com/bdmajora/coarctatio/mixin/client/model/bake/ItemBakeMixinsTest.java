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
        // Checks the BitSet put is skipped.
        assertNull(Mixins.call(faces, "coarctatio$skipBitSets", new EnumMap<EnumFacing, Object>(EnumFacing.class),
                EnumFacing.UP, new Object()));
        Mixins.call(faces, "coarctatio$allocate", 16, 16, Mixins.ci());

        faces.set(EnumFacing.WEST, 3, 4);
        assertTrue(faces.get(EnumFacing.WEST, 3, 4));
        // Checks each face has its own words.
        assertFalse(faces.get(EnumFacing.EAST, 3, 4));
        assertFalse(faces.get(EnumFacing.WEST, 4, 4));
        for (EnumFacing facing : new EnumFacing[] {EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP, EnumFacing.DOWN}) {
            faces.set(facing, 0, 0);
            assertTrue(faces.get(facing, 0, 0));
        }
        // The model only ever produces those four
        assertThrows(IllegalArgumentException.class, () -> faces.set(EnumFacing.NORTH, 0, 0));

        // Checks a frame taller than wide.
        ItemLayerFaceDataMixin tall = Mixins.concrete(ItemLayerFaceDataMixin.class);
        Mixins.set(tall, "vMax", 32);
        Mixins.call(tall, "coarctatio$allocate", 16, 32, Mixins.ci());
        tall.set(EnumFacing.DOWN, 15, 31);
        assertTrue(tall.get(EnumFacing.DOWN, 15, 31));
        tall.set(EnumFacing.UP, 15, 31);
        assertFalse(tall.get(EnumFacing.DOWN, 0, 0));
        assertFalse(tall.get(EnumFacing.WEST, 15, 31));
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
        // Checks the position and colour and uv.
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
        // Checks the fourth component.
        assertEquals(1.0f, to[3]);

        // Checks a short destination.
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

        // Checks signed zero and NaN.
        Matrix4f signed = new Matrix4f();
        signed.setIdentity();
        signed.m03 = -0.0F;
        Mixins.set(transformation, "matrix", signed);
        Mixins.call(transformation, "coarctatio$cacheIdentity", Mixins.ci());
        assertTrue(transformation.isIdentity());
        signed.m11 = Float.NaN;
        Mixins.call(transformation, "coarctatio$cacheIdentity", Mixins.ci());
        assertFalse(transformation.isIdentity());
        for (int element = 0; element < 16; element++) {
            Matrix4f off = new Matrix4f();
            off.setIdentity();
            off.setElement(element / 4, element % 4, off.getElement(element / 4, element % 4) + 0.5F);
            Mixins.set(transformation, "matrix", off);
            Mixins.call(transformation, "coarctatio$cacheIdentity", Mixins.ci());
            assertFalse(transformation.isIdentity(), "element " + element);
        }
        // Checks the mixin holds no static state.
        assertTrue(java.util.Arrays.stream(TRSRTransformationIdentityMixin.class.getDeclaredFields())
                .noneMatch(field -> !field.isSynthetic() && java.lang.reflect.Modifier.isStatic(field.getModifiers())));
    }
}
