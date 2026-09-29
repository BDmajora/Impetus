package com.bdmajora.coarctatio.mixin.util;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocationMixinsTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    @Test
    void bothStringsOfALocationComeFromThePool() {
        String domain = ResourceLocationCaches.DOMAINS.deduplicate("minecraft");
        String path = ResourceLocationCaches.PATHS.deduplicate("blocks/stone");

        ResourceLocationMixin location = Mixins.instance(ResourceLocationMixin.class);
        Mixins.set(location, "namespace", new String("minecraft"));
        Mixins.set(location, "path", new String("blocks/stone"));
        Mixins.call(location, "coarctatio$internComponents", 0, new String[0], Mixins.ci());
        assertSame(domain, Mixins.get(location, "namespace"));
        assertSame(path, Mixins.get(location, "path"));
    }

    @Test
    void theVariantOfAModelLocationIsInternedWhole() {
        ModelCaches.open();
        String variant = ModelCaches.VARIANTS.deduplicate("inventory");

        ModelResourceLocationMixin location = Mixins.instance(ModelResourceLocationMixin.class);
        Mixins.set(location, "variant", new String("inventory"));
        Mixins.call(location, "coarctatio$internVariant", 0, new String[0], Mixins.ci());
        assertSame(variant, Mixins.get(location, "variant"));
    }
}
