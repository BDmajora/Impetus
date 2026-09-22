package com.bdmajora.impetus.umbra.shaderpack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.include.IncludeProcessor;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.IdMap;
import com.bdmajora.impetus.umbra.shaderpack.option.Profile;
import com.bdmajora.impetus.umbra.shaderpack.option.ProfileSet;
import com.bdmajora.impetus.umbra.shaderpack.option.ShaderPackOptions;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureFilteringData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.umbra.features.FeatureFlags;
import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// A fully parsed pack: every GLSL file by path, shaders.properties and the assembled ProgramSet; works over an in-memory map so it is Minecraft-free and testable, world0/ overrides the root, other dimensions unhandled
public final class ShaderPack {
    // The conventional location of shaders.properties, relative to shaders/
    public static final AbsolutePackPath PROPERTIES_PATH = AbsolutePackPath.fromAbsolutePath("/shaders.properties");
    // The overworld override directory, checked BEFORE the pack root so a world0/ program wins
    private static final String OVERWORLD_DIR = "/world0";

    // Matches `!defined(IS_IRIS) && MC_VERSION < <n>` — the shape detectLegacyPrograms below looks for
    private static final Pattern LEGACY_BRANCH_PATTERN = Pattern.compile(
            "!\\s*defined\\s*\\(?\\s*IS_IRIS\\s*\\)?\\s*&&\\s*MC_VERSION\\s*<\\s*(\\d+)");
    // The extensions that make a pack file a shader STAGE rather than an include; everything else only reaches the driver by being included
    private static final Set<String> STAGE_EXTENSIONS =
            new HashSet<>(Arrays.asList("vsh", "fsh", "gsh", "csh", "tcs", "tes"));

    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final Map<AbsolutePackPath, String> sources;
    private final Map<AbsolutePackPath, byte[]> binaries;
    private final ShaderPackOptions shaderPackOptions;
    private final IncludeProcessor includeProcessor;
    private final ShaderProperties properties;
    private final ProgramSet baseProgramSet;
    // The features this pack DECLARED in iris.features.required/optional that this port honours; distinct from the IRIS_FEATURE_<NAME> defines, which advertise everything supported (Iris draws the same line between hasFeature and isUsable())
    private final Set<FeatureFlags> activeFeatures;
    // The pack's block/item/entity.properties maps preprocessed with the ACTIVE option values, since a pack gates its ID map on its own options
    private final IdMap idMap;

    // texture.noise resolved to data, or null meaning "keep the generated noisetex" (Umbra ShaderPack parity)
    private final CustomTextureData customNoiseTexture;
    // texture.<stage>.<sampler> directives resolved to data, keyed by stage then sampler name, since the same sampler name means different things in different stages
    private final Map<TextureStage, Map<String, CustomTextureData>> customTextureDataMap =
            new EnumMap<>(TextureStage.class);
    // customTexture.<name> directives resolved to data keyed by sampler name; stage-independent, so no stage dimension
    private final Map<String, CustomTextureData> irisCustomTextureDataMap = new LinkedHashMap<>();

    public ShaderPack(Map<AbsolutePackPath, String> sources) {
        this(sources, Collections.emptyMap());
    }

    public ShaderPack(Map<AbsolutePackPath, String> sources, Map<String, String> changedConfigs) {
        this(sources, changedConfigs, Collections.emptyMap());
    }

