package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.FaceLightOcclusion;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// Table of the faces of each block that stop light.
@SuppressWarnings("deprecation")
public final class FaceOcclusion {
    private static final Reference2ObjectOpenHashMap<Block, long[]> FACE_SOLIDITY = new Reference2ObjectOpenHashMap<>();
    // Has sided transparency.
    private static final Set<Block> HAS_SIDED_TRANSPARENCY = Collections.newSetFromMap(new IdentityHashMap<>());

    // Facings by axis and direction.
    private static final EnumFacing[] AXIS_TO_FACING = {
            EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.UP, EnumFacing.DOWN
    };

    // Flag set once the tables are filled.
    private static volatile boolean registered;

    // Prevents instantiation
    private FaceOcclusion() {
    }

    // Returns whether a block passes light on some faces only.
    public static boolean hasSidedTransparency(Block block) {
        return HAS_SIDED_TRANSPARENCY.contains(block);
    }

    // Returns whether a face of a block state stops light.
    public static boolean isFaceSolid(Block block, int meta, int axisDir) {
        long[] bits = FACE_SOLIDITY.get(block);
        if (bits == null || meta < 0 || meta > 15 || axisDir < 0 || axisDir > 5) {
            return true;
        }
        int bitIndex = meta * 6 + axisDir;
        return (bits[bitIndex >> 6] & (1L << (bitIndex & 63))) != 0;
    }

    // Returns the opacity of a state toward a direction.
    public static int resolveScalarAbsorption(IBlockState state, int dirOrdinal) {
        Block block = state.getBlock();
        if (block instanceof FaceLightOcclusion) {
            return Math.max(1, ((FaceLightOcclusion) block).getDirectionalLightOpacity(state, AXIS_TO_FACING[dirOrdinal]));
        }
        int opacity = state.getLightOpacity();
        if (opacity > 1 && hasSidedTransparency(block)) {
            return isFaceSolid(block, block.getMetaFromState(state), dirOrdinal) ? opacity : 1;
        }
        return Math.max(1, opacity);
    }

    // Fills the tables of the vanilla blocks.
    public static synchronized void registerDefaults() {
        if (registered) {
            return;
        }
        int count = 0;
        FakeBlockAccess fake = new FakeBlockAccess();

        for (Block block : ForgeRegistries.BLOCKS) {
            if (block instanceof FaceLightOcclusion) {
                HAS_SIDED_TRANSPARENCY.add(block);
                count++;
                continue;
            }

            boolean anySidedDifference = false;
            long bits0 = 0, bits1 = 0;

            for (int meta = 0; meta < 16; meta++) {
                IBlockState state;
                try {
                    state = block.getStateFromMeta(meta);
                    if (state.isFullCube() || state.getLightOpacity() <= 0) {
                        continue;
                    }
                } catch (Throwable t) {
                    continue;
                }
                fake.state = state;

                for (int dir = 0; dir < 6; dir++) {
                    boolean solid;
                    try {
                        solid = block.isSideSolid(state, fake, BlockPos.ORIGIN, AXIS_TO_FACING[dir]);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (solid) {
                        int bitIndex = meta * 6 + dir;
                        if (bitIndex < 64) {
                            bits0 |= 1L << bitIndex;
                        } else {
                            bits1 |= 1L << (bitIndex - 64);
                        }
                    } else {
                        anySidedDifference = true;
                    }
                }
            }

            if (anySidedDifference) {
                FACE_SOLIDITY.put(block, new long[]{bits0, bits1});
                HAS_SIDED_TRANSPARENCY.add(block);
                count++;
            }
        }

        registered = true;
        Fulgor.LOGGER.info("Registered {} blocks with per-face light transparency", count);
    }

    // Block access that returns one state at every position.
    private static final class FakeBlockAccess implements IBlockAccess {
        // State returned at every position.
        IBlockState state = Blocks.AIR.getDefaultState();

        // Returns the block state.
        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ORIGIN) ? this.state : Blocks.AIR.getDefaultState();
        }

        // Returns whether air block.
        @Override
        public boolean isAirBlock(BlockPos pos) {
            return !pos.equals(BlockPos.ORIGIN);
        }

        // Returns the tile entity.
        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        // Returns the combined light.
        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return lightValue;
        }

        // Returns the strong power.
        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        // Returns the biome.
        @Override
        public Biome getBiome(BlockPos pos) {
            return Biome.getBiomeForId(0);
        }

        // Returns whether side solid.
        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return pos.equals(BlockPos.ORIGIN) ? this.state.isSideSolid(this, pos, side) : _default;
        }

        // Returns the world type.
        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }
    }
}
