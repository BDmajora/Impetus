package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.AbstractResourcePackAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.FallbackResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.FileResourcePackAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.LegacyV2AdapterAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.SimpleReloadableResourceManagerAccessor;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipFile;

// Every path a resource pack holds, the way 1.19.3+'s texture loading lists a pack's folders; the file and folder packs hand back the index the existence cache built, everything else is walked here
public final class PackPathLister {
    private PackPathLister() {
    }

    // The packs behind every domain, in the manager's own order and without repeats
    public static List<IResourcePack> packsOf(SimpleReloadableResourceManager manager) {
        Set<IResourcePack> packs = new LinkedHashSet<>();
        for (FallbackResourceManager domain : ((SimpleReloadableResourceManagerAccessor) manager).coarctatio$domainManagers().values()) {
            packs.addAll(((FallbackResourceManagerAccessor) domain).coarctatio$packs());
        }
        return new ArrayList<>(packs);
    }

    // Paths are relative to the pack root with forward slashes ("assets/mod/textures/x.png"); a pack of a kind this cannot list contributes nothing, and says so once
    public static Collection<String> paths(IResourcePack pack) {
        if (pack instanceof IndexedResourcePack) {
            Set<String> index = ((IndexedResourcePack) pack).coarctatio$indexedPaths();
            if (index != null) {
                return index;
            }
        }
        if (pack instanceof LegacyV2Adapter) {
            return paths(((LegacyV2AdapterAccessor) pack).coarctatio$pack());
        }
        try {
            if (pack instanceof DefaultResourcePack) {
                return classpathAssets();
            }
            if (pack instanceof FileResourcePack) {
                return zipEntries(((FileResourcePackAccessor) pack).coarctatio$zipFile());
            }
            if (pack instanceof FolderResourcePack) {
                return ResourceLookupCaches.indexFolder(((AbstractResourcePackAccessor) pack).coarctatio$file());
            }
        } catch (IOException e) {
            Coarctatio.LOGGER.error("Could not list resources of pack {}", pack.getPackName(), e);
            return Collections.emptyList();
        }
        Coarctatio.LOGGER.warn("Cannot list resources of pack {} ({})", pack.getPackName(), pack.getClass().getName());
        return Collections.emptyList();
    }

    private static Collection<String> zipEntries(ZipFile zip) {
        List<String> paths = new ArrayList<>(zip.size());
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            paths.add(entries.nextElement().getName());
        }
        return paths;
    }

    // The vanilla pack reads from the classpath, so its contents are the assets folder of the client jar and of every jar on the launch class loader; synchronised because opening the same zip filesystem from two threads fails
    private static synchronized Collection<String> classpathAssets() throws IOException {
        Set<String> paths = new HashSet<>();
        URI root;
        try {
            root = Objects.requireNonNull(DefaultResourcePack.class.getResource("/assets/.mcassetsroot"), "vanilla assets root").toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Couldn't list vanilla resources", e);
        }
        if (!"jar".equals(root.getScheme())) {
            throw new IOException("Vanilla assets are not in a jar: " + root);
        }
        walkZipUri(root, paths);
        ClassLoader loader = DefaultResourcePack.class.getClassLoader();
        if (loader instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) loader).getURLs()) {
                // Only real files can be opened as zip filesystems; asmgen and similar synthetic protocols are skipped
                if (!"file".equals(url.getProtocol())) {
                    continue;
                }
                URI uri = null;
                try {
                    uri = new URI("jar:" + url.toString());
                    walkZipUri(uri, paths);
                } catch (IOException | URISyntaxException | RuntimeException | ZipError e) {
                    Coarctatio.LOGGER.debug("Skipping classpath entry {}: {}", uri != null ? uri : url, e.toString());
                }
            }
        }
        return paths;
    }

    // Adds every file under /assets of the zip at uri; a filesystem this call opened is closed again, one another holder opened is left alone
    private static void walkZipUri(URI uri, Set<String> into) throws IOException {
        FileSystem fs;
        boolean opened = false;
        try {
            fs = FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException e) {
            fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
            opened = true;
        }
        try {
            Path assets = fs.getPath("/assets");
            if (!Files.isDirectory(assets)) {
                return;
            }
            try (Stream<Path> stream = Files.walk(assets)) {
                into.addAll(stream.filter(Files::isRegularFile)
                        .map(path -> "assets/" + join(assets.relativize(path)))
                        .collect(Collectors.toList()));
            }
        } finally {
            if (opened) {
                fs.close();
            }
        }
    }

    // Path.toString on a zip filesystem already uses '/', but joining the names is separator-proof for folder walks too
    private static String join(Path relative) {
        StringBuilder out = new StringBuilder();
        for (Path part : relative) {
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(part);
        }
        return out.toString();
    }
}
