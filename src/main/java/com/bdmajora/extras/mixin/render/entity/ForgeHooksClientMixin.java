package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.client.ItemFrameLodState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

// Item quads on Forge's emissive-item path (coplanar faces resolved, then item-frame LOD); allowEmissiveItems defaults to true, so most item rendering goes through renderLitItem instead of RenderItem.renderModel, and both are hooked
@Mixin(ForgeHooksClient.class)
public class ForgeHooksClientMixin {
    // Not remap = false despite the Forge class: the enclosing method is Forge's, but IBakedModel.getQuads is Minecraft's and becomes func_188616_a in production
    @Redirect(
            method = "renderLitItem(Lnet/minecraft/client/renderer/RenderItem;Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/model/IBakedModel;getQuads(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;")
    )
    private static List<BakedQuad> impetus$itemQuads(IBakedModel model, IBlockState state, EnumFacing side, long rand) {
        return ItemFrameLodState.filterLodQuads(model, state, side, rand);
    }
}
