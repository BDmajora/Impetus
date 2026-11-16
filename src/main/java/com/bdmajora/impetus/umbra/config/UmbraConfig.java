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

    // Shader pack name.
    private String shaderPackName = NO_PACK;

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

    // Stores the shader pack name.
    public void setShaderPackName(String name) {
        this.shaderPackName = (name == null || name.trim().isEmpty()) ? NO_PACK : name.trim();
    }

    // Returns whether shader pack enabled.
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
        com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter.setColorSpace(
                com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter.ColorSpace.byName(
                        properties.getProperty("colorSpace", "SRGB")));
    }

    // Writes the settings file.
    public void save() throws IOException {
        Files.createDirectories(this.propertiesFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("shaderPack", this.shaderPackName);
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
