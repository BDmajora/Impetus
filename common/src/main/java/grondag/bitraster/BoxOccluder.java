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

import static grondag.bitraster.Constants.CAMERA_PRECISION_BITS;
import static grondag.bitraster.Constants.CAMERA_PRECISION_UNITY;
import static grondag.bitraster.Constants.DOWN;
import static grondag.bitraster.Constants.EAST;
import static grondag.bitraster.Constants.NORTH;
import static grondag.bitraster.Constants.PROJECTED_VERTEX_STRIDE;
import static grondag.bitraster.Constants.SOUTH;
import static grondag.bitraster.Constants.UP;
import static grondag.bitraster.Constants.V000;
import static grondag.bitraster.Constants.V001;
import static grondag.bitraster.Constants.V010;
import static grondag.bitraster.Constants.V011;
import static grondag.bitraster.Constants.V100;
import static grondag.bitraster.Constants.V101;
import static grondag.bitraster.Constants.V110;
import static grondag.bitraster.Constants.V111;
import static grondag.bitraster.Constants.WEST;

import java.util.function.Consumer;

// Occlusion buffer that tests and draws boxes.
public abstract class BoxOccluder {
	// Distance of the near occluders.
	private static final int NEAR_RANGE = 8 << CAMERA_PRECISION_BITS;

	// Matrix of the scene.
	private final Matrix4L baseMvpMatrix = new Matrix4L();

	// Rasterizer that keeps the coverage.
	protected final AbstractRasterizer raster;
	// Counter that changes when the buffer is cleared.
	private int occlusionVersion = 1;
	// Tests by box type.
	protected final BoxTest[] partiallyClearTests = new BoxTest[128];
	// Tests for covered parts by box type.
	protected final BoxTest[] partiallyOccludedTests = new BoxTest[128];
	// Draws by box type.
	protected final BoxDraw[] boxDraws = new BoxDraw[128];
	// View position.
	private long viewX;
	private long viewY;
	private long viewZ;
	// Offset of the region.
	protected int offsetX;
	protected int offsetY;
	protected int offsetZ;
	// Range class of the region.
	private int occlusionRange;
	// Squared chunk distance of the region.
	private int regionSquaredChunkDist;
	// Version of the view.
	private int viewVersion = -1;
	// Version of the region.
	private int regionVersion = -1;
	// Whether the next scene redraws everything.
	private volatile boolean forceRedraw = false;
	// Largest squared chunk distance of the scene.
	private int maxSquaredChunkDistance;
	// Whether near occluders were drawn.
	private boolean hasNearOccluders = false;
	// Whether near occluders are drawn.
	private boolean drawNearOccluders = true;

	// Corners of the silhouette of a box by view position.
	private static final int[][] SILHOUETTES = new int[64][];

