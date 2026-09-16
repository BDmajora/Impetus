package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.dedup.StringPool;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// One ASMData per annotation on every class of every mod, kept for the session; the annotation name is one of a few hundred strings and the class name is shared by every annotation on that class and by the candidate's own class list, yet each is a fresh substring (Chibi's asmDataStringCanonicalization). remap = false, Forge class
@Mixin(value = ASMDataTable.ASMData.class, remap = false)
public abstract class ASMDataMixin {
    @Shadow
    private String annotationName;

    @Shadow
    private String className;

    @Shadow
    private String objectName;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$internStrings(CallbackInfo ci) {
        this.annotationName = StringPool.LOADER.deduplicate(this.annotationName);
        this.className = StringPool.LOADER.deduplicate(this.className);
        this.objectName = StringPool.LOADER.deduplicate(this.objectName);
    }
}
