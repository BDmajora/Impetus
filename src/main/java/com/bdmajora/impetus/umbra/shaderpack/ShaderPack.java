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

// Parsed shader pack with its options and properties and program sets and textures.
public final class ShaderPack {
    // Path of the pack properties file.
    public static final AbsolutePackPath PROPERTIES_PATH = AbsolutePackPath.fromAbsolutePath("/shaders.properties");

    // Pattern of a guard that targets a newer game version on non Iris loaders.
    private static final Pattern LEGACY_BRANCH_PATTERN = Pattern.compile(
            "!\\s*defined\\s*\\(?\\s*IS_IRIS\\s*\\)?\\s*&&\\s*MC_VERSION\\s*<\\s*(\\d+)");
    // Extension of a mesh stage.
    public static final String MESH_EXTENSION = "mesh";
    // Extension of the fragment stage paired with a mesh stage.
    public static final String MESH_FRAGMENT_EXTENSION = "mesh.fsh";
    // Extensions of the classic program stages.
    private static final Set<String> STAGE_EXTENSIONS =
            new HashSet<>(Arrays.asList("vsh", "fsh", "gsh", "csh", "tcs", "tes"));

    // Logger of the shader pipeline.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Text sources by pack path.
    private final Map<AbsolutePackPath, String> sources;
    // Binary files by pack path.
    private final Map<AbsolutePackPath, byte[]> binaries;
    // Options of the pack with their current values.
    private final ShaderPackOptions shaderPackOptions;
    // Resolver of include lines over the edited sources.
    private final IncludeProcessor includeProcessor;
    // Parsed pack properties.
    private final ShaderProperties properties;
    // Program set of the base folder.
    private final ProgramSet baseProgramSet;
    // Folders of each dimension.
    private final DimensionFolders dimensionFolders;
    // Program sets of dimension folders by folder.
    private final Map<String, ProgramSet> dimensionProgramSets = new HashMap<>();
    // Program set of the current dimension.
    private volatile ProgramSet activeProgramSet;
    // Features the pack declares.
    private final Set<FeatureFlags> activeFeatures;
    // Block and item and entity id mappings of the pack.
    private final IdMap idMap;

    // Noise texture of the pack or null.
    private final CustomTextureData customNoiseTexture;
    // Custom textures by stage and sampler.
    private final Map<TextureStage, Map<String, CustomTextureData>> customTextureDataMap =
            new EnumMap<>(TextureStage.class);
    // Named custom images of the pack.
    private final Map<String, CustomTextureData> irisCustomTextureDataMap = new LinkedHashMap<>();

    // Creates a pack with default option values.
    public ShaderPack(Map<AbsolutePackPath, String> sources) {
        this(sources, Collections.emptyMap());
    }

    // Creates a pack with changed option values and no binaries.
    public ShaderPack(Map<AbsolutePackPath, String> sources, Map<String, String> changedConfigs) {
        this(sources, changedConfigs, Collections.emptyMap());
    }

    // Parses the options and properties and programs and textures of a pack.
    public ShaderPack(Map<AbsolutePackPath, String> sources, Map<String, String> changedConfigs,
                      Map<AbsolutePackPath, byte[]> binaries) {
        this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
        this.binaries = Collections.unmodifiableMap(new HashMap<>(binaries));

        Map<AbsolutePackPath, String> optionSources = new HashMap<>(this.sources);
        optionSources.remove(PROPERTIES_PATH);
        this.shaderPackOptions = new ShaderPackOptions(optionSources, changedConfigs);

        Map<String, String> propertiesDefines = getShaderDefines();

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

        this.activeFeatures = FeatureFlags.parseDeclared(
                this.properties.getRaw().get("iris.features.required"),
                this.properties.getRaw().get("iris.features.optional"));
        List<String> unsupportedRequired = FeatureFlags.findUnsupported(this.properties.getRaw().get("iris.features.required"));
        if (!unsupportedRequired.isEmpty()) {
            String missing = String.join(", ", unsupportedRequired);
            String reason = FeatureFlags.describeUnsupported(unsupportedRequired);
            LOGGER.error("[Umbra] This shader pack requires features {}: {}", reason, missing);
            ImpetusNotifications.warn(
                    "Shader pack may not work correctly",
                    "Requires features " + reason + ":",
                    missing);
        }

        Map<AbsolutePackPath, String> flattenSources = new HashMap<>(this.sources);
        flattenSources.putAll(this.shaderPackOptions.getEditedSources());
        this.includeProcessor = new IncludeProcessor(flattenSources);

        this.dimensionFolders = DimensionFolders.from(this.sources, propertiesDefines);
        this.baseProgramSet = buildProgramSet(this.dimensionFolders.baseFolder(), true);
        this.activeProgramSet = this.baseProgramSet;

        this.idMap = new IdMap(this.sources, propertiesDefines);

        this.customNoiseTexture = this.properties.getNoiseTexturePath().map(this::readTextureOrNull).orElse(null);

        this.properties.getCustomTextures().forEach((stage, texturePropertiesMap) -> {
            Map<String, CustomTextureData> innerCustomTextureDataMap = new LinkedHashMap<>();
            texturePropertiesMap.forEach((samplerName, path) -> putTextureIfReadable(innerCustomTextureDataMap, samplerName, path));
            this.customTextureDataMap.put(stage, innerCustomTextureDataMap);
        });

        this.properties.getUmbraCustomTextures().forEach((name, path) -> putTextureIfReadable(this.irisCustomTextureDataMap, name, path));

        ShaderMacros.setPackLegacyPrograms(detectLegacyPrograms(this.sources));
        CustomTextureTransformer.setActivePatches(
                this.properties.getCustomTexturePatches());
    }

