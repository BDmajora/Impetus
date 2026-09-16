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

// Per-face light occlusion: a FaceLightOcclusion block answers itself, a block found at post-init to have a non-solid side on a non-full-cube state gets a 16-meta x 6-face table, everything else uses its scalar opacity
@SuppressWarnings("deprecation")
public final class FaceOcclusion {
    // Block to 96 packed face-solidity bits
    private static final Reference2ObjectOpenHashMap<Block, long[]> FACE_SOLIDITY = new Reference2ObjectOpenHashMap<>();
    private static final Set<Block> HAS_SIDED_TRANSPARENCY = Collections.newSetFromMap(new IdentityHashMap<>());

    // Axis direction ordinal to facing, matching BfsLightEngine.AxisDirection
    private static final EnumFacing[] AXIS_TO_FACING = {
            EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.UP, EnumFacing.DOWN
    };

    private static volatile boolean registered;

    private FaceOcclusion() {
    }

    public static boolean hasSidedTransparency(Block block) {
        return HAS_SIDED_TRANSPARENCY.contains(block);
    }

    // Whether a face of a table block is solid; only meaningful for blocks with sided transparency that do not implement the interface, and metas above 15 count as solid
    public static boolean isFaceSolid(Block block, int meta, int axisDir) {
        long[] bits = FACE_SOLIDITY.get(block);
        if (bits == null || meta > 15) {
            return true;
        }
        int bitIndex = meta * 6 + axisDir;
        return (bits[bitIndex >> 6] & (1L << (bitIndex & 63))) != 0;
    }

    // Absorption (1-15) into a state through one face, from the interface, the table, or the scalar opacity
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

    // Probes every registered block's 16 metas x 6 faces with isSideSolid at post-init, once mods have finished shaping their blocks
    public static void registerDefaults() {
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
                // A modded block can throw on a meta it never uses, or on a cube query without a tile entity; either just skips that meta
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

    // Minimal world for probing isSideSolid: the state under test at the origin, air everywhere else
    private static final class FakeBlockAccess implements IBlockAccess {
        IBlockState state = Blocks.AIR.getDefaultState();

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ORIGIN) ? this.state : Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return !pos.equals(BlockPos.ORIGIN);
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return lightValue;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return Biome.getBiomeForId(0);
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return pos.equals(BlockPos.ORIGIN) ? this.state.isSideSolid(this, pos, side) : _default;
        }

        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }
    }
}