	// Fills the silhouette table.
	static {
		SILHOUETTES[UP] = new int[] { V110, V010, V011, V111 };
		SILHOUETTES[DOWN] = new int[] { V000, V100, V101, V001 };
		SILHOUETTES[EAST] = new int[] { V101, V100, V110, V111 };
		SILHOUETTES[WEST] = new int[] { V000, V001, V011, V010 };
		SILHOUETTES[NORTH] = new int[] { V100, V000, V010, V110 };
		SILHOUETTES[SOUTH] = new int[] { V001, V101, V111, V011 };
		SILHOUETTES[UP | EAST] = new int[] { V110, V010, V011, V111, V101, V100 };
		SILHOUETTES[UP | WEST] = new int[] { V011, V111, V110, V010, V000, V001 };
		SILHOUETTES[UP | NORTH] = new int[] { V010, V011, V111, V110, V100, V000 };
		SILHOUETTES[UP | SOUTH] = new int[] { V111, V110, V010, V011, V001, V101 };
		SILHOUETTES[DOWN | EAST] = new int[] { V101, V001, V000, V100, V110, V111 };
		SILHOUETTES[DOWN | WEST] = new int[] { V000, V100, V101, V001, V011, V010 };
		SILHOUETTES[DOWN | NORTH] = new int[] { V100, V101, V001, V000, V010, V110 };
		SILHOUETTES[DOWN | SOUTH] = new int[] { V001, V000, V100, V101, V111, V011 };
		SILHOUETTES[NORTH | EAST] = new int[] { V100, V000, V010, V110, V111, V101 };
		SILHOUETTES[NORTH | WEST] = new int[] { V010, V110, V100, V000, V001, V011 };
		SILHOUETTES[SOUTH | EAST] = new int[] { V111, V011, V001, V101, V100, V110 };
		SILHOUETTES[SOUTH | WEST] = new int[] { V001, V101, V111, V011, V010, V000 };
		SILHOUETTES[UP | EAST | NORTH] = new int[] { V011, V111, V101, V100, V000, V010 };
		SILHOUETTES[UP | WEST | NORTH] = new int[] { V111, V110, V100, V000, V001, V011 };
		SILHOUETTES[UP | EAST | SOUTH] = new int[] { V010, V011, V001, V101, V100, V110 };
		SILHOUETTES[UP | WEST | SOUTH] = new int[] { V110, V010, V000, V001, V101, V111 };
		SILHOUETTES[DOWN | EAST | NORTH] = new int[] { V001, V000, V010, V110, V111, V101 };
		SILHOUETTES[DOWN | WEST | NORTH] = new int[] { V101, V001, V011, V010, V110, V100 };
		SILHOUETTES[DOWN | EAST | SOUTH] = new int[] { V000, V100, V110, V111, V011, V001 };
		SILHOUETTES[DOWN | WEST | SOUTH] = new int[] { V100, V101, V111, V011, V010, V000 };
	}