    // sources is the pack's raw text keyed relative to shaders/; changedConfigs is only the option values that DIFFER from defaults (from <pack>.txt and the menu), applied BEFORE include flattening since an option can gate an #include; binaries are .png textures and .mcmeta sidecars
    public ShaderPack(Map<AbsolutePackPath, String> sources, Map<String, String> changedConfigs,
                      Map<AbsolutePackPath, byte[]> binaries) {
        this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
        this.binaries = Collections.unmodifiableMap(new HashMap<>(binaries));

        // Discover options across every source except the properties file and apply the changed values FIRST, since the properties/ID-map preprocessing needs the resolved values as macros and the include processor flattens the EDITED sources
        Map<AbsolutePackPath, String> optionSources = new HashMap<>(this.sources);
        optionSources.remove(PROPERTIES_PATH);
        this.shaderPackOptions = new ShaderPackOptions(optionSources, changedConfigs);

        // The macro environment Umbra feeds its PropertiesPreprocessor: MC_* defines plus the pack's option values (enabled booleans as flags, string options as values)
        Map<String, String> propertiesDefines = getShaderDefines();

        // Umbra parity: pipeline directives read from the PREPROCESSED contents so #if gates resolve, menu-layout directives from the original; option EDITS never touch this file
        String propertiesContents = this.sources.get(PROPERTIES_PATH);
        ShaderProperties parsedProperties = propertiesContents != null
                ? ShaderProperties.parse(propertiesContents,
                        PropertiesPreprocessor.preprocessProperties(propertiesContents, propertiesDefines),
                        propertiesDefines,
                        Collections.emptySet())
                : ShaderProperties.empty();
        Set<String> profileDisabledPrograms = activeProfileDisabledPrograms(parsedProperties);
        this.properties = profileDisabledPrograms.isEmpty()
                ? parsedProperties
                : parsedProperties.withProfileDisabledPrograms(profileDisabledPrograms);

        // Feature-flag validation: a pack *requiring* a flag this port cannot honor fails loudly rather than rendering subtly wrong; optional flags stay undefined for the pack to detect
        this.activeFeatures = FeatureFlags.parseDeclared(
                this.properties.getRaw().get("iris.features.required"),
                this.properties.getRaw().get("iris.features.optional"));
        List<String> unsupportedRequired = FeatureFlags.findUnsupported(this.properties.getRaw().get("iris.features.required"));
        if (!unsupportedRequired.isEmpty()) {
            String missing = String.join(", ", unsupportedRequired);
            LOGGER.error("[Umbra] This shader pack requires Umbra features not supported by this port: {}", missing);
            ImpetusNotifications.warn(
                    "Shader pack may not work correctly",
                    "Requires unsupported features:",
                    missing);
        }

        Map<AbsolutePackPath, String> flattenSources = new HashMap<>(this.sources);
        flattenSources.putAll(this.shaderPackOptions.getEditedSources());
        this.includeProcessor = new IncludeProcessor(flattenSources);

        this.baseProgramSet = buildProgramSet();

        this.idMap = new IdMap(this.sources, propertiesDefines);

        // Resolve the custom-texture directives to data like Umbra's ShaderPack constructor; a texture that fails to read is logged and dropped, and the sampler sees the normal target or generated noise
        this.customNoiseTexture = this.properties.getNoiseTexturePath().map(this::readTextureOrNull).orElse(null);

        this.properties.getCustomTextures().forEach((stage, texturePropertiesMap) -> {
            Map<String, CustomTextureData> innerCustomTextureDataMap = new LinkedHashMap<>();
            texturePropertiesMap.forEach((samplerName, path) -> putTextureIfReadable(innerCustomTextureDataMap, samplerName, path));
            this.customTextureDataMap.put(stage, innerCustomTextureDataMap);
        });

        this.properties.getUmbraCustomTextures().forEach((name, path) -> putTextureIfReadable(this.irisCustomTextureDataMap, name, path));

        // Must run before anything compiles: the terrain and composite compile paths ask ShaderMacros.forProgram which programs take their pre-Umbra branch
        ShaderMacros.setPackLegacyPrograms(detectLegacyPrograms(this.sources));
        // Likewise for the raw-custom-texture renames: the gbuffers/terrain/shadow compile paths reach the transform from static contexts with no pack handle
        CustomTextureTransformer.setActivePatches(
                this.properties.getCustomTexturePatches());
    }

    // Finds programs containing `!defined(IS_IRIS) && MC_VERSION < <newer than ours>`, the pack's authored path for an OptiFine this old, and compiles them WITHOUT IS_IRIS; across nine packs this selects exactly Sildur's gbuffers_water and composite1 (its 1.12.2 water defers reflection to composite1 via gl_FragData[2], giving see-through water), while BSL's and Complementary's guards do not match. Scans RAW sources since an include says nothing about which program switches
    private static Set<String> detectLegacyPrograms(Map<AbsolutePackPath, String> rawSources) {
        Set<String> legacy = new HashSet<>();
        for (Map.Entry<AbsolutePackPath, String> entry : rawSources.entrySet()) {
            String name = programName(entry.getKey());
            if (name == null || legacy.contains(name)) {
                continue;
            }
            Matcher matcher = LEGACY_BRANCH_PATTERN.matcher(entry.getValue());
            while (matcher.find()) {
                int version;
                try {
                    version = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }
                // Only a cutoff ABOVE ours means the legacy branch is the one 1.12.2 would take.
                if (version > ShaderMacros.MC_VERSION) {
                    legacy.add(name);
                    break;
                }
            }
        }
        return legacy;
    }

