package com.bdmajora.coarctatio.mixin.state;

import com.bdmajora.coarctatio.state.CoarctatioExtendedBlockState;
import com.bdmajora.coarctatio.state.MappedStateOwner;
import com.bdmajora.coarctatio.state.PropertyValueMapper;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// Mixin that changes ExtendedBlockState.
@Mixin(ExtendedBlockState.class)
public abstract class ExtendedBlockStateMixin {
    // Runs at the start of createState and can cancel it.
    @Inject(method = "createState", at = @At("HEAD"), cancellable = true, remap = false)
    private void coarctatio$createPackedExtendedState(Block block,
                                                     ImmutableMap<IProperty<?>, Comparable<?>> properties,
                                                     ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlistedProperties,
                                                     CallbackInfoReturnable<BlockStateContainer.StateImplementation> cir) {
        if (unlistedProperties == null || unlistedProperties.isEmpty()) {
            return;
        }

        PropertyValueMapper mapper = ((MappedStateOwner) this).coarctatio$mapper(block);

        if (mapper == null) {
            return;
        }

        cir.setReturnValue(new CoarctatioExtendedBlockState(mapper, block, properties, unlistedProperties,
                coarctatio$hasPresentValue(unlistedProperties)));
    }

    // Returns whether any unlisted property has a value.
    @Unique
    private static boolean coarctatio$hasPresentValue(ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlisted) {
        for (Optional<?> value : unlisted.values()) {
            if (value.isPresent()) {
                return true;
            }
        }

        return false;
    }
}
