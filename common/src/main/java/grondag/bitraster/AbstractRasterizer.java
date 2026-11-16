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

import java.util.Arrays;

import static grondag.bitraster.Constants.MIN_SIZE_TILES;
import static grondag.bitraster.Constants.MAX_SIZE_TILES;
import static grondag.bitraster.Constants.PRECISE_PIXEL_CENTER;
import static grondag.bitraster.Constants.BOUNDS_IN;
import static grondag.bitraster.Constants.BOUNDS_OUTSIDE_OR_TOO_SMALL;
import static grondag.bitraster.Constants.EDGE_BOTTOM;
import static grondag.bitraster.Constants.EDGE_POINT;
import static grondag.bitraster.Constants.EDGE_TOP;
import static grondag.bitraster.Constants.EVENT_0123_FFFF;
import static grondag.bitraster.Constants.EVENT_0123_FFFL;
import static grondag.bitraster.Constants.EVENT_0123_FFFR;
import static grondag.bitraster.Constants.EVENT_0123_FFLF;
import static grondag.bitraster.Constants.EVENT_0123_FFLL;
import static grondag.bitraster.Constants.EVENT_0123_FFLR;
import static grondag.bitraster.Constants.EVENT_0123_FFRF;
import static grondag.bitraster.Constants.EVENT_0123_FFRL;
import static grondag.bitraster.Constants.EVENT_0123_FFRR;
import static grondag.bitraster.Constants.EVENT_0123_FLFF;
import static grondag.bitraster.Constants.EVENT_0123_FLFL;
import static grondag.bitraster.Constants.EVENT_0123_FLFR;
import static grondag.bitraster.Constants.EVENT_0123_FLLF;
import static grondag.bitraster.Constants.EVENT_0123_FLLL;
import static grondag.bitraster.Constants.EVENT_0123_FLLR;
import static grondag.bitraster.Constants.EVENT_0123_FLRF;
import static grondag.bitraster.Constants.EVENT_0123_FLRL;
import static grondag.bitraster.Constants.EVENT_0123_FLRR;
import static grondag.bitraster.Constants.EVENT_0123_FRFF;
import static grondag.bitraster.Constants.EVENT_0123_FRFL;
import static grondag.bitraster.Constants.EVENT_0123_FRFR;
import static grondag.bitraster.Constants.EVENT_0123_FRLF;
import static grondag.bitraster.Constants.EVENT_0123_FRLL;
import static grondag.bitraster.Constants.EVENT_0123_FRLR;
import static grondag.bitraster.Constants.EVENT_0123_FRRF;
import static grondag.bitraster.Constants.EVENT_0123_FRRL;
import static grondag.bitraster.Constants.EVENT_0123_FRRR;
import static grondag.bitraster.Constants.EVENT_0123_LFFF;
import static grondag.bitraster.Constants.EVENT_0123_LFFL;
import static grondag.bitraster.Constants.EVENT_0123_LFFR;
import static grondag.bitraster.Constants.EVENT_0123_LFLF;
import static grondag.bitraster.Constants.EVENT_0123_LFLL;
import static grondag.bitraster.Constants.EVENT_0123_LFLR;
import static grondag.bitraster.Constants.EVENT_0123_LFRF;
import static grondag.bitraster.Constants.EVENT_0123_LFRL;
import static grondag.bitraster.Constants.EVENT_0123_LFRR;
import static grondag.bitraster.Constants.EVENT_0123_LLFF;
import static grondag.bitraster.Constants.EVENT_0123_LLFL;
import static grondag.bitraster.Constants.EVENT_0123_LLFR;
import static grondag.bitraster.Constants.EVENT_0123_LLLF;
import static grondag.bitraster.Constants.EVENT_0123_LLLL;
import static grondag.bitraster.Constants.EVENT_0123_LLLR;
import static grondag.bitraster.Constants.EVENT_0123_LLRF;
import static grondag.bitraster.Constants.EVENT_0123_LLRL;
import static grondag.bitraster.Constants.EVENT_0123_LLRR;
import static grondag.bitraster.Constants.EVENT_0123_LRFF;
import static grondag.bitraster.Constants.EVENT_0123_LRFL;
import static grondag.bitraster.Constants.EVENT_0123_LRFR;
import static grondag.bitraster.Constants.EVENT_0123_LRLF;
import static grondag.bitraster.Constants.EVENT_0123_LRLL;
import static grondag.bitraster.Constants.EVENT_0123_LRLR;
import static grondag.bitraster.Constants.EVENT_0123_LRRF;
import static grondag.bitraster.Constants.EVENT_0123_LRRL;
import static grondag.bitraster.Constants.EVENT_0123_LRRR;
import static grondag.bitraster.Constants.EVENT_0123_RFFF;
import static grondag.bitraster.Constants.EVENT_0123_RFFL;
import static grondag.bitraster.Constants.EVENT_0123_RFFR;
import static grondag.bitraster.Constants.EVENT_0123_RFLF;
import static grondag.bitraster.Constants.EVENT_0123_RFLL;
import static grondag.bitraster.Constants.EVENT_0123_RFLR;
import static grondag.bitraster.Constants.EVENT_0123_RFRF;
import static grondag.bitraster.Constants.EVENT_0123_RFRL;
import static grondag.bitraster.Constants.EVENT_0123_RFRR;
import static grondag.bitraster.Constants.EVENT_0123_RLFF;
import static grondag.bitraster.Constants.EVENT_0123_RLFL;
import static grondag.bitraster.Constants.EVENT_0123_RLFR;
import static grondag.bitraster.Constants.EVENT_0123_RLLF;
import static grondag.bitraster.Constants.EVENT_0123_RLLL;
import static grondag.bitraster.Constants.EVENT_0123_RLLR;
import static grondag.bitraster.Constants.EVENT_0123_RLRF;
import static grondag.bitraster.Constants.EVENT_0123_RLRL;
import static grondag.bitraster.Constants.EVENT_0123_RLRR;
import static grondag.bitraster.Constants.EVENT_0123_RRFF;
import static grondag.bitraster.Constants.EVENT_0123_RRFL;
import static grondag.bitraster.Constants.EVENT_0123_RRFR;
import static grondag.bitraster.Constants.EVENT_0123_RRLF;
import static grondag.bitraster.Constants.EVENT_0123_RRLL;
import static grondag.bitraster.Constants.EVENT_0123_RRLR;
import static grondag.bitraster.Constants.EVENT_0123_RRRF;
import static grondag.bitraster.Constants.EVENT_0123_RRRL;
import static grondag.bitraster.Constants.EVENT_0123_RRRR;
import static grondag.bitraster.Constants.EVENT_POSITION_MASK;
import static grondag.bitraster.Constants.IDX_AX0;
import static grondag.bitraster.Constants.IDX_AX1;
import static grondag.bitraster.Constants.IDX_AY0;
import static grondag.bitraster.Constants.IDX_AY1;
import static grondag.bitraster.Constants.IDX_BX0;
import static grondag.bitraster.Constants.IDX_BX1;
import static grondag.bitraster.Constants.IDX_BY0;
import static grondag.bitraster.Constants.IDX_BY1;
import static grondag.bitraster.Constants.IDX_CX0;
import static grondag.bitraster.Constants.IDX_CX1;
import static grondag.bitraster.Constants.IDX_CY0;
import static grondag.bitraster.Constants.IDX_CY1;
import static grondag.bitraster.Constants.IDX_DX0;
import static grondag.bitraster.Constants.IDX_DX1;
import static grondag.bitraster.Constants.IDX_DY0;
import static grondag.bitraster.Constants.IDX_DY1;
import static grondag.bitraster.Constants.TEST_DILATION_PIXELS;
import static grondag.bitraster.Constants.EVENT_0_FLAT;
import static grondag.bitraster.Constants.EVENT_0_LEFT;
import static grondag.bitraster.Constants.TILE_PIXEL_INDEX_MASK;
import static grondag.bitraster.Constants.A_ZERO;
import static grondag.bitraster.Constants.A_POSITIVE;
import static grondag.bitraster.Constants.PRECISION_BITS;
import static grondag.bitraster.Constants.PV_PX;
import static grondag.bitraster.Constants.PV_PY;
import static grondag.bitraster.Constants.PV_W;
import static grondag.bitraster.Constants.PV_Z;
import static grondag.bitraster.Constants.SCANT_PRECISE_PIXEL_CENTER;
import static grondag.bitraster.Constants.TILE_AXIS_MASK;
import static grondag.bitraster.Constants.TILE_AXIS_SHIFT;
import static grondag.bitraster.Constants.VERTEX_DATA_LENGTH;

