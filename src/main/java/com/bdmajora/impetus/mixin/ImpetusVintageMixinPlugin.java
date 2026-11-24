package com.bdmajora.impetus.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.core.HbmCompat;
import com.bdmajora.impetus.core.VintageFixCompat;
import com.bdmajora.impetus.engine.impl.util.MixinClassValidator;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Class impetus vintage mixin plugin.
public class ImpetusVintageMixinPlugin implements IMixinConfigPlugin {
    // Logger of the mixin plugin.
    public static final Logger LOGGER = LogManager.getLogger("ImpetusMixins");

    // Claims the chunk renderer for VintageFix before any config, VintageFix's included, lists its mixins.
    @Override
    public void onLoad(String mixinPackage) {
        VintageFixCompat.claimChunkRenderer();
        // Every config is queued and none is prepared yet while the first one loads its plugin.
        HbmCompat.withdrawTerrainHook();
    }

    // Returns the ref mapper config.
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    // Returns false.
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    // Nothing to negotiate with other configs.
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    // Returns the mixins.
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

    // No rewriting before a mixin applies.
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    // No rewriting after a mixin applies.
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
