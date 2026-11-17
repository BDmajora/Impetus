package com.bdmajora.coarctatio.mixin.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Mixin that changes OreDictionary.
@Mixin(value = OreDictionary.class, remap = false)
public abstract class OreDictionaryMixin {
    // Default return value of the name map.
    @Unique
    private static final int COARCTATIO_NO_ID = -1;

    @Shadow
    @Mutable
    private static Map<String, Integer> nameToId;

    @Shadow
    @Mutable
    private static Map<Integer, List<Integer>> stackToId;

    // Reference to the field id to name of the target.
    @Shadow
    private static List<String> idToName;

    // Reference to the field id to stack of the target.
    @Shadow
    private static List<NonNullList<ItemStack>> idToStack;

    // Reference to the field id to stack un of the target.
    @Shadow
    private static List<NonNullList<ItemStack>> idToStackUn;

    // Reference to the method get ores of the target.
    @Shadow
    private static NonNullList<ItemStack> getOres(int id) {
        throw new AssertionError();
    }

    // Runs around the call of OreDictionary.initVanillaEntries in static initializer.
    @Inject(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/oredict/OreDictionary;initVanillaEntries()V"))
    private static void coarctatio$installPrimitiveMaps(CallbackInfo ci) {
        Object2IntOpenHashMap<String> names = new Object2IntOpenHashMap<>(128);
        names.defaultReturnValue(COARCTATIO_NO_ID);
        nameToId = coarctatio$cast(names);
        stackToId = coarctatio$cast(new Int2ObjectOpenHashMap<IntList>((int) (128 * 0.75)));
    }

    // Casts a primitive map to the boxed type Forge declares the field with.
    @Unique
    @SuppressWarnings("unchecked")
    private static <T> T coarctatio$cast(Object value) {
        return (T) value;
    }

    // Returns the name to id map as the primitive map it really is.
    @Unique
    private static Object2IntOpenHashMap<String> coarctatio$names() {
        return coarctatio$cast(nameToId);
    }

    // Returns the stack hash to ids map as the primitive map it really is.
    @Unique
    private static Int2ObjectOpenHashMap<IntList> coarctatio$stacks() {
        return coarctatio$cast(stackToId);
    }

    // Returns the ids of a stack hash and converts a boxed list on first sight.
    @Unique
    @SuppressWarnings("unchecked")
    private static IntList coarctatio$ids(int hash) {
        Int2ObjectOpenHashMap<Object> raw = coarctatio$cast(stackToId);
        Object value = raw.get(hash);
        if (value == null || value instanceof IntList) {
            return (IntList) value;
        }
        IntArrayList primitive = new IntArrayList((List<Integer>) value);
        raw.put(hash, primitive);
        return primitive;
    }

    // Replaces the whole method getOreID.
    @Overwrite
    public static int getOreID(String name) {
        Object2IntOpenHashMap<String> names = coarctatio$names();
        int cached = names.getInt(name);
        if (cached != COARCTATIO_NO_ID) {
            return cached;
        }
        idToName.add(name);
        int id = idToName.size() - 1;
        names.put(name, id);
        NonNullList<ItemStack> back = NonNullList.create();
        idToStack.add(back);
        idToStackUn.add(back);
        return id;
    }

    // Forge getOreIDs over the primitive maps in HashSet order.
    @Overwrite
    public static int[] getOreIDs(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Stack can not be invalid!");
        }

        ResourceLocation registryName = stack.getItem().delegate.name();
        if (registryName == null) {
            FMLLog.log.debug("Attempted to find the oreIDs for an unregistered object ({}). This won't work very well.", stack);
            return new int[0];
        }

        int id = Item.REGISTRY.getIDForObject(stack.getItem().delegate.get());
        IntList byItem = coarctatio$ids(id);
        IntList byMeta = coarctatio$ids(id | ((stack.getItemDamage() + 1) << 16));
        int itemCount = byItem == null ? 0 : byItem.size();
        int metaCount = byMeta == null ? 0 : byMeta.size();
        if (itemCount + metaCount == 0) {
            return new int[0];
        }
        if (itemCount + metaCount == 1) {
            return new int[]{itemCount == 1 ? byItem.getInt(0) : byMeta.getInt(0)};
        }
        // Collects the ids in a HashSet like Forge does.
        Set<Integer> set = new HashSet<>();
        if (byItem != null) {
            set.addAll(byItem);
        }
        if (byMeta != null) {
            set.addAll(byMeta);
        }
        int[] ids = new int[set.size()];
        int index = 0;
        for (int oreId : set) {
            ids[index++] = oreId;
        }
        return ids;
    }

