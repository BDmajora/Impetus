package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;

// Persists the SWMR light in the chunk's Level tag so a reloaded chunk skips its initial pass (Starlight's SaveUtil, via Pulsar); a chunk saved mid-relight simply carries no tag and relights on load
public final class AsyncLightStorage {
    // Bumped whenever the layout or the BFS semantics change incompatibly; old data relights once on load
    public static final int LIGHT_VERSION = 1;

    private static final String TAG_ROOT = "FulgorLight";
    private static final String TAG_VERSION = "version";
    private static final String TAG_SECTIONS = "sections";
    private static final String TAG_Y = "y";
    private static final String TAG_BLOCK_STATE = "bs";
    private static final String TAG_BLOCK_DATA = "bd";
    private static final String TAG_SKY_STATE = "ss";
    private static final String TAG_SKY_DATA = "sd";

    private AsyncLightStorage() {
    }

    // Writes the tag only for a chunk whose light is ready and has no queued work that could still change values
    public static void save(Chunk chunk, WorldLightManager manager, NBTTagCompound level) {
        try {
            AsyncLitChunk lit = (AsyncLitChunk) chunk;
            if (!lit.fulgor$isLightReady()) {
                return;
            }
            SWMRNibbleArray[] blockNibbles = lit.fulgor$getBlockNibbles();
            SWMRNibbleArray[] skyNibbles = lit.fulgor$getSkyNibbles();
            if (blockNibbles == null || skyNibbles == null) {
                return;
            }
            // A lava pocket just turned to stone whose removal has not run would freeze phantom light into the save
            if (manager != null && manager.hasPendingLightWork(chunk.x, chunk.z)) {
                return;
            }
            boolean hasSky = chunk.getWorld().provider.hasSkyLight();

            NBTTagList sections = new NBTTagList();
            for (int i = 0; i < ChunkLightHelper.LIGHT_SECTIONS; ++i) {
                // Null entries are legal: the sky engine drops NULL-state nibbles to java nulls before publishing
                SWMRNibbleArray blockNibble = blockNibbles[i];
                SWMRNibbleArray skyNibble = hasSky ? skyNibbles[i] : null;
                SWMRNibbleArray.SaveState blockState = blockNibble == null ? null : blockNibble.getSaveState();
                SWMRNibbleArray.SaveState skyState = skyNibble == null ? null : skyNibble.getSaveState();
                if (blockState == null && skyState == null) {
                    continue;
                }
                NBTTagCompound section = new NBTTagCompound();
                section.setInteger(TAG_Y, i + ChunkLightHelper.MIN_LIGHT_SECTION);
                if (blockState != null) {
                    section.setByte(TAG_BLOCK_STATE, (byte) blockState.state);
                    if (blockState.data != null) {
                        section.setByteArray(TAG_BLOCK_DATA, blockState.data);
                    }
                }
                if (skyState != null) {
                    section.setByte(TAG_SKY_STATE, (byte) skyState.state);
                    if (skyState.data != null) {
                        section.setByteArray(TAG_SKY_DATA, skyState.data);
                    }
                }
                sections.appendTag(section);
            }

            NBTTagCompound root = new NBTTagCompound();
            root.setInteger(TAG_VERSION, LIGHT_VERSION);
            root.setTag(TAG_SECTIONS, sections);
            level.setTag(TAG_ROOT, root);
        } catch (Throwable t) {
            // Not fatal: the chunk relights on next load
            Fulgor.LOGGER.warn("Failed to save light data for chunk ({}, {})", chunk.x, chunk.z, t);
        }
    }

    // Restores the tag into the chunk and marks its saved light valid; anything invalid leaves the flag unset and the chunk relights
    public static void load(Chunk chunk, NBTTagCompound level) {
        try {
            if (!level.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND)) {
                return;
            }
            NBTTagCompound root = level.getCompoundTag(TAG_ROOT);
            if (root.getInteger(TAG_VERSION) != LIGHT_VERSION) {
                return;
            }

            SWMRNibbleArray[] blockNibbles = ChunkLightHelper.newNullNibbles();
            SWMRNibbleArray[] skyNibbles = ChunkLightHelper.newNullNibbles();

            NBTTagList sections = root.getTagList(TAG_SECTIONS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0, len = sections.tagCount(); i < len; ++i) {
                NBTTagCompound section = sections.getCompoundTagAt(i);
                int index = section.getInteger(TAG_Y) - ChunkLightHelper.MIN_LIGHT_SECTION;
                if (index < 0 || index >= ChunkLightHelper.LIGHT_SECTIONS) {
                    throw new IllegalStateException("Light section index out of range: " + section.getInteger(TAG_Y));
                }
                if (section.hasKey(TAG_BLOCK_STATE, Constants.NBT.TAG_BYTE)) {
                    blockNibbles[index] = restoreNibble(section, TAG_BLOCK_STATE, TAG_BLOCK_DATA);
                }
                if (section.hasKey(TAG_SKY_STATE, Constants.NBT.TAG_BYTE)) {
                    skyNibbles[index] = restoreNibble(section, TAG_SKY_STATE, TAG_SKY_DATA);
                }
            }

            AsyncLitChunk lit = (AsyncLitChunk) chunk;
            lit.fulgor$setBlockNibbles(blockNibbles);
            lit.fulgor$setSkyNibbles(skyNibbles);
            lit.fulgor$setSavedLightValid(true);
        } catch (Throwable t) {
            Fulgor.LOGGER.warn("Failed to load light data for chunk ({}, {}); it will be relit", chunk.x, chunk.z, t);
        }
    }

    private static SWMRNibbleArray restoreNibble(NBTTagCompound section, String stateTag, String dataTag) {
        int state = section.getByte(stateTag);
        byte[] raw = section.hasKey(dataTag, Constants.NBT.TAG_BYTE_ARRAY) ? section.getByteArray(dataTag) : null;
        if (raw != null && raw.length != SWMRNibbleArray.ARRAY_SIZE) {
            throw new IllegalStateException("Light nibble of wrong length: " + raw.length);
        }
        // Cloned since the NBT object owns the parsed array
        return new SWMRNibbleArray(raw == null ? null : raw.clone(), state);
    }
}