    // Reduces a path to its program name (/world1/composite1.fsh -> composite1), null for anything that is not a shader stage
    private static String programName(AbsolutePackPath path) {
        String file = path.getPathString();
        int slash = file.lastIndexOf('/');
        if (slash >= 0) {
            file = file.substring(slash + 1);
        }
        int dot = file.lastIndexOf('.');
        if (dot < 0 || !STAGE_EXTENSIONS.contains(file.substring(dot + 1))) {
            return null;
        }
        return file.substring(0, dot);
    }

    // readTexture with the failure logged and swallowed, so one bad texture drops just its sampler
    private CustomTextureData readTextureOrNull(String path) {
        try {
            return readTexture(path);
        } catch (IOException e) {
            LOGGER.error("[Umbra] Unable to read the custom texture at {}: {}", path, e.getMessage());
            return null;
        }
    }

    // Stores the sampler's texture only when it could be read
    private void putTextureIfReadable(Map<String, CustomTextureData> into, String samplerName, String path) {
        CustomTextureData data = readTextureOrNull(path);
        if (data != null) {
            into.put(samplerName, data);
        }
    }

    // Resolves one custom-texture directive value like Iris's readTexture: a namespace:path becomes a resource-location texture looked up at bind time (minecraft:dynamic/lightmap_1 marks the live lightmap), anything else a PNG in the pack with a tolerated leading / (Continuum 2.0.4) and blur/clamp from its .mcmeta
    private CustomTextureData readTexture(String path) throws IOException {
        String[] rawParts = path.trim().split("\\s+");
        if (rawParts.length > 1) {
            return readRawTexture(rawParts);
        }
        if (path.contains(":")) {
            String[] parts = path.split(":");
            if (parts.length > 2) {
                LOGGER.warn("[Umbra] Resource location {} contained more than two parts?", path);
            }
            if (parts[0].equals("minecraft")
                    && (parts[1].equals("dynamic/lightmap_1") || parts[1].equals("dynamic/light_map_1"))) {
                return new CustomTextureData.LightmapMarker();
            }
            return new CustomTextureData.ResourceData(parts[0], parts[1]);
        }

        // NB: like Umbra, this does not guarantee the path stays inside the pack; the strip just fixes packs writing "/lib/..." instead of "lib/..."
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        byte[] content = readBinary(path);

        boolean blur = false;
        boolean clamp = false;
        byte[] mcMeta = this.binaries.get(AbsolutePackPath.fromAbsolutePath("/" + path + ".mcmeta"));
        if (mcMeta != null) {
            try {
                JsonObject meta = new JsonParser()
                        .parse(new String(mcMeta, StandardCharsets.UTF_8)).getAsJsonObject();
                if (meta.get("texture") != null) {
                    JsonObject texture = meta.get("texture").getAsJsonObject();
                    if (texture.get("blur") != null) {
                        blur = texture.get("blur").getAsBoolean();
                    }
                    if (texture.get("clamp") != null) {
                        clamp = texture.get("clamp").getAsBoolean();
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.error("[Umbra] Unable to read the custom texture mcmeta at {}.mcmeta, ignoring: {}",
                        path, e.getMessage());
            }
        }

        return new CustomTextureData.PngData(new TextureFilteringData(blur, clamp), content);
    }

    // texture.<stage>.<name> = <path> <type> <format> <w> <h> [<d>] <pixelFormat> <pixelType>
    private CustomTextureData readRawTexture(String[] parts) throws IOException {
        String textureType = parts[1].toUpperCase(Locale.ROOT);
        if (textureType.equals("TEXTURE_3D")) {
            if (parts.length < 8) {
                throw new IOException("Malformed raw 3D texture definition");
            }
            return new CustomTextureData.RawData(textureType, parts[2],
                    parsePositiveInt(parts[3], "width"),
                    parsePositiveInt(parts[4], "height"),
                    parsePositiveInt(parts[5], "depth"),
                    parts[6], parts[7], readBinary(parts[0]));
        }
        if (textureType.equals("TEXTURE_2D")) {
            if (parts.length < 7) {
                throw new IOException("Malformed raw 2D texture definition");
            }
            return new CustomTextureData.RawData(textureType, parts[2],
                    parsePositiveInt(parts[3], "width"),
                    parsePositiveInt(parts[4], "height"),
                    1, parts[5], parts[6], readBinary(parts[0]));
        }
        throw new IOException("Unsupported raw texture target: " + parts[1]);
    }

    // A binary resource from the pack, by pack-relative path
    private byte[] readBinary(String path) throws IOException {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        AbsolutePackPath texturePath = AbsolutePackPath.fromAbsolutePath("/" + path);
        byte[] content = this.binaries.get(texturePath);
        if (content == null) {
            throw new IOException("Texture file not found in pack: " + path);
        }
        return content;
    }

    // Fails with the field named, since a raw texture directive has many
    private static int parsePositiveInt(String value, String field) throws IOException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IOException("Raw texture " + field + " must be positive: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IOException("Bad raw texture " + field + ": " + value);
        }
    }

    // The macro set for preprocessing the pack's *.properties files: the GL-free MC_* macros PLUS the pack's option values, matching Iris's PropertiesPreprocessor; option values deliberately do not go in getEnvironmentDefines
    public Map<String, String> getShaderDefines() {
        Map<String, String> defines = ShaderMacros.standard();
        this.shaderPackOptions.getOptionSet().getBooleanOptions().forEach((name, option) -> {
            if (this.shaderPackOptions.getOptionValues().getBooleanValueOrDefault(name)) {
                defines.put(name, "1");
            }
        });
        this.shaderPackOptions.getOptionSet().getStringOptions().forEach((name, option) ->
                defines.put(name, this.shaderPackOptions.getOptionValues().getStringValueOrDefault(name)));
        return defines;
    }

    // The macro set for GLSL injection, environment macros ONLY; option values are already applied in place to the sources (OptiFine/Iris semantics), so injecting them redefines the pack's #define lines ("macro redefined") and force-defines names like SuperDuperVanilla's VERTEX/FRAGMENT stage guards
    public Map<String, String> getEnvironmentDefines() {
        Map<String, String> macros = ShaderMacros.standard();

        // OptiFine's ShaderMacros: two shaders.properties switches exposed to GLSL so a pack adapts its lighting to the setting; both default true, matching Shaders.isOldHandLight()/isOldLighting()
        if (this.properties.getOldHandLight().orElse(Boolean.TRUE)) {
            macros.put("MC_OLD_HAND_LIGHT", "");
        }
        if (this.properties.getOldLighting().orElse(Boolean.TRUE)) {
            macros.put("MC_OLD_LIGHTING", "");
        }
        // `supportsColorCorrection` means the pack converts to the output colour space itself, so Umbra hands it the COLOR_SPACE_* enumeration to compare `currentColorSpace` against
        if (this.properties.getSupportsColorCorrection().orElse(Boolean.FALSE)) {
            for (ColorSpaceConverter.ColorSpace space : ColorSpaceConverter.ColorSpace.values()) {
                macros.put("COLOR_SPACE_" + space.name(), Integer.toString(space.ordinal()));
            }
        }
        return macros;
    }

    // Programs the currently selected profile turns off
    private Set<String> activeProfileDisabledPrograms(ShaderProperties parsedProperties) {
        if (parsedProperties.getProfiles().isEmpty()) {
            return Collections.emptySet();
        }
        try {
            ProfileSet profileSet = ProfileSet.fromTree(
                    new LinkedHashMap<>(parsedProperties.getProfiles()),
                    this.shaderPackOptions.getOptionSet());
            ProfileSet.ProfileResult result = profileSet.scan(
                    this.shaderPackOptions.getOptionSet(),
                    this.shaderPackOptions.getOptionValues());
            if (!result.current.isPresent()) {
                return Collections.emptySet();
            }
            Profile current = result.current.get();
            return new LinkedHashSet<>(current.disabledPrograms);
        } catch (RuntimeException e) {
            LOGGER.warn("[Umbra] Failed to parse shader pack profiles; profile program disables ignored", e);
            return Collections.emptySet();
        }
    }

    // The pack's parsed block, item and entity ID maps
    public IdMap getIdMap() {
        return this.idMap;
    }

    // texture.noise as resolved data, or null when the pack keeps the generated noisetex
    public CustomTextureData getCustomNoiseTexture() {
        return this.customNoiseTexture;
    }

    // The stage-scoped custom-texture overrides: stage, then sampler name, then the resolved data
    public Map<TextureStage, Map<String, CustomTextureData>> getCustomTextureDataMap() {
        return Collections.unmodifiableMap(this.customTextureDataMap);
    }

    // The stage-independent customTexture.<name> definitions: sampler name to resolved data, live in every stage
    public Map<String, CustomTextureData> getUmbraCustomTextureDataMap() {
        return Collections.unmodifiableMap(this.irisCustomTextureDataMap);
    }

    // The option set and current values
    public ShaderPackOptions getShaderPackOptions() {
        return this.shaderPackOptions;
    }

    // Parsed shaders.properties
    public ShaderProperties getProperties() {
        return this.properties;
    }

    // Every program source, by id
    public ProgramSet getProgramSet() {
        return this.baseProgramSet;
    }

    // Whether the pack ASKED for a feature, distinct from whether this port provides it; a pack that never opted into SEPARATE_HARDWARE_SAMPLERS expects shadowtex0/1 to carry hardware comparison themselves
    public boolean hasFeature(FeatureFlags feature) {
        return this.activeFeatures.contains(feature);
    }

    // Every text file, by path
    public Map<AbsolutePackPath, String> getSources() {
        return this.sources;
    }

    // Reads each program's stages, applying includes and option edits
    private ProgramSet buildProgramSet() {
        ProgramSet set = new ProgramSet(this.properties);

        for (ProgramId id : ProgramId.VALUES) {
            ProgramSource source = readProgram(id.getSourceName());
            if (source != null) {
                set.put(id, source);
            }
        }

        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            ProgramSource[] arr = new ProgramSource[arrayId.getNumPrograms()];
            boolean any = false;
            for (int i = 0; i < arr.length; i++) {
                ProgramSource source = readProgram(arrayId.getSourceName(i));
                if (source != null) {
                    arr[i] = source;
                    any = true;
                }
            }
            if (any) {
                set.putArray(arrayId, arr);
            }
        }

        return set;
    }

