package com.bdmajora.coarctatio.launch.discovery;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ParserCodecEdgeTest {
    private static byte[] header(java.util.function.Consumer<DataOutputStream> tail) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBoolean(false);
            out.writeInt(52);
            out.writeBoolean(false);
            tail.accept(out);
        } 
        return bytes.toByteArray();
    }

    private static void decode(byte[] data) throws IOException {
        ParserCodec.decode(new DataInputStream(new ByteArrayInputStream(data)));
    }

    @Test
    void implausibleCountsAreRefused() throws Exception {
        Assumptions.assumeTrue(ParserCodec.canDecode());
        assertThrows(IOException.class, () -> decode(header(out -> {
            try { out.writeInt(Integer.MAX_VALUE); } catch (IOException e) { throw new RuntimeException(e); }
        })));
        assertThrows(IOException.class, () -> decode(header(out -> {
            try { out.writeInt(-1); } catch (IOException e) { throw new RuntimeException(e); }
        })));
        assertThrows(IOException.class, () -> decode(header(out -> {
            try { out.writeInt(0); out.writeInt(1); out.writeByte(120); } catch (IOException e) { throw new RuntimeException(e); }
        })));
    }

    @Test
    void truncatedInputFailsAsIoError() throws Exception {
        Assumptions.assumeTrue(ParserCodec.canDecode());
        assertThrows(IOException.class, () -> decode(new byte[0]));
        assertThrows(IOException.class, () -> decode(new byte[] {0, 0, 0}));
    }

    @Test
    void deepNestingIsRefusedWithoutOverflowingTheStack() throws Exception {
        Assumptions.assumeTrue(ParserCodec.canDecode());
        byte[] data = header(out -> {
            try {
                out.writeInt(0);
                out.writeInt(1);
                out.writeByte(0);
                out.writeBoolean(false);
                out.writeBoolean(false);
                out.writeInt(1);
                out.writeUTF("k");
                for (int i = 0; i < 5000; i++) {
                    out.writeByte(9);
                    out.writeInt(1);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(IOException.class, () -> decode(data));
    }
}
