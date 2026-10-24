package com.bdmajora.impetus.engine.api.util;

import com.bdmajora.testing.FreshClasses;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ColorU8Test {
    // every 32-bit pattern, as a long so the range does not overflow
    private static final long ALL_BITS = 0xFFFF_FFFFL;

    // floats outside, on and just inside the 0..1 contract
    private static final float[] EDGE_FLOATS = {
            0f, -0f, 1f, 0.5f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, -Float.MIN_NORMAL, Math.nextDown(1f),
            Math.nextUp(1f), 256 / 255f, 1.0039f, 1.5f, 2f, -0.1f, -1f, Float.MAX_VALUE, -Float.MAX_VALUE,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.intBitsToFloat(0x7FC00001), Float.intBitsToFloat(0xFFFFFFFF)
    };

    // ints on every sign and byte boundary
    private static final int[] EDGE_INTS = {
            0, 1, -1, 0x7F, 0x80, 0xFF, 0x100, 0x101, -0x80, -0x81, -0xFF, -0x100, 0xFFFF, 0x10000, 0x7FFFFFFF, 0x80000000,
            0x800000FF, 0x7FFFFF00, 0xDEADBEEF, 0x12345678
    };

    // one uninstrumented copy of ColorU8 and the sweeps: FreshClasses defines them without a code source, so JaCoCo's shared probe writes do not serialise the threads
    private static final FreshClasses UNINSTRUMENTED = new FreshClasses(ColorU8.class, Sweeps.class);

    // runs one exhaustive sweep over from..to on the uninstrumented copy and returns its first failing bit pattern, or -1
    private static long sweep(String name, long from, long to) throws ReflectiveOperationException {
        Class<?> copy = UNINSTRUMENTED.load(ColorU8.class);
        assertNotSame(ColorU8.class, copy);
        assertTrue(Arrays.stream(copy.getDeclaredMethods()).noneMatch(Method::isSynthetic), "the copy was instrumented");
        return (long) UNINSTRUMENTED.load(Sweeps.class).getMethod(name, long.class, long.class).invoke(null, from, to);
    }

    // the exhaustive checks; public so the fresh copy is reachable reflectively, and nothing here touches the outer class
    public static final class Sweeps {
        // reference model, worked in double: f * 255 is exact in double, so one cast is the float multiply; then truncate and clamp, NaN 0
        public static int modelByte(float value) {
            if (!(value > 0f)) {
                return 0;
            }
            double product = (float) (value * 255d);
            return product >= 255d ? 255 : (int) Math.floor(product);
        }

        // reference model: the low byte divided by 255, correctly rounded by IEEE division
        public static float modelFloat(int value) {
            return (value & 0xFF) / 255f;
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

        public static long byteMismatch(long from, long to) {
            return first(from, to, bits -> {
                float f = Float.intBitsToFloat((int) bits);
                return ColorU8.normalizedFloatToByte(f) != modelByte(f);
            });
        }

        // non-negative float bit patterns sort like their values, so no step may lower the byte
        public static long byteDrop(long from, long to) {
            return first(from, to, bits -> ColorU8.normalizedFloatToByte(Float.intBitsToFloat((int) bits))
                    < ColorU8.normalizedFloatToByte(Float.intBitsToFloat((int) bits - 1)));
        }

        public static long floatMismatch(long from, long to) {
            return first(from, to, bits -> Float.floatToRawIntBits(ColorU8.byteToNormalizedFloat((int) bits))
                    != Float.floatToRawIntBits(modelFloat((int) bits)));
        }

        public static long quantiseDrift(long from, long to) {
            return first(from, to, bits -> {
                int once = ColorU8.normalizedFloatToByte(Float.intBitsToFloat((int) bits));
                return ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(once)) != once;
            });
        }
    }

    @Test
    void constantsDescribeAnEightBitComponent() {
        assertEquals(8, ColorU8.COMPONENT_BITS);
        assertEquals(0xFF, ColorU8.COMPONENT_MASK);
        assertEquals(255f, ColorU8.COMPONENT_RANGE);
        assertEquals(Float.floatToRawIntBits(1f / 255f), Float.floatToRawIntBits(ColorU8.COMPONENT_RANGE_INVERSE));
        assertEquals(ColorU8.COMPONENT_MASK, (1 << ColorU8.COMPONENT_BITS) - 1);
        assertEquals(ColorU8.COMPONENT_MASK, (int) ColorU8.COMPONENT_RANGE);
    }

    @Test
    void interfaceHoldsOnlyItsFourConstantsAndTwoStaticConversions() {
        assertTrue(ColorU8.class.isInterface());
        assertTrue(Modifier.isPublic(ColorU8.class.getModifiers()));
        // JaCoCo adds synthetic members to the loaded class; only the declared ones count
        List<Field> fields = Arrays.stream(ColorU8.class.getDeclaredFields()).filter(f -> !f.isSynthetic()).toList();
        assertEquals(List.of("COMPONENT_BITS", "COMPONENT_MASK", "COMPONENT_RANGE", "COMPONENT_RANGE_INVERSE"),
                fields.stream().map(Field::getName).sorted().toList());
        for (Field field : fields) {
            assertTrue(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field.getName());
            assertTrue(field.getType().isPrimitive(), field.getName());
        }
        List<Method> methods = Arrays.stream(ColorU8.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList();
        assertEquals(List.of("byteToNormalizedFloat", "normalizedFloatToByte"), methods.stream().map(Method::getName).sorted().toList());
        for (Method method : methods) {
            assertTrue(Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()), method.getName());
        }
    }

    @Test
    void everyConstantIsCompileTimeSoTheInterfaceHasNoInitialiser() throws IOException {
        ClassModel model;
        try (InputStream in = Objects.requireNonNull(ColorU8.class.getResourceAsStream("ColorU8.class"))) {
            model = ClassFile.of().parse(in.readAllBytes());
        }
        // a <clinit> would mean a constant became a runtime field read at every use
        assertTrue(model.methods().stream().map(MethodModel::methodName).noneMatch(name -> name.equalsString("<clinit>")));
    }

    @Test
    void floatEndpointsAndNamedEdgesConvert() {
        assertEquals(0, ColorU8.normalizedFloatToByte(0f));
        assertEquals(0, ColorU8.normalizedFloatToByte(-0f));
        assertEquals(255, ColorU8.normalizedFloatToByte(1f));
        assertEquals(127, ColorU8.normalizedFloatToByte(0.5f));
        // only 1.0 and above reach 255, as vanilla's (int) (f * 255); pinned so a switch to rounding is deliberate
        assertEquals(254, ColorU8.normalizedFloatToByte(Math.nextDown(1f)));
        assertEquals(255, ColorU8.normalizedFloatToByte(Math.nextUp(1f)));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.MIN_VALUE));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.MIN_NORMAL));
        assertEquals(0, ColorU8.normalizedFloatToByte(1 / 256f));
        assertEquals(0, ColorU8.normalizedFloatToByte(Math.nextDown(1 / 255f)));
        assertEquals(1, ColorU8.normalizedFloatToByte(1 / 255f));
        for (float f : EDGE_FLOATS) {
            assertEquals(Sweeps.modelByte(f), ColorU8.normalizedFloatToByte(f), Float.toString(f));
        }
    }

    @Test
    void outOfRangeFloatsClampInsteadOfWrapping() {
        // just past 1.0 wrapped to 0 (black / fully occluded), 1.5 to 126, -0.1 to 231
        assertEquals(255, ColorU8.normalizedFloatToByte(256 / 255f));
        assertEquals(255, ColorU8.normalizedFloatToByte(1.5f));
        assertEquals(255, ColorU8.normalizedFloatToByte(2f));
        assertEquals(255, ColorU8.normalizedFloatToByte(Float.MAX_VALUE));
        assertEquals(0, ColorU8.normalizedFloatToByte(-0.1f));
        assertEquals(0, ColorU8.normalizedFloatToByte(-1f));
        assertEquals(0, ColorU8.normalizedFloatToByte(-Float.MAX_VALUE));
        assertEquals(0, ColorU8.normalizedFloatToByte(-Float.MIN_VALUE));
        // past 2^31 / 255 the product no longer fits an int and the cast saturates
        assertEquals(255, ColorU8.normalizedFloatToByte(0x1p31f / 255f));
        assertEquals(255, ColorU8.normalizedFloatToByte(0x1p40f));
        assertEquals(0, ColorU8.normalizedFloatToByte(-0x1p40f));
    }

    @Test
    void nonFiniteFloatsSaturateAndEveryNaNIsZero() throws ReflectiveOperationException {
        assertEquals(255, ColorU8.normalizedFloatToByte(Float.POSITIVE_INFINITY));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.NEGATIVE_INFINITY));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.NaN));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.intBitsToFloat(0x7F800001)));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.intBitsToFloat(0xFFFFFFFF)));
        // every NaN payload and sign, quiet or signalling
        assertEquals(-1, sweep("byteMismatch", 0x7F800001L, 0x7FFFFFFFL));
        assertEquals(-1, sweep("byteMismatch", 0xFF800001L, 0xFFFFFFFFL));
    }

    @Test
    void everyFloatBitPatternMatchesTheTruncatingModel() throws ReflectiveOperationException {
        long mismatch = sweep("byteMismatch", 0, ALL_BITS);
        float f = Float.intBitsToFloat((int) mismatch);
        assertEquals(-1, mismatch, () -> f + " gave " + ColorU8.normalizedFloatToByte(f) + ", model " + Sweeps.modelByte(f));
    }

    @Test
    void floatConversionNeverDecreasesAndHitsEveryByte() throws ReflectiveOperationException {
        long drop = sweep("byteDrop", 1, Float.floatToRawIntBits(1f));
        assertEquals(-1, drop, () -> "dropped at " + Float.intBitsToFloat((int) drop));
        boolean[] hit = new boolean[256];
        for (int v = 0; v <= 255; v++) {
            hit[ColorU8.normalizedFloatToByte(v / 255f)] = true;
        }
        for (int v = 0; v <= 255; v++) {
            assertTrue(hit[v], "no float reaches " + v);
        }
    }

    @Test
    void everyByteConvertsToItsCorrectlyRoundedQuotient() {
        // multiplying by the float inverse of 255 landed one ulp high for 126 of the 256 bytes
        for (int v = 0; v <= 255; v++) {
            assertEquals(Float.floatToRawIntBits(v / 255f), Float.floatToRawIntBits(ColorU8.byteToNormalizedFloat(v)), "byte " + v);
        }
        assertEquals(0, Float.floatToRawIntBits(ColorU8.byteToNormalizedFloat(0)));
        assertEquals(1f, ColorU8.byteToNormalizedFloat(255));
        assertEquals(0.5019608f, ColorU8.byteToNormalizedFloat(128));
        assertEquals(0.003921569f, ColorU8.byteToNormalizedFloat(1));
        assertEquals(0.99607843f, ColorU8.byteToNormalizedFloat(254));
    }

    @Test
    void byteConversionRisesStrictlyFromZeroToOne() {
        float previous = -1f;
        for (int v = 0; v <= 255; v++) {
            float f = ColorU8.byteToNormalizedFloat(v);
            assertTrue(f > previous && f >= 0f && f <= 1f, "byte " + v);
            previous = f;
        }
    }

    @Test
    void outOfRangeIntsKeepOnlyTheirLowByte() {
        // 256 gave 1.0039 and -1 a negative float; ints keep their low byte like ColorABGR.pack
        assertEquals(0f, ColorU8.byteToNormalizedFloat(256));
        assertEquals(1f, ColorU8.byteToNormalizedFloat(-1));
        assertEquals(1f, ColorU8.byteToNormalizedFloat(0x1FF));
        assertEquals(0f, ColorU8.byteToNormalizedFloat(Integer.MIN_VALUE));
        assertEquals(1f, ColorU8.byteToNormalizedFloat(Integer.MAX_VALUE));
        assertEquals(128 / 255f, ColorU8.byteToNormalizedFloat(-128));
        for (int v : EDGE_INTS) {
            assertEquals(Float.floatToRawIntBits(Sweeps.modelFloat(v)), Float.floatToRawIntBits(ColorU8.byteToNormalizedFloat(v)), Integer.toHexString(v));
        }
    }

    @Test
    void signedJavaBytesReadAsUnsignedComponents() {
        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
            byte raw = (byte) b;
            assertEquals(Float.floatToRawIntBits((raw & 0xFF) / 255f), Float.floatToRawIntBits(ColorU8.byteToNormalizedFloat(raw)), "byte " + b);
        }
        assertEquals(1f, ColorU8.byteToNormalizedFloat((byte) 0xFF));
    }

    @Test
    void everyIntConvertsLikeItsLowByte() throws ReflectiveOperationException {
        long mismatch = sweep("floatMismatch", 0, ALL_BITS);
        int v = (int) mismatch;
        assertEquals(-1, mismatch, () -> "int " + v + " gave " + ColorU8.byteToNormalizedFloat(v) + ", expected " + Sweeps.modelFloat(v));
    }

    @Test
    void everyByteRoundTripsThroughItsFloat() {
        for (int v = 0; v <= 255; v++) {
            assertEquals(v, ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(v)), "byte " + v);
        }
        for (int v : EDGE_INTS) {
            assertEquals(v & 0xFF, ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(v)), Integer.toHexString(v));
        }
    }

    @Test
    void quantisingAFloatTwiceChangesNothing() throws ReflectiveOperationException {
        // float -> byte -> float lands on a value that converts back to the same byte, for every float
        long drift = sweep("quantiseDrift", 0, ALL_BITS);
        assertEquals(-1, drift, () -> "drifted at " + Float.intBitsToFloat((int) drift));
    }
}