// Software rasterizer that keeps one coverage bit per pixel.
public abstract class AbstractRasterizer {
	// Combined model view and projection matrix.
	final Matrix4L mvpMatrix = new Matrix4L();

	// Elements of the matrix as floats.
	float m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;

	// Offset applied to vertices before projection.
	float offsetX, offsetY, offsetZ;
	// Data of the projected vertices.
	final int[] vertexData = new int[VERTEX_DATA_LENGTH];
	// Size of the buffer in tiles.
	int tileWidth, tileHeight, tileCount;
	// Size of the buffer in pixels.
	int pixelWidth, pixelHeight, lastPixelX, lastPixelY, halfPixelWidth, halfPixelHeight;
	// Size of the buffer in precise units.
	int preciseWidth, preciseHeight, halfPreciseWidth, halfPreciseHeight, preciseWidthClamp, preciseHeightClamp;

	// Events of the current polygon.
	int[] eventData;
	// Coverage of every tile.
	long[] tiles;

	// Bits of the tiles that are full.
	long[] fullBits;

	// Fillers by event key.
	final EventFiller[] EVENT_FILLERS = new EventFiller[0x1000];

	// Pixel bounds of the current polygon.
	protected int minPixelX, minPixelY, maxPixelX, maxPixelY;

	// Position of each vertex against the edges.
	protected int pos0, pos1, pos2, pos3;

	// Index of the tile saved for later.
	protected int saveTileIndex;
	// Rows of the event data.
	protected int eventY0, eventLimit;

	// Key of the event combination.
	protected int eventKey;

	// Inner rows of the band.
	protected int bandInnerFirst, bandInnerLast;

	// Outer rows of the band.
	protected int bandOuterFirst, bandOuterLast;

	// Whether the edges were prepared.
	protected boolean edgesReady;
	// Start x of each edge.
	protected final long[] edgeX0 = new long[6];
	// Step of each edge.
	protected final long[] edgeStep = new long[6];

	// Whether the generic event path is used.
	protected boolean genericEvents;

	// Vertices of the polygon.
	private int[] polyVertices;
	// Number of polygon vertices.
	private int polyCount;

	// Rows of the polygon.
	private int polyMinRow, polyMaxRow;

	// Whether the bounds of a test are widened.
	protected boolean dilate;
	// Number of edges of each chain.
	private int leftCount, rightCount;
	// Edge data of the left and right chains.
	private final long[] leftX0 = new long[6], leftStep = new long[6], rightX0 = new long[6], rightStep = new long[6];
	// End rows of the chains.
	private final int[] leftEnd = new int[6], rightEnd = new int[6];
	// Edges of a chain.
	private final int[] chainEdges = new int[6];

