package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTexturePatch;
import com.bdmajora.impetus.umbra.uniforms.custom.CustomUniforms;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

// Parsed contents of a pack properties file.
public final class ShaderProperties {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final Map<String, String> raw;
    private final Map<String, String> original;
    private final Map<String, String> expressionDefines;
    // Programs the current profile disables.
    private final Set<String> profileDisabledPrograms;

    // Options shown as sliders.
    private final List<String> sliderOptions = new ArrayList<>();
    private final Map<String, List<String>> profiles = new LinkedHashMap<>();
    // Options of the main screen or null.
    private List<String> mainScreenOptions = null;
    private final Map<String, List<String>> subScreenOptions = new LinkedHashMap<>();
    // Columns of the main screen or null.
    private Integer mainScreenColumnCount = null;
    private final Map<String, Integer> subScreenColumnCount = new HashMap<>();

    // Path of the noise texture or null.
    private String noiseTexturePath = null;
    private final Map<TextureStage, Map<String, String>> customTextures = new EnumMap<>(TextureStage.class);
    private final Map<String, String> irisCustomTextures = new LinkedHashMap<>();
    // Sampler renames of custom textures.
    private final List<CustomTexturePatch> customTexturePatches =
            new ArrayList<>();
    // Number of custom textures.
    private int customTexAmount = 0;
    private final Map<Integer, float[]> bufferSizes = new LinkedHashMap<>();
    private final Map<Integer, boolean[]> bufferSizeRelative = new LinkedHashMap<>();
    // Custom image definitions.
    private final List<CustomImageDefinition> irisCustomImages =
            new ArrayList<>();
    private final Map<String, Map<Integer, Boolean>> explicitFlips = new LinkedHashMap<>();
    private final Map<String, float[]> viewportScaleOverrides = new LinkedHashMap<>();

    // Builder of the custom uniforms.
    private final CustomUniforms.Builder customUniforms =
            new CustomUniforms.Builder();

