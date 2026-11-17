package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.model.dynamic.IndexedResourcePack;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.resources.FileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Mixin that changes FileResourcePack.
@Mixin(FileResourcePack.class)
public abstract class FileResourcePackMixin implements IndexedResourcePack {
    // Reference to the method get resource pack zip file of the target.
    @Shadow
    protected abstract ZipFile getResourcePackZipFile() throws IOException;

    // Names of the entries in the zip.
    private Set<String> coarctatio$entries;
    // Flag set when the zip listing failed.
    private boolean coarctatio$entriesFailed;

    // Runs at the start of hasResourceName and can cancel it.
    @Inject(method = "hasResourceName", at = @At("HEAD"), cancellable = true)
    private void coarctatio$lookupInIndex(String name, CallbackInfoReturnable<Boolean> cir) {
        Set<String> entries = this.coarctatio$entries;
        if (entries == null) {
            if (this.coarctatio$entriesFailed) {
                return;
            }
            entries = this.coarctatio$index();
            if (entries == null) {
                this.coarctatio$entriesFailed = true;
                return;
            }
            this.coarctatio$entries = entries;
        }
        cir.setReturnValue(entries.contains(name));
    }

    // Returns every entry name of the zip or null when it cannot be read.
    @Override
    public Set<String> coarctatio$indexedPaths() {
        if (this.coarctatio$entries == null && !this.coarctatio$entriesFailed) {
            this.coarctatio$entries = this.coarctatio$index();
            this.coarctatio$entriesFailed = this.coarctatio$entries == null;
        }
        return this.coarctatio$entries;
    }

    // Lists the entry names of the zip or null when it cannot be read.
    private Set<String> coarctatio$index() {
        try {
            ZipFile zip = this.getResourcePackZipFile();
            ObjectOpenHashSet<String> entries = new ObjectOpenHashSet<>(zip.size());
            Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith("assets/") || entryName.indexOf('/') == -1) {
                    entries.add(entryName);
                }
            }
            entries.trim();
            return entries;
        } catch (IOException e) {
            return null;
        }
    }
}
