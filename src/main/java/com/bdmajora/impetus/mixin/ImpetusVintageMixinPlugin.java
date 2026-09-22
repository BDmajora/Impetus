package com.bdmajora.impetus.mixin;

import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.engine.impl.util.MixinClassValidator;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import com.bdmajora.impetus.core.ImpetusLwjgl3ifyCompat;

import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImpetusVintageMixinPlugin implements IMixinConfigPlugin {
    public static final Logger LOGGER = LogManager.getLogger("ImpetusMixins");

    // Applies lwjgl3ify compat if RetroFuturaBootstrap is present; absence is the normal stock-Forge case
    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("com.gtnewhorizons.retrofuturabootstrap.SharedConfig");
            // class exists, apply compat
            ImpetusLwjgl3ifyCompat.apply();
        } catch (Throwable e) {
            LOGGER.warn("RFB class not found, hopefully we're not running with lwjgl3ify, otherwise bad things are about to happen");
        }
    }

    // Empty: Impetus reobfuscates mixins directly rather than via a refmap
    @Override
    public String getRefMapperConfig() {
        return "";
    }

    // Unused; getMixins supplies the list directly and Mixin does not consult this for those
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    // Nothing to negotiate with other configs
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    // Discovers every class under com.bdmajora.impetus.mixin by walking the jar or classes dir, so new mixins need no listing
    @Override
    public List<String> getMixins() {
        List<Path> rootPaths = new ArrayList<>();

        rootPaths.addAll(Stream.of("com.bdmajora.impetus.mixin")
                .flatMap(str -> {
                    URL url = ImpetusVintageMixinPlugin.class.getResource("/" + str.replace('.', '/'));
                    if (url == null) {
                        return Stream.empty();
                    }
                    try {
                        return Stream.of(Paths.get(url.toURI()));
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList()));

        if (rootPaths.isEmpty()) {
            try {
                URI uri = Objects.requireNonNull(ImpetusVintageMixinPlugin.class.getResource("/mixins.impetus.json")).toURI();
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException var11) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                rootPaths.add(fs.getPath("com", "bdmajora", "impetus", "mixin").toAbsolutePath());
            } catch(Exception e) {
                LOGGER.error("Error finding mixins", e);
            }
        }

        Set<String> possibleMixinClasses = new HashSet<>();

        for (Path rootPath : rootPaths) {
            possibleMixinClasses.addAll(MixinClassValidator.scanMixinFolder(rootPath));
        }
        if (possibleMixinClasses.isEmpty()) {
            throw new IllegalStateException("Found no mixin classes, something went very wrong");
        }
        return new ArrayList<>(possibleMixinClasses);
    }

    // No pre-apply rewriting needed
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    // No post-apply rewriting needed
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
