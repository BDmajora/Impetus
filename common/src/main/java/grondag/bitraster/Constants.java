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

// Constants of the rasterizer.
public class Constants {
	// Bit of the upward face.
	public static final int UP = 1 << 1;
	// Bit of the downward face.
	public static final int DOWN = 1 << 0;
	// Bit of the east face.
	public static final int EAST = 1 << 5;
	// Bit of the west face.
	public static final int WEST = 1 << 4;
	// Bit of the north face.
	public static final int NORTH = 1 << 2;
	// Bit of the south face.
	public static final int SOUTH = 1 << 3;
	// Shift of the tile size along one axis.
	public static final int TILE_AXIS_SHIFT = 3;
	// Mask that clears the pixel bits of a coordinate.
	static final int TILE_AXIS_MASK = ~((1 << TILE_AXIS_SHIFT) - 1);
	// Pixels along one axis of a tile.
	static final int TILE_PIXEL_DIAMETER = 1 << TILE_AXIS_SHIFT;
	// Mask of the pixel index in a tile axis.
	static final int TILE_PIXEL_INDEX_MASK = TILE_PIXEL_DIAMETER - 1;
	// Mask of the tile bits of a coordinate.
	static final int TILE_PIXEL_INVERSE_MASK = ~TILE_PIXEL_INDEX_MASK;
	// Fraction bits of a precise coordinate.
	static final int PRECISION_BITS = 4;
	// Mask of the fraction of a precise coordinate.
	static final int PRECISE_FRACTION_MASK = (1 << PRECISION_BITS) - 1;
	// Mask of the integer part of a precise coordinate.
	static final int PRECISE_INTEGER_MASK = ~PRECISE_FRACTION_MASK;
	// Size of a pixel in precise units.
	static final int PRECISE_PIXEL_SIZE = 1 << PRECISION_BITS;
	// Center of a pixel in precise units.
	static final int PRECISE_PIXEL_CENTER = PRECISE_PIXEL_SIZE / 2;
	// Center of a pixel in precise units rounded down.
	static final int SCANT_PRECISE_PIXEL_CENTER = PRECISE_PIXEL_CENTER - 1;
	// Smallest size of the buffer along an axis in tiles.
	public static final int MIN_SIZE_TILES = 64;
	// Default size of the buffer along an axis in tiles.
	public static final int DEFAULT_SIZE_TILES = 128;
	// Largest size of the buffer along an axis in tiles.
	public static final int MAX_SIZE_TILES = 256;

	// Pixels added around a tested polygon.
	static final int TEST_DILATION_PIXELS = 1;

