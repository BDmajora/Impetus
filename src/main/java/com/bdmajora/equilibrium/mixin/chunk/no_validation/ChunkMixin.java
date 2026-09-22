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

// Takes the debug-world test out of getBlockState, which every block read reaches: resolved once in the constructor into a boolean instead of a world-type lookup per read; the try/catch stays since its crash report is what makes a corruption diagnosable
@Mixin(Chunk.class)
public abstract class ChunkMixin {
    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;

    @Shadow
    @Final
    private World world;

    @Unique
    private boolean equilibrium$debugWorld;

    // Matches both constructors deliberately: the four-arg one delegates to the three-arg one so this runs twice and assigns the same value; a descriptor would need an obfuscated signature for no benefit
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$resolveWorldType(CallbackInfo ci) {
        this.equilibrium$debugWorld = this.world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES;
    }

    // Overwrite: skips the crash-report wrapping; an out-of-range read here is a programming error, not a data error
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
