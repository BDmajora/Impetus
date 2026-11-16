package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.NamespacedId;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

// Folders of pack programs by dimension.
public final class DimensionFolders {
    // Properties path.
    static final AbsolutePackPath PROPERTIES_PATH = AbsolutePackPath.fromAbsolutePath("/dimension.properties");
    // Id that matches any dimension.
    private static final NamespacedId ANY = new NamespacedId("*", "*");
    // Prefix of dimension keys.
    private static final String KEY_PREFIX = "dimension.";
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Folders that exist in the pack.
    private final Set<String> present;
    private final Map<NamespacedId, String> byName = new HashMap<>();
    // Flag set when the pack declares its dimensions.
    private final boolean declared;

    // Creates a dimension folders from its present and declared.
    private DimensionFolders(Set<String> present, boolean declared) {
        this.present = present;
        this.declared = declared;
    }

    // Creates from.
    public static DimensionFolders from(Map<AbsolutePackPath, String> sources, Map<String, String> defines) {
        Set<String> present = new HashSet<>();
        for (AbsolutePackPath path : sources.keySet()) {
            String file = path.getPathString();
            int slash = file.indexOf('/', 1);
            if (slash > 1) {
                present.add(file.substring(1, slash));
            }
        }

        Properties properties = new Properties();
        String contents = sources.get(PROPERTIES_PATH);
        if (contents != null) {
            try {
                properties.load(new StringReader(PropertiesPreprocessor.preprocess(contents, defines)));
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.warn("[Umbra] Could not read dimension.properties; using the default world folders: {}", e.getMessage());
                properties.clear();
            }
        }

        DimensionFolders folders = new DimensionFolders(present, !properties.isEmpty());
        if (folders.declared) {
            properties.forEach((key, value) -> {
                String name = (String) key;
                if (!name.startsWith(KEY_PREFIX)) {
                    return;
                }
                String folder = name.substring(KEY_PREFIX.length());
                for (String id : ((String) value).trim().split("\\s+")) {
                    if (!id.isEmpty()) {
                        folders.byName.put(id.equals("*") ? ANY : new NamespacedId(id), folder);
                    }
                }
            });
        } else {
            folders.defaultTo("world0", new NamespacedId("minecraft", "overworld"));
            folders.defaultTo("world0", ANY);
            folders.defaultTo("world-1", new NamespacedId("minecraft", "the_nether"));
            folders.defaultTo("world1", new NamespacedId("minecraft", "the_end"));
        }
        return folders;
    }

    // Defaults the to.
    private void defaultTo(String folder, NamespacedId id) {
        if (this.present.contains(folder)) {
            this.byName.putIfAbsent(id, folder);
        }
    }

    // Returns the base folder or null.
    public String baseFolder() {
        return existing(this.byName.get(ANY));
    }

    // Folders the for.
    public String folderFor(int dimensionId, String dimensionName) {
        String mapped = this.byName.get(new NamespacedId(dimensionName));
        if (mapped != null) {
            if (this.present.contains(mapped)) {
                return mapped;
            }
            LOGGER.error("[Umbra] dimension.properties maps {} to folder {}, which the pack does not have", dimensionName, mapped);
        }
        if (!this.declared) {
            String numbered = existing("world" + dimensionId);
            if (numbered != null) {
                return numbered;
            }
        }
        return null;
    }

    // Returns a folder when it exists or null.
    private String existing(String folder) {
        return folder != null && this.present.contains(folder) ? folder : null;
    }
}
