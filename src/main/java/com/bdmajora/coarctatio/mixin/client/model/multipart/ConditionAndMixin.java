package com.bdmajora.coarctatio.mixin.client.model.multipart;

import com.bdmajora.coarctatio.state.ConditionCanonicalizer;
import com.google.common.base.Predicate;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.multipart.ConditionAnd;
import net.minecraft.client.renderer.block.model.multipart.ICondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ConditionAnd.
@Mixin(ConditionAnd.class)
public class ConditionAndMixin {
    // Reference to the field conditions of the target.
    @Shadow
    @Final
    Iterable<ICondition> conditions;

    // Runs at the start of getPredicate and can cancel it.
    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true)
    private void coarctatio$flatten(BlockStateContainer container,
                                   CallbackInfoReturnable<Predicate<IBlockState>> cir) {
        cir.setReturnValue(ConditionCanonicalizer.all(
                ConditionCanonicalizer.resolve(this.conditions, container)));
    }
}
