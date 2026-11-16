package com.bdmajora.impetus.core;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

// Mixin plugin that applies every mixin unless a subclass skips it.
public abstract class SimpleMixinPlugin implements IMixinConfigPlugin {
    // Handles the load.
    @Override
    public void onLoad(String mixinPackage) {
    }

    // Returns the ref mapper config.
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    // Returns whether apply mixin.
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    // Ignores the target lists.
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    // Returns the mixins.
    @Override
    public List<String> getMixins() {
        return null;
    }

    // Does nothing before a mixin applies.
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    // Does nothing after a mixin applies.
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
