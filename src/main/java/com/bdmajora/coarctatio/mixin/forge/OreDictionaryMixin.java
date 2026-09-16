package com.bdmajora.coarctatio.mixin.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
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
import java.util.List;
import java.util.Map;

// The ore dictionary's two lookup maps keyed and valued by primitives (StellarCore): a modpack registers tens of thousands of ores, each boxing its id into an Integer and a List<Integer>, and every recipe match boxes again to look them up. remap = false, Forge class
@Mixin(value = OreDictionary.class, remap = false)
public abstract class OreDictionaryMixin {
    @Unique
    private static final int COARCTATIO_NO_ID = -1;

    @Shadow
    @Mutable
    private static Map<String, Integer> nameToId;

    @Shadow
    @Mutable
    private static Map<Integer, List<Integer>> stackToId;

    @Shadow
    private static List<String> idToName;

    @Shadow
    private static List<NonNullList<ItemStack>> idToStack;

    @Shadow
    private static List<NonNullList<ItemStack>> idToStackUn;

    @Shadow
    private static NonNullList<ItemStack> getOres(int id) {
        throw new AssertionError();
    }

    // Swapped in before the vanilla entries are registered, so no boxed entry ever exists; the raw types are what let a fastutil map sit behind the declared generic ones
    @Inject(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/oredict/OreDictionary;initVanillaEntries()V"))
    private static void coarctatio$installPrimitiveMaps(CallbackInfo ci) {
        Object2IntOpenHashMap<String> names = new Object2IntOpenHashMap<>(128);
        names.defaultReturnValue(COARCTATIO_NO_ID);
        nameToId = coarctatio$cast(names);
        stackToId = coarctatio$cast(new Int2ObjectOpenHashMap<IntList>((int) (128 * 0.75)));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <T> T coarctatio$cast(Object value) {
        return (T) value;
    }

    @Unique
    private static Object2IntOpenHashMap<String> coarctatio$names() {
        return coarctatio$cast(nameToId);
    }

    @Unique
    private static Int2ObjectOpenHashMap<IntList> coarctatio$stacks() {
        return coarctatio$cast(stackToId);
    }

    // A mod reaching into stackToId reflectively could have stored a boxed list; it is converted on first sight
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

    /**
     * @author Circulate233, bdmajora
     * @reason The name lookup without boxing the id
     */
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

    /**
     * @author Circulate233, bdmajora
     * @reason The ids for a stack collected into a primitive set rather than through a HashSet<Integer> and an Integer[]
     */
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
        if (byItem == null || byItem.isEmpty()) {
            return byMeta == null || byMeta.isEmpty() ? new int[0] : byMeta.toIntArray();
        }
        if (byMeta == null || byMeta.isEmpty()) {
            return byItem.toIntArray();
        }

        IntArraySet set = new IntArraySet(byItem.size() + byMeta.size());
        set.addAll(byItem);
        set.addAll(byMeta);
        return set.toIntArray();
    }

    /**
     * @author Circulate233, bdmajora
     * @reason Existence test without boxing
     */
    @Overwrite
    public static boolean doesOreNameExist(String name) {
        return coarctatio$names().getInt(name) != COARCTATIO_NO_ID;
    }

    /**
     * @author Circulate233, bdmajora
     * @reason Probes the primitive map before creating an entry
     */
    @Overwrite
    public static NonNullList<ItemStack> getOres(String name, boolean alwaysCreateEntry) {
        if (alwaysCreateEntry) {
            return getOres(getOreID(name));
        }
        return coarctatio$names().getInt(name) != COARCTATIO_NO_ID ? getOres(getOreID(name)) : OreDictionary.EMPTY_LIST;
    }

    /**
     * @author Circulate233, bdmajora
     * @reason Registration straight into primitive lists, otherwise vanilla's logic and warnings verbatim
     */
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

    /**
     * @author Circulate233, bdmajora
     * @reason Rebakes into primitive lists
     */
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
