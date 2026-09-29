package com.bdmajora.extras.client.booster;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

class BoosterTest {
    private static final int REGION_SIZE = 4 << 20;
    private final Map<Class<?>, MockedStatic<?>> gl = new LinkedHashMap<>();

    @BeforeEach
    void mockGl() {
        for (Class<?> type : List.of(GL11.class, GL13.class, GL15.class, GL20.class, GL30.class, GL32.class, GL44.class, ARBBufferStorage.class)) {
            gl.put(type, Mockito.mockStatic(type));
        }
        reset();
        StreamingUploader.enabled = true;
    }

    @AfterEach
    void unmockGl() {
        gl.values().forEach(MockedStatic::close);
        gl.clear();
        reset();
        StreamingUploader.enabled = false;
        GLContext.reset();
    }

    private static void reset() {
        Mixins.set(StreamingUploader.class, "buffer", -1);
        Mixins.set(StreamingUploader.class, "unavailable", false);
        Mixins.set(StreamingUploader.class, "persistent", false);
        Mixins.set(StreamingUploader.class, "mapped", null);
        Mixins.set(StreamingUploader.class, "region", 0);
        Mixins.set(StreamingUploader.class, "cursor", 0);
        java.util.Arrays.fill(Mixins.<GLSync[]>get(StreamingUploader.class, "fences"), null);
    }

    @SuppressWarnings("unchecked")
    private <T> MockedStatic<T> gl(Class<T> type) {
        return (MockedStatic<T>) gl.get(type);
    }

    // A quad with position, texture, colour, normal and padding, every kind the fixed-function path binds
    private static BufferBuilder quad() {
        BufferBuilder builder = new BufferBuilder(256);
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for (int i = 0; i < 4; i++) {
            builder.pos(i, 0, 0).tex(0, 0).color(1.0F, 1.0F, 1.0F, 1.0F).normal(0, 1, 0).endVertex();
        }
        builder.finishDrawing();
        return builder;
    }

    private static ContextCapabilities caps() {
        return GLContext.getCapabilities();
    }

    @Test
    void drawsThatDoNotQualifyAreHandedBackToVanilla() {
        StreamingUploader.enabled = false;
        assertFalse(StreamingUploader.draw(quad()));
        StreamingUploader.enabled = true;
        BufferBuilder empty = new BufferBuilder(16);
        empty.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        assertFalse(StreamingUploader.draw(empty));
        // Without core buffers the ring is never built
        caps().OpenGL15 = false;
        assertFalse(StreamingUploader.draw(quad()));
        assertFalse(StreamingUploader.draw(quad()));
        assertNotNull(Mixins.construct(StreamingUploader.class));
    }