	// Creates an occluder with a rasterizer.
	public BoxOccluder(AbstractRasterizer raster) {
		this.raster = raster;

		partiallyClearTests[0] = (x0, y0, z0, x1, y1, z1) -> {
			return false;
		};

		partiallyClearTests[UP] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V110, V010, V011, V111);
		};

		partiallyClearTests[DOWN] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			return raster.isQuadPartiallyClear(V000, V100, V101, V001);
		};

		partiallyClearTests[EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V101, V100, V110, V111);
		};

		partiallyClearTests[WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			return raster.isQuadPartiallyClear(V000, V001, V011, V010);
		};

		partiallyClearTests[NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyClear(V100, V000, V010, V110);
		};

		partiallyClearTests[SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V001, V101, V111, V011);
		};

		partiallyClearTests[UP | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V110, V010, V011, V111)
					|| raster.isQuadPartiallyClear(V101, V100, V110, V111);
		};

		partiallyClearTests[UP | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V110, V010, V011, V111)
					|| raster.isQuadPartiallyClear(V000, V001, V011, V010);
		};

		partiallyClearTests[UP | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V110, V010, V011, V111)
					|| raster.isQuadPartiallyClear(V100, V000, V010, V110);
		};

		partiallyClearTests[UP | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V110, V010, V011, V111)
					|| raster.isQuadPartiallyClear(V001, V101, V111, V011);
		};

		partiallyClearTests[DOWN | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V000, V100, V101, V001)
					|| raster.isQuadPartiallyClear(V101, V100, V110, V111);
		};

		partiallyClearTests[DOWN | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			return raster.isQuadPartiallyClear(V000, V100, V101, V001)
					|| raster.isQuadPartiallyClear(V000, V001, V011, V010);
		};

		partiallyClearTests[DOWN | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyClear(V000, V100, V101, V001)
					|| raster.isQuadPartiallyClear(V100, V000, V010, V110);
		};

		partiallyClearTests[DOWN | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V000, V100, V101, V001)
					|| raster.isQuadPartiallyClear(V001, V101, V111, V011);
		};

		partiallyClearTests[NORTH | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V100, V000, V010, V110)
					|| raster.isQuadPartiallyClear(V101, V100, V110, V111);
		};

		partiallyClearTests[NORTH | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyClear(V100, V000, V010, V110)
					|| raster.isQuadPartiallyClear(V000, V001, V011, V010);
		};

		partiallyClearTests[SOUTH | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V001, V101, V111, V011)
					|| raster.isQuadPartiallyClear(V101, V100, V110, V111);
		};

		partiallyClearTests[SOUTH | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V001, V101, V111, V011)
					|| raster.isQuadPartiallyClear(V000, V001, V011, V010);
		};

		partiallyClearTests[UP | EAST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V011, V111, V101, V100)
					|| raster.isQuadPartiallyClear(V100, V000, V010, V011);
		};

		partiallyClearTests[UP | WEST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V111, V110, V100, V000)
					|| raster.isQuadPartiallyClear(V000, V001, V011, V111);
		};

		partiallyClearTests[UP | EAST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyClear(V010, V011, V001, V101)
					|| raster.isQuadPartiallyClear(V101, V100, V110, V010);
		};

		partiallyClearTests[UP | WEST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V110, V010, V000, V001)
					|| raster.isQuadPartiallyClear(V001, V101, V111, V110);
		};

		partiallyClearTests[DOWN | EAST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V001, V000, V010, V110)
					|| raster.isQuadPartiallyClear(V110, V111, V101, V001);
		};

		partiallyClearTests[DOWN | WEST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyClear(V101, V001, V011, V010)
					|| raster.isQuadPartiallyClear(V010, V110, V100, V101);
		};

		partiallyClearTests[DOWN | EAST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V000, V100, V110, V111)
					|| raster.isQuadPartiallyClear(V111, V011, V001, V000);
		};

		partiallyClearTests[DOWN | WEST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyClear(V100, V101, V111, V011)
					|| raster.isQuadPartiallyClear(V011, V010, V000, V100);
		};

		partiallyOccludedTests[0] = (x0, y0, z0, x1, y1, z1) -> {
			return false;
		};

		partiallyOccludedTests[UP] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V110, V010, V011, V111);
		};

		partiallyOccludedTests[DOWN] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			return raster.isQuadPartiallyOccluded(V000, V100, V101, V001);
		};

		partiallyOccludedTests[EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V101, V100, V110, V111);
		};

		partiallyOccludedTests[WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			return raster.isQuadPartiallyOccluded(V000, V001, V011, V010);
		};

		partiallyOccludedTests[NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyOccluded(V100, V000, V010, V110);
		};

		partiallyOccludedTests[SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V001, V101, V111, V011);
		};

		partiallyOccludedTests[UP | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V110, V010, V011, V111)
					|| raster.isQuadPartiallyOccluded(V101, V100, V110, V111);
		};

		partiallyOccludedTests[UP | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V110, V010, V011, V111)
					|| raster.isQuadPartiallyOccluded(V000, V001, V011, V010);
		};

		partiallyOccludedTests[UP | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V110, V010, V011, V111)
					|| raster.isQuadPartiallyOccluded(V100, V000, V010, V110);
		};

		partiallyOccludedTests[UP | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V110, V010, V011, V111)
					|| raster.isQuadPartiallyOccluded(V001, V101, V111, V011);
		};

		partiallyOccludedTests[DOWN | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V000, V100, V101, V001)
					|| raster.isQuadPartiallyOccluded(V101, V100, V110, V111);
		};

		partiallyOccludedTests[DOWN | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			return raster.isQuadPartiallyOccluded(V000, V100, V101, V001)
					|| raster.isQuadPartiallyOccluded(V000, V001, V011, V010);
		};

		partiallyOccludedTests[DOWN | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyOccluded(V000, V100, V101, V001)
					|| raster.isQuadPartiallyOccluded(V100, V000, V010, V110);
		};

		partiallyOccludedTests[DOWN | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V000, V100, V101, V001)
					|| raster.isQuadPartiallyOccluded(V001, V101, V111, V011);
		};

		partiallyOccludedTests[NORTH | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V100, V000, V010, V110)
					|| raster.isQuadPartiallyOccluded(V101, V100, V110, V111);
		};

		partiallyOccludedTests[NORTH | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyOccluded(V100, V000, V010, V110)
					|| raster.isQuadPartiallyOccluded(V000, V001, V011, V010);
		};

		partiallyOccludedTests[SOUTH | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V001, V101, V111, V011)
					|| raster.isQuadPartiallyOccluded(V101, V100, V110, V111);
		};

		partiallyOccludedTests[SOUTH | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V001, V101, V111, V011)
					|| raster.isQuadPartiallyOccluded(V000, V001, V011, V010);
		};

		partiallyOccludedTests[UP | EAST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V011, V111, V101, V100)
					|| raster.isQuadPartiallyOccluded(V100, V000, V010, V011);
		};

		partiallyOccludedTests[UP | WEST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V111, V110, V100, V000)
					|| raster.isQuadPartiallyOccluded(V000, V001, V011, V111);
		};

		partiallyOccludedTests[UP | EAST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyOccluded(V010, V011, V001, V101)
					|| raster.isQuadPartiallyOccluded(V101, V100, V110, V010);
		};

		partiallyOccludedTests[UP | WEST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V110, V010, V000, V001)
					|| raster.isQuadPartiallyOccluded(V001, V101, V111, V110);
		};

		partiallyOccludedTests[DOWN | EAST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V001, V000, V010, V110)
					|| raster.isQuadPartiallyOccluded(V110, V111, V101, V001);
		};

		partiallyOccludedTests[DOWN | WEST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			return raster.isQuadPartiallyOccluded(V101, V001, V011, V010)
					|| raster.isQuadPartiallyOccluded(V010, V110, V100, V101);
		};

		partiallyOccludedTests[DOWN | EAST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V000, V100, V110, V111)
					|| raster.isQuadPartiallyOccluded(V111, V011, V001, V000);
		};

		partiallyOccludedTests[DOWN | WEST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			return raster.isQuadPartiallyOccluded(V100, V101, V111, V011)
					|| raster.isQuadPartiallyOccluded(V011, V010, V000, V100);
		};

		boxDraws[0] = (x0, y0, z0, x1, y1, z1) -> {
		};

		boxDraws[UP] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V110, V010, V011, V111);
		};

		boxDraws[DOWN] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.drawQuad(V000, V100, V101, V001);
		};

		boxDraws[EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V101, V100, V110, V111);
		};

		boxDraws[WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.drawQuad(V000, V001, V011, V010);
		};

		boxDraws[NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.drawQuad(V100, V000, V010, V110);
		};

		boxDraws[SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V001, V101, V111, V011);
		};

		boxDraws[UP | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V110, V010, V011, V111);
			raster.drawQuad(V101, V100, V110, V111);
		};

		boxDraws[UP | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V110, V010, V011, V111);
			raster.drawQuad(V000, V001, V011, V010);
		};

		boxDraws[UP | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V110, V010, V011, V111);
			raster.drawQuad(V100, V000, V010, V110);
		};

		boxDraws[UP | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V110, V010, V011, V111);
			raster.drawQuad(V001, V101, V111, V011);
		};

		boxDraws[DOWN | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V000, V100, V101, V001);
			raster.drawQuad(V101, V100, V110, V111);
		};

		boxDraws[DOWN | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.drawQuad(V000, V100, V101, V001);
			raster.drawQuad(V000, V001, V011, V010);
		};

		boxDraws[DOWN | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.drawQuad(V000, V100, V101, V001);
			raster.drawQuad(V100, V000, V010, V110);
		};

		boxDraws[DOWN | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V000, V100, V101, V001);
			raster.drawQuad(V001, V101, V111, V011);
		};

		boxDraws[NORTH | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V100, V000, V010, V110);
			raster.drawQuad(V101, V100, V110, V111);
		};

		boxDraws[NORTH | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.drawQuad(V100, V000, V010, V110);
			raster.drawQuad(V000, V001, V011, V010);
		};

		boxDraws[SOUTH | EAST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V001, V101, V111, V011);
			raster.drawQuad(V101, V100, V110, V111);
		};

		boxDraws[SOUTH | WEST] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V001, V101, V111, V011);
			raster.drawQuad(V000, V001, V011, V010);
		};

		boxDraws[UP | EAST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V011, V111, V101, V100);
			raster.drawQuad(V100, V000, V010, V011);
		};

		boxDraws[UP | WEST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V111, V110, V100, V000);
			raster.drawQuad(V000, V001, V011, V111);
		};

		boxDraws[UP | EAST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.drawQuad(V010, V011, V001, V101);
			raster.drawQuad(V101, V100, V110, V010);
		};

		boxDraws[UP | WEST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V110, V010, V000, V001);
			raster.drawQuad(V001, V101, V111, V110);
		};

		boxDraws[DOWN | EAST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V001, V000, V010, V110);
			raster.drawQuad(V110, V111, V101, V001);
		};

		boxDraws[DOWN | WEST | NORTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V110, x1, y1, z0);
			raster.drawQuad(V101, V001, V011, V010);
			raster.drawQuad(V010, V110, V100, V101);
		};

		boxDraws[DOWN | EAST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V001, x0, y0, z1);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V110, x1, y1, z0);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V000, V100, V110, V111);
			raster.drawQuad(V111, V011, V001, V000);
		};

		boxDraws[DOWN | WEST | SOUTH] = (x0, y0, z0, x1, y1, z1) -> {
			raster.setupVertex(V000, x0, y0, z0);
			raster.setupVertex(V010, x0, y1, z0);
			raster.setupVertex(V011, x0, y1, z1);
			raster.setupVertex(V100, x1, y0, z0);
			raster.setupVertex(V101, x1, y0, z1);
			raster.setupVertex(V111, x1, y1, z1);
			raster.drawQuad(V100, V101, V111, V011);
			raster.drawQuad(V011, V010, V000, V100);
		};
	}

	// Returns the state of the occluder as text.
	@Override
	public final String toString() {
		return String.format("OcclusionVersion:%d  viewX:%d  viewY:%d  viewZ:%d  offsetX:%d  offsetY:%d  offsetZ:%d viewVersion:%d  regionVersion:%d  forceRedraw:%b  matrix:%s",
				occlusionVersion, viewX, viewY, viewZ, offsetX, offsetY, offsetZ, viewVersion, regionVersion, forceRedraw, raster.mvpMatrix.toString());
	}

	// Copies the state of another occluder.
	public void copyFrom(BoxOccluder source) {
		baseMvpMatrix.copyFrom(source.baseMvpMatrix);
		raster.copyFrom(source.raster);
		viewX = source.viewX;
		viewY = source.viewY;
		viewZ = source.viewZ;

		offsetX = source.offsetX;
		offsetY = source.offsetY;
		offsetZ = source.offsetZ;

		occlusionRange = source.occlusionRange;

		viewVersion = source.viewVersion;
		regionVersion = source.regionVersion;
		occlusionVersion = source.occlusionVersion;
		maxSquaredChunkDistance = source.maxSquaredChunkDistance;

		forceRedraw = source.forceRedraw;
	}

	// Returns the counter that changes when the buffer is cleared.
	public final int occlusionVersion() {
		return occlusionVersion;
	}

	// Stores whether near occluders are drawn.
	public void drawNearOccluders(boolean val) {
		drawNearOccluders = val;
	}

	// Returns the width of the buffer in pixels.
	public final int bufferWidth() {
		return raster.pixelWidth;
	}

	// Returns the height of the buffer in pixels.
	public final int bufferHeight() {
		return raster.pixelHeight;
	}

	// Resizes the buffer in tiles.
	public final void resizeBuffer(int tileWidth, int tileHeight) {
		if (tileWidth != raster.tileWidth() || tileHeight != raster.tileHeight()) {
			raster.resize(tileWidth, tileHeight);
			forceRedraw = true;
		}
	}

	// Marks the buffer as outdated.
	public final void invalidate() {
		forceRedraw = true;
	}

	// Prepares a region for testing and drawing.
	public final void prepareRegion(int originX, int originY, int originZ, int occlusionRange, int squaredChunkDistance) {
		this.occlusionRange = occlusionRange;
		regionSquaredChunkDist = squaredChunkDistance;

		offsetX = (int) (((long) originX << CAMERA_PRECISION_BITS) - viewX);
		offsetY = (int) (((long) originY << CAMERA_PRECISION_BITS) - viewY);
		offsetZ = (int) (((long) originZ << CAMERA_PRECISION_BITS) - viewZ);

		raster.setRegionOffset(offsetX, offsetY, offsetZ);
	}

	// Prepares the buffer for a scene and returns whether it was cleared.
	public final boolean prepareScene(int viewVersion, double cameraX, double cameraY, double cameraZ, Consumer<Matrix4L> modelMatrixSetter, Consumer<Matrix4L> projectionMatrixSetter) {
		if (this.viewVersion != viewVersion) {
			final Matrix4L baseMvpMatrix = this.baseMvpMatrix;
			final Matrix4L tempMatrix = raster.mvpMatrix;

			baseMvpMatrix.loadIdentity();

			projectionMatrixSetter.accept(tempMatrix);
			baseMvpMatrix.multiply(tempMatrix);

			modelMatrixSetter.accept(tempMatrix);
			baseMvpMatrix.multiply(tempMatrix);

			viewX = Math.round(cameraX * CAMERA_PRECISION_UNITY);
			viewY = Math.round(cameraY * CAMERA_PRECISION_UNITY);
			viewZ = Math.round(cameraZ * CAMERA_PRECISION_UNITY);

			raster.setMatrix(baseMvpMatrix);
		}

		if (forceRedraw || this.viewVersion != viewVersion) {
			this.viewVersion = viewVersion;
			raster.clear();
			forceRedraw = false;
			hasNearOccluders = false;
			maxSquaredChunkDistance = 0;
			++occlusionVersion;
			return true;
		} else {
			return false;
		}
	}

	// Returns whether near occluders were drawn.
	public final boolean hasNearOccluders() {
		return hasNearOccluders;
	}

	// Returns whether a packed box is visible.
	public abstract boolean isBoxVisible(int packedBox, int fuzz);

	// Returns whether a packed box is visible from the perspective.
	protected final boolean isBoxVisibleFromPerspective(int packedBox, int fuzz) {
		final int x0 = PackedBox.x0(packedBox) - fuzz;
		final int y0 = PackedBox.y0(packedBox) - fuzz;
		final int z0 = PackedBox.z0(packedBox) - fuzz;
		final int x1 = PackedBox.x1(packedBox) + fuzz;
		final int y1 = PackedBox.y1(packedBox) + fuzz;
		final int z1 = PackedBox.z1(packedBox) + fuzz;

		final int offsetX = this.offsetX;
		final int offsetY = this.offsetY;
		final int offsetZ = this.offsetZ;

		int outcome = 0;

		if (offsetY < -(y1 << CAMERA_PRECISION_BITS)) {
			outcome |= UP;
		} else if (offsetY > -(y0 << CAMERA_PRECISION_BITS)) {
			outcome |= DOWN;
		}

		if (offsetX < -(x1 << CAMERA_PRECISION_BITS)) {
			outcome |= EAST;
		} else if (offsetX > -(x0 << CAMERA_PRECISION_BITS)) {
			outcome |= WEST;
		}

		if (offsetZ < -(z1 << CAMERA_PRECISION_BITS)) {
			outcome |= SOUTH;
		} else if (offsetZ > -(z0 << CAMERA_PRECISION_BITS)) {
			outcome |= NORTH;
		}

		final int[] silhouette = SILHOUETTES[outcome];

		if (silhouette != null && raster.setupBoxVertices(silhouette, x0, y0, z0, x1, y1, z1)) {
			return raster.isAnyVertexPixelClear(silhouette, silhouette.length)
					|| raster.isPolygonPartiallyClear(silhouette, silhouette.length);
		}

		return partiallyClearTests[outcome].apply(x0, y0, z0, x1, y1, z1);
	}

	// Returns whether the center of a packed box is not covered.
	public final boolean isBoxCenterClear(int packedBox) {
		final float cx = (PackedBox.x0(packedBox) + PackedBox.x1(packedBox)) * 0.5f;
		final float cy = (PackedBox.y0(packedBox) + PackedBox.y1(packedBox)) * 0.5f;
		final float cz = (PackedBox.z0(packedBox) + PackedBox.z1(packedBox)) * 0.5f;
		return raster.isPointClear(cx, cy, cz);
	}

	// Returns whether an empty region is visible.
	public final boolean isEmptyRegionVisible(int originX, int originY, int originZ, int fuzz) {
		prepareRegion(originX, originY, originZ, 0, 0);
		return isBoxVisible(PackedBox.FULL_BOX, fuzz);
	}

	// Draws a packed box.
	public abstract void occludeBox(int packedBox);

	// Draws a packed box from the perspective.
	protected void occludeFromPerspective(int packedBox) {
		final int x0 = PackedBox.x0(packedBox);
		final int y0 = PackedBox.y0(packedBox);
		final int z0 = PackedBox.z0(packedBox);
		final int x1 = PackedBox.x1(packedBox);
		final int y1 = PackedBox.y1(packedBox);
		final int z1 = PackedBox.z1(packedBox);

		final int offsetX = this.offsetX;
		final int offsetY = this.offsetY;
		final int offsetZ = this.offsetZ;

		int outcome = 0;

		boolean hasNear = true;

		final int top = (y1 << CAMERA_PRECISION_BITS) + offsetY;

		if (top < 0) {
			outcome |= UP;
			hasNear &= top > -NEAR_RANGE;
		} else {
			final int bottom = (y0 << CAMERA_PRECISION_BITS) + offsetY;

			if (bottom > 0) {
				outcome |= DOWN;
				hasNear &= bottom < NEAR_RANGE;
			}
		}

		final int east = (x1 << CAMERA_PRECISION_BITS) + offsetX;

		if (east < 0) {
			outcome |= EAST;
			hasNear &= east > -NEAR_RANGE;
		} else {
			final int west = (x0 << CAMERA_PRECISION_BITS) + offsetX;

			if (west > 0) {
				outcome |= WEST;
				hasNear &= west < NEAR_RANGE;
			}
		}

		final int south = (z1 << CAMERA_PRECISION_BITS) + offsetZ;

		if (south < 0) {
			outcome |= SOUTH;
			hasNear &= south > -NEAR_RANGE;
		} else {
			final int north = (z0 << CAMERA_PRECISION_BITS) + offsetZ;

			if (north > 0) {
				outcome |= NORTH;
				hasNear &= north < NEAR_RANGE;
			}
		}

		if (hasNear) {
			if (drawNearOccluders) {
				hasNearOccluders |= hasNear;
			} else {
				return;
			}
		}

		final int[] silhouette = SILHOUETTES[outcome];

		if (silhouette != null && raster.setupBoxVertices(silhouette, x0, y0, z0, x1, y1, z1)) {
			raster.drawPolygon(silhouette, silhouette.length);
		} else {
			boxDraws[outcome].apply(x0, y0, z0, x1, y1, z1);
		}
	}

	// Draws the boxes of a section.
	public final void occlude(int[] visData) {
		final int occlusionRange = this.occlusionRange;
		final int limit = visData.length;

		if (limit > 1) {
			boolean updateDist = false;

			for (int i = 1; i < limit; i++) {
				final int box = visData[i];

				if (occlusionRange > PackedBox.range(box)) {
					break;
				}

				updateDist = true;
				if (AbstractRasterizer.STATS) {
					switch (PackedBox.range(box)) {
						case PackedBox.RANGE_NEAR -> AbstractRasterizer.STAT_D_RANGE0++;
						case PackedBox.RANGE_MID -> AbstractRasterizer.STAT_D_RANGE1++;
						case PackedBox.RANGE_FAR -> AbstractRasterizer.STAT_D_RANGE2++;
						default -> AbstractRasterizer.STAT_D_RANGE3++;
					}
				}
				occludeBox(box);
			}

			if (updateDist && maxSquaredChunkDistance < regionSquaredChunkDist) {
				maxSquaredChunkDistance = regionSquaredChunkDist;
			}
		}
	}

	// Test of a box type.
	@FunctionalInterface
	protected interface BoxTest {
		boolean apply(int x0, int y0, int z0, int x1, int y1, int z1);
	}

	// Draw of a box type.
	@FunctionalInterface
	protected interface BoxDraw {
		void apply(int x0, int y0, int z0, int x1, int y1, int z1);
	}

	// Returns the largest squared chunk distance of the scene.
	public final int maxSquaredChunkDistance() {
		return maxSquaredChunkDistance;
	}
}
