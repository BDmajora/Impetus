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

// Which pack folder a dimension's programs come from, as Iris decides it: dimension.properties when the pack ships one (dimension.<folder> = <ids>, * for any other), else world0 for the overworld and any other dimension, world-1 for the Nether and world1 for the End; without the file, OptiFine's world<N> also serves dimension N, which is how 1.12.2 packs reach modded dimensions
public final class DimensionFolders {
    static final AbsolutePackPath PROPERTIES_PATH = AbsolutePackPath.fromAbsolutePath("/dimension.properties");
    private static final NamespacedId ANY = new NamespacedId("*", "*");
    private static final String KEY_PREFIX = "dimension.";
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Folders holding at least one file, by name without slashes
    private final Set<String> present;
    private final Map<NamespacedId, String> byName = new HashMap<>();
    private final boolean declared;

    private DimensionFolders(Set<String> present, boolean declared) {
        this.present = present;
        this.declared = declared;
    }

    // Reads the mapping from the pack's files; the properties file is preprocessed with the same defines as shaders.properties, since packs gate folders on their own options
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

    private void defaultTo(String folder, NamespacedId id) {
        if (this.present.contains(folder)) {
            this.byName.putIfAbsent(id, folder);
        }
    }

    // The folder the base program set reads ahead of the pack root, null for the root alone
    public String baseFolder() {
        return existing(this.byName.get(ANY));
    }

    // The folder for this dimension, null meaning the base set; a mapped folder the pack does not have is logged and ignored like Iris does
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

    private String existing(String folder) {
        return folder != null && this.present.contains(folder) ? folder : null;
    }
}
