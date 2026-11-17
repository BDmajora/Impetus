package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.collections.CollectionHelper;
import com.bdmajora.coarctatio.dedup.TransformCaches;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Mixin that changes SimpleBakedModel.
@Mixin(SimpleBakedModel.class)
public class SimpleBakedModelMixin {
    // Reference to the field general quads of the target.
    @Mutable
    @Shadow
    @Final
    protected List<BakedQuad> generalQuads;

    @Mutable
    @Shadow
    @Final
    protected Map<EnumFacing, List<BakedQuad>> faceQuads;

    // Reference to the field camera transforms of the target.
    @Mutable
    @Shadow
    @Final
    protected ItemCameraTransforms cameraTransforms;

    // Reference to the field item override list of the target.
    @Mutable
    @Shadow
    @Final
    protected ItemOverrideList itemOverrideList;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$compactQuadLists(CallbackInfo ci) {
        // Shares the transforms and overrides when their switch is on.
        if (CoarctatioConfig.get().deduplicateModelTransforms) {
            this.cameraTransforms = TransformCaches.TRANSFORMS.deduplicate(this.cameraTransforms);
            this.itemOverrideList = TransformCaches.deduplicate(this.itemOverrideList);
        }

        this.generalQuads = CollectionHelper.fixed(this.generalQuads);

        Map<EnumFacing, List<BakedQuad>> compacted = new EnumMap<>(EnumFacing.class);

        for (Map.Entry<EnumFacing, List<BakedQuad>> entry : this.faceQuads.entrySet()) {
            compacted.put(entry.getKey(), CollectionHelper.fixed(entry.getValue()));
        }

        this.faceQuads = compacted;
    }
}