    @Test
    void theOrphaningRingStreamsDrawsAndRespecifiesWhenFull() {
        caps().OpenGL44 = false;
        caps().GL_ARB_buffer_storage = false;
        gl(GL15.class).when(GL15::glGenBuffers).thenReturn(7);
        BufferBuilder builder = quad();
        assertTrue(StreamingUploader.draw(builder));
        assertEquals(0, builder.getVertexCount());
        gl(GL15.class).verify(() -> GL15.glBufferSubData(eq(GL15.GL_ARRAY_BUFFER), eq(0L), any(ByteBuffer.class)));
        gl(GL11.class).verify(() -> GL11.glDrawArrays(GL11.GL_QUADS, 0, 4));
        // A shader attribute goes through the generic array calls
        VertexFormatElement generic = new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.GENERIC, 2);
        Mixins.call(StreamingUploader.class, "enable", generic, 0L, 8);
        Mixins.call(StreamingUploader.class, "disable", generic);
        gl(GL20.class).verify(() -> GL20.glEnableVertexAttribArray(0));
        gl(GL20.class).verify(() -> GL20.glDisableVertexAttribArray(0));
        // Past the end of the ring the whole buffer is orphaned first
        Mixins.set(StreamingUploader.class, "cursor", 4 * REGION_SIZE - 16);
        assertTrue(StreamingUploader.draw(quad()));
        gl(GL15.class).verify(() -> GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4 * REGION_SIZE, GL15.GL_STREAM_DRAW), Mockito.times(2));
    }

    @Test
    void aStorageBufferThatWillNotMapFallsBackToOrphaning() {
        assertTrue(StreamingUploader.draw(quad()));
        gl(GL44.class).verify(() -> GL44.glBufferStorage(eq(GL15.GL_ARRAY_BUFFER), anyLong(), anyInt()));
        gl(GL15.class).verify(() -> GL15.glDeleteBuffers(anyInt()));
        assertFalse((boolean) Mixins.<Boolean>get(StreamingUploader.class, "persistent"));

        // The ARB form is used where core 4.4 is missing
        reset();
        caps().OpenGL44 = false;
        assertTrue(StreamingUploader.draw(quad()));
        gl(ARBBufferStorage.class).verify(() -> ARBBufferStorage.glBufferStorage(eq(GL15.GL_ARRAY_BUFFER), anyLong(), anyInt()));
    }

    @Test
    void thePersistentRingMovesOnOnlyToRegionsTheGpuHasFinished() {
        ByteBuffer mapped = ByteBuffer.allocateDirect(4 * REGION_SIZE);
        gl(GL30.class).when(() -> GL30.glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt(), any())).thenReturn(mapped);
        GLSync fence = mock(GLSync.class);
        gl(GL32.class).when(() -> GL32.glFenceSync(anyInt(), anyInt())).thenReturn(fence);
        assertTrue(StreamingUploader.draw(quad()));
        assertTrue((boolean) Mixins.<Boolean>get(StreamingUploader.class, "persistent"));
        assertTrue(mapped.getFloat(0) == 0.0F);

        // A full region is fenced and the next free one taken
        Mixins.set(StreamingUploader.class, "cursor", REGION_SIZE);
        assertTrue(StreamingUploader.draw(quad()));
        assertEquals(1, (int) Mixins.<Integer>get(StreamingUploader.class, "region"));
        GLSync[] fences = Mixins.get(StreamingUploader.class, "fences");
        assertSame(fence, fences[0]);

        // A next region the GPU still reads sends this draw down vanilla's path, changing nothing
        fences[2] = mock(GLSync.class);
        gl(GL32.class).when(() -> GL32.glClientWaitSync(any(), anyInt(), anyLong())).thenReturn(GL32.GL_TIMEOUT_EXPIRED);
        Mixins.set(StreamingUploader.class, "cursor", REGION_SIZE);
        assertFalse(StreamingUploader.draw(quad()));
        assertEquals(1, (int) Mixins.<Integer>get(StreamingUploader.class, "region"));
        // Once it has signalled the fence is freed and the region used
        gl(GL32.class).when(() -> GL32.glClientWaitSync(any(), anyInt(), anyLong())).thenReturn(GL32.GL_ALREADY_SIGNALED);
        assertTrue(StreamingUploader.draw(quad()));
        assertNull(fences[2]);
        // A failed wait retires the ring for the session
        fences[3] = mock(GLSync.class);
        gl(GL32.class).when(() -> GL32.glClientWaitSync(any(), anyInt(), anyLong())).thenReturn(GL32.GL_WAIT_FAILED);
        Mixins.set(StreamingUploader.class, "cursor", REGION_SIZE);
        assertFalse(StreamingUploader.draw(quad()));
        assertTrue((boolean) Mixins.<Boolean>get(StreamingUploader.class, "unavailable"));
        assertFalse(StreamingUploader.draw(quad()));

        // A draw bigger than one region never fits the persistent ring
        reset();
        Mixins.set(StreamingUploader.class, "persistent", true);
        Mixins.set(StreamingUploader.class, "buffer", 1);
        BufferBuilder huge = new BufferBuilder(REGION_SIZE / 4 + 64);
        huge.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        for (int i = 0; i < REGION_SIZE / 12 + 4; i++) {
            huge.pos(0, 0, 0).endVertex();
        }
        assertFalse(StreamingUploader.draw(huge));
    }

    @Test
    void theMasterSwitchGatesEveryBoosterPart() {
        ExtrasConfig.GpuBoosterSettings settings = new ExtrasConfig.GpuBoosterSettings();
        settings.enabled = true;
        settings.fastMath = true;
        settings.fastRandom = true;
        settings.streamUploads = true;
        GpuBooster.apply(settings);
        assertTrue(FastMath.enabled && FastRandom.enabled && StreamingUploader.enabled);
        settings.enabled = false;
        GpuBooster.apply(settings);
        assertFalse(FastMath.enabled || FastRandom.enabled || StreamingUploader.enabled);
        assertNotNull(Mixins.construct(GpuBooster.class));
    }

    @Test
    void theFastMathMatchesVanillaEverywhereAnEntityCanBe() {
        for (float angle : new float[] {-721.5F, -180.0F, -179.9F, 0.0F, 179.9F, 180.0F, 540.25F}) {
            float wrapped = FastMath.wrapDegrees(angle);
            assertTrue(wrapped >= -180.0F && wrapped < 180.0F, Float.toString(angle));
            assertEquals(0.0F, (float) Math.IEEEremainder(wrapped - angle, 360.0), 1e-3F);
            double wide = FastMath.wrapDegrees((double) angle);
            assertTrue(wide >= -180.0D && wide < 180.0D);
        }
        assertEquals(0, FastMath.ceilLog2(1));
        assertEquals(4, FastMath.ceilLog2(16));
        assertEquals(5, FastMath.ceilLog2(17));
        assertEquals(4, FastMath.floorLog2(31));
        assertEquals(-1, FastMath.floorLog2(0));
        assertNotNull(Mixins.construct(FastMath.class));
    }

    @Test
    void theFastRandomIsASoundRandom() {
        FastRandom seeded = new FastRandom(42L);
        FastRandom again = new FastRandom(42L);
        assertEquals(seeded.nextLong(), again.nextLong());
        assertSame(FastRandom.particles(), FastRandom.particles());
        FastRandom random = new FastRandom();
        double sum = 0;
        double squares = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            double value = random.nextGaussian();
            sum += value;
            squares += value * value;
        }
        // A standard normal: mean near zero, variance near one
        assertEquals(0.0, sum / n, 0.02);
        assertEquals(1.0, squares / n, 0.02);
        float f = random.nextFloat();
        assertTrue(f >= 0.0F && f < 1.0F);
        double d = random.nextDouble();
        assertTrue(d >= 0.0D && d < 1.0D);
        random.nextBoolean();
        random.nextInt();
        assertTrue(random.nextInt(10) < 10);
        assertInstanceOf(Random.class, random);
    }
}
