package com.bdmajora.coarctatio.nbt;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;

// Shared instances for the small numeric tags (StellarCore's nbtPrimitiveConstantsPool, the same idea as Integer's cache): item counts, damage values, slot indices and small ids dominate the numeric tags in a save and an inventory, and the tag objects carry no state beyond the number. Every byte, and -128..1023 for short, int and long
public final class NbtPrimitivePool {
    private static final int MIN = -128;
    private static final int MAX = 1023;

    private static final NBTTagByte[] BYTES = new NBTTagByte[256];
    private static final NBTTagShort[] SHORTS = new NBTTagShort[MAX - MIN + 1];
    private static final NBTTagInt[] INTS = new NBTTagInt[MAX - MIN + 1];
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

    private NbtPrimitivePool() {
    }

    public static NBTTagByte of(byte value) {
        return BYTES[value + 128];
    }

    public static NBTTagShort of(short value) {
        return value >= MIN && value <= MAX ? SHORTS[value - MIN] : new NBTTagShort(value);
    }

    public static NBTTagInt of(int value) {
        return value >= MIN && value <= MAX ? INTS[value - MIN] : new NBTTagInt(value);
    }

    public static NBTTagLong of(long value) {
        return value >= MIN && value <= MAX ? LONGS[(int) value - MIN] : new NBTTagLong(value);
    }

    // The pooled twin of a freshly read tag, or the tag itself when it is not a small numeric
    public static NBTBase canonical(NBTBase tag) {
        switch (tag.getId()) {
            case 1:
                return of(((NBTTagByte) tag).getByte());
            case 2:
                return of(((NBTTagShort) tag).getShort());
            case 3:
                return of(((NBTTagInt) tag).getInt());
            case 4:
                return of(((NBTTagLong) tag).getLong());
            default:
                return tag;
        }
    }
}
