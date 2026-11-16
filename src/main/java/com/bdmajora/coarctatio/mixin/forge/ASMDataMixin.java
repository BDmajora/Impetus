package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.dedup.StringPool;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ASMData.
@Mixin(value = ASMDataTable.ASMData.class, remap = false)
public abstract class ASMDataMixin {
    // Reference to the field annotation name of the target.
    @Shadow
    private String annotationName;

    // Reference to the field class name of the target.
    @Shadow
    private String className;

    // Reference to the field object name of the target.
    @Shadow
    private String objectName;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$internStrings(CallbackInfo ci) {
        this.annotationName = StringPool.LOADER.deduplicate(this.annotationName);
        this.className = StringPool.LOADER.deduplicate(this.className);
        this.objectName = StringPool.LOADER.deduplicate(this.objectName);
    }
}
