package com.bdmajora.impetus.umbra.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

// Settings of the Umbra shader loader.
public final class UmbraConfig {
    // Name that selects no pack.
    public static final String NO_PACK = "(internal)";

    // Shaderpacks directory.
    private final Path shaderpacksDirectory;
    // Nvidium shaderpacks directory.
    private final Path nvidiumShaderpacksDirectory;
    // Properties file.
    private final Path propertiesFile;

    // Shader pack name or NO_PACK while shaders are off.
    private String shaderPackName = NO_PACK;
    // Pack used last.
    private String lastShaderPackName = NO_PACK;

    // Creates an umbra config from its game directory.
    public UmbraConfig(Path gameDirectory) {
        this.shaderpacksDirectory = gameDirectory.resolve("shaderpacks");
        this.nvidiumShaderpacksDirectory = gameDirectory.resolve("nvidium_shaderpacks");
        this.propertiesFile = gameDirectory.resolve("optionsshaders.txt");
    }

    // Returns the shaderpacks directory.
    public Path getShaderpacksDirectory() {
        return this.shaderpacksDirectory;
    }

    // Returns the folder name of the mesh terrain port of a pack.
    public static String portName(String packName) {
        return packName.toLowerCase(Locale.ROOT).endsWith(".zip") ? packName.substring(0, packName.length() - 4) : packName;
    }

    // Returns the path of the mesh terrain port of a pack.
    public Path portPath(String packName) {
        return this.nvidiumShaderpacksDirectory.resolve(portName(packName));
    }

    // Returns the shader pack name.
    public String getShaderPackName() {
        return this.shaderPackName;
    }

    // Stores the shader pack name and remembers a real pack as the last one.
    public void setShaderPackName(String name) {
        this.shaderPackName = normalize(name);
        rememberShaderPackName(this.shaderPackName);
    }

    // Returns the pack that switching shaders back on would load.
    public String getLastShaderPackName() {
        return this.lastShaderPackName;
    }

    // Remembers a pack for the next switch on and ignores NO_PACK and blank names.
    public void rememberShaderPackName(String name) {
        String pack = normalize(name);
        if (!NO_PACK.equals(pack)) {
            this.lastShaderPackName = pack;
        }
    }

    // Turns shaders off or back on with the last pack and returns whether the selection changed.
    public boolean setShadersEnabled(boolean enabled) {
        if (enabled == isShaderPackEnabled()) {
            return false;
        }
        if (!enabled) {
            this.shaderPackName = NO_PACK;
            return true;
        }
        if (NO_PACK.equals(this.lastShaderPackName)) {
            return false;
        }
        this.shaderPackName = this.lastShaderPackName;
        return true;
    }

    // Returns whether a pack folder or zip of that name is in the shaderpacks directory.
    public boolean isPackInstalled(String name) {
        String pack = normalize(name);
        return !NO_PACK.equals(pack) && Files.exists(this.shaderpacksDirectory.resolve(pack));
    }

    // Trims a pack name and turns null and blank into NO_PACK.
    private static String normalize(String name) {
        return (name == null || name.trim().isEmpty()) ? NO_PACK : name.trim();
    }

    // Returns whether a shader pack is selected and switched on.
    public boolean isShaderPackEnabled() {
        return !NO_PACK.equals(this.shaderPackName);
    }

    // Returns the selected pack path.
    public Path getSelectedPackPath() {
        return isShaderPackEnabled() ? this.shaderpacksDirectory.resolve(this.shaderPackName) : null;
    }

    // Reads the settings file.
    public void load() throws IOException {
        if (!Files.exists(this.propertiesFile)) {
            return;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(this.propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        setShaderPackName(properties.getProperty("shaderPack", NO_PACK));
        // Writes the last pack while shaders are off.
        if (!isShaderPackEnabled()) {
            rememberShaderPackName(properties.getProperty("lastShaderPack"));
        }
        com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter.setColorSpace(
                com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter.ColorSpace.byName(
                        properties.getProperty("colorSpace", "SRGB")));
    }

    // Writes the settings file.
    public void save() throws IOException {
        Files.createDirectories(this.propertiesFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("shaderPack", this.shaderPackName);
        if (!NO_PACK.equals(this.lastShaderPackName)) {
            properties.setProperty("lastShaderPack", this.lastShaderPackName);
        }
        properties.setProperty("colorSpace",
                com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter.getColorSpace().name());
        try (var writer = Files.newBufferedWriter(this.propertiesFile, StandardCharsets.UTF_8)) {
            properties.store(writer, "Impetus/Umbra shader configuration");
        }
    }

    // Creates the shader pack folder when missing.
    public void ensureShaderpacksDirectory() throws IOException {
        Files.createDirectories(this.shaderpacksDirectory);
    }

    // Returns the names of the installed packs.
    public List<String> listShaderpacks() {
        List<String> result = new ArrayList<>();
        if (!Files.isDirectory(this.shaderpacksDirectory)) {
            return result;
        }
        try (Stream<Path> entries = Files.list(this.shaderpacksDirectory)) {
            entries.forEach(path -> {
                String name = path.getFileName().toString();
                if (Files.isDirectory(path) || name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    result.add(name);
                }
            });
        } catch (IOException e) {
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(result);
    }
}
