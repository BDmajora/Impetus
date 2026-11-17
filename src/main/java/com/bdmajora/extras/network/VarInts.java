package com.bdmajora.extras.network;

import io.netty.buffer.ByteBuf;

// Readers and writers of variable length integers.
public final class VarInts {
    // Encoded VarInt size for each count of leading zero bits.
    private static final int[] SIZE_BY_LEADING_ZEROS = new int[33];

    static {
        for (int i = 0; i <= 32; i++) {
            SIZE_BY_LEADING_ZEROS[i] = (int) Math.ceil((31.0D - (i - 1)) / 7.0D);
        }
        SIZE_BY_LEADING_ZEROS[32] = 1;
    }

    // Prevents instantiation
    private VarInts() {
    }

    // Returns the encoded size of a VarInt in bytes.
    public static int size(int value) {
        return SIZE_BY_LEADING_ZEROS[Integer.numberOfLeadingZeros(value)];
    }

    // Writes the value.
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
