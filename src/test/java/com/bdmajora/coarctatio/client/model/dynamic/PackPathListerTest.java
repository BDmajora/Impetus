package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.mixin.client.model.dynamic.AbstractResourcePackAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.FallbackResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.LegacyV2AdapterAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.SimpleReloadableResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.resources.FileResourcePackMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackPathListerTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void theManagersPacksAreListedInOrderAndWithoutRepeats() {
        IResourcePack shared = mock(IResourcePack.class);
        IResourcePack own = mock(IResourcePack.class);

        FallbackResourceManager minecraft = Mc.mock(FallbackResourceManager.class);
        when(((FallbackResourceManagerAccessor) minecraft).coarctatio$packs())
                .thenReturn(new ArrayList<>(Arrays.asList(shared, own)));
        FallbackResourceManager example = Mc.mock(FallbackResourceManager.class);
        when(((FallbackResourceManagerAccessor) example).coarctatio$packs())
                .thenReturn(new ArrayList<>(Arrays.asList(shared)));

        Map<String, FallbackResourceManager> domains = new LinkedHashMap<>();
        domains.put("minecraft", minecraft);
        domains.put("example", example);
        SimpleReloadableResourceManager manager = Mc.mock(SimpleReloadableResourceManager.class);
        when(((SimpleReloadableResourceManagerAccessor) manager).coarctatio$domainManagers()).thenReturn(domains);

        List<IResourcePack> packs = PackPathLister.packsOf(manager);
        assertEquals(Arrays.asList(shared, own), packs);
        assertNotNull(Mixins.construct(PackPathLister.class));
    }

    @Test
    void anIndexedPackHandsBackTheIndexTheExistenceCacheBuilt() {
        FileResourcePackMixin pack = Mixins.instance(FileResourcePackMixin.class);
        Mixins.set(pack, "coarctatio$entries", Set.of("assets/minecraft/textures/blocks/stone.png"));
        Collection<String> paths = PackPathLister.paths((IResourcePack) pack);
        assertTrue(paths.contains("assets/minecraft/textures/blocks/stone.png"));
    }

    @Test
    void aZipPackIsWalkedWhenTheIndexIsOff() throws Exception {
        Path zipPath = dir.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry("assets/minecraft/sounds.json"));
            zip.closeEntry();
        }

        FileResourcePackMixin pack = Mixins.instance(FileResourcePackMixin.class);
        // No index built, and none possible, so the zip itself is listed
        Mixins.set(pack, "coarctatio$entriesFailed", true);
        Mixins.set(pack, "resourcePackFile", zipPath.toFile());

        Collection<String> paths = PackPathLister.paths((IResourcePack) pack);
        assertEquals(List.of("assets/minecraft/sounds.json"), new ArrayList<>(paths));
        ((ZipFile) Mixins.get(pack, "resourcePackZipFile")).close();

        // A pack whose zip cannot be opened contributes nothing rather than failing the scan
        FileResourcePackMixin broken = Mixins.instance(FileResourcePackMixin.class);
        Mixins.set(broken, "coarctatio$entriesFailed", true);
        Mixins.set(broken, "resourcePackFile", dir.resolve("missing.zip").toFile());
        assertTrue(PackPathLister.paths((IResourcePack) broken).isEmpty());
    }

    @Test
    void aFolderPackIsIndexedAndAnAdapterListsWhatItWraps() throws Exception {
        Files.createDirectories(dir.resolve("assets/minecraft"));
        Files.writeString(dir.resolve("assets/minecraft/sounds.json"), "{}");

        FolderResourcePack folder = Mc.mock(FolderResourcePack.class, AbstractResourcePackAccessor.class);
        when(((AbstractResourcePackAccessor) folder).coarctatio$file()).thenReturn(dir.toFile());
        Collection<String> paths = PackPathLister.paths(folder);
        assertTrue(paths.contains("assets/minecraft/sounds.json"));

        // A format-2 adapter is listed as the pack it wraps
        LegacyV2Adapter adapter = Mc.mock(LegacyV2Adapter.class);
        when(((LegacyV2AdapterAccessor) adapter).coarctatio$pack()).thenReturn(folder);
        assertTrue(PackPathLister.paths(adapter).contains("assets/minecraft/sounds.json"));
    }

    @Test
    void theVanillaPackIsReadFromTheClasspathAndAnUnknownKindIsSkipped() {
        // In a workspace the vanilla assets are a plain folder rather than a jar, which is refused and logged
        Collection<String> vanilla = PackPathLister.paths(Mc.mock(DefaultResourcePack.class));
        assertTrue(vanilla.stream().allMatch(path -> path.startsWith("assets/")));

        // Anything else says so once and contributes nothing
        IResourcePack unknown = mock(IResourcePack.class);
        when(unknown.getPackName()).thenReturn("a mod's own pack");
        assertTrue(PackPathLister.paths(unknown).isEmpty());
    }

    @Test
    void aJarOnTheClasspathIsWalkedUnderItsAssetsFolder() throws Exception {
        Path jar = dir.resolve("assets.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("assets/minecraft/lang/en_us.lang"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("com/example/Mod.class"));
            zip.closeEntry();
        }

        Set<String> paths = new java.util.HashSet<>();
        Mixins.call(PackPathLister.class, "walkZipUri", new java.net.URI("jar:" + jar.toUri()), paths);
        // Only what lives under assets, spelled with forward slashes relative to the pack root
        assertEquals(Set.of("assets/minecraft/lang/en_us.lang"), paths);

        // A jar with no assets folder at all contributes nothing
        Path empty = dir.resolve("empty.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(empty))) {
            zip.putNextEntry(new ZipEntry("com/example/Mod.class"));
            zip.closeEntry();
        }
        Set<String> none = new java.util.HashSet<>();
        Mixins.call(PackPathLister.class, "walkZipUri", new java.net.URI("jar:" + empty.toUri()), none);
        assertTrue(none.isEmpty());
    }
}