	// Fraction bits of the camera position.
	public static final int CAMERA_PRECISION_BITS = 12;
	// Camera position of one block.
	static final int CAMERA_PRECISION_UNITY = 1 << CAMERA_PRECISION_BITS;
	// Largest camera offset inside a chunk range.
	static final int CAMERA_PRECISION_CHUNK_MAX = 18 * CAMERA_PRECISION_UNITY;
	// Camera position of half a block.
	static final int CAMERA_PRECISION_HALF = CAMERA_PRECISION_UNITY / 2;
	// Result of bounds that are inside.
	static final int BOUNDS_IN = 0;
	// Result of bounds that are outside or too small.
	static final int BOUNDS_OUTSIDE_OR_TOO_SMALL = 1;
	// Flag of a negative b coefficient.
	static final int B_NEGATIVE = 8;
	// Flag of a zero b coefficient.
	static final int B_ZERO = 16;
	// Flag of a positive b coefficient.
	static final int B_POSITIVE = 32;
	// Flag of a negative a coefficient.
	static final int A_NEGATIVE = 1;
	// Flag of a zero a coefficient.
	static final int A_ZERO = 2;
	// Flag of a positive a coefficient.
	static final int A_POSITIVE = 4;
	// Edge class of a top edge.
	static final int EDGE_TOP = B_NEGATIVE | A_ZERO;
	// Edge class of a bottom edge.
	static final int EDGE_BOTTOM = B_POSITIVE | A_ZERO;
	// Edge class of a right edge.
	static final int EDGE_RIGHT = B_ZERO | A_NEGATIVE;
	// Edge class of a left edge.
	static final int EDGE_LEFT = B_ZERO | A_POSITIVE;
	// Edge class of a top right edge.
	static final int EDGE_TOP_RIGHT = B_NEGATIVE | A_NEGATIVE;
	// Edge class of a top left edge.
	static final int EDGE_TOP_LEFT = B_NEGATIVE | A_POSITIVE;
	// Edge class of a bottom right edge.
	static final int EDGE_BOTTOM_RIGHT = B_POSITIVE | A_NEGATIVE;
	// Edge class of a bottom left edge.
	static final int EDGE_BOTTOM_LEFT = B_POSITIVE | A_POSITIVE;
	// Edge class of a point.
	static final int EDGE_POINT = A_ZERO | B_ZERO;
	// Event of vertex 0 on the left.
	static final int EVENT_0_LEFT = A_POSITIVE - 1;
	// Event of vertex 0 on the flat.
	static final int EVENT_0_FLAT = A_ZERO - 1;
	// Event of vertex 0 on the right.
	static final int EVENT_0_RIGHT = A_NEGATIVE - 1;
	// Mask of the position of one vertex event.
	static final int EVENT_POSITION_MASK = 3;
	// Event of vertex 1 on the left.
	static final int EVENT_1_LEFT = EVENT_0_LEFT << 2;
	// Event of vertex 1 on the flat.
	static final int EVENT_1_FLAT = EVENT_0_FLAT << 2;
	// Event of vertex 1 on the right.
	static final int EVENT_1_RIGHT = EVENT_0_RIGHT << 2;
	// Event of vertex 2 on the left.
	static final int EVENT_2_LEFT = EVENT_1_LEFT << 2;
	// Event of vertex 2 on the flat.
	static final int EVENT_2_FLAT = EVENT_1_FLAT << 2;
	// Event of vertex 2 on the right.
	static final int EVENT_2_RIGHT = EVENT_1_RIGHT << 2;
	// Event of vertex 3 on the left.
	static final int EVENT_3_LEFT = EVENT_2_LEFT << 2;
	// Event of vertex 3 on the flat.
	static final int EVENT_3_FLAT = EVENT_2_FLAT << 2;
	// Event of vertex 3 on the right.
	static final int EVENT_3_RIGHT = EVENT_2_RIGHT << 2;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RRR = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LRR = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FRR = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RLR = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LLR = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FLR = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RFR = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LFR = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FFR = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_RIGHT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RRL = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LRL = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FRL = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RLL = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LLL = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FLL = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RFL = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LFL = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FFL = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_LEFT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LRF = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RRF = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FRF = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_FLAT;

	// Combined events of vertices zero to two.
	static final int EVENT_012_RLF = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LLF = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FLF = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_RFF = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_LFF = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_FLAT;
	// Combined events of vertices zero to two.
	static final int EVENT_012_FFF = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRRR = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRRR = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRRR = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLRR = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLRR = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLRR = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFRR = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFRR = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFRR = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRLR = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRLR = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRLR = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLLR = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLLR = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLLR = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFLR = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFLR = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFLR = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRFR = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRFR = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRFR = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLFR = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLFR = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLFR = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFFR = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFFR = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFFR = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_RIGHT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRRL = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRRL = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRRL = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLRL = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLRL = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLRL = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFRL = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFRL = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFRL = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRLL = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRLL = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRLL = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLLL = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLLL = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLLL = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFLL = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFLL = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFLL = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRFL = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRFL = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRFL = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLFL = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLFL = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLFL = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFFL = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFFL = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFFL = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_LEFT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRRF = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRRF = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRRF = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLRF = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLRF = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLRF = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFRF = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFRF = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFRF = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_RIGHT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRLF = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRLF = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRLF = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLLF = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLLF = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLLF = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFLF = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFLF = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFLF = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_LEFT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LRFF = EVENT_0_LEFT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RRFF = EVENT_0_RIGHT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FRFF = EVENT_0_FLAT | EVENT_1_RIGHT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RLFF = EVENT_0_RIGHT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LLFF = EVENT_0_LEFT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FLFF = EVENT_0_FLAT | EVENT_1_LEFT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_RFFF = EVENT_0_RIGHT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_LFFF = EVENT_0_LEFT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_FLAT;
	// Combined events of vertices zero to three.
	static final int EVENT_0123_FFFF = EVENT_0_FLAT | EVENT_1_FLAT | EVENT_2_FLAT | EVENT_3_FLAT;

