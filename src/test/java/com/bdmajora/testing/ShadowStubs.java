package com.bdmajora.testing;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

// Answers for the @Shadow methods that carry a placeholder body, whose real implementation lives in the target class
// and so cannot run here; MixinSubclassing rewrites those bodies to ask this table instead of throwing
public final class ShadowStubs {
    private static final Map<Object, Map<String, Function<Object[], Object>>> ANSWERS = new IdentityHashMap<>();

    private ShadowStubs() {}

    // Registers what one instance's shadow method should answer; the arguments are handed to the function in order
    public static synchronized void on(Object instance, String method, Function<Object[], Object> answer) {
        ANSWERS.computeIfAbsent(instance, key -> new HashMap<>()).put(method, answer);
    }

    public static synchronized void clear() {
        ANSWERS.clear();
    }

    // Called from the rewritten shadow bodies; an unregistered method answers the type's default
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
