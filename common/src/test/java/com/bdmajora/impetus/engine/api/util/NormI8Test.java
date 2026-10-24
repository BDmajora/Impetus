package com.bdmajora.impetus.engine.api.util;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.util.QuadUtil;
import com.bdmajora.testing.FreshClasses;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class NormI8Test {
    // every 32-bit pattern, as a long so the range does not overflow
    private static final long ALL_BITS = 0xFFFF_FFFFL;

    // floats outside, on and just inside the -1..1 contract, and on the rounding boundaries
    private static final float[] EDGE_FLOATS = {
            0f, -0f, 1f, -1f, 0.5f, -0.5f, Math.nextDown(0.5f), Math.nextUp(0.5f), Math.nextDown(1f), Math.nextUp(-1f),
            Math.nextUp(1f), Math.nextDown(-1f), Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, 0.0039f, 0.004f, -0.004f,
            0.996f, 0.9962f, 0.57735026f, 0.70710677f, -0.70710677f, 1.0039f, 5f, -5f, 0x1p31f, 1e10f, Float.MAX_VALUE,
            -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.intBitsToFloat(0x7FC00001),
            Float.intBitsToFloat(0xFFFFFFFF), Float.intBitsToFloat(0x7F800001)
    };

    // ints on every sign and byte boundary, with garbage around each byte
    private static final int[] EDGE_INTS = {
            0, 1, -1, 0x7F, 0x80, 0x81, 0xFF, 0x100, 0x7F7F7F7F, 0x80808080, 0x81818181, 0xFFFFFF7F, 0x7FFFFFFF, 0x80000000,
            0x00FFFFFF, 0xFF000000, 0xDEADBEEF, 0x12345678, 0xC040817F
    };

    // one uninstrumented copy of NormI8 and the sweeps: FreshClasses defines them without a code source, so JaCoCo's shared probe writes do not serialise the threads
    private static final FreshClasses UNINSTRUMENTED = new FreshClasses(NormI8.class, Sweeps.class);

    // runs one exhaustive sweep over from..to on the uninstrumented copy and returns its first failing bit pattern, or -1
    private static long sweep(String name, long from, long to) throws ReflectiveOperationException {
        Class<?> copy = UNINSTRUMENTED.load(NormI8.class);
        assertNotSame(NormI8.class, copy);
        assertTrue(Arrays.stream(copy.getDeclaredMethods()).noneMatch(Method::isSynthetic), "the copy was instrumented");
        return (long) UNINSTRUMENTED.load(Sweeps.class).getMethod(name, long.class, long.class).invoke(null, from, to);
    }

    private static int bits(float f) {
        return Float.floatToRawIntBits(f);
    }

    // the exhaustive checks; public so the fresh copy is reachable reflectively, and nothing here touches the outer class
    public static final class Sweeps {
        // reference encoder: |f| * 127 is exact in double, so round half away from zero then clamp; in -1..1 the only ties are +-0.5, where that agrees with ties-to-even
        public static int modelByte(float value) {
            if (value != value) {
                return 0;
            }
            int magnitude = (int) Math.min(Math.floor(Math.abs((double) value) * 127.0 + 0.5), 127.0);
            return (value < 0 ? -magnitude : magnitude) & 0xFF;
        }

        // reference decoder: the signed byte over 127, correctly rounded by IEEE division, with -128 read as -1
        public static float modelFloat(int component) {
            byte b = (byte) component;
            return b == Byte.MIN_VALUE ? -1f : b / 127f;
        }

        // first bit pattern in from..to that fails, scanning 65536-pattern blocks in parallel, or -1
        private static long first(long from, long to, LongPredicate fails) {
            return LongStream.rangeClosed(from >>> 16, to >>> 16).parallel()
                    .map(block -> {
                        long end = Math.min(to, block << 16 | 0xFFFF);
                        for (long bits = Math.max(from, block << 16); bits <= end; bits++) {
                            if (fails.test(bits)) {
                                return bits;
                            }
                        }
                        return -1;
                    })
                    .filter(bits -> bits >= 0)
                    .findFirst()
                    .orElse(-1);
        }

        // the float in every slot of both pack forms
        public static long packMismatch(long from, long to) {
            return first(from, to, bits -> {
                float f = Float.intBitsToFloat((int) bits);
                int b = modelByte(f);
                return NormI8.pack(f, f, f) != b * 0x010101 || NormI8.pack(f, f, f, f) != b * 0x01010101;
            });
        }

        // a float and its negation pack to negated signed bytes
        public static long asymmetry(long from, long to) {
            return first(from, to, bits -> {
                float f = Float.intBitsToFloat((int) bits);
                return (byte) NormI8.pack(-f, 0, 0) != -(byte) NormI8.pack(f, 0, 0);
            });
        }

        // non-negative float bit patterns sort like their values, so no step may lower the byte
        public static long byteDrop(long from, long to) {
            return first(from, to, bits -> (byte) NormI8.pack(Float.intBitsToFloat((int) bits), 0, 0)
                    < (byte) NormI8.pack(Float.intBitsToFloat((int) bits - 1), 0, 0));
        }

        // pack -> unpack -> pack changes nothing
        public static long quantiseDrift(long from, long to) {
            return first(from, to, bits -> {
                float f = Float.intBitsToFloat((int) bits);
                int once = NormI8.pack(f, -f, f, -f);
                return NormI8.pack(NormI8.unpackX(once), NormI8.unpackY(once), NormI8.unpackZ(once), NormI8.unpackW(once)) != once;
            });
        }

        // each unpack reads only its byte, like the model
        public static long unpackMismatch(long from, long to) {
            return first(from, to, bits -> {
                int n = (int) bits;
                return Float.floatToRawIntBits(NormI8.unpackX(n)) != Float.floatToRawIntBits(modelFloat(n))
                        || Float.floatToRawIntBits(NormI8.unpackY(n)) != Float.floatToRawIntBits(modelFloat(n >> 8))
                        || Float.floatToRawIntBits(NormI8.unpackZ(n)) != Float.floatToRawIntBits(modelFloat(n >> 16))
                        || Float.floatToRawIntBits(NormI8.unpackW(n)) != Float.floatToRawIntBits(modelFloat(n >> 24));
            });
        }
    }

    @Test
    void holderIsFinalWithAPrivateConstructorAndOnlyItsSevenEntryPoints() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(NormI8.class.getModifiers()));
        Constructor<?>[] constructors = NormI8.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
        // JaCoCo adds synthetic members to the loaded class; only the declared ones count
        List<Method> methods = Arrays.stream(NormI8.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList();
        assertEquals(List.of("pack", "pack", "pack", "unpackW", "unpackX", "unpackY", "unpackZ"),
                methods.stream().filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).sorted().toList());
        assertEquals(List.of("decode", "encode"),
                methods.stream().filter(m -> Modifier.isPrivate(m.getModifiers())).map(Method::getName).sorted().toList());
        for (Method method : methods) {
            assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
        }
        for (Field field : NormI8.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()) && field.getType().isPrimitive(), field.getName());
            }
        }
    }

    @Test
    void everyConstantIsCompileTimeSoTheClassHasNoInitialiser() throws IOException {
        ClassModel model;
        try (InputStream in = Objects.requireNonNull(NormI8.class.getResourceAsStream("NormI8.class"))) {
            model = ClassFile.of().parse(in.readAllBytes());
        }
        // a <clinit> would mean a constant became a runtime field read on the hot path
        assertTrue(model.methods().stream().map(MethodModel::methodName).noneMatch(name -> name.equalsString("<clinit>")));
    }

    @Test
    void layoutPutsXLowThenYThenZAndTheHandednessOnTop() {
        assertEquals(0x0000007F, NormI8.pack(1, 0, 0));
        assertEquals(0x00007F00, NormI8.pack(0, 1, 0));
        assertEquals(0x007F0000, NormI8.pack(0, 0, 1));
        assertEquals(0x00000081, NormI8.pack(-1, 0, 0));
        assertEquals(0x00008100, NormI8.pack(0, -1, 0));
        assertEquals(0x00810000, NormI8.pack(0, 0, -1));
        assertEquals(0x7F000000, NormI8.pack(0, 0, 0, 1));
        assertEquals(0x81000000, NormI8.pack(0, 0, 0, -1));
        assertEquals(0, NormI8.pack(0, 0, 0));
        assertEquals(0, NormI8.pack(0, 0, 0, 0));
        assertEquals(0xC040817F, NormI8.pack(1, -1, 0.5f, -0.5f));
        // the three-component form leaves the top byte zero even when every component is negative
        assertEquals(0x00818181, NormI8.pack(-1, -1, -1));
        for (float f : EDGE_FLOATS) {
            assertEquals(0, NormI8.pack(f, f, f) >>> 24, Float.toString(f));
        }
    }

    @Test
    void unpackReadsItsOwnByteAndShiftingDownReadsTheNextAsX() {
        int packed = NormI8.pack(1, -1, 0.5f, -0.5f);
        assertEquals(1f, NormI8.unpackX(packed));
        assertEquals(-1f, NormI8.unpackY(packed));
        assertEquals(64 / 127f, NormI8.unpackZ(packed));
        assertEquals(-64 / 127f, NormI8.unpackW(packed));
        // SmoothLightPipeline walks the axes with unpackX(normal >> (axis * 8))
        for (int n : EDGE_INTS) {
            assertEquals(bits(NormI8.unpackY(n)), bits(NormI8.unpackX(n >> 8)), Integer.toHexString(n));
            assertEquals(bits(NormI8.unpackZ(n)), bits(NormI8.unpackX(n >> 16)), Integer.toHexString(n));
            assertEquals(bits(NormI8.unpackW(n)), bits(NormI8.unpackX(n >> 24)), Integer.toHexString(n));
            assertEquals(bits(NormI8.unpackW(n)), bits(NormI8.unpackX(n >>> 24)), Integer.toHexString(n));
        }
    }

    @Test
    void unpackIgnoresTheOtherBytes() {
        assertEquals(1f, NormI8.unpackX(0xFFFFFF7F));
        assertEquals(1f, NormI8.unpackY(0xFFFF7FFF));
        assertEquals(1f, NormI8.unpackZ(0xFF7FFFFF));
        assertEquals(1f, NormI8.unpackW(0x7FFFFFFF));
        assertEquals(0, bits(NormI8.unpackX(0xFFFFFF00)));
        assertEquals(0, bits(NormI8.unpackW(0x00FFFFFF)));
        for (int n : EDGE_INTS) {
            assertEquals(bits(Sweeps.modelFloat(n)), bits(NormI8.unpackX(n)), Integer.toHexString(n));
            assertEquals(bits(Sweeps.modelFloat(n >> 8)), bits(NormI8.unpackY(n)), Integer.toHexString(n));
            assertEquals(bits(Sweeps.modelFloat(n >> 16)), bits(NormI8.unpackZ(n)), Integer.toHexString(n));
            assertEquals(bits(Sweeps.modelFloat(n >> 24)), bits(NormI8.unpackW(n)), Integer.toHexString(n));
        }
    }

    @Test
    void axisNormalsRoundTripToExactUnitsAndPositiveZero() {
        float[] steps = {-1, 0, 1};
        for (float x : steps) {
            for (float y : steps) {
                for (float z : steps) {
                    int packed = NormI8.pack(x, y, z);
                    assertEquals(bits(x == 0 ? 0f : x), bits(NormI8.unpackX(packed)));
                    assertEquals(bits(y == 0 ? 0f : y), bits(NormI8.unpackY(packed)));
                    assertEquals(bits(z == 0 ? 0f : z), bits(NormI8.unpackZ(packed)));
                    assertEquals(0, bits(NormI8.unpackW(packed)));
                }
            }
        }
        for (ModelQuadFacing face : ModelQuadFacing.DIRECTIONS) {
            assertEquals(face, QuadUtil.findNormalFace(NormI8.pack(face.getStepX(), face.getStepY(), face.getStepZ())));
        }
    }

    @Test
    void componentsRoundToTheNearestStepInsteadOfTruncating() {
        // truncation dropped a just-under-one component to 126, so a float-noisy axis quad lost its facing
        assertEquals(0x7F, NormI8.pack(Math.nextDown(1f), 0, 0));
        assertEquals(0x81, NormI8.pack(Math.nextUp(-1f), 0, 0));
        assertEquals(1f, NormI8.unpackY(NormI8.pack(0, Math.nextDown(1f), 1e-4f)));
        assertEquals(ModelQuadFacing.POS_Y, QuadUtil.findNormalFace(NormI8.pack(1e-4f, Math.nextDown(1f), -1e-4f)));
        // the half-step boundaries either side of 0 and 1
        assertEquals(0, NormI8.pack(0.0039f, 0, 0));
        assertEquals(1, NormI8.pack(0.004f, 0, 0));
        assertEquals(0xFF, NormI8.pack(-0.004f, 0, 0));
        assertEquals(0, NormI8.pack(-0.0039f, 0, 0));
        assertEquals(126, NormI8.pack(0.996f, 0, 0));
        assertEquals(127, NormI8.pack(0.9962f, 0, 0));
        // +-0.5 are the only ties in range, 63.5 going to 64 either way
        assertEquals(64, NormI8.pack(0.5f, 0, 0));
        assertEquals(0xC0, NormI8.pack(-0.5f, 0, 0));
        assertEquals(63, NormI8.pack(Math.nextDown(0.5f), 0, 0));
        assertEquals(64, NormI8.pack(Math.nextUp(0.5f), 0, 0));
        assertEquals(0xC1, NormI8.pack(-Math.nextDown(0.5f), 0, 0));
        // unit diagonals: 1/sqrt(2) * 127 is 89.8, which truncation took to 89
        assertEquals(90, NormI8.pack(0.70710677f, 0, 0));
        assertEquals(73 * 0x010101, NormI8.pack(0.57735026f, 0.57735026f, 0.57735026f));
        for (float f : EDGE_FLOATS) {
            assertEquals(Sweeps.modelByte(f), NormI8.pack(f, 0, 0), Float.toString(f));
        }
    }

    @Test
    void outOfRangeAndNonFiniteInputsClampAndNeverWrite0x80() {
        assertEquals(0x7F, NormI8.pack(5f, 0, 0));
        assertEquals(0x81, NormI8.pack(-5f, 0, 0));
        assertEquals(0x7F, NormI8.pack(1.0039f, 0, 0));
        assertEquals(0x7F, NormI8.pack(Math.nextUp(1f), 0, 0));
        assertEquals(0x81, NormI8.pack(Math.nextDown(-1f), 0, 0));
        // past 2^31 / 127 the product no longer fits an int and the cast saturates before the clamp
        assertEquals(0x7F, NormI8.pack(0x1p31f, 0, 0));
        assertEquals(0x7F, NormI8.pack(1e10f, 0, 0));
        assertEquals(0x81, NormI8.pack(-1e10f, 0, 0));
        assertEquals(0x7F, NormI8.pack(Float.MAX_VALUE, 0, 0));
        assertEquals(0x81, NormI8.pack(-Float.MAX_VALUE, 0, 0));
        assertEquals(0x7F, NormI8.pack(Float.POSITIVE_INFINITY, 0, 0));
        assertEquals(0x81, NormI8.pack(Float.NEGATIVE_INFINITY, 0, 0));
        assertEquals(0x7F00817F, NormI8.pack(5f, -5f, Float.NaN, Float.POSITIVE_INFINITY));
        assertEquals(0x81000000, NormI8.pack(0, 0, 0, Float.NEGATIVE_INFINITY));
        for (float f : EDGE_FLOATS) {
            int packed = NormI8.pack(f, f, f, f);
            for (int shift = 0; shift < 32; shift += 8) {
                assertNotEquals(0x80, (packed >>> shift) & 0xFF, f + " at " + shift);
            }
        }
    }

    @Test
    void zerosDenormalsAndEveryNaNPackToZero() throws ReflectiveOperationException {
        assertEquals(0, NormI8.pack(-0f, -0f, -0f, -0f));
        assertEquals(0, NormI8.pack(Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, -Float.MIN_NORMAL));
        assertEquals(0, NormI8.pack(Float.NaN, Float.NaN, Float.NaN, Float.NaN));
        assertEquals(0, NormI8.pack(Float.intBitsToFloat(0x7F800001), Float.intBitsToFloat(0xFFFFFFFF),
                Float.intBitsToFloat(0x7FC00001), Float.intBitsToFloat(0xFF800001)));
        // every NaN payload and sign, quiet or signalling
        assertEquals(-1, sweep("packMismatch", 0x7F800001L, 0x7FFFFFFFL));
        assertEquals(-1, sweep("packMismatch", 0xFF800001L, 0xFFFFFFFFL));
    }

    @Test
    void everyByteUnpacksToItsCorrectlyRoundedQuotient() {
        // multiplying by the float inverse of 127 landed one ulp off for 16 of the bytes
        for (int c = -127; c <= 127; c++) {
            assertEquals(bits(c / 127f), bits(NormI8.unpackX(c & 0xFF)), "byte " + c);
            assertEquals(bits(c / 127f), bits(NormI8.unpackY((c & 0xFF) << 8)), "byte " + c);
            assertEquals(bits(c / 127f), bits(NormI8.unpackZ((c & 0xFF) << 16)), "byte " + c);
            assertEquals(bits(c / 127f), bits(NormI8.unpackW(c << 24)), "byte " + c);
        }
        assertEquals(0, bits(NormI8.unpackX(0)));
        assertEquals(1f, NormI8.unpackX(0x7F));
        assertEquals(-1f, NormI8.unpackX(0x81));
    }

    @Test
    void minus128ReadsAsMinusOneAndRepacksAsMinus127() {
        // -128 / 127 was -1.0079, outside the -1..1 every caller assumes; GL clamps it to -1
        assertEquals(-1f, NormI8.unpackX(0x80));
        assertEquals(-1f, NormI8.unpackY(0x8000));
        assertEquals(-1f, NormI8.unpackZ(0x800000));
        assertEquals(-1f, NormI8.unpackW(0x80000000));
        int all = 0x80808080;
        assertEquals(0x81818181, NormI8.pack(NormI8.unpackX(all), NormI8.unpackY(all), NormI8.unpackZ(all), NormI8.unpackW(all)));
    }

    @Test
    void unpackedValuesRiseStrictlyAndStayInsideTheUnitRange() {
        float previous = Float.NEGATIVE_INFINITY;
        for (int c = -127; c <= 127; c++) {
            float f = NormI8.unpackX(c);
            assertTrue(f > previous && f >= -1f && f <= 1f, "byte " + c);
            previous = f;
        }
        for (int b = 0; b <= 0xFF; b++) {
            float f = NormI8.unpackX(b);
            assertTrue(f >= -1f && f <= 1f, "byte " + b);
        }
    }

    @Test
    void everyByteExceptMinus128RoundTripsInEverySlot() {
        for (int b = 0; b <= 0xFF; b++) {
            int n = b * 0x01010101;
            int expected = b == 0x80 ? 0x81818181 : n;
            assertEquals(expected, NormI8.pack(NormI8.unpackX(n), NormI8.unpackY(n), NormI8.unpackZ(n), NormI8.unpackW(n)), "byte " + b);
            assertEquals(expected & 0xFFFFFF, NormI8.pack(NormI8.unpackX(n), NormI8.unpackY(n), NormI8.unpackZ(n)), "byte " + b);
        }
    }

    @Test
    void unitVectorsDecodeWithinHalfAStepPerComponent() {
        float halfStep = 0.5f / 127f + 1e-7f;
        for (int pitch = -90; pitch <= 90; pitch += 3) {
            for (int yaw = 0; yaw < 360; yaw += 7) {
                double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
                Vector3f v = new Vector3f((float) (Math.cos(p) * Math.cos(y)), (float) Math.sin(p), (float) (Math.cos(p) * Math.sin(y))).normalize();
                int packed = NormI8.pack(v);
                assertEquals(v.x, NormI8.unpackX(packed), halfStep, pitch + "/" + yaw);
                assertEquals(v.y, NormI8.unpackY(packed), halfStep, pitch + "/" + yaw);
                assertEquals(v.z, NormI8.unpackZ(packed), halfStep, pitch + "/" + yaw);
            }
        }
    }

    @Test
    void vectorFormMatchesTheComponentFormAndLeavesTheVectorAlone() {
        for (float f : EDGE_FLOATS) {
            Vector3f v = new Vector3f(f, -f, f * 0.5f);
            assertEquals(NormI8.pack(f, -f, f * 0.5f), NormI8.pack(v), Float.toString(f));
            assertEquals(bits(f), bits(v.x));
            assertEquals(bits(-f), bits(v.y));
        }
        assertEquals(0xC0817F, NormI8.pack(new Vector3f(1, -1, -0.5f)));
        assertThrows(NullPointerException.class, () -> NormI8.pack(null));
    }

    @Test
    void tangentFormAddsOnlyTheTopByte() {
        for (float x : EDGE_FLOATS) {
            for (float w : EDGE_FLOATS) {
                int packed = NormI8.pack(x, -x, x, w);
                assertEquals(NormI8.pack(x, -x, x), packed & 0xFFFFFF, x + "/" + w);
                assertEquals(Sweeps.modelByte(w), packed >>> 24, x + "/" + w);
            }
        }
        assertEquals(-1f, NormI8.unpackW(NormI8.pack(0, 0, 1, -1)));
        assertEquals(1f, NormI8.unpackW(NormI8.pack(0, 0, 1, 1)));
        assertEquals(1f, NormI8.unpackZ(NormI8.pack(0, 0, 1, -1)));
    }

    @Test
    void everyFloatPacksToItsNearestStepInEverySlot() throws ReflectiveOperationException {
        long mismatch = sweep("packMismatch", 0, ALL_BITS);
        float f = Float.intBitsToFloat((int) mismatch);
        assertEquals(-1, mismatch, () -> f + " gave " + Integer.toHexString(NormI8.pack(f, f, f, f)) + ", model " + Sweeps.modelByte(f));
    }

    @Test
    void negatingAFloatNegatesItsByte() throws ReflectiveOperationException {
        // the non-negative patterns pair with every negative one
        long broken = sweep("asymmetry", 0, 0x7FFF_FFFFL);
        assertEquals(-1, broken, () -> "asymmetric at " + Float.intBitsToFloat((int) broken));
    }

    @Test
    void packingNeverDecreasesAlongTheFloats() throws ReflectiveOperationException {
        long drop = sweep("byteDrop", 1, bits(Float.POSITIVE_INFINITY));
        assertEquals(-1, drop, () -> "dropped at " + Float.intBitsToFloat((int) drop));
        boolean[] hit = new boolean[256];
        for (int c = -127; c <= 127; c++) {
            hit[NormI8.pack(c / 127f, 0, 0)] = true;
        }
        for (int b = 0; b <= 0xFF; b++) {
            assertEquals(b != 0x80, hit[b], "byte " + b);
        }
    }

    @Test
    void quantisingAFloatTwiceChangesNothing() throws ReflectiveOperationException {
        long drift = sweep("quantiseDrift", 0, ALL_BITS);
        assertEquals(-1, drift, () -> "drifted at " + Float.intBitsToFloat((int) drift));
    }

    @Test
    void everyIntUnpacksEachByteLikeTheModel() throws ReflectiveOperationException {
        long mismatch = sweep("unpackMismatch", 0, ALL_BITS);
        assertEquals(-1, mismatch, () -> "int " + Integer.toHexString((int) mismatch));
    }
}
