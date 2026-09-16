package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.model.dynamic.IndexedResourcePack;
import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.Set;

// A directory pack answers each lookup with a stat and a canonical-path resolution (the latter to reject wrong-case names on case-insensitive filesystems); the tree is walked once per reload instead, and membership in an exact-case index answers both questions
@Mixin(FolderResourcePack.class)
public abstract class FolderResourcePackMixin extends AbstractResourcePack implements IndexedResourcePack {
    protected FolderResourcePackMixin(File file) {
        super(file);
    }

    private Set<String> coarctatio$files;
    private int coarctatio$indexedGeneration = -1;

    @Inject(method = "getFile", at = @At("HEAD"), cancellable = true)
    private void coarctatio$lookupInIndex(String name, CallbackInfoReturnable<File> cir) {
        cir.setReturnValue(this.coarctatio$indexedPaths().contains(name) ? new File(this.resourcePackFile, name) : null);
    }

    // The same index, for the texture scan; rebuilt when the reload generation moved on
    @Override
    public Set<String> coarctatio$indexedPaths() {
        int generation = ResourceLookupCaches.generation();
        if (this.coarctatio$files == null || this.coarctatio$indexedGeneration != generation) {
            this.coarctatio$files = ResourceLookupCaches.indexFolder(this.resourcePackFile);
            this.coarctatio$indexedGeneration = generation;
        }
        return this.coarctatio$files;
    }
}
