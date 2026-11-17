package com.bdmajora.testing;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

// Answers for shadow methods whose placeholder bodies MixinSubclassing redirects here.
public final class ShadowStubs {
    private static final Map<Object, Map<String, Function<Object[], Object>>> ANSWERS = new IdentityHashMap<>();

    private ShadowStubs() {}

    // Registers the answer of one instance's shadow method.
    public static synchronized void on(Object instance, String method, Function<Object[], Object> answer) {
        ANSWERS.computeIfAbsent(instance, key -> new HashMap<>()).put(method, answer);
    }

    public static synchronized void clear() {
        ANSWERS.clear();
    }

    // Returns the registered answer or the type's default.
    public static synchronized Object call(Object instance, String method, Object[] args) {
        Map<String, Function<Object[], Object>> answers = ANSWERS.get(instance);
        Function<Object[], Object> answer = answers == null ? null : answers.get(method);
        return answer == null ? null : answer.apply(args);
    }

    public static boolean asBoolean(Object value) {
        return value != null && (Boolean) value;
    }

    public static byte asByte(Object value) {
        return value == null ? 0 : ((Number) value).byteValue();
    }

    public static short asShort(Object value) {
        return value == null ? 0 : ((Number) value).shortValue();
    }

    public static char asChar(Object value) {
        return value == null ? 0 : (Character) value;
    }

    public static int asInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    public static long asLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static float asFloat(Object value) {
        return value == null ? 0f : ((Number) value).floatValue();
    }

    public static double asDouble(Object value) {
        return value == null ? 0d : ((Number) value).doubleValue();
    }
}
