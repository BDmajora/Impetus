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

// Mixin that changes FolderResourcePack.
@Mixin(FolderResourcePack.class)
public abstract class FolderResourcePackMixin extends AbstractResourcePack implements IndexedResourcePack {
    // Creates a folder resource pack mixin from its file.
    protected FolderResourcePackMixin(File file) {
        super(file);
    }

    // Relative paths of the files in the folder.
    private Set<String> coarctatio$files;
    // Generation of the file index.
    private int coarctatio$indexedGeneration = -1;

    // Runs at the start of getFile and can cancel it.
    @Inject(method = "getFile", at = @At("HEAD"), cancellable = true)
    private void coarctatio$lookupInIndex(String name, CallbackInfoReturnable<File> cir) {
        cir.setReturnValue(this.coarctatio$indexedPaths().contains(name) ? new File(this.resourcePackFile, name) : null);
    }

    // Handles indexed paths.
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
