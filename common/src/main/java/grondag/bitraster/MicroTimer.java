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

// Simple timer for small measurements.
public class MicroTimer {
	// Number of runs per report.
	private final int sampleSize;
	// Name in the report.
	private final String label;
	// Number of runs.
	private int hits;
	// Total time of the runs.
	private long elapsed;
	// Shortest run.
	private long min;
	// Longest run.
	private long max;
	// Start time of the current run.
	private long started;

	// Number of runs of the subset.
	private int subsetHits;
	// Total time of the subset.
	private long subsetElapsed;
	// Shortest run of the subset.
	private long subsetMin;
	// Longest run of the subset.
	private long subsetMax;

	// Creates a timer and rejects a null label.
	public MicroTimer(String label, int sampleSize) {
		this.label = java.util.Objects.requireNonNull(label, "label");
		this.sampleSize = sampleSize;
		this.min = Long.MAX_VALUE;
		this.max = Long.MIN_VALUE;
		this.subsetMin = Long.MAX_VALUE;
		this.subsetMax = Long.MIN_VALUE;
	}

	// Returns the number of runs.
	public int hits() {
		return hits;
	}

	// Returns the total time.
	public long elapsed() {
		return elapsed;
	}

	// Starts a run.
	public void start() {
		started = System.nanoTime();
	}

	// Ends a run and returns whether a report was printed.
	public boolean stop(boolean subset) {
		final long t = System.nanoTime() - started;
		elapsed += t;

		if (t < min) {
			min = t;
		}

		if (t > max) {
			max = t;
		}

		if (subset) {
			++subsetHits;
			subsetElapsed += t;

			if (t < subsetMin) {
				subsetMin = t;
			}

			if (t > subsetMax) {
				subsetMax = t;
			}
		}

		final long h = ++hits;

		if (h == sampleSize) {
			reportAndClear();
			return true;
		} else {
			return false;
		}
	}

	// Prints the statistics and clears them.
	public void reportAndClear() {
		if (hits == 0) {
			hits = 1;
		}

		System.out.println(String.format("Avg %s duration = %,d ns, min = %d, max = %d, total duration = %,d, total runs = %,d", label,
				elapsed / hits, min, max, elapsed / 1000000, hits));

		if (subsetHits > 0) {
			if (subsetElapsed == 0) {
				subsetElapsed = 1;
			}

			System.out.println(String.format("Subset avg duration = %,d ns, min = %d, max = %d, total duration = %,d, (%d) total runs = %,d",
					subsetElapsed / subsetHits, subsetMin, subsetMax, subsetElapsed / 1000000, subsetElapsed * 100L / Math.max(1L, elapsed), subsetHits));

			subsetHits = 0;
			subsetElapsed = 0;
			subsetMax = Long.MIN_VALUE;
			subsetMin = Long.MAX_VALUE;
		}

		hits = 0;
		elapsed = 0;
		max = Long.MIN_VALUE;
		min = Long.MAX_VALUE;
	}
}
