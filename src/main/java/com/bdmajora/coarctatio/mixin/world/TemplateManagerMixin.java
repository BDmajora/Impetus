package com.bdmajora.coarctatio.mixin.world;

import com.google.common.cache.CacheBuilder;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.world.gen.structure.template.TemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes TemplateManager.
@Mixin(TemplateManager.class)
public abstract class TemplateManagerMixin {
    @Shadow
    @Final
    @Mutable
    private Map<String, Template> templates;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$softTemplates(CallbackInfo ci) {
        this.templates = CacheBuilder.newBuilder().softValues().<String, Template>build().asMap();
    }
}
