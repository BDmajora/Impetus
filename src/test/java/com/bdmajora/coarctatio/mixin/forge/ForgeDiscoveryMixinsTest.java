package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.coarctatio.launch.discovery.ModScanCache;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgeDiscoveryMixinsTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void pointAtTheTempGameDirectory() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
    }

    @Test
    void everyStringAScanKeepsIsInterned() {
        ASMDataMixin data = Mixins.instance(ASMDataMixin.class);
        String annotation = StringPool.LOADER.deduplicate("Lnet/minecraftforge/fml/common/Mod;");
        String owner = StringPool.LOADER.deduplicate("com/example/ExampleMod");
        String member = StringPool.LOADER.deduplicate("modid");
        Mixins.set(data, "annotationName", new String(annotation));
        Mixins.set(data, "className", new String(owner));
        Mixins.set(data, "objectName", new String(member));

        Mixins.call(data, "coarctatio$internStrings", Mixins.ci());
        assertSame(annotation, Mixins.get(data, "annotationName"));
        assertSame(owner, Mixins.get(data, "className"));
        assertSame(member, Mixins.get(data, "objectName"));
    }

    @Test
    void aCandidatesPackagesAreAddedOnceAndItsClassNamesInterned() {
        ModCandidateMixin candidate = Mixins.instance(ModCandidateMixin.class);
        Mixins.set(candidate, "coarctatio$seenPackages", new HashSet<String>());
        String pooled = StringPool.LOADER.deduplicate("com.example");

        Set<Object> classes = new HashSet<>();
        Mc.Recorded<Boolean> addClass = Mc.operation(true);
        assertTrue((boolean) Mixins.call(candidate, "coarctatio$internClassEntry",
                classes, new String("com.example.ExampleMod"), addClass));
        assertSame(StringPool.LOADER.deduplicate("com.example.ExampleMod"), addClass.calls.get(0)[1]);

        List<Object> packages = new ArrayList<>();
        Mc.Recorded<Boolean> addPackage = Mc.operation(true);
        assertTrue((boolean) Mixins.call(candidate, "coarctatio$addPackageOnce", packages, new String("com.example"), addPackage));
        assertSame(pooled, addPackage.calls.get(0)[1]);
        // The second class in the same package does not append it again
        assertFalse((boolean) Mixins.call(candidate, "coarctatio$addPackageOnce", packages, new String("com.example"), addPackage));
        assertEquals(1, addPackage.count());
    }

    @Test
    void aRegistryNameThatCarriesItsNamespaceIsReturnedAsGiven() {
        CallbackInfoReturnable<ResourceLocation> cir = Mixins.cir();
        assertNotNull(Mixins.instance(GameDataMixin.class));
        Mixins.call(GameDataMixin.class, "coarctatio$quietPrefixCheck", "example:thing", true, cir);
        assertTrue(cir.isCancelled());
        assertEquals(new ResourceLocation("example", "thing"), cir.getReturnValue());

        // A bare name takes the active mod's id, and minecraft when there is none
        CallbackInfoReturnable<ResourceLocation> bare = Mixins.cir();
        Mixins.call(GameDataMixin.class, "coarctatio$quietPrefixCheck", "thing", true, bare);
        assertEquals(new ResourceLocation("minecraft", "thing"), bare.getReturnValue());

        // The namespace is lower-cased, as vanilla does
        CallbackInfoReturnable<ResourceLocation> upper = Mixins.cir();
        Mixins.call(GameDataMixin.class, "coarctatio$quietPrefixCheck", "Example:Thing", false, upper);
        assertEquals("example", upper.getReturnValue().getNamespace());
        // The path is lower-cased with it, which is what vanilla's own check does
        assertEquals("thing", upper.getReturnValue().getPath());
    }

    @Test
    void aScannedJarIsServedFromTheCacheOnTheNextLaunch() throws Exception {
        File jar = Files.createFile(dir.resolve("example.jar")).toFile();
        ModCandidate candidate = mock(ModCandidate.class);
        when(candidate.getModContainer()).thenReturn(jar);

        JarDiscovererMixin discoverer = Mixins.instance(JarDiscovererMixin.class);
        Mixins.call(discoverer, "coarctatio$openRecord", candidate, mock(ASMDataTable.class), Mixins.cir());
        Map<String, byte[]> record = Mixins.get(discoverer, "coarctatio$record");
        assertNotNull(record);

        // The entry name is caught on the way past, so the parser knows which class it is building
        JarFile jarFile = mock(JarFile.class);
        ZipEntry entry = new ZipEntry("com/example/ExampleMod.class");
        InputStream classBytes = new ByteArrayInputStream(classFile());
        when(jarFile.getInputStream(entry)).thenReturn(classBytes);
        assertSame(classBytes, Mixins.call(discoverer, "coarctatio$rememberEntry", jarFile, entry));
        assertEquals("com/example/ExampleMod.class", Mixins.get(discoverer, "coarctatio$entryName"));

        // First pass: the class is parsed and its result recorded
        ASMModParser scanned = Mixins.call(discoverer, "coarctatio$parserFromCache", new ByteArrayInputStream(classFile()));
        assertNotNull(scanned);
        assertNotNull(ModScanCache.encode(scanned), "the scan result has to be encodable");
        assertTrue(record.containsKey("com/example/ExampleMod.class"));

        // Second pass: the recorded result is rebuilt and the stream left unread
        InputStream untouched = new ByteArrayInputStream(classFile());
        ASMModParser served = Mixins.call(discoverer, "coarctatio$parserFromCache", untouched);
        assertNotNull(served);
        assertEquals(classFile().length, untouched.available());

        // With no record, or no entry name, every class is parsed as before
        Mixins.set(discoverer, "coarctatio$record", null);
        assertNotNull(Mixins.call(discoverer, "coarctatio$parserFromCache", new ByteArrayInputStream(classFile())));
        Mixins.set(discoverer, "coarctatio$record", new HashMap<String, byte[]>());
        Mixins.set(discoverer, "coarctatio$entryName", null);
        assertNotNull(Mixins.call(discoverer, "coarctatio$parserFromCache", new ByteArrayInputStream(classFile())));
    }

    @Test
    void theCacheIsWrittenOnceDiscoveryIsOver() {
        ModDiscovererMixin discoverer = Mixins.instance(ModDiscovererMixin.class);
        ModScanCache.recordFor("jar|1|2").put("com/example/A.class", new byte[] {1});
        Mixins.call(discoverer, "coarctatio$saveScanCache", Mixins.cir(new ArrayList<ModContainer>()));
    }

    // A minimal class file the ASM the discoverer bundles can read, which this suite's own classes are too new for
    private static byte[] classFile() {
        return new net.bytebuddy.ByteBuddy(net.bytebuddy.ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name("com.example.ExampleMod")
                .make()
                .getBytes();
    }
}
