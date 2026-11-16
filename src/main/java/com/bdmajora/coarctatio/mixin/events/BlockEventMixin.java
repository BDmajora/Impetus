package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// Mixin that changes BlockEvent.
@Mixin(value = BlockEvent.class, remap = false)
public abstract class BlockEventMixin extends Event implements RecyclableEvent {
    // Reference to the field world of the target.
    @Shadow
    @Final
    @Mutable
    private World world;

    // Reference to the field pos of the target.
    @Shadow
    @Final
    @Mutable
    private BlockPos pos;

    // Reference to the field state of the target.
    @Shadow
    @Final
    @Mutable
    private IBlockState state;

    // Field phase added to the target.
    @Unique
    private EventPriority coarctatio$phase;

    // Returns the phase.
    @Nullable
    @Override
    public EventPriority getPhase() {
        return this.coarctatio$phase;
    }

    // Handles set phase.
    @Override
    public void setPhase(@Nonnull EventPriority value) {
        this.coarctatio$phase = RecyclableEvent.advancePhase(this.coarctatio$phase, value);
    }

    // Handles reset phase.
    @Override
    public void coarctatio$resetPhase() {
        this.coarctatio$phase = null;
    }

    // Handles refresh block.
    @Override
    public void coarctatio$refreshBlock(World world, BlockPos pos, IBlockState state) {
        this.world = world;
        this.pos = pos;
        this.state = state;
    }
}
