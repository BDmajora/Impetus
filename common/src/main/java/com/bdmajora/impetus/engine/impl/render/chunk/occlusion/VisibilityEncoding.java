package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import org.jetbrains.annotations.NotNull;

// Encodes which faces of a section see each other in one long.
public class VisibilityEncoding {
    // Encoding where no face sees another.
    public static final long NULL = 0L;
    // Encoding where every face sees every face.
    public static final long EVERYTHING = everything();

    // Packs the face to face table of a holder into one long.
    public static <T> long encode(@NotNull DataHolder holder) {
        long visibilityData = 0;

        for (int from = 0; from < GraphDirection.COUNT; from++) {
            for (int to = 0; to < GraphDirection.COUNT; to++) {
                if (holder.canFaceSeeFace(from, to)) {
                    visibilityData |= 1L << bit(from, to);
                }
            }
        }

        return visibilityData;
    }

    // Source of the face to face visibility.
    public interface DataHolder {
        // Returns whether a face sees another face.
        boolean canFaceSeeFace(int fromDir, int toDir);
    }

    // Returns the bit of a pair of faces.
    static int bit(int from, int to) {
        return (from * 8) + to;
    }

    // Returns the encoding where every face sees every face.
    private static long everything() {
        long visibilityData = 0;

        for (int from = 0; from < GraphDirection.COUNT; from++) {
            for (int to = 0; to < GraphDirection.COUNT; to++) {
                visibilityData |= 1L << bit(from, to);
            }
        }

        return visibilityData;
    }

    // Returns the outgoing directions reachable from a set of incoming directions.
    public static int getConnections(long visibilityData, int incoming) {
        return foldOutgoingDirections(visibilityData & createMask(incoming & GraphDirectionSet.ALL));
    }

    // Returns every outgoing direction.
    public static int getConnections(long visibilityData) {
        return foldOutgoingDirections(visibilityData);
    }

    // Returns the mask of the rows of a set of incoming faces.
    private static long createMask(int incoming) {
        var expanded = (0b0000001_0000001_0000001_0000001_0000001_0000001L * Integer.toUnsignedLong(incoming));
        return (expanded & 0b00000001_00000001_00000001_00000001_00000001_00000001L) * 0xFF;
    }

    // Combines the rows into one set of directions.
    private static int foldOutgoingDirections(long data) {
        long folded = data;
        folded |= folded >> 32;
        folded |= folded >> 16;
        folded |= folded >> 8;

        return (int) (folded & GraphDirectionSet.ALL);
    }

    // Returns the table as text.
    public static String stringify(long data) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ");
        for (char c : GraphDirection.LETTERS) {
            sb.append(c);
        }
        sb.append('\n');
        for (int from = 0; from < GraphDirection.COUNT; from++) {
            sb.append(GraphDirection.LETTERS[from]);
            sb.append('|');
            for (int to = 0; to < GraphDirection.COUNT; to++) {
                if ((data & (1L << bit(from, to))) != 0) {
                    sb.append('x');
                } else {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}