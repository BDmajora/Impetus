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

// hasResourceName goes to ZipFile.getEntry, a synchronised native lookup, and model loading asks every pack about every candidate path; the entry names are read once into a set the first time a pack is asked (VintageFix's and StellarCore's resource existence caches). Per instance, since the pack keeps its ZipFile open for as long as it lives anyway. Mod jars are FileResourcePacks too, so this covers every mod's assets
@Mixin(FileResourcePack.class)
public abstract class FileResourcePackMixin implements IndexedResourcePack {
    @Shadow
    protected abstract ZipFile getResourcePackZipFile() throws IOException;

    private Set<String> coarctatio$entries;
    private boolean coarctatio$entriesFailed;

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

    // The same index, for the texture scan; built here if nothing has probed the pack yet
    @Override
    public Set<String> coarctatio$indexedPaths() {
        if (this.coarctatio$entries == null && !this.coarctatio$entriesFailed) {
            this.coarctatio$entries = this.coarctatio$index();
            this.coarctatio$entriesFailed = this.coarctatio$entries == null;
        }
        return this.coarctatio$entries;
    }

    private Set<String> coarctatio$index() {
        try {
            ZipFile zip = this.getResourcePackZipFile();
            ObjectOpenHashSet<String> entries = new ObjectOpenHashSet<>(zip.size());
            Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                // Only assets and top-level files (pack.mcmeta, pack.png) are ever asked for; class files stay out of the set
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
