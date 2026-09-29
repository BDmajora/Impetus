package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.FastMath;
import com.bdmajora.extras.client.booster.FastRandom;
import com.bdmajora.extras.client.booster.StreamingUploader;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.MathHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoosterMixinsTest {
    @AfterEach
    void switchOff() {
        FastMath.enabled = false;
        FastRandom.enabled = false;
        StreamingUploader.enabled = false;
    }

    @Test
    void entitiesAndParticlesTakeTheFastRandomWhenSwitchedOn() {
        EntityRandomMixin entity = Mixins.instance(EntityRandomMixin.class);
        ParticleRandomMixin particle = Mixins.instance(ParticleRandomMixin.class);
        assertSame(Random.class, Mixins.<Random>call(entity, "impetus$fastRandom").getClass());
        assertSame(Random.class, Mixins.<Random>call(particle, "impetus$sharedRandom").getClass());
        FastRandom.enabled = true;
        assertInstanceOf(FastRandom.class, Mixins.call(entity, "impetus$fastRandom"));
        // Particles all share the one client-thread instance
        assertSame(FastRandom.particles(), Mixins.call(particle, "impetus$sharedRandom"));
    }

    @Test
    void theMathOverwritesMatchVanillaEitherWay() {
        assertNotNull(Mixins.instance(MathHelperFastMixin.class));
        for (boolean fast : new boolean[] {false, true}) {
            FastMath.enabled = fast;
            for (float angle : new float[] {-540.0F, -180.0F, -90.5F, 0.0F, 179.0F, 180.0F, 725.0F}) {
                float wrapped = MathHelperFastMixin.wrapDegrees(angle);
                assertTrue(wrapped >= -180.0F && wrapped < 180.0F, fast + " " + angle);
                double wide = MathHelperFastMixin.wrapDegrees((double) angle);
                assertTrue(wide >= -180.0D && wide < 180.0D, fast + " " + angle);
            }
            for (int value : new int[] {1, 2, 3, 16, 17, 1000}) {
                assertEquals(MathHelper.log2DeBruijn(value), MathHelperFastMixin.log2DeBruijn(value), fast + " " + value);
                assertEquals(MathHelper.log2(value), MathHelperFastMixin.log2(value), fast + " " + value);
            }
        }
    }

    @Test
    void theUploaderTakesOnlyTheDrawsItCanStream() {
        WorldVertexBufferUploaderMixin uploader = Mixins.instance(WorldVertexBufferUploaderMixin.class);
        CallbackInfo vanilla = Mixins.ci();
        Mixins.call(uploader, "impetus$streamDraw", new BufferBuilder(16), vanilla);
        assertFalse(vanilla.isCancelled());
    }
}
