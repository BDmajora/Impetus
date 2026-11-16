package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.util.collections.DoubleBufferedQueue;
import com.bdmajora.impetus.engine.impl.util.color.BoxBlur;
import com.bdmajora.impetus.engine.impl.util.suppliers.ExpiringSupplier;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Tests argument limits of the small utilities.
class MiscUtilEdgeTest {
    // Blurs with a radius wider than the image and rejects invalid radii and sizes.
    @Test
    void boxBlurLimits() {
        BoxBlur.ColorBuffer a = new BoxBlur.ColorBuffer(3, 2);
        BoxBlur.ColorBuffer b = new BoxBlur.ColorBuffer(3, 2);
        a.set(0, 0, 0xFFFFFFFF);
        BoxBlur.blur(a, b, 10);
        BoxBlur.blur(a, b, 0);
        BoxBlur.blur(a, b, 32767);
        assertThrows(IllegalArgumentException.class, () -> BoxBlur.blur(a, b, -1));
        assertThrows(IllegalArgumentException.class, () -> BoxBlur.blur(a, b, 32768));
        assertThrows(IllegalArgumentException.class, () -> BoxBlur.blur(a, new BoxBlur.ColorBuffer(2, 3), 1));
        assertThrows(NullPointerException.class, () -> BoxBlur.blur(null, b, 1));
        BoxBlur.ColorBuffer empty = new BoxBlur.ColorBuffer(0, 5);
        BoxBlur.blur(empty, new BoxBlur.ColorBuffer(0, 5), 3);
        assertThrows(IllegalArgumentException.class, () -> new BoxBlur.ColorBuffer(-1, 2));
        assertThrows(ArithmeticException.class, () -> new BoxBlur.ColorBuffer(Integer.MAX_VALUE, 2));
        BoxBlur.ColorBuffer one = new BoxBlur.ColorBuffer(1, 1);
        one.set(0, 0, 0xFF102030);
        BoxBlur.blur(one, new BoxBlur.ColorBuffer(1, 1), 5);
        assertEquals(0xFF102030, one.get(0, 0));
    }

    // Blurs a row to the average of its pixels.
    @Test
    void boxBlurAverages() {
        BoxBlur.ColorBuffer a = new BoxBlur.ColorBuffer(2, 1);
        a.set(0, 0, 0xFF000000);
        a.set(1, 0, 0xFFFFFFFF);
        BoxBlur.blur(a, new BoxBlur.ColorBuffer(2, 1), 1);
        int left = a.get(0, 0) & 0xFF;
        int right = a.get(1, 0) & 0xFF;
        assertTrue(left > 60 && left < 200, "" + left);
        assertTrue(right > 60 && right < 200, "" + right);
    }

