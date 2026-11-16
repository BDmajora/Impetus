package com.bdmajora.extras.client.bakedentities;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.bakedentities.TextureMapRegisteredSpritesAccessor;
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

// Block entities drawn as baked models.
@Mod.EventBusSubscriber(Side.CLIENT)
public final class BakedEntities {
    // Whether chests are baked.
    public static volatile boolean chests;
    // Whether ender chests are baked.
    public static volatile boolean enderChests;
    // Whether signs are baked.
    public static volatile boolean signs;
    // Whether beds are baked.
    public static volatile boolean beds;
    // Whether shulker boxes are baked.
    public static volatile boolean shulkerBoxes;

    // Whether the holiday texture applies.
    private static final boolean CHRISTMAS = isChristmas();

    // Model of chests.
    private static final ChestBakedModel CHEST = new ChestBakedModel();
    // Model of ender chests.
    private static final EnderChestBakedModel ENDER_CHEST = new EnderChestBakedModel();
    // Model of signs.
    private static final SignBakedModel SIGN = new SignBakedModel();
    // Model of beds.
    private static final BedBakedModel BED = new BedBakedModel();
    // Model of shulker boxes.
    private static final ShulkerBoxBakedModel SHULKER_BOX = new ShulkerBoxBakedModel();

    // Texture of a chest.
    public enum ChestTexture {
        NORMAL("entity/chest/normal"), NORMAL_DOUBLE("entity/chest/normal_double"),
        TRAPPED("entity/chest/trapped"), TRAPPED_DOUBLE("entity/chest/trapped_double"),
        CHRISTMAS("entity/chest/christmas"), CHRISTMAS_DOUBLE("entity/chest/christmas_double");

        // Location of the texture.
        public final ResourceLocation location;

        // Creates a texture entry.
        ChestTexture(String path) {
            this.location = new ResourceLocation("minecraft", path);
        }
    }

    // Prevents instantiation
    private BakedEntities() {
    }

    // Applies the settings.
    public static void apply(ExtrasConfig.BakedEntitySettings settings) {
        boolean on = settings.enabled;
        chests = on && settings.chests;
        enderChests = on && settings.enderChests;
        signs = on && settings.signs;
        beds = on && settings.beds;
        shulkerBoxes = on && settings.shulkerBoxes;
    }

    // Returns whether the date is a holiday.
    private static boolean isChristmas() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.MONTH) + 1 == 12 && calendar.get(Calendar.DAY_OF_MONTH) >= 24 && calendar.get(Calendar.DAY_OF_MONTH) <= 26;
    }

    // Returns the texture of the chest.
    public static ChestTexture chestTexture(BlockChest.Type type, boolean large) {
        if (CHRISTMAS) {
            return large ? ChestTexture.CHRISTMAS_DOUBLE : ChestTexture.CHRISTMAS;
        }
        if (type == BlockChest.Type.TRAP) {
            return large ? ChestTexture.TRAPPED_DOUBLE : ChestTexture.TRAPPED;
        }
        return large ? ChestTexture.NORMAL_DOUBLE : ChestTexture.NORMAL;
    }

    // Returns whether the block is drawn as a baked model.
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

    // Returns the model of the state or null.
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

    // Returns the atlas sprite of the location.
    public static TextureAtlasSprite sprite(ResourceLocation location) {
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(location.toString());
    }

    // Registers the sheets before stitching.
    @SubscribeEvent
    public static void onStitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        if (map != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return;
        }
        for (ChestTexture texture : ChestTexture.values()) {
            registerSheet(map, texture.location);
        }
        registerSheet(map, EnderChestBakedModel.TEXTURE);
        registerSheet(map, SignBakedModel.TEXTURE);
        for (EnumDyeColor color : EnumDyeColor.values()) {
            registerSheet(map, BedBakedModel.texture(color));
            registerSheet(map, ShulkerBoxBakedModel.texture(color));
        }
    }

    // Registers one sheet.
    private static void registerSheet(TextureMap map, ResourceLocation location) {
        EntitySheetSprite sprite = new EntitySheetSprite(location, map.getMipmapLevels());
        if (!map.setTextureEntry(sprite)) {
            ((TextureMapRegisteredSpritesAccessor) map).impetus$getRegisteredSprites().put(sprite.getIconName(), sprite);
        }
    }

    // Clears the cached models.
    @SubscribeEvent
    public static void onStitched(TextureStitchEvent.Post event) {
        invalidate();
    }

    // Clears the cached models.
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        invalidate();
    }

    // Clears the cached models.
    private static void invalidate() {
        CHEST.invalidate();
        ENDER_CHEST.invalidate();
        SIGN.invalidate();
        BED.invalidate();
        SHULKER_BOX.invalidate();
    }
}
