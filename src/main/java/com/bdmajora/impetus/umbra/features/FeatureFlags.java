package com.bdmajora.impetus.umbra.features;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Iris feature names a pack can require with the hardware each needs.
public enum FeatureFlags {
    // Hardware compared shadow samplers beside the plain shadow textures.
    SEPARATE_HARDWARE_SAMPLERS(Hardware.ANY),
    // shadowcolor2 to shadowcolor7 on top of OptiFine's two.
    HIGHER_SHADOWCOLOR(Hardware.ANY),
    // Image directives read and written with image load and store.
    CUSTOM_IMAGES(Hardware.IMAGE_LOAD_STORE),
    // A blend mode per draw buffer.
    PER_BUFFER_BLENDING(Hardware.BUFFER_BLENDING),
    // Compute programs in the composite and deferred chains.
    COMPUTE_SHADERS(Hardware.COMPUTE),
    // Tessellation control and evaluation stages on gbuffer programs.
    TESSELLATION_SHADERS(Hardware.TESSELLATION),
    // A gbuffers_entities_translucent program for translucent entities.
    ENTITY_TRANSLUCENT(Hardware.ANY),
    // Culls the shadow pass from the far side.
    REVERSED_CULLING(Hardware.ANY),
    // Block light emission in at_midBlock.w.
    BLOCK_EMISSION_ATTRIBUTE(Hardware.ANY),
    // Lets a pack stop vanilla rain and snow.
    CAN_DISABLE_WEATHER(Hardware.ANY),
    // bufferObject.<index> shader storage buffers.
    SSBO(Hardware.SSBO),
    // The mc_chunkFade value for chunks fading in.
    FADE_VARIABLE(Hardware.ANY),
    // Filtering and clamping set per custom texture.
    TEXTURE_FILTERING(Hardware.ANY),
    // A name this version does not know.
    UNKNOWN(Hardware.NEVER);

    // Hardware the feature needs.
    private final Hardware hardware;

    // Creates a feature from the hardware it needs.
    FeatureFlags(Hardware hardware) {
        this.hardware = hardware;
    }

    // Returns whether this GPU and driver can run the feature.
    public boolean isUsable() {
        return this.hardware.isSupported();
    }

    // Returns the feature a pack token names or UNKNOWN.
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

    // GL capabilities a feature can depend on.
    enum Hardware {
        // Every GPU this mod runs on.
        ANY(() -> true),
        // Never supported.
        NEVER(() -> false),
        // GL 4.2 image load and store.
        IMAGE_LOAD_STORE(() -> LWJGL.isOpenGLVersionSupported(4, 2)),
        // Per draw buffer blend state.
        BUFFER_BLENDING(() -> LWJGL.supportsBufferBlending()),
        // GL 4.3 compute shaders.
        COMPUTE(() -> LWJGL.isOpenGLVersionSupported(4, 3)),
        // GL 4.0 tessellation stages.
        TESSELLATION(() -> LWJGL.isOpenGLVersionSupported(4, 0)),
        // Shader storage buffers.
        SSBO(() -> LWJGL.isOpenGLVersionSupported(4, 3)
                || LWJGL.isExtensionSupported(com.bdmajora.impetus.lwjgl.GLExtension.ARB_shader_storage_buffer_object));

        // Query of the current context.
        private final BooleanSupplier check;

        // Creates a capability from its context query.
        Hardware(BooleanSupplier check) {
            this.check = check;
        }

        // Returns whether the context has the capability and true before a context exists.
        boolean isSupported() {
            try {
                return this.check.getAsBoolean();
            } catch (IllegalStateException e) {
                return true;
            }
        }
    }
}
