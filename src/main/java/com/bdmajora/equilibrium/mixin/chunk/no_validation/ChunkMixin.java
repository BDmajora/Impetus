package com.bdmajora.equilibrium.mixin.chunk.no_validation;

import net.minecraft.util.ReportedException;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.CrashReport;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorDebug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkMixin {
    // Reference to the field storage arrays of the target.
    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Whether the chunk belongs to a debug world.
    @Unique
    private boolean equilibrium$debugWorld;

    // Reads the world type once per chunk and treats a chunk without a world as not debug.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$resolveWorldType(CallbackInfo ci) {
        this.equilibrium$debugWorld = this.world != null && this.world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES;
    }

    // Vanilla getBlockState with the world type read from the cached flag.
    @Overwrite
    public IBlockState getBlockState(final int x, final int y, final int z) {
        if (this.equilibrium$debugWorld) {
            IBlockState state = null;

            if (y == 60) {
                state = Blocks.BARRIER.getDefaultState();
            }

            if (y == 70) {
                state = ChunkGeneratorDebug.getBlockStateFor(x, z);
            }

            return state == null ? Blocks.AIR.getDefaultState() : state;
        }

        try {
            if (y >= 0 && y >> 4 < this.storageArrays.length) {
                ExtendedBlockStorage section = this.storageArrays[y >> 4];

                if (section != Chunk.NULL_BLOCK_STORAGE) {
                    return section.get(x & 15, y & 15, z & 15);
                }
            }

            return Blocks.AIR.getDefaultState();
        } catch (Throwable throwable) {
            CrashReport report = CrashReport.makeCrashReport(throwable, "Getting block state");
            CrashReportCategory category = report.makeCategory("Block being got");
            category.addDetail("Location", () -> net.minecraft.crash.CrashReportCategory.getCoordinateInfo(
                    new BlockPos(x, y, z)));
            throw new ReportedException(report);
        }
    }
}
