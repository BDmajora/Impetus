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

// Every structure template ever loaded (each a full block-by-block copy, and modded structure packs ship thousands) stays in the manager's map for the life of the save (VintageFix's dynamic structures); soft values let the collector reclaim the ones world-gen has moved past, and a template asked for again simply reloads from disk
@Mixin(TemplateManager.class)
public abstract class TemplateManagerMixin {
    @Shadow
    @Final
    @Mutable
    private Map<String, Template> templates;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$softTemplates(CallbackInfo ci) {
        this.templates = CacheBuilder.newBuilder().softValues().<String, Template>build().asMap();
    }
}