    // Creates a shader properties from its preprocessed and original.
    private ShaderProperties(Map<String, String> preprocessed, Map<String, String> original,
                             Map<String, String> expressionDefines, Set<String> profileDisabledPrograms) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(preprocessed));
        this.original = Collections.unmodifiableMap(new LinkedHashMap<>(original));
        this.expressionDefines = Collections.unmodifiableMap(new HashMap<>(expressionDefines));
        this.profileDisabledPrograms = Collections.unmodifiableSet(new LinkedHashSet<>(profileDisabledPrograms));
        parseMenuDirectives(original);
        parseCustomTextureDirectives();
        parseCustomUniformDirectives();
    }

    // Returns empty properties.
    public static ShaderProperties empty() {
        return new ShaderProperties(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptySet());
    }

    // Parses properties text without a preprocessed copy.
    public static ShaderProperties parse(String contents) {
        Map<String, String> map = parseMap(contents);
        return new ShaderProperties(map, map, Collections.emptyMap(), Collections.emptySet());
    }

    // Parses properties text with its preprocessed copy.
    public static ShaderProperties parse(String original, String preprocessed) {
        return parse(original, preprocessed, Collections.emptyMap(), Collections.emptySet());
    }

    // Parses properties text with defines and known custom uniforms.
    public static ShaderProperties parse(String original, String preprocessed,
                                         Map<String, String> expressionDefines,
                                         Set<String> profileDisabledPrograms) {
        return new ShaderProperties(parseMap(preprocessed), parseMap(original),
                expressionDefines, profileDisabledPrograms);
    }

    // Returns a copy with the profile disabled programs.
    public ShaderProperties withProfileDisabledPrograms(Set<String> disabledPrograms) {
        return new ShaderProperties(this.raw, this.original, this.expressionDefines, disabledPrograms);
    }

    // Parses properties text into a map.
    private static Map<String, String> parseMap(String contents) {
        Map<String, String> map = new LinkedHashMap<>();
        if (contents == null) {
            return map;
        }
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

    // Reads the custom uniform and variable lines.
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

    // Returns the custom uniforms.
    public CustomUniforms.Builder getCustomUniforms() {
        return this.customUniforms;
    }

    // Returns the raw.
    public Map<String, String> getRaw() {
        return this.raw;
    }

    // Reads the screen and slider lines.
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

    // Reads the texture and image lines.
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
                String samplerName = rest.substring(dot + 1).split("\\.")[0];
                Optional<TextureStage> stage = TextureStage.parse(stageName);
                if (!stage.isPresent()) {
                    LOGGER.warn("[Umbra] Unknown texture stage \"{}\", ignoring custom texture directive for {}",
                            stageName, key);
                    return;
                }
                String[] parts = value.trim().split("\\s+");
                if (parts.length > 1) {
                    String textureType = rawTextureType(parts);
                    if (textureType == null) {
                        LOGGER.warn("[Umbra] Unknown raw texture directive for {}: {}", key, value);
                        return;
                    }
                    String newSamplerName = "customtex" + this.customTexAmount++;
                    this.irisCustomTextures.put(newSamplerName, value.trim());
                    this.customTexturePatches.add(
                            new CustomTexturePatch(
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
                CustomImageDefinition definition =
                        CustomImageDefinition.parse(name, value);
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

    // Returns the raw texture type of a definition or null.
    private static String rawTextureType(String[] parts) {
        return switch (parts.length) {
            case 6 -> "TEXTURE_1D";
            case 7 -> parts[1].toUpperCase(Locale.ROOT);
            case 8 -> "TEXTURE_3D";
            default -> null;
        };
    }

    // Reads a buffer size line.
    private void parseBufferSize(String key, String value) {
        String targetName = key.substring("size.buffer.".length()).trim();
        Integer index = UmbraRenderTargets.colorTargetIndex(targetName);
        if (index == null) {
            LOGGER.warn("[Umbra] Unknown render target '{}' in {}, ignoring it", targetName, key);
            return;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 2) {
            LOGGER.warn("[Umbra] {} needs exactly two values (got '{}'), ignoring it", key, value);
            return;
        }
        parts[0] = resolveNumericMacro(parts[0]);
        parts[1] = resolveNumericMacro(parts[1]);
        try {
            boolean xRelative = parts[0].contains(".");
            boolean yRelative = parts[1].contains(".");
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            if (!Float.isFinite(x) || !Float.isFinite(y) || x <= 0.0f || y <= 0.0f) {
                LOGGER.warn("[Umbra] {} has a non-positive size ({} {}), ignoring it", key, parts[0], parts[1]);
                return;
            }
            this.bufferSizes.put(index, new float[]{x, y});
            this.bufferSizeRelative.put(index, new boolean[]{xRelative, yRelative});
        } catch (NumberFormatException e) {
            LOGGER.warn("[Umbra] Malformed size in {} ('{}'), ignoring it", key, value);
        }
    }

    // Returns a numeric value of a macro name or the token.
    private String resolveNumericMacro(String token) {
        if (token.isEmpty() || Character.isDigit(token.charAt(0)) || token.charAt(0) == '.'
                || token.charAt(0) == '-') {
            return token;
        }
        String resolved = this.expressionDefines.get(token);
        return resolved == null ? token : resolved.trim();
    }

    // Returns the buffer sizes.
    public Map<Integer, float[]> getBufferSizes() {
        return Collections.unmodifiableMap(this.bufferSizes);
    }

    // Returns which size parts of a buffer are relative.
    public boolean[] getBufferSizeRelative(int index) {
        boolean[] flags = this.bufferSizeRelative.get(index);
        return flags == null ? new boolean[]{false, false} : flags.clone();
    }

    // Reads a flip line.
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
        Integer target = UmbraRenderTargets.colorTargetIndex(rest.substring(dot + 1));
        if (target == null) {
            LOGGER.warn("[Umbra] Unknown explicit flip target, ignoring: {}", key);
            return;
        }
        this.explicitFlips
                .computeIfAbsent(rest.substring(0, dot), ignored -> new LinkedHashMap<>())
                .put(target, shouldFlip.get());
    }

    // Reads a viewport scale line.
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
            if (!Float.isFinite(scale) || !Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
                LOGGER.warn("[Umbra] Scale directive for {} is not finite: {}", program, value);
                return;
            }
            this.viewportScaleOverrides.put(program, new float[]{scale, offsetX, offsetY});
        } catch (NumberFormatException e) {
            LOGGER.warn("[Umbra] Unable to parse scale directive for {}: {}", program, value);
        }
    }

    // Returns the viewport scale of a program or null.
    public float[] getViewportScale(String programName) {
        return this.viewportScaleOverrides.get(programName);
    }

    // Splits text on whitespace.
    private static List<String> splitWhitespace(String value) {
        List<String> result = new ArrayList<>();
        for (String token : value.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    // Parses an int or returns null.
    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Returns the raw properties.
    public Map<String, String> asMap() {
        return this.raw;
    }

    // Returns the slider options.
    public List<String> getSliderOptions() {
        return Collections.unmodifiableList(this.sliderOptions);
    }

    // Returns the profiles.
    public Map<String, List<String>> getProfiles() {
        return Collections.unmodifiableMap(this.profiles);
    }

    // Returns the main screen options.
    public Optional<List<String>> getMainScreenOptions() {
        return Optional.ofNullable(this.mainScreenOptions);
    }

    // Returns the sub screen options.
    public Map<String, List<String>> getSubScreenOptions() {
        return Collections.unmodifiableMap(this.subScreenOptions);
    }

    // Returns the main screen column count.
    public Optional<Integer> getMainScreenColumnCount() {
        return Optional.ofNullable(this.mainScreenColumnCount);
    }

    // Returns the sub screen column count.
    public Map<String, Integer> getSubScreenColumnCount() {
        return Collections.unmodifiableMap(this.subScreenColumnCount);
    }

    // Returns the value.
    public Optional<String> get(String key) {
        return Optional.ofNullable(this.raw.get(key));
    }

    // Returns the shadow map resolution.
    public OptionalInt getShadowMapResolution() {
        return getInt("shadowMapResolution");
    }

    // Returns the shadow distance.
    public OptionalInt getShadowDistance() {
        return getInt("shadowDistance");
    }

    // Returns the shadow enabled.
    public Optional<Boolean> getShadowEnabled() {
        return getBoolean("shadow.enabled");
    }

    // Returns the dh shadow enabled.
    public Optional<Boolean> getDhShadowEnabled() {
        return getBoolean("dhShadow.enabled");
    }

    // Returns the cloud mode.
    public Optional<String> getCloudMode() {
        return get("clouds").map(s -> s.toLowerCase(Locale.ROOT));
    }

    // Returns the dh cloud mode.
    public Optional<String> getDhCloudMode() {
        return get("dhClouds").map(s -> s.toLowerCase(Locale.ROOT));
    }

    // Returns the render sun.
    public Optional<Boolean> getRenderSun() {
        return getBoolean("sun");
    }

    // Returns the render moon.
    public Optional<Boolean> getRenderMoon() {
        return getBoolean("moon");
    }

    // Returns the render stars.
    public Optional<Boolean> getRenderStars() {
        return getBoolean("stars");
    }

    // Returns the render sky.
    public Optional<Boolean> getRenderSky() {
        return getBoolean("sky");
    }

    // Returns the render vignette.
    public Optional<Boolean> getRenderVignette() {
        return getBoolean("vignette");
    }

    // Returns the render underwater overlay.
    public Optional<Boolean> getRenderUnderwaterOverlay() {
        return getBoolean("underwaterOverlay");
    }

    // Returns the render weather.
    public Optional<Boolean> getRenderWeather() {
        return getBoolean("weather");
    }

    // Returns the beacon beam depth.
    public Optional<Boolean> getBeaconBeamDepth() {
        return getBoolean("beacon.beam.depth");
    }

    // Returns the rain depth.
    public Optional<Boolean> getRainDepth() {
        return getBoolean("rain.depth");
    }

    // Returns the separate ao.
    public Optional<Boolean> getSeparateAo() {
        return getBoolean("separateAo");
    }

    // Returns the old lighting.
    public Optional<Boolean> getOldLighting() {
        return getBoolean("oldLighting");
    }

    // Returns the old hand light.
    public Optional<Boolean> getOldHandLight() {
        return getBoolean("oldHandLight");
    }

    // Returns the dynamic hand light.
    public Optional<Boolean> getDynamicHandLight() {
        return getBoolean("dynamicHandLight");
    }

    // Returns the frustum culling.
    public Optional<Boolean> getFrustumCulling() {
        return getBoolean("frustum.culling");
    }

    // Returns the occlusion culling.
    public Optional<Boolean> getOcclusionCulling() {
        return getBoolean("occlusion.culling");
    }

    // Returns the back face culling switch of a layer.
    public Optional<Boolean> getBackFaceCulling(String layer) {
        return getBoolean("backFace." + layer);
    }

    // Returns the shadow terrain.
    public Optional<Boolean> getShadowTerrain() {
        return getBoolean("shadowTerrain");
    }

    // Returns the shadow translucent.
    public Optional<Boolean> getShadowTranslucent() {
        return getBoolean("shadowTranslucent");
    }

    // Returns the shadow entities.
    public Optional<Boolean> getShadowEntities() {
        return getBoolean("shadowEntities");
    }

    // Returns the shadow block entities.
    public Optional<Boolean> getShadowBlockEntities() {
        return getBoolean("shadowBlockEntities");
    }

    // Returns the shadow light block entities.
    public Optional<Boolean> getShadowLightBlockEntities() {
        return getBoolean("shadowLightBlockEntities");
    }

    // Returns the shadow player.
    public Optional<Boolean> getShadowPlayer() {
        return getBoolean("shadowPlayer");
    }

    // Returns the shadow culling.
    public Optional<String> getShadowCulling() {
        return get("shadow.culling").map(s -> s.toLowerCase(Locale.ROOT));
    }

    // Returns the voxelize light blocks.
    public Optional<Boolean> getVoxelizeLightBlocks() {
        return getBoolean("voxelizeLightBlocks");
    }

    // Returns the separate entity draws.
    public Optional<Boolean> getSeparateEntityDraws() {
        return getBoolean("separateEntityDraws");
    }

    // Returns the particle ordering.
    public Optional<String> getParticleOrdering() {
        Optional<String> ordering = get("particles.ordering").map(s -> s.toLowerCase(Locale.ROOT));
        if (ordering.isPresent()) {
            return ordering;
        }
        return getBoolean("particles.before.deferred").orElse(Boolean.FALSE)
                ? Optional.of("before")
                : Optional.empty();
    }

    // Returns the prepare before shadow.
    public Optional<Boolean> getPrepareBeforeShadow() {
        return getBoolean("prepareBeforeShadow");
    }

    // Returns the allow concurrent compute.
    public Optional<Boolean> getAllowConcurrentCompute() {
        return getBoolean("allowConcurrentCompute");
    }

    // Returns the supports color correction.
    public Optional<Boolean> getSupportsColorCorrection() {
        return getBoolean("supportsColorCorrection");
    }

    // Returns the breaks anisotropy.
    public Optional<Boolean> getBreaksAnisotropy() {
        return getBoolean("breaksAnisotropy");
    }

    // Returns the skip all rendering.
    public Optional<Boolean> getSkipAllRendering() {
        return getBoolean("skipAllRendering");
    }

    // Returns the fallback tex.
    public OptionalInt getFallbackTex() {
        return getInt("fallbackTex");
    }

    // Returns the alpha test override of a program.
    public Optional<String> getAlphaTestOverride(String programName) {
        return get("alphaTest." + programName);
    }

    // Returns the noise texture path.
    public Optional<String> getNoiseTexturePath() {
        return Optional.ofNullable(this.noiseTexturePath);
    }

    // Returns the custom textures.
    public Map<TextureStage, Map<String, String>> getCustomTextures() {
        return Collections.unmodifiableMap(this.customTextures);
    }

    // Returns the umbra custom textures.
    public Map<String, String> getUmbraCustomTextures() {
        return Collections.unmodifiableMap(this.irisCustomTextures);
    }

    // Returns the custom texture patches.
    public List<CustomTexturePatch> getCustomTexturePatches() {
        return Collections.unmodifiableList(this.customTexturePatches);
    }

    // Returns the umbra custom images.
    public List<CustomImageDefinition> getUmbraCustomImages() {
        return Collections.unmodifiableList(this.irisCustomImages);
    }

    // Returns the blend override of a program.
    public Optional<String> getBlendModeOverride(String programName) {
        return get("blend." + programName);
    }

    // Returns the enabled switch of a program.
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

    // Returns whether the current profile disables a program.
    private boolean isProfileDisabled(String programName) {
        return this.profileDisabledPrograms.contains(programName)
                || this.profileDisabledPrograms.contains("world0/" + programName);
    }

    // Returns the first boolean of a list of keys.
    private Optional<Boolean> firstBoolean(String... keys) {
        for (String key : keys) {
            Optional<Boolean> value = getBoolean(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    // Returns the flips of a program by buffer.
    public Map<Integer, Boolean> getExplicitFlips(String programName) {
        Map<Integer, Boolean> flips = this.explicitFlips.get(programName);
        return flips == null ? Collections.emptyMap() : Collections.unmodifiableMap(flips);
    }

    // Returns an int property or empty.
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

    // Returns a boolean property or empty.
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

    // Parses true or false or returns empty.
    private static Optional<Boolean> parseBooleanValue(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Optional.of(Boolean.TRUE);
            case "false" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }
}
