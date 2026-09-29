package com.bdmajora.coarctatio.mixin.nbt;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.nbt.NbtPrimitivePool;
import com.bdmajora.coarctatio.nbt.TagMap;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NbtMixinsTest {
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
    void aCompoundGetsTheCompactBackingMap() {
        NBTTagCompoundMixin compound = Mixins.instance(NBTTagCompoundMixin.class);
        Mixins.call(compound, "coarctatio$compactBackingMap", Mixins.ci());
        assertInstanceOf(TagMap.class, Mixins.get(compound, "tagMap"));
    }

    @Test
    void everyNumericTagEnteringACompoundIsPooled() {
        NBTTagCompoundPoolMixin compound = Mixins.instance(NBTTagCompoundPoolMixin.class);
        assertSame(NbtPrimitivePool.of(5), Mixins.call(compound, "coarctatio$poolNumeric", new NBTTagInt(5)));
        // Anything that is not a small numeric passes straight through
        NBTTagString text = new NBTTagString("keep");
        assertSame(text, Mixins.call(compound, "coarctatio$poolNumeric", text));
        assertEquals("plain", Mixins.call(compound, "coarctatio$poolNumeric", "plain"));
    }

    @Test
    void listsPoolWhatIsAddedAndWhatReplacesIt() {
        NBTTagListPoolMixin list = Mixins.instance(NBTTagListPoolMixin.class);
        assertSame(NbtPrimitivePool.of((byte) 1), Mixins.call(list, "coarctatio$poolAdded", new net.minecraft.nbt.NBTTagByte((byte) 1)));
        assertSame(NbtPrimitivePool.of(2L), Mixins.call(list, "coarctatio$poolSet", new net.minecraft.nbt.NBTTagLong(2L)));
        Object notATag = new Object();
        assertSame(notATag, Mixins.call(list, "coarctatio$poolAdded", notATag));
        assertSame(notATag, Mixins.call(list, "coarctatio$poolSet", notATag));
    }
}
