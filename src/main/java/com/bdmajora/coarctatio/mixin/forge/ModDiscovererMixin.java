package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.launch.discovery.ModScanCache;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ModDiscoverer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// identifyMods is the whole discovery pass; when it returns every jar has been scanned or served and the cache can be written. remap = false, Forge class
@Mixin(value = ModDiscoverer.class, remap = false)
public abstract class ModDiscovererMixin {
    @Inject(method = "identifyMods", at = @At("RETURN"))
    private void coarctatio$saveScanCache(CallbackInfoReturnable<List<ModContainer>> cir) {
        ModScanCache.save();
    }
}
