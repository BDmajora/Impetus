package com.bdmajora.impetus.umbra.shaderpack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

// A parsed shaders.properties: the full map plus typed accessors; pipeline directives from the preprocessed contents so option gates resolve, menu layout from the original, split on first = rather than Properties since its escapes mangle GLSL
public final class ShaderProperties {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final List<String> LEGACY_RENDER_TARGETS =
            Arrays.asList("gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4");

    private final Map<String, String> raw;
    private final Map<String, String> original;
    private final Map<String, String> expressionDefines;
    private final Set<String> profileDisabledPrograms;

    // --- Option-menu layout directives (parsed from the raw, non-preprocessed file) ---
    private final List<String> sliderOptions = new ArrayList<>();
    private final Map<String, List<String>> profiles = new LinkedHashMap<>();
    private List<String> mainScreenOptions = null;
    private final Map<String, List<String>> subScreenOptions = new LinkedHashMap<>();
    private Integer mainScreenColumnCount = null;
    private final Map<String, Integer> subScreenColumnCount = new HashMap<>();

    // texture.noise, the pack's own noise texture replacing the generated noisetex (Umbra ShaderProperties parity)
    private String noiseTexturePath = null;
    // texture.<stage>.<sampler> — per-stage sampler overrides, keyed by stage and then by sampler name
    private final Map<TextureStage, Map<String, String>> customTextures = new EnumMap<>(TextureStage.class);
    // customTexture.<name> — pack-defined named samplers, bound in every stage rather than scoped to one
    private final Map<String, String> irisCustomTextures = new LinkedHashMap<>();
    // Raw texture.<stage>.<sampler> directives resolved into a minted customtexN sampler plus the type-checked rename redirecting that stage's programs (Iris's customTexturePatching); the type check keeps a 3D texture from feeding a sampler2D of the same name
    private final List<com.bdmajora.impetus.umbra.shaderpack.texture.CustomTexturePatch> customTexturePatches =
            new ArrayList<>();
    // The counter behind the minted customtexN names, matching Iris's customTexAmount
    private int customTexAmount = 0;
    // size.buffer.colortexN — explicit render-target sizes, either absolute texels or screen-relative fractions
    private final Map<Integer, float[]> bufferSizes = new LinkedHashMap<>();
    // Per AXIS, whether the matching bufferSizes entry is a fraction of the render size rather than texels, since `512 0.5` is a legal mix
    private final Map<Integer, boolean[]> bufferSizeRelative = new LinkedHashMap<>();
    // image.<name>, the writable custom images a pack reaches through imageStore, in DECLARATION ORDER since that is the order image units are assigned
    private final List<com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition> irisCustomImages =
            new ArrayList<>();
    // flip.<program>.<target>, explicit ping-pong overrides including the deferred_pre and composite_pre pseudo-programs that run before their chains
    private final Map<String, Map<Integer, Boolean>> explicitFlips = new LinkedHashMap<>();
    // scale.<program> (Iris's viewportScaleOverrides): the pass rasterises into a SUB-RECTANGLE of its targets, how packs run SSAO and volumetrics at reduced resolution; distinct from size.buffer.colortexN, which resizes the target itself. Stored as {scale, offsetX, offsetY}
    private final Map<String, float[]> viewportScaleOverrides = new LinkedHashMap<>();

    // The uniform.<type>.<name> and variable.<type>.<name> custom expressions, accumulated into a builder because declaration order matters for evaluation
    private final com.bdmajora.impetus.umbra.uniforms.custom.CustomUniforms.Builder customUniforms =
            new com.bdmajora.impetus.umbra.uniforms.custom.CustomUniforms.Builder();

