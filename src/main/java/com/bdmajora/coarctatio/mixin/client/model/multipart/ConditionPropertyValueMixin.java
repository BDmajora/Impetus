package com.bdmajora.coarctatio.mixin.client.model.multipart;

import com.bdmajora.coarctatio.state.ConditionCanonicalizer;
import com.google.common.base.Predicate;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.multipart.ConditionPropertyValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ConditionPropertyValue.
@Mixin(ConditionPropertyValue.class)
public class ConditionPropertyValueMixin {
    // Reference to the field key of the target.
    @Shadow
    @Final
    private String key;

    // Reference to the field value of the target.
    @Shadow
    @Final
    private String value;

    // Runs at the start of getPredicate and can cancel it.
    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true)
    private void coarctatio$canonicalize(BlockStateContainer container,
                                        CallbackInfoReturnable<Predicate<IBlockState>> cir) {
        cir.setReturnValue(ConditionCanonicalizer.propertyValue(container, this.key, this.value));
    }
}
