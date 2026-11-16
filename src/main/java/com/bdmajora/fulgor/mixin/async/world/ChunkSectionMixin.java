package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import com.bdmajora.fulgor.async.WorldLightManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkSectionMixin {
    // Reference to the field x of the target.
    @Shadow
    @Final
    public int x;

    // Reference to the field z of the target.
    @Shadow
    @Final
    public int z;

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Reference to the method get block storage array of the target.
    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    // Field section was empty added to the target.
    @Unique
    private boolean fulgor$sectionWasEmpty;

    // Runs at the start of setBlockState.
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void fulgor$preSetBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        int sectionY = pos.getY() >> 4;
        ExtendedBlockStorage[] storage = getBlockStorageArray();
        this.fulgor$sectionWasEmpty = sectionY >= 0 && sectionY < storage.length && storage[sectionY] == Chunk.NULL_BLOCK_STORAGE;
    }

    // Runs before every return of setBlockState.
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void fulgor$postSetBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        if (!this.fulgor$sectionWasEmpty || cir.getReturnValue() == null) {
            return;
        }

        int sectionY = pos.getY() >> 4;
        ExtendedBlockStorage[] storage = getBlockStorageArray();
        if (sectionY < 0 || sectionY >= storage.length) {
            return;
        }
        ExtendedBlockStorage section = storage[sectionY];
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            return;
        }

        this.fulgor$sectionWasEmpty = false;
        AsyncLitChunk lit = (AsyncLitChunk) (Object) this;
        ChunkLightHelper.fillVanillaFromEngine(lit.fulgor$getSkyNibbles(), lit.fulgor$getBlockNibbles(), section, sectionY,
                this.world.provider.hasSkyLight());

        if (this.world.isRemote) {
            NibbleArray blockNibble = section.getBlockLight();
            if (blockNibble != null) {
                lit.fulgor$getBlockNibbles()[sectionY + 1] = new SWMRNibbleArray(blockNibble.getData());
            }
            NibbleArray skyNibble = section.getSkyLight();
            if (skyNibble != null) {
                lit.fulgor$getSkyNibbles()[sectionY + 1] = new SWMRNibbleArray(skyNibble.getData());
            }
        }

        WorldLightManager manager = ((AsyncLitWorld) this.world).fulgor$getLightManager();
        if (manager != null) {
            manager.queueSectionChange(this.x, sectionY, this.z, false);
        }
    }
}
