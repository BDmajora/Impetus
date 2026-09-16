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

// Keeps the engine and vanilla storage aligned when setBlockState creates a previously absent section
@Mixin(Chunk.class)
public abstract class ChunkSectionMixin {
    @Shadow
    @Final
    public int x;

    @Shadow
    @Final
    public int z;

    @Shadow
    @Final
    private World world;

    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    @Unique
    private boolean fulgor$sectionWasEmpty;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void fulgor$preSetBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        int sectionY = pos.getY() >> 4;
        ExtendedBlockStorage[] storage = getBlockStorageArray();
        this.fulgor$sectionWasEmpty = sectionY >= 0 && sectionY < storage.length && storage[sectionY] == Chunk.NULL_BLOCK_STORAGE;
    }

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

        // The client shares storage with the vanilla arrays, so the new section's nibbles are wrapped like the packet's were
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
