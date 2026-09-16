package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.launch.discovery.ModScanCache;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.JarDiscoverer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

// The scan loop opens each class entry and hands the stream to a new ASMModParser; the entry name is caught on the way past and, when the jar's record has that class, the parser is rebuilt from the cache and the stream left unread (the loop's try-with-resources still closes it). remap = false, Forge class
@Mixin(value = JarDiscoverer.class, remap = false)
public abstract class JarDiscovererMixin {
    private Map<String, byte[]> coarctatio$record;
    private String coarctatio$entryName;

    @Inject(method = "discover", at = @At("HEAD"))
    private void coarctatio$openRecord(ModCandidate candidate, ASMDataTable table, CallbackInfoReturnable<List<ModContainer>> cir) {
        this.coarctatio$record = ModScanCache.recordFor(ModScanCache.keyFor(candidate.getModContainer()));
    }

    @Redirect(method = "findClassesASM", at = @At(value = "INVOKE", target = "Ljava/util/jar/JarFile;getInputStream(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;"))
    private InputStream coarctatio$rememberEntry(JarFile jar, ZipEntry entry) throws IOException {
        this.coarctatio$entryName = entry.getName();
        return jar.getInputStream(entry);
    }

    @Redirect(method = "findClassesASM", at = @At(value = "NEW", target = "(Ljava/io/InputStream;)Lnet/minecraftforge/fml/common/discovery/asm/ASMModParser;"))
    private ASMModParser coarctatio$parserFromCache(InputStream stream) throws IOException {
        Map<String, byte[]> record = this.coarctatio$record;
        String name = this.coarctatio$entryName;
        if (record != null && name != null) {
            byte[] blob = record.get(name);
            if (blob != null) {
                ASMModParser cached = ModScanCache.decode(blob);
                if (cached != null) {
                    return cached;
                }
            }
        }
        ASMModParser parser = new ASMModParser(stream);
        if (record != null && name != null) {
            byte[] blob = ModScanCache.encode(parser);
            if (blob != null) {
                record.put(name, blob);
            }
        }
        return parser;
    }
}
