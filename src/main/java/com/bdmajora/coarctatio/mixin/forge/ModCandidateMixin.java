package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.coarctatio.dedup.StringPool;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Mixin that changes ModCandidate.
@Mixin(value = ModCandidate.class, remap = false)
public abstract class ModCandidateMixin {
    // Packages already added.
    private final Set<String> coarctatio$seenPackages = new HashSet<>();

    // Wraps the call of Set.add in addClassEntry.
    @WrapOperation(method = "addClassEntry", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean coarctatio$internClassEntry(Set<Object> set, Object className, Operation<Boolean> original) {
        return original.call(set, StringPool.LOADER.deduplicate((String) className));
    }

    // Wraps the call of List.add in addClassEntry.
    @WrapOperation(method = "addClassEntry", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean coarctatio$addPackageOnce(List<Object> list, Object pkg, Operation<Boolean> original) {
        String name = StringPool.LOADER.deduplicate((String) pkg);
        if (!this.coarctatio$seenPackages.add(name)) {
            return false;
        }
        return original.call(list, name);
    }
}
