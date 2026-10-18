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

// Backed by OptiFine-style optionsshaders.txt plus shaderpacks/, with the Nvidium ports Umbra makes kept in nvidium_shaderpacks/; the game directory is injected rather than read from Minecraft so this stays Minecraft-free and testable
public final class UmbraConfig {
    // OptiFine's sentinel value for "shaders disabled"; empty name also means this
    public static final String NO_PACK = "(internal)";

    private final Path shaderpacksDirectory;
    private final Path nvidiumShaderpacksDirectory;
    private final Path propertiesFile;

    private String shaderPackName = NO_PACK;

    public UmbraConfig(Path gameDirectory) {
        this.shaderpacksDirectory = gameDirectory.resolve("shaderpacks");
        this.nvidiumShaderpacksDirectory = gameDirectory.resolve("nvidium_shaderpacks");
        this.propertiesFile = gameDirectory.resolve("optionsshaders.txt");
    }

    // The shaderpacks folder under the game directory, created on first access
    public Path getShaderpacksDirectory() {
        return this.shaderpacksDirectory;
    }

    // The folder name a pack's port takes: the pack's own name without .zip, since a port is always a folder
    public static String portName(String packName) {
        return packName.toLowerCase(Locale.ROOT).endsWith(".zip") ? packName.substring(0, packName.length() - 4) : packName;
    }

    // Where a pack's Nvidium port goes, a complete pack folder in nvidium_shaderpacks/
    public Path portPath(String packName) {
        return this.nvidiumShaderpacksDirectory.resolve(portName(packName));
    }

    // Returns the currently selected pack name (file or folder name under shaderpacks/)
    public String getShaderPackName() {
        return this.shaderPackName;
    }

    // Records the chosen pack; null means shaders off
    public void setShaderPackName(String name) {
        this.shaderPackName = (name == null || name.trim().isEmpty()) ? NO_PACK : name.trim();
    }

    // True if a real pack is selected, i.e. not the NO_PACK sentinel
    public boolean isShaderPackEnabled() {
        return !NO_PACK.equals(this.shaderPackName);
    }

    // Resolved path to the selected pack (folder or zip); null if shaders are disabled
    public Path getSelectedPackPath() {
        return isShaderPackEnabled() ? this.shaderpacksDirectory.resolve(this.shaderPackName) : null;
    }

    // Missing file is not an error, just means defaults
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

    // Persists the current selection, creating directories as needed
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

    // Ensures shaderpacks/ exists so users have somewhere to drop packs
    public void ensureShaderpacksDirectory() throws IOException {
        Files.createDirectories(this.shaderpacksDirectory);
    }

    // Names of every selectable pack (sub-dirs and .zip files) sorted case-insensitively; an unreadable directory yields an empty list
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
            // Best-effort listing; return whatever we managed to collect.
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(result);
    }
}
