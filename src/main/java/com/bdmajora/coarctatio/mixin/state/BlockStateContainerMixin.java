package com.bdmajora.coarctatio.mixin.state;

import com.bdmajora.coarctatio.state.CoarctatioBlockState;
import com.bdmajora.coarctatio.state.MappedStateOwner;
import com.bdmajora.coarctatio.state.PropertyValueMapper;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// Mixin that changes BlockStateContainer.
@Mixin(BlockStateContainer.class)
public abstract class BlockStateContainerMixin implements MappedStateOwner {
    // Packed property mapper of the container's block or null.
    @Unique
    private PropertyValueMapper coarctatio$stateMapper;

    // Whether the mapper was resolved yet.
    @Unique
    private boolean coarctatio$stateMapperResolved;

    // Returns the container's mapper and creates it on first use.
    @Override
    public PropertyValueMapper coarctatio$mapper(Block block) {
        if (!this.coarctatio$stateMapperResolved) {
            this.coarctatio$stateMapperResolved = true;
            this.coarctatio$stateMapper = PropertyValueMapper.create((BlockStateContainer) (Object) this, block);
        }

        return this.coarctatio$stateMapper;
    }

    // Runs at the start of createState and can cancel it.
    @Inject(method = "createState", at = @At("HEAD"), cancellable = true, remap = false)
    private void coarctatio$createPackedState(Block block,
                                             ImmutableMap<IProperty<?>, Comparable<?>> properties,
                                             ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlistedProperties,
                                             CallbackInfoReturnable<BlockStateContainer.StateImplementation> cir) {
        PropertyValueMapper mapper = coarctatio$mapper(block);

        if (mapper == null) {
            return;
        }

        cir.setReturnValue(new CoarctatioBlockState(mapper, block, properties));
    }
}
