package com.bdmajora.fulgor.async;

import com.bdmajora.testing.Mc;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkLightHelperTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    private static ExtendedBlockStorage[] sections(boolean hasSky) {
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        sections[4] = new ExtendedBlockStorage(64, hasSky);
        return sections;
    }

    @Test
    void vanillaLightIsImportedCopiedOrWrapped() {
        ExtendedBlockStorage[] storage = sections(true);
        storage[4].getSkyLight().set(1, 2, 3, 11);
        storage[4].getBlockLight().set(1, 2, 3, 7);

        SWMRNibbleArray[] sky = ChunkLightHelper.newNullNibbles();
        SWMRNibbleArray[] block = ChunkLightHelper.newNullNibbles();
        ChunkLightHelper.importVanillaSky(sky, storage);
        ChunkLightHelper.importVanillaBlock(block, storage);
        assertEquals(11, sky[5].getVisible(1, 2, 3));
        assertEquals(7, block[5].getVisible(1, 2, 3));
        // Importing copies, so a later vanilla write is not seen
        storage[4].getSkyLight().set(1, 2, 3, 0);
        assertEquals(11, sky[5].getVisible(1, 2, 3));
        storage[4].getSkyLight().set(1, 2, 3, 11);

        // Wrapping shares the array, so the engine writes straight into what the renderer reads
        SWMRNibbleArray[] wrappedSky = ChunkLightHelper.newNullNibbles();
        SWMRNibbleArray[] wrappedBlock = ChunkLightHelper.newNullNibbles();
        ChunkLightHelper.wrapVanillaSky(wrappedSky, storage);
        ChunkLightHelper.wrapVanillaBlock(wrappedBlock, storage);
        assertSame(storage[4].getSkyLight().getData(), wrappedSky[5].getVisibleData());
        assertSame(storage[4].getBlockLight().getData(), wrappedBlock[5].getVisibleData());

        // A section with no sky storage contributes nothing to the sky arrays
        ExtendedBlockStorage[] skyless = sections(false);
        SWMRNibbleArray[] none = ChunkLightHelper.newNullNibbles();
        ChunkLightHelper.importVanillaSky(none, skyless);
        ChunkLightHelper.wrapVanillaSky(none, skyless);
        assertTrue(none[5].isNullNibbleVisible());
    }

    @Test
    void publishingWritesBackIntoTheVanillaArrays() {
        ExtendedBlockStorage[] storage = sections(true);
        SWMRNibbleArray[] sky = ChunkLightHelper.newNullNibbles();
        SWMRNibbleArray[] block = ChunkLightHelper.newNullNibbles();
        sky[5] = new SWMRNibbleArray();
        block[5] = new SWMRNibbleArray();
        sky[5].set(1, 2, 3, 14);
        block[5].set(1, 2, 3, 6);
        sky[5].updateVisible();
        block[5].updateVisible();
        ChunkLightHelper.syncSkyToVanilla(sky, storage);
        ChunkLightHelper.syncBlockToVanilla(block, storage);
        assertEquals(14, storage[4].getSkyLight().get(1, 2, 3));
        assertEquals(6, storage[4].getBlockLight().get(1, 2, 3));

        // An all-zero section clears the vanilla array rather than leaving stale light behind
        sky[5].setUninitialised();
        block[5].setUninitialised();
        sky[5].updateVisible();
        block[5].updateVisible();
        ChunkLightHelper.syncSkyToVanilla(sky, storage);
        ChunkLightHelper.syncBlockToVanilla(block, storage);
        assertEquals(0, storage[4].getSkyLight().get(1, 2, 3));
        assertEquals(0, storage[4].getBlockLight().get(1, 2, 3));
        // A null sky nibble leaves the vanilla data alone, since the column fill already approximates open sky
        storage[4].getSkyLight().set(1, 2, 3, 15);
        sky[5] = new SWMRNibbleArray(null, true);
        ChunkLightHelper.syncSkyToVanilla(sky, storage);
        assertEquals(15, storage[4].getSkyLight().get(1, 2, 3));
        // Missing sections and missing nibbles are skipped
        ChunkLightHelper.syncSkyToVanilla(ChunkLightHelper.newNullNibbles(), storage);
        ChunkLightHelper.syncBlockToVanilla(ChunkLightHelper.newNullNibbles(), storage);
        ChunkLightHelper.syncSkyToVanilla(sky, sections(false));
        ChunkLightHelper.syncBlockToVanilla(block, new ExtendedBlockStorage[16]);
    }

    @Test
    void aSectionAddedAfterLightingIsFilledFromTheEngine() {
        SWMRNibbleArray[] sky = ChunkLightHelper.newNullNibbles();
        SWMRNibbleArray[] block = ChunkLightHelper.newNullNibbles();
        sky[5] = new SWMRNibbleArray();
        block[5] = new SWMRNibbleArray();
        for (int y = 0; y < 16; y++) {
            sky[5].set(0, y, 0, 15);
            block[5].set(0, y, 0, 3);
        }
        sky[5].updateVisible();
        block[5].updateVisible();
        ExtendedBlockStorage section = new ExtendedBlockStorage(64, true);
        ChunkLightHelper.fillVanillaFromEngine(sky, block, section, 4, true);
        assertEquals(15, section.getSkyLight().get(0, 0, 0));
        assertEquals(3, section.getBlockLight().get(0, 0, 0));
        assertEquals(0, section.getBlockLight().get(1, 0, 0));
        // Without sky storage only the block half is filled
        ExtendedBlockStorage skyless = new ExtendedBlockStorage(64, false);
        ChunkLightHelper.fillVanillaFromEngine(sky, block, skyless, 4, false);
        assertEquals(3, skyless.getBlockLight().get(0, 0, 0));
        assertNull(skyless.getSkyLight());
    }

    @Test
    void lightReadsFallBackTheWayTheColumnDoes() {
        SWMRNibbleArray[] block = ChunkLightHelper.newNullNibbles();
        assertEquals(0, ChunkLightHelper.getBlockLight(block, 0, 64, 0));
        assertEquals(0, ChunkLightHelper.getBlockLight(null, 0, 64, 0));
        assertEquals(0, ChunkLightHelper.getBlockLight(block, 0, 300, 0));
        assertEquals(0, ChunkLightHelper.getBlockLight(block, 0, -20, 0));
        block[5] = new SWMRNibbleArray();
        block[5].set(1, 2, 3, 9);
        block[5].updateVisible();
        assertEquals(9, ChunkLightHelper.getBlockLight(block, 1, 66, 3));

        SWMRNibbleArray[] sky = ChunkLightHelper.newNullNibbles();
        // Above the world is full daylight, below it is dark, and an all-null column reads as open sky
        assertEquals(15, ChunkLightHelper.getSkyLight(sky, 0, 300, 0));
        assertEquals(0, ChunkLightHelper.getSkyLight(sky, 0, -20, 0));
        assertEquals(15, ChunkLightHelper.getSkyLight(null, 0, 64, 0));
        assertEquals(15, ChunkLightHelper.getSkyLight(sky, 0, 64, 0));
        // A null section borrows the bottom layer of the first real section above it
        sky[6] = new SWMRNibbleArray();
        sky[6].set(1, 0, 3, 12);
        sky[6].updateVisible();
        assertEquals(12, ChunkLightHelper.getSkyLight(sky, 1, 64, 3));
        // An uninitialised section above means the column is dark there
        sky[6].setUninitialised();
        sky[6].updateVisible();
        assertEquals(0, ChunkLightHelper.getSkyLight(sky, 1, 64, 3));
        // A section of its own answers directly, and uninitialised means present and dark
        sky[5] = new SWMRNibbleArray();
        sky[5].set(1, 2, 3, 7);
        sky[5].updateVisible();
        assertEquals(7, ChunkLightHelper.getSkyLight(sky, 1, 66, 3));
        sky[5].setUninitialised();
        sky[5].updateVisible();
        assertEquals(0, ChunkLightHelper.getSkyLight(sky, 1, 66, 3));
    }
}
