package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.SWMRNibbleArray;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;

// Maintains section-nibble topology and reconciles the four horizontal chunk edges for one engine; shares the engine's window and queues, so it lives and dies with it
final class LightSectionProcessor {
    private final BfsLightEngine engine;
    private final int[] centerDelayedUpdates = new int[16 * 16];
    private final int[] neighbourDelayedUpdates = new int[16 * 16];

    LightSectionProcessor(BfsLightEngine engine) {
        this.engine = engine;
    }

    // Applies sparse emptiness changes and initialises the 3x3x3 nibble neighbourhood of any section that became non-empty; returns the new emptiness map when one was created
    boolean[] handleEmptySectionChanges(Chunk chunk, Boolean[] emptinessChanges, boolean unlit) {
        BfsLightEngine engine = this.engine;
        int chunkX = chunk.x;
        int chunkZ = chunk.z;

        boolean[] chunkEmptinessMap = engine.getEmptinessMap(chunkX, chunkZ);
        boolean[] initializedMap = null;
        boolean needsInit = unlit || chunkEmptinessMap == null;

        if (needsInit) {
            initializedMap = chunkEmptinessMap = new boolean[16];
            engine.setEmptinessMapCache(chunkX, chunkZ, chunkEmptinessMap);
        }

        for (int sectionY = emptinessChanges.length - 1; sectionY >= 0; --sectionY) {
            Boolean empty = emptinessChanges[sectionY];
            if (empty == null) {
                if (!needsInit) {
                    continue;
                }
                empty = BfsLightEngine.isEmptyOfBlocks(engine.getChunkSection(chunkX, sectionY, chunkZ)) ? Boolean.TRUE : Boolean.FALSE;
                emptinessChanges[sectionY] = empty;
            }
            chunkEmptinessMap[sectionY] = empty;
        }

        for (int sectionY = emptinessChanges.length - 1; sectionY >= 0; --sectionY) {
            Boolean empty = emptinessChanges[sectionY];
            if (empty == null || empty) {
                continue;
            }
            for (int deltaZ = -1; deltaZ <= 1; ++deltaZ) {
                for (int deltaX = -1; deltaX <= 1; ++deltaX) {
                    boolean extrude = (deltaX | deltaZ) != 0 || !unlit;
                    for (int deltaY = 1; deltaY >= -1; --deltaY) {
                        engine.initNibble(chunkX + deltaX, sectionY + deltaY, chunkZ + deltaZ, extrude, false);
                    }
                }
            }
        }

        refreshLazyNibbles(chunkX, chunkZ, unlit);
        return initializedMap;
    }

    // Nulls a section whose whole 3x3x3 neighbourhood is empty (once all its neighbours are known) and initialises any that is not
    private void refreshLazyNibbles(int chunkX, int chunkZ, boolean unlit) {
        BfsLightEngine engine = this.engine;
        for (int deltaZ = -1; deltaZ <= 1; ++deltaZ) {
            for (int deltaX = -1; deltaX <= 1; ++deltaX) {
                boolean neighboursLoaded = true;
                neighbourLoadedSearch:
                for (int neighbourZ = -1; neighbourZ <= 1; ++neighbourZ) {
                    for (int neighbourX = -1; neighbourX <= 1; ++neighbourX) {
                        if (engine.getEmptinessMap(chunkX + deltaX + neighbourX, chunkZ + deltaZ + neighbourZ) == null) {
                            neighboursLoaded = false;
                            break neighbourLoadedSearch;
                        }
                    }
                }

                for (int sectionY = LightEngineCache.MAX_LIGHT_SECTION; sectionY >= LightEngineCache.MIN_LIGHT_SECTION; --sectionY) {
                    boolean allEmpty = isNeighbourhoodEmpty(chunkX + deltaX, sectionY, chunkZ + deltaZ);
                    if (allEmpty & neighboursLoaded) {
                        engine.setNibbleNull(chunkX + deltaX, sectionY, chunkZ + deltaZ);
                    } else if (!allEmpty) {
                        boolean extrude = (deltaX | deltaZ) != 0 || !unlit;
                        engine.initNibble(chunkX + deltaX, sectionY, chunkZ + deltaZ, extrude, false);
                    }
                }
            }
        }
    }