    // Reads and flattens one program's stages by source name; null when the pack has neither a raster nor a compute stage for it, distinguishing an unshipped program from a failed parse
    private ProgramSource readProgram(String sourceName) {
        String vertex = readStage(sourceName, "vsh");
        String fragment = readStage(sourceName, "fsh");
        String[] computes = readComputeVariants(sourceName);
        if (vertex == null && fragment == null && computes.length == 0) {
            return null;
        }
        String geometry = readStage(sourceName, "gsh");
        String tessControl = readStage(sourceName, "tcs");
        String tessEval = readStage(sourceName, "tes");
        return new ProgramSource(sourceName, vertex, geometry, tessControl, tessEval, fragment, computes);
    }

    // Reads a program's compute stages, <name>.csh plus _a through _z (Iris extension); the letter scan STOPS at the first missing suffix like Iris's readComputeArray since the suffixes are an ordered chain, returning empty or a 27-entry array that may hold nulls
    private String[] readComputeVariants(String sourceName) {
        String[] computes = new String[ProgramSource.MAX_COMPUTE_VARIANTS];
        computes[0] = readStage(sourceName, "csh");
        boolean any = computes[0] != null;

        for (int variant = 1; variant < computes.length; variant++) {
            computes[variant] = readStage(ProgramSource.computeVariantName(sourceName, variant), "csh");
            if (computes[variant] == null) {
                break;
            }
            any = true;
        }

        return any ? computes : new String[0];
    }

    // One stage file, or null when the pack has none
    private String readStage(String sourceName, String extension) {
        AbsolutePackPath path = locateStage(sourceName, extension);
        if (path == null) {
            return null;
        }
        return String.join("\n", this.includeProcessor.process(path));
    }

    // Looks in the overworld override directory FIRST and the pack root second, so a world0/ program shadows the root one
    private AbsolutePackPath locateStage(String sourceName, String extension) {
        AbsolutePackPath overworld = AbsolutePackPath.fromAbsolutePath(
                OVERWORLD_DIR + "/" + sourceName + "." + extension);
        if (this.sources.containsKey(overworld)) {
            return overworld;
        }
        AbsolutePackPath root = AbsolutePackPath.fromAbsolutePath("/" + sourceName + "." + extension);
        return this.sources.containsKey(root) ? root : null;
    }
}
