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

import static grondag.bitraster.Constants.TILE_AXIS_SHIFT;
import static grondag.bitraster.Constants.TILE_PIXEL_INDEX_MASK;

// Index math of the pixels of a tile.
abstract class Indexer {
	// Prevents instantiation
	private Indexer() {
	}

	// Returns the interleaved bits of two coordinates from zero to seven.
	static int mortonNumber(int x, int y) {
		int z = (x & 0b001) | ((y & 0b001) << 1);
		z |= ((x & 0b010) << 1) | ((y & 0b010) << 2);
		return z | ((x & 0b100) << 2) | ((y & 0b100) << 3);
	}

	// Returns the bit index of a pixel in a tile.
	static int pixelIndex(int x, int y) {
		return ((y & TILE_PIXEL_INDEX_MASK) << TILE_AXIS_SHIFT) | (x & TILE_PIXEL_INDEX_MASK);
	}

	// Returns whether a pixel is clear in a word.
	static boolean isPixelClear(long word, int x, int y) {
		return (word & (1L << pixelIndex(x, y))) == 0;
	}

	// Returns the mask of a pixel.
	static long pixelMask(int x, int y) {
		return 1L << pixelIndex(x, y);
	}

	// Returns whether a pixel with coordinates from zero to seven is clear.
	static boolean testPixelInWordPreMasked(long word, int x, int y) {
		return (word & (1L << ((y << TILE_AXIS_SHIFT) | x))) == 0;
	}

	// Returns the word with a pixel set where the coordinates are from zero to seven.
	static long setPixelInWordPreMasked(long word, int x, int y) {
		return word | (1L << ((y << TILE_AXIS_SHIFT) | x));
	}
}