	// Offset of the precise x of a projected vertex.
	static final int PV_PX = 0;
	// Offset of the precise y of a projected vertex.
	static final int PV_PY = 1;
	// Offset of the x of a projected vertex.
	static final int PV_X = 2;
	// Offset of the y of a projected vertex.
	static final int PV_Y = 3;
	// Offset of the z of a projected vertex.
	static final int PV_Z = 4;
	// Offset of the w of a projected vertex.
	static final int PV_W = 5;
	// Ints of one projected vertex.
	static final int PROJECTED_VERTEX_STRIDE = 6;

	// Offset of the corner 000 in the vertex data.
	static final int V000 = 0;
	// Offset of the corner 001 in the vertex data.
	static final int V001 = V000 + PROJECTED_VERTEX_STRIDE;
	// Offset of the corner 010 in the vertex data.
	static final int V010 = V001 + PROJECTED_VERTEX_STRIDE;
	// Offset of the corner 011 in the vertex data.
	static final int V011 = V010 + PROJECTED_VERTEX_STRIDE;
	// Offset of the corner 100 in the vertex data.
	static final int V100 = V011 + PROJECTED_VERTEX_STRIDE;
	// Offset of the corner 101 in the vertex data.
	static final int V101 = V100 + PROJECTED_VERTEX_STRIDE;
	// Offset of the corner 110 in the vertex data.
	static final int V110 = V101 + PROJECTED_VERTEX_STRIDE;
	// Offset of the corner 111 in the vertex data.
	static final int V111 = V110 + PROJECTED_VERTEX_STRIDE;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_AX0 = V111 + PROJECTED_VERTEX_STRIDE;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_AY0 = IDX_AX0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_AX1 = IDX_AY0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_AY1 = IDX_AX1 + 1;

	// Index of a bound of the box edges in the vertex data.
	static final int IDX_BX0 = IDX_AY1 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_BY0 = IDX_BX0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_BX1 = IDX_BY0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_BY1 = IDX_BX1 + 1;

	// Index of a bound of the box edges in the vertex data.
	static final int IDX_CX0 = IDX_BY1 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_CY0 = IDX_CX0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_CX1 = IDX_CY0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_CY1 = IDX_CX1 + 1;

	// Index of a bound of the box edges in the vertex data.
	static final int IDX_DX0 = IDX_CY1 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_DY0 = IDX_DX0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_DX1 = IDX_DY0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_DY1 = IDX_DX1 + 1;

	// Index of a bound of the box edges in the vertex data.
	static final int IDX_EX0 = IDX_DY1 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_EY0 = IDX_EX0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_EX1 = IDX_EY0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_EY1 = IDX_EX1 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_FX0 = IDX_EY1 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_FY0 = IDX_FX0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_FX1 = IDX_FY0 + 1;
	// Index of a bound of the box edges in the vertex data.
	static final int IDX_FY1 = IDX_FX1 + 1;
	// Ints of the vertex data.
	static final int VERTEX_DATA_LENGTH = IDX_FY1 + 1;

}
