package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Arrays;

// Moves light between the vanilla nibble arrays and the SWMR arrays; sections run -1..16 in the SWMR arrays (index = sectionY + 1) and 0..15 in vanilla storage
public final class ChunkLightHelper {
    public static final int MIN_LIGHT_SECTION = -1;
    public static final int MAX_LIGHT_SECTION = 16;
    public static final int LIGHT_SECTIONS = 18;

    private ChunkLightHelper() {
    }

    // Copies each stored section's vanilla skylight into a fresh SWMR array
    public static void importVanillaSky(SWMRNibbleArray[] sky, ExtendedBlockStorage[] storageArrays) {
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            ExtendedBlockStorage section = storageArrays[sectionY];
            if (section == null) {
                continue;
            }
            NibbleArray vanilla = section.getSkyLight();
            if (vanilla != null) {
                sky[sectionY + 1] = SWMRNibbleArray.fromVanilla(vanilla);
            }
        }
    }

    // Copies each stored section's vanilla block light into a fresh SWMR array
    public static void importVanillaBlock(SWMRNibbleArray[] block, ExtendedBlockStorage[] storageArrays) {
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            ExtendedBlockStorage section = storageArrays[sectionY];
            if (section == null) {
                continue;
            }
            NibbleArray vanilla = section.getBlockLight();
            if (vanilla != null) {
                block[sectionY + 1] = SWMRNibbleArray.fromVanilla(vanilla);
            }
        }
    }

    // Wraps the vanilla arrays without copying, so a publish lands straight in what the renderer reads; client only, where every write is on the main thread
    public static void wrapVanillaBlock(SWMRNibbleArray[] block, ExtendedBlockStorage[] storageArrays) {
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            ExtendedBlockStorage section = storageArrays[sectionY];
            if (section == null) {
                continue;
            }
            NibbleArray vanilla = section.getBlockLight();
            if (vanilla != null) {
                block[sectionY + 1] = new SWMRNibbleArray(vanilla.getData());
            }
        }
    }

    public static void wrapVanillaSky(SWMRNibbleArray[] sky, ExtendedBlockStorage[] storageArrays) {
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            ExtendedBlockStorage section = storageArrays[sectionY];
            if (section == null) {
                continue;
            }
            NibbleArray vanilla = section.getSkyLight();
            if (vanilla != null) {
                sky[sectionY + 1] = new SWMRNibbleArray(vanilla.getData());
            }
        }
    }

    // Publishes the visible sky data into the vanilla arrays, under each nibble's monitor since updateVisible mutates the visible array in place
    public static void syncSkyToVanilla(SWMRNibbleArray[] skyNibbles, ExtendedBlockStorage[] storageArrays) {
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            SWMRNibbleArray nibble = skyNibbles[sectionY + 1];
            ExtendedBlockStorage section = storageArrays[sectionY];
            if (nibble == null || section == null) {
                continue;
            }
            NibbleArray vanilla = section.getSkyLight();
            if (vanilla == null) {
                continue;
            }
            byte[] vanillaData = vanilla.getData();
            synchronized (nibble) {
                byte[] data = nibble.getVisibleData();
                if (data != null) {
                    if (data != vanillaData) {
                        System.arraycopy(data, 0, vanillaData, 0, SWMRNibbleArray.ARRAY_SIZE);
                    }
                } else if (nibble.isUninitialisedVisible()) {
                    // UNINIT means present and all zero; filling 0xFF here lit every enclosed cave section
                    Arrays.fill(vanillaData, (byte) 0);
                }
                // NULL leaves the vanilla array alone: the column fill already approximates open sky
            }
        }
    }

    public static void syncBlockToVanilla(SWMRNibbleArray[] blockNibbles, ExtendedBlockStorage[] storageArrays) {
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            SWMRNibbleArray nibble = blockNibbles[sectionY + 1];
            ExtendedBlockStorage section = storageArrays[sectionY];
            if (nibble == null || section == null) {
                continue;
            }
            NibbleArray vanilla = section.getBlockLight();
            if (vanilla == null) {
                continue;
            }
            byte[] vanillaData = vanilla.getData();
            synchronized (nibble) {
                byte[] data = nibble.getVisibleData();
                if (data != null) {
                    if (data != vanillaData) {
                        System.arraycopy(data, 0, vanillaData, 0, SWMRNibbleArray.ARRAY_SIZE);
                    }
                } else {
                    Arrays.fill(vanillaData, (byte) 0);
                }
            }
        }
    }

    // Fills a section created after the chunk was lit from the visible SWMR state; nothing else republishes already-visible data into fresh zeroed vanilla arrays, and the section would ship to clients black
    public static void fillVanillaFromEngine(SWMRNibbleArray[] skyNibbles, SWMRNibbleArray[] blockNibbles,
                                             ExtendedBlockStorage section, int sectionY, boolean hasSky) {
        NibbleArray blockArray = section.getBlockLight();
        NibbleArray skyArray = hasSky ? section.getSkyLight() : null;
        int baseY = sectionY << 4;
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    if (blockArray != null) {
                        blockArray.set(x, y, z, getBlockLight(blockNibbles, x, baseY + y, z));
                    }
                    if (skyArray != null) {
                        skyArray.set(x, y, z, getSkyLight(skyNibbles, x, baseY + y, z));
                    }
                }
            }
        }
    }

    // Visible block light at a world position; zero outside the light sections or for a null nibble
    public static int getBlockLight(SWMRNibbleArray[] block, int x, int y, int z) {
        int sectionY = y >> 4;
        if (sectionY < MIN_LIGHT_SECTION || sectionY > MAX_LIGHT_SECTION || block == null) {
            return 0;
        }
        SWMRNibbleArray nibble = block[sectionY + 1];
        return nibble == null ? 0 : nibble.getVisible((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    // Visible skylight at a world position: 15 above the world, 0 below it, and for a null section the bottom layer of the first real section above (the column's shadow), 15 if there is none
    public static int getSkyLight(SWMRNibbleArray[] sky, int x, int y, int z) {
        int sectionY = y >> 4;
        if (sectionY > MAX_LIGHT_SECTION) {
            return 15;
        }
        if (sectionY < MIN_LIGHT_SECTION) {
            return 0;
        }
        if (sky == null) {
            return 15;
        }

        int index = sectionY + 1;
        SWMRNibbleArray nibble = sky[index];
        if (nibble == null || nibble.isNullNibbleVisible()) {
            for (int i = index + 1; i < sky.length; ++i) {
                SWMRNibbleArray above = sky[i];
                if (above == null || above.isNullNibbleVisible()) {
                    continue;
                }
                if (above.isUninitialisedVisible()) {
                    return 0;
                }
                return above.getVisible((x & 15) | ((z & 15) << 4));
            }
            return 15;
        }
        // UNINIT is present and all zero, not unknown
        if (nibble.isUninitialisedVisible()) {
            return 0;
        }
        return nibble.getVisible(x, y, z);
    }

    // A fresh set of NULL nibbles for a new chunk
    public static SWMRNibbleArray[] newNullNibbles() {
        SWMRNibbleArray[] nibbles = new SWMRNibbleArray[LIGHT_SECTIONS];
        for (int i = 0; i < LIGHT_SECTIONS; ++i) {
            nibbles[i] = new SWMRNibbleArray(null, true);
        }
        return nibbles;
    }
}
