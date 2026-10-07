package com.bdmajora.impetus.umbra.features;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The Iris feature-flag vocabulary; packs name these in shaders.properties as `iris.features.required`/`optional`, so the constant names are the pack-facing spelling and cannot be renamed. A flag is usable when this port implements it (all of Iris's now) and the GPU can run it, Iris's own two-part rule
public enum FeatureFlags {
    SEPARATE_HARDWARE_SAMPLERS(Hardware.ANY),
    HIGHER_SHADOWCOLOR(Hardware.ANY),
    CUSTOM_IMAGES(Hardware.IMAGE_LOAD_STORE),
    PER_BUFFER_BLENDING(Hardware.BUFFER_BLENDING),
    COMPUTE_SHADERS(Hardware.COMPUTE),
    TESSELLATION_SHADERS(Hardware.TESSELLATION),
    ENTITY_TRANSLUCENT(Hardware.ANY),
    REVERSED_CULLING(Hardware.ANY),
    BLOCK_EMISSION_ATTRIBUTE(Hardware.ANY),
    CAN_DISABLE_WEATHER(Hardware.ANY),
    SSBO(Hardware.SSBO),
    FADE_VARIABLE(Hardware.ANY),
    TEXTURE_FILTERING(Hardware.ANY),
    // Catch-all for a token we do not recognise at all, a newer Iris flag or a typo in the pack
    UNKNOWN(Hardware.NEVER);

    private final Hardware hardware;

    FeatureFlags(Hardware hardware) {
        this.hardware = hardware;
    }

    // Implemented here and runnable on this GPU
    public boolean isUsable() {
        return this.hardware.isSupported();
    }

    // Token -> constant, trimmed and upper-cased since packs write these loosely; Iris's old misspelling of tessellation is accepted like Iris accepts it, and anything else unrecognised is UNKNOWN rather than an exception so a pack listing a newer flag under `optional` still loads
    public static FeatureFlags byName(String name) {
        String token = name.trim().toUpperCase(Locale.ROOT);
        if (token.equals("TESSELATION_SHADERS")) {
            return TESSELLATION_SHADERS;
        }
        try {
            return valueOf(token);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    // Names from a declaration list this port cannot honour, in the pack's own spelling so the error quotes the author; only called with the `required` list
    public static List<String> findUnsupported(String declared) {
        List<String> missing = new ArrayList<>();
        if (declared == null || declared.trim().isEmpty()) {
            return missing;
        }
        // Whitespace-separated, matching Iris's own parse of the property value
        for (String token : declared.trim().split("\\s+")) {
            if (!byName(token).isUsable()) {
                missing.add(token);
            }
        }
        return missing;
    }

    // Whether a refused list is the GPU's doing or the port's, for the message Iris shows: an unknown token is the port's, a known one the hardware's
    public static String describeUnsupported(List<String> missing) {
        boolean port = false;
        boolean hardware = false;
        for (String token : missing) {
            if (byName(token) == UNKNOWN) {
                port = true;
            } else {
                hardware = true;
            }
        }
        if (port && hardware) {
            return "not supported by Umbra or by this GPU";
        }
        return port ? "not supported by Umbra" : "not supported by this GPU";
    }

    // Flags the pack asked for AND this port can use, from both lists; what ShaderPack.hasFeature answers, distinct from which IRIS_FEATURE_* defines exist
    public static Set<FeatureFlags> parseDeclared(String required, String optional) {
        Set<FeatureFlags> declared = EnumSet.noneOf(FeatureFlags.class);
        for (String list : new String[]{required, optional}) {
            if (list == null || list.trim().isEmpty()) {
                continue;
            }
            for (String token : list.trim().split("\\s+")) {
                FeatureFlags flag = byName(token);
                // UNKNOWN and unusable tokens drop out here; unusable `required` ones already failed the load
                if (flag.isUsable()) {
                    declared.add(flag);
                }
            }
        }
        return declared;
    }

    // Emits one `IRIS_FEATURE_<NAME>` define per usable flag; the prefix is Iris's, packs gate on it verbatim (Complementary's colored lighting), and it is advertised unconditionally since the lists are a contract, not a request
    public static void addUsableDefines(Map<String, String> macros) {
        for (FeatureFlags flag : values()) {
            if (flag.isUsable()) {
                macros.put("IRIS_FEATURE_" + flag.name(), "");
            }
        }
    }

    // The GPU side of each flag, asked of the live context; a thread without one (tests, an early parse) is treated as capable, as before this check existed
    enum Hardware {
        ANY(() -> true),
        NEVER(() -> false),
        IMAGE_LOAD_STORE(() -> LWJGL.isOpenGLVersionSupported(4, 2)),
        BUFFER_BLENDING(() -> LWJGL.supportsBufferBlending()),
        COMPUTE(() -> LWJGL.isOpenGLVersionSupported(4, 3)),
        TESSELLATION(() -> LWJGL.isOpenGLVersionSupported(4, 0)),
        SSBO(() -> LWJGL.isOpenGLVersionSupported(4, 3)
                || LWJGL.isExtensionSupported(com.bdmajora.impetus.lwjgl.GLExtension.ARB_shader_storage_buffer_object));

        private final BooleanSupplier check;

        Hardware(BooleanSupplier check) {
            this.check = check;
        }

        boolean isSupported() {
            try {
                return this.check.getAsBoolean();
            } catch (IllegalStateException e) {
                return true;
            }
        }
    }
}
