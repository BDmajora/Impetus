package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.NibbleArray;

import java.util.ArrayDeque;
import java.util.Arrays;

// Starlight's single-writer multi-reader nibble array (via Pulsar): the worker writes an updating copy and publishes it as the visible copy in one step, so readers on other threads never see a half-written section
public final class SWMRNibbleArray {
    // Section does not exist; reads as zero and is never written
    public static final int INIT_STATE_NULL = 0;
    // Section exists conceptually but is all zero, backing array not allocated
    public static final int INIT_STATE_UNINIT = 1;
    // Section holds light data
    public static final int INIT_STATE_INIT = 2;
    // Initialised but hidden from the vanilla view; block light uses it to keep data around for a decrease that is still propagating
    public static final int INIT_STATE_HIDDEN = 3;

    public static final int ARRAY_SIZE = 16 * 16 * 16 / 2;

    // Per-thread pool of 2048-byte arrays, since a pass allocates and frees a working copy per touched section
    private static final ThreadLocal<ArrayDeque<byte[]>> WORKING_BYTES_POOL = ThreadLocal.withInitial(ArrayDeque::new);

    private int stateUpdating;
    private volatile int stateVisible;
    private byte[] storageUpdating;
    private boolean updatingDirty;
    private volatile byte[] storageVisible;
    // Whole-section shortcuts so edge checks can skip a pair of full or empty sections without reading them
    private boolean fullFlag;
    private boolean zeroFlag;
    private volatile boolean fullFlagVisible;
    private volatile boolean zeroFlagVisible;

    public SWMRNibbleArray() {
        this(null, false);
    }

    public SWMRNibbleArray(byte[] bytes) {
        this(bytes, false);
    }

    public SWMRNibbleArray(byte[] bytes, boolean isNullNibble) {
        if (bytes != null && bytes.length != ARRAY_SIZE) {
            throw new IllegalArgumentException("Data of wrong length: " + bytes.length);
        }
        this.stateVisible = this.stateUpdating = bytes == null
                ? (isNullNibble ? INIT_STATE_NULL : INIT_STATE_UNINIT)
                : INIT_STATE_INIT;
        this.storageUpdating = this.storageVisible = bytes;
        this.zeroFlag = bytes == null && !isNullNibble;
        this.fullFlagVisible = this.fullFlag;
        this.zeroFlagVisible = this.zeroFlag;
    }

    public SWMRNibbleArray(byte[] bytes, int state) {
        if (bytes != null && bytes.length != ARRAY_SIZE) {
            throw new IllegalArgumentException("Data of wrong length: " + bytes.length);
        }
        if (bytes == null && (state == INIT_STATE_INIT || state == INIT_STATE_HIDDEN)) {
            throw new IllegalArgumentException("Data cannot be null and have state be initialised");
        }
        this.stateUpdating = this.stateVisible = state;
        this.storageUpdating = this.storageVisible = bytes;
        this.zeroFlag = bytes == null && state == INIT_STATE_UNINIT;
        this.fullFlagVisible = this.fullFlag;
        this.zeroFlagVisible = this.zeroFlag;
    }

    private static byte[] allocateBytes() {
        byte[] inPool = WORKING_BYTES_POOL.get().pollFirst();
        return inPool != null ? inPool : new byte[ARRAY_SIZE];
    }

    private static void freeBytes(byte[] bytes) {
        WORKING_BYTES_POOL.get().addFirst(bytes);
    }

    // Copies a vanilla array into a fresh SWMR array; a null vanilla array means the section has no light storage at all
    public static SWMRNibbleArray fromVanilla(NibbleArray nibble) {
        if (nibble == null) {
            return new SWMRNibbleArray(null, true);
        }
        byte[] data = nibble.getData();
        return data == null ? new SWMRNibbleArray() : new SWMRNibbleArray(data.clone());
    }

    private static boolean isAllZero(byte[] data) {
        for (int i = 0; i < (ARRAY_SIZE >>> 4); ++i) {
            byte whole = data[i << 4];
            for (int k = 1; k < (1 << 4); ++k) {
                whole |= data[(i << 4) | k];
            }
            if (whole != 0) {
                return false;
            }
        }
        return true;
    }

