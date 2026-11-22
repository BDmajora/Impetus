package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
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

    // Returns a minimal class file.
    private static byte[] classFile() {
        return new net.bytebuddy.ByteBuddy(net.bytebuddy.ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name("com.example.ExampleMod")
                .make()
                .getBytes();
    }
}
