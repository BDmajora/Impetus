package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Work group directives of a compute shader.
public final class ComputeDirectives {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Work groups vector.
    private static final Pattern WORK_GROUPS_VECTOR = Pattern.compile(
            "const\\s+[iu]?vec([234])\\s+workGroups\\s*=\\s*[iu]?vec\\d\\s*\\(([^)]*)\\)");
    // Work groups named.
    private static final Pattern WORK_GROUPS_NAMED = Pattern.compile("const\\s+int\\s+workGroups([XYZ])\\s*=\\s*(\\d+)\\s*;");
    // Work groups render.
    private static final Pattern WORK_GROUPS_RENDER = Pattern.compile(
            "const\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(([^)]*)\\)");
    // Local size.
    private static final Pattern LOCAL_SIZE = Pattern.compile("local_size_([xyz])\\s*=\\s*(\\d+)");
    // Prefix of indirect dispatch keys.
    private static final String INDIRECT_PREFIX = "indirect.";

    // Data of the indirect pointer.
    public record IndirectPointer(int buffer, long offset) {
    }

    // Work groups.
    private final int[] workGroups;
    // Render scale.
    private final float[] renderScale;
    // Local size.
    private final int[] localSize;

    // Creates a compute directives from its work groups and render scale and local size.
    private ComputeDirectives(int[] workGroups, float[] renderScale, int[] localSize) {
        this.workGroups = workGroups;
        this.renderScale = renderScale;
        this.localSize = localSize;
    }

    // Parses the directives of a compute source.
    public static ComputeDirectives parse(String source, Map<String, String> defines) {
        String active = stripComments(GlslPreprocessor.resolveConditionals(source, defines));
        int[] groups = workGroups(active);
        float[] scale = renderScale(active);
        int[] local = localSize(active);
        if (groups == null || scale == null || local == null) {
            String raw = stripComments(source);
            groups = groups != null ? groups : workGroups(raw);
            scale = scale != null ? scale : renderScale(raw);
            local = local != null ? local : localSize(raw);
        }
        return new ComputeDirectives(groups, scale, local);
    }

    // Returns the fixed work group counts or null.
    public int[] workGroups() {
        return this.workGroups;
    }

    // Returns the screen scale of the dispatch or null.
    public float[] renderScale() {
        return this.renderScale;
    }

    // Returns the local work group size.
    public int[] localSize() {
        return this.localSize;
    }

    // Parses the fixed work group counts of a source.
    static int[] workGroups(String code) {
        Matcher vector = WORK_GROUPS_VECTOR.matcher(code);
        if (vector.find()) {
            int dimensions = vector.group(1).charAt(0) - '0';
            String[] values = vector.group(2).split(",");
            if (values.length == dimensions || values.length == 1) {
                int[] groups = {1, 1, 1};
                for (int i = 0; i < Math.min(3, dimensions); i++) {
                    int value = positiveInt(values.length == 1 ? values[0] : values[i]);
                    if (value <= 0) {
                        return null;
                    }
                    groups[i] = value;
                }
                return groups;
            }
        }
        int[] named = new int[3];
        boolean any = false;
        Matcher matcher = WORK_GROUPS_NAMED.matcher(code);
        while (matcher.find()) {
            int axis = matcher.group(1).charAt(0) - 'X';
            int value = positiveInt(matcher.group(2));
            if (named[axis] == 0 && value > 0) {
                named[axis] = value;
                any = true;
            }
        }
        return any ? new int[]{Math.max(1, named[0]), Math.max(1, named[1]), Math.max(1, named[2])} : null;
    }

    // Parses the screen scale of a source.
    static float[] renderScale(String code) {
        Matcher matcher = WORK_GROUPS_RENDER.matcher(code);
        if (!matcher.find()) {
            return null;
        }
        String[] values = matcher.group(1).split(",");
        try {
            float x = floatLiteral(values[0]);
            float y = values.length > 1 ? floatLiteral(values[1]) : x;
            return Float.isFinite(x) && Float.isFinite(y) ? new float[]{x, y} : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Parses the local work group size of a source.
    static int[] localSize(String code) {
        Matcher matcher = LOCAL_SIZE.matcher(code);
        int[] size = {0, 0, 0};
        boolean any = false;
        while (matcher.find()) {
            int axis = matcher.group(1).charAt(0) - 'x';
            if (size[axis] == 0) {
                size[axis] = Math.max(1, positiveInt(matcher.group(2)));
                any = true;
            }
        }
        return any ? new int[]{Math.max(1, size[0]), Math.max(1, size[1]), Math.max(1, size[2])} : null;
    }

    // Parses a positive int or returns minus one.
    static int positiveInt(String raw) {
        String value = raw.trim();
        if (value.endsWith("u") || value.endsWith("U")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Parses a float literal or returns NaN.
    static float floatLiteral(String raw) {
        String value = raw.trim();
        if (value.endsWith("f") || value.endsWith("F")) {
            value = value.substring(0, value.length() - 1);
        }
        return Float.parseFloat(value);
    }

    // Returns a quotient rounded up.
    public static int ceilDiv(int value, int divisor) {
        long d = Math.max(1, divisor);
        long groups = (Math.max(0L, value) + d - 1) / d;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, groups));
    }

    // Returns the groups that cover a scaled screen size.
    public static int screenGroups(int size, float scale, int localSize) {
        return ceilDiv((int) Math.ceil(size * scale), localSize);
    }

    // Returns whether the groups cover a volume.
    public static boolean coversVolume(int[] workGroups, int[] localSize, int[] volume) {
        for (int i = 0; i < 3; i++) {
            if ((long) workGroups[i] * localSize[i] < volume[i]) {
                return false;
            }
        }
        return true;
    }

    // Parses the indirect dispatch pointers of the properties.
    public static Map<String, IndirectPointer> parseIndirectPointers(Map<String, String> rawProperties) {
        Map<String, IndirectPointer> pointers = new LinkedHashMap<>();
        rawProperties.forEach((key, value) -> {
            if (!key.startsWith(INDIRECT_PREFIX)) {
                return;
            }
            String[] parts = value.trim().split("\\s+");
            long index;
            long offset;
            try {
                index = Long.parseLong(parts[0]);
                offset = Long.parseLong(parts[1]);
            } catch (RuntimeException e) {
                LOGGER.warn("[Umbra] Malformed indirect directive '{} = {}'", key, value);
                return;
            }
            if (index < 0 || index > Integer.MAX_VALUE || offset < 0 || (offset & 3) != 0) {
                LOGGER.warn("[Umbra] Indirect directive '{} = {}' needs a bufferObject index and a non-negative offset that is a multiple of 4", key, value);
                return;
            }
            pointers.put(key.substring(INDIRECT_PREFIX.length()), new IndirectPointer((int) index, offset));
        });
        return pointers;
    }

    // Returns a source without comments.
    public static String stripComments(String source) {
        int length = source.length();
        StringBuilder out = null;
        int copyFrom = 0;
        int i = 0;
        while (i < length - 1) {
            char c = source.charAt(i);
            char next = source.charAt(i + 1);
            if (c != '/' || (next != '/' && next != '*')) {
                i++;
                continue;
            }
            if (out == null) {
                out = new StringBuilder(length);
            }
            out.append(source, copyFrom, i);
            if (next == '/') {
                int end = source.indexOf('\n', i + 2);
                i = end < 0 ? length : end;
            } else {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                out.append(' ');
            }
            copyFrom = i;
        }
        if (out == null) {
            return source;
        }
        return out.append(source, copyFrom, length).toString();
    }
}
