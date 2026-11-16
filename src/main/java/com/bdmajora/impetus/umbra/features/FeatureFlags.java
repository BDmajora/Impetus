package com.bdmajora.impetus.umbra.features;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Choices of the feature flags setting.
public enum FeatureFlags {
    // Value that selects separate hardware samplers.
    SEPARATE_HARDWARE_SAMPLERS(Hardware.ANY),
    // Value that selects higher shadowcolor.
    HIGHER_SHADOWCOLOR(Hardware.ANY),
    // Value that selects custom images.
    CUSTOM_IMAGES(Hardware.IMAGE_LOAD_STORE),
    // Value that selects per buffer blending.
    PER_BUFFER_BLENDING(Hardware.BUFFER_BLENDING),
    // Value that selects compute shaders.
    COMPUTE_SHADERS(Hardware.COMPUTE),
    // Value that selects tessellation shaders.
    TESSELLATION_SHADERS(Hardware.TESSELLATION),
    // Value that selects entity translucent.
    ENTITY_TRANSLUCENT(Hardware.ANY),
    // Value that selects reversed culling.
    REVERSED_CULLING(Hardware.ANY),
    // Value that selects block emission attribute.
    BLOCK_EMISSION_ATTRIBUTE(Hardware.ANY),
    // Value that selects can disable weather.
    CAN_DISABLE_WEATHER(Hardware.ANY),
    // Value that selects ssbo.
    SSBO(Hardware.SSBO),
    // Value that selects fade variable.
    FADE_VARIABLE(Hardware.ANY),
    // Value that selects texture filtering.
    TEXTURE_FILTERING(Hardware.ANY),
    // Value that selects unknown.
    UNKNOWN(Hardware.NEVER);

    // Hardware check of the feature.
    private final Hardware hardware;

    // Creates a feature flags from its hardware.
    FeatureFlags(Hardware hardware) {
        this.hardware = hardware;
    }

    // Returns whether usable.
    public boolean isUsable() {
        return this.hardware.isSupported();
    }

    // Bies the name.
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

    // Returns the declared features that the hardware lacks.
    public static List<String> findUnsupported(String declared) {
        List<String> missing = new ArrayList<>();
        if (declared == null || declared.trim().isEmpty()) {
            return missing;
        }
        for (String token : declared.trim().split("\\s+")) {
            if (!byName(token).isUsable()) {
                missing.add(token);
            }
        }
        return missing;
    }

    // Returns the text of missing features.
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

    // Returns the usable features of the required and optional lists.
    public static Set<FeatureFlags> parseDeclared(String required, String optional) {
        Set<FeatureFlags> declared = EnumSet.noneOf(FeatureFlags.class);
        for (String list : new String[]{required, optional}) {
            if (list == null || list.trim().isEmpty()) {
                continue;
            }
            for (String token : list.trim().split("\\s+")) {
                FeatureFlags flag = byName(token);
                if (flag.isUsable()) {
                    declared.add(flag);
                }
            }
        }
        return declared;
    }

    // Adds a define for each usable feature.
    public static void addUsableDefines(Map<String, String> macros) {
        for (FeatureFlags flag : values()) {
            if (flag.isUsable()) {
                macros.put("IRIS_FEATURE_" + flag.name(), "");
            }
        }
    }

    // Choices of the hardware setting.
    enum Hardware {
        // Value that selects any.
        ANY(() -> true),
        // Value that selects never.
        NEVER(() -> false),
        // Value that selects image load store.
        IMAGE_LOAD_STORE(() -> LWJGL.isOpenGLVersionSupported(4, 2)),
        // Value that selects buffer blending.
        BUFFER_BLENDING(() -> LWJGL.supportsBufferBlending()),
        // Value that selects compute.
        COMPUTE(() -> LWJGL.isOpenGLVersionSupported(4, 3)),
        // Value that selects tessellation.
        TESSELLATION(() -> LWJGL.isOpenGLVersionSupported(4, 0)),
        // Value that selects ssbo.
        SSBO(() -> LWJGL.isOpenGLVersionSupported(4, 3)
                || LWJGL.isExtensionSupported(com.bdmajora.impetus.lwjgl.GLExtension.ARB_shader_storage_buffer_object));

        // Test of whether the hardware supports the feature.
        private final BooleanSupplier check;

        // Creates a hardware from its check.
        Hardware(BooleanSupplier check) {
            this.check = check;
        }

        // Returns whether supported.
        boolean isSupported() {
            try {
                return this.check.getAsBoolean();
            } catch (IllegalStateException e) {
                return true;
            }
        }
    }
}