    // Returns a zero normal for degenerate and non finite quads.
    @Test
    void normals() {
        Vector3f out = new Vector3f(9, 9, 9);
        QuadUtil.faceNormal(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, out);
        assertEquals(new Vector3f(), out);
        QuadUtil.faceNormal(Float.NaN, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, out);
        assertEquals(new Vector3f(), out);
        QuadUtil.faceNormal(0, 0, 0, 1e30f, 0, 0, 1e30f, 1e30f, 1e30f, 0, 1e30f, 0, out);
        assertFalse(Float.isNaN(out.x) || Float.isNaN(out.y) || Float.isNaN(out.z));
        QuadUtil.faceNormal(0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, out);
        assertEquals(1f, out.z, 1e-6f);
        assertEquals(0, QuadUtil.packedFaceNormal(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(ModelQuadFacing.POS_Z, QuadUtil.findNormalFace(0, 0, 1));
        assertEquals(ModelQuadFacing.UNASSIGNED, QuadUtil.findNormalFace(Float.NaN, 0, 1));
        assertEquals(ModelQuadFacing.UNASSIGNED, QuadUtil.findNormalFace(0.7f, 0.7f, 0));
        assertEquals(ModelQuadFacing.UNASSIGNED, QuadUtil.findNormalFace(0, 0, 0));
    }

    // Limits the emission to a byte and merges channels.
    @Test
    void lightMerging() {
        assertEquals(0x00140020, ModelQuadUtil.mergeBakedLight(0x00100020, 0, 0x00140010));
        assertEquals(0x0000FF00 & 0, ModelQuadUtil.mergeBakedLight(0, 0, 0) & 0xFF00);
        assertEquals(255, ModelQuadUtil.mergeBakedLight(1, 1000, 0) & 0xFFFF);
        assertEquals(1, ModelQuadUtil.mergeBakedLight(1, -5, 0) & 0xFFFF);
        assertEquals(0xFF123456, ModelQuadUtil.mixARGBColors(-1, 0xFF123456));
        assertEquals(0xFF123456, ModelQuadUtil.mixARGBColors(0xFF123456, -1));
        assertEquals(8, ModelQuadUtil.vertexOffset(1));
    }

    // Rejects null elements and keeps order across flips.
    @Test
    void doubleBufferedQueue() {
        DoubleBufferedQueue<String> queue = new DoubleBufferedQueue<>();
        assertFalse(queue.flip());
        assertThrows(NullPointerException.class, () -> queue.write().enqueue(null));
        queue.write().ensureCapacity(1000);
        queue.write().ensureCapacity(-5);
        for (int i = 0; i < 1000; i++) queue.write().enqueue("e" + i);
        assertTrue(queue.flip());
        assertEquals("e0", queue.read().dequeue());
        for (int i = 1; i < 1000; i++) assertEquals("e" + i, queue.read().dequeue());
        assertNull(queue.read().dequeue());
        queue.write().enqueue("x");
        queue.reset();
        assertFalse(queue.flip());
    }

    // Caches values until they expire and rejects bad arguments.
    @Test
    void expiringSupplier() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        ExpiringSupplier<Integer> supplier = new ExpiringSupplier<>(calls::incrementAndGet, 50, TimeUnit.MILLISECONDS);
        assertEquals(1, supplier.get());
        assertEquals(1, supplier.get());
        Thread.sleep(80);
        assertEquals(2, supplier.get());
        assertThrows(NullPointerException.class, () -> new ExpiringSupplier<Integer>(null, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> new ExpiringSupplier<>(() -> 1, 1, null));
        ExpiringSupplier<Object> nulls = new ExpiringSupplier<>(() -> null, 1, TimeUnit.SECONDS);
        assertThrows(NullPointerException.class, nulls::get);
        ExpiringSupplier<Integer> always = new ExpiringSupplier<>(calls::incrementAndGet, -1, TimeUnit.SECONDS);
        assertNotEquals(always.get(), always.get());
    }

    // Reads the first line of failures and rejects null.
    @Test
    void failureText() {
        assertEquals("java.lang.IllegalStateException: a", FailureText.of(new IllegalStateException("a\nb")));
        assertThrows(NullPointerException.class, () -> FailureText.firstLine(null));
        assertEquals("", FailureText.firstLine(""));
        assertEquals("a", FailureText.firstLine("a\rb"));
        assertEquals(256, FailureText.firstLine("x".repeat(1000)).length());
        String pairs = "x".repeat(255) + "😀";
        assertEquals(255, FailureText.firstLine(pairs).length());
        Throwable unprintable = new RuntimeException() {
            @Override
            public String toString() {
                throw new IllegalStateException();
            }
        };
        assertTrue(FailureText.of(unprintable).contains("MiscUtilEdgeTest"));
        assertThrows(OutOfMemoryError.class, () -> FailureText.absorb(new OutOfMemoryError()));
        FailureText.absorb(new InterruptedException());
        assertTrue(Thread.interrupted());
    }

    // Skips corrupt class files and rejects null.
    @Test
    void mixinValidator() throws Exception {
        Path dir = Files.createTempDirectory("mixins");
        Path bad = dir.resolve("Bad.class");
        Files.write(bad, new byte[]{1, 2, 3});
        assertFalse(MixinClassValidator.isMixinClass(bad));
        assertFalse(MixinClassValidator.isMixinClass(dir.resolve("Missing.class")));
        assertTrue(MixinClassValidator.scanMixinFolder(dir).isEmpty());
        assertTrue(MixinClassValidator.scanMixinFolder(dir.resolve("none")).isEmpty());
        assertThrows(NullPointerException.class, () -> MixinClassValidator.scanMixinFolder(null));
        assertThrows(NullPointerException.class, () -> MixinClassValidator.fromBytecode(null));
        Files.delete(bad);
        Files.delete(dir);
    }
}