    private ShaderProperties(Map<String, String> preprocessed, Map<String, String> original,
                             Map<String, String> expressionDefines, Set<String> profileDisabledPrograms) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(preprocessed));
        this.original = Collections.unmodifiableMap(new LinkedHashMap<>(original));
        this.expressionDefines = Collections.unmodifiableMap(new HashMap<>(expressionDefines));
        this.profileDisabledPrograms = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(profileDisabledPrograms));
        parseMenuDirectives(original);
        parseCustomTextureDirectives();
        parseCustomUniformDirectives();
    }

    // No directives at all; every getter returns its default
    public static ShaderProperties empty() {
        return new ShaderProperties(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptySet());
    }

    // Parses with NO conditional evaluation, every #if-guarded line read and the last wins; only for callers with no define set, the pipeline always uses the two-argument form
    public static ShaderProperties parse(String contents) {
        Map<String, String> map = parseMap(contents);
        return new ShaderProperties(map, map, Collections.emptyMap(), Collections.emptySet());
    }

    // original is the file as shipped (menu-layout directives read from it), preprocessed has conditionals resolved against the active defines and options (everything the pipeline consumes reads from it)
    public static ShaderProperties parse(String original, String preprocessed) {
        return parse(original, preprocessed, Collections.emptyMap(), Collections.emptySet());
    }

    public static ShaderProperties parse(String original, String preprocessed,
                                         Map<String, String> expressionDefines,
                                         Set<String> profileDisabledPrograms) {
        return new ShaderProperties(parseMap(preprocessed), parseMap(original),
                expressionDefines, profileDisabledPrograms);
    }

    // A copy with the active profile's program.<name>.enabled=false entries applied
    public ShaderProperties withProfileDisabledPrograms(Set<String> disabledPrograms) {
        return new ShaderProperties(this.raw, this.original, this.expressionDefines, disabledPrograms);
    }

    // Key=value lines, honouring backslash continuation and skipping comments
    private static Map<String, String> parseMap(String contents) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String rawLine : contents.split("\r\n|\r|\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    // Parses the uniform.<type>.<name> and variable.<type>.<name> directives into the custom-uniforms builder, mirroring Iris
    private void parseCustomUniformDirectives() {
        this.raw.forEach((key, value) -> {
            boolean isUniform = key.startsWith("uniform.");
            boolean isVariable = key.startsWith("variable.");
            if (!isUniform && !isVariable) {
                return;
            }

            String remainder = key.substring((isUniform ? "uniform." : "variable.").length());
            String[] parts = remainder.split("\\.", 2);
            if (parts.length != 2) {
                LOGGER.warn("[Umbra] Custom {} should take the form `{}.<type>.<name> = <expression>`; ignoring {}",
                        isUniform ? "uniforms" : "variables", isUniform ? "uniform" : "variable", key);
                return;
            }

            this.customUniforms.addVariable(parts[0], parts[1], value, isUniform);
        });
    }

    // The collected custom uniform and variable declarations, still as a builder so the pipeline can finish them
    public com.bdmajora.impetus.umbra.uniforms.custom.CustomUniforms.Builder getCustomUniforms() {
        return this.customUniforms;
    }

    // The raw preprocessed key/value directives as a read-only view, what the accessors read from and what a consumer uses for an uncovered key
    public Map<String, String> getRaw() {
        return Collections.unmodifiableMap(this.raw);
    }

    // Parses the option-menu layout directives (sliders, profile.*, screen, screen.*, column counts) from the ORIGINAL contents, since the menu must present every option regardless of which values are active
    private void parseMenuDirectives(Map<String, String> original) {
        original.forEach((key, value) -> {
            if (key.equals("sliders")) {
                this.sliderOptions.clear();
                this.sliderOptions.addAll(splitWhitespace(value));
            } else if (key.startsWith("profile.")) {
                this.profiles.put(key.substring("profile.".length()), splitWhitespace(value));
            } else if (key.equals("screen.columns")) {
                this.mainScreenColumnCount = parseIntOrNull(value);
            } else if (key.startsWith("screen.") && key.endsWith(".columns")) {
                String name = key.substring("screen.".length(), key.length() - ".columns".length());
                Integer columns = parseIntOrNull(value);
                if (columns != null) {
                    this.subScreenColumnCount.put(name, columns);
                }
            } else if (key.equals("screen")) {
                this.mainScreenOptions = splitWhitespace(value);
            } else if (key.startsWith("screen.")) {
                this.subScreenOptions.put(key.substring("screen.".length()), splitWhitespace(value));
            }
        });
    }

    // Parses the custom-texture directives like Iris: texture.noise, texture.<stage>.<sampler> (a .N mip-level suffix is dropped to the base name), customTexture.<name> (Iris-exclusive, every stage); a multi-token value is a raw-texture definition resolved later at data load
    private void parseCustomTextureDirectives() {
        this.raw.forEach((key, value) -> {
            if (key.equals("texture.noise")) {
                this.noiseTexturePath = value;
            } else if (key.startsWith("flip.")) {
                parseExplicitFlip(key, value);
            } else if (key.startsWith("scale.")) {
                parseViewportScale(key, value);
            } else if (key.startsWith("texture.")) {
                String rest = key.substring("texture.".length());
                int dot = rest.indexOf('.');
                if (dot <= 0 || dot == rest.length() - 1) {
                    LOGGER.warn("[Umbra] Malformed custom texture directive, ignoring: {}", key);
                    return;
                }
                String stageName = rest.substring(0, dot);
                // OptiFine allows a trailing ".N" mip-level suffix; Umbra keeps only the base sampler name.
                String samplerName = rest.substring(dot + 1).split("\\.")[0];
                Optional<TextureStage> stage = TextureStage.parse(stageName);
                if (!stage.isPresent()) {
                    LOGGER.warn("[Umbra] Unknown texture stage \"{}\", ignoring custom texture directive for {}",
                            stageName, key);
                    return;
                }
                String[] parts = value.trim().split("\\s+");
                if (parts.length > 1) {
                    // A raw texture definition; Umbra does NOT hijack the sampler's unit for the whole stage but mints a new name and renames only programs declaring a matching sampler type, since Photon's colortex6 is a sampler3D in deferred/deferred1 and a sampler2D in deferred3/deferred4
                    String textureType = rawTextureType(parts);
                    if (textureType == null) {
                        LOGGER.warn("[Umbra] Unknown raw texture directive for {}: {}", key, value);
                        return;
                    }
                    String newSamplerName = "customtex" + this.customTexAmount++;
                    this.irisCustomTextures.put(newSamplerName, value.trim());
                    this.customTexturePatches.add(
                            new com.bdmajora.impetus.umbra.shaderpack.texture.CustomTexturePatch(
                                    samplerName, stage.get(), textureType, newSamplerName));
                    return;
                }
                this.customTextures
                        .computeIfAbsent(stage.get(), s -> new LinkedHashMap<>())
                        .put(samplerName, value);
            } else if (key.startsWith("size.buffer.")) {
                parseBufferSize(key, value);
            } else if (key.startsWith("image.")) {
                String name = key.substring("image.".length());
                com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition definition =
                        com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition.parse(name, value);
                if (definition != null) {
                    this.irisCustomImages.add(definition);
                }
            } else if (key.startsWith("customTexture.")) {
                String name = key.substring("customTexture.".length());
                if (name.isEmpty()) {
                    LOGGER.warn("[Umbra] Malformed custom texture directive, ignoring: {}", key);
                    return;
                }
                this.irisCustomTextures.put(name, value.trim());
            }
        });
    }

    // The texture target of a raw texture.* definition inferred from TOKEN COUNT like Iris: 6 is 1D, 7 is whatever <type> says (2D or rectangle), 8 is 3D, anything else malformed
    private static String rawTextureType(String[] parts) {
        switch (parts.length) {
            case 6:
                return "TEXTURE_1D";
            case 7:
                return parts[1].toUpperCase(Locale.ROOT);
            case 8:
                return "TEXTURE_3D";
            default:
                return null;
        }
    }

    // size.buffer.colortexN = <width> <height>; Iris decides absolute vs relative per axis by whether the token parses as an INTEGER, so `512 512` is fixed and `0.5 0.5` is half resolution
    private void parseBufferSize(String key, String value) {
        String targetName = key.substring("size.buffer.".length()).trim();
        Integer index = colorTargetIndex(targetName);
        if (index == null) {
            LOGGER.warn("[Umbra] Unknown render target '{}' in {}, ignoring it", targetName, key);
            return;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 2) {
            LOGGER.warn("[Umbra] {} needs exactly two values (got '{}'), ignoring it", key, value);
            return;
        }
        // Complementary writes `size.buffer.colortex1 = REFLECTION_RES REFLECTION_RES`, so a non-numeric token is resolved against the pack's option values first
        parts[0] = resolveNumericMacro(parts[0]);
        parts[1] = resolveNumericMacro(parts[1]);
        try {
            // Umbra (TextureScaleOverride) decides PER AXIS: a token containing '.' is a fraction, one without is texels, so `512 0.5` is fixed width by half height; check the text, since "1 1" is 1x1 and "1.0 1.0" is full resolution
            boolean xRelative = parts[0].contains(".");
            boolean yRelative = parts[1].contains(".");
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            if (x <= 0.0f || y <= 0.0f) {
                LOGGER.warn("[Umbra] {} has a non-positive size ({} {}), ignoring it", key, parts[0], parts[1]);
                return;
            }
            this.bufferSizes.put(index, new float[]{x, y});
            this.bufferSizeRelative.put(index, new boolean[]{xRelative, yRelative});
        } catch (NumberFormatException e) {
            LOGGER.warn("[Umbra] Malformed size in {} ('{}'), ignoring it", key, value);
        }
    }

    // Resolves an option macro where a number is expected, since packs size buffers by their options; unchanged when already numeric or undefined, so the caller's parse fails and warns naming the directive
    private String resolveNumericMacro(String token) {
        if (token.isEmpty() || Character.isDigit(token.charAt(0)) || token.charAt(0) == '.'
                || token.charAt(0) == '-') {
            return token;
        }
        String resolved = this.expressionDefines.get(token);
        return resolved == null ? token : resolved.trim();
    }

    // The explicit render-target sizes as {width, height}, texels or fractions per the per-axis relative flags, which must be read alongside
    public Map<Integer, float[]> getBufferSizes() {
        return Collections.unmodifiableMap(this.bufferSizes);
    }

    // The {xRelative, yRelative} pair for one target, or {false, false} when it declares no size at all
    public boolean[] getBufferSizeRelative(int index) {
        boolean[] flags = this.bufferSizeRelative.get(index);
        return flags == null ? new boolean[]{false, false} : flags.clone();
    }

    // flip.<program>.<buffer> = true|false
    private void parseExplicitFlip(String key, String value) {
        String rest = key.substring("flip.".length());
        int dot = rest.indexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) {
            LOGGER.warn("[Umbra] Malformed explicit flip directive, ignoring: {}", key);
            return;
        }
        Optional<Boolean> shouldFlip = parseBooleanValue(value);
        if (!shouldFlip.isPresent()) {
            LOGGER.warn("[Umbra] Invalid explicit flip value, ignoring: {} = {}", key, value);
            return;
        }
        Integer target = colorTargetIndex(rest.substring(dot + 1));
        if (target == null) {
            LOGGER.warn("[Umbra] Unknown explicit flip target, ignoring: {}", key);
            return;
        }
        this.explicitFlips
                .computeIfAbsent(rest.substring(0, dot), ignored -> new LinkedHashMap<>())
                .put(target, shouldFlip.get());
    }

    // scale.<program> = <factor> [offsetX offsetY]; Iris parses the offsets as a PAIR, so exactly two tokens is malformed and rejected rather than guessed, matching its ArrayIndexOutOfBoundsException branch
    private void parseViewportScale(String key, String value) {
        String program = key.substring("scale.".length());
        if (program.isEmpty()) {
            LOGGER.warn("[Umbra] Malformed scale directive, ignoring: {}", key);
            return;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 1 && parts.length != 3) {
            LOGGER.warn("[Umbra] Unable to parse scale directive for {}: {}", program, value);
            return;
        }
        try {
            float scale = Float.parseFloat(parts[0]);
            float offsetX = parts.length == 3 ? Float.parseFloat(parts[1]) : 0.0f;
            float offsetY = parts.length == 3 ? Float.parseFloat(parts[2]) : 0.0f;
            this.viewportScaleOverrides.put(program, new float[]{scale, offsetX, offsetY});
        } catch (NumberFormatException e) {
            LOGGER.warn("[Umbra] Unable to parse scale directive for {}: {}", program, value);
        }
    }

    // The {scale, offsetX, offsetY} override for one fullscreen pass, or null when it has none
    public float[] getViewportScale(String programName) {
        return this.viewportScaleOverrides.get(programName);
    }

    // colortexN or gcolor-style name to an index; null for anything else
    private static Integer colorTargetIndex(String name) {
        if (name.startsWith("colortex")) {
            try {
                return Integer.parseInt(name.substring("colortex".length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        int legacyIndex = LEGACY_RENDER_TARGETS.indexOf(name);
        return legacyIndex >= 0 ? legacyIndex : null;
    }

    // Tokens of a space-separated value
    private static List<String> splitWhitespace(String value) {
        List<String> result = new ArrayList<>();
        for (String token : value.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    // Null rather than an exception for a malformed number
    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // The raw directive map, unmodifiable and in declaration order
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(this.raw);
    }

    // The options the pack wants rendered as sliders rather than click-to-cycle buttons, a presentation choice only since both walk the same value list
    public List<String> getSliderOptions() {
        return Collections.unmodifiableList(this.sliderOptions);
    }

    // The declared profiles in declaration order, each mapping to its option directives; order makes next/previous cycling meaningful
    public Map<String, List<String>> getProfiles() {
        return Collections.unmodifiableMap(this.profiles);
    }

    // The main option screen's layout, empty when the pack declares none, in which case the screen is generated from the discovered options
    public Optional<List<String>> getMainScreenOptions() {
        return Optional.ofNullable(this.mainScreenOptions);
    }

    // The sub-screen layouts, keyed by sub-screen name
    public Map<String, List<String>> getSubScreenOptions() {
        return Collections.unmodifiableMap(this.subScreenOptions);
    }

    // screen.columns
    public Optional<Integer> getMainScreenColumnCount() {
        return Optional.ofNullable(this.mainScreenColumnCount);
    }

    // screen.<name>.columns per sub-screen
    public Map<String, Integer> getSubScreenColumnCount() {
        return Collections.unmodifiableMap(this.subScreenColumnCount);
    }

    // Raw lookup for directives with no dedicated getter
    public Optional<String> get(String key) {
        return Optional.ofNullable(this.raw.get(key));
    }

    // --- Typed pipeline-relevant accessors ---

    // shadowMapResolution — the square shadow map's edge length in texels, empty when unspecified
    public OptionalInt getShadowMapResolution() {
        return getInt("shadowMapResolution");
    }

    // shadowDistance — how far the shadow map reaches, in blocks, empty when unspecified
    public OptionalInt getShadowDistance() {
        return getInt("shadowDistance");
    }

    // shadow.enabled — the pack's explicit shadow toggle, empty when it states no preference
    public Optional<Boolean> getShadowEnabled() {
        return getBoolean("shadow.enabled");
    }

    // dhShadow.enabled — whether Distant Horizons LODs cast into the shadow map (Iris's PackShadowDirectives.isDhShadowEnabled), empty when unstated
    public Optional<Boolean> getDhShadowEnabled() {
        return getBoolean("dhShadow.enabled");
    }

    // clouds = off | fast | fancy, empty when the pack leaves the player's own setting alone
    public Optional<String> getCloudMode() {
        return get("clouds").map(s -> s.toLowerCase(Locale.ROOT));
    }

    // dhClouds = off | fast | fancy: whether Distant Horizons draws its own clouds under this pack (Iris's getDHCloudSetting), empty meaning "follow clouds"
    public Optional<String> getDhCloudMode() {
        return get("dhClouds").map(s -> s.toLowerCase(Locale.ROOT));
    }

    // Vanilla feature toggles: OptiFine lets a pack suppress vanilla world features it draws itself, each a plain true/false key, absent meaning "leave vanilla alone"

    // sun — draw the vanilla sun quad. Packs that render their own celestial bodies set this false.
    public Optional<Boolean> getRenderSun() {
        return getBoolean("sun");
    }

    // moon — draw the vanilla moon quad.
    public Optional<Boolean> getRenderMoon() {
        return getBoolean("moon");
    }

    // stars — draw the vanilla star field (Umbra extension; OptiFine folds stars into the sky).
    public Optional<Boolean> getRenderStars() {
        return getBoolean("stars");
    }

    // sky — draw the vanilla sky dome/void plane at all (Umbra extension).
    public Optional<Boolean> getRenderSky() {
        return getBoolean("sky");
    }

    // vignette — draw vanilla's screen vignette overlay.
    public Optional<Boolean> getRenderVignette() {
        return getBoolean("vignette");
    }

    // underwaterOverlay — draw vanilla's underwater texture overlay.
    public Optional<Boolean> getRenderUnderwaterOverlay() {
        return getBoolean("underwaterOverlay");
    }

    // weather — draw vanilla rain/snow particles (Umbra extension).
    public Optional<Boolean> getRenderWeather() {
        return getBoolean("weather");
    }

    // beacon.beam.depth — whether the beacon beam writes depth.
    public Optional<Boolean> getBeaconBeamDepth() {
        return getBoolean("beacon.beam.depth");
    }

    // rain.depth — whether rain/snow writes depth.
    public Optional<Boolean> getRainDepth() {
        return getBoolean("rain.depth");
    }

    // separateAo — feed vanilla's ambient occlusion as vertex colour separately from block light.
    public Optional<Boolean> getSeparateAo() {
        return getBoolean("separateAo");
    }

    // oldLighting — keep vanilla's fixed-function directional block shading.
    public Optional<Boolean> getOldLighting() {
        return getBoolean("oldLighting");
    }

    // oldHandLight — use the legacy held-light behaviour instead of heldBlockLightValue.
    public Optional<Boolean> getOldHandLight() {
        return getBoolean("oldHandLight");
    }

    // dynamicHandLight — let the held item emit light through the heldBlockLightValue uniform.
    public Optional<Boolean> getDynamicHandLight() {
        return getBoolean("dynamicHandLight");
    }

    // frustum.culling — allow vanilla frustum culling in the main pass.
    public Optional<Boolean> getFrustumCulling() {
        return getBoolean("frustum.culling");
    }

    // occlusion.culling — allow vanilla occlusion culling in the main pass.
    public Optional<Boolean> getOcclusionCulling() {
        return getBoolean("occlusion.culling");
    }

    // backFace.solid|cutout|cutoutMipped|translucent — per-terrain-layer back-face culling override.
    public Optional<Boolean> getBackFaceCulling(String layer) {
        return getBoolean("backFace." + layer);
    }

    // ------------------------------------------------------------------ shadow pass content

    // shadowTerrain — draw terrain into the shadow map.
    public Optional<Boolean> getShadowTerrain() {
        return getBoolean("shadowTerrain");
    }

    // shadowTranslucent — draw translucent terrain into the shadow map (colored/water shadows).
    public Optional<Boolean> getShadowTranslucent() {
        return getBoolean("shadowTranslucent");
    }

    // shadowEntities — draw entities into the shadow map.
    public Optional<Boolean> getShadowEntities() {
        return getBoolean("shadowEntities");
    }

    // shadowBlockEntities — draw block entities (TESRs) into the shadow map.
    public Optional<Boolean> getShadowBlockEntities() {
        return getBoolean("shadowBlockEntities");
    }

    // shadowLightBlockEntities — draw only light-emitting block entities into the shadow map.
    public Optional<Boolean> getShadowLightBlockEntities() {
        return getBoolean("shadowLightBlockEntities");
    }

    // shadowPlayer — draw the player model into the shadow map.
    public Optional<Boolean> getShadowPlayer() {
        return getBoolean("shadowPlayer");
    }

    // shadow.culling = on | off | reversed; Photon and Complementary ask for reversed, keeping geometry between the light and camera a normal frustum test drops
    public Optional<String> getShadowCulling() {
        return get("shadow.culling").map(s -> s.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ pipeline behaviour flags

    // voxelizeLightBlocks — submit light-emitting blocks to the shadow pass for voxelization.
    public Optional<Boolean> getVoxelizeLightBlocks() {
        return getBoolean("voxelizeLightBlocks");
    }

    // separateEntityDraws — draw entities in their own pass, after the deferred chain.
    public Optional<Boolean> getSeparateEntityDraws() {
        return getBoolean("separateEntityDraws");
    }

    // particles.ordering = mixed | after | before, falling back to OptiFine's older particles.before.deferred boolean only while UNSET; MakeUp-UltraFast and E-LITE declare only the legacy key, and ignoring it drew their particles after the deferred chain
    public Optional<String> getParticleOrdering() {
        Optional<String> ordering = get("particles.ordering").map(s -> s.toLowerCase(Locale.ROOT));
        if (ordering.isPresent()) {
            return ordering;
        }
        return getBoolean("particles.before.deferred").orElse(Boolean.FALSE)
                ? Optional.of("before")
                : Optional.empty();
    }

    // prepareBeforeShadow — run the prepare family before the shadow map instead of after.
    public Optional<Boolean> getPrepareBeforeShadow() {
        return getBoolean("prepareBeforeShadow");
    }

    // allowConcurrentCompute — skip the memory barrier between consecutive compute dispatches.
    public Optional<Boolean> getAllowConcurrentCompute() {
        return getBoolean("allowConcurrentCompute");
    }

    // supportsColorCorrection — the pack handles the output colour space itself.
    public Optional<Boolean> getSupportsColorCorrection() {
        return getBoolean("supportsColorCorrection");
    }

    // breaksAnisotropy, the pack is incompatible with anisotropic atlas filtering; parsed for parity only, since this renderer never anisotropically filters the atlas (see BlockAtlasFiltering)
    public Optional<Boolean> getBreaksAnisotropy() {
        return getBoolean("breaksAnisotropy");
    }

    // skipAllRendering — draw nothing but the composite chain (debug/benchmark packs).
    public Optional<Boolean> getSkipAllRendering() {
        return getBoolean("skipAllRendering");
    }

    // fallbackTex, the texture index a pack nominates for an unbound sampler; parsed for parity, and like Umbra nothing reads it
    public OptionalInt getFallbackTex() {
        return getInt("fallbackTex");
    }

    // Per-program alpha test override, e.g. alphaTest.gbuffers_water = GREATER 0.0001 or alphaTest.gbuffers_terrain = off
    public Optional<String> getAlphaTestOverride(String programName) {
        return get("alphaTest." + programName);
    }

    // texture.noise — pack path of the PNG that replaces the generated noisetex, if specified.
    public Optional<String> getNoiseTexturePath() {
        return Optional.ofNullable(this.noiseTexturePath);
    }

    // texture.<stage>.<sampler> overrides: stage → (sampler name → pack path / resource location).
    public Map<TextureStage, Map<String, String>> getCustomTextures() {
        return Collections.unmodifiableMap(this.customTextures);
    }

    // customTexture.<name> definitions: sampler name → pack path / resource location (all stages).
    public Map<String, String> getUmbraCustomTextures() {
        return Collections.unmodifiableMap(this.irisCustomTextures);
    }

    // Raw texture.<stage>.<sampler> directives as type-checked sampler renames (see the patch's javadoc).
    public List<com.bdmajora.impetus.umbra.shaderpack.texture.CustomTexturePatch> getCustomTexturePatches() {
        return Collections.unmodifiableList(this.customTexturePatches);
    }

    // image.<name> definitions in declaration order (Umbra custom writable images).
    public List<com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition> getUmbraCustomImages() {
        return Collections.unmodifiableList(this.irisCustomImages);
    }

    // Per-program blend override by source name, e.g. blend.composite2 = SRC_ALPHA ONE_MINUS_SRC_ALPHA or blend.water = off
    public Optional<String> getBlendModeOverride(String programName) {
        return get("blend." + programName);
    }

    // per-program enable toggle, e.g. program.composite4.enabled = false
    public Optional<Boolean> getProgramEnabled(String programName) {
        if (isProfileDisabled(programName)) {
            return Optional.of(Boolean.FALSE);
        }

        return firstBoolean(
                "program.world0/" + programName + ".enabled",
                "program.world0/" + programName + "..enabled",
                "program." + programName + ".enabled",
                "program." + programName + "..enabled");
    }

    // Whether the active profile switched the program off
    private boolean isProfileDisabled(String programName) {
        return this.profileDisabledPrograms.contains(programName)
                || this.profileDisabledPrograms.contains("world0/" + programName);
    }

    // First of several alternative spellings that is set
    private Optional<Boolean> firstBoolean(String... keys) {
        for (String key : keys) {
            Optional<Boolean> value = getBoolean(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    // The program's flip directives, by buffer index
    public Map<Integer, Boolean> getExplicitFlips(String programName) {
        Map<Integer, Boolean> flips = this.explicitFlips.get(programName);
        return flips == null ? Collections.emptyMap() : Collections.unmodifiableMap(flips);
    }

    // Parsed integer, empty when unset or malformed
    private OptionalInt getInt(String key) {
        String value = this.raw.get(key);
        if (value == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    // Parsed boolean, empty when unset or malformed
    private Optional<Boolean> getBoolean(String key) {
        String value = this.raw.get(key);
        if (value == null) {
            return Optional.empty();
        }
        Optional<Boolean> literal = parseBooleanValue(value);
        if (literal.isPresent()) {
            return literal;
        }
        return PropertiesPreprocessor.evaluateBooleanExpression(value, this.expressionDefines);
    }

    // true/false, on/off; empty for anything else
    private static Optional<Boolean> parseBooleanValue(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true")) {
            return Optional.of(Boolean.TRUE);
        }
        if (v.equals("false")) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }
}