	{
		EVENT_FILLERS[EVENT_0123_RRRR] = () -> {
			populateLeftEvents();
			populateRightEvents4(IDX_AX0, IDX_BX0, IDX_CX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LRRR] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents3(IDX_BX0, IDX_CX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FRRR] = () -> {
			populateLeftEvents();
			populateRightEvents3(IDX_BX0, IDX_CX0, IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RLRR] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents3(IDX_AX0, IDX_CX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LLRR] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_BX0);
			populateRightEvents2(IDX_CX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FLRR] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents2(IDX_CX0, IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RFRR] = () -> {
			populateLeftEvents();
			populateRightEvents3(IDX_AX0, IDX_CX0, IDX_DX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_LFRR] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents2(IDX_CX0, IDX_DX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_FFRR] = () -> {
			populateLeftEvents();
			populateRightEvents2(IDX_CX0, IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
		};

		EVENT_FILLERS[EVENT_0123_RRLR] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents3(IDX_AX0, IDX_BX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LRLR] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_CX0);
			populateRightEvents2(IDX_BX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FRLR] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents2(IDX_BX0, IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RLLR] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_CX0);
			populateRightEvents2(IDX_AX0, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LLLR] = () -> {
			populateLeftEvents3(IDX_AX0, IDX_BX0, IDX_CX0);
			populateRightEvents(IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FLLR] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_CX0);
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RFLR] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents2(IDX_AX0, IDX_DX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_LFLR] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_CX0);
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_FFLR] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
		};

		EVENT_FILLERS[EVENT_0123_LRFR] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents2(IDX_BX0, IDX_DX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RRFR] = () -> {
			populateLeftEvents();
			populateRightEvents3(IDX_AX0, IDX_BX0, IDX_DX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FRFR] = () -> {
			populateLeftEvents();
			populateRightEvents2(IDX_BX0, IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RLFR] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents2(IDX_AX0, IDX_DX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_LLFR] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_BX0);
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FLFR] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RFFR] = () -> {
			populateLeftEvents();
			populateRightEvents2(IDX_AX0, IDX_DX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_LFFR] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FFFR] = () -> {
			populateLeftEvents();
			populateRightEvents(IDX_DX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};

		EVENT_FILLERS[EVENT_0123_RRRL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents3(IDX_AX0, IDX_BX0, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_LRRL] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_DX0);
			populateRightEvents2(IDX_BX0, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FRRL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents2(IDX_BX0, IDX_CX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RLRL] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_DX0);
			populateRightEvents2(IDX_AX0, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_LLRL] = () -> {
			populateLeftEvents3(IDX_AX0, IDX_BX0, IDX_DX0);
			populateRightEvents(IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FLRL] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_DX0);
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RFRL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents2(IDX_AX0, IDX_CX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_LFRL] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_DX0);
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_FFRL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_RRLL] = () -> {
			populateLeftEvents2(IDX_CX0, IDX_DX0);
			populateRightEvents2(IDX_AX0, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_LRLL] = () -> {
			populateLeftEvents3(IDX_AX0, IDX_CX0, IDX_DX0);
			populateRightEvents(IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_FRLL] = () -> {
			populateLeftEvents2(IDX_CX0, IDX_DX0);
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RLLL] = () -> {
			populateLeftEvents3(IDX_BX0, IDX_CX0, IDX_DX0);
			populateRightEvents(IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_LLLL] = () -> {
			populateLeftEvents4(IDX_AX0, IDX_BX0, IDX_CX0, IDX_DX0);
			populateRightEvents();
		};
		EVENT_FILLERS[EVENT_0123_FLLL] = () -> {
			populateLeftEvents3(IDX_BX0, IDX_CX0, IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
		};
		EVENT_FILLERS[EVENT_0123_RFLL] = () -> {
			populateLeftEvents2(IDX_CX0, IDX_DX0);
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_LFLL] = () -> {
			populateLeftEvents3(IDX_AX0, IDX_CX0, IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_FFLL] = () -> {
			populateLeftEvents2(IDX_CX0, IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
		};
		EVENT_FILLERS[EVENT_0123_LRFL] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_DX0);
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RRFL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents2(IDX_AX0, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FRFL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RLFL] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_DX0);
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_LLFL] = () -> {
			populateLeftEvents3(IDX_AX0, IDX_BX0, IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FLFL] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RFFL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_LFFL] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_FFFL] = () -> {
			populateLeftEvents(IDX_DX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
		};
		EVENT_FILLERS[EVENT_0123_RRRF] = () -> {
			populateLeftEvents();
			populateRightEvents3(IDX_AX0, IDX_BX0, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LRRF] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents2(IDX_BX0, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FRRF] = () -> {
			populateLeftEvents();
			populateRightEvents2(IDX_BX0, IDX_CX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RLRF] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents2(IDX_AX0, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LLRF] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_BX0);
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FLRF] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RFRF] = () -> {
			populateLeftEvents();
			populateRightEvents2(IDX_AX0, IDX_CX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LFRF] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FFRF] = () -> {
			populateLeftEvents();
			populateRightEvents(IDX_CX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RRLF] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents2(IDX_AX0, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LRLF] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_CX0);
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FRLF] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RLLF] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_CX0);
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LLLF] = () -> {
			populateLeftEvents3(IDX_AX0, IDX_BX0, IDX_CX0);
			populateRightEvents();
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FLLF] = () -> {
			populateLeftEvents2(IDX_BX0, IDX_CX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RFLF] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LFLF] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_CX0);
			populateRightEvents();
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FFLF] = () -> {
			populateLeftEvents(IDX_CX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LRFF] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RRFF] = () -> {
			populateLeftEvents();
			populateRightEvents2(IDX_AX0, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FRFF] = () -> {
			populateLeftEvents();
			populateRightEvents(IDX_BX0);
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RLFF] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LLFF] = () -> {
			populateLeftEvents2(IDX_AX0, IDX_BX0);
			populateRightEvents();
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FLFF] = () -> {
			populateLeftEvents(IDX_BX0);
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_RFFF] = () -> {
			populateLeftEvents();
			populateRightEvents(IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_LFFF] = () -> {
			populateLeftEvents(IDX_AX0);
			populateRightEvents();
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
		EVENT_FILLERS[EVENT_0123_FFFF] = () -> {
			populateLeftEvents();
			populateRightEvents();
			populateFlatEvents(pos0, IDX_AX0);
			populateFlatEvents(pos1, IDX_BX0);
			populateFlatEvents(pos2, IDX_CX0);
			populateFlatEvents(pos3, IDX_DX0);
		};
	}

	// Creates a rasterizer with a size in tiles.
	AbstractRasterizer(int tileWidth, int tileHeight) {
		resize(tileWidth, tileHeight);
	}

	// Returns the width in tiles.
	public final int tileWidth() {
		return tileWidth;
	}

	// Returns the height in tiles.
	public final int tileHeight() {
		return tileHeight;
	}

	// Resizes the buffer in tiles.
	public final void resize(int tileWidth, int tileHeight) {
		if (tileWidth < MIN_SIZE_TILES || tileWidth > MAX_SIZE_TILES || tileHeight < MIN_SIZE_TILES || tileHeight > MAX_SIZE_TILES) {
			throw new IllegalArgumentException("unsupported buffer size in tiles " + tileWidth + "x" + tileHeight);
		}

		if (this.tileWidth == tileWidth && this.tileHeight == tileHeight && tiles != null) {
			return;
		}

		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		tileCount = tileWidth * tileHeight;
		pixelWidth = tileWidth << TILE_AXIS_SHIFT;
		pixelHeight = tileHeight << TILE_AXIS_SHIFT;
		lastPixelX = pixelWidth - 1;
		lastPixelY = pixelHeight - 1;
		halfPixelWidth = pixelWidth >> 1;
		halfPixelHeight = pixelHeight >> 1;
		preciseWidth = pixelWidth << PRECISION_BITS;
		preciseHeight = pixelHeight << PRECISION_BITS;
		halfPreciseWidth = preciseWidth >> 1;
		halfPreciseHeight = preciseHeight >> 1;
		preciseWidthClamp = preciseWidth - PRECISE_PIXEL_CENTER;
		preciseHeightClamp = preciseHeight - PRECISE_PIXEL_CENTER;

		if (eventData == null || eventData.length < pixelHeight * 2) eventData = new int[pixelHeight * 2];
		if (tiles == null || tiles.length < tileCount) tiles = new long[tileCount];
		if (fullBits == null || fullBits.length < fullBitsWords()) fullBits = new long[fullBitsWords()];
		clear();
	}

	// Returns the number of words of the full bits.
	private int fullBitsWords() {
		return (tileCount + 63) >> 6;
	}

	// Clears the coverage.
	final void clear() {
		Arrays.fill(tiles, 0, tileCount, 0L);
		Arrays.fill(fullBits, 0, fullBitsWords(), 0L);
	}

	// Returns the tile index of a pixel.
	final int tileIndexFromPixelXY(int x, int y) {
		return (x >>> TILE_AXIS_SHIFT) + ((y >>> TILE_AXIS_SHIFT) * tileWidth);
	}

	// Stores the matrix.
	final void setMatrix(Matrix4L m) {
		mvpMatrix.copyFrom(m);
		m00 = m.a00f(); m01 = m.a01f(); m02 = m.a02f(); m03 = m.a03f();
		m10 = m.a10f(); m11 = m.a11f(); m12 = m.a12f(); m13 = m.a13f();
		m20 = m.a20f(); m21 = m.a21f(); m22 = m.a22f(); m23 = m.a23f();
		m30 = m.a30f(); m31 = m.a31f(); m32 = m.a32f(); m33 = m.a33f();
	}

	// Stores the offset of the region.
	final void setRegionOffset(int x, int y, int z) {
		final float scale = 1f / Constants.CAMERA_PRECISION_UNITY;
		offsetX = x * scale;
		offsetY = y * scale;
		offsetZ = z * scale;
	}

	// Copies the state of another rasterizer.
	final void copyFrom(AbstractRasterizer source) {
		resize(source.tileWidth, source.tileHeight);
		setMatrix(source.mvpMatrix);
		offsetX = source.offsetX;
		offsetY = source.offsetY;
		offsetZ = source.offsetZ;
		System.arraycopy(source.vertexData, 0, vertexData, 0, VERTEX_DATA_LENGTH);
		System.arraycopy(source.eventData, 0, eventData, 0, pixelHeight * 2);
		System.arraycopy(source.tiles, 0, tiles, 0, tileCount);
		System.arraycopy(source.fullBits, 0, fullBits, 0, fullBitsWords());
	}

	// Whether work counters are kept.
	public static final boolean STATS = Boolean.getBoolean("bitraster.stats");
	// Work counters.
	public static long STAT_D_NOOP, STAT_D_RANGE0, STAT_D_RANGE1, STAT_D_RANGE2, STAT_D_RANGE3;
	// Test counters.
	public static long STAT_T_EMPTY, STAT_T_INNER, STAT_T_COVER, STAT_T_HIDDEN, STAT_T_VERTEX, STAT_T_BOX_HIDDEN, STAT_D_INNER, STAT_D_COVER;
	// Call counters.
	public static long STAT_DRAW_QUADS, STAT_TEST_QUADS, STAT_TEST_TILES, STAT_EVENT_ROWS, STAT_DRAW_CALLS, STAT_TEST_CALLS;

	// Draws a quad from four vertices.
	final void drawQuad(int v0, int v1, int v2, int v3) {
		if (STATS) STAT_DRAW_CALLS++;
		dilate = false;
		if (prepareBounds(v0, v1, v2, v3) == BOUNDS_OUTSIDE_OR_TOO_SMALL) {
			return;
		}
		if (STATS) STAT_DRAW_QUADS++;

		if (minPixelX == maxPixelX && minPixelY == maxPixelY) {
			return;
		}

		drawQuad();
	}

	// Returns whether part of a quad is not covered.
	final boolean isQuadPartiallyClear(int v0, int v1, int v2, int v3) {
		if (STATS) STAT_TEST_CALLS++;
		dilate = true;
		if (prepareBounds(v0, v1, v2, v3) == BOUNDS_OUTSIDE_OR_TOO_SMALL) {
			return false;
		}
		if (STATS) STAT_TEST_QUADS++;

		dilateBounds();
		return isQuadPartiallyClear();
	}

	// Widens the bounds by the test margin.
	private void dilateBounds() {
		minPixelX = Math.max(0, minPixelX - TEST_DILATION_PIXELS);
		minPixelY = Math.max(0, minPixelY - TEST_DILATION_PIXELS);
		maxPixelX = Math.min(lastPixelX, maxPixelX + TEST_DILATION_PIXELS);
		maxPixelY = Math.min(lastPixelY, maxPixelY + TEST_DILATION_PIXELS);
	}

	// Returns whether part of the prepared quad is not covered.
	final boolean isQuadPartiallyClear() {
		final int minTileX = minPixelX >> TILE_AXIS_SHIFT;
		final int maxTileX = maxPixelX >> TILE_AXIS_SHIFT;
		final int minTileY = minPixelY >> TILE_AXIS_SHIFT;
		final int maxTileY = maxPixelY >> TILE_AXIS_SHIFT;
		final long[] tiles = this.tiles;
		final long[] fullBits = this.fullBits;

		for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
			final int rowBase = tileY * tileWidth;
			final int last = rowBase + maxTileX;
			boolean populated = false;
			int innerFirst = 0, innerLast = -1, outerFirst = 0, outerLast = -1;

			for (int tileIndex = rowBase + minTileX; tileIndex <= last; tileIndex++) {
				final long open = ~fullBits[tileIndex >> 6] >>> (tileIndex & 63);

				if (open == 0) {
					tileIndex |= 63;
					continue;
				}

				tileIndex += Long.numberOfTrailingZeros(open);

				if (tileIndex > last) {
					break;
				}

				if (STATS) STAT_TEST_TILES++;
				final long word = tiles[tileIndex];

				if (word == 0L) {
					if (STATS) STAT_T_EMPTY++;
					return true;
				}

				if (!populated) {
					populateBand(tileY);
					populated = true;
					innerFirst = rowBase + bandInnerFirst;
					innerLast = rowBase + bandInnerLast;
					outerFirst = rowBase + bandOuterFirst;
					outerLast = rowBase + bandOuterLast;
				}

				if (tileIndex >= innerFirst && tileIndex <= innerLast) {
					if (STATS) STAT_T_INNER++;
					return true;
				}

				if (tileIndex < outerFirst) {
					tileIndex = outerFirst - 1;
					continue;
				} else if (tileIndex > outerLast) {
					break;
				}

				if ((~word & computeTileCoverage(tileIndex - rowBase)) != 0) {
					if (STATS) STAT_T_COVER++;
					return true;
				}
			}
		}

		if (STATS) STAT_T_HIDDEN++;
		return false;
	}

	// Returns whether part of a quad is covered.
	final boolean isQuadPartiallyOccluded(int v0, int v1, int v2, int v3) {
		dilate = false;
		if (prepareBounds(v0, v1, v2, v3) == BOUNDS_OUTSIDE_OR_TOO_SMALL) {
			return false;
		}

		final int px = minPixelX;
		final int py = minPixelY;

		if (px == maxPixelX && py == maxPixelY) {
			return px >= 0 && py >= 0 && px < pixelWidth && py < pixelHeight && isPixelSet(px, py);
		} else {
			return isQuadPartiallyOccluded();
		}
	}

	// Returns whether part of the prepared quad is covered.
	final boolean isQuadPartiallyOccluded() {
		final int minTileX = minPixelX >> TILE_AXIS_SHIFT;
		final int maxTileX = maxPixelX >> TILE_AXIS_SHIFT;
		final int minTileY = minPixelY >> TILE_AXIS_SHIFT;
		final int maxTileY = maxPixelY >> TILE_AXIS_SHIFT;
		final long[] tiles = this.tiles;

		for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
			final int rowBase = tileY * tileWidth;
			boolean populated = false;

			for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
				final int tileIndex = rowBase + tileX;
				final long word = tiles[tileIndex];

				if (word == 0L) {
					continue;
				}

				if (!populated) {
					populateBand(tileY);
					populated = true;
				}

				if ((word & computeTileCoverage(tileIndex - rowBase)) != 0) {
					return true;
				}
			}
		}

		return false;
	}

	// Draws the prepared quad.
	final void drawQuad() {
		final int minTileX = minPixelX >> TILE_AXIS_SHIFT;
		final int maxTileX = maxPixelX >> TILE_AXIS_SHIFT;
		final int minTileY = minPixelY >> TILE_AXIS_SHIFT;
		final int maxTileY = maxPixelY >> TILE_AXIS_SHIFT;
		final long[] tiles = this.tiles;
		final long[] fullBits = this.fullBits;
		for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
			final int rowBase = tileY * tileWidth;
			final int last = rowBase + maxTileX;
			boolean populated = false;
			int innerFirst = 0, innerLast = -1, outerFirst = 0, outerLast = -1;

			for (int tileIndex = rowBase + minTileX; tileIndex <= last; tileIndex++) {
				final int wordIndex = tileIndex >> 6;
				final long open = ~fullBits[wordIndex] >>> (tileIndex & 63);

				if (open == 0) {
					tileIndex |= 63;
					continue;
				}

				tileIndex += Long.numberOfTrailingZeros(open);

				if (tileIndex > last) {
					break;
				}

				if (!populated) {
					populateBand(tileY);
					populated = true;
					innerFirst = rowBase + bandInnerFirst;
					innerLast = Math.min(last, rowBase + bandInnerLast);
					outerFirst = rowBase + bandOuterFirst;
					outerLast = Math.min(last, rowBase + bandOuterLast);
				}

				if (tileIndex < outerFirst) {
					tileIndex = outerFirst - 1;
					continue;
				} else if (tileIndex > outerLast) {
					break;
				}

				if (tileIndex >= innerFirst && tileIndex <= innerLast) {
					if (STATS) STAT_D_INNER += innerLast - tileIndex + 1;
					Arrays.fill(tiles, tileIndex, innerLast + 1, -1L);
					markFull(fullBits, tileIndex, innerLast);
					tileIndex = innerLast;
					continue;
				}

				if (STATS) STAT_D_COVER++;
				final long word = tiles[tileIndex] | computeTileCoverage(tileIndex - rowBase);
				tiles[tileIndex] = word;

				if (word == -1L) {
					fullBits[wordIndex] |= 1L << (tileIndex & 63);
				}
			}
		}

		if (STATS && !edgesReady) STAT_D_NOOP++;
	}

	// Marks a range of tiles as full.
	private static void markFull(long[] fullBits, int from, int to) {
		final int firstWord = from >> 6;
		final int lastWord = to >> 6;
		final long head = -1L << (from & 63);
		final long tail = -1L >>> (63 - (to & 63));

		if (firstWord == lastWord) {
			fullBits[firstWord] |= head & tail;
		} else {
			fullBits[firstWord] |= head;

			for (int w = firstWord + 1; w < lastWord; w++) {
				fullBits[w] = -1L;
			}

			fullBits[lastWord] |= tail;
		}
	}

	// Calculates the bounds of a quad.
	abstract int prepareBounds(int v0, int v1, int v2, int v3);

	// Fills the events of a flat edge.
	private void populateFlatEvents(int position, int slot) {
		final int[] eventData = this.eventData;
		final int[] data = this.vertexData;
		final int y0In = data[slot + 1];
		final int bandFirst = eventY0;
		final int bandLast = eventLimit >> 1;
		final int grow = dilate ? TEST_DILATION_PIXELS : 0;

		if (position == EDGE_TOP) {
			final int edgeRow = (y0In + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
			final int py = edgeRow + 1 + grow;

			if (py > lastPixelY) return;

			final int start = (py < bandFirst ? bandFirst : py) << 1;
			final int limit = bandLast << 1;

			assert limit < eventData.length;

			for (int y = start; y <= limit; ) {
				eventData[y++] = pixelWidth;
				eventData[y++] = -1;
			}

			for (int i = 1; i <= grow; i++) {
				spanRow(edgeRow + i, slot, bandFirst, bandLast);
			}
		} else if (position == EDGE_BOTTOM) {
			final int edgeRow = y0In >> PRECISION_BITS;
			final int py = edgeRow - grow;

			if (py <= 0) return;

			final int start = bandFirst << 1;
			final int end = py > bandLast + 1 ? bandLast + 1 : py;
			final int limit = end > lastPixelY ? (lastPixelY << 1) : (end << 1);

			assert limit < eventData.length;

			for (int y = start; y < limit; ) {
				eventData[y++] = pixelWidth;
				eventData[y++] = -1;
			}

			for (int i = 1; i <= grow; i++) {
				spanRow(edgeRow - i, slot, bandFirst, bandLast);
			}
		} else {
			assert position == EDGE_POINT;
		}
	}

	// Fills a row between the edges.
	private void spanRow(int row, int slot, int bandFirst, int bandLast) {
		if (row < bandFirst || row > bandLast || row < 0 || row > lastPixelY) {
			return;
		}

		final int[] data = this.vertexData;
		final int xa = data[slot];
		final int xb = data[slot + 2];
		final int[] eventData = this.eventData;
		eventData[row << 1] = ((xa < xb ? xa : xb) + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
		eventData[(row << 1) + 1] = ((xa < xb ? xb : xa) + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
	}

	// Fills the events of the left edges.
	private void populateLeftEvents() {
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit;

		for (int y = y0 << 1; y <= limit; y += 2) {
			eventData[y] = 0;
		}
	}

	// Fills the events of one left edge.
	private void populateLeftEvents(int a) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit;
		final int ea = (a - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;

		for (int y = (y0 << 1); y <= limit; y += 2) {
			eventData[y] = (int) (ax >> 20);
			ax += aStep;
		}
	}

	// Fills the events of the right edges.
	private void populateRightEvents() {
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit + 1;

		for (int y = (y0 << 1) + 1; y <= limit; y += 2) {
			eventData[y] = pixelWidth;
		}
	}

	// Fills the events of one right edge.
	private void populateRightEvents(int a) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit + 1;
		final int ea = (a - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;

		for (int y = (y0 << 1) + 1; y <= limit; y += 2) {
			eventData[y] = (int) (ax >> 20);
			ax += aStep;
		}
	}

	// Fills the events of two left edges.
	private void populateLeftEvents2(int a, int b) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit;
		final int ea = (a - IDX_AX0) >> 2;
		final int eb = (b - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;
		final long bStep = edgeStep[eb];
		long bx = edgeX0[eb] + bStep * y0;

		for (int y = (y0 << 1); y <= limit; y += 2) {
			eventData[y] = (int) ((ax > bx ? ax : bx) >> 20);
			ax += aStep;
			bx += bStep;
		}
	}

	// Fills the events of three left edges.
	private void populateLeftEvents3(int a, int b, int c) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit;
		final int ea = (a - IDX_AX0) >> 2;
		final int eb = (b - IDX_AX0) >> 2;
		final int ec = (c - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;
		final long bStep = edgeStep[eb];
		long bx = edgeX0[eb] + bStep * y0;
		final long cStep = edgeStep[ec];
		long cx = edgeX0[ec] + cStep * y0;

		for (int y = (y0 << 1); y <= limit; y += 2) {
			final long i = ax > bx ? ax : bx;
			eventData[y] = (int) ((cx > i ? cx : i) >> 20);
			ax += aStep;
			bx += bStep;
			cx += cStep;
		}
	}

	// Fills the events of four left edges.
	private void populateLeftEvents4(int a, int b, int c, int d) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit;
		final int ea = (a - IDX_AX0) >> 2;
		final int eb = (b - IDX_AX0) >> 2;
		final int ec = (c - IDX_AX0) >> 2;
		final int ed = (d - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;
		final long bStep = edgeStep[eb];
		long bx = edgeX0[eb] + bStep * y0;
		final long cStep = edgeStep[ec];
		long cx = edgeX0[ec] + cStep * y0;
		final long dStep = edgeStep[ed];
		long dx = edgeX0[ed] + dStep * y0;

		for (int y = (y0 << 1); y <= limit; y += 2) {
			final long i = ax > bx ? ax : bx;
			final long j = cx > dx ? cx : dx;
			eventData[y] = (int) ((i > j ? i : j) >> 20);
			ax += aStep;
			bx += bStep;
			cx += cStep;
			dx += dStep;
		}
	}

	// Fills the events of two right edges.
	private void populateRightEvents2(int a, int b) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit + 1;
		final int ea = (a - IDX_AX0) >> 2;
		final int eb = (b - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;
		final long bStep = edgeStep[eb];
		long bx = edgeX0[eb] + bStep * y0;

		for (int y = (y0 << 1) + 1; y <= limit; y += 2) {
			eventData[y] = (int) ((ax < bx ? ax : bx) >> 20);
			ax += aStep;
			bx += bStep;
		}
	}

	// Fills the events of three right edges.
	private void populateRightEvents3(int a, int b, int c) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit + 1;
		final int ea = (a - IDX_AX0) >> 2;
		final int eb = (b - IDX_AX0) >> 2;
		final int ec = (c - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;
		final long bStep = edgeStep[eb];
		long bx = edgeX0[eb] + bStep * y0;
		final long cStep = edgeStep[ec];
		long cx = edgeX0[ec] + cStep * y0;

		for (int y = (y0 << 1) + 1; y <= limit; y += 2) {
			final long i = ax < bx ? ax : bx;
			eventData[y] = (int) ((cx < i ? cx : i) >> 20);
			ax += aStep;
			bx += bStep;
			cx += cStep;
		}
	}

	// Fills the events of four right edges.
	private void populateRightEvents4(int a, int b, int c, int d) {
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;
		final int[] eventData = this.eventData;
		final int y0 = eventY0;
		final int limit = eventLimit + 1;
		final int ea = (a - IDX_AX0) >> 2;
		final int eb = (b - IDX_AX0) >> 2;
		final int ec = (c - IDX_AX0) >> 2;
		final int ed = (d - IDX_AX0) >> 2;
		final long aStep = edgeStep[ea];
		long ax = edgeX0[ea] + aStep * y0;
		final long bStep = edgeStep[eb];
		long bx = edgeX0[eb] + bStep * y0;
		final long cStep = edgeStep[ec];
		long cx = edgeX0[ec] + cStep * y0;
		final long dStep = edgeStep[ed];
		long dx = edgeX0[ed] + dStep * y0;

		for (int y = (y0 << 1) + 1; y <= limit; y += 2) {
			final long i = ax < bx ? ax : bx;
			final long j = cx < dx ? cx : dx;
			eventData[y] = (int) ((i < j ? i : j) >> 20);
			ax += aStep;
			bx += bStep;
			cx += cStep;
			dx += dStep;
		}
	}

	// Projects one vertex.
	abstract void setupVertex(int baseIndex, int x, int y, int z);

	// Returns whether a point is not covered.
	abstract boolean isPointClear(float x, float y, float z);

	// Projects the corners of a box.
	abstract boolean setupBoxVertices(int[] corners, int x0, int y0, int z0, int x1, int y1, int z1);

	// Transformed corners of a box.
	protected final float[] cornerX = new float[8], cornerY = new float[8], cornerZ = new float[8], cornerW = new float[8];

	// Transforms the corners of a box.
	final void transformBoxCorners(int x0, int y0, int z0, int x1, int y1, int z1) {
		final float fx = x0 + offsetX;
		final float fy = y0 + offsetY;
		final float fz = z0 + offsetZ;
		final float ex = x1 - x0;
		final float ey = y1 - y0;
		final float ez = z1 - z0;

		final float[] cx = cornerX, cy = cornerY, cz = cornerZ, cw = cornerW;

		final float bx = m00 * fx + m01 * fy + m02 * fz + m03;
		final float by = m10 * fx + m11 * fy + m12 * fz + m13;
		final float bz = m20 * fx + m21 * fy + m22 * fz + m23;
		final float bw = m30 * fx + m31 * fy + m32 * fz + m33;

		final float dxx = m00 * ex, dxy = m10 * ex, dxz = m20 * ex, dxw = m30 * ex;
		final float dyx = m01 * ey, dyy = m11 * ey, dyz = m21 * ey, dyw = m31 * ey;
		final float dzx = m02 * ez, dzy = m12 * ez, dzz = m22 * ez, dzw = m32 * ez;

		cx[0] = bx; cy[0] = by; cz[0] = bz; cw[0] = bw;
		cx[1] = bx + dzx; cy[1] = by + dzy; cz[1] = bz + dzz; cw[1] = bw + dzw;
		cx[2] = bx + dyx; cy[2] = by + dyy; cz[2] = bz + dyz; cw[2] = bw + dyw;
		cx[3] = cx[2] + dzx; cy[3] = cy[2] + dzy; cz[3] = cz[2] + dzz; cw[3] = cw[2] + dzw;
		cx[4] = bx + dxx; cy[4] = by + dxy; cz[4] = bz + dxz; cw[4] = bw + dxw;
		cx[5] = cx[4] + dzx; cy[5] = cy[4] + dzy; cz[5] = cz[4] + dzz; cw[5] = cw[4] + dzw;
		cx[6] = cx[4] + dyx; cy[6] = cy[4] + dyy; cz[6] = cz[4] + dyz; cw[6] = cw[4] + dyw;
		cx[7] = cx[6] + dzx; cy[7] = cy[6] + dzy; cz[7] = cz[6] + dzz; cw[7] = cw[6] + dzw;
	}

	// Returns a float rounded to an int.
	static int roundToInt(float v) {
		return (int) Math.floor(v + 0.5);
	}

	// Returns whether a projected point is not covered.
	final boolean isProjectedPointClear(float tx, float ty, float iw) {
		final float sx = tx * iw;
		final float sy = ty * iw;

		if (!(sx >= -1f && sx < 1f && sy >= -1f && sy < 1f)) {
			return false;
		}

		final int px = (int) ((sx + 1f) * halfPixelWidth);
		final int py = (int) ((sy + 1f) * halfPixelHeight);

		if (px >= pixelWidth || py >= pixelHeight) {
			return false;
		}

		final int tileIndex = (px >> TILE_AXIS_SHIFT) + ((py >> TILE_AXIS_SHIFT) * tileWidth);

		return (tiles[tileIndex] & (1L << (((py & TILE_PIXEL_INDEX_MASK) << TILE_AXIS_SHIFT) | (px & TILE_PIXEL_INDEX_MASK)))) == 0;
	}

	// Returns whether a vertex needs clipping at the near plane.
	int needsNearClip(final int baseIndex) {
		final int[] data = vertexData;
		final float w = Float.intBitsToFloat(data[baseIndex + PV_W]);
		final float z = Float.intBitsToFloat(data[baseIndex + PV_Z]);

		return (w > 0 && z > 0) ? 0 : 1;
	}

	// Returns the coverage of a tile column.
	long computeTileCoverage(int tileX) {
		final int[] data = eventData;
		final int tileFirstX = tileX << TILE_AXIS_SHIFT;
		final int tileLastX = tileFirstX + 7;

		final int b = eventY0 << 1;

		return rowCoverage(data[b], data[b + 1], tileFirstX, tileLastX)
				| rowCoverage(data[b + 2], data[b + 3], tileFirstX, tileLastX) << 8
				| rowCoverage(data[b + 4], data[b + 5], tileFirstX, tileLastX) << 16
				| rowCoverage(data[b + 6], data[b + 7], tileFirstX, tileLastX) << 24
				| rowCoverage(data[b + 8], data[b + 9], tileFirstX, tileLastX) << 32
				| rowCoverage(data[b + 10], data[b + 11], tileFirstX, tileLastX) << 40
				| rowCoverage(data[b + 12], data[b + 13], tileFirstX, tileLastX) << 48
				| rowCoverage(data[b + 14], data[b + 15], tileFirstX, tileLastX) << 56;
	}

	// Returns the bits of a row between two pixels.
	private static long rowCoverage(int left, int right, int tileFirstX, int tileLastX) {
		final int l = Math.min(Math.max(left - tileFirstX, 0), 8);
		final int r = Math.min(Math.max(tileLastX - right, 0), 8);
		return (0xFFL << l) & (0xFFL >>> r);
	}

	// Returns whether a pixel is not covered.
	public boolean isPixelClear(int x, int y) {
		return (tiles[tileIndexFromPixelXY(x, y)] & (1L << (Indexer.pixelIndex(x, y)))) == 0;
	}

	// Returns whether a pixel is covered.
	public boolean isPixelSet(int x, int y) {
		return (tiles[tileIndexFromPixelXY(x, y)] & (1L << (Indexer.pixelIndex(x, y)))) != 0;
	}

	// Marks a pixel as covered.
	void drawPixel(int x, int y) {
		tiles[tileIndexFromPixelXY(x, y)] |= (1L << (Indexer.pixelIndex(x, y)));
	}

	// Fills the events of one event combination.
	@FunctionalInterface
	interface EventFiller {
		void apply();
	}

	// Returns the position class of an edge.
	final int edgePosition(int x0In, int y0In, int x1In, int y1In) {
		final int dy = y1In - y0In;
		final int dx = x1In - x0In;
		return (1 << (((-dy >> 31) | (dy >>> 31)) + 1)) | (1 << (((dx >> 31) | (-dx >>> 31)) + 4));
	}

	// Calculates the bounds of a quad without clipping.
	final int prepareBoundsNoClip(int v0, int v1, int v2, int v3) {
		final int[] data = vertexData;
		int ax0, ay0, ax1, ay1;
		int bx0, by0, bx1, by1;
		int cx0, cy0, cx1, cy1;
		int dx0, dy0, dx1, dy1;
		int minY = 0, maxY = 0, minX = 0, maxX = 0;

		ax0 = data[v0 + PV_PX];
		ay0 = data[v0 + PV_PY];
		bx0 = data[v1 + PV_PX];
		by0 = data[v1 + PV_PY];
		cx0 = data[v2 + PV_PX];
		cy0 = data[v2 + PV_PY];
		dx0 = data[v3 + PV_PX];
		dy0 = data[v3 + PV_PY];

		ax1 = bx0;
		ay1 = by0;
		bx1 = cx0;
		by1 = cy0;
		cx1 = dx0;
		cy1 = dy0;
		dx1 = ax0;
		dy1 = ay0;

		minX = ax0;
		maxX = ax0;

		if (bx0 < minX) {
			minX = bx0;
		} else if (bx0 > maxX) {
			maxX = bx0;
		}

		if (cx0 < minX) {
			minX = cx0;
		} else if (cx0 > maxX) {
			maxX = cx0;
		}

		if (dx0 < minX) {
			minX = dx0;
		} else if (dx0 > maxX) {
			maxX = dx0;
		}

		minY = ay0;
		maxY = ay0;

		if (by0 < minY) {
			minY = by0;
		} else if (by0 > maxY) {
			maxY = by0;
		}

		if (cy0 < minY) {
			minY = cy0;
		} else if (cy0 > maxY) {
			maxY = cy0;
		}

		if (dy0 < minY) {
			minY = dy0;
		} else if (dy0 > maxY) {
			maxY = dy0;
		}

		if (maxY <= 0 || minY >= preciseHeight) {
			return BOUNDS_OUTSIDE_OR_TOO_SMALL;
		}

		if (maxX <= 0 || minX >= preciseWidth) {
			return BOUNDS_OUTSIDE_OR_TOO_SMALL;
		}

		if (minX < 0) {
			minX = 0;
		}

		if (maxX >= preciseWidthClamp) {
			maxX = preciseWidthClamp;

			if (minX > preciseWidthClamp) {
				minX = preciseWidthClamp;
			}
		}

		if (minY < 0) {
			minY = 0;
		}

		if (maxY >= preciseHeightClamp) {
			maxY = preciseHeightClamp;

			if (minY > preciseHeightClamp) {
				minY = preciseHeightClamp;
			}
		}

		final int minPixelX = ((minX + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS);
		final int minPixelY = ((minY + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS);
		final int maxPixelX = ((maxX + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS);
		final int maxPixelY = ((maxY + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS);

		final int position0 = edgePosition(ax0, ay0, ax1, ay1);
		final int position1 = edgePosition(bx0, by0, bx1, by1);
		final int position2 = edgePosition(cx0, cy0, cx1, cy1);
		final int position3 = edgePosition(dx0, dy0, dx1, dy1);

		this.minPixelX = minPixelX;
		this.minPixelY = minPixelY;
		this.maxPixelX = maxPixelX;
		this.maxPixelY = maxPixelY;
		data[IDX_AX0] = ax0;
		data[IDX_AY0] = ay0;
		data[IDX_AX1] = ax1;
		data[IDX_AY1] = ay1;
		data[IDX_BX0] = bx0;
		data[IDX_BY0] = by0;
		data[IDX_BX1] = bx1;
		data[IDX_BY1] = by1;
		data[IDX_CX0] = cx0;
		data[IDX_CY0] = cy0;
		data[IDX_CX1] = cx1;
		data[IDX_CY1] = cy1;
		data[IDX_DX0] = dx0;
		data[IDX_DY0] = dy0;
		data[IDX_DX1] = dx1;
		data[IDX_DY1] = dy1;
		pos0 = position0;
		pos1 = position1;
		pos2 = position2;
		pos3 = position3;

		final int eventKey = (position0 - 1) & EVENT_POSITION_MASK
				| (((position1 - 1) & EVENT_POSITION_MASK) << 2)
				| (((position2 - 1) & EVENT_POSITION_MASK) << 4)
				| (((position3 - 1) & EVENT_POSITION_MASK) << 6);

		prepareEvents(eventKey);
		return BOUNDS_IN;
	}

	// Prepares the events of an event key.
	void prepareEvents(int eventKey) {
		this.eventKey = eventKey;
		this.edgesReady = false;
		this.genericEvents = false;
	}

	// Calculates the bounds of a polygon without clipping.
	final int preparePolygonNoClip(int[] vertices, int count) {
		final int[] data = vertexData;

		int minX = data[vertices[0] + PV_PX];
		int maxX = minX;
		int minY = data[vertices[0] + PV_PY];
		int maxY = minY;

		for (int i = 1; i < count; i++) {
			final int x = data[vertices[i] + PV_PX];
			final int y = data[vertices[i] + PV_PY];
			if (x < minX) minX = x; else if (x > maxX) maxX = x;
			if (y < minY) minY = y; else if (y > maxY) maxY = y;
		}

		if (maxY <= 0 || minY >= preciseHeight || maxX <= 0 || minX >= preciseWidth) {
			return BOUNDS_OUTSIDE_OR_TOO_SMALL;
		}

		if (minX < 0) minX = 0;

		if (maxX >= preciseWidthClamp) {
			maxX = preciseWidthClamp;
			if (minX > preciseWidthClamp) minX = preciseWidthClamp;
		}

		if (minY < 0) minY = 0;

		if (maxY >= preciseHeightClamp) {
			maxY = preciseHeightClamp;
			if (minY > preciseHeightClamp) minY = preciseHeightClamp;
		}

		minPixelX = (minX + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
		minPixelY = (minY + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
		maxPixelX = (maxX + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
		maxPixelY = (maxY + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;

		polyMinRow = minPixelY;
		polyMaxRow = (maxY - PRECISE_PIXEL_CENTER) >> PRECISION_BITS;

		this.polyVertices = vertices;
		this.polyCount = count;
		this.genericEvents = true;
		this.edgesReady = false;
		return BOUNDS_IN;
	}

	// Draws a polygon.
	final void drawPolygon(int[] vertices, int count) {
		if (STATS) STAT_DRAW_CALLS++;
		dilate = false;

		if (preparePolygonNoClip(vertices, count) == BOUNDS_OUTSIDE_OR_TOO_SMALL) {
			return;
		}

		if (STATS) STAT_DRAW_QUADS++;

		if (minPixelX == maxPixelX && minPixelY == maxPixelY) {
			return;
		}

		drawQuad();
	}

	// Returns whether the pixel of any vertex is not covered.
	final boolean isAnyVertexPixelClear(int[] vertices, int count) {
		final int[] data = vertexData;
		final long[] tiles = this.tiles;
		final int lastPixelX = this.lastPixelX;
		final int lastPixelY = this.lastPixelY;

		for (int i = 0; i < count; i++) {
			final int v = vertices[i];
			final int px = data[v + PV_PX] >> PRECISION_BITS;
			final int py = data[v + PV_PY] >> PRECISION_BITS;

			if (px < 0 || py < 0 || px > lastPixelX || py > lastPixelY) {
				continue;
			}

			final int tileIndex = (px >> TILE_AXIS_SHIFT) + ((py >> TILE_AXIS_SHIFT) * tileWidth);

			if ((tiles[tileIndex] & (1L << (((py & TILE_PIXEL_INDEX_MASK) << TILE_AXIS_SHIFT) | (px & TILE_PIXEL_INDEX_MASK)))) == 0) {
				if (STATS) STAT_T_VERTEX++;
				return true;
			}
		}

		return false;
	}

	// Returns whether part of a polygon is not covered.
	final boolean isPolygonPartiallyClear(int[] vertices, int count) {
		if (STATS) STAT_TEST_CALLS++;
		dilate = true;

		if (preparePolygonNoClip(vertices, count) == BOUNDS_OUTSIDE_OR_TOO_SMALL) {
			return false;
		}

		if (STATS) STAT_TEST_QUADS++;

		dilateBounds();

		if (isSmallPixelBoxCovered()) {
			if (STATS) STAT_T_BOX_HIDDEN++;
			return false;
		}

		return isQuadPartiallyClear();
	}

	// Largest box in tiles that is checked directly.
	private static final int COVERED_BOX_MAX_TILES = 16;

	// Returns whether a small box is fully covered.
	private boolean isSmallPixelBoxCovered() {
		final int minTileX = minPixelX >> TILE_AXIS_SHIFT;
		final int maxTileX = maxPixelX >> TILE_AXIS_SHIFT;
		final int minTileY = minPixelY >> TILE_AXIS_SHIFT;
		final int maxTileY = maxPixelY >> TILE_AXIS_SHIFT;

		if ((maxTileX - minTileX + 1) * (maxTileY - minTileY + 1) > COVERED_BOX_MAX_TILES) {
			return false;
		}

		final long[] tiles = this.tiles;
		final int tileWidth = this.tileWidth;

		for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
			final int r0 = tileY == minTileY ? minPixelY & TILE_PIXEL_INDEX_MASK : 0;
			final int r1 = tileY == maxTileY ? maxPixelY & TILE_PIXEL_INDEX_MASK : 7;
			final long rowsMask = (-1L << (r0 << TILE_AXIS_SHIFT)) & (-1L >>> ((7 - r1) << TILE_AXIS_SHIFT));
			final int rowBase = tileY * tileWidth;

			for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
				final int c0 = tileX == minTileX ? minPixelX & TILE_PIXEL_INDEX_MASK : 0;
				final int c1 = tileX == maxTileX ? maxPixelX & TILE_PIXEL_INDEX_MASK : 7;
				final long mask = (((0xFFL << c0) & (0xFFL >>> (7 - c1))) * 0x0101010101010101L) & rowsMask;

				if ((tiles[rowBase + tileX] & mask) != mask) {
					return false;
				}
			}
		}

		return true;
	}

	// Prepares the edges of the quad.
	private void prepareEdges() {
		final int eventKey = this.eventKey;
		final int[] data = vertexData;
		final long[] edgeX0 = this.edgeX0;
		final long[] edgeStep = this.edgeStep;

		for (int e = 0; e < 4; e++) {
			final int role = (eventKey >> (e << 1)) & EVENT_POSITION_MASK;

			if (role == EVENT_0_FLAT) {
				continue;
			}

			final int i = IDX_AX0 + (e << 2);
			final int x0 = data[i];
			final int y0 = data[i + 1];
			final int dx = data[i + 2] - x0;

			if (dx == 0) {
				edgeStep[e] = 0;
				edgeX0[e] = (long) ((x0 + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS) << 20;
			} else {
				final long n = (long) ((double) (((long) dx) << 16) / (data[i + 3] - y0));
				edgeStep[e] = n << PRECISION_BITS;
				edgeX0[e] = ((long) x0 << 16) - n * y0 + (role == EVENT_0_LEFT ? 0x100000L : 0x7FFFFL);
			}
		}
	}

	// Prepares the edges of any polygon.
	private void prepareEdgesGeneric() {
		final int[] data = vertexData;
		final int[] vertices = this.polyVertices;
		final int count = this.polyCount;

		int descending = 0;
		int y0 = data[vertices[0] + PV_PY];

		for (int e = 0; e < count; e++) {
			final int y1 = data[vertices[e + 1 == count ? 0 : e + 1] + PV_PY];

			if (y1 < y0) {
				descending |= 1 << e;
			} else if (y1 == y0) {
				descending |= 1 << (e + 8);
			}

			y0 = y1;
		}

		final int flat = descending >>> 8;
		final int all = (1 << count) - 1;
		descending &= all;
		leftCount = prepareChain(descending, true, leftX0, leftStep, leftEnd);
		rightCount = prepareChain(all & ~descending & ~flat, false, rightX0, rightStep, rightEnd);
	}

	// Prepares one chain of edges and returns its edge count.
	private int prepareChain(int members, boolean left, long[] x0Out, long[] stepOut, int[] endOut) {
		final int[] data = vertexData;
		final int[] vertices = this.polyVertices;
		final int count = this.polyCount;
		final int[] chain = this.chainEdges;

		if (members == 0) {
			return 0;
		}

		final int predecessors = ((members << 1) | (members >>> (count - 1))) & ((1 << count) - 1);
		int start = Integer.numberOfTrailingZeros(members & ~predecessors);
		int chained = 0;

		for (int e = start; (members & (1 << e)) != 0; ) {
			chain[chained++] = e;

			if (++e == count) {
				e = 0;
			}
		}

		int emitted = 0;

		for (int i = 0; i < chained; i++) {
			final int e = chain[left ? chained - 1 - i : i];
			final int v0 = vertices[e];
			final int v1 = vertices[e + 1 == count ? 0 : e + 1];
			final int ex0 = data[v0 + PV_PX], ey0 = data[v0 + PV_PY];
			final int ex1 = data[v1 + PV_PX], ey1 = data[v1 + PV_PY];
			final int yLow = Math.min(ey0, ey1), yHigh = Math.max(ey0, ey1);

			final int rowLow = (yLow + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS;
			final int rowHigh = (yHigh - PRECISE_PIXEL_CENTER) >> PRECISION_BITS;

			if (rowLow > rowHigh) {
				continue;
			}

			final int dx = ex1 - ex0;

			if (dx == 0) {
				stepOut[emitted] = 0;
				x0Out[emitted] = (long) ((ex0 + SCANT_PRECISE_PIXEL_CENTER) >> PRECISION_BITS) << 20;
			} else {
				final long n = (long) ((double) (((long) dx) << 16) / (ey1 - ey0));
				stepOut[emitted] = n << PRECISION_BITS;
				x0Out[emitted] = ((long) ex0 << 16) - n * ey0 + (left ? 0x100000L : 0x7FFFFL);
			}

			endOut[emitted++] = rowHigh;
		}

		return emitted;
	}

	// Fills the events of a band for any polygon.
	private void populateBandGeneric(int y0) {
		final int[] eventData = this.eventData;
		final long[] leftX0 = this.leftX0;
		final long[] leftStep = this.leftStep;
		final int[] leftEnd = this.leftEnd;
		final long[] rightX0 = this.rightX0;
		final long[] rightStep = this.rightStep;
		final int[] rightEnd = this.rightEnd;
		final int lastLeft = leftCount - 1;
		final int lastRight = rightCount - 1;
		final int grow = dilate ? TEST_DILATION_PIXELS : 0;
		final int rowMin = polyMinRow;
		final int rowMax = polyMaxRow;
		final int firstRow = rowMin - grow;
		final int lastRow = rowMax + grow;
		final int emptyLeft = pixelWidth;
		final int screenRight = lastPixelX;

		final int firstActive = y0 < rowMin ? rowMin : (y0 > rowMax ? rowMax : y0);
		int li = 0, ri = 0;
		while (li < lastLeft && firstActive > leftEnd[li]) li++;
		while (ri < lastRight && firstActive > rightEnd[ri]) ri++;
		long lx0 = lastLeft < 0 ? 0 : leftX0[li], ls = lastLeft < 0 ? 0 : leftStep[li];
		long rx0 = lastRight < 0 ? 0 : rightX0[ri], rs = lastRight < 0 ? 0 : rightStep[ri];
		int lEnd = li < lastLeft ? leftEnd[li] : Integer.MAX_VALUE;
		int rEnd = ri < lastRight ? rightEnd[ri] : Integer.MAX_VALUE;

		int maxLeft = Integer.MIN_VALUE, minRight = Integer.MAX_VALUE;
		int minLeft = Integer.MAX_VALUE, maxRight = Integer.MIN_VALUE;

		for (int y = y0, i = y0 << 1, end = y0 + 8; y < end; y++, i += 2) {
			int l, r;

			if (y < firstRow || y > lastRow) {
				l = emptyLeft;
				r = -1;
			} else {
				final int ey = y < rowMin ? rowMin : (y > rowMax ? rowMax : y);

				if (ey > lEnd) {
					li++;
					lx0 = leftX0[li];
					ls = leftStep[li];
					lEnd = li < lastLeft ? leftEnd[li] : Integer.MAX_VALUE;
				}

				if (ey > rEnd) {
					ri++;
					rx0 = rightX0[ri];
					rs = rightStep[ri];
					rEnd = ri < lastRight ? rightEnd[ri] : Integer.MAX_VALUE;
				}

				l = (lastLeft < 0 ? 0 : (int) ((lx0 + ls * ey) >> 20)) - grow;
				r = (lastRight < 0 ? screenRight : (int) ((rx0 + rs * ey) >> 20)) + grow;

				if (l < minLeft) minLeft = l;
				if (r > maxRight) maxRight = r;
			}

			eventData[i] = l;
			eventData[i + 1] = r;
			if (l > maxLeft) maxLeft = l;
			if (r < minRight) minRight = r;
		}

		setBandRanges(maxLeft, minRight, minLeft, maxRight);
	}

	// Stores the ranges of a band.
	private void setBandRanges(int maxLeft, int minRight, int minLeft, int maxRight) {
		bandInnerFirst = (maxLeft + 7) >> TILE_AXIS_SHIFT;
		bandInnerLast = (minRight - 7) >> TILE_AXIS_SHIFT;

		if (minLeft <= maxRight) {
			bandOuterFirst = Math.max(minLeft, 0) >> TILE_AXIS_SHIFT;
			bandOuterLast = Math.min(maxRight, lastPixelX) >> TILE_AXIS_SHIFT;
		} else {
			bandOuterFirst = tileWidth;
			bandOuterLast = -1;
		}
	}

	// Fills the events of the band of a tile row.
	final void populateBand(int tileY) {
		final boolean generic = this.genericEvents;

		if (!edgesReady) {
			if (generic) {
				prepareEdgesGeneric();
			} else {
				prepareEdges();
			}

			edgesReady = true;
		}

		final int y0 = tileY << TILE_AXIS_SHIFT;
		eventY0 = y0;
		eventLimit = (y0 + 7) << 1;
		if (STATS) STAT_EVENT_ROWS += 8;

		if (generic) {
			populateBandGeneric(y0);
			return;
		}

		EVENT_FILLERS[eventKey].apply();

		final int[] data = eventData;
		final int grow = dilate ? TEST_DILATION_PIXELS : 0;
		int maxLeft = Integer.MIN_VALUE, minRight = Integer.MAX_VALUE;
		int minLeft = Integer.MAX_VALUE, maxRight = Integer.MIN_VALUE;

		for (int i = y0 << 1, limit = i + 16; i < limit; i += 2) {
			int l = data[i];
			int r = data[i + 1];

			if (l <= r) {
				l -= grow;
				r += grow;
				data[i] = l;
				data[i + 1] = r;
				if (l < minLeft) minLeft = l;
				if (r > maxRight) maxRight = r;
			}

			if (l > maxLeft) maxLeft = l;
			if (r < minRight) minRight = r;
		}

		setBandRanges(maxLeft, minRight, minLeft, maxRight);
	}
}
