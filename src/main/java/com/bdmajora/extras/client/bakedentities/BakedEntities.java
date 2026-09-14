package com.bdmajora.extras.client.bakedentities;

import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.BlockSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Calendar;

// Block entities drawn as terrain (after FoundationGames' Enhanced Block Entities and the Better Block Entities team's design): chests, ender chests, signs, beds and shulker boxes bake to block models from the very ModelRenderer geometry their renderers draw, and only an animating one goes back to its renderer
@Mod.EventBusSubscriber(Side.CLIENT)
public final class BakedEntities {
    // Live switches, applied whenever the Extras config loads or saves
    public static volatile boolean chests;
    public static volatile boolean enderChests;
    public static volatile boolean signs;
    public static volatile boolean beds;
    public static volatile boolean shulkerBoxes;

    // Same window TileEntityChestRenderer uses for the festive texture
    private static final boolean CHRISTMAS = isChristmas();

    private static final ChestBakedModel CHEST = new ChestBakedModel();
    private static final EnderChestBakedModel ENDER_CHEST = new EnderChestBakedModel();
    private static final SignBakedModel SIGN = new SignBakedModel();
    private static final BedBakedModel BED = new BedBakedModel();
    private static final ShulkerBoxBakedModel SHULKER_BOX = new ShulkerBoxBakedModel();

    // The chest sheets, single and double, by type and season
    public enum ChestTexture {
        NORMAL("entity/chest/normal"), NORMAL_DOUBLE("entity/chest/normal_double"),
        TRAPPED("entity/chest/trapped"), TRAPPED_DOUBLE("entity/chest/trapped_double"),
        CHRISTMAS("entity/chest/christmas"), CHRISTMAS_DOUBLE("entity/chest/christmas_double");

        public final ResourceLocation location;

        ChestTexture(String path) {
            this.location = new ResourceLocation("minecraft", path);
        }
    }

    private BakedEntities() {
    }

    public static void apply(ExtrasConfig.BakedEntitySettings settings) {
        boolean on = settings.enabled;
        chests = on && settings.chests;
        enderChests = on && settings.enderChests;
        signs = on && settings.signs;
        beds = on && settings.beds;
        shulkerBoxes = on && settings.shulkerBoxes;
    }

    private static boolean isChristmas() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.MONTH) + 1 == 12 && calendar.get(Calendar.DAY_OF_MONTH) >= 24 && calendar.get(Calendar.DAY_OF_MONTH) <= 26;
    }

    public static ChestTexture chestTexture(BlockChest.Type type, boolean large) {
        if (CHRISTMAS) {
            return large ? ChestTexture.CHRISTMAS_DOUBLE : ChestTexture.CHRISTMAS;
        }
        if (type == BlockChest.Type.TRAP) {
            return large ? ChestTexture.TRAPPED_DOUBLE : ChestTexture.TRAPPED;
        }
        return large ? ChestTexture.NORMAL_DOUBLE : ChestTexture.NORMAL;
    }

    // Whether this block is one of ours and its switch is on; the render type and model hooks both key off this
    public static boolean isBaked(Block block) {
        if (block instanceof BlockChest) {
            return chests;
        }
        if (block instanceof BlockEnderChest) {
            return enderChests;
        }
        if (block instanceof BlockSign) {
            return signs;
        }
        if (block instanceof BlockBed) {
            return beds;
        }
        if (block instanceof BlockShulkerBox) {
            return shulkerBoxes;
        }
        return false;
    }

    // The baked model for a state, or null when its block is not one of ours or is switched off
    public static IBakedModel modelFor(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockChest) {
            return chests ? CHEST : null;
        }
        if (block instanceof BlockEnderChest) {
            return enderChests ? ENDER_CHEST : null;
        }
        if (block instanceof BlockSign) {
            return signs ? SIGN : null;
        }
        if (block instanceof BlockBed) {
            return beds ? BED : null;
        }
        if (block instanceof BlockShulkerBox) {
            return shulkerBoxes ? SHULKER_BOX : null;
        }
        return null;
    }

    // Atlas sprite for an entity texture; the missing sprite comes back if the pack lacks it, which draws purple rather than crashing
    public static TextureAtlasSprite sprite(ResourceLocation location) {
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(location.toString());
    }

    // Puts every entity sheet the models draw from onto the block atlas; registered unconditionally since a toggle can flip without a restitch
    @SubscribeEvent
    public static void onStitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        for (ChestTexture texture : ChestTexture.values()) {
            map.registerSprite(texture.location);
        }
        map.registerSprite(EnderChestBakedModel.TEXTURE);
        map.registerSprite(SignBakedModel.TEXTURE);
        for (EnumDyeColor color : EnumDyeColor.values()) {
            map.registerSprite(BedBakedModel.texture(color));
            map.registerSprite(ShulkerBoxBakedModel.texture(color));
        }
    }

    // Sprite coordinates move on every stitch and models rebake on reload, so cached geometry is dropped at both
    @SubscribeEvent
    public static void onStitched(TextureStitchEvent.Post event) {
        invalidate();
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        invalidate();
    }

    private static void invalidate() {
        CHEST.invalidate();
        ENDER_CHEST.invalidate();
        SIGN.invalidate();
        BED.invalidate();
        SHULKER_BOX.invalidate();
    }
}
