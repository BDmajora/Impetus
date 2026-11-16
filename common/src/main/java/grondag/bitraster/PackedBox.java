/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.bitraster;

// Boxes of a section packed into one int.
public abstract class PackedBox {
	// Range class of near sections.
	public static final int RANGE_NEAR = 0;
	// Range class of middle sections.
	public static final int RANGE_MID = 1;
	// Range class of far sections.
	public static final int RANGE_FAR = 2;
	// Range class of the farthest sections.
	public static final int RANGE_EXTREME = 3;
	// Chunk distance of the near range.
	public static final int CHUNK_DIST_NEAR = 1;
	// Chunk distance of the middle range.
	public static final int CHUNK_DIST_MID = 2;
	// Chunk distance of the far range.
	public static final int CHUNK_DIST_FAR = 8;
	// Squared distance of the near range.
	public static final int SQUARE_CHUNK_DIST_NEAR = CHUNK_DIST_NEAR * CHUNK_DIST_NEAR * 3;
	// Squared distance of the middle range.
	public static final int SQUARE_CHUNK_DIST_MID = CHUNK_DIST_MID * CHUNK_DIST_MID * 3;
	// Squared distance of the far range.
	public static final int SQUARE_CHUNK_DIST_FAR = CHUNK_DIST_FAR * CHUNK_DIST_FAR * 3;
	// Box without volume.
	public static final int EMPTY_BOX = 0;
	// Shift of the first x.
	private static final int SHIFT_X0 = 0;
	// Shift of the first y.
	private static final int SHIFT_Y0 = SHIFT_X0 + 5;
	// Shift of the first z.
	private static final int SHIFT_Z0 = SHIFT_Y0 + 5;
	// Shift of the last x.
	private static final int SHIFT_X1 = SHIFT_Z0 + 5;
	// Shift of the last y.
	private static final int SHIFT_Y1 = SHIFT_X1 + 5;
	// Shift of the last z.
	private static final int SHIFT_Z1 = SHIFT_Y1 + 5;
	// Shift of the range.
	private static final int SHIFT_RANGE = SHIFT_Z1 + 5;
	// Box that covers a whole section.
	public static final int FULL_BOX = pack(0, 0, 0, 16, 16, 16, RANGE_EXTREME);

	// Prevents instantiation
	private PackedBox() {
	}

	// Returns the range class of a squared chunk distance.
	public static int rangeFromSquareChunkDist(int squareChunkDist) {
		if (squareChunkDist <= SQUARE_CHUNK_DIST_MID) {
			return squareChunkDist > SQUARE_CHUNK_DIST_NEAR ? RANGE_MID : RANGE_NEAR;
		} else {
			return squareChunkDist > SQUARE_CHUNK_DIST_FAR ? RANGE_EXTREME : RANGE_FAR;
		}
	}

	// Returns the packed box and rejects a value out of range.
	public static int pack(int x0, int y0, int z0, int x1, int y1, int z1, int range) {
		if (((x0 | y0 | z0 | x1 | y1 | z1) & ~31) != 0 || (range & ~3) != 0) {
			throw new IllegalArgumentException("Packed box value out of range");
		}
		return x0 | (y0 << SHIFT_Y0) | (z0 << SHIFT_Z0)
				| ((x1) << SHIFT_X1) | ((y1) << SHIFT_Y1) | ((z1) << SHIFT_Z1)
				| (range << SHIFT_RANGE);
	}

	// Returns the range of a packed box.
	public static int range(int packed) {
		return (packed >>> SHIFT_RANGE) & 3;
	}

	// Returns the first x of a packed box.
	public static int x0(int packed) {
		return (packed & 31);
	}

	// Returns the first y of a packed box.
	public static int y0(int packed) {
		return (packed >>> SHIFT_Y0) & 31;
	}

	// Returns the first z of a packed box.
	public static int z0(int packed) {
		return (packed >>> SHIFT_Z0) & 31;
	}

	// Returns the last x of a packed box.
	public static int x1(int packed) {
		return ((packed >>> SHIFT_X1) & 31);
	}

	// Returns the last y of a packed box.
	public static int y1(int packed) {
		return ((packed >>> SHIFT_Y1) & 31);
	}

	// Returns the last z of a packed box.
	public static int z1(int packed) {
		return ((packed >>> SHIFT_Z1) & 31);
	}

	// Returns the corners of a packed box as text.
	public static String toString(int packed) {
		return "(" + x0(packed) + ", " + y0(packed) + ", " + z0(packed) + "), ("
				+ x1(packed) + ", " + y1(packed) + ", " + z1(packed) + ")";
	}
}