    // Returns the programs that guard code behind a newer game version.
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
                if (version > ShaderMacros.MC_VERSION) {
                    legacy.add(name);
                    break;
                }
            }
        }
        return legacy;
    }

    // Returns the program name of a stage file or null.
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

    // Reads a custom texture and logs a failure as null.
    private CustomTextureData readTextureOrNull(String path) {
        try {
            return readTexture(path);
        } catch (IOException e) {
            LOGGER.error("[Umbra] Unable to read the custom texture at {}: {}", path, e.getMessage());
            return null;
        }
    }

    // Adds a custom texture to a map when it reads.
    private void putTextureIfReadable(Map<String, CustomTextureData> into, String samplerName, String path) {
        CustomTextureData data = readTextureOrNull(path);
        if (data != null) {
            into.put(samplerName, data);
        }
    }

    // Reads a raw or resource or pack file texture.
    private CustomTextureData readTexture(String path) throws IOException {
        String[] rawParts = path.trim().split("\\s+");
        if (rawParts.length > 1) {
            return readRawTexture(rawParts);
        }
        if (path.contains(":")) {
            String[] parts = path.split(":");
            if (parts.length < 2) {
                throw new IOException("Malformed resource location: " + path);
            }
            if (parts.length > 2) {
                LOGGER.warn("[Umbra] Resource location {} contained more than two parts?", path);
            }
            if (parts[0].equals("minecraft")
                    && (parts[1].equals("dynamic/lightmap_1") || parts[1].equals("dynamic/light_map_1"))) {
                return new CustomTextureData.LightmapMarker();
            }
            return new CustomTextureData.ResourceData(parts[0], parts[1]);
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        byte[] content = readBinary(path);

        boolean blur = false;
        boolean clamp = false;
        byte[] mcMeta = this.binaries.get(AbsolutePackPath.fromAbsolutePath("/" + path + ".mcmeta"));
        if (mcMeta != null) {
            try {
                JsonObject meta = JsonParser.parseString(new String(mcMeta, StandardCharsets.UTF_8)).getAsJsonObject();
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

    // Reads a raw two or three dimensional texture definition.
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

    // Returns the bytes of a pack file and fails when it is missing.
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

    // Parses a positive dimension of a raw texture.
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

    // Returns the standard macros with the pack option values.
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

    // Returns the standard macros with the lighting and color space switches.
    public Map<String, String> getEnvironmentDefines() {
        Map<String, String> macros = ShaderMacros.standard();

        if (this.properties.getOldHandLight().orElse(Boolean.TRUE)) {
            macros.put("MC_OLD_HAND_LIGHT", "");
        }
        if (this.properties.getOldLighting().orElse(Boolean.TRUE)) {
            macros.put("MC_OLD_LIGHTING", "");
        }
        if (this.properties.getSupportsColorCorrection().orElse(Boolean.FALSE)) {
            for (ColorSpaceConverter.ColorSpace space : ColorSpaceConverter.ColorSpace.values()) {
                macros.put("COLOR_SPACE_" + space.name(), Integer.toString(space.ordinal()));
            }
        }
        return macros;
    }

    // Returns the programs that the current profile disables.
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

    // Returns the id mappings.
    public IdMap getIdMap() {
        return this.idMap;
    }

    // Returns the noise texture or null.
    public CustomTextureData getCustomNoiseTexture() {
        return this.customNoiseTexture;
    }

    // Returns the custom textures by stage.
    public Map<TextureStage, Map<String, CustomTextureData>> getCustomTextureDataMap() {
        return Collections.unmodifiableMap(this.customTextureDataMap);
    }

    // Returns the named custom images.
    public Map<String, CustomTextureData> getUmbraCustomTextureDataMap() {
        return Collections.unmodifiableMap(this.irisCustomTextureDataMap);
    }

    // Returns the pack options.
    public ShaderPackOptions getShaderPackOptions() {
        return this.shaderPackOptions;
    }

    // Returns the pack properties.
    public ShaderProperties getProperties() {
        return this.properties;
    }

    // Returns the program set of the current dimension.
    public ProgramSet getProgramSet() {
        return this.activeProgramSet;
    }

    // Switches to the program set of a dimension and returns whether it changed.
    public boolean selectDimension(int dimensionId, String dimensionName) {
        String folder = this.dimensionFolders.folderFor(dimensionId, dimensionName);
        ProgramSet selected = folder == null || folder.equals(this.dimensionFolders.baseFolder())
                ? this.baseProgramSet
                : this.dimensionProgramSets.computeIfAbsent(folder, dir -> buildProgramSet(dir, false));
        boolean changed = selected != this.activeProgramSet;
        this.activeProgramSet = selected;
        return changed;
    }

    // Returns whether the pack declares a feature.
    public boolean hasFeature(FeatureFlags feature) {
        return this.activeFeatures.contains(feature);
    }

    // Returns the text sources.
    public Map<AbsolutePackPath, String> getSources() {
        return this.sources;
    }

    // Reads every program of a folder into a program set.
    private ProgramSet buildProgramSet(String folder, boolean rootFallback) {
        ProgramSet set = new ProgramSet(this.properties);
        StageLocator stages = new StageLocator(folder == null ? null : "/" + folder, rootFallback || folder == null);

        for (ProgramId id : ProgramId.VALUES) {
            ProgramSource source = readProgram(stages, id.getSourceName());
            if (source != null) {
                set.put(id, source);
            }
        }

        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            ProgramSource[] arr = new ProgramSource[arrayId.getNumPrograms()];
            boolean any = false;
            for (int i = 0; i < arr.length; i++) {
                ProgramSource source = readProgram(stages, arrayId.getSourceName(i));
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

    // Reads the stages of one program or null when it has none.
    private ProgramSource readProgram(StageLocator stages, String sourceName) {
        String vertex = readStage(stages, sourceName, "vsh");
        String fragment = readStage(stages, sourceName, "fsh");
        String[] computes = readComputeVariants(stages, sourceName);
        if (vertex == null && fragment == null && computes.length == 0) {
            return null;
        }
        String geometry = readStage(stages, sourceName, "gsh");
        boolean tessellation = this.activeFeatures.contains(FeatureFlags.TESSELLATION_SHADERS);
        String tessControl = tessellation ? readStage(stages, sourceName, "tcs") : null;
        String tessEval = tessellation ? readStage(stages, sourceName, "tes") : null;
        String mesh = readStage(stages, sourceName, MESH_EXTENSION);
        String meshFragment = readStage(stages, sourceName, MESH_FRAGMENT_EXTENSION);
        return new ProgramSource(sourceName, vertex, geometry, tessControl, tessEval, fragment, computes, mesh, meshFragment);
    }

    // Reads the compute stage and its numbered variants.
    private String[] readComputeVariants(StageLocator stages, String sourceName) {
        String[] computes = new String[ProgramSource.MAX_COMPUTE_VARIANTS];
        computes[0] = readStage(stages, sourceName, "csh");
        boolean any = computes[0] != null;

        for (int variant = 1; variant < computes.length; variant++) {
            computes[variant] = readStage(stages, ProgramSource.computeVariantName(sourceName, variant), "csh");
            if (computes[variant] == null) {
                break;
            }
            any = true;
        }

        return any ? computes : new String[0];
    }

    // Reads one stage with its includes resolved or null.
    private String readStage(StageLocator stages, String sourceName, String extension) {
        AbsolutePackPath path = stages.locate(sourceName + "." + extension);
        if (path == null) {
            return null;
        }
        return String.join("\n", this.includeProcessor.process(path));
    }

    // Finder of stage files in a folder with an optional root fallback.
    private final class StageLocator {
        // Folder searched first or null.
        private final String folder;
        // Flag set when the pack root is searched after the folder.
        private final boolean root;

        // Creates a finder for a folder.
        StageLocator(String folder, boolean root) {
            this.folder = folder;
            this.root = root;
        }

        // Returns the path of a stage file or null.
        AbsolutePackPath locate(String file) {
            if (this.folder != null) {
                AbsolutePackPath inFolder = AbsolutePackPath.fromAbsolutePath(this.folder + "/" + file);
                if (ShaderPack.this.sources.containsKey(inFolder)) {
                    return inFolder;
                }
            }
            if (!this.root) {
                return null;
            }
            AbsolutePackPath atRoot = AbsolutePackPath.fromAbsolutePath("/" + file);
            return ShaderPack.this.sources.containsKey(atRoot) ? atRoot : null;
        }
    }
}