    // Snapshot of the visible side for saving; an all-zero initialised section saves as UNINIT so the file never carries 2 KB of zeros
    public SaveState getSaveState() {
        synchronized (this) {
            int state = this.stateVisible;
            byte[] data = this.storageVisible;
            if (state == INIT_STATE_NULL) {
                return null;
            }
            if (state == INIT_STATE_UNINIT) {
                return new SaveState(null, state);
            }
            if (isAllZero(data)) {
                return state == INIT_STATE_INIT ? new SaveState(null, INIT_STATE_UNINIT) : null;
            }
            return new SaveState(data.clone(), state);
        }
    }

    // Fills every layer with the bottom layer of the section above, the skylight a column carries down through empty space
    public void extrudeLower(SWMRNibbleArray other) {
        if (other.stateUpdating == INIT_STATE_NULL) {
            throw new IllegalArgumentException();
        }
        if (other.storageUpdating == null) {
            this.setUninitialised();
            return;
        }

        byte[] src = other.storageUpdating;
        byte[] into;
        if (!this.updatingDirty) {
            into = this.storageUpdating = allocateBytes();
            if (this.stateUpdating == INIT_STATE_NULL || this.stateUpdating == INIT_STATE_UNINIT) {
                this.stateUpdating = INIT_STATE_INIT;
            }
            this.updatingDirty = true;
        } else {
            into = this.storageUpdating;
        }

        int end = (15 | (15 << 4)) >>> 1;
        // Index layout is x | z << 4 | y << 8, so one layer is the first 128 bytes
        for (int y = 0; y <= 15; ++y) {
            System.arraycopy(src, 0, into, y << (8 - 1), end + 1);
        }
        this.fullFlag = other.fullFlag;
        this.zeroFlag = other.zeroFlag;
    }

    public void setFull() {
        if (this.stateUpdating != INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
        }
        Arrays.fill(this.storageUpdating == null || !this.updatingDirty
                ? this.storageUpdating = allocateBytes()
                : this.storageUpdating, (byte) -1);
        this.updatingDirty = true;
        this.fullFlag = true;
        this.zeroFlag = false;
    }

    public void setZero() {
        if (this.stateUpdating != INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
        }
        Arrays.fill(this.storageUpdating == null || !this.updatingDirty
                ? this.storageUpdating = allocateBytes()
                : this.storageUpdating, (byte) 0);
        this.updatingDirty = true;
        this.fullFlag = false;
        this.zeroFlag = true;
    }

    public void setNonNull() {
        if (this.stateUpdating == INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
            return;
        }
        if (this.stateUpdating != INIT_STATE_NULL) {
            return;
        }
        this.stateUpdating = INIT_STATE_UNINIT;
    }

    public void setNull() {
        this.stateUpdating = INIT_STATE_NULL;
        if (this.updatingDirty && this.storageUpdating != null) {
            freeBytes(this.storageUpdating);
        }
        this.storageUpdating = null;
        this.updatingDirty = false;
        this.fullFlag = false;
        this.zeroFlag = false;
    }

    public void setUninitialised() {
        this.stateUpdating = INIT_STATE_UNINIT;
        if (this.storageUpdating != null && this.updatingDirty) {
            freeBytes(this.storageUpdating);
        }
        this.storageUpdating = null;
        this.updatingDirty = false;
        this.fullFlag = false;
        this.zeroFlag = true;
    }

    public void setHidden() {
        if (this.stateUpdating == INIT_STATE_HIDDEN) {
            return;
        }
        if (this.stateUpdating != INIT_STATE_INIT) {
            this.setNull();
        } else {
            this.stateUpdating = INIT_STATE_HIDDEN;
        }
    }

    public boolean isDirty() {
        return this.stateUpdating != this.stateVisible || this.updatingDirty;
    }

    public boolean isNullNibbleUpdating() {
        return this.stateUpdating == INIT_STATE_NULL;
    }

    public boolean isNullNibbleVisible() {
        return this.stateVisible == INIT_STATE_NULL;
    }

    public boolean isUninitialisedUpdating() {
        return this.stateUpdating == INIT_STATE_UNINIT;
    }

