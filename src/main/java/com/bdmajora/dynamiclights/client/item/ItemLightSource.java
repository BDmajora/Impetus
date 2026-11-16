package com.bdmajora.dynamiclights.client.item;

import com.bdmajora.dynamiclights.DynamicLights;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Optional;

// Light level of one item.
public abstract class ItemLightSource {
    // Identifier of the definition.
    private final ResourceLocation id;
    // Item that emits light.
    private final Item item;
    // Whether the light goes out in a fluid.
    private final boolean waterSensitive;

    // Creates a light source.
    protected ItemLightSource(ResourceLocation id, Item item, boolean waterSensitive) {
        this.id = id;
        this.item = item;
        this.waterSensitive = waterSensitive;
    }

    // Returns the identifier of the definition.
    public ResourceLocation id() {
        return this.id;
    }

    // Returns the item.
    public Item item() {
        return this.item;
    }

    // Returns whether the light goes out in a fluid.
    public boolean waterSensitive() {
        return this.waterSensitive;
    }

    // Returns the light level and zero for a submerged water sensitive item.
    public int getLuminance(ItemStack stack, boolean submergedInWater) {
        if (this.waterSensitive() && DynamicLights.options().waterSensitiveCheck && submergedInWater) {
            return 0;
        }
        return this.getLuminance(stack);
    }

    // Returns the light level of the stack.
    public abstract int getLuminance(ItemStack stack);

    // Returns the definition as text.
    @Override
    public String toString() {
        return "ItemLightSource{id=" + this.id + ", item=" + this.item
                + ", water_sensitive=" + this.waterSensitive + '}';
    }

    // Returns the light source read from a definition or empty when it is invalid.
    public static Optional<ItemLightSource> fromJson(ResourceLocation id, JsonObject json) {
        if (!json.has("item") || !json.has("luminance")) {
            DynamicLights.LOGGER.warn("Item light source \"{}\" is missing a required field", id);
            return Optional.empty();
        }

        Item item;
        try {
            item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(json.get("item").getAsString()));
        } catch (Exception e) {
            DynamicLights.LOGGER.warn("Could not parse the item id for \"{}\"", id);
            return Optional.empty();
        }

        if (item == null || item == Items.AIR) {
            return Optional.empty();
        }

        boolean waterSensitive = json.has("water_sensitive") && json.get("water_sensitive").getAsBoolean();
        JsonPrimitive luminance = json.getAsJsonPrimitive("luminance");

        if (luminance.isNumber()) {
            return Optional.of(new StaticItemLightSource(id, item, luminance.getAsInt(), waterSensitive));
        }

        if (!luminance.isString()) {
            DynamicLights.LOGGER.warn("Item light source \"{}\" has a luminance that is neither a number "
                    + "nor a string", id);
            return Optional.empty();
        }

        String value = luminance.getAsString();

        if (value.equals("block")) {
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                return Optional.of(new BlockItemLightSource(id, item, block.getDefaultState(), waterSensitive));
            }
            DynamicLights.LOGGER.warn("Item light source \"{}\" uses \"block\" luminance but \"{}\" does not "
                    + "place a block", id, item);
            return Optional.empty();
        }

        try {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(value));
            if (block != null && block != Blocks.AIR) {
                return Optional.of(new BlockItemLightSource(id, item, block.getDefaultState(), waterSensitive));
            }
        } catch (Exception e) {
            DynamicLights.LOGGER.warn("Item light source \"{}\" names an invalid block \"{}\"", id, value);
        }

        return Optional.empty();
    }

    // Light source with a fixed level.
    public static class StaticItemLightSource extends ItemLightSource {
        // Fixed light level.
        private final int luminance;

        // Creates a light source.
        public StaticItemLightSource(ResourceLocation id, Item item, int luminance, boolean waterSensitive) {
            super(id, item, waterSensitive);
            this.luminance = luminance;
        }

        // Returns the fixed level.
        @Override
        public int getLuminance(ItemStack stack) {
            return this.luminance;
        }
    }

    // Light source that copies the level of a block.
    public static class BlockItemLightSource extends ItemLightSource {
        // Block state whose level is copied.
        private final IBlockState mimic;

        // Creates a light source.
        public BlockItemLightSource(ResourceLocation id, Item item, IBlockState block, boolean waterSensitive) {
            super(id, item, waterSensitive);
            this.mimic = block;
        }

        // Returns the level of the block state of the stack.
        @Override
        public int getLuminance(ItemStack stack) {
            return getLuminance(stack, this.mimic);
        }

        // Returns the level of the state with the properties stored in the stack.
        @SuppressWarnings("deprecation")
        public static int getLuminance(ItemStack stack, IBlockState state) {
            NBTTagCompound nbt = stack.getTagCompound();

            if (nbt != null && nbt.hasKey("BlockStateTag", 10)) {
                NBTTagCompound blockStateTag = nbt.getCompoundTag("BlockStateTag");

                for (String key : blockStateTag.getKeySet()) {
                    for (IProperty<?> property : state.getPropertyKeys()) {
                        if (property.getName().equals(key)) {
                            state = withValue(state, property, blockStateTag.getString(key));
                            break;
                        }
                    }
                }
            }

            return state.getLightValue();
        }

        // Returns the state with the property set from text or the same state when no value matches.
        private static <T extends Comparable<T>> IBlockState withValue(IBlockState state,
                                                                      IProperty<T> property, String value) {
            for (T allowed : property.getAllowedValues()) {
                if (allowed.toString().equalsIgnoreCase(value)) {
                    return state.withProperty(property, allowed);
                }
            }
            return state;
        }
    }
}
