package com.bdmajora.extras.network;

import io.netty.buffer.ByteBuf;

// Branch-free varint sizing and one-write encoding for the short cases, after Krypton (Andrew Steinborn's "how fast can you write a varint"): vanilla loops a byte at a time through ByteBuf bounds checks, and every packet carries at least two varints (frame length and packet id)
public final class VarInts {
    // Indexed by Integer.numberOfLeadingZeros(value); 32 leading zeros (value 0) still takes one byte
    private static final int[] SIZE_BY_LEADING_ZEROS = new int[33];

    static {
        for (int i = 0; i <= 32; i++) {
            SIZE_BY_LEADING_ZEROS[i] = (int) Math.ceil((31.0D - (i - 1)) / 7.0D);
        }
        SIZE_BY_LEADING_ZEROS[32] = 1;
    }

    private VarInts() {
    }

    public static int size(int value) {
        return SIZE_BY_LEADING_ZEROS[Integer.numberOfLeadingZeros(value)];
    }

    public static void write(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShort((value & 0x7F | 0x80) << 8 | (value >>> 7));
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            buf.writeMedium((value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14));
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            buf.writeInt((value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16 | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21));
        } else {
            buf.writeInt((value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16 | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80));
            buf.writeByte(value >>> 28);
        }
    }
}
