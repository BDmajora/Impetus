package com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry;

import grondag.bitraster.PackedBox;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// Finds boxes that occlude other sections.
public final class OccluderBoxes {
    // Smallest volume of a kept box.
    private static final int MIN_VOLUME = 8;

    // Box finder of each thread.
    private static final ThreadLocal<BoxFinder> FINDER = ThreadLocal.withInitial(() -> new BoxFinder(new AreaFinder()));

    // Prevents instantiation
    private OccluderBoxes() {
    }

    // Returns the boxes of the opaque cells ordered from the largest range down or null when none qualify.
    public static int @Nullable [] from(long[] opacityBits) {
        java.util.Objects.requireNonNull(opacityBits, "opacityBits");
        BoxFinder finder = FINDER.get();
        finder.findBoxes(opacityBits, 0);

        var found = finder.boxes;
        int count = found.size();

        if (count == 0) {
            return null;
        }

        int[] boxes = new int[count];
        int kept = 0;

        for (int range = PackedBox.RANGE_EXTREME; range >= PackedBox.RANGE_NEAR; range--) {
            for (int i = 0; i < count; i++) {
                int box = found.getInt(i);

                if (PackedBox.range(box) == range && volume(box) >= MIN_VOLUME) {
                    boxes[kept++] = toWorldAxes(box);
                }
            }
        }

        if (kept == 0) {
            return null;
        }

        return kept == count ? boxes : Arrays.copyOf(boxes, kept);
    }

    // Swaps the y and z axes of a box.
    private static int toWorldAxes(int box) {
        return PackedBox.pack(
                PackedBox.x0(box), PackedBox.z0(box), PackedBox.y0(box),
                PackedBox.x1(box), PackedBox.z1(box), PackedBox.y1(box),
                PackedBox.range(box)
        );
    }

    // Returns the volume of a box.
    private static int volume(int box) {
        return (PackedBox.x1(box) - PackedBox.x0(box))
                * (PackedBox.y1(box) - PackedBox.y0(box))
                * (PackedBox.z1(box) - PackedBox.z0(box));
    }
}
