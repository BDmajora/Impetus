package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

// Sorting data of the translucent quads of a section.
public sealed interface SortState {
    // State where any draw order is correct.
    SortState NONE = new None();

    // Names of the states for the debug screen.
    String[] DEBUG_NAMES = { "NONE", "STATIC", "TREE", "DYNAMIC" };

    // Returns the position in the debug names that also ranks the cost.
    int debugIndex();

    // Returns the number of quads.
    int quadCount();

    // Returns the state that is kept after the build.
    SortState compactForStorage();

    // Returns whether the state needs sorting when the camera moves.
    default boolean requiresDynamicSorting() {
        return this instanceof Resortable;
    }

    // State whose order depends on the camera.
    sealed interface Resortable extends SortState permits Dynamic, PartitionTree {
        // Returns the state itself.
        @Override
        default Resortable compactForStorage() {
            return this;
        }
    }

    // State without any order.
    record None() implements SortState {
        // Returns the debug position.
        @Override
        public int debugIndex() {
            return 0;
        }

        // Returns the number of quads.
        @Override
        public int quadCount() {
            return 0;
        }

        // Returns the state kept after the build.
        @Override
        public SortState compactForStorage() {
            return NONE;
        }
    }

    // State with one order for every camera.
    record Static(int[] order) implements SortState {
        // Rejects a null order.
        public Static {
            java.util.Objects.requireNonNull(order, "order");
        }

        @Override
        public int debugIndex() {
            return 1;
        }

        @Override
        public int quadCount() {
            return order.length;
        }

        @Override
        public SortState compactForStorage() {
            return NONE;
        }
    }

    // State that keeps the quad centers for every sort.
    record Dynamic(float[] centers) implements Resortable {
        // Rejects centers that are not a multiple of three.
        public Dynamic {
            if (centers.length % 3 != 0) {
                throw new IllegalArgumentException("centers length must be a multiple of 3");
            }
        }

        @Override
        public int debugIndex() {
            return 3;
        }

        @Override
        public int quadCount() {
            return centers.length / 3;
        }
    }
}
