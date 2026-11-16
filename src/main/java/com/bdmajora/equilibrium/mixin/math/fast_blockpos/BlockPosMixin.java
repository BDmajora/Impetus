package com.bdmajora.equilibrium.mixin.math.fast_blockpos;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Mixin that changes BlockPos.
@Mixin(BlockPos.class)
public abstract class BlockPosMixin extends Vec3i {
    // Creates a block pos mixin from its x and y and z.
    public BlockPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    // Replaces the whole method up.
    @Overwrite
    public BlockPos up() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
    }

    // Replaces the whole method up.
    @Overwrite
    public BlockPos up(int n) {
        return n == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY() + n, this.getZ());
    }

    // Replaces the whole method down.
    @Overwrite
    public BlockPos down() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
    }

    // Replaces the whole method down.
    @Overwrite
    public BlockPos down(int n) {
        return n == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY() - n, this.getZ());
    }

    // Replaces the whole method north.
    @Overwrite
    public BlockPos north() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
    }

    // Replaces the whole method north.
    @Overwrite
    public BlockPos north(int n) {
        return n == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY(), this.getZ() - n);
    }

    // Replaces the whole method south.
    @Overwrite
    public BlockPos south() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
    }

    // Replaces the whole method south.
    @Overwrite
    public BlockPos south(int n) {
        return n == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY(), this.getZ() + n);
    }

    // Replaces the whole method west.
    @Overwrite
    public BlockPos west() {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
    }

    // Replaces the whole method west.
    @Overwrite
    public BlockPos west(int n) {
        return n == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX() - n, this.getY(), this.getZ());
    }

    // Replaces the whole method east.
    @Overwrite
    public BlockPos east() {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
    }

    // Replaces the whole method east.
    @Overwrite
    public BlockPos east(int n) {
        return n == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX() + n, this.getY(), this.getZ());
    }
}