    // Replaces the whole method doesOreNameExist.
    @Overwrite
    public static boolean doesOreNameExist(String name) {
        return coarctatio$names().getInt(name) != COARCTATIO_NO_ID;
    }

    // Replaces the whole method getOres.
    @Overwrite
    public static NonNullList<ItemStack> getOres(String name, boolean alwaysCreateEntry) {
        if (alwaysCreateEntry) {
            return getOres(getOreID(name));
        }
        return coarctatio$names().getInt(name) != COARCTATIO_NO_ID ? getOres(getOreID(name)) : OreDictionary.EMPTY_LIST;
    }

    // Replaces the whole method registerOreImpl.
    @Overwrite
    private static void registerOreImpl(String name, @Nonnull ItemStack ore) {
        if ("Unknown".equals(name)) {
            return;
        }
        if (ore.isEmpty()) {
            FMLLog.bigWarning("Invalid registration attempt for an Ore Dictionary item with name {} has occurred. The registration has been denied to prevent crashes. The mod responsible for the registration needs to correct this.", name);
            return;
        }

        int oreID = getOreID(name);
        ResourceLocation registryName = ore.getItem().delegate.name();
        int hash;
        if (registryName == null) {
            ModContainer modContainer = Loader.instance().activeModContainer();
            String modContainerName = modContainer == null ? null : modContainer.getName();
            FMLLog.bigWarning("A broken ore dictionary registration with name {} has occurred. It adds an item (type: {}) which is currently unknown to the game registry. This dictionary item can only support a single value when"
                    + " registered with ores like this, and NO I am not going to turn this spam off. Just register your ore dictionary entries after the GameRegistry.\n"
                    + "TO USERS: YES this is a BUG in the mod " + modContainerName + " report it to them!", name, ore.getItem().getClass());
            hash = COARCTATIO_NO_ID;
        } else {
            hash = Item.REGISTRY.getIDForObject(ore.getItem().delegate.get());
        }
        if (ore.getItemDamage() != OreDictionary.WILDCARD_VALUE) {
            hash |= ((ore.getItemDamage() + 1) << 16);
        }

        IntList ids = coarctatio$ids(hash);
        if (ids != null && ids.contains(oreID)) {
            return;
        }
        if (ids == null) {
            ids = new IntArrayList();
            coarctatio$stacks().put(hash, ids);
        }
        ids.add(oreID);

        ore = ore.copy();
        idToStack.get(oreID).add(ore);
        MinecraftForge.EVENT_BUS.post(new OreDictionary.OreRegisterEvent(name, ore));
    }

    // Replaces the whole method rebakeMap.
    @Overwrite
    public static void rebakeMap() {
        Int2ObjectOpenHashMap<IntList> stacks = coarctatio$stacks();
        stacks.clear();
        for (int id = 0; id < idToStack.size(); id++) {
            NonNullList<ItemStack> ores = idToStack.get(id);
            if (ores == null) {
                continue;
            }
            for (ItemStack ore : ores) {
                ResourceLocation name = ore.getItem().delegate.name();
                int hash;
                if (name == null) {
                    FMLLog.log.debug("Defaulting unregistered ore dictionary entry for ore dictionary {}: type {} to -1", OreDictionary.getOreName(id), ore.getItem().getClass());
                    hash = COARCTATIO_NO_ID;
                } else {
                    hash = Item.REGISTRY.getIDForObject(ore.getItem().delegate.get());
                }
                if (ore.getItemDamage() != OreDictionary.WILDCARD_VALUE) {
                    hash |= ((ore.getItemDamage() + 1) << 16);
                }
                IntList ids = stacks.get(hash);
                if (ids == null) {
                    ids = new IntArrayList();
                    stacks.put(hash, ids);
                }
                ids.add(id);
            }
        }
    }
}