    public boolean isUninitialisedVisible() {
        return this.stateVisible == INIT_STATE_UNINIT;
    }

    public boolean isInitialisedUpdating() {
        return this.stateUpdating == INIT_STATE_INIT;
    }

    public boolean isInitialisedVisible() {
        return this.stateVisible == INIT_STATE_INIT;
    }

    public boolean isHiddenUpdating() {
        return this.stateUpdating == INIT_STATE_HIDDEN;
    }

    public boolean isFullUpdating() {
        return this.fullFlag;
    }

    public boolean isZeroUpdating() {
        return this.zeroFlag;
    }

    public boolean isFullVisible() {
        return this.fullFlagVisible;
    }

    public boolean isZeroVisible() {
        return this.zeroFlagVisible;
    }

    // First write of a pass: the updating side becomes a private copy so readers keep seeing the old visible array until publish
    private void swapUpdatingAndMarkDirty() {
        if (this.updatingDirty) {
            return;
        }
        if (this.storageUpdating == null) {
            this.storageUpdating = allocateBytes();
            Arrays.fill(this.storageUpdating, (byte) 0);
        } else {
            System.arraycopy(this.storageUpdating, 0, this.storageUpdating = allocateBytes(), 0, ARRAY_SIZE);
        }
        if (this.stateUpdating != INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
        }
        this.updatingDirty = true;
        this.fullFlag = false;
        this.zeroFlag = false;
    }

    // Publishes the updating side; copied in place into the visible array when one exists, so a client whose vanilla nibble shares that array sees the new light without a swap
    public boolean updateVisible() {
        if (!this.isDirty()) {
            return false;
        }
        synchronized (this) {
            if (this.stateUpdating == INIT_STATE_NULL || this.stateUpdating == INIT_STATE_UNINIT) {
                this.storageVisible = null;
            } else {
                if (this.storageVisible == null) {
                    this.storageVisible = this.storageUpdating.clone();
                } else if (this.storageUpdating != this.storageVisible) {
                    System.arraycopy(this.storageUpdating, 0, this.storageVisible, 0, ARRAY_SIZE);
                }
                if (this.storageUpdating != this.storageVisible) {
                    freeBytes(this.storageUpdating);
                }
                this.storageUpdating = this.storageVisible;
            }
            this.updatingDirty = false;
            this.stateVisible = this.stateUpdating;
            this.fullFlagVisible = this.fullFlag;
            this.zeroFlagVisible = this.zeroFlag;
        }
        return true;
    }

    // The visible backing array, null for NULL and UNINIT; callers must not write to it
    public byte[] getVisibleData() {
        return this.storageVisible;
    }

    public int getUpdating(int x, int y, int z) {
        return this.getUpdating((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    public int getUpdating(int index) {
        byte[] bytes = this.storageUpdating;
        if (bytes == null) {
            return 0;
        }
        byte value = bytes[index >>> 1];
        return ((value >>> ((index & 1) << 2)) & 0xF);
    }

    public int getVisible(int x, int y, int z) {
        return this.getVisible((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    public int getVisible(int index) {
        byte[] visibleBytes = this.storageVisible;
        if (visibleBytes == null) {
            return 0;
        }
        byte value = visibleBytes[index >>> 1];
        return ((value >>> ((index & 1) << 2)) & 0xF);
    }

    public void set(int x, int y, int z, int value) {
        this.set((x & 15) | ((z & 15) << 4) | ((y & 15) << 8), value);
    }

    public void set(int index, int value) {
        if (this.fullFlag | this.zeroFlag) {
            this.fullFlag = false;
            this.zeroFlag = false;
        }
        if (!this.updatingDirty) {
            this.swapUpdatingAndMarkDirty();
        }
        int shift = (index & 1) << 2;
        int i = index >>> 1;
        this.storageUpdating[i] = (byte) ((this.storageUpdating[i] & (0xF0 >>> shift)) | (value << shift));
    }

    // Saved form of one section: data is null for UNINIT
    public static final class SaveState {
        public final byte[] data;
        public final int state;

        public SaveState(byte[] data, int state) {
            this.data = data;
            this.state = state;
        }
    }
}
