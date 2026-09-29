package com.bdmajora.coarctatio.mixin.world;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.template.Template;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WorldMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void aLoadedChunksTagIsStrippedToWhatEntityLoadingStillReads() {
        AnvilChunkLoaderMixin loader = Mixins.concrete(AnvilChunkLoaderMixin.class);
        NBTTagCompound level = new NBTTagCompound();
        level.setTag("Entities", new NBTTagList());
        level.setTag("TileEntities", new NBTTagList());
        level.setTag("Sections", new NBTTagList());
        level.setString("LastUpdate", "kept out");
        NBTTagCompound original = new NBTTagCompound();
        original.setTag("Level", level);

        Object[] result = {mock(Chunk.class), original};
        Mixins.call(loader, "coarctatio$stripPendingChunkNbt",
                mock(World.class), 0, 0, original, Mixins.cir(result));

        NBTTagCompound stripped = (NBTTagCompound) result[1];
        assertNotSame(original, stripped);
        NBTTagCompound strippedLevel = stripped.getCompoundTag("Level");
        assertTrue(strippedLevel.hasKey("Entities", 9));
        assertTrue(strippedLevel.hasKey("TileEntities", 9));
        // The sections were copied into the chunk already, and everything else is dead weight
        assertFalse(strippedLevel.hasKey("Sections"));
        assertFalse(strippedLevel.hasKey("LastUpdate"));
        assertNotNull(loader);
    }

    @Test
    void aRejectedOrOddlyShapedChunkIsLeftAlone() {
        AnvilChunkLoaderMixin loader = Mixins.concrete(AnvilChunkLoaderMixin.class);
        NBTTagCompound level = new NBTTagCompound();
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("Level", level);

        // A rejected chunk has no result at all
        Mixins.call(loader, "coarctatio$stripPendingChunkNbt",
                mock(World.class), 0, 0, compound, Mixins.cir((Object[]) null));
        // So does one whose result is not the pair the loader produces
        Mixins.call(loader, "coarctatio$stripPendingChunkNbt",
                mock(World.class), 0, 0, compound, Mixins.cir(new Object[] {mock(Chunk.class)}));
        Object[] notATag = {mock(Chunk.class), "unexpected"};
        Mixins.call(loader, "coarctatio$stripPendingChunkNbt",
                mock(World.class), 0, 0, compound, Mixins.cir(notATag));
        assertEquals("unexpected", notATag[1]);

        // And a tag with no level compound has nothing to strip
        NBTTagCompound levelless = new NBTTagCompound();
        levelless.setTag("NotLevel", new NBTTagString("x"));
        Object[] kept = {mock(Chunk.class), levelless};
        Mixins.call(loader, "coarctatio$stripPendingChunkNbt",
                mock(World.class), 0, 0, levelless, Mixins.cir(kept));
        assertSame(levelless, kept[1]);
    }

    @Test
    void anEmptySectionWhoseLightCanBeRecomputedIsDropped() {
        ChunkMixin chunk = Mixins.concrete(ChunkMixin.class);
        ExtendedBlockStorage empty = new ExtendedBlockStorage(0, true);
        ExtendedBlockStorage litSky = new ExtendedBlockStorage(16, true);
        // Full daylight throughout, which is exactly what the fallback would give back
        java.util.Arrays.fill(litSky.getSkyLight().getData(), (byte) 0xFF);
        ExtendedBlockStorage patchySky = new ExtendedBlockStorage(32, true);
        patchySky.getSkyLight().set(0, 0, 0, 7);
        ExtendedBlockStorage litBlocks = new ExtendedBlockStorage(48, true);
        litBlocks.getBlockLight().set(0, 0, 0, 9);
        ExtendedBlockStorage occupied = new ExtendedBlockStorage(64, true);
        occupied.set(0, 0, 0, Blocks.STONE.getDefaultState());
        ExtendedBlockStorage noSky = new ExtendedBlockStorage(80, false);

        ExtendedBlockStorage[] sections = {empty, litSky, patchySky, litBlocks, occupied, noSky, null};
        Mixins.call(chunk, "coarctatio$dropEmptySections", sections, Mixins.ci());

        // Dark and fully lit sections both reproduce from the fallback, so they go
        assertSame(Chunk.NULL_BLOCK_STORAGE, sections[0]);
        assertSame(Chunk.NULL_BLOCK_STORAGE, sections[1]);
        assertSame(Chunk.NULL_BLOCK_STORAGE, sections[5]);
        // Half-lit skylight and any block light would be lost, and a section with blocks is not empty
        assertSame(patchySky, sections[2]);
        assertSame(litBlocks, sections[3]);
        assertSame(occupied, sections[4]);
        assertNull(sections[6]);

        // No array at all is nothing to do
        Mixins.call(chunk, "coarctatio$dropEmptySections", (ExtendedBlockStorage[]) null, Mixins.ci());
        assertNotNull(chunk);
        // A missing nibble array contradicts nothing, so it counts as uniform
        assertTrue((boolean) Mixins.call(ChunkMixin.class, "coarctatio$isUniform", (NibbleArray) null, 0));
    }

    @Test
    void structureTemplatesAreHeldSoftly() {
        TemplateManagerMixin manager = Mixins.instance(TemplateManagerMixin.class);
        Map<String, Template> templates = new HashMap<>();
        Mixins.set(manager, "templates", templates);
        Mixins.call(manager, "coarctatio$softTemplates", Mixins.ci());

        Map<String, Template> soft = Mixins.get(manager, "templates");
        assertNotSame(templates, soft);
        // Still a working map; what changed is that world gen can move past a template and let it go
        Template template = new Template();
        soft.put("village/house", template);
        assertSame(template, soft.get("village/house"));
    }
}
