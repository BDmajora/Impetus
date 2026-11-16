package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.NibbleArray;

import java.util.ArrayDeque;
import java.util.Arrays;

// Light nibble array with one writer copy and one published copy for readers.
public final class SWMRNibbleArray {
    // State of a section without light storage.
    public static final int INIT_STATE_NULL = 0;
    // State of a section that reads as zero light.
    public static final int INIT_STATE_UNINIT = 1;
    // State of a section with stored light.
    public static final int INIT_STATE_INIT = 2;
    // State of a section with stored light that readers treat as absent.
    public static final int INIT_STATE_HIDDEN = 3;

    // Bytes of one section of four bit values.
    public static final int ARRAY_SIZE = 16 * 16 * 16 / 2;

    // Per thread pool of released writer arrays.
    private static final ThreadLocal<ArrayDeque<byte[]>> WORKING_BYTES_POOL = ThreadLocal.withInitial(ArrayDeque::new);

    // State seen by the writer.
    private int stateUpdating;
    // State seen by readers.
    private volatile int stateVisible;
    // Array written by the writer.
    private byte[] storageUpdating;
    // Flag set when the writer array is private and holds unpublished changes.
    private boolean updatingDirty;
    // Array read by readers.
    private volatile byte[] storageVisible;
    // Flag set when every writer value is fifteen.
    private boolean fullFlag;
    // Flag set when every writer value is zero.
    private boolean zeroFlag;
    // Published full flag.
    private volatile boolean fullFlagVisible;
    // Published zero flag.
    private volatile boolean zeroFlagVisible;

    // Creates a uninitialised array that reads as zero.
    public SWMRNibbleArray() {
        this(null, false);
    }

    // Creates an array over the bytes or an uninitialised array for null.
    public SWMRNibbleArray(byte[] bytes) {
        this(bytes, false);
    }

    // Creates an array over the bytes or a null or uninitialised array for null and rejects a wrong length.
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

    // Creates an array in a saved state and rejects a wrong length or an unknown state or missing data.
    public SWMRNibbleArray(byte[] bytes, int state) {
        if (bytes != null && bytes.length != ARRAY_SIZE) {
            throw new IllegalArgumentException("Data of wrong length: " + bytes.length);
        }
        if (state < INIT_STATE_NULL || state > INIT_STATE_HIDDEN) {
            throw new IllegalArgumentException("Unknown state " + state);
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

    // Returns a pooled array or a new one.
    private static byte[] allocateBytes() {
        byte[] inPool = WORKING_BYTES_POOL.get().pollFirst();
        return inPool != null ? inPool : new byte[ARRAY_SIZE];
    }

    // Returns an array to the pool of this thread.
    private static void freeBytes(byte[] bytes) {
        WORKING_BYTES_POOL.get().addFirst(bytes);
    }

    // Creates an array from a copy of a vanilla nibble array.
    public static SWMRNibbleArray fromVanilla(NibbleArray nibble) {
        if (nibble == null) {
            return new SWMRNibbleArray(null, true);
        }
        byte[] data = nibble.getData();
        return data == null ? new SWMRNibbleArray() : new SWMRNibbleArray(data.clone());
    }

    // Returns whether every byte of the array is zero.
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

    // Returns the published data for saving or null when nothing needs saving.
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

    // Fills every layer with the bottom layer of another array and rejects a null source.
    public void extrudeLower(SWMRNibbleArray other) {
        if (other.stateUpdating == INIT_STATE_NULL) {
            throw new IllegalArgumentException("Cannot extrude a null nibble");
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
        for (int y = 0; y <= 15; ++y) {
            System.arraycopy(src, 0, into, y << (8 - 1), end + 1);
        }
        this.fullFlag = other.fullFlag;
        this.zeroFlag = other.zeroFlag;
    }

    // Sets every writer value to fifteen.
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

    // Sets every writer value to zero.
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

    // Turns a null array into an uninitialised one and a hidden one into an initialised one.
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

    // Drops the writer storage and marks the array null.
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

    // Drops the writer storage and marks the array uninitialised.
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

    // Hides an initialised array and nulls any other array.
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

    // Returns whether the writer holds unpublished changes.
    public boolean isDirty() {
        return this.stateUpdating != this.stateVisible || this.updatingDirty;
    }

    // Returns whether the writer state is null.
    public boolean isNullNibbleUpdating() {
        return this.stateUpdating == INIT_STATE_NULL;
    }

    // Returns whether the published state is null.
    public boolean isNullNibbleVisible() {
        return this.stateVisible == INIT_STATE_NULL;
    }

    // Returns whether the writer state is uninitialised.
    public boolean isUninitialisedUpdating() {
        return this.stateUpdating == INIT_STATE_UNINIT;
    }

    // Returns whether the published state is uninitialised.
    public boolean isUninitialisedVisible() {
        return this.stateVisible == INIT_STATE_UNINIT;
    }

    // Returns whether the writer state is initialised.
    public boolean isInitialisedUpdating() {
        return this.stateUpdating == INIT_STATE_INIT;
    }

    // Returns whether the published state is initialised.
    public boolean isInitialisedVisible() {
        return this.stateVisible == INIT_STATE_INIT;
    }

    // Returns whether the writer state is hidden.
    public boolean isHiddenUpdating() {
        return this.stateUpdating == INIT_STATE_HIDDEN;
    }

    // Returns whether every writer value is fifteen.
    public boolean isFullUpdating() {
        return this.fullFlag;
    }

    // Returns whether every writer value is zero.
    public boolean isZeroUpdating() {
        return this.zeroFlag;
    }

    // Returns whether every published value is fifteen.
    public boolean isFullVisible() {
        return this.fullFlagVisible;
    }

    // Returns whether every published value is zero.
    public boolean isZeroVisible() {
        return this.zeroFlagVisible;
    }

    // Gives the writer a private copy of its array and marks it dirty.
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

    // Publishes the writer changes and returns whether anything changed.
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

    // Returns the published array or null.
    public byte[] getVisibleData() {
        return this.storageVisible;
    }

    // Returns the writer value at a block position.
    public int getUpdating(int x, int y, int z) {
        return this.getUpdating((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    // Returns the writer value at an index.
    public int getUpdating(int index) {
        byte[] bytes = this.storageUpdating;
        if (bytes == null) {
            return 0;
        }
        byte value = bytes[index >>> 1];
        return ((value >>> ((index & 1) << 2)) & 0xF);
    }

    // Returns the published value at a block position.
    public int getVisible(int x, int y, int z) {
        return this.getVisible((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    // Returns the published value at an index.
    public int getVisible(int index) {
        byte[] visibleBytes = this.storageVisible;
        if (visibleBytes == null) {
            return 0;
        }
        byte value = visibleBytes[index >>> 1];
        return ((value >>> ((index & 1) << 2)) & 0xF);
    }

    // Sets the writer value at a block position.
    public void set(int x, int y, int z, int value) {
        this.set((x & 15) | ((z & 15) << 4) | ((y & 15) << 8), value);
    }

    // Sets the low four bits of a value at an index.
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
        this.storageUpdating[i] = (byte) ((this.storageUpdating[i] & (0xF0 >>> shift)) | ((value & 0xF) << shift));
    }

    // Saved data and state of an array.
    public static final class SaveState {
        // Saved bytes or null.
        public final byte[] data;
        // Saved state.
        public final int state;

        // Creates a saved state.
        public SaveState(byte[] data, int state) {
            this.data = data;
            this.state = state;
        }
    }
}
