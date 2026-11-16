package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Arrays;

// Helpers that read and seed chunk light data.
public final class ChunkLightHelper {
    // Lowest light section.
    public static final int MIN_LIGHT_SECTION = -1;
    // Highest light section.
    public static final int MAX_LIGHT_SECTION = 16;
    // Number of light sections.
    public static final int LIGHT_SECTIONS = 18;

    // Prevents instantiation
    private ChunkLightHelper() {
    }

    // Copies vanilla sky light into the nibbles.
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

    // Copies vanilla block light into the nibbles.
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

    // Wraps vanilla block light arrays in nibbles.
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

    // Wraps vanilla sky light arrays in nibbles.
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

    // Synchronizes the sky to vanilla.
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
                    Arrays.fill(vanillaData, (byte) 0);
                }
            }
        }
    }

    // Synchronizes the block to vanilla.
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

    // Copies engine light into the vanilla arrays.
    public static void fillVanillaFromEngine(SWMRNibbleArray[] skyNibbles, SWMRNibbleArray[] blockNibbles,
                                             // Storage section being read.
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

    // Returns the block light.
    public static int getBlockLight(SWMRNibbleArray[] block, int x, int y, int z) {
        int sectionY = y >> 4;
        if (sectionY < MIN_LIGHT_SECTION || sectionY > MAX_LIGHT_SECTION || block == null) {
            return 0;
        }
        SWMRNibbleArray nibble = block[sectionY + 1];
        return nibble == null ? 0 : nibble.getVisible((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    // Returns the sky light.
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
        if (nibble.isUninitialisedVisible()) {
            return 0;
        }
        return nibble.getVisible(x, y, z);
    }

    // Creates the null nibbles.
    public static SWMRNibbleArray[] newNullNibbles() {
        SWMRNibbleArray[] nibbles = new SWMRNibbleArray[LIGHT_SECTIONS];
        for (int i = 0; i < LIGHT_SECTIONS; ++i) {
            nibbles[i] = new SWMRNibbleArray(null, true);
        }
        return nibbles;
    }
}
