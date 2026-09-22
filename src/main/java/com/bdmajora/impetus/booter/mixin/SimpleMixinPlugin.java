package com.bdmajora.impetus.booter.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

// The no-op half of IMixinConfigPlugin every Impetus subsystem shares: no refmap (Impetus reobfuscates mixins directly), the json's own mixin list, nothing to negotiate and no bytecode rewriting; subsystems override only the gating they need
public abstract class SimpleMixinPlugin implements IMixinConfigPlugin {
    // Nothing to prepare unless a subsystem has a config to load
    @Override
    public void onLoad(String mixinPackage) {
    }

    // Null: there is no refmap to name
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    // Applies everything unless a subsystem gates by config
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    // Nothing to negotiate with other configs
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    // Null means use the mixin list from the json
    @Override
    public List<String> getMixins() {
        return null;
    }

    // No pre-apply rewriting needed
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    // No post-apply rewriting needed
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