    private boolean isNeighbourhoodEmpty(int chunkX, int sectionY, int chunkZ) {
        BfsLightEngine engine = this.engine;
        for (int deltaY = -1; deltaY <= 1; ++deltaY) {
            for (int deltaZ = -1; deltaZ <= 1; ++deltaZ) {
                for (int deltaX = -1; deltaX <= 1; ++deltaX) {
                    int neighbourY = sectionY + deltaY;
                    if (neighbourY < LightEngineCache.MIN_SECTION || neighbourY > LightEngineCache.MAX_SECTION) {
                        continue;
                    }
                    boolean[] emptinessMap = engine.getEmptinessMap(chunkX + deltaX, chunkZ + deltaZ);
                    if (emptinessMap != null) {
                        if (!emptinessMap[neighbourY]) {
                            return false;
                        }
                    } else {
                        if (!BfsLightEngine.isEmptyOfBlocks(engine.getChunkSection(chunkX + deltaX, neighbourY, chunkZ + deltaZ))) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    void checkChunkEdges(Chunk chunk, int fromSection, int toSection) {
        for (int sectionY = toSection; sectionY >= fromSection; --sectionY) {
            this.engine.checkChunkEdge(chunk.x, sectionY, chunk.z);
        }
        this.engine.performLightDecrease();
    }

    // Compares both sides of each horizontal edge of one section against what they should be, skipping pairs that are both full or both empty
    void checkChunkEdge(int chunkX, int chunkY, int chunkZ) {
        BfsLightEngine engine = this.engine;
        int currentCacheIndex = chunkX + 5 * chunkZ + (5 * 5) * chunkY + engine.chunkSectionIndexOffset;
        SWMRNibbleArray currentNibble = engine.nibbleCache[currentCacheIndex];
        if (currentNibble == null) {
            return;
        }

        for (BfsLightEngine.AxisDirection direction : BfsLightEngine.ONLY_HORIZONTAL_DIRECTIONS) {
            int neighbourCacheIndex = (chunkX + direction.x) + 5 * (chunkZ + direction.z) + (5 * 5) * chunkY + engine.chunkSectionIndexOffset;
            SWMRNibbleArray neighbourNibble = engine.nibbleCache[neighbourCacheIndex];
            if (neighbourNibble == null) {
                continue;
            }
            if (!currentNibble.isInitialisedUpdating() && !neighbourNibble.isInitialisedUpdating()) {
                continue;
            }
            if (engine.areBothEdgeSectionsFull(currentCacheIndex, neighbourCacheIndex)) {
                continue;
            }
            if (engine.areBothEdgeSectionsZero(currentCacheIndex, neighbourCacheIndex)) {
                continue;
            }

            reconcileEdge(chunkX, chunkY, chunkZ, direction, currentCacheIndex, neighbourCacheIndex);
        }
    }

    private void reconcileEdge(int chunkX, int chunkY, int chunkZ, BfsLightEngine.AxisDirection direction,
                               int currentCacheIndex, int neighbourCacheIndex) {
        BfsLightEngine engine = this.engine;
        int neighbourOffsetX = direction.x;
        int neighbourOffsetZ = direction.z;
        int incrementX;
        int incrementZ;
        int startX;
        int startZ;
        if (neighbourOffsetX != 0) {
            incrementX = 0;
            incrementZ = 1;
            startX = direction.x < 0 ? chunkX << 4 : chunkX << 4 | 15;
            startZ = chunkZ << 4;
        } else {
            incrementX = 1;
            incrementZ = 0;
            startZ = neighbourOffsetZ < 0 ? chunkZ << 4 : chunkZ << 4 | 15;
            startX = chunkX << 4;
        }

        int centerDelayedCount = 0;
        int neighbourDelayedCount = 0;
        for (int worldY = chunkY << 4, maxY = worldY | 15; worldY <= maxY; ++worldY) {
            for (int index = 0, worldX = startX, worldZ = startZ; index < 16; ++index, worldX += incrementX, worldZ += incrementZ) {
                int neighbourX = worldX + neighbourOffsetX;
                int neighbourZ = worldZ + neighbourOffsetZ;
                int currentIndex = (worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8);
                int currentLevel = engine.getLightLevel(currentCacheIndex, currentIndex);
                int neighbourIndex = (neighbourX & 15) | ((neighbourZ & 15) << 4) | ((worldY & 15) << 8);
                int neighbourLevel = engine.getLightLevel(neighbourCacheIndex, neighbourIndex);

                if (currentLevel == 0 && neighbourLevel == 0) {
                    continue;
                }

                // No consistency shortcut: it is invalid for a seam opacity above one
                if (engine.calculateLightValue(worldX, worldY, worldZ, currentLevel) != currentLevel) {
                    this.centerDelayedUpdates[centerDelayedCount++] = currentIndex;
                }
                if (engine.calculateLightValue(neighbourX, worldY, neighbourZ, neighbourLevel) != neighbourLevel) {
                    this.neighbourDelayedUpdates[neighbourDelayedCount++] = neighbourIndex;
                }
            }
        }

        enqueueMismatches(chunkX, chunkY, chunkZ, direction, centerDelayedCount, neighbourDelayedCount);
    }

    private void enqueueMismatches(int chunkX, int chunkY, int chunkZ, BfsLightEngine.AxisDirection direction,
                                   int centerCount, int neighbourCount) {
        int currentChunkOffsetX = chunkX << 4;
        int currentChunkOffsetZ = chunkZ << 4;
        int neighbourChunkOffsetX = (chunkX + direction.x) << 4;
        int neighbourChunkOffsetZ = (chunkZ + direction.z) << 4;
        int chunkOffsetY = chunkY << 4;
        int maxCount = Math.max(centerCount, neighbourCount);
        for (int index = 0; index < maxCount; ++index) {
            if (index < centerCount) {
                int value = this.centerDelayedUpdates[index];
                this.engine.checkBlock(currentChunkOffsetX | (value & 15), chunkOffsetY | (value >>> 8), currentChunkOffsetZ | ((value >>> 4) & 15));
            }
            if (index < neighbourCount) {
                int value = this.neighbourDelayedUpdates[index];
                this.engine.checkBlock(neighbourChunkOffsetX | (value & 15), chunkOffsetY | (value >>> 8), neighbourChunkOffsetZ | ((value >>> 4) & 15));
            }
        }
    }

    // Seeds the increase queue from every lit neighbour edge column, so a freshly lit chunk receives its neighbours' light without a full edge check
    void propagateNeighbourLevels(Chunk chunk, int fromSection, int toSection) {
        BfsLightEngine engine = this.engine;
        int chunkX = chunk.x;
        int chunkZ = chunk.z;

        for (int sectionY = toSection; sectionY >= fromSection; --sectionY) {
            SWMRNibbleArray currentNibble = engine.getNibbleFromCache(chunkX, sectionY, chunkZ);
            if (currentNibble == null) {
                continue;
            }
            for (BfsLightEngine.AxisDirection direction : BfsLightEngine.ONLY_HORIZONTAL_DIRECTIONS) {
                int neighbourOffsetX = direction.x;
                int neighbourOffsetZ = direction.z;
                int neighbourCacheIndex = (chunkX + neighbourOffsetX) + 5 * (chunkZ + neighbourOffsetZ) + (5 * 5) * sectionY + engine.chunkSectionIndexOffset;
                if (engine.nibbleCache[neighbourCacheIndex] == null || !engine.nibbleCache[neighbourCacheIndex].isInitialisedUpdating()) {
                    continue;
                }

                int incrementX;
                int incrementZ;
                int startX;
                int startZ;
                if (neighbourOffsetX != 0) {
                    incrementX = 0;
                    incrementZ = 1;
                    startX = direction.x < 0 ? (chunkX << 4) - 1 : (chunkX << 4) + 16;
                    startZ = chunkZ << 4;
                } else {
                    incrementX = 1;
                    incrementZ = 0;
                    startZ = neighbourOffsetZ < 0 ? (chunkZ << 4) - 1 : (chunkZ << 4) + 16;
                    startX = chunkX << 4;
                }

                long propagateDirection = 1L << direction.oppositeOrdinal;
                int encodeOffset = engine.coordinateOffset;
                for (int worldY = sectionY << 4, maxY = worldY | 15; worldY <= maxY; ++worldY) {
                    for (int index = 0, worldX = startX, worldZ = startZ; index < 16; ++index, worldX += incrementX, worldZ += incrementZ) {
                        int localIndex = (worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8);
                        int level = engine.getLightLevel(neighbourCacheIndex, localIndex);
                        if (engine.isBelowPropagationThreshold(level)) {
                            continue;
                        }
                        int edgeCacheIndex = (worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + engine.chunkSectionIndexOffset;
                        IBlockState edgeState = engine.getBlockStateFast(edgeCacheIndex, worldX & 15, worldY & 15, worldZ & 15);
                        engine.appendToIncreaseQueue(BfsLightEngine.encodeCoords(worldX, worldZ, worldY, encodeOffset)
                                | engine.encodeQueueLevel(level)
                                | (propagateDirection << BfsLightEngine.DIRECTION_SHIFT)
                                | BfsLightEngine.sidedFlag(edgeState));
                    }
                }
            }
        }
    }
}
