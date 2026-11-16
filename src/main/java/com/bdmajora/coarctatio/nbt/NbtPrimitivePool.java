package com.bdmajora.coarctatio.nbt;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;

// Shared instances of small primitive tags.
public final class NbtPrimitivePool {
    // Smallest pooled value for wide tags.
    private static final int MIN = -128;
    // Largest pooled value for wide tags.
    private static final int MAX = 1023;

    // Every byte tag.
    private static final NBTTagByte[] BYTES = new NBTTagByte[256];
    // Short tags between the minimum and maximum.
    private static final NBTTagShort[] SHORTS = new NBTTagShort[MAX - MIN + 1];
    // Int tags between the minimum and maximum.
    private static final NBTTagInt[] INTS = new NBTTagInt[MAX - MIN + 1];
    // Long tags between the minimum and maximum.
    private static final NBTTagLong[] LONGS = new NBTTagLong[MAX - MIN + 1];

    static {
        for (int i = 0; i < 256; i++) {
            BYTES[i] = new NBTTagByte((byte) (i - 128));
        }
        for (int i = MIN; i <= MAX; i++) {
            SHORTS[i - MIN] = new NBTTagShort((short) i);
            INTS[i - MIN] = new NBTTagInt(i);
            LONGS[i - MIN] = new NBTTagLong(i);
        }
    }

    // Prevents instantiation
    private NbtPrimitivePool() {
    }

    // Returns the shared tag of the byte.
    public static NBTTagByte of(byte value) {
        return BYTES[value + 128];
    }

    // Returns the shared tag of the short or a new tag outside the pooled range.
    public static NBTTagShort of(short value) {
        return value >= MIN && value <= MAX ? SHORTS[value - MIN] : new NBTTagShort(value);
    }

    // Returns the shared tag of the int or a new tag outside the pooled range.
    public static NBTTagInt of(int value) {
        return value >= MIN && value <= MAX ? INTS[value - MIN] : new NBTTagInt(value);
    }

    // Returns the shared tag of the long or a new tag outside the pooled range.
    public static NBTTagLong of(long value) {
        return value >= MIN && value <= MAX ? LONGS[(int) value - MIN] : new NBTTagLong(value);
    }

    // Returns the shared tag equal to a primitive tag or the same tag for other types.
    public static NBTBase canonical(NBTBase tag) {
        return switch (tag.getId()) {
            case 1 -> of(((NBTTagByte) tag).getByte());
            case 2 -> of(((NBTTagShort) tag).getShort());
            case 3 -> of(((NBTTagInt) tag).getInt());
            case 4 -> of(((NBTTagLong) tag).getLong());
            default -> tag;
        };
    }
}
