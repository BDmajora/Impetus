package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.FaceLightOcclusion;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
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
    // Closed face bits by block for 16 metas and the sided metas.
    private static final Reference2ObjectOpenHashMap<Block, long[]> FACE_SOLIDITY = new Reference2ObjectOpenHashMap<>();
    // Blocks with at least one sided meta and every FaceLightOcclusion block.
    private static final Set<Block> HAS_SIDED_TRANSPARENCY = Collections.newSetFromMap(new IdentityHashMap<>());
    // All six face bits
    private static final int ALL_FACES = (1 << 6) - 1;

    // Facings by axis and direction.
    private static final EnumFacing[] AXIS_TO_FACING = {
            EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.UP, EnumFacing.DOWN
    };

    // Flag set once the tables are filled.
    private static volatile boolean registered;

    // Prevents instantiation
    private FaceOcclusion() {
    }

    // Returns whether any meta of a block passes light on some faces only.
    public static boolean hasSidedTransparency(Block block) {
        return HAS_SIDED_TRANSPARENCY.contains(block);
    }

    // Returns the closed face bits of a sided meta or -1 for a meta that treats every face alike.
    public static int sidedFaces(Block block, int meta) {
        long[] table = FACE_SOLIDITY.get(block);
        if (table == null || meta < 0 || meta > 15 || (table[2] & (1L << meta)) == 0) {
            return -1;
        }
        int faces = 0;
        for (int dir = 0; dir < 6; ++dir) {
            int bitIndex = meta * 6 + dir;
            if ((table[bitIndex >> 6] & (1L << (bitIndex & 63))) != 0) {
                faces |= 1 << dir;
            }
        }
        return faces;
    }

    // Returns whether a face of a block state stops light.
    public static boolean isFaceSolid(Block block, int meta, int axisDir) {
        int faces = sidedFaces(block, meta);
        return faces < 0 || axisDir < 0 || axisDir > 5 || (faces & (1 << axisDir)) != 0;
    }

    // Returns the opacity of a state toward a direction.
    public static int resolveScalarAbsorption(IBlockState state, int dirOrdinal) {
        Block block = state.getBlock();
        if (block instanceof FaceLightOcclusion) {
            return Math.max(1, ((FaceLightOcclusion) block).getDirectionalLightOpacity(state, AXIS_TO_FACING[dirOrdinal]));
        }
        int opacity = state.getLightOpacity();
        if (opacity > 1 && hasSidedTransparency(block)) {
            int faces = sidedFaces(block, block.getMetaFromState(state));
            if (faces >= 0) {
                return (faces & (1 << dirOrdinal)) != 0 ? opacity : 1;
            }
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

            long[] table = new long[3];
            for (int meta = 0; meta < 16; meta++) {
                IBlockState state;
                try {
                    state = block.getStateFromMeta(meta);
                    if (state.isFullCube() || state.getLightOpacity() <= 0) {
                        continue;
                    }
                } catch (VirtualMachineError fatal) {
                    throw fatal;
                } catch (Throwable t) {
                    continue;
                }
                fake.state = state;

                int closed = 0;
                for (int dir = 0; dir < 6; dir++) {
                    boolean solid;
                    try {
                        solid = block.isSideSolid(state, fake, BlockPos.ORIGIN, AXIS_TO_FACING[dir]);
                    } catch (VirtualMachineError fatal) {
                        throw fatal;
                    } catch (Throwable t) {
                        // A face that cannot be asked keeps the block's plain opacity
                        solid = true;
                    }
                    if (solid) {
                        closed |= 1 << dir;
                    }
                }
                // Leaves a meta with no solid face or all solid faces unsided.
                if (closed == 0 || closed == ALL_FACES) {
                    continue;
                }
                // Adds the faces the bounding box fills.
                closed |= coveredFaces(state, fake);
                if (closed == ALL_FACES) {
                    continue;
                }
                for (int dir = 0; dir < 6; dir++) {
                    if ((closed & (1 << dir)) != 0) {
                        int bitIndex = meta * 6 + dir;
                        table[bitIndex >> 6] |= 1L << (bitIndex & 63);
                    }
                }
                table[2] |= 1L << meta;
            }

            if (table[2] != 0) {
                FACE_SOLIDITY.put(block, table);
                HAS_SIDED_TRANSPARENCY.add(block);
                count++;
            }
        }

        registered = true;
        Fulgor.LOGGER.info("Registered {} blocks with per-face light transparency", count);
    }

    // Returns the faces a state's bounding box fills or none for a whole cube box.
    static int coveredFaces(IBlockState state, IBlockAccess access) {
        AxisAlignedBB box;
        try {
            box = state.getBoundingBox(access, BlockPos.ORIGIN);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            return 0;
        }
        if (box == null) {
            return 0;
        }
        boolean spansX = box.minX <= 0 && box.maxX >= 1;
        boolean spansY = box.minY <= 0 && box.maxY >= 1;
        boolean spansZ = box.minZ <= 0 && box.maxZ >= 1;
        if (spansX && spansY && spansZ) {
            return 0;
        }
        int faces = 0;
        if (box.maxX >= 1 && spansY && spansZ) {
            faces |= 1;
        }
        if (box.minX <= 0 && spansY && spansZ) {
            faces |= 1 << 1;
        }
        if (box.maxZ >= 1 && spansX && spansY) {
            faces |= 1 << 2;
        }
        if (box.minZ <= 0 && spansX && spansY) {
            faces |= 1 << 3;
        }
        if (box.maxY >= 1 && spansX && spansZ) {
            faces |= 1 << 4;
        }
        if (box.minY <= 0 && spansX && spansZ) {
            faces |= 1 << 5;
        }
        return faces;
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

        // Returns whether a position is air.
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

        // Returns whether a side of the probed state is solid at the origin.
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
